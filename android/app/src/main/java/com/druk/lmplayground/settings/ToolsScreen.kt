@file:OptIn(ExperimentalMaterial3Api::class)

package com.druk.ai_offline_llm.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.druk.ai_offline_llm.R
import com.druk.llamacpp.tools.Tool

private data class ToolUiSpec(val title: String, val desc: String, val example: String, val icon: ImageVector)

private val TOOL_UI_SPECS = mapOf(
    "get_device_status" to ToolUiSpec("สถานะเครื่อง & แบตเตอรี่", "เช็กแบตเตอรี่ การชาร์จ พื้นที่ว่าง และสถานะเน็ตแบบออฟไลน์", "\"แบตเหลือเท่าไหร่ พื้นที่เครื่องพอไหม\"", Icons.Outlined.BatteryChargingFull),
    "get_current_datetime" to ToolUiSpec("วันและเวลาปัจจุบัน", "อ่านเวลา วันที่ วันในสัปดาห์ และปี พ.ศ. ปัจจุบัน", "\"ตอนนี้กี่โมงแล้ว วันนี้วันที่เท่าไหร่\"", Icons.Outlined.AccessTime),
    "set_alarm_or_timer" to ToolUiSpec("ตั้งปลุก & นับเวลาถอยหลัง", "ตั้งนาฬิกาปลุกหรือจับเวลาถอยหลังผ่านแอปนาฬิกาทันที", "\"ตั้งปลุก 06:30 น.\" หรือ \"จับเวลา 5 นาที\"", Icons.Outlined.Alarm),
    "manage_notes" to ToolUiSpec("สมุดโน้ตส่วนตัวออฟไลน์", "บันทึก ค้นหา และลบโน้ตเตือนความจำในเครื่อง", "\"จดโน้ตว่าซื้อกาแฟ\" หรือ \"ดูโน้ตทั้งหมด\"", Icons.Outlined.EditNote),
    "search_contacts" to ToolUiSpec("ค้นหารายชื่อผู้ติดต่อ", "ค้นหาเบอร์โทรจากสมุดรายชื่อแบบออฟไลน์", "\"ขอเบอร์โทรของสมชาย\"", Icons.Outlined.Contacts),
    "run_javascript" to ToolUiSpec("คำนวณ & โค้ด JavaScript", "คำนวณเลขซับซ้อนและรันตรรกะใน Sandbox ปลอดภัย", "\"คำนวณดอกเบี้ยทบต้น 5% ต่อปี 3 ปี\"", Icons.Outlined.Code),
    "web_search" to ToolUiSpec("ค้นหาข้อมูลเว็บ", "ค้นหาข้อมูลจากอินเทอร์เน็ตเมื่อออนไลน์", "\"ค้นหาข่าวเทคโนโลยีวันนี้\"", Icons.Outlined.Search),
    "web_fetch" to ToolUiSpec("ดึงเนื้อหาหน้าเว็บ", "อ่านสรุปเนื้อหาจากลิงก์ URL", "\"สรุปเนื้อหาจากลิงก์นี้\"", Icons.Outlined.Public),
)

@Composable
fun ToolsScreen(
    tools: List<Tool>,
    enabledStates: Map<String, Boolean>,
    onToolEnabledChanged: (String, Boolean) -> Unit,
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Assistant, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("ผู้ช่วยเริ่มต้นของระบบ (Jarvis Shortcut)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("ตั้งแอปนี้เป็นผู้ช่วยหลักเพื่อเรียกใช้ด้วยปุ่มโฮมหรือปุ่มข้าง", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalButton(onClick = {
                    runCatching { context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)) }
                        .onFailure { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                }) { Text("ตั้งค่า") }
            }
            HorizontalDivider()
            tools.forEach { tool ->
                val spec = TOOL_UI_SPECS[tool.name]
                val enabled = enabledStates[tool.name] ?: false
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onToolEnabledChanged(tool.name, !enabled) }.padding(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    if (spec != null) {
                        Icon(spec.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(16.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(spec?.title ?: tool.name, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(spec?.desc ?: tool.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (spec != null) {
                            Spacer(Modifier.height(4.dp))
                            Text("ตัวอย่าง: ${spec.example}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontStyle = FontStyle.Italic)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = enabled, onCheckedChange = { onToolEnabledChanged(tool.name, it) }, modifier = Modifier.align(Alignment.CenterVertically))
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}
