package com.druk.ai_offline_llm.conversation

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.text.Html
import android.util.Base64
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipInputStream

enum class OnlineProvider(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val suggestedModels: List<String>,
) {
    GEMINI(
        id = "gemini",
        displayName = "Gemini",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
        defaultModel = "gemini-2.5-flash",
        suggestedModels = listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash"),
    ),
    OPENAI(
        id = "openai",
        displayName = "OpenAI",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o-mini",
        suggestedModels = listOf("gpt-4o-mini", "gpt-4o", "o3-mini"),
    ),
    OPENROUTER(
        id = "openrouter",
        displayName = "OpenRouter",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        defaultModel = "openrouter/free",
        suggestedModels = listOf(
            "openrouter/free",
            "openai/gpt-4o-mini",
            "google/gemini-2.5-flash",
            "x-ai/grok-3-mini",
            "deepseek/deepseek-chat-v3-0324",
        ),
    ),
    GROK(
        id = "grok",
        displayName = "Grok (xAI)",
        defaultBaseUrl = "https://api.x.ai/v1",
        defaultModel = "grok-3-mini",
        suggestedModels = listOf("grok-3-mini", "grok-3", "grok-2-1212"),
    );

    companion object {
        fun fromId(id: String?): OnlineProvider =
            entries.firstOrNull { it.id == id } ?: GEMINI
    }
}

data class OnlineProviderConfig(
    val provider: OnlineProvider,
    val apiKey: String,
    val baseUrl: String,
    val model: String,
) {
    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank()
}

data class OnlineAttachedFile(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val extractedText: String,
    val lineCount: Int,
    val isTruncated: Boolean = false,
)

object OnlineApiPrefsStore {
    private const val PREF_NAME = "online_api_secure_prefs"
    private const val KEY_ONLINE_ENABLED = "online_mode_enabled"
    private const val KEY_ACTIVE_PROVIDER = "active_provider"
    private const val KEY_WEB_SEARCH_ENABLED = "online_skill_web_search"
    private const val KEY_PROMPT_CACHE_ENABLED = "online_skill_prompt_cache"

    @Volatile
    private var prefsCache: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences {
        prefsCache?.let { return it }
        synchronized(this) {
            prefsCache?.let { return it }
            val appCtx = context.applicationContext
            val created = appCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefsCache = created
            return created
        }
    }

    fun isOnlineModeEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ONLINE_ENABLED, false)

    fun setOnlineModeEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ONLINE_ENABLED, enabled).apply()
    }

    fun isWebSearchEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_WEB_SEARCH_ENABLED, true)

    fun setWebSearchEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_WEB_SEARCH_ENABLED, enabled).apply()
    }

    fun isPromptCacheEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PROMPT_CACHE_ENABLED, true)

    fun setPromptCacheEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_PROMPT_CACHE_ENABLED, enabled).apply()
    }

    fun getActiveProvider(context: Context): OnlineProvider =
        OnlineProvider.fromId(prefs(context).getString(KEY_ACTIVE_PROVIDER, null))

    fun setActiveProvider(context: Context, provider: OnlineProvider) {
        prefs(context).edit().putString(KEY_ACTIVE_PROVIDER, provider.id).apply()
    }

    fun getConfig(context: Context, provider: OnlineProvider): OnlineProviderConfig {
        val p = prefs(context)
        val prefix = "provider_${provider.id}_"
        return OnlineProviderConfig(
            provider = provider,
            apiKey = p.getString("${prefix}key", "").orEmpty().trim(),
            baseUrl = p.getString("${prefix}base_url", provider.defaultBaseUrl)
                .orEmpty().trim().ifEmpty { provider.defaultBaseUrl },
            model = p.getString("${prefix}model", provider.defaultModel)
                .orEmpty().trim().ifEmpty { provider.defaultModel },
        )
    }

    fun saveConfig(context: Context, config: OnlineProviderConfig) {
        val prefix = "provider_${config.provider.id}_"
        prefs(context).edit()
            .putString("${prefix}key", config.apiKey.trim())
            .putString("${prefix}base_url", config.baseUrl.trim().ifEmpty { config.provider.defaultBaseUrl })
            .putString("${prefix}model", config.model.trim().ifEmpty { config.provider.defaultModel })
            .putString(KEY_ACTIVE_PROVIDER, config.provider.id)
            .apply()
    }

    fun getActiveConfig(context: Context): OnlineProviderConfig =
        getConfig(context, getActiveProvider(context))
}

// ===========================================================================
// 1. Vision Skill: Downscale & Encode Image to Base64 Data URL
// ===========================================================================
object OnlineImageEncoder {
    private const val MAX_DIMENSION_PX = 1536
    private const val JPEG_QUALITY = 85
    private val encodedCache = LruCache<String, String>(8)

    fun encodeImageFileToDataUrl(file: File?): String? {
        if (file == null || !file.exists() || file.length() <= 0L) return null
        val cacheKey = "${file.absolutePath}:${file.lastModified()}:${file.length()}"
        encodedCache.get(cacheKey)?.let { return it }

        return runCatching {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
            val srcW = boundsOpts.outWidth
            val srcH = boundsOpts.outHeight
            if (srcW <= 0 || srcH <= 0) return@runCatching null

            var sampleSize = 1
            while (srcW / sampleSize > MAX_DIMENSION_PX * 2 || srcH / sampleSize > MAX_DIMENSION_PX * 2) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return@runCatching null
            val maxSide = maxOf(decoded.width, decoded.height)
            val scaled = if (maxSide > MAX_DIMENSION_PX) {
                val ratio = MAX_DIMENSION_PX.toFloat() / maxSide.toFloat()
                val targetW = (decoded.width * ratio).toInt().coerceAtLeast(1)
                val targetH = (decoded.height * ratio).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
                    if (it !== decoded) decoded.recycle()
                }
            } else {
                decoded
            }

            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos)
            scaled.recycle()

            val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            val dataUrl = "data:image/jpeg;base64,$b64"
            encodedCache.put(cacheKey, dataUrl)
            dataUrl
        }.getOrNull()
    }
}

