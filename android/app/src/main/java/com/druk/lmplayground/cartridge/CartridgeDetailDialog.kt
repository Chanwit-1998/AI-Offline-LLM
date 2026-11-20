package com.druk.ai_offline_llm.cartridge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun CartridgeDetailDialog(
    cartridge: AiCartridge,
    onDismiss: () -> Unit,
    primaryActionLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val accent = Color(cartridge.colorHex)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Extension, contentDescription = null, tint = accent)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(cartridge.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${cartridge.category} • ${cartridge.version} • โดย ${cartridge.author}", style = MaterialTheme.typography.labelSmall, color = accent)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(cartridge.description, style = MaterialTheme.typography.bodyMedium)

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("เหมาะกับงานแบบไหน", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = accent)
                        Text(
                            cartridge.bestFor.ifBlank { "• งานเฉพาะทางในหมวด ${cartridge.category}\n• ปรับจูนคำตอบให้ตรงประเด็นและเสถียรขึ้น" },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("วิธีใช้งานให้ได้คำตอบตรงจุดที่สุด", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(
                            cartridge.usageTips.ifBlank { "• เสียบตลับในกระเป๋าของฉันก่อนเริ่มพิมพ์คำถาม\n• ระบุเป้าหมายหรือข้อมูลตั้งต้นให้ชัดเจนในประโยคเดียว" },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (cartridge.samplePrompt.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("ตัวอย่างคำถามพร้อมใช้", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                TextButton(
                                    onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("Sample Prompt", cartridge.samplePrompt))
                                        Toast.makeText(context, "คัดลอกตัวอย่างคำถามแล้ว", Toast.LENGTH_SHORT).show()
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("คัดลอก", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Text("\"${cartridge.samplePrompt}\"", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("ค่าจูนสมอง & คำสั่งเบื้องหลัง", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text("Temperature: ${cartridge.temperature}  |  Top-P: ${cartridge.topP}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(cartridge.systemPrompt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            if (primaryActionLabel != null && onPrimaryAction != null) {
                Button(onClick = {
                    onPrimaryAction()
                    onDismiss()
                }) {
                    Text(primaryActionLabel)
                }
            } else {
                TextButton(onClick = onDismiss) { Text("ปิด") }
            }
        },
        dismissButton = {
            if (primaryActionLabel != null && onPrimaryAction != null) {
                TextButton(onClick = onDismiss) { Text("ปิด") }
            }
        }
    )
}
