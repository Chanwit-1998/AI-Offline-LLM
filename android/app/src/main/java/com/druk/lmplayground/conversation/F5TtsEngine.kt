package com.druk.lmplayground.conversation

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Environment
import android.speech.tts.TextToSpeech
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import java.nio.ShortBuffer
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

object F5TtsEngine {
    private val MODEL_DIRS = listOf(
        "/sdcard/Termux/Ai/Models/03_TTS/F5_V4",
        File(Environment.getExternalStorageDirectory(), "Download/F5_V4").absolutePath
    )
    private const val REF_TEXT = "สวัสดีค่ะ ยินดีต้อนรับเข้าสู่ระบบผู้ช่วยอัจฉริยะนะคะ"
    private const val SAMPLE_RATE = 24000
    private const val NUM_STEPS = 20

    private val DICT = mapOf(
        "สวัสดี" to "sa\u0300\u0294 wa\u0300t di\u02d0", "ค่ะ" to "k\u02b0a\u0302\u0294",
        "คะ" to "k\u02b0a\u0301\u0294", "นะคะ" to "na\u0301\u0294 k\u02b0a\u0301\u0294",
        "ครับ" to "k\u02b0ra\u0301p", "ยินดี" to "jin di\u02d0", "ต้อนรับ" to "t\u0254\u0358\u02d0n ra\u0301p",
        "เข้าสู่" to "k\u02b0a\u0302w su\u0300\u02d0", "ระบบ" to "ra\u0301\u0294 bo\u0300p",
        "ผู้ช่วย" to "p\u02b0u\u02d0\u0358 t\u0255\u02b0u\u0358aj", "อัจฉริยะ" to "\u0294a\u0300t t\u0255\u02b0a\u0300\u0294 ri\u0301\u0294 ja\u0301\u0294",
        "วันนี้" to "wan ni\u0301\u02d0", "มี" to "mi\u02d0", "เรื่อง" to "r\u026fa\u0302\u014b",
        "ไหน" to "na\u030c\u0268", "ให้" to "ha\u0302\u0268", "ช่วย" to "t\u0255\u02b0u\u0358aj",
        "ดูแล" to "du\u02d0 l\u025b\u02d0", "สามารถ" to "sa\u030c\u02d0 ma\u0302\u02d0t",
        "พิมพ์" to "p\u02b0im", "หรือ" to "r\u026f\u030c\u02d0", "พูดคุย" to "p\u02b0u\u0358\u02d0t k\u02b0uj",
        "ได้" to "da\u0302\u0268", "เลย" to "l\u0259\u02d0j", "พร้อม" to "p\u02b0r\u0254\u0301\u02d0m",
        "ใช้งาน" to "t\u0255\u02b0a\u0301\u0268 \u014ba\u02d0n", "แล้ว" to "l\u025b\u0301\u02d0w",
        "เสียง" to "si\u030ca\u02d0\u014b", "ออฟไลน์" to "\u0294\u0254\u0301\u02d0p la\u0268",
        "ออนไลน์" to "\u0294\u0254\u0301\u02d0n la\u0268", "โมเดล" to "mo\u02d0 de\u02d0n",
        "ภาษาไทย" to "p\u02b0a\u0304 sa\u030c\u02d0 t\u02b0aj", "อะไร" to "\u0294a\u0300\u0294 raj",
        "ไหม" to "ma\u030c\u0268", "ขอบคุณ" to "k\u02b0\u0254\u0300\u02d0p k\u02b0un",
        "เข้าใจ" to "k\u02b0a\u0302w t\u0255aj", "กำลัง" to "kam la\u014b",
        "เสร็จ" to "se\u0300t", "เรียบร้อย" to "ri\u0302ap r\u0254\u0301\u02d0j"
    )

