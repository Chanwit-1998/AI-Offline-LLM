package com.druk.ai_offline_llm.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.druk.ai_offline_llm.data.SystemPromptEntity
import com.druk.ai_offline_llm.settings.SystemPromptEditorSheet
import com.druk.ai_offline_llm.settings.ToolsContent
import com.druk.llamacpp.tools.Tool
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerationParamsSheet(
    params: GenerationParams,
    maxContextSize: Int,
    supportsThinking: Boolean = false,
    supportsToolCalling: Boolean = false,
    tools: List<Tool> = emptyList(),
    toolEnabledStates: Map<String, Boolean> = emptyMap(),
    onToolEnabledChanged: (String, Boolean) -> Unit = { _, _ -> },
    systemPrompt: String = "",
    systemPromptId: String? = null,
    recentSystemPrompts: List<SystemPromptEntity> = emptyList(),
    canUpdateLinkedPrompt: Boolean = false,
    activeCartridgeId: String? = null,
    onSelectCartridge: ((AiCartridge?) -> Unit)? = null,
    onParamsChanged: (GenerationParams) -> Unit,
    onUpdateLinkedPrompt: (String) -> Unit = {},
    onSaveAsNewPrompt: (String) -> Unit = {},
    onClearSystemPrompt: () -> Unit = {},
    onApplyPrompt: (SystemPromptEntity) -> Unit = {},
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editedParams by remember(params) { mutableStateOf(params) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var showPromptReviser by remember { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val maxTabHeight = (screenHeightDp * 0.82f).dp
    var paramsContentHeight by remember { mutableStateOf(0.dp) }

    ModalBottomSheet(
        onDismissRequest = {
            if (editedParams != params) {
                onParamsChanged(editedParams)
            }
            onDismiss()
        },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.tab_params), maxLines = 1, softWrap = false) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.tab_prompt), maxLines = 1, softWrap = false) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text(stringResource(R.string.tools), maxLines = 1, softWrap = false) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("ตลับความรู้", maxLines = 1, softWrap = false) }
                )
            }

            when (selectedTab) {
                0 -> ParamsTab(
                    params = editedParams,
                    baselineParams = params,
                    maxContextSize = maxContextSize,
                    supportsThinking = supportsThinking,
                    showAdvanced = showAdvanced,
                    onShowAdvancedChange = { showAdvanced = it },
                    onParamsChange = { editedParams = it },
                    maxHeight = maxTabHeight,
                    onHeightChange = { paramsContentHeight = it }
                )
                1 -> PromptTab(
                    systemPrompt = systemPrompt,
                    systemPromptId = systemPromptId,
                    recentSystemPrompts = recentSystemPrompts,
                    onEditCurrent = { showPromptReviser = true },
                    onClearSystemPrompt = onClearSystemPrompt,
                    onApplyPrompt = onApplyPrompt,
                    minHeight = paramsContentHeight,
                    maxHeight = maxTabHeight
                )
                2 -> ToolsTab(
                    supportsToolCalling = supportsToolCalling,
                    tools = tools,
                    toolEnabledStates = toolEnabledStates,
                    onToolEnabledChanged = onToolEnabledChanged,
                    minHeight = paramsContentHeight,
                    maxHeight = maxTabHeight
                )
                3 -> ChatCartridgeTab(
                    activeCartridgeId = activeCartridgeId,
                    onSelectCartridge = onSelectCartridge,
                    minHeight = paramsContentHeight,
                    maxHeight = maxTabHeight
                )
            }
        }
    }

    if (showPromptReviser) {
        SystemPromptEditorSheet(
            initialText = systemPrompt,
            title = stringResource(
                if (systemPrompt.isEmpty()) R.string.system_prompt_new
                else R.string.system_prompt_edit
            ),
            primaryLabel = stringResource(R.string.system_prompt_update),
            onPrimary = { text ->
                if (canUpdateLinkedPrompt && systemPrompt.isNotEmpty()) {
                    onUpdateLinkedPrompt(text)
                } else {
                    onSaveAsNewPrompt(text)
                }
            },
            onDismiss = { showPromptReviser = false }
        )
    }
}

