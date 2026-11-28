package com.druk.ai_offline_llm.models

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.druk.ai_offline_llm.R
import com.druk.ai_offline_llm.conversation.OnlineApiPrefsStore
import com.druk.ai_offline_llm.conversation.OnlineProvider
import com.druk.ai_offline_llm.conversation.OnlineProviderConfig
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectModelDialog(
    models: List<ModelWithStatus>,
    currentModelFilename: String? = null,
    isModelLoaded: Boolean = false,
    onlineModeEnabled: Boolean = false,
    activeOnlineProvider: OnlineProvider = OnlineProvider.GEMINI,
    onOnlineModeChanged: (Boolean) -> Unit = {},
    onSaveOnlineConfig: (OnlineProviderConfig, Boolean) -> Unit = { _, _ -> },
    chatPaneStartOffset: Dp = 0.dp,
    hazeState: HazeState? = null,
    hazeStyle: HazeStyle = HazeStyle.Unspecified,
    onLoadModel: (ModelInfo) -> Unit,
    onUnloadModel: () -> Unit = {},
    onGenerationParams: () -> Unit = {},
    onBrowseModels: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val downloadedModels = models.filter { it.isDownloaded }
    var selectedSection by remember { mutableIntStateOf(if (onlineModeEnabled) 1 else 0) }
    var selectedFilter by remember { mutableIntStateOf(0) }

    val filteredModels = remember(downloadedModels, selectedFilter) {
        when (selectedFilter) {
            1 -> downloadedModels.filter { !it.model.isVision && !it.model.isImageGenerator }
            2 -> downloadedModels.filter { it.model.isVision }
            3 -> downloadedModels.filter { it.model.isImageGenerator }
            else -> downloadedModels
        }
    }

    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        focusManager.clearFocus()
        keyboard?.hide()
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedSection == 0) "เลือกโมเดล AI" else "ตั้งค่า API Key ออนไลน์",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = if (selectedSection == 0) "${filteredModels.size} โมเดล" else "4 ผู้ให้บริการ",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onDismissRequest) {
                    Icon(Icons.Outlined.Close, contentDescription = "ปิด")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedSection == 0,
                    onClick = { selectedSection = 0 },
                    leadingIcon = {
                        Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    label = { Text("ออฟไลน์") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectedSection == 1,
                    onClick = { selectedSection = 1 },
                    leadingIcon = {
                        Icon(Icons.Outlined.Cloud, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    label = {
                        Text(if (onlineModeEnabled) "API ออนไลน์ (เปิดอยู่)" else "API ออนไลน์")
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            if (selectedSection == 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(selected = selectedFilter == 0, onClick = { selectedFilter = 0 }, label = { Text("ทั้งหมด") })
                    FilterChip(selected = selectedFilter == 1, onClick = { selectedFilter = 1 }, label = { Text("แชท (LLM)") })
                    FilterChip(selected = selectedFilter == 2, onClick = { selectedFilter = 2 }, label = { Text("Vision") })
                    FilterChip(selected = selectedFilter == 3, onClick = { selectedFilter = 3 }, label = { Text("สร้างภาพ") })
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 420.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (filteredModels.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.no_downloaded_models),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    } else {
                        items(items = filteredModels, key = { it.model.filename }) { modelWithStatus ->
                            val isCurrent = !onlineModeEnabled && modelWithStatus.model.filename == currentModelFilename
                            ModelBottomSheetCard(
                                model = modelWithStatus.model,
                                vision = modelWithStatus.isMmprojDownloaded,
                                isCurrent = isCurrent,
                                onUnload = {
                                    onUnloadModel()
                                    onDismissRequest()
                                },
                                onClick = {
                                    onDismissRequest()
                                    if (onlineModeEnabled) onOnlineModeChanged(false)
                                    onLoadModel(modelWithStatus.model)
                                }
                            )
                        }
                    }
                }
            } else {
                OnlineApiConfigPanel(
                    onlineModeEnabled = onlineModeEnabled,
                    initialProvider = activeOnlineProvider,
                    loadConfigForProvider = { provider -> OnlineApiPrefsStore.getConfig(context, provider) },
                    onOnlineModeChanged = onOnlineModeChanged,
                    onSaveAndActivate = { config, activate ->
                        onSaveOnlineConfig(config, activate)
                        if (activate) onDismissRequest()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 460.dp)
                )
            }
        }
    }
}

@Composable
private fun OnlineApiConfigPanel(
    onlineModeEnabled: Boolean,
    initialProvider: OnlineProvider,
    loadConfigForProvider: (OnlineProvider) -> OnlineProviderConfig,
    onOnlineModeChanged: (Boolean) -> Unit,
    onSaveAndActivate: (OnlineProviderConfig, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var chosenProvider by remember { mutableStateOf(initialProvider) }
    val loadedCfg = remember(chosenProvider) { loadConfigForProvider(chosenProvider) }

    var apiKey by remember(chosenProvider) { mutableStateOf(loadedCfg.apiKey) }
    var modelName by remember(chosenProvider) { mutableStateOf(loadedCfg.model) }
    var baseUrl by remember(chosenProvider) { mutableStateOf(loadedCfg.baseUrl) }
    var showApiKey by remember { mutableStateOf(false) }
    var savedFeedback by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OnlineProvider.entries.forEach { provider ->
                FilterChip(
                    selected = chosenProvider == provider,
                    onClick = {
                        chosenProvider = provider
                        savedFeedback = null
                    },
                    label = { Text(provider.displayName) }
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (onlineModeEnabled) "กำลังใช้โหมดแชทออนไลน์" else "กำลังใช้โหมดออฟไลน์",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "สลับกลับไปใช้โมเดล GGUF บนเครื่องได้ทุกเมื่อ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = onlineModeEnabled, onCheckedChange = { onOnlineModeChanged(it) })
            }
        }

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it; savedFeedback = null },
            label = { Text("API Key (${chosenProvider.displayName})") },
            placeholder = { Text("วาง API Key ที่นี่") },
            singleLine = true,
            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
            leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { showApiKey = !showApiKey }) {
                    Icon(
                        imageVector = if (showApiKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = null
                    )
                }
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = modelName,
            onValueChange = { modelName = it; savedFeedback = null },
            label = { Text("ชื่อโมเดล (Model ID)") },
            placeholder = { Text(chosenProvider.defaultModel) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            chosenProvider.suggestedModels.forEach { suggestion ->
                AssistChip(
                    onClick = { modelName = suggestion; savedFeedback = null },
                    label = { Text(suggestion, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        var customSkill by remember(chosenProvider) {
            mutableStateOf(OnlineApiPrefsStore.getCustomSkill(context, chosenProvider))
        }

        OutlinedTextField(
            value = customSkill,
            onValueChange = {
                customSkill = it
                savedFeedback = null
                OnlineApiPrefsStore.setCustomSkill(context, chosenProvider, it)
            },
            label = { Text("ทักษะของ AI (Skill / บทบาทเฉพาะ)") },
            placeholder = { Text("เช่น เขียน Kotlin/Android, รีวิวหาบั๊ก, อ่าน Error Log") },
            minLines = 2,
            maxLines = 4,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "ผู้เชี่ยวชาญ Kotlin & Android" to "เชี่ยวชาญ Kotlin, Jetpack Compose, Coroutines, ViewModel และแก้บั๊ก Android อย่างแม่นยำ",
                "นักรีวิวและหาบั๊กโค้ด" to "หา Root Cause, Edge cases, Race condition และ Memory leak แล้วเสนอวิธีแก้ที่ชัดเจน",
                "วิเคราะห์ Build & Error Log" to "สกัดสาเหตุหลักจาก Build/Compiler/Runtime log แบบตรงจุดไม่เดา"
            ).forEach { (chipTitle, skillPrompt) ->
                AssistChip(
                    onClick = {
                        customSkill = skillPrompt
                        savedFeedback = null
                        OnlineApiPrefsStore.setCustomSkill(context, chosenProvider, skillPrompt)
                    },
                    label = { Text(chipTitle, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it; savedFeedback = null },
            label = { Text("Base URL") },
            placeholder = { Text(chosenProvider.defaultBaseUrl) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        if (savedFeedback != null) {
            Text(
                text = savedFeedback!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val cfg = OnlineProviderConfig(
                        provider = chosenProvider,
                        apiKey = apiKey.trim(),
                        baseUrl = baseUrl.trim().ifEmpty { chosenProvider.defaultBaseUrl },
                        model = modelName.trim().ifEmpty { chosenProvider.defaultModel },
                    )
                    onSaveAndActivate(cfg, false)
                    OnlineApiPrefsStore.setCustomSkill(context, chosenProvider, customSkill.trim())
                    savedFeedback = "บันทึกค่าและทักษะ ${chosenProvider.displayName} แล้ว"
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            ) { Text("บันทึกคีย์") }

            Button(
                onClick = {
                    val cfg = OnlineProviderConfig(
                        provider = chosenProvider,
                        apiKey = apiKey.trim(),
                        baseUrl = baseUrl.trim().ifEmpty { chosenProvider.defaultBaseUrl },
                        model = modelName.trim().ifEmpty { chosenProvider.defaultModel },
                    )
                    OnlineApiPrefsStore.setCustomSkill(context, chosenProvider, customSkill.trim())
                    onSaveAndActivate(cfg, true)
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            ) { Text("บันทึกและใช้งาน") }
        }
    }
}

@Composable
private fun ModelBottomSheetCard(
    model: ModelInfo,
    vision: Boolean,
    isCurrent: Boolean,
    onUnload: () -> Unit = {},
    onClick: () -> Unit
) {
    val borderColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val containerColor = if (isCurrent) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = containerColor,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (model.logoRes != 0) {
                Image(
                    painter = painterResource(id = model.logoRes),
                    contentDescription = null,
                    modifier = Modifier.size(42.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isCurrent) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Text(
                    text = model.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    if (model.isCustom) {
                        SmallBadge(text = "Custom", color = MaterialTheme.colorScheme.secondary)
                    } else {
                        SmallBadge(text = "Official", color = MaterialTheme.colorScheme.tertiary)
                    }

                    if (model.isImageGenerator) {
                        SmallBadge(
                            text = "Image Gen",
                            color = MaterialTheme.colorScheme.error,
                            icon = { Icon(Icons.Outlined.Image, null, Modifier.size(12.dp)) }
                        )
                    } else if (vision || model.isVision) {
                        SmallBadge(
                            text = "Vision",
                            color = Color(0xFF0288D1),
                            icon = { Icon(Icons.Outlined.Image, null, Modifier.size(12.dp)) }
                        )
                    } else {
                        SmallBadge(
                            text = "Chat",
                            color = MaterialTheme.colorScheme.primary,
                            icon = { Icon(Icons.Outlined.Chat, null, Modifier.size(12.dp)) }
                        )
                    }

                    if (model.supportsThinking) {
                        SmallBadge(
                            text = "Reasoning",
                            color = Color(0xFF7B1FA2),
                            icon = { Icon(Icons.Outlined.Lightbulb, null, Modifier.size(12.dp)) }
                        )
                    }
                }

                if (isCurrent) {
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onUnload,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.StopCircle,
                            contentDescription = "หยุดใช้งานโมเดล",
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "หยุดใช้งาน",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun SmallBadge(
    text: String,
    color: Color,
    icon: (@Composable () -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
        contentColor = color
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            if (icon != null) {
                icon()
                Spacer(Modifier.width(3.dp))
            }
            Text(text = text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
        }
    }
}
