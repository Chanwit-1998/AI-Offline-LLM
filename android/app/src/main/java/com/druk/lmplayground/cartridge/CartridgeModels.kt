package com.druk.ai_offline_llm.cartridge

import org.json.JSONArray
import org.json.JSONObject

data class AiCartridge(
    val id: String,
    val name: String,
    val version: String = "v1.0",
    val author: String = "Chanwit™",
    val category: String = "ทั่วไป",
    val colorHex: Long = 0xFF00BFA5,
    val description: String,
    val systemPrompt: String,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val isCustom: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("version", version)
        put("author", author); put("category", category); put("colorHex", colorHex)
        put("description", description); put("systemPrompt", systemPrompt)
        put("temperature", temperature.toDouble()); put("topP", topP.toDouble())
        put("isCustom", isCustom)
    }

    companion object {
        fun fromJson(obj: JSONObject) = AiCartridge(
            id = obj.optString("id", "cart_${System.currentTimeMillis()}"),
            name = obj.optString("name", "ตลับไม่ระบุชื่อ"),
            version = obj.optString("version", "v1.0"),
            author = obj.optString("author", "Community"),
            category = obj.optString("category", "ทั่วไป"),
            colorHex = obj.optLong("colorHex", 0xFF00BFA5),
            description = obj.optString("description", ""),
            systemPrompt = obj.optString("systemPrompt", ""),
            temperature = obj.optDouble("temperature", 0.7).toFloat(),
            topP = obj.optDouble("topP", 0.9).toFloat(),
            isCustom = obj.optBoolean("isCustom", true)
        )

        fun listToJsonString(list: List<AiCartridge>): String =
            JSONArray().apply { list.forEach { put(it.toJson()) } }.toString(2)

        fun listFromJsonString(raw: String): List<AiCartridge> =
            if (raw.isBlank()) emptyList()
            else runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            }.getOrDefault(emptyList())
    }
}
