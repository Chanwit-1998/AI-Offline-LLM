package com.druk.ai_offline_llm.cartridge

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONObject
import java.io.File

object CartridgeManager {
    private const val PREFS_NAME = "ai_cartridge_prefs"
    private const val KEY_ACTIVE_ID = "active_cartridge_id"
    private const val KEY_FOLDER_URI = "custom_cartridge_folder_uri"
    private const val DIR_NAME = "cartridges"
    private const val FILE_INSTALLED = "installed_cartridges.json"

    val storeCatalog: List<AiCartridge> = listOf(
        AiCartridge(
            id = "cart_termux_dev",
            name = "ตลับวิศวกรโค้ด & Termux Pro",
            version = "v1.2",
            category = "เขียนโค้ด",
            colorHex = 0xFF00BFA5,
            description = "จูนค่าความแม่นยำสูง (Temp 0.2) สำหรับเขียนสคริปต์ Termux, Kotlin, Android และแก้บั๊กตรงประเด็น",
            systemPrompt = "คุณคือวิศวกรซอฟต์แวร์ระดับอาวุโสและผู้เชี่ยวชาญ Linux/Termux และ Android Kotlin ตอบกระชับ ชัดเจน ให้โค้ดที่พร้อมรันได้จริงโดยไม่เยิ่นเย้อ",
            temperature = 0.2f,
            topP = 0.85f
        ),
        AiCartridge(
            id = "cart_rpg_story",
            name = "ตลับเกมสวมบทบาท RPG แฟนตาซี",
            category = "เกม & นิยาย",
            colorHex = 0xFF7C4DFF,
            description = "เปิดโหมดจินตนาการสูง (Temp 0.85) สร้างโลกผจญภัย ภารกิจ และทางเลือกให้ผู้เล่นตัดสินใจทีละฉาก",
            systemPrompt = "คุณคือ Game Master เกม RPG แฟนตาซี บรรยายฉากอย่างมีชีวิตชีวา สร้างสถานการณ์น่าตื่นเต้น และจบแต่ละข้อความด้วยตัวเลือก 3 ข้อให้ผู้เล่นเลือก",
            temperature = 0.85f,
            topP = 0.95f
        ),
        AiCartridge(
            id = "cart_translator_pro",
            name = "ตลับล่ามแปลภาษา ไทย-อังกฤษ-ญี่ปุ่น",
            version = "v1.1",
            category = "ภาษา",
            colorHex = 0xFFFF9100,
            description = "แปลภาษาเป็นธรรมชาติระดับเจ้าของภาษา พร้อมอธิบายศัพท์แสลงและบริบททางวัฒนธรรม",
            systemPrompt = "คุณคือล่ามและนักแปลภาษามืออาชีพ แปลข้อความที่ได้รับให้สละสลวยเป็นธรรมชาติที่สุด หากเป็นคำศัพท์เฉพาะหรือเกม ให้รักษาชื่อเฉพาะและอธิบายความหมายเพิ่มเติมสั้นๆ",
            temperature = 0.35f,
            topP = 0.9f
        ),
        AiCartridge(
            id = "cart_fast_assistant",
            name = "ตลับผู้ช่วยสรุปไว & ตอบตรงประเด็น",
            category = "เครื่องมือ",
            colorHex = 0xFF2979FF,
            description = "ตัดน้ำออกให้หมด สรุปใจความสำคัญเป็นข้อๆ อ่านจบใน 5 วินาที ประหยัดเวลาและ Token",
            systemPrompt = "คุณคือผู้ช่วยอัจฉริยะที่ตอบคำถามสั้น กระชับ ตรงประเด็นที่สุด ใช้หัวข้อย่อย (Bullet points) และตัวหนาเน้นคำสำคัญ ห้ามอธิบายยืดยาว",
            temperature = 0.4f,
            topP = 0.88f
        ),
        AiCartridge(
            id = "cart_game_guide",
            name = "ตลับคัมภีร์สูตรเกม & Emulator",
            category = "เกม & นิยาย",
            colorHex = 0xFFFF1744,
            description = "ผู้เชี่ยวชาญบทสรุปเกมคลาสสิก PSP, PS2, GameCube การตั้งค่า Emulator และเทคนิคผ่านด่าน",
            systemPrompt = "คุณคือผู้เชี่ยวชาญเกมคอนโซลคลาสสิก (PSP, PS2, GameCube) และระบบ Emulator แนะนำวิธีผ่านด่าน จุดเก็บไอเทมลับ และการตั้งค่าให้ลื่นไหลบนมือถือ",
            temperature = 0.5f,
            topP = 0.9f
        )
    )

