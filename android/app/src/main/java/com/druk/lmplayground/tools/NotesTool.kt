package com.druk.ai_offline_llm.tools

import android.content.Context
import com.druk.llamacpp.tools.Tool
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class NotesTool(context: Context) : Tool {
    private val prefs = context.getSharedPreferences("offline_assistant_notes", Context.MODE_PRIVATE)
    override val name = "manage_notes"
    override val description = "Save, list, search, or delete personal offline notes stored privately on this device."
    override val parametersSchema = """{"type":"object","properties":{"action":{"type":"string","enum":["add","list","delete","clear"]},"content":{"type":"string"},"index":{"type":"integer"}},"required":["action"]}"""

    override fun execute(arguments: String): String {
        return try {
            val args = JSONObject(arguments)
            val action = args.getString("action").lowercase()
            val arr = JSONArray(prefs.getString("notes_json", "[]") ?: "[]")
            when (action) {
                "add" -> {
                    val content = args.optString("content", "").trim()
                    if (content.isEmpty()) return """{"error":"Empty note"}"""
                    arr.put(JSONObject().apply {
                        put("text", content)
                        put("created_at", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                    })
                    prefs.edit().putString("notes_json", arr.toString()).apply()
                    """{"status":"saved","total":${arr.length()}}"""
                }
                "list" -> {
                    val q = args.optString("content", "").trim().lowercase()
                    val out = JSONArray()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val t = obj.optString("text")
                        if (q.isEmpty() || t.lowercase().contains(q)) {
                            out.put(JSONObject().put("index", i + 1).put("text", t).put("created_at", obj.optString("created_at")))
                        }
                    }
                    JSONObject().put("count", out.length()).put("notes", out).toString()
                }
                "delete" -> {
                    val idx = args.optInt("index", -1) - 1
                    if (idx !in 0 until arr.length()) return """{"error":"Invalid index"}"""
                    val next = JSONArray()
                    for (i in 0 until arr.length()) if (i != idx) next.put(arr.get(i))
                    prefs.edit().putString("notes_json", next.toString()).apply()
                    """{"status":"deleted","remaining":${next.length()}}"""
                }
                "clear" -> {
                    prefs.edit().putString("notes_json", "[]").apply()
                    """{"status":"cleared"}"""
                }
                else -> """{"error":"Unknown action"}"""
            }
        } catch (e: Exception) {
            val msg = (e.message ?: "Notes error").replace("\"", "'")
            """{"error":"$msg"}"""
        }
    }
}
