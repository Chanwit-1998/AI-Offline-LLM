package com.druk.ai_offline_llm.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.druk.llamacpp.tools.Tool
import org.json.JSONArray
import org.json.JSONObject

class ContactsTool(private val context: Context) : Tool {
    override val name = "search_contacts"
    override val description = "Search saved phone contacts by name to look up phone numbers offline."
    override val parametersSchema = """{"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}"""

    override fun execute(arguments: String): String {
        return try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                return """{"error":"READ_CONTACTS permission not granted"}"""
            }
            val query = JSONObject(arguments).getString("name").trim()
            val results = JSONArray()
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$query%"),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { c ->
                val nIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val pIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (c.moveToNext() && results.length() < 10) {
                    val dName = if (nIdx >= 0) c.getString(nIdx) else ""
                    val phone = if (pIdx >= 0) c.getString(pIdx) else ""
                    if (!dName.isNullOrBlank()) results.put(JSONObject().put("name", dName).put("phone", phone ?: ""))
                }
            }
            JSONObject().put("query", query).put("matches", results).toString()
        } catch (e: Exception) {
            """{"error":"${(e.message ?: "Contacts error").replace(""", "'")}"}"""
        }
    }
}
