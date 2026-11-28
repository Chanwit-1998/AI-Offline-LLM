package com.druk.ai_offline_llm.models

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.druk.ai_offline_llm.R
import com.druk.ai_offline_llm.cartridge.AiCartridge
import com.druk.ai_offline_llm.cartridge.CartridgeDetailDialog
import com.druk.ai_offline_llm.cartridge.CartridgeManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.druk.ai_offline_llm.cartridge.CartridgeStoreScreen
import com.druk.ai_offline_llm.conversation.GenerationParams
import com.druk.ai_offline_llm.data.SystemPromptEntity
import com.druk.ai_offline_llm.settings.ToolsContent
import com.druk.ai_offline_llm.tools.createDefault
import com.druk.llamacpp.tools.Tool
import com.druk.llamacpp.tools.ToolRegistry
import kotlin.math.roundToInt

private data class BuiltInPersonaPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val promptText: String
)

private val BUILT_IN_PERSONAS = listOf(
    BuiltInPersonaPreset(
        id = "preset_general_th",
        title = "ผู้ช่วยสารพัดประโยชน์ (ไทย-อังกฤษ)",
        subtitle = "ตอบชัดเจน กระชับ ตรงประเด็น เป็นธรรมชาติ",
        promptText = "คุณคือผู้ช่วย AI อัจฉริยะที่ทำงานแบบออฟไลน์ ตอบคำถามเป็นภาษาไทยที่ชัดเจน กระชับ ตรงประเด็น และเป็นธรรมชาติ หากมีศัพท์เทคนิคให้คงภาษาอังกฤษควบคู่ไว้เพื่อความแม่นยำ"
    ),
    BuiltInPersonaPreset(
        id = "preset_coder",
        title = "โปรแกรมเมอร์ & แก้โค้ด",
        subtitle = "เน้นโค้ดที่รันได้จริง อธิบายสั้นกระชับ",
        promptText = "คุณคือวิศวกรซอฟต์แวร์อาวุโส ตอบด้วยโค้ดที่สมบูรณ์ ถูกต้องตามหลักปฏิบัติที่ดี และรันใช้งานได้จริง หลีกเลี่ยงคำอธิบายเยิ่นเย้อ เน้นจุดสำคัญและวิธีแก้ปัญหาที่ตรงจุด"
    ),
    BuiltInPersonaPreset(
        id = "preset_translator",
        title = "นักแปลภาษา & ปรับสำนวน",
        subtitle = "แปลความหมายสละสลวย ตรงบริบท",
        promptText = "คุณคือนักแปลภาษามืออาชีพ แปลข้อความให้สละสลวย ตรงตามบริบทและระดับภาษาของต้นฉบับ พร้อมอธิบายคำศัพท์หรือสำนวนเฉพาะทางสั้นๆ เมื่อจำเป็น"
    ),
    BuiltInPersonaPreset(
        id = "preset_analyst",
        title = "สรุปเนื้อหา & วิเคราะห์ข้อมูล",
        subtitle = "จัดลำดับเป็นข้อ อ่านเข้าใจง่ายใน 10 วินาที",
        promptText = "คุณคือผู้เชี่ยวชาญด้านการสรุปและวิเคราะห์ข้อมูล สรุปประเด็นสำคัญขึ้นต้นก่อนเสมอ แล้วแยกย่อยรายละเอียดเป็นข้อๆ อย่างเป็นระบบ กระชับ และครบถ้วน"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelConfigBottomSheet(
    model: ModelInfo,
    isActiveModel: Boolean = false,
    recentSystemPrompts: List<SystemPromptEntity> = emptyList(),
    onProfileChanged: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tools = remember { ToolRegistry.createDefault(context).getAllTools() }
    val toolNames = remember(tools) { tools.map { it.name } }

    var profile by remember(model.filename) {
        mutableStateOf(ModelProfileStore.load(context, model.filename, toolNames))
    }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showResetConfirm by remember { mutableStateOf(false) }

    val maxContextSize = remember {
        val totalGb = DeviceCapability.totalRamBytes(context) / (1024L * 1024L * 1024L)
        when {
            totalGb >= 10 -> 16384
            totalGb >= 7 -> 8192
            else -> 4096
        }
    }
    val supportsThinking = model.supportsThinking ||
        model.thinkingMode == ThinkingMode.OPTIONAL ||
        model.thinkingMode == ThinkingMode.ALWAYS

    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val maxTabHeight = (screenHeightDp * 0.78f).dp

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = model.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isActiveModel) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "กำลังใช้งาน",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "ตั้งค่าแยกอิสระเฉพาะโมเดลนี้ (${model.filename})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { showResetConfirm = true }) {
                    Icon(
                        imageVector = Icons.Outlined.RestartAlt,
                        contentDescription = "รีเซ็ตค่าโมเดลนี้",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (model.isImageModel || com.druk.ai_offline_llm.models.looksLikeImageModelFilename(model.filename)) {
                var imgSettings by remember(model.filename) {
                    mutableStateOf(
                        com.druk.ai_offline_llm.conversation.ImageGenPrefsStore.load(context, model.filename)
                    )
                }
                var seedText by remember(model.filename) {
                    mutableStateOf(if (imgSettings.seed < 0L) "" else imgSettings.seed.toString())
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxTabHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("ความละเอียดภาพ (Resolution)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            384 to "384×384 (เร็วพิเศษ ~2x)",
                            512 to "512×512 (มาตรฐานคมชัด)"
                        ).forEach { (res, label) ->
                            FilterChip(
                                selected = imgSettings.resolution == res,
                                onClick = {
                                    imgSettings = imgSettings.copy(resolution = res)
                                    com.druk.ai_offline_llm.conversation.ImageGenPrefsStore.save(context, model.filename, imgSettings)
                                },
                                label = { Text(label) }
                            )
                        }
                    }

                    Text("สไตล์ภาพเริ่มต้น (Default Style)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        com.druk.ai_offline_llm.conversation.IMAGE_STYLE_PRESETS.forEach { preset ->
                            FilterChip(
                                selected = imgSettings.styleId == preset.id,
                                onClick = {
                                    imgSettings = imgSettings.copy(styleId = preset.id)
                                    com.druk.ai_offline_llm.conversation.ImageGenPrefsStore.save(context, model.filename, imgSettings)
                                },
                                label = { Text(preset.label) }
                            )
                        }
                    }

                    Column {
                        val stepLabel = if (imgSettings.steps == 0) "อัตโนมัติ (SDXS = 1 Step)" else "${imgSettings.steps} Steps"
                        Text("จำนวนรอบวาด (Steps): $stepLabel", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Slider(
                            value = imgSettings.steps.toFloat(),
                            onValueChange = {
                                imgSettings = imgSettings.copy(steps = it.roundToInt())
                                com.druk.ai_offline_llm.conversation.ImageGenPrefsStore.save(context, model.filename, imgSettings)
                            },
                            valueRange = 0f..20f,
                            steps = 19
                        )
                    }

                    Column {
                        val cfgLabel = if (imgSettings.cfgScale < 0f) "อัตโนมัติ (SDXS = 1.0)" else String.format("%.1f", imgSettings.cfgScale)
                        Text("ความเข้มตามคำสั่ง (CFG Scale): $cfgLabel", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Slider(
                            value = if (imgSettings.cfgScale < 0f) 1.0f else imgSettings.cfgScale,
                            onValueChange = {
                                val rounded = (it * 2f).roundToInt() / 2f
                                imgSettings = imgSettings.copy(cfgScale = rounded)
                                com.druk.ai_offline_llm.conversation.ImageGenPrefsStore.save(context, model.filename, imgSettings)
                            },
                            valueRange = 1.0f..12.0f
                        )
                    }

                    OutlinedTextField(
                        value = seedText,
                        onValueChange = { raw ->
                            seedText = raw.filter { it.isDigit() }
                            val parsed = seedText.toLongOrNull() ?: -1L
                            imgSettings = imgSettings.copy(seed = parsed)
                            com.druk.ai_offline_llm.conversation.ImageGenPrefsStore.save(context, model.filename, imgSettings)
                        },
                        label = { Text("Seed (เว้นว่าง = สุ่มใหม่ทุกรอบ)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = imgSettings.negativePrompt,
                        onValueChange = { neg ->
                            imgSettings = imgSettings.copy(negativePrompt = neg)
                            com.druk.ai_offline_llm.conversation.ImageGenPrefsStore.save(context, model.filename, imgSettings)
                        },
                        label = { Text("Negative Prompt (สิ่งที่ไม่อยากให้มีในภาพ)") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                return@Column
            }

            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("พารามิเตอร์", maxLines = 1, softWrap = false) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("คำสั่งระบบ", maxLines = 1, softWrap = false) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        val count = profile.enabledTools.size
                        Text(
                            if (count > 0) "เครื่องมือ ($count)" else "เครื่องมือ",
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("ตลับความรู้", maxLines = 1, softWrap = false) }
                )
            }

            when (selectedTab) {
                0 -> ModelParamsConfigTab(
                    params = profile.params,
                    maxContextSize = maxContextSize,
                    supportsThinking = supportsThinking,
                    maxHeight = maxTabHeight,
                    onParamsChange = { newParams ->
                        profile = profile.copy(params = newParams)
                        ModelProfileStore.saveParams(context, model.filename, newParams)
                        onProfileChanged()
                    }
                )
                1 -> ModelPromptConfigTab(
                    selectedPromptId = profile.systemPromptId,
                    customPromptText = profile.customPromptText.orEmpty(),
                    recentSystemPrompts = recentSystemPrompts,
                    maxHeight = maxTabHeight,
                    onPromptUpdated = { promptId, text ->
                        profile = profile.copy(systemPromptId = promptId, customPromptText = text)
                        ModelProfileStore.savePrompt(context, model.filename, promptId, text)
                        onProfileChanged()
                    }
                )
                2 -> ModelToolsConfigTab(
                    supportsTools = model.supportsTools,
                    tools = tools,
                    enabledTools = profile.enabledTools,
                    maxHeight = maxTabHeight,
                    onToggleTool = { toolName, enabled ->
                        val nextSet = if (enabled) profile.enabledTools + toolName else profile.enabledTools - toolName
                        profile = profile.copy(enabledTools = nextSet)
                        ModelProfileStore.saveToolEnabled(context, model.filename, toolName, enabled)
                        onProfileChanged()
                    }
                )
                3 -> ModelCartridgeConfigTab(
                    boundCartridgeId = profile.cartridgeId,
                    maxHeight = maxTabHeight,
                    onSelectCartridge = { cart ->
                        if (cart != null) {
                            CartridgeManager.installCartridge(context, cart)
                        }
                        ModelProfileStore.saveCartridge(context, model.filename, cart?.id)
                        profile = profile.copy(cartridgeId = cart?.id)
                        onProfileChanged()
                        Toast.makeText(
                            context,
                            if (cart != null) "ผูกตลับ '${cart.name}' กับ ${model.name} แล้ว"
                            else "ถอดตลับออกจาก ${model.name} แล้ว",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("รีเซ็ตการตั้งค่าโมเดลนี้?") },
            text = { Text("คืนค่าพารามิเตอร์ คำสั่งระบบ ปิดเครื่องมือทั้งหมด และถอดตลับความรู้ของ ${model.name} กลับเป็นค่าเริ่มต้น") },
            confirmButton = {
                TextButton(
                    onClick = {
                        ModelProfileStore.reset(context, model.filename)
                        profile = ModelProfile(filename = model.filename)
                        onProfileChanged()
                        showResetConfirm = false
                        Toast.makeText(context, "รีเซ็ตค่าของ ${model.name} เรียบร้อย", Toast.LENGTH_SHORT).show()
                    }
                ) { Text("รีเซ็ต") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("ยกเลิก") }
            }
        )
    }
}

@Composable
private fun ModelParamsConfigTab(
    params: GenerationParams,
    maxContextSize: Int,
    supportsThinking: Boolean,
    maxHeight: Dp,
    onParamsChange: (GenerationParams) -> Unit
) {
    val contextMin = 512
    val contextMax = maxContextSize.coerceAtLeast(512)
    val contextStep = 512
    var showAdvanced by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "โปรไฟล์ด่วน",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = params.temperature <= 0.3f,
                onClick = {
                    onParamsChange(params.copy(temperature = 0.2f, topP = 0.9f, topK = 20, minP = 0.05f, repetitionPenalty = 1.1f))
                },
                label = { Text("แม่นยำ") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = params.temperature in 0.6f..0.9f,
                onClick = {
                    onParamsChange(params.copy(temperature = 0.8f, topP = 0.95f, topK = 40, minP = 0.05f, repetitionPenalty = 1.1f))
                },
                label = { Text("สมดุล") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = params.temperature >= 1.1f,
                onClick = {
                    onParamsChange(params.copy(temperature = 1.3f, topP = 0.98f, topK = 80, minP = 0.02f, repetitionPenalty = 1.05f))
                },
                label = { Text("สร้างสรรค์") },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        ConfigSlider(
            label = stringResource(R.string.context_size),
            value = params.contextSize.toFloat(),
            valueRange = contextMin.toFloat()..contextMax.toFloat(),
            steps = (((contextMax - contextMin) / contextStep) - 1).coerceAtLeast(0),
            valueDisplay = "${params.contextSize}",
            onValueChange = {
                val snapped = ((it / contextStep).roundToInt() * contextStep).coerceIn(contextMin, contextMax)
                val newBudget = if (params.contextSize > 0) {
                    (params.thinkingBudget.toLong() * snapped / params.contextSize).toInt().coerceIn(64, snapped)
                } else snapped / 4
                onParamsChange(params.copy(contextSize = snapped, thinkingBudget = newBudget))
            }
        )

        if (supportsThinking) {
            ConfigSlider(
                label = stringResource(R.string.thinking_budget),
                value = params.thinkingBudget.toFloat(),
                valueRange = 64f..params.contextSize.toFloat(),
                steps = 0,
                valueDisplay = stringResource(R.string.tokens_value, params.thinkingBudget),
                onValueChange = {
                    val snapped = ((it / 64).roundToInt() * 64).coerceIn(64, params.contextSize)
                    onParamsChange(params.copy(thinkingBudget = snapped))
                }
            )
        }

        ConfigSlider(
            label = stringResource(R.string.temperature),
            value = params.temperature,
            valueRange = 0f..2f,
            steps = 0,
            valueDisplay = "%.2f".format(params.temperature),
            onValueChange = { onParamsChange(params.copy(temperature = (it * 100).roundToInt() / 100f)) }
        )

        ConfigSlider(
            label = stringResource(R.string.top_p),
            value = params.topP,
            valueRange = 0f..1f,
            steps = 0,
            valueDisplay = "%.2f".format(params.topP),
            onValueChange = { onParamsChange(params.copy(topP = (it * 100).roundToInt() / 100f)) }
        )

        ConfigSlider(
            label = stringResource(R.string.repetition_penalty),
            value = params.repetitionPenalty,
            valueRange = 1f..2f,
            steps = 0,
            valueDisplay = "%.2f".format(params.repetitionPenalty),
            onValueChange = { onParamsChange(params.copy(repetitionPenalty = (it * 100).roundToInt() / 100f)) }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showAdvanced = !showAdvanced }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.advanced), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Icon(if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
        }

        AnimatedVisibility(visible = showAdvanced) {
            Column {
                ConfigSlider(
                    label = stringResource(R.string.top_k),
                    value = params.topK.toFloat(),
                    valueRange = 0f..200f,
                    steps = 0,
                    valueDisplay = "${params.topK}",
                    onValueChange = { onParamsChange(params.copy(topK = it.roundToInt())) }
                )
                ConfigSlider(
                    label = stringResource(R.string.min_p),
                    value = params.minP,
                    valueRange = 0f..0.5f,
                    steps = 0,
                    valueDisplay = "%.3f".format(params.minP),
                    onValueChange = { onParamsChange(params.copy(minP = (it * 1000).roundToInt() / 1000f)) }
                )
                var seedText by remember(params.seed) {
                    mutableStateOf(if (params.seed < 0) "" else params.seed.toString())
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.seed), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = seedText,
                        onValueChange = { text ->
                            seedText = text
                            onParamsChange(params.copy(seed = text.toIntOrNull() ?: -1))
                        },
                        modifier = Modifier.weight(1f).padding(start = 16.dp),
                        placeholder = { Text(stringResource(R.string.random)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun ModelPromptConfigTab(
    selectedPromptId: String?,
    customPromptText: String,
    recentSystemPrompts: List<SystemPromptEntity>,
    maxHeight: Dp,
    onPromptUpdated: (String?, String?) -> Unit
) {
    val context = LocalContext.current
    val resolvedCurrentText = remember(selectedPromptId, customPromptText, recentSystemPrompts) {
        when {
            customPromptText.isNotBlank() -> customPromptText
            !selectedPromptId.isNullOrBlank() -> {
                BUILT_IN_PERSONAS.find { it.id == selectedPromptId }?.promptText
                    ?: recentSystemPrompts.find { it.id == selectedPromptId }?.text.orEmpty()
            }
            else -> ""
        }
    }
    var draftText by remember(resolvedCurrentText) { mutableStateOf(resolvedCurrentText) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "คำสั่งระบบที่ผูกกับโมเดลนี้",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (draftText.isNotBlank() || !selectedPromptId.isNullOrBlank()) {
                TextButton(
                    onClick = {
                        draftText = ""
                        onPromptUpdated(null, null)
                        Toast.makeText(context, "ล้างคำสั่งระบบของโมเดลนี้แล้ว", Toast.LENGTH_SHORT).show()
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text("ล้างค่า")
                }
            }
        }

        OutlinedTextField(
            value = draftText,
            onValueChange = { draftText = it },
            placeholder = { Text("พิมพ์บทบาทหรือคำสั่งระบบเฉพาะโมเดลนี้...") },
            minLines = 3,
            maxLines = 5,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                val trimmed = draftText.trim()
                val matchedBuiltIn = BUILT_IN_PERSONAS.find { it.promptText == trimmed }?.id
                val matchedSaved = recentSystemPrompts.find { it.text.trim() == trimmed }?.id
                val id = matchedBuiltIn ?: matchedSaved
                onPromptUpdated(id, if (id == null) trimmed else null)
                Toast.makeText(context, "บันทึกคำสั่งระบบเฉพาะโมเดลนี้แล้ว", Toast.LENGTH_SHORT).show()
            },
            enabled = draftText.trim() != resolvedCurrentText.trim() && draftText.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("บันทึกคำสั่งระบบนี้")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "บุคลิกสำเร็จรูป (แตะเพื่อเลือกใช้ทันที)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        BUILT_IN_PERSONAS.forEach { preset ->
            val isSelected = selectedPromptId == preset.id || resolvedCurrentText == preset.promptText
            OutlinedCard(
                onClick = {
                    draftText = preset.promptText
                    onPromptUpdated(preset.id, null)
                    Toast.makeText(context, "ตั้งบุคลิก '${preset.title}' แล้ว", Toast.LENGTH_SHORT).show()
                },
                border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else CardDefaults.outlinedCardBorder(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(preset.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (isSelected) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                    Text(preset.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (recentSystemPrompts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.saved_prompts),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            recentSystemPrompts.forEach { item ->
                val isSelected = selectedPromptId == item.id
                OutlinedCard(
                    onClick = {
                        draftText = item.text
                        onPromptUpdated(item.id, null)
                        Toast.makeText(context, "เลือกคำสั่งระบบที่บันทึกไว้แล้ว", Toast.LENGTH_SHORT).show()
                    },
                    border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else CardDefaults.outlinedCardBorder(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ModelToolsConfigTab(
    supportsTools: Boolean,
    tools: List<Tool>,
    enabledTools: Set<String>,
    maxHeight: Dp,
    onToggleTool: (String, Boolean) -> Unit
) {
    val stateMap = remember(tools, enabledTools) {
        tools.associate { it.name to (it.name in enabledTools) }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
    ) {
        Surface(
            color = if (supportsTools) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = if (supportsTools) {
                    "สวิตช์เครื่องมือด้านล่างมีผลเฉพาะกับโมเดลนี้เท่านั้น (ปิดเป็นค่าเริ่มต้น)"
                } else {
                    "โมเดลนี้ไม่ได้ระบุว่ารองรับ Function Calling ใน Metadata (คุณยังสามารถเปิดทดลองใช้งานเฉพาะโมเดลนี้ได้)"
                },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp)
            )
        }
        ToolsContent(
            tools = tools,
            enabledStates = stateMap,
            onToolEnabledChanged = onToggleTool,
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
        )
    }
}

@Composable
private fun ModelCartridgeConfigTab(
    boundCartridgeId: String?,
    maxHeight: Dp,
    onSelectCartridge: (AiCartridge?) -> Unit
) {
    val context = LocalContext.current
    var availableCartridges by remember { mutableStateOf(CartridgeManager.getAllAvailableCartridges(context)) }
    var installedIds by remember { mutableStateOf(CartridgeManager.getInstalledCartridges(context).map { it.id }.toSet()) }
    var cartridgeFolderPath by remember { mutableStateOf(CartridgeManager.getCartridgeFolderPath(context)) }
    var inspectingCartridge by remember { mutableStateOf<AiCartridge?>(null) }
    var showCartridgeStore by remember { mutableStateOf(false) }

    val refreshCartridges = {
        availableCartridges = CartridgeManager.getAllAvailableCartridges(context)
        installedIds = CartridgeManager.getInstalledCartridges(context).map { it.id }.toSet()
        cartridgeFolderPath = CartridgeManager.getCartridgeFolderPath(context)
    }

    val importCartridgeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            CartridgeManager.importFromUri(context, uri)
                .onSuccess { imported ->
                    CartridgeManager.installCartridge(context, imported)
                    refreshCartridges()
                    Toast.makeText(context, "นำเข้าตลับ ${imported.name} สำเร็จ!", Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    Toast.makeText(context, "ไฟล์ตลับไม่ถูกต้อง: ${it.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    val cartridgeFolderPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                val count = CartridgeManager.setCustomCartridgeFolderUri(context, uri)
                refreshCartridges()
                Toast.makeText(context, "ตั้งค่าโฟลเดอร์ตลับข้อมูลแล้ว (พบ $count ตลับ)", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "ไม่สามารถเปิดโฟลเดอร์ได้: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "เลือกตลับความรู้ RAG ที่จะเสียบใช้งานอัตโนมัติเมื่อเปิดโมเดลนี้",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))

        // โฟลเดอร์ตลับข้อมูล
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("โฟลเดอร์ตลับข้อมูล (${availableCartridges.size} ตลับ)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = cartridgeFolderPath.ifBlank { "โฟลเดอร์แอป/cartridges" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        runCatching { cartridgeFolderPickerLauncher.launch(null) }
                            .onFailure { Toast.makeText(context, R.string.folder_picker_unavailable, Toast.LENGTH_SHORT).show() }
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("เปลี่ยน", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ปุ่มร้านค้าแพ็กข้อมูล RAG และนำเข้า .json
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = { showCartridgeStore = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "ร้านค้าแพ็กข้อมูล RAG",
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(
                onClick = { importCartridgeLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "นำเข้า .json",
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        availableCartridges.forEach { cart ->
            val isBound = boundCartridgeId == cart.id
            val isCustomInstalled = installedIds.contains(cart.id)
            val accent = Color(cart.colorHex)
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isBound) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                border = BorderStroke(
                    width = if (isBound) 1.5.dp else 1.dp,
                    color = if (isBound) accent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                ),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.Extension, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(cart.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${cart.category} • ${cart.knowledge.size} หัวข้อ", style = MaterialTheme.typography.labelSmall, color = accent)
                        }
                        if (isCustomInstalled) {
                            IconButton(
                                onClick = {
                                    CartridgeManager.removeInstalledCartridge(context, cart.id)
                                    if (isBound) onSelectCartridge(null)
                                    refreshCartridges()
                                    Toast.makeText(context, "ลบตลับ ${cart.name} แล้ว", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Outlined.Delete, contentDescription = "ลบตลับ", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(cart.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { inspectingCartridge = cart },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("รายละเอียด")
                        }
                        if (isBound) {
                            OutlinedButton(
                                onClick = { onSelectCartridge(null) },
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.7f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Outlined.StopCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("ถอดตลับออก")
                            }
                        } else {
                            FilledTonalButton(
                                onClick = { onSelectCartridge(cart) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Outlined.Extension, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("ผูกกับโมเดลนี้")
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    inspectingCartridge?.let { cart ->
        val isBound = boundCartridgeId == cart.id
        CartridgeDetailDialog(
            cartridge = cart,
            onDismiss = { inspectingCartridge = null },
            primaryActionLabel = if (isBound) null else "ผูกกับโมเดลนี้",
            onPrimaryAction = if (isBound) null else { { onSelectCartridge(cart) } }
        )
    }

    if (showCartridgeStore) {
        Dialog(
            onDismissRequest = {
                showCartridgeStore = false
                refreshCartridges()
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                CartridgeStoreScreen(
                    onBackClick = {
                        showCartridgeStore = false
                        refreshCartridges()
                    },
                    onInsertAndReturnToChat = { cart ->
                        showCartridgeStore = false
                        refreshCartridges()
                        onSelectCartridge(cart)
                    }
                )
            }
        }
    }
}

@Composable
private fun ConfigSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueDisplay: String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(text = valueDisplay, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
