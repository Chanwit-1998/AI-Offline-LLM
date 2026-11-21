@file:OptIn(ExperimentalMaterial3Api::class)

package com.druk.ai_offline_llm.conversation

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.druk.ai_offline_llm.AppBar
import com.druk.ai_offline_llm.cartridge.AiCartridge
import com.druk.ai_offline_llm.models.ModelInfo

const val ConversationBarTestTag = "ConversationBarTestTag"

@Composable
fun ConversationBar(
    modelInfo: ModelInfo?,
    modelStatus: String? = null,
    activeCartridge: AiCartridge? = null,
    voiceReplyEnabled: Boolean = false,
    continuousLoopEnabled: Boolean = false,
    isSpeaking: Boolean = false,
    onToggleVoiceMode: () -> Unit = { },
    modifier: Modifier = Modifier,
    colors: TopAppBarColors? = null,
    showNavIcon: Boolean = true,
    compact: Boolean = false,
    onModelNamePressed: () -> Unit = { },
    onNavIconPressed: () -> Unit = { },
    onNewSessionPressed: () -> Unit = { },
    onBagClicked: () -> Unit = { }
) {
    AppBar(
        modifier = modifier.testTag(ConversationBarTestTag),
        colors = colors,
        showNavIcon = showNavIcon,
        expandedHeight = if (compact) 40.dp else Dp.Unspecified,
        onNavIconPressed = onNavIconPressed,
        title = {
            if (modelInfo != null) {
                if (compact) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable(onClick = onModelNamePressed)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = modelInfo.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val cartText = activeCartridge?.let { "ตลับ: ${it.name}" } ?: (modelStatus ?: modelInfo.description)
                        if (cartText.isNotBlank()) {
                            Text(
                                text = "  ·  $cartText",
                                style = MaterialTheme.typography.bodySmall,
                                color = activeCartridge?.let { Color(it.colorHex) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (activeCartridge != null) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable(onClick = onModelNamePressed)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = modelInfo.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val sub = when {
                            continuousLoopEnabled -> "โหมดคุยต่อเนื่อง (Hands-Free)"
                            voiceReplyEnabled -> if (isSpeaking) "กำลังอ่านคำตอบ..." else "เปิดเสียงพูดตอบกลับ"
                            activeCartridge != null -> "ตลับ: ${activeCartridge.name}"
                            !modelStatus.isNullOrBlank() -> modelStatus
                            else -> "ตลับ: ยังไม่เสียบตลับ"
                        }
                        Text(
                            text = sub,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (voiceReplyEnabled || activeCartridge != null) FontWeight.SemiBold else FontWeight.Normal,
                            color = when {
                                continuousLoopEnabled -> Color(0xFF4ADE80)
                                voiceReplyEnabled -> MaterialTheme.colorScheme.primary
                                activeCartridge != null -> Color(activeCartridge.colorHex)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable(onClick = onModelNamePressed)
                ) {
                    Text(
                        text = "โปรดเลือกโมเดล เพื่อสนทนา",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onToggleVoiceMode) {
                val icon = when {
                    continuousLoopEnabled -> Icons.Outlined.RecordVoiceOver
                    voiceReplyEnabled -> Icons.AutoMirrored.Filled.VolumeUp
                    else -> Icons.AutoMirrored.Filled.VolumeOff
                }
                val tint = when {
                    continuousLoopEnabled -> Color(0xFF4ADE80)
                    voiceReplyEnabled -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Icon(
                    imageVector = icon,
                    contentDescription = "สลับโหมดเสียงพูดตอบกลับ",
                    tint = tint
                )
            }
        }
    )
}
