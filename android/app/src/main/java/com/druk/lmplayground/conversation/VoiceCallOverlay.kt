package com.druk.ai_offline_llm.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun VoiceCallOverlay(
    modelName: String,
    isListening: Boolean,
    isGenerating: Boolean,
    isSpeaking: Boolean,
    liveText: String,
    onMicClick: () -> Unit,
    onEndCall: () -> Unit,
) {
    val statusLabel = when {
        isGenerating -> "กำลังประมวลผลคำตอบ..."
        isSpeaking -> "กำลังพูดตอบกลับ..."
        isListening -> "กำลังฟังเสียงของคุณ..."
        else -> "แตะไมค์เพื่อพูดคุย"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF20D1017))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "โหมดคุยสายสด (OFFLINE)",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF4ADE80),
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = modelName,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = when {
                        isGenerating -> Color(0xFF2563EB)
                        isSpeaking -> Color(0xFF16A34A)
                        isListening -> Color(0xFFDC2626)
                        else -> Color(0xFF1F2937)
                    },
                    modifier = Modifier.size(128.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = when {
                                isGenerating -> "คิด"
                                isSpeaking -> "พูด"
                                isListening -> "ฟัง"
                                else -> "พร้อม"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFE5E7EB)
                )
                if (liveText.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = liveText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF93C5FD),
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(36.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                FilledIconButton(
                    onClick = onMicClick,
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isListening) Color(0xFFDC2626) else Color(0xFF374151)
                    )
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Filled.Mic else Icons.Filled.MicOff,
                        contentDescription = "ไมโครโฟน",
                        tint = Color.White
                    )
                }

                FilledIconButton(
                    onClick = onEndCall,
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color(0xFFEF4444)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.CallEnd,
                        contentDescription = "วางสาย",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
