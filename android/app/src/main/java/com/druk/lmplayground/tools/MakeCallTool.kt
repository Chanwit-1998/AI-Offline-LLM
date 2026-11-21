package com.druk.ai_offline_llm.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.druk.llamacpp.tools.Tool
import org.json.JSONObject

class MakeCallTool(private val context: Context) : Tool {
    override val name = "make_call"
    override val description = "Open the phone dialer with a target phone number ready to place a call offline."
    override val parametersSchema = """{"type":"object","properties":{"phone_number":{"type":"string","description":"Phone number or contact number to dial."}},"required":["phone_number"]}"""

    override fun execute(arguments: String): String {
        return try {
            val raw = JSONObject(arguments).optString("phone_number", "").trim()
            val clean = raw.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
            if (clean.isEmpty()) return """{"error":"Invalid phone number"}"""
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            """{"status":"dialer_opened","phone_number":"$clean"}"""
        } catch (e: Exception) {
            val msg = (e.message ?: "Dialer error").replace("\"", "'")
            """{"error":"$msg"}"""
        }
    }
}