    private fun getInstalledFile(context: Context): File =
        File(File(context.filesDir, DIR_NAME).apply { mkdirs() }, FILE_INSTALLED)

    fun getCartridgeFolderPath(context: Context): String {
        val rawUri = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_FOLDER_URI, null)
        if (rawUri.isNullOrBlank()) return "โฟลเดอร์แอป/cartridges"
        val decoded = Uri.decode(rawUri)
        val segment = decoded.substringAfterLast("primary:", decoded.substringAfterLast("/"))
        return if (segment.isNotBlank()) "Internal Storage/$segment" else decoded
    }

    fun setCustomCartridgeFolderUri(context: Context, uri: Uri): Int {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_FOLDER_URI, uri.toString()).apply()
        val tree = DocumentFile.fromTreeUri(context, uri) ?: return 0
        var importedCount = 0
        tree.listFiles().forEach { doc ->
            if (doc.isFile && (doc.name?.endsWith(".json", ignoreCase = true) == true)) {
                if (importFromUri(context, doc.uri).isSuccess) importedCount++
            }
        }
        return importedCount
    }

    fun getInstalledCartridges(context: Context): List<AiCartridge> {
        val file = getInstalledFile(context)
        if (!file.exists()) {
            val defaults = storeCatalog.take(2)
            saveInstalledCartridges(context, defaults)
            return defaults
        }
        return AiCartridge.listFromJsonString(file.readText(Charsets.UTF_8))
    }

    fun saveInstalledCartridges(context: Context, list: List<AiCartridge>) {
        getInstalledFile(context).writeText(AiCartridge.listToJsonString(list), Charsets.UTF_8)
    }

    fun installCartridge(context: Context, cartridge: AiCartridge): List<AiCartridge> {
        val current = getInstalledCartridges(context).toMutableList()
        val idx = current.indexOfFirst { it.id == cartridge.id }
        if (idx >= 0) current[idx] = cartridge else current.add(0, cartridge)
        saveInstalledCartridges(context, current)
        return current
    }

    fun removeInstalledCartridge(context: Context, cartridgeId: String): List<AiCartridge> {
        val updated = getInstalledCartridges(context).filterNot { it.id == cartridgeId }
        saveInstalledCartridges(context, updated)
        if (getActiveCartridgeId(context) == cartridgeId) setActiveCartridgeId(context, null)
        return updated
    }

    fun getAllAvailableCartridges(context: Context): List<AiCartridge> {
        val map = LinkedHashMap<String, AiCartridge>()
        getInstalledCartridges(context).forEach { map[it.id] = it }
        storeCatalog.forEach { if (!map.containsKey(it.id)) map[it.id] = it }
        return map.values.toList()
    }

    fun getActiveCartridgeId(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_ACTIVE_ID, null)

    fun setActiveCartridgeId(context: Context, cartridgeId: String?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_ACTIVE_ID, cartridgeId).apply()
    }

    fun getActiveCartridge(context: Context): AiCartridge? =
        getActiveCartridgeId(context)?.let { id -> getAllAvailableCartridges(context).find { it.id == id } }

    fun importFromUri(context: Context, uri: Uri): Result<AiCartridge> = runCatching {
        val raw = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: error("ไม่สามารถอ่านไฟล์ตลับได้")
        val json = JSONObject(raw)
        val imported = AiCartridge.fromJson(json).copy(
            id = json.optString("id").ifBlank { "custom_${System.currentTimeMillis()}" },
            isCustom = true
        )
        installCartridge(context, imported)
        imported
    }
}
