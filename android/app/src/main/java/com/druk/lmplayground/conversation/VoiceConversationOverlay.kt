package com.druk.ai_offline_llm.conversation

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import coil.compose.AsyncImage
import com.druk.ai_offline_llm.R
import com.druk.ai_offline_llm.dictation.DictationState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun VoiceConversationOverlay(
    visible: Boolean, isModelReady: Boolean, modelName: String?, isGenerating: Boolean,
    messages: List<Message>, micOn: Boolean, micRecording: StateFlow<Boolean>, micLevel: StateFlow<Float>,
    dictationState: DictationState, pendingTranscript: String?, onTranscriptConsumed: () -> Unit,
    supportsVision: Boolean, cameraAvailable: Boolean, attachedImageUri: Uri?,
    onAttachImage: () -> Unit, onTakePhoto: () -> Unit, onAttachDocument: () -> Unit,
    onClearImage: () -> Unit, onStartListening: () -> Unit, onStopListening: () -> Unit,
    onSendMessage: (String) -> Unit, onCancelGeneration: () -> Unit,
    onSelectModelClicked: () -> Unit, onClose: () -> Unit,
) {
    if (!visible) return
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var userMuted by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var awaitingReply by remember { mutableStateOf(false) }
    var wasGenerating by remember { mutableStateOf(false) }
    var ttsReady by remember { mutableStateOf(false) }
    var ttsInstance by remember { mutableStateOf<TextToSpeech?>(null) }
    var attachMenuExpanded by remember { mutableStateOf(false) }
    val isRecording by micRecording.collectAsState()
    val currentLevel by micLevel.collectAsState()

    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val res = engine?.setLanguage(Locale("th", "TH"))
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine?.setLanguage(Locale.getDefault())
                }
                ttsReady = true
            }
        }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { mainHandler.post { isSpeaking = true } }
            override fun onDone(id: String?) {
                mainHandler.post {
                    isSpeaking = false
                    if (visible && isModelReady && !userMuted && !isGenerating) {
                        mainHandler.postDelayed({
                            if (visible && isModelReady && !userMuted && !isGenerating && !isSpeaking) onStartListening()
                        }, 350L)
                    }
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                mainHandler.post {
                    isSpeaking = false
                    if (visible && isModelReady && !userMuted && !isGenerating) onStartListening()
                }
            }
        })
        ttsInstance = engine
        onDispose { runCatching { engine?.stop(); engine?.shutdown() }; ttsInstance = null }
    }

    LaunchedEffect(visible, isModelReady) {
        if (visible && isModelReady && !userMuted && !isGenerating && !isSpeaking && !micOn) {
            delay(250L)
            onStartListening()
        }
    }
    LaunchedEffect(pendingTranscript, visible) {
        if (!visible) return@LaunchedEffect
        val text = pendingTranscript?.trim().orEmpty()
        if (text.isNotEmpty()) {
            onTranscriptConsumed()
            if (isModelReady) { awaitingReply = true; onSendMessage(text) }
        } else if (pendingTranscript != null) onTranscriptConsumed()
    }
    LaunchedEffect(micOn, isGenerating, isSpeaking, userMuted, isModelReady) {
        if (visible && isModelReady && !micOn && !isGenerating && !isSpeaking && !userMuted && !awaitingReply) {
            delay(650L)
            if (visible && isModelReady && !micOn && !isGenerating && !isSpeaking && !userMuted && !awaitingReply) onStartListening()
        }
    }
    LaunchedEffect(isGenerating) {
        if (wasGenerating && !isGenerating && awaitingReply) {
            awaitingReply = false
            val reply = messages.lastOrNull { it.author != "User" }?.content.orEmpty()
            val clean = cleanTextForSpeech(reply)
            if (clean.isNotEmpty() && ttsReady && ttsInstance != null) {
                isSpeaking = true
                ttsInstance?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "vr_${System.currentTimeMillis()}")
            } else if (!userMuted && isModelReady) onStartListening()
        }
        wasGenerating = isGenerating
    }

    val rawActivity = when {
        micOn && isRecording -> currentLevel.coerceIn(0f, 1f)
        isSpeaking -> 0.52f
        isGenerating -> 0.28f
        micOn -> 0.14f
        else -> 0.04f
    }
    val smoothVoice by animateFloatAsState(
        targetValue = rawActivity,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "voiceSmooth"
    )

    val infinite = rememberInfiniteTransition(label = "orbFlow")
    val driftPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2.0).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "driftPhase"
    )
    val slowWave by infinite.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "slowWave"
    )

    val listeningText = (dictationState as? DictationState.Listening)?.text.orEmpty()
    val latestReply = messages.lastOrNull { it.author != "User" }?.content.orEmpty()

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF8F8F7)) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Top status header
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 20.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!isModelReady) {
                    Surface(
                        onClick = onSelectModelClicked,
                        shape = CircleShape,
                        color = Color.White,
                        shadowElevation = 2.dp
                    ) {
                        Text(
                            text = "โปรดเลือกโมเดล เพื่อสนทนา",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF1F242D),
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)
                        )
                    }
                } else {
                    Text(
                        text = when {
                            isSpeaking -> "กำลังพูดตอบกลับ..."
                            isGenerating -> "กำลังคิดคำตอบ..."
                            micOn -> "กำลังฟังเสียงคุณ..."
                            else -> "ปิดไมค์อยู่ • แตะลูกบอลเพื่อพูด"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF4A5260)
                    )
                    if (!modelName.isNullOrBlank()) {
                        Text(
                            text = modelName,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF7B8494),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // Center animated pastel orb + live transcript
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (attachedImageUri != null) {
                    Box(modifier = Modifier.padding(bottom = 14.dp)) {
                        AsyncImage(
                            model = attachedImageUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(84.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.5.dp, Color(0xFF9E8CF2), RoundedCornerShape(16.dp))
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF141414))
                                .clickable(onClick = onClearImage),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                }

                PastelFluidOrb(
                    voiceLevel = smoothVoice,
                    driftPhase = driftPhase,
                    breathScale = slowWave,
                    isActive = micOn || isSpeaking || isGenerating,
                    onClick = {
                        when {
                            isSpeaking -> {
                                runCatching { ttsInstance?.stop() }
                                isSpeaking = false
                                userMuted = false
                                if (isModelReady) onStartListening()
                            }
                            isGenerating -> {
                                onCancelGeneration()
                                awaitingReply = false
                                userMuted = false
                                if (isModelReady) onStartListening()
                            }
                            micOn -> {
                                userMuted = true
                                onStopListening()
                            }
                            else -> {
                                userMuted = false
                                if (isModelReady) onStartListening() else onSelectModelClicked()
                            }
                        }
                    }
                )

                val showTxt = when {
                    listeningText.isNotBlank() -> listeningText
                    isSpeaking && latestReply.isNotBlank() -> latestReply
                    else -> ""
                }
                if (showTxt.isNotBlank()) {
                    Text(
                        text = showTxt,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF23272F),
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = 24.dp)
                            .widthIn(max = 360.dp)
                    )
                }
            }

            // Bottom dock: + (left), mic state pill (center), X (right)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val density = LocalDensity.current
                Box {
                    Surface(
                        onClick = { if (supportsVision) attachMenuExpanded = true else onAttachDocument() },
                        shape = CircleShape,
                        color = Color.White,
                        shadowElevation = 3.dp,
                        modifier = Modifier.size(54.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "แนบไฟล์หรือรูปภาพ",
                                tint = Color(0xFF141414),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    if (attachMenuExpanded) {
                        val gapPx = with(density) { 14.dp.roundToPx() }
                        val pos = remember(gapPx) {
                            object : PopupPositionProvider {
                                override fun calculatePosition(
                                    anchorBounds: IntRect,
                                    windowSize: IntSize,
                                    layoutDirection: LayoutDirection,
                                    popupContentSize: IntSize
                                ) = IntOffset(
                                    anchorBounds.left.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
                                    (anchorBounds.top - gapPx - popupContentSize.height).coerceAtLeast(0)
                                )
                            }
                        }
                        Popup(
                            popupPositionProvider = pos,
                            onDismissRequest = { attachMenuExpanded = false },
                            properties = PopupProperties(focusable = true)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White,
                                shadowElevation = 6.dp
                            ) {
                                Column(Modifier.width(IntrinsicSize.Max).padding(vertical = 6.dp)) {
                                    if (cameraAvailable) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.take_photo)) },
                                            leadingIcon = { Icon(Icons.Outlined.PhotoCamera, null) },
                                            onClick = { attachMenuExpanded = false; onTakePhoto() }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.choose_from_library)) },
                                        leadingIcon = { Icon(Icons.Outlined.Image, null) },
                                        onClick = { attachMenuExpanded = false; onAttachImage() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.attach_document)) },
                                        leadingIcon = { Icon(Icons.Outlined.Description, null) },
                                        onClick = { attachMenuExpanded = false; onAttachDocument() }
                                    )
                                }
                            }
                        }
                    }
                }

                Surface(
                    onClick = {
                        if (micOn) {
                            userMuted = true
                            onStopListening()
                        } else {
                            userMuted = false
                            if (isModelReady) onStartListening() else onSelectModelClicked()
                        }
                    },
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 2.dp,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (micOn || isSpeaking || isGenerating) Icons.Filled.Mic else Icons.Filled.MicOff,
                            contentDescription = "เปิด/ปิดไมค์",
                            tint = if (micOn) Color(0xFF7D6CF0) else Color(0xFF6A7280),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Surface(
                    onClick = {
                        runCatching { ttsInstance?.stop() }
                        isSpeaking = false
                        onClose()
                    },
                    shape = CircleShape,
                    color = Color(0xFF111111),
                    shadowElevation = 3.dp,
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "ปิดโหมดสนทนาด้วยเสียง",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PastelFluidOrb(
    voiceLevel: Float,
    driftPhase: Float,
    breathScale: Float,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val dynamicScale = breathScale + voiceLevel * 0.14f

    Box(
        modifier = Modifier
            .size(236.dp)
            .graphicsLayer {
                scaleX = dynamicScale
                scaleY = dynamicScale
            }
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val r = minOf(w, h) * 0.5f

            val cyanCenter = Offset(
                x = w * (0.36f + 0.08f * cos(driftPhase)),
                y = h * (0.28f + 0.07f * sin(driftPhase))
            )
            val pinkCenter = Offset(
                x = w * (0.76f + 0.07f * cos(driftPhase + 1.8f)),
                y = h * (0.56f + 0.08f * sin(driftPhase + 1.8f))
            )
            val lavenderCenter = Offset(
                x = w * (0.32f + 0.08f * cos(driftPhase + 3.6f)),
                y = h * (0.74f + 0.06f * sin(driftPhase + 3.6f))
            )
            val coreCenter = Offset(
                x = w * (0.50f + 0.05f * sin(driftPhase * 0.8f)),
                y = h * (0.48f + 0.05f * cos(driftPhase * 0.8f))
            )

            // Base pastel wash: cyan-mint -> soft periwinkle -> lilac
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF9DF0EC),
                        Color(0xFFAECBF8),
                        Color(0xFFC3A8F5)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                ),
                radius = r
            )

            // Top-left aqua-mint glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFB5F7F2).copy(alpha = 0.92f),
                        Color(0xFF9CE3F7).copy(alpha = 0.45f),
                        Color.Transparent
                    ),
                    center = cyanCenter,
                    radius = r * 1.05f
                ),
                radius = r
            )

            // Bottom-left lavender-blue blend
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF9A9CF5).copy(alpha = 0.78f),
                        Color(0xFFA7C7F9).copy(alpha = 0.35f),
                        Color.Transparent
                    ),
                    center = lavenderCenter,
                    radius = r * 0.95f
                ),
                radius = r
            )

            // Right soft pink-rose cloud
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFF6B9EC).copy(alpha = 0.88f + voiceLevel * 0.10f),
                        Color(0xFFE7B6F5).copy(alpha = 0.42f),
                        Color.Transparent
                    ),
                    center = pinkCenter,
                    radius = r * 0.92f
                ),
                radius = r
            )

            // Luminous milky center highlight
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isActive) 0.52f else 0.42f),
                        Color(0xFFEAF7FF).copy(alpha = 0.20f),
                        Color.Transparent
                    ),
                    center = coreCenter,
                    radius = r * 0.72f
                ),
                radius = r
            )
        }
    }
}