// ===========================================================================
// 2. Document & Source Code Reader Skill
// ===========================================================================
object OnlineDocumentReader {
    private const val MAX_SINGLE_FILE_CHARS = 90_000
    const val MAX_TOTAL_CONTEXT_CHARS = 360_000

    private val CODE_AND_TEXT_EXTENSIONS = setOf(
        "kt", "kts", "java", "py", "js", "jsx", "mjs", "cjs", "ts", "tsx",
        "c", "cc", "cpp", "cxx", "h", "hpp", "cs", "go", "rs", "rb", "php",
        "swift", "dart", "scala", "groovy", "lua", "r", "pl", "pm",
        "sh", "bash", "zsh", "fish", "bat", "cmd", "ps1",
        "sql", "graphql", "gql", "proto",
        "html", "htm", "css", "scss", "sass", "less", "vue", "svelte",
        "xml", "svg", "xsd", "xsl", "plist",
        "json", "json5", "jsonl", "yaml", "yml", "toml", "ini", "cfg", "conf",
        "properties", "gradle", "env", "gitignore", "dockerignore", "editorconfig",
        "md", "markdown", "rst", "txt", "csv", "tsv", "log", "diff", "patch",
    )

    private val SPECIAL_FILENAMES = setOf(
        "dockerfile", "makefile", "cmakelists.txt", "gradlew", "gemfile", "podfile", "procfile", "readme", "license"
    )

