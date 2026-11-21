package com.druk.ai_offline_llm.tools

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.druk.llamacpp.tools.Tool
import org.json.JSONObject

class AlarmTimerTool(private val context: Context) : Tool {
    override val name = "set_alarm_or_timer"
    override val description = "Set an alarm at hour/minute or start a countdown timer in seconds on the device."
    override val parametersSchema = """{"type":"object","properties":{"mode":{"type":"string","enum":["alarm","timer"]},"hour":{"type":"integer"},"minute":{"type":"integer"},"duration_seconds":{"type":"integer"},"label":{"type":"string"}},"required":["mode"]}"""

    override fun execute(arguments: String): String {
        return try {
            val args = JSONObject(arguments)
            val mode = args.getString("mode").lowercase()
            val label = args.optString("label", "Jarvis")
            if (mode == "alarm") {
                val hour = args.optInt("hour", -1)
                val minute = args.optInt("minute", 0)
                if (hour !in 0..23 || minute !in 0..59) return """{"error":"Invalid time"}"""
                context.startActivity(Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    putExtra(AlarmClock.EXTRA_MESSAGE, label)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                """{"status":"success","mode":"alarm","time":"${String.format("%02d:%02d", hour, minute)}"}"""
            } else {
                val sec = args.optInt("duration_seconds", 0)
                if (sec <= 0) return """{"error":"Invalid duration"}"""
                context.startActivity(Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_LENGTH, sec)
                    putExtra(AlarmClock.EXTRA_MESSAGE, label)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                """{"status":"success","mode":"timer","duration_seconds":$sec}"""
            }
        } catch (e: Exception) {
            """{"error":"${(e.message ?: "Clock unavailable").replace(""", "'")}"}"""
        }
    }
}
