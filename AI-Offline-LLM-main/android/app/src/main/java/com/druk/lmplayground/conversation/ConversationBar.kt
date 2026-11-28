@file:OptIn(ExperimentalMaterial3Api::class)

package com.druk.ai_offline_llm.conversation

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.druk.ai_offline_llm.AppBar
import com.druk.ai_offline_llm.cartridge.AiCartridge
import com.druk.ai_offline_llm.models.ModelInfo
import com.druk.ai_offline_llm.theme.PlaygroundTheme
import java.time.LocalDate

const val ConversationBarTestTag = "ConversationBarTestTag"

@Composable
fun ConversationBar(
    modelInfo: ModelInfo?,
    modelStatus: String? = null,
    activeCartridge: AiCartridge? = null,
    onlineModeEnabled: Boolean = false,
    onlineLabel: String? = null,
    modifier: Modifier = Modifier,
    colors: TopAppBarColors? = null,
    showNavIcon: Boolean = true,
    compact: Boolean = false,
    onModelNamePressed: () -> Unit = { },
    onNavIconPressed: () -> Unit = { },
    onNewSessionPressed: () -> Unit = { },
    onBagClicked: () -> Unit = { },
    onParamsPressed: () -> Unit = { }
) {
    AppBar(
        modifier = modifier.testTag(ConversationBarTestTag),
        colors = colors,
        showNavIcon = showNavIcon,
        expandedHeight = if (compact) 48.dp else Dp.Unspecified,
        onNavIconPressed = onNavIconPressed,
        title = {
            ModelPill(
                modelInfo = modelInfo,
                modelStatus = modelStatus,
                activeCartridge = activeCartridge,
                onlineModeEnabled = onlineModeEnabled,
                onlineLabel = onlineLabel,
                onClick = onModelNamePressed
            )
        },
    )
}

@Composable
private fun ModelPill(
    modelInfo: ModelInfo?,
    modelStatus: String?,
    activeCartridge: AiCartridge?,
    onlineModeEnabled: Boolean,
    onlineLabel: String?,
    onClick: () -> Unit
) {
    val isReady = onlineModeEnabled || modelInfo != null
    val label = when {
        onlineModeEnabled -> onlineLabel?.takeIf { it.isNotBlank() } ?: "API ออนไลน์"
        modelInfo != null -> {
            val sizePart = modelInfo.description
                .substringAfter("·", "")
                .substringAfter("·", "")
                .trim()
                .ifBlank { modelInfo.description }
            if (sizePart.isNotBlank() && sizePart != modelInfo.name) {
                "${modelInfo.name} · $sizePart"
            } else {
                modelInfo.name
            }
        }
        else -> "โปรดเลือกโมเดล เพื่อสนทนา"
    }

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (isReady) Color(0xFF22C55E)
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )

            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (isReady) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp)
            )

            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Preview
@Composable
fun ChannelBarPreview() {
    PlaygroundTheme {
        ConversationBar(modelInfo = null)
    }
}

@Preview("Bar with model info")
@Composable
fun ChannelBarWithModelInfoPreview() {
    PlaygroundTheme {
        ConversationBar(
            modelInfo = ModelInfo(
                name = "Qwen 3.5 0.8B",
                filename = "model.gguf",
                remoteUri = Uri.parse("https://example.com/model.gguf"),
                releaseDate = LocalDate.parse("2025-01-01"),
                description = "512 MB"
            ),
            modelStatus = "พร้อมใช้งาน"
        )
    }
}