    fun readFromUri(
        context: Context,
        uri: Uri,
        displayName: String,
        mimeType: String?,
    ): Result<OnlineAttachedFile> {
        val ext = displayName.substringAfterLast('.', "").lowercase(Locale.US)
        val lowerName = displayName.lowercase(Locale.US)

        if (ext == "docx" || mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document") {
            return readDocx(context, uri, displayName, mimeType)
        }

        if (ext == "zip" || mimeType == "application/zip" || mimeType == "application/x-zip-compressed") {
            return readZip(context, uri, displayName, mimeType)
        }

        val isKnownCodeOrText = ext in CODE_AND_TEXT_EXTENSIONS ||
            lowerName in SPECIAL_FILENAMES ||
            (mimeType?.startsWith("text/") == true) ||
            mimeType in setOf("application/json", "application/xml", "application/javascript", "application/x-sh")

        return runCatching {
            val rawBytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                var total = 0
                val maxReadBytes = 1024 * 1024 // 1 MB cap for raw text/code
                while (total < maxReadBytes) {
                    val n = input.read(chunk)
                    if (n <= 0) break
                    buffer.write(chunk, 0, n)
                    total += n
                }
                buffer.toByteArray()
            } ?: throw IllegalStateException("ไม่สามารถเปิดอ่านไฟล์ $displayName ได้")

            if (!isKnownCodeOrText && looksBinary(rawBytes)) {
                throw IllegalArgumentException("BINARY_FORMAT")
            }

            val fullText = rawBytes.toString(Charsets.UTF_8).replace("\r\n", "\n")
            if (fullText.isBlank()) {
                throw IllegalStateException("ไฟล์ $displayName ไม่มีเนื้อหาข้อความ")
            }

            val truncated = fullText.length > MAX_SINGLE_FILE_CHARS
            val clippedText = if (truncated) {
                fullText.take(MAX_SINGLE_FILE_CHARS) + "\n... [ตัดทอนเนื้อหาส่วนเกินเนื่องจากไฟล์มีขนาดใหญ่] ..."
            } else {
                fullText
            }
            val lines = clippedText.count { it == '\n' } + 1
            OnlineAttachedFile(
                displayName = displayName,
                mimeType = mimeType,
                sizeBytes = rawBytes.size.toLong(),
                extractedText = clippedText,
                lineCount = lines,
                isTruncated = truncated,
            )
        }
    }

    private fun readDocx(
        context: Context,
        uri: Uri,
        displayName: String,
        mimeType: String?,
    ): Result<OnlineAttachedFile> = runCatching {
        val sb = StringBuilder()
        var fileBytes = 0L
        context.contentResolver.openInputStream(uri)?.use { rawIn ->
            ZipInputStream(rawIn).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        val xmlBytes = zip.readBytes()
                        fileBytes = xmlBytes.size.toLong()
                        val xml = xmlBytes.toString(Charsets.UTF_8)
                        val withBreaks = xml
                            .replace(Regex("</w:p>"), "\n")
                            .replace(Regex("</w:tr>"), "\n")
                            .replace(Regex("</w:tc>"), "\t")
                            .replace(Regex("<[^>]+>"), "")
                        sb.append(decodeHtml(withBreaks))
                        break
                    }
                    entry = zip.nextEntry
                }
            }
        }
        val clean = sb.toString().replace(Regex("\n{3,}"), "\n\n").trim()
        if (clean.isBlank()) {
            throw IllegalStateException("ไม่พบข้อความในเอกสาร $displayName")
        }
        val truncated = clean.length > MAX_SINGLE_FILE_CHARS
        val finalContent = if (truncated) clean.take(MAX_SINGLE_FILE_CHARS) else clean
        val lines = finalContent.count { it == '\n' } + 1
        OnlineAttachedFile(
            displayName = displayName,
            mimeType = mimeType,
            sizeBytes = fileBytes,
            extractedText = finalContent,
            lineCount = lines,
            isTruncated = truncated,
        )
    }

    private fun readZip(
        context: Context,
        uri: Uri,
        displayName: String,
        mimeType: String?,
    ): Result<OnlineAttachedFile> = runCatching {
        val maxEntries = 60
        val maxPerEntryChars = 80_000
        val maxTotalZipChars = 360_000
        val skippedDirs = listOf("/.git/", "/build/", "/node_modules/", "/__macosx/", "/.gradle/", "/.idea/")

        val entryPaths = mutableListOf<String>()
        val extractedSections = StringBuilder()
        var totalChars = 0
        var totalRawBytes = 0L
        var hitLimits = false

        context.contentResolver.openInputStream(uri)?.use { rawIn ->
            ZipInputStream(rawIn).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    val normalized = "/${name.replace('\\', '/').lowercase(Locale.US)}"
                    val isSkippedDir = skippedDirs.any { normalized.contains(it) } ||
                        name.startsWith("__MACOSX/", ignoreCase = true)

                    if (!entry.isDirectory && !isSkippedDir) {
                        if (entryPaths.size >= maxEntries || totalChars >= maxTotalZipChars) {
                            hitLimits = true
                        } else {
                            val bytes = zip.readBytes()
                            totalRawBytes += bytes.size.toLong()
                            val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
                            val isTextLike = ext in CODE_AND_TEXT_EXTENSIONS || !looksBinary(bytes)
                            if (isTextLike && bytes.isNotEmpty()) {
                                val text = bytes.toString(Charsets.UTF_8)
                                    .replace("\r\n", "\n")
                                    .take(maxPerEntryChars)
                                val remaining = maxTotalZipChars - totalChars
                                val piece = if (text.length > remaining) {
                                    hitLimits = true
                                    text.take(remaining) + "\n... [ตัดทอนเนื้อหา ZIP] ..."
                                } else {
                                    text
                                }
                                if (piece.isNotBlank()) {
                                    entryPaths.add(name)
                                    extractedSections.append("### Entry: $name\n")
                                    extractedSections.append("```$ext\n")
                                    extractedSections.append(piece)
                                    if (!piece.endsWith("\n")) extractedSections.append("\n")
                                    extractedSections.append("```\n\n")
                                    totalChars += piece.length
                                }
                            } else {
                                entryPaths.add("$name [binary/skipped]")
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: throw IllegalStateException("ไม่สามารถเปิดอ่านไฟล์ ZIP $displayName ได้")

        if (entryPaths.isEmpty()) {
            throw IllegalStateException("ไม่พบไฟล์ที่อ่านข้อความได้ใน $displayName")
        }

        val summaryHeader = buildString {
            append("ZIP Archive: $displayName\n")
            append("Readable/Listed Entries (${entryPaths.size}):\n")
            entryPaths.forEach { append(" - $it\n") }
            if (hitLimits) append("Note: บางส่วนถูกจำกัดขนาดเพื่อรักษาประสิทธิภาพของคำสั่ง\n")
            append("\n")
        }

        val fullBody = (summaryHeader + extractedSections.toString()).trim()
        val lines = fullBody.count { it == '\n' } + 1
        OnlineAttachedFile(
            displayName = displayName,
            mimeType = mimeType,
            sizeBytes = totalRawBytes,
            extractedText = fullBody,
            lineCount = lines,
            isTruncated = hitLimits,
        )
    }

    private fun looksBinary(bytes: ByteArray): Boolean {
        val sampleLen = minOf(bytes.size, 2048)
        if (sampleLen == 0) return false
        var controlCount = 0
        for (i in 0 until sampleLen) {
            val b = bytes[i].toInt() and 0xFF
            if (b == 0) return true
            if (b < 0x09 || (b in 0x0E..0x1F)) controlCount++
        }
        return controlCount.toFloat() / sampleLen.toFloat() > 0.15f
    }

    fun formatAttachedFilesContext(files: List<OnlineAttachedFile>): String {
        if (files.isEmpty()) return ""
        val sb = StringBuilder()
        sb.append("=== ATTACHED SOURCE CODE & DOCUMENTS ===\n")
        sb.append("Use the attached files below as primary reference when answering, reviewing, debugging, or modifying code.\n\n")
        var remaining = MAX_TOTAL_CONTEXT_CHARS
        for (file in files) {
            if (remaining <= 400) break
            val ext = file.displayName.substringAfterLast('.', "").lowercase(Locale.US)
            val body = if (file.extractedText.length > remaining) {
                file.extractedText.take(remaining) + "\n... [truncated] ..."
            } else {
                file.extractedText
            }
            sb.append("--- File: ${file.displayName} (${file.lineCount} lines) ---\n")
            sb.append("```$ext\n")
            sb.append(body)
            if (!body.endsWith("\n")) sb.append("\n")
            sb.append("```\n\n")
            remaining -= body.length
        }
        return sb.toString().trim()
    }

    private fun decodeHtml(input: String): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(input, Html.FROM_HTML_MODE_LEGACY).toString()
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(input).toString()
        }
    }
}

// ===========================================================================
// 3. Real-Time Web Search & URL Fetch Skill (Function Calling + Fallback)
// ===========================================================================
object OnlineWebSearchTool {

    fun buildToolDefinitions(): JSONArray {
        val webSearchTool = JSONObject()
            .put("type", "function")
            .put(
                "function",
                JSONObject()
                    .put("name", "web_search")
                    .put(
                        "description",
                        "Search the live internet for up-to-date facts, current news, prices, weather, release versions, or recent events."
                    )
                    .put(
                        "parameters",
                        JSONObject()
                            .put("type", "object")
                            .put(
                                "properties",
                                JSONObject().put(
                                    "query",
                                    JSONObject()
                                        .put("type", "string")
                                        .put("description", "Search query keywords in Thai or English")
                                )
                            )
                            .put("required", JSONArray().put("query"))
                    )
            )

        val webFetchTool = JSONObject()
            .put("type", "function")
            .put(
                "function",
                JSONObject()
                    .put("name", "web_fetch")
                    .put("description", "Fetch and read readable text content from a specific http or https URL.")
                    .put(
                        "parameters",
                        JSONObject()
                            .put("type", "object")
                            .put(
                                "properties",
                                JSONObject().put(
                                    "url",
                                    JSONObject()
                                        .put("type", "string")
                                        .put("description", "The full URL starting with http:// or https://")
                                )
                            )
                            .put("required", JSONArray().put("url"))
                    )
            )

        return JSONArray().put(webSearchTool).put(webFetchTool)
    }

    fun shouldAutoSearchFallback(userText: String): Boolean {
        val q = userText.trim().lowercase(Locale.US)
        if (q.startsWith("/search ") || q.startsWith("ค้นหา:") || q.startsWith("ค้นหา ")) return true
        val triggers = listOf(
            "วันนี้", "ล่าสุด", "ตอนนี้", "ปัจจุบัน", "ข่าว", "ราคาทอง", "ราคาหุ้น",
            "อากาศ", "พยากรณ์", "ผลบอล", "อัตราแลกเปลี่ยน", "ค่าเงิน", "2026",
            "today", "latest", "current", "news", "weather", "price", "release"
        )
        return triggers.any { q.contains(it) }
    }

    fun executeToolCall(name: String, argumentsJson: String): String {
        val args = runCatching { JSONObject(argumentsJson) }.getOrDefault(JSONObject())
        return when (name) {
            "web_search" -> {
                val query = args.optString("query", "").trim()
                if (query.isEmpty()) "Error: missing search query" else searchWeb(query)
            }
            "web_fetch" -> {
                val url = args.optString("url", "").trim()
                if (url.isEmpty()) "Error: missing url" else fetchUrlText(url)
            }
            else -> "Error: unknown tool $name"
        }
    }

    fun searchWeb(query: String): String {
        val cleanQuery = query.removePrefix("/search").removePrefix("ค้นหา:").trim()
        val htmlResults = searchDuckDuckGoHtml(cleanQuery)
        if (htmlResults.isNotBlank()) return htmlResults

        val wikiResults = searchWikipediaFallback(cleanQuery)
        if (wikiResults.isNotBlank()) return wikiResults

        return "ไม่พบผลการค้นหาที่ชัดเจนสำหรับ \"$cleanQuery\""
    }

    private fun searchDuckDuckGoHtml(query: String): String {
        return runCatching {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://html.duckduckgo.com/html/?q=$encoded")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 15_000
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; SM-A546E) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
                )
                setRequestProperty("Accept-Language", "th-TH,th;q=0.9,en-US;q=0.8,en;q=0.7")
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                conn.disconnect()
                return@runCatching ""
            }
            val html = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            conn.disconnect()

            val titleRegex = Regex(
                """<a[^>]*class="result__a"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            )
            val snippetRegex = Regex(
                """<a[^>]*class="result__snippet"[^>]*>(.*?)</a>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            )

            val titles = titleRegex.findAll(html).toList()
            val snippets = snippetRegex.findAll(html).toList()
            if (titles.isEmpty()) return@runCatching ""

            val sb = StringBuilder("Live Web Search Results for \"$query\":\n\n")
            val limit = minOf(titles.size, 6)
            for (i in 0 until limit) {
                val rawHref = titles[i].groupValues[1]
                val cleanUrl = extractRedirectUrl(rawHref)
                val title = stripTags(titles[i].groupValues[2]).trim()
                val snippet = snippets.getOrNull(i)?.groupValues?.get(1)?.let { stripTags(it).trim() }.orEmpty()
                sb.append("[${i + 1}] $title\n")
                if (cleanUrl.isNotBlank()) sb.append("URL: $cleanUrl\n")
                if (snippet.isNotBlank()) sb.append("Summary: $snippet\n")
                sb.append("\n")
            }
            sb.toString().trim()
        }.getOrDefault("")
    }

    private fun searchWikipediaFallback(query: String): String {
        val langs = listOf("th", "en")
        val sb = StringBuilder()
        for (lang in langs) {
            runCatching {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val api = URL("https://$lang.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&format=json&srlimit=3")
                val conn = (api.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8_000
                    readTimeout = 10_000
                    setRequestProperty("User-Agent", "AI-Offline-LLM-Android/1.0")
                }
                if (conn.responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val items = JSONObject(body).optJSONObject("query")?.optJSONArray("search")
                    if (items != null && items.length() > 0) {
                        for (i in 0 until items.length()) {
                            val item = items.optJSONObject(i) ?: continue
                            val title = item.optString("title")
                            val snippet = stripTags(item.optString("snippet"))
                            sb.append("- [$lang.wikipedia] $title: $snippet\n")
                        }
                    }
                }
                conn.disconnect()
            }
            if (sb.isNotEmpty()) break
        }
        return sb.toString().trim()
    }

    fun fetchUrlText(rawUrl: String): String {
        if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) {
            return "Invalid URL: $rawUrl"
        }
        return runCatching {
            val conn = (URL(rawUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = 12_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android 14) AI-Offline-LLM/1.0")
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                conn.disconnect()
                return@runCatching "HTTP $code while fetching $rawUrl"
            }
            val rawHtml = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            conn.disconnect()

            val noScripts = rawHtml
                .replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), " ")
                .replace(Regex("<style[^>]*>.*?</style>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), " ")
            val plain = stripTags(noScripts).replace(Regex("\\s+"), " ").trim()
            if (plain.length > 6000) plain.take(6000) + "... [truncated]" else plain
        }.getOrElse { "Failed to fetch $rawUrl: ${it.message}" }
    }

    private fun extractRedirectUrl(href: String): String {
        val uddgIndex = href.indexOf("uddg=")
        if (uddgIndex >= 0) {
            val encoded = href.substring(uddgIndex + 5).substringBefore('&')
            return runCatching { java.net.URLDecoder.decode(encoded, "UTF-8") }.getOrDefault(href)
        }
        return if (href.startsWith("//")) "https:$href" else href
    }

    private fun stripTags(html: String): String {
        val noTag = html.replace(Regex("<[^>]+>"), " ")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(noTag, Html.FROM_HTML_MODE_LEGACY).toString()
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(noTag).toString()
        }
    }
}