@Composable
private fun ChatCartridgeTab(
    activeCartridgeId: String?,
    onSelectCartridge: ((AiCartridge?) -> Unit)?,
    minHeight: Dp,
    maxHeight: Dp
) {
    val context = LocalContext.current
    val available = remember { CartridgeManager.getAllAvailableCartridges(context) }
    var inspecting by remember { mutableStateOf<AiCartridge?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight, max = maxHeight)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "ตลับความรู้ RAG ที่เสียบใช้งานกับโมเดลปัจจุบัน",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))

        available.forEach { cart ->
            val isInserted = activeCartridgeId == cart.id
            val accent = Color(cart.colorHex)
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isInserted) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                border = BorderStroke(
                    width = if (isInserted) 1.5.dp else 1.dp,
                    color = if (isInserted) accent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
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
                            onClick = { inspecting = cart },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("รายละเอียด")
                        }
                        if (isInserted) {
                            OutlinedButton(
                                onClick = { onSelectCartridge?.invoke(null) },
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
                                onClick = {
                                    CartridgeManager.installCartridge(context, cart)
                                    onSelectCartridge?.invoke(cart)
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Outlined.Extension, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("เสียบตลับใช้งาน")
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    inspecting?.let { cart ->
        val isInserted = activeCartridgeId == cart.id
        CartridgeDetailDialog(
            cartridge = cart,
            onDismiss = { inspecting = null },
            primaryActionLabel = if (isInserted) null else "เสียบตลับใช้งาน",
            onPrimaryAction = if (isInserted) null else {
                {
                    CartridgeManager.installCartridge(context, cart)
                    onSelectCartridge?.invoke(cart)
                }
            }
        )
    }
}

@Composable
private fun ParamsTab(
    params: GenerationParams,
    baselineParams: GenerationParams,
    maxContextSize: Int,
    supportsThinking: Boolean,
    showAdvanced: Boolean,
    onShowAdvancedChange: (Boolean) -> Unit,
    onParamsChange: (GenerationParams) -> Unit,
    maxHeight: Dp,
    onHeightChange: (Dp) -> Unit
) {
    val contextMin = 512
    val contextMax = maxContextSize.coerceAtLeast(512)
    val contextStep = 512
    val density = LocalDensity.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .onSizeChanged { onHeightChange(with(density) { it.height.toDp() }) }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

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
            ProfileChip(
                text = "แม่นยำ",
                selected = params.temperature <= 0.3f,
                onClick = {
                    onParamsChange(
                        params.copy(
                            temperature = 0.2f,
                            topP = 0.9f,
                            topK = 20,
                            minP = 0.05f,
                            repetitionPenalty = 1.1f
                        )
                    )
                },
                modifier = Modifier.weight(1f)
            )
            ProfileChip(
                text = "สมดุล",
                selected = params.temperature in 0.6f..0.9f,
                onClick = {
                    onParamsChange(
                        params.copy(
                            temperature = 0.8f,
                            topP = 0.95f,
                            topK = 40,
                            minP = 0.05f,
                            repetitionPenalty = 1.1f
                        )
                    )
                },
                modifier = Modifier.weight(1f)
            )
            ProfileChip(
                text = "สร้างสรรค์",
                selected = params.temperature >= 1.1f,
                onClick = {
                    onParamsChange(
                        params.copy(
                            temperature = 1.3f,
                            topP = 0.98f,
                            topK = 80,
                            minP = 0.02f,
                            repetitionPenalty = 1.05f
                        )
                    )
                },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        val contextWarning = params.contextSize != baselineParams.contextSize
        ParamSlider(
            label = stringResource(R.string.context_size),
            value = params.contextSize.toFloat(),
            valueRange = contextMin.toFloat()..contextMax.toFloat(),
            steps = (((contextMax - contextMin) / contextStep) - 1).coerceAtLeast(0),
            valueDisplay = "${params.contextSize}",
            warning = if (contextWarning) stringResource(R.string.will_reset_conversation) else null,
            onValueChange = {
                val snapped = (it / contextStep).roundToInt() * contextStep
                val newContextSize = snapped.coerceIn(contextMin, contextMax)
                val oldContextSize = params.contextSize
                val newBudget = if (oldContextSize > 0) {
                    (params.thinkingBudget.toLong() * newContextSize / oldContextSize).toInt()
                        .coerceIn(64, newContextSize)
                } else {
                    newContextSize / 4
                }
                onParamsChange(params.copy(contextSize = newContextSize, thinkingBudget = newBudget))
            }
        )

        if (supportsThinking) {
            val budgetMin = 64
            val budgetMax = params.contextSize
            ParamSlider(
                label = stringResource(R.string.thinking_budget),
                value = params.thinkingBudget.toFloat(),
                valueRange = budgetMin.toFloat()..budgetMax.toFloat(),
                steps = 0,
                valueDisplay = stringResource(R.string.tokens_value, params.thinkingBudget),
                onValueChange = {
                    val snapped = (it / 64).roundToInt() * 64
                    onParamsChange(
                        params.copy(thinkingBudget = snapped.coerceIn(budgetMin, budgetMax))
                    )
                }
            )
        }

        ParamSlider(
            label = stringResource(R.string.temperature),
            value = params.temperature,
            valueRange = 0f..2f,
            steps = 0,
            valueDisplay = "%.2f".format(params.temperature),
            onValueChange = {
                onParamsChange(params.copy(temperature = (it * 100).roundToInt() / 100f))
            }
        )

        ParamSlider(
            label = stringResource(R.string.top_p),
            value = params.topP,
            valueRange = 0f..1f,
            steps = 0,
            valueDisplay = "%.2f".format(params.topP),
            subtitle = if (params.topP >= 1f) stringResource(R.string.disabled_label) else null,
            onValueChange = {
                onParamsChange(params.copy(topP = (it * 100).roundToInt() / 100f))
            }
        )

        ParamSlider(
            label = stringResource(R.string.repetition_penalty),
            value = params.repetitionPenalty,
            valueRange = 1f..2f,
            steps = 0,
            valueDisplay = "%.2f".format(params.repetitionPenalty),
            subtitle = if (params.repetitionPenalty <= 1f) stringResource(R.string.disabled_label) else null,
            onValueChange = {
                onParamsChange(params.copy(repetitionPenalty = (it * 100).roundToInt() / 100f))
            }
        )

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onShowAdvancedChange(!showAdvanced) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.advanced),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null
            )
        }

        AnimatedVisibility(visible = showAdvanced) {
            Column {
                ParamSlider(
                    label = stringResource(R.string.top_k),
                    value = params.topK.toFloat(),
                    valueRange = 0f..200f,
                    steps = 0,
                    valueDisplay = "${params.topK}",
                    subtitle = if (params.topK == 0) stringResource(R.string.disabled_label) else null,
                    onValueChange = {
                        onParamsChange(params.copy(topK = it.roundToInt()))
                    }
                )

                ParamSlider(
                    label = stringResource(R.string.min_p),
                    value = params.minP,
                    valueRange = 0f..0.5f,
                    steps = 0,
                    valueDisplay = "%.3f".format(params.minP),
                    subtitle = if (params.minP <= 0f) stringResource(R.string.disabled_label) else null,
                    onValueChange = {
                        onParamsChange(params.copy(minP = (it * 1000).roundToInt() / 1000f))
                    }
                )

                var seedText by remember(params.seed) {
                    mutableStateOf(if (params.seed < 0) "" else params.seed.toString())
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.seed),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = seedText,
                        onValueChange = { text ->
                            seedText = text
                            val value = text.toIntOrNull() ?: -1
                            onParamsChange(params.copy(seed = value))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp),
                        placeholder = { Text(stringResource(R.string.random)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = { onParamsChange(GenerationParams()) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.reset))
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ProfileChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        modifier = modifier
    )
}

@Composable
private fun PromptTab(
    systemPrompt: String,
    systemPromptId: String?,
    recentSystemPrompts: List<SystemPromptEntity>,
    onEditCurrent: () -> Unit,
    onClearSystemPrompt: () -> Unit,
    onApplyPrompt: (SystemPromptEntity) -> Unit,
    minHeight: Dp,
    maxHeight: Dp
) {
    val hasPrompt = systemPrompt.isNotEmpty()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight, max = maxHeight)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.system_prompt_current),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = onClearSystemPrompt,
                enabled = hasPrompt,
                modifier = Modifier.alpha(if (hasPrompt) 1f else 0f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(stringResource(R.string.clear))
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onEditCurrent() },
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = if (hasPrompt) systemPrompt
                else stringResource(R.string.system_prompt_current_empty),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = if (hasPrompt) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 3,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (recentSystemPrompts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.saved_prompts),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            for (prompt in recentSystemPrompts) {
                SavedPromptCard(
                    prompt = prompt,
                    selected = prompt.id == systemPromptId,
                    onClick = { onApplyPrompt(prompt) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SavedPromptCard(
    prompt: SystemPromptEntity,
    selected: Boolean,
    onClick: () -> Unit
) {
    val border: BorderStroke = if (selected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        CardDefaults.outlinedCardBorder()
    }
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        border = border,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = prompt.text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            minLines = 2,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ToolsTab(
    supportsToolCalling: Boolean,
    tools: List<Tool>,
    toolEnabledStates: Map<String, Boolean>,
    onToolEnabledChanged: (String, Boolean) -> Unit,
    minHeight: Dp,
    maxHeight: Dp
) {
    if (supportsToolCalling && tools.isNotEmpty()) {
        ToolsContent(
            tools = tools,
            enabledStates = toolEnabledStates,
            onToolEnabledChanged = onToolEnabledChanged,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight, max = maxHeight)
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight, max = maxHeight),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.tools_unsupported),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

@Composable
private fun ParamSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueDisplay: String,
    subtitle: String? = null,
    warning: String? = null,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = label, style = MaterialTheme.typography.bodyMedium)
                if (subtitle != null) {
                    Text(
                        text = " ($subtitle)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = valueDisplay,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
        if (warning != null) {
            Text(
                text = warning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