    fun textToIpa(text: String): String {
        val sb = StringBuilder()
        var i = 0
        val sortedKeys = DICT.keys.sortedByDescending { it.length }
        while (i < text.length) {
            val ch = text[i]
            if (ch.isWhitespace() || ch in ",.?-") {
                sb.append(ch); i++; continue
            }
            val hit = sortedKeys.firstOrNull { text.startsWith(it, i) }
            if (hit != null) {
                sb.append(DICT[hit]).append(' '); i += hit.length; continue
            }
            if (ch in '\u0E00'..'\u0E7F') {
                sb.append(ch)
            } else if (ch.code < 128) {
                sb.append(ch)
            }
            i++
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    

    private val speakingFlag = AtomicBoolean(false)
    @Volatile private var activeTrack: AudioTrack? = null
    @Volatile private var fallbackTts: TextToSpeech? = null

    fun isSpeaking(): Boolean = speakingFlag.get() || (activeTrack?.playState == AudioTrack.PLAYSTATE_PLAYING)

    fun stop() {
        speakingFlag.set(false)
        try { activeTrack?.pause(); activeTrack?.flush(); activeTrack?.release() } catch (_: Exception) {}
        activeTrack = null
        try { fallbackTts?.stop() } catch (_: Exception) }
    }

    private fun findModelDir(): File? = MODEL_DIRS.map(::File).firstOrNull { dir ->
        File(dir, "Preprocess.onnx").exists() &&
        File(dir, "Transformer.onnx").exists() &&
        File(dir, "Vocoder.onnx").exists() &&
        File(dir, "vocab.json").exists() &&
        File(dir, "ref_voice.wav").exists()
    }

    private fun loadVocab(file: File): Map<String, Int> {
        val root = JSONObject(file.readText(Charsets.UTF_8))
        val obj = root.optJSONObject("vocab") ?: root
        val map = HashMap<String, Int>(obj.length())
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            map[k] = obj.optInt(k, -1)
        }
        return map
    }

    private fun readPcm16Mono(wavFile: File): ShortArray {
        val bytes = wavFile.readBytes()
        if (bytes.size <= 44) return ShortArray(0)
        val bb = ByteBuffer.wrap(bytes, 44, bytes.size - 44).order(ByteOrder.LITTLE_ENDIAN)
        val out = ShortArray(bb.remaining() / 2)
        bb.asShortBuffer().get(out)
        return out
    }

    private fun playSamples(samples: FloatArray) {
        if (samples.isEmpty() || !speakingFlag.get()) return
        val pcm = ShortArray(samples.size) { idx ->
            val v = (samples[idx] * 32767f).toInt().coerceIn(-32768, 32767)
            v.toShort()
        }
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(pcm.size * 2)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        activeTrack = track
        track.write(pcm, 0, pcm.size)
        track.play()
        var slept = 0L
        val totalMs = ((pcm.size.toLong() * 1000L) / SAMPLE_RATE).coerceAtLeast(100L)
        while (speakingFlag.get() && slept < totalMs) {
            Thread.sleep(60L)
            slept += 60L
        }
        try { track.stop(); track.release() } catch (_: Exception) {}
        if (activeTrack === track) activeTrack = null
    }

