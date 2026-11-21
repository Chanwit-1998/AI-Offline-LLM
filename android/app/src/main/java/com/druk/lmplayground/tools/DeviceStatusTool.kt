package com.druk.ai_offline_llm.tools

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import com.druk.llamacpp.tools.Tool
import org.json.JSONObject

class DeviceStatusTool(private val context: Context) : Tool {
    override val name = "get_device_status"
    override val description = "Get current device status including battery percentage, charging state, available storage space, and network connection type."
    override val parametersSchema = """{"type":"object","properties":{}}"""

    override fun execute(arguments: String): String {
        return try {
            val bIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = bIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = bIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val pct = if (level >= 0 && scale > 0) (level * 100) / scale else -1
            val status = bIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

            val stat = StatFs(Environment.getDataDirectory().path)
            val freeGb = String.format("%.2f", (stat.availableBlocksLong * stat.blockSizeLong) / 1073741824.0)
            val totalGb = String.format("%.2f", (stat.blockCountLong * stat.blockSizeLong) / 1073741824.0)

            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
            val net = when {
                caps == null -> "Offline"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile Data"
                else -> "Connected"
            }
            JSONObject().apply {
                put("battery_percent", pct)
                put("is_charging", charging)
                put("storage_free_gb", freeGb)
                put("storage_total_gb", totalGb)
                put("network_status", net)
            }.toString()
        } catch (e: Exception) {
            val err = (e.message ?: "Status error").replace("\"", "'")
            """{"error":"$err"}"""
        }
    }
}