// ===========================================================================
// 4. Prompt Caching System (Local Response Cache + Server Prefix Caching)
// ===========================================================================
object OnlinePromptCache {
    private const val MAX_ENTRIES = 50
    private const val TTL_MS = 60L * 60L * 1000L // 60 minutes

    data class CacheEntry(
        val response: String,
        val timestampMs: Long,
    )

    data class CacheStats(
        val localHits: Int = 0,
        val serverCachedTokens: Long = 0L,
        val entryCount: Int = 0,
    )

    private val memoryCache = object : LinkedHashMap<String, CacheEntry>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean =
            size > MAX_ENTRIES
    }

    @Volatile
    private var localHitCount: Int = 0

    @Volatile
    private var serverCachedTokenCount: Long = 0L

    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun buildCacheKey(
        config: OnlineProviderConfig,
        systemPrompt: String,
        documentContext: String,
        historyTailHash: String,
        userText: String,
        hasImage: Boolean,
    ): String {
        val raw = listOf(
            config.provider.id,
            config.baseUrl.trim(),
            config.model.trim(),
            systemPrompt.trim(),
            documentContext.trim(),
            historyTailHash,
            userText.trim(),
            hasImage.toString(),
        ).joinToString("|")
        return sha256(raw)
    }

    @Synchronized
    fun get(key: String): String? {
        val entry = memoryCache[key] ?: return null
        if (System.currentTimeMillis() - entry.timestampMs > TTL_MS) {
            memoryCache.remove(key)
            return null
        }
        localHitCount += 1
        return entry.response
    }

    @Synchronized
    fun put(key: String, response: String) {
        if (response.isBlank()) return
        memoryCache[key] = CacheEntry(response = response, timestampMs = System.currentTimeMillis())
    }

    fun recordServerCachedTokens(tokens: Long) {
        if (tokens > 0L) {
            serverCachedTokenCount += tokens
        }
    }

    @Synchronized
    fun clear() {
        memoryCache.clear()
        localHitCount = 0
        serverCachedTokenCount = 0L
    }

    @Synchronized
    fun getStats(): CacheStats = CacheStats(
        localHits = localHitCount,
        serverCachedTokens = serverCachedTokenCount,
        entryCount = memoryCache.size,
    )
}