    private fun speakWithSystemTts(context: Context, text: String, onStart: () -> Unit, onDone: () -> Unit) {
        val tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                fallbackTts?.let {
                    it.language = if (text.any { c -> c in '\u0E00'..'\u0E7F' }) Locale("th", "TH") else Locale.getDefault()
                    onStart()
                    it.speak(text, TextToSpeech.QUEUE_FLUSH, null, "f5_fb")
                }
            } else {
                speakingFlag.set(false)
                onDone()
            }
        }
        fallbackTts = tts
    }

    suspend fun speakChunks(
        context: Context,
        chunks: List<String>,
        onStart: () -> Unit,
        onDone: () -> Unit
    ) = withContext(Dispatchers.Default) {
        val validChunks = chunks.map { it.trim() }.filter { it.isNotEmpty() }
        if (validChunks.isEmpty()) {
            withContext(Dispatchers.Main) { onDone() }
            return@withContext
        }
        stop()
        speakingFlag.set(true)
        val modelDir = findModelDir()
        if (modelDir == null) {
            withContext(Dispatchers.Main) {
                speakWithSystemTts(context, validChunks.joinToString(" "), onStart, onDone)
            }
            return@withContext
        }

        var env: OrtEnvironment? = null
        var sPre: OrtSession? = null
        var sTr: OrtSession? = null
        var sVoc: OrtSession? = null
        try {
            val vocab = loadVocab(File(modelDir, "vocab.json"))
            val refAudio = readPcm16Mono(File(modelDir, "ref_voice.wav"))
            val refIpa = textToIpa(REF_TEXT)
            val refFrames = (refAudio.size / 256).coerceAtLeast(1)

            env = OrtEnvironment.getEnvironment()
            val opts = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(4)
                try { addXnnpack(emptyMap()) } catch (_: Exception) {}
            }
            sPre = env.createSession(File(modelDir, "Preprocess.onnx").absolutePath, opts)
            sTr = env.createSession(File(modelDir, "Transformer.onnx").absolutePath, opts)
            sVoc = env.createSession(File(modelDir, "Vocoder.onnx").absolutePath, opts)

            withContext(Dispatchers.Main) { if (speakingFlag.get()) onStart() }

            for (chunk in validChunks) {
                if (!speakingFlag.get()) break
                val genIpa = textToIpa(chunk)
                val fullIpa = "$refIpa $genIpa".trim()
                val idsList = fullIpa.mapNotNull { ch -> vocab[ch.toString()]?.takeIf { it >= 0 } }
                if (idsList.isEmpty()) continue
                val textIds = idsList.toIntArray()
                val genFrames = ((refFrames.toFloat() * (genIpa.length.coerceAtLeast(1).toFloat() / refIpa.length.coerceAtLeast(1).toFloat())) * 1.05f)
                    .toInt().coerceAtLeast(35)
                val maxDur = longArrayOf((refFrames + genFrames).toLong())

                val tAudio = OnnxTensor.createTensor(env, ShortBuffer.wrap(refAudio), longArrayOf(1L, 1L, refAudio.size.toLong()))
                val tIds = OnnxTensor.createTensor(env, IntBuffer.wrap(textIds), longArrayOf(1L, textIds.size.toLong()))
                val tDur = OnnxTensor.createTensor(env, LongBuffer.wrap(maxDur), longArrayOf(1L))

                val preOut = sPre.run(mapOf("audio" to tAudio, "text_ids" to tIds, "max_duration" to tDur))
                var noiseTensor = preOut.get(0) as OnnxTensor
                val rCosQ = preOut.get(1) as OnnxTensor
                val rSinQ = preOut.get(2) as OnnxTensor
                val rCosK = preOut.get(3) as OnnxTensor
                val rSinK = preOut.get(4) as OnnxTensor
                val catMel = preOut.get(5) as OnnxTensor
                val catDrop = preOut.get(6) as OnnxTensor
                val refLenTensor = preOut.get(7) as OnnxTensor

                var stepIdx = 0
                while (stepIdx < NUM_STEPS && speakingFlag.get()) {
                    val tStep = OnnxTensor.createTensor(env, IntBuffer.wrap(intArrayOf(stepIdx)), longArrayOf(1L))
                    val trOut = try {
                        sTr.run(mapOf(
                            "noise" to noiseTensor, "rope_cos_q" to rCosQ, "rope_sin_q" to rSinQ,
                            "rope_cos_k" to rCosK, "rope_sin_k" to rSinK,
                            "cat_mel_text" to catMel, "cat_mel_text_drop" to catDrop,
                            "time_step.1" to tStep
                        ))
                    } catch (_: Exception) {
                        tStep.close()
                        break
                    }
                    tStep.close()
                    if (noiseTensor !== preOut.get(0)) noiseTensor.close()
                    noiseTensor = trOut.get(0) as OnnxTensor
                    stepIdx++
                }

                val vocOut = sVoc.run(mapOf("denoised" to noiseTensor, "ref_signal_len" to refLenTensor))
                val vocTensor = vocOut.get(0) as OnnxTensor
                val fb: FloatBuffer = vocTensor.floatBuffer
                val samples = FloatArray(fb.remaining())
                fb.get(samples)

                vocOut.close()
                if (noiseTensor !== preOut.get(0)) noiseTensor.close()
                preOut.close()
                tAudio.close(); tIds.close(); tDur.close()

                playSamples(samples)
            }
        } catch (_: Exception) {
            if (speakingFlag.get()) {
                withContext(Dispatchers.Main) {
                    speakWithSystemTts(context, validChunks.joinToString(" "), onStart, onDone)
                }
                return@withContext
            }
        } finally {
            try { sPre?.close(); sTr?.close(); sVoc?.close() } catch (_: Exception) {}
            speakingFlag.set(false)
            withContext(Dispatchers.Main) { onDone() }
        }
