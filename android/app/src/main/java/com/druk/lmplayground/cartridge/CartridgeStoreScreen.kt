@file:OptIn(ExperimentalMaterial3Api::class)

package com.druk.ai_offline_llm.cartridge

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun CartridgeStoreScreen(onBackClick: () -> Unit, onInsertAndReturnToChat: (AiCartridge) -> Unit) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var installedCartridges by remember { mutableStateOf(CartridgeManager.getInstalledCartridges(context)) }
    var activeId by remember { mutableStateOf(CartridgeManager.getActiveCartridgeId(context)) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            CartridgeManager.importFromUri(context, uri)
                .onSuccess { imported ->
                    installedCartridges = CartridgeManager.getInstalledCartridges(context)
                    selectedTab = 1
                    Toast.makeText(context, "นำเข้าตลับ \"${imported.name}\" สำเร็จ!", Toast.LENGTH_SHORT).show()
                }
                .onFailure { Toast.makeText(context, "ไฟล์ตลับไม่ถูกต้อง: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("ร้านค้าตลับ AI") },
                    navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "กลับ") } },
                    actions = {
                        TextButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                            Icon(Icons.Outlined.Download, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("นำเข้า .json")
                        }
                    }
                )
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("ร้านค้าตลับ (${CartridgeManager.storeCatalog.size})") }, icon = { Icon(Icons.Outlined.Extension, null, modifier = Modifier.size(18.dp)) })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("กระเป๋าตลับ (${installedCartridges.size})") }, icon = { Icon(Icons.Outlined.Storage, null, modifier = Modifier.size(18.dp)) })
                }
            }
        },
        floatingActionButton = { FloatingActionButton(onClick = { showCreateDialog = true }) { Icon(Icons.Default.Add, "สร้างตลับใหม่") } }
    ) { padding ->
        val installedIds = remember(installedCartridges) { installedCartridges.map { it.id }.toSet() }
        val listToShow = if (selectedTab == 0) CartridgeManager.storeCatalog else installedCartridges

        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(listToShow, key = { it.id }) { item ->
                val isInstalled = selectedTab == 1 || installedIds.contains(item.id)
                val accent = Color(item.colorHex)
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    border = BorderStroke(if (activeId == item.id) 2.dp else 1.dp, if (activeId == item.id) accent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Extension, null, tint = accent, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("${item.category} • ${item.version} • โดย ${item.author}", style = MaterialTheme.typography.labelSmall, color = accent)
                            }
                            if (selectedTab == 1) {
                                IconButton(onClick = {
                                    installedCartridges = CartridgeManager.removeInstalledCartridge(context, item.id)
                                    if (activeId == item.id) activeId = null
                                    Toast.makeText(context, "ลบตลับออกแล้ว", Toast.LENGTH_SHORT).show()
                                }) { Icon(Icons.Outlined.Delete, "ลบตลับ", tint = MaterialTheme.colorScheme.error) }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(item.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("ค่าจูนตลับ: Temp ${item.temperature} | Top-P ${item.topP}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            if (!isInstalled) {
                                OutlinedButton(onClick = {
                                    installedCartridges = CartridgeManager.installCartridge(context, item)
                                    Toast.makeText(context, "ติดตั้งตลับ \"${item.name}\" ลงกระเป๋าแล้ว", Toast.LENGTH_SHORT).show()
                                }) { Icon(Icons.Outlined.Download, null, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(6.dp)); Text("รับตลับเข้ากระเป๋า") }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            FilledTonalButton(onClick = {
                                if (!isInstalled) installedCartridges = CartridgeManager.installCartridge(context, item)
                                activeId = item.id
                                onInsertAndReturnToChat(item)
                            }) {
                                Icon(if (activeId == item.id) Icons.Default.Check else Icons.Outlined.Extension, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (activeId == item.id) "กำลังเสียบใช้งาน" else "เสียบตลับใช้งาน")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var prompt by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("สร้างตลับ AI ใหม่") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("ชื่อตลับ") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("คำอธิบายความสามารถ") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = prompt, onValueChange = { prompt = it }, label = { Text("ชุดคำสั่ง System Prompt") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        installedCartridges = CartridgeManager.installCartridge(context, AiCartridge(id = "custom_${System.currentTimeMillis()}", name = name.trim(), description = desc.trim().ifBlank { "ตลับกำหนดเอง" }, systemPrompt = prompt.trim(), colorHex = 0xFF00E5FF, isCustom = true))
                        showCreateDialog = false; selectedTab = 1
                        Toast.makeText(context, "สร้างตลับเรียบร้อย!", Toast.LENGTH_SHORT).show()
                    },
                    enabled = name.isNotBlank() && prompt.isNotBlank()
                ) { Text("บันทึกตลับ") }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("ยกเลิก") } }
        )
    }
}