// ===========================================================================
// 5. Coding Precision Skill (Kotlin/Android, Bug Review & Build Error Log)
// ===========================================================================
object OnlineCodingSkill {

    enum class FocusMode {
        KOTLIN_ANDROID,
        BUG_REVIEW,
        BUILD_ERROR_LOG,
        GENERAL_CODE,
    }

    fun detectFocusMode(userText: String, hasAttachedFiles: Boolean): FocusMode? {
        val q = userText.trim()
        val lower = q.lowercase(Locale.US)

        val hasBuildErrorSignals = listOf(
            "e: file://", "unresolved reference", "type mismatch", "cannot be inferred",
            "expecting '", "unexpected tokens", "task :app:", "execution failed for task",
            "gradle", "kapt", "ksp", "dex", "r8", "proguard", "stacktrace", "exception in thread",
            "fatal exception", "no such method", "classnotfound", "error log", "บิลด์พัง", "คอมไพล์ไม่ผ่าน"
        ).any { lower.contains(it) }
        if (hasBuildErrorSignals) return FocusMode.BUILD_ERROR_LOG

        val hasReviewSignals = listOf(
            "รีวิว", "หาบั๊ก", "บั๊ก", "bug", "review", "audit", "leak", "race condition",
            "deadlock", "nullpointer", "npe", "edge case", "จุดอ่อน", "ตรวจสอบโค้ด", "วิจารณ์โค้ด"
        ).any { lower.contains(it) }
        if (hasReviewSignals) return FocusMode.BUG_REVIEW

        val hasAndroidKotlinSignals = listOf(
            "kotlin", ".kt", ".kts", "android", "compose", "@composable", "viewmodel",
            "livedata", "stateflow", "coroutine", "lifecycle", "fragment", "activity",
            "recyclerview", "manifest", "gradle.build", "retrofit", "okhttp", "room"
        ).any { lower.contains(it) }
        if (hasAndroidKotlinSignals) return FocusMode.KOTLIN_ANDROID

        val hasGeneralCodeSignals = listOf(
            "เขียนโค้ด", "แก้โค้ด", "โค้ด", "code", "function", "fun ", "class ", "python",
            ".py", "script", "refactor", "regex", "sql", "json", "xml", "git", "termux"
        ).any { lower.contains(it) }
        if (hasGeneralCodeSignals || hasAttachedFiles) return FocusMode.GENERAL_CODE

        return null
    }

    fun buildCodingPrompt(userText: String, hasAttachedFiles: Boolean): String {
        val mode = detectFocusMode(userText, hasAttachedFiles) ?: return ""
        val modeInstruction = when (mode) {
            FocusMode.KOTLIN_ANDROID -> """
Focus on Kotlin & Android development:
- Respect lifecycle awareness, main-thread safety, coroutine structured concurrency, and cancellation (`isActive`, `CancellationException`).
- Guard against nulls, index bounds, stream/bitmap/cursor leaks, and Compose recomposition pitfalls.
- Match existing symbols, signatures, and conventions from attached files or context; never invent non-existent APIs.
""".trimIndent()

            FocusMode.BUG_REVIEW -> """
Focus on Code Review & Bug Hunting:
- Prioritize real bugs, logic flaws, race conditions, nullability/type mismatches, resource leaks, and edge cases over style nitpicks.
- State the exact root cause, trigger condition, and concrete fix for each finding.
- Keep explanations concise and actionable.
""".trimIndent()

            FocusMode.BUILD_ERROR_LOG -> """
Focus on Build, Compiler & Runtime Error Log Analysis:
- Pinpoint the primary root-cause error line first (ignore cascading downstream noise when possible).
- Identify the exact file, symbol, or configuration causing the failure.
- Provide a direct, verified fix and a quick check to confirm resolution without guessing unknown facts.
""".trimIndent()

            FocusMode.GENERAL_CODE -> """
Focus on accurate, complete code generation and editing:
- Write complete, runnable code without omitting critical logic or placeholders unless explicitly requested.
- Handle edge cases and error states cleanly.
""".trimIndent()
        }

        return buildString {
            append("=== CODING PRECISION DIRECTIVES ===\n")
            append(modeInstruction)
            append("\nSelf-Check before answering:\n")
            append("- Verify exact types, imports/references, nullability, and balanced delimiters ({}, (), [], quotes).\n")
            append("- If critical code or log lines are missing, state clearly what is needed instead of guessing.")
        }
    }
}

object OnlineChatClient {

    @Volatile
    private var activeConnection: HttpURLConnection? = null

