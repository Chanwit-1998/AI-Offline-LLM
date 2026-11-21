package com.druk.ai_offline_llm.tools

import com.druk.llamacpp.tools.Tool
import org.json.JSONObject
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class DateTimeTool : Tool {
    override val name = "get_current_datetime"
    override val description = "Get exact current local date, time, day of week, Buddhist Era (B.E.) year, and timezone."
    override val parametersSchema = """{"type":"object","properties":{}}"""

    override fun execute(arguments: String): String {
        return try {
            val now = ZonedDateTime.now(ZoneId.systemDefault())
            val beYear = now.year + 543
            val thFmt = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale("th", "TH"))
            JSONObject().apply {
                put("iso_datetime", now.toOffsetDateTime().toString())
                put("date_thai", "${now.format(thFmt)} พ.ศ. $beYear")
                put("time_24h", now.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
                put("day_of_week", now.dayOfWeek.name)
                put("timezone", now.zone.id)
            }.toString()
        } catch (e: Exception) {
            JSONObject().put("error", e.message ?: "Time error").toString()
        }
    }
}