    fun cancelActiveRequest() {
        runCatching { activeConnection?.disconnect() }
        activeConnection = null
    }

    suspend fun streamChatCompletion(
        config: OnlineProviderConfig,
        systemPrompt: String,
        historyMessages: List<Message>,
        currentUserText: String,
        temperature: Float = 0.7f,
        context: Context? = null,
        currentImageFile: File? = null,
        documentContext: String = "",
        codingContext: String = "",
        enableWebSearch: Boolean = true,
        enablePromptCaching: Boolean = true,
        bypassCache: Boolean = false,
        onDelta: (accumulatedText: String) -> Unit,
    ): Result<String> = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("ยังไม่ได้กรอก API Key สำหรับ ${config.provider.displayName}")
            )
        }
        if (config.model.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("ยังไม่ได้ระบุชื่อโมเดลสำหรับ ${config.provider.displayName}")
            )
        }

        val normalizedBase = config.baseUrl.trim().trimEnd('/')
        val endpoint = if (normalizedBase.endsWith("/chat/completions")) {
            normalizedBase
        } else {
            "$normalizedBase/chat/completions"
        }

        // Prepare prior messages
        val trimmedHistory = if (historyMessages.isNotEmpty() &&
            !historyMessages.last().author.equals("User", ignoreCase = true) &&
            historyMessages.last().content.isBlank()
        ) {
            historyMessages.dropLast(1)
        } else {
            historyMessages
        }
        val priorMessages = if (trimmedHistory.isNotEmpty() &&
            trimmedHistory.last().author.equals("User", ignoreCase = true) &&
            trimmedHistory.last().content.trim() == currentUserText.trim()
        ) {
            trimmedHistory.dropLast(1)
        } else {
            trimmedHistory
        }
        val recentHistory = priorMessages.takeLast(16)

        // Check Local Prompt Cache (for deterministic text/code queries without live web search override)
        val historyHash = OnlinePromptCache.sha256(
            recentHistory.joinToString("\n") { "${it.author}:${it.content.take(200)}" }
        )
        val cacheKey = OnlinePromptCache.buildCacheKey(
            config = config,
            systemPrompt = systemPrompt,
            documentContext = listOf(documentContext, codingContext).filter { it.isNotBlank() }.joinToString("\n\n"),
            historyTailHash = historyHash,
            userText = currentUserText,
            hasImage = currentImageFile != null,
        )

        if (enablePromptCaching && !bypassCache && currentImageFile == null) {
            val cachedReply = OnlinePromptCache.get(cacheKey)
            if (!cachedReply.isNullOrBlank()) {
                withContext(Dispatchers.Main) {
                    onDelta(cachedReply)
                }
                return@withContext Result.success(cachedReply)
            }
        }

        // Build deterministic cache-friendly system prefix (Static System Prompt + Attached Files)
        val combinedSystem = buildString {
            if (systemPrompt.isNotBlank()) {
                append(systemPrompt.trim())
            }
            if (codingContext.isNotBlank()) {
                if (isNotEmpty()) append("\n\n")
                append(codingContext.trim())
            }
            if (documentContext.isNotBlank()) {
                if (isNotEmpty()) append("\n\n")
                append(documentContext.trim())
            }
        }

        val isClaudeModel = config.model.contains("claude", ignoreCase = true)

        fun buildBaseMessages(useEphemeralCacheBlock: Boolean): JSONArray {
            val arr = JSONArray()
            if (combinedSystem.isNotBlank()) {
                if (useEphemeralCacheBlock && combinedSystem.length >= 1024) {
                    val block = JSONObject()
                        .put("type", "text")
                        .put("text", combinedSystem)
                        .put("cache_control", JSONObject().put("type", "ephemeral"))
                    arr.put(
                        JSONObject()
                            .put("role", "system")
                            .put("content", JSONArray().put(block))
                    )
                } else {
                    arr.put(
                        JSONObject()
                            .put("role", "system")
                            .put("content", combinedSystem)
                    )
                }
            }

            // Include up to 2 recent historical images so vision follow-ups stay fast
            val recentImagePaths = recentHistory
                .mapNotNull { it.imagePath }
                .takeLast(2)
                .toSet()

            for (msg in recentHistory) {
                val content = msg.content.trim()
                val imgFile = msg.imagePath?.takeIf { it in recentImagePaths }?.let { File(it) }
                if (content.isEmpty() && (imgFile == null || !imgFile.exists())) continue
                if (content.startsWith("[[IMG_GEN]]")) continue
                val role = if (msg.author.equals("User", ignoreCase = true)) "user" else "assistant"

                val histDataUrl = if (role == "user") OnlineImageEncoder.encodeImageFileToDataUrl(imgFile) else null
                if (histDataUrl != null) {
                    val parts = JSONArray()
                    if (content.isNotEmpty()) {
                        parts.put(JSONObject().put("type", "text").put("text", content))
                    }
                    parts.put(
                        JSONObject()
                            .put("type", "image_url")
                            .put("image_url", JSONObject().put("url", histDataUrl))
                    )
                    arr.put(JSONObject().put("role", role).put("content", parts))
                } else {
                    arr.put(JSONObject().put("role", role).put("content", content))
                }
            }

            // Current user turn (Text + Vision Image if attached)
            val currentImageDataUrl = OnlineImageEncoder.encodeImageFileToDataUrl(currentImageFile)
            if (currentImageDataUrl != null) {
                val parts = JSONArray()
                val promptText = currentUserText.trim().ifEmpty { "ช่วยวิเคราะห์รูปภาพนี้โดยละเอียด" }
                parts.put(JSONObject().put("type", "text").put("text", promptText))
                parts.put(
                    JSONObject()
                        .put("type", "image_url")
                        .put(
                            "image_url",
                            JSONObject()
                                .put("url", currentImageDataUrl)
                                .put("detail", "auto")
                        )
                )
                arr.put(JSONObject().put("role", "user").put("content", parts))
            } else {
                arr.put(JSONObject().put("role", "user").put("content", currentUserText))
            }
            return arr
        }

        var useEphemeralBlock = enablePromptCaching && isClaudeModel
        var useTools = enableWebSearch
        var messagesArray = buildBaseMessages(useEphemeralBlock)
        val toolsDefinition = if (useTools) OnlineWebSearchTool.buildToolDefinitions() else null

        val maxToolRounds = 3
        var round = 0
        var finalAccumulated = ""

        while (round <= maxToolRounds && isActive) {
            val payload = JSONObject()
                .put("model", config.model.trim())
                .put("messages", messagesArray)
                .put("stream", true)

            if (useTools && toolsDefinition != null) {
                payload.put("tools", toolsDefinition)
                payload.put("tool_choice", "auto")
            }

            val stepResult = executeSingleStreamRound(
                endpoint = endpoint,
                config = config,
                payload = payload,
                prefixText = finalAccumulated,
                onDelta = onDelta,
            )

            if (stepResult.isFailure) {
                val err = stepResult.exceptionOrNull()
                val msg = err?.message.orEmpty()
                // Graceful fallback if proxy rejects ephemeral cache_control array or tools parameter
                if (round == 0 && (msg.contains("(400)") || msg.contains("(422)"))) {
                    if (useEphemeralBlock) {
                        useEphemeralBlock = false
                        messagesArray = buildBaseMessages(false)
                        continue
                    }
                    if (useTools) {
                        useTools = false
                        messagesArray = buildBaseMessages(false)
                        // If user explicitly asked for current web info, inject fallback search context
                        if (OnlineWebSearchTool.shouldAutoSearchFallback(currentUserText)) {
                            withContext(Dispatchers.Main) {
                                onDelta("🌐 กำลังค้นหาข้อมูลปัจจุบัน...\n\n")
                            }
                            val searchSummary = OnlineWebSearchTool.searchWeb(currentUserText)
                            val lastIdx = messagesArray.length() - 1
                            val lastUserObj = messagesArray.optJSONObject(lastIdx)
                            if (lastUserObj != null && lastUserObj.opt("content") is String) {
                                val orig = lastUserObj.optString("content")
                                lastUserObj.put(
                                    "content",
                                    "$orig\n\n[ข้อมูลจากการค้นหาเว็บล่าสุด]\n$searchSummary"
                                )
                            }
                        }
                        continue
                    }
                }
                return@withContext Result.failure(err ?: IllegalStateException("เกิดข้อผิดพลาดในการเรียก API"))
            }

            val roundOutput = stepResult.getOrThrow()

            // If model invoked tool_calls (web_search / web_fetch), execute them and continue loop
            if (roundOutput.toolCalls.isNotEmpty() && round < maxToolRounds) {
                val assistantToolMsg = JSONObject()
                    .put("role", "assistant")
                    .put("content", if (roundOutput.text.isBlank()) JSONObject.NULL else roundOutput.text)
                val tcArray = JSONArray()
                for (tc in roundOutput.toolCalls) {
                    tcArray.put(
                        JSONObject()
                            .put("id", tc.id)
                            .put("type", "function")
                            .put(
                                "function",
                                JSONObject()
                                    .put("name", tc.name)
                                    .put("arguments", tc.arguments)
                            )
                    )
                }
                assistantToolMsg.put("tool_calls", tcArray)
                messagesArray.put(assistantToolMsg)

                for (tc in roundOutput.toolCalls) {
                    val queryPreview = runCatching {
                        val obj = JSONObject(tc.arguments)
                        obj.optString("query").ifBlank { obj.optString("url") }
                    }.getOrDefault("").take(60)
                    val statusLine = if (tc.name == "web_fetch") {
                        "🌐 กำลังอ่านหน้าเว็บ: $queryPreview...\n\n"
                    } else {
                        "🌐 กำลังค้นหาข้อมูล: \"$queryPreview\"...\n\n"
                    }
                    withContext(Dispatchers.Main) {
                        onDelta(finalAccumulated + statusLine)
                    }

                    val toolOutput = OnlineWebSearchTool.executeToolCall(tc.name, tc.arguments)
                    messagesArray.put(
                        JSONObject()
                            .put("role", "tool")
                            .put("tool_call_id", tc.id)
                            .put("name", tc.name)
                            .put("content", toolOutput)
                    )
                }
                round += 1
                continue
            }

            finalAccumulated += roundOutput.text
            break
        }

        val finalText = finalAccumulated.trim()
        if (finalText.isBlank()) {
            Result.failure(IllegalStateException("ไม่ได้รับข้อความตอบกลับจาก ${config.provider.displayName}"))
        } else {
            if (enablePromptCaching && currentImageFile == null) {
                OnlinePromptCache.put(cacheKey, finalText)
            }
            Result.success(finalText)
        }
    }

    private data class AccumulatedToolCall(
        var id: String = "call_${UUID.randomUUID().toString().take(8)}",
        var name: String = "",
        val argumentsBuilder: StringBuilder = StringBuilder(),
    ) {
        val arguments: String get() = argumentsBuilder.toString().ifBlank { "{}" }
    }

    private data class RoundStreamResult(
        val text: String,
        val toolCalls: List<AccumulatedToolCall>,
    )

    private suspend fun executeSingleStreamRound(
        endpoint: String,
        config: OnlineProviderConfig,
        payload: JSONObject,
        prefixText: String,
        onDelta: (accumulatedText: String) -> Unit,
    ): Result<RoundStreamResult> {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(endpoint)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 25_000
                readTimeout = 90_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "text/event-stream")
                setRequestProperty("Authorization", "Bearer ${config.apiKey.trim()}")
                if (config.provider == OnlineProvider.OPENROUTER) {
                    setRequestProperty("X-Title", "AI Offline LLM")
                }
            }
            activeConnection = conn

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val code = conn.responseCode
            if (code !in 200..299) {
                val errBody = runCatching {
                    conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                }.getOrDefault("")
                val friendly = formatHttpError(code, errBody, config.provider.displayName)
                return Result.failure(IllegalStateException(friendly))
            }

            val accumulated = StringBuilder()
            val rawLines = StringBuilder()
            val toolCallMap = LinkedHashMap<Int, AccumulatedToolCall>()

            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                while (kotlin.coroutines.coroutineContext.isActive) {
                    val line = reader.readLine() ?: break
                    rawLines.appendLine(line)
                    if (!line.startsWith("data:")) continue
                    val dataStr = line.removePrefix("data:").trim()
                    if (dataStr.isEmpty()) continue
                    if (dataStr == "[DONE]") break

                    val chunkJson = runCatching { JSONObject(dataStr) }.getOrNull() ?: continue

                    // Track server-side prompt cache hits if reported in usage object
                    chunkJson.optJSONObject("usage")?.let { usage ->
                        val cachedDetails = usage.optJSONObject("prompt_tokens_details")?.optLong("cached_tokens", 0L) ?: 0L
                        val anthropicRead = usage.optLong("cache_read_input_tokens", 0L)
                        OnlinePromptCache.recordServerCachedTokens(maxOf(cachedDetails, anthropicRead))
                    }

                    val streamErr = chunkJson.optJSONObject("error")?.optString("message").orEmpty()
                    if (streamErr.isNotBlank()) {
                        return Result.failure(IllegalStateException("${config.provider.displayName} แจ้งข้อผิดพลาด: $streamErr"))
                    }
                    val choices = chunkJson.optJSONArray("choices") ?: continue
                    if (choices.length() == 0) continue
                    val firstChoice = choices.optJSONObject(0) ?: continue
                    val deltaObj = firstChoice.optJSONObject("delta")
                    val msgObj = firstChoice.optJSONObject("message")

                    // Parse streaming tool_calls deltas
                    val deltaTools = deltaObj?.optJSONArray("tool_calls") ?: msgObj?.optJSONArray("tool_calls")
                    if (deltaTools != null) {
                        for (i in 0 until deltaTools.length()) {
                            val tcObj = deltaTools.optJSONObject(i) ?: continue
                            val idx = tcObj.optInt("index", i)
                            val entry = toolCallMap.getOrPut(idx) { AccumulatedToolCall() }
                            val tcId = tcObj.optString("id", "")
                            if (tcId.isNotBlank()) entry.id = tcId
                            tcObj.optJSONObject("function")?.let { fn ->
                                val fnName = fn.optString("name", "")
                                if (fnName.isNotBlank()) entry.name += fnName
                                val fnArgs = fn.optString("arguments", "")
                                if (fnArgs.isNotEmpty()) entry.argumentsBuilder.append(fnArgs)
                            }
                        }
                    }

                    val deltaText = when {
                        deltaObj != null && deltaObj.has("content") && !deltaObj.isNull("content") ->
                            deltaObj.optString("content", "")
                        msgObj != null && msgObj.has("content") && !msgObj.isNull("content") ->
                            msgObj.optString("content", "")
                        else -> ""
                    }
                    if (deltaText.isNotEmpty() && deltaText != "null") {
                        accumulated.append(deltaText)
                        withContext(Dispatchers.Main) {
                            onDelta(prefixText + accumulated.toString())
                        }
                    }
                }
            }

            if (accumulated.isBlank() && toolCallMap.isEmpty()) {
                val fallbackJson = runCatching { JSONObject(rawLines.toString().trim()) }.getOrNull()
                val fallbackMsg = fallbackJson
                    ?.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                val fallbackContent = fallbackMsg?.optString("content", "").orEmpty()
                if (fallbackContent.isNotBlank() && fallbackContent != "null") {
                    accumulated.append(fallbackContent)
                    withContext(Dispatchers.Main) {
                        onDelta(prefixText + accumulated.toString())
                    }
                }
                val fallbackTools = fallbackMsg?.optJSONArray("tool_calls")
                if (fallbackTools != null) {
                    for (i in 0 until fallbackTools.length()) {
                        val tcObj = fallbackTools.optJSONObject(i) ?: continue
                        val entry = AccumulatedToolCall()
                        val tcId = tcObj.optString("id", "")
                        if (tcId.isNotBlank()) entry.id = tcId
                        tcObj.optJSONObject("function")?.let { fn ->
                            entry.name = fn.optString("name", "")
                            entry.argumentsBuilder.append(fn.optString("arguments", ""))
                        }
                        if (entry.name.isNotBlank()) toolCallMap[i] = entry
                    }
                }
            }

            val validTools = toolCallMap.values.filter { it.name.isNotBlank() }
            Result.success(RoundStreamResult(text = accumulated.toString(), toolCalls = validTools))
        } catch (t: Throwable) {
            if (!kotlin.coroutines.coroutineContext.isActive || activeConnection == null) {
                Result.failure(kotlinx.coroutines.CancellationException("ยกเลิกคำขอออนไลน์แล้ว"))
            } else {
                Result.failure(
                    IllegalStateException("เชื่อมต่อ ${config.provider.displayName} ไม่สำเร็จ: ${t.message ?: t::class.java.simpleName}")
                )
            }
        } finally {
            if (activeConnection === conn) {
                activeConnection = null
            }
            runCatching { conn?.disconnect() }
        }
    }

    private fun formatHttpError(code: Int, rawBody: String, providerName: String): String {
        val parsedMsg = runCatching {
            val obj = JSONObject(rawBody)
            obj.optJSONObject("error")?.optString("message").orEmpty()
        }.getOrDefault("").ifBlank { rawBody.take(180) }

        return when (code) {
            401, 403 -> "API Key ของ $providerName ไม่ถูกต้องหรือไม่มีสิทธิ์ใช้งาน ($code)"
            402 -> "เครดิตของ $providerName ไม่เพียงพอ ($code) — แนะนำเปลี่ยนโมเดลเป็น openrouter/free"
            404 -> "ไม่พบโมเดลหรือ Endpoint ของ $providerName ($code): $parsedMsg"
            429 -> "เกินโควตาหรืออัตราการเรียกใช้งานของ $providerName ($code)"
            in 500..599 -> "เซิร์ฟเวอร์ของ $providerName ขัดข้องชั่วคราว ($code)"
            else -> "$providerName แจ้งข้อผิดพลาด ($code): $parsedMsg"
        }
    }
}
