package com.druk.ai_offline_llm.conversation

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.edit
import java.io.File
import java.io.OutputStream

data class ImageStylePreset(
    val id: String,
    val label: String,
    val promptSuffix: String
)

val IMAGE_STYLE_PRESETS = listOf(
    ImageStylePreset("none", "ธรรมชาติ (ตามคำสั่ง)", ""),
    ImageStylePreset("photo", "ภาพถ่ายสมจริง", ", photorealistic, sharp focus, natural lighting, detailed"),
    ImageStylePreset("anime", "อนิเมะ", ", anime style, vibrant colors, clean lineart, detailed illustration"),
    ImageStylePreset("3d", "3D การ์ตูน", ", 3d render, pixar style, soft studio lighting, smooth textures"),
    ImageStylePreset("watercolor", "สีน้ำ", ", watercolor painting, artistic brushwork, soft colors"),
    ImageStylePreset("pixel", "พิกเซลอาร์ต", ", pixel art, 16-bit retro game style, crisp pixels"),
    ImageStylePreset("cyberpunk", "ไซเบอร์พังก์", ", cyberpunk, neon lights, futuristic city, cinematic lighting")
)

const val DEFAULT_NEGATIVE_PROMPT =
    "blurry, low quality, worst quality, distorted, deformed, watermark, text, ugly, bad anatomy, " +
        "extra limbs, bad hands, missing fingers, cropped, jpeg artifacts"

data class ImageGenSettings(
    val resolution: Int = 512,
    val steps: Int = 0, // 0 = auto from model preset
    val cfgScale: Float = -1f, // -1f = auto from model preset
    val seed: Long = -1L,
    val styleId: String = "none",
    val negativePrompt: String = DEFAULT_NEGATIVE_PROMPT
)

object ImageGenPrefsStore {
    private const val PREF_NAME = "image_gen_settings_v1"

    fun load(context: Context, modelFilename: String): ImageGenSettings {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val key = modelFilename.ifBlank { "default" }
        return ImageGenSettings(
            resolution = prefs.getInt("${key}_res", 512),
            steps = prefs.getInt("${key}_steps", 0),
            cfgScale = prefs.getFloat("${key}_cfg", -1f),
            seed = prefs.getLong("${key}_seed", -1L),
            styleId = prefs.getString("${key}_style", "none") ?: "none",
            negativePrompt = prefs.getString("${key}_neg", DEFAULT_NEGATIVE_PROMPT)
                ?: DEFAULT_NEGATIVE_PROMPT
        )
    }

    fun save(context: Context, modelFilename: String, settings: ImageGenSettings) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val key = modelFilename.ifBlank { "default" }
        prefs.edit {
            putInt("${key}_res", settings.resolution)
            putInt("${key}_steps", settings.steps)
            putFloat("${key}_cfg", settings.cfgScale)
            putLong("${key}_seed", settings.seed)
            putString("${key}_style", settings.styleId)
            putString("${key}_neg", settings.negativePrompt)
        }
    }

    fun reset(context: Context, modelFilename: String) {
        save(context, modelFilename, ImageGenSettings())
    }
}

object ImagePromptTranslator {
    data class Result(
        val finalPrompt: String,
        val wasTranslated: Boolean
    )

    // Budget for the subject part (each word = 1, each phrase separator = 1) so CLIP's
    // 77-token limit does not cut the style/quality tags.
    private const val MAX_SUBJECT_TOKENS = 50

    private val whitespaceRegex = Regex("\\s+")

    private val thToEnDict: List<Pair<String, String>> = listOf(
        // Animals
        "ลูกแมว" to "cute kitten",
        "แมวส้ม" to "orange tabby cat",
        "แมวดำ" to "black cat",
        "แมวขาว" to "white cat",
        "แมว" to "cat",
        "ลูกหมา" to "cute puppy",
        "ลูกสุนัข" to "cute puppy",
        "หมาป่า" to "wolf",
        "สุนัข" to "dog",
        "หมา" to "dog",
        "กระต่าย" to "rabbit",
        "สิงโต" to "lion",
        "เสือโคร่ง" to "tiger",
        "เสือ" to "tiger",
        "ช้าง" to "elephant",
        "ม้า" to "horse",
        "นกอินทรี" to "eagle",
        "นกฮูก" to "owl",
        "นก" to "bird",
        "ปลาโลมา" to "dolphin",
        "ปลาวาฬ" to "whale",
        "ปลาทอง" to "goldfish",
        "ปลา" to "fish",
        "ผีเสื้อ" to "butterfly",
        "มังกร" to "dragon",
        "ไดโนเสาร์" to "dinosaur",
        "หมีแพนด้า" to "panda",
        "หมี" to "bear",
        "จิ้งจอก" to "fox",
        // People & Characters
        "ผู้หญิงไทย" to "thai woman",
        "ผู้ชายไทย" to "thai man",
        "เด็กผู้หญิง" to "young girl",
        "เด็กผู้ชาย" to "young boy",
        "ผู้หญิง" to "woman",
        "ผู้ชาย" to "man",
        "เด็ก" to "child",
        "เจ้าหญิง" to "princess",
        "อัศวิน" to "knight",
        "ซามูไร" to "samurai",
        "นินจา" to "ninja",
        "นักบินอวกาศ" to "astronaut",
        "หุ่นยนต์" to "robot",
        "ชุดไทย" to "traditional thai dress",
        // Nature & Scenery
        "พระอาทิตย์ตก" to "sunset",
        "พระอาทิตย์ขึ้น" to "sunrise",
        "ท้องฟ้ากลางคืน" to "night sky with stars",
        "ทางช้างเผือก" to "milky way galaxy",
        "ท้องฟ้า" to "blue sky with clouds",
        "ชายหาด" to "tropical beach",
        "ทะเล" to "ocean",
        "ภูเขาไฟ" to "volcano",
        "ภูเขา" to "mountain landscape",
        "น้ำตก" to "waterfall",
        "ป่าไม้" to "lush green forest",
        "ในป่า" to "in the forest",
        "ป่า" to "forest",
        "ดอกซากุระ" to "cherry blossom sakura",
        "ดอกกุหลาบ" to "red rose",
        "ดอกทานตะวัน" to "sunflower",
        "ดอกไม้" to "colorful flowers",
        "ต้นไม้" to "tree",
        "ฝนตก" to "in the rain",
        "หิมะตก" to "snowfall",
        "หิมะ" to "snow",
        "อวกาศ" to "outer space",
        "ดวงจันทร์" to "full moon",
        // Places & Objects
        "เมืองอนาคต" to "futuristic city",
        "เมือง" to "cityscape",
        "ปราสาท" to "medieval castle",
        "วัดไทย" to "thai temple",
        "บ้านไม้" to "wooden house",
        "บ้าน" to "cozy house",
        "ร้านกาแฟ" to "cozy coffee shop",
        "รถสปอร์ต" to "sports car",
        "รถยนต์" to "car",
        "รถ" to "car",
        "มอเตอร์ไซค์" to "motorcycle",
        "เครื่องบิน" to "airplane",
        "ยานอวกาศ" to "spaceship",
        "เรือ" to "boat",
        "กาแฟ" to "cup of coffee",
        "เค้ก" to "delicious cake",
        "อาหารไทย" to "thai food",
        "อาหาร" to "delicious food",
        // Actions, Colors & Styles
        "กำลังนอนหลับ" to "sleeping peacefully",
        "กำลังนอน" to "sleeping",
        "กำลังวิ่ง" to "running",
        "กำลังนั่ง" to "sitting",
        "กำลังยิ้ม" to "smiling",
        "กำลังบิน" to "flying",
        "ตอนกลางคืน" to "at night",
        "สีขาว" to "white",
        "สีดำ" to "black",
        "สีส้ม" to "orange",
        "สีแดง" to "red",
        "สีฟ้า" to "blue",
        "สีน้ำเงิน" to "deep blue",
        "สีเขียว" to "green",
        "สีชมพู" to "pink",
        "สีเหลือง" to "yellow",
        "สีม่วง" to "purple",
        "สีทอง" to "golden",
        "น่ารัก" to "cute",
        "สวยงาม" to "beautiful",
        "สวย" to "beautiful",
        "เท่" to "epic",
        "สยองขวัญ" to "dark horror",
        "แฟนตาซี" to "fantasy art",
        "การ์ตูน" to "cartoon style",
        "อนิเมะ" to "anime style",
        "สมจริง" to "photorealistic",
        "ภาพถ่าย" to "photograph",
        "ภาพวาด" to "painting",
        // History & World War I / II (People & Uniforms)
        "ทหารราบ" to "infantry soldier",
        "ทหาร" to "soldier",
        "กองทัพ" to "military army",
        "ผู้บัญชาการ" to "military commander",
        "นายพล" to "general officer",
        "พลซุ่มยิง" to "sniper",
        "ทหารเรือ" to "naval sailor",
        "นักบินขับไล่" to "fighter pilot",
        "ทหารม้า" to "cavalry soldier",
        "นักรบ" to "warrior",
        "ชาวโรมัน" to "ancient roman",
        "ฟาโรห์" to "ancient egyptian pharaoh",
        "ชาวบ้าน" to "villagers",
        "ผู้ลี้ภัย" to "refugees",
        // History & War (Vehicles, Weapons & Gear)
        "รถถัง" to "vintage military tank",
        "เครื่องบินใบพัด" to "vintage propeller aircraft",
        "เครื่องบินรบ" to "warplane",
        "เรือรบ" to "battleship warship",
        "เรือดำน้ำ" to "submarine",
        "เรือยกพลขึ้นบก" to "amphibious landing craft",
        "รถจี๊ปทหาร" to "vintage military jeep",
        "รถบรรทุกทหาร" to "military supply truck",
        "ปืนใหญ่" to "artillery cannon",
        "ปืนไรเฟิล" to "vintage rifle",
        "หมวกเหล็ก" to "steel helmet",
        "หน้ากากกันแก๊ส" to "vintage gas mask",
        "ธง" to "flag banner",
        // History & War (Battlefield & Settings)
        "สนามเพลาะ" to "war trench battlefield",
        "สมรภูมิ" to "battlefield",
        "ซากปรักหักพัง" to "war ruins",
        "เมืองเก่า" to "historic old town",
        "เมืองโบราณ" to "ancient city ruins",
        "วิหาร" to "ancient stone temple",
        "พีระมิด" to "egyptian pyramids",
        "โคลอสเซียม" to "roman colosseum",
        "กำแพงเมือง" to "ancient city wall",
        "บังเกอร์" to "concrete military bunker",
        "ค่ายทหาร" to "military camp",
        "ท่าเรือ" to "historic harbor port",
        "สะพาน" to "stone bridge",
        // History & War (Action, Atmosphere & Film Styles)
        "สงคราม" to "war scene",
        "การรบ" to "battle",
        "ระเบิด" to "explosion",
        "ควันไฟ" to "smoke and fire",
        "ไฟไหม้" to "burning fire",
        "ซากตึก" to "ruined buildings",
        "โคลน" to "muddy ground",
        "ขาวดำ" to "black and white",
        "ภาพขาวดำ" to "vintage black and white photograph",
        "ภาพยุคเก่า" to "vintage archival style",
        "สารคดี" to "documentary style",
        "ภาพยนตร์สงคราม" to "cinematic war film still",
        "ดราม่า" to "dramatic cinematic lighting",
        // Sci-Fi & Space (Ships, Tech & Worlds)
        "สถานีอวกาศ" to "space station",
        "ดาวเคราะห์" to "alien planet",
        "หลุมดำ" to "black hole",
        "เนบิวลา" to "colorful nebula space",
        "ฐานทัพ" to "sci-fi base",
        "ประตูมิติ" to "sci-fi portal",
        "แสงเลเซอร์" to "laser beams",
        "โฮโลแกรม" to "hologram projection",
        "ชุดเกราะ" to "armored suit",
        "ปืนเลเซอร์" to "sci-fi blaster weapon",
        "หุ่นยนต์รบ" to "mecha robot",
        "โดรน" to "futuristic drone",
        "ไซไฟ" to "sci-fi",
        // Thai Historical & Ayutthaya Era
        "กรุงศรีอยุธยา" to "ancient ayutthaya kingdom",
        "อยุธยา" to "ayutthaya historical",
        "เรือนไทย" to "traditional thai wooden house",
        "เรือสำเภา" to "ancient sailing junk ship",
        "เรือพระที่นั่ง" to "royal thai golden barge",
        "เจดีย์" to "ancient pagoda stupa",
        "พระพุทธรูป" to "ancient buddha statue",
        "ขุนนาง" to "ancient thai noble",
        "แม่ทัพ" to "ancient military general",
        "นักดาบ" to "swordsman",
        "ดาบไทย" to "traditional thai sword",
        "ช้างศึก" to "war elephant",
        "ตลาดน้ำ" to "floating market",
        "โคมยี่เป็ง" to "floating sky lanterns",
        "วิถีชีวิตไทย" to "traditional thai life",
        // Horror & Mystery (Moods, Entities & Settings)
        "ผี" to "ghostly spirit",
        "ปีศาจ" to "demon",
        "ซอมบี้" to "zombie",
        "แวมไพร์" to "vampire",
        "โครงกระดูก" to "skeleton",
        "บ้านร้าง" to "abandoned haunted house",
        "สุสาน" to "dark graveyard",
        "ป่าช้า" to "eerie misty cemetery",
        "อุโมงค์" to "dark underground tunnel",
        "เงา" to "dark shadow figure",
        "ดวงตาเรืองแสง" to "glowing eyes in dark",
        "เลือด" to "crimson blood",
        "ขนลุก" to "eerie",
        "ลึกลับน่าค้นหา" to "mysterious",
        "บรรยากาศวังเวง" to "haunting atmosphere",
        // Fantasy & Magic (Creatures, Realms & Spells)
        "เวทมนตร์" to "magic aura",
        "คฑาเวทมนตร์" to "magic staff",
        "หนังสือเวทมนตร์" to "ancient spellbook",
        "ผลึกคริสตัล" to "glowing crystal",
        "ป่าแฟนตาซี" to "enchanted fantasy forest",
        "ต้นไม้เรืองแสง" to "bioluminescent glowing tree",
        "กริฟฟิน" to "griffin",
        "ฟีนิกซ์เพลิง" to "fire phoenix",
        "สัตว์อสูร" to "mythical monster",
        "วิหารลอยฟ้า" to "floating sky temple",
        "ดินแดนแห่งความฝัน" to "dreamscape fantasy world",
        "แสงวิญญาณ" to "glowing spirit wisps",
        // Wildlife & Nature (Extended Scenes & Animals)
        "หมาป่าหิมะ" to "arctic white wolf",
        "สิงโตเจ้าป่า" to "majestic male lion",
        "ฉลาม" to "shark underwater",
        "เต่าทะเล" to "sea turtle underwater",
        "ปะการัง" to "coral reef underwater",
        "โลกใต้น้ำ" to "underwater ocean world",
        "ทุ่งดอกไม้" to "wildflower meadow",
        "ทุ่งดอกทานตะวัน" to "sunflower field",
        "ป่าไผ่" to "bamboo forest",
        "ถ้ำ" to "cave",
        "ถ้ำน้ำแข็ง" to "ice cave",
        "ธารน้ำแข็ง" to "glacier",
        "แสงแดดยามเช้า" to "morning sunlight rays",
        "พระจันทร์เต็มดวง" to "bright full moon",
        // Modern City, Cafe & Lifestyle
        "เมืองยามค่ำคืน" to "city at night neon lights",
        "ตึกสูง" to "tall skyscrapers",
        "ถนนในเมือง" to "city street",
        "ทางม้าลาย" to "crosswalk street",
        "คาเฟ่" to "modern cafe interior",
        "มุมกาแฟ" to "coffee corner",
        "ลาเต้อาร์ต" to "latte art coffee",
        "เบเกอรี่" to "bakery pastries",
        "รถจักรยาน" to "bicycle",
        "รถตู้" to "camper van",
        "แคมป์ปิ้ง" to "camping tent outdoors",
        "กองไฟ" to "campfire at night",
        "หูฟัง" to "headphones",
        "กล้องถ่ายรูป" to "vintage camera",
        "โน้ตบุ๊ก" to "laptop workspace",
        "โต๊ะทำงาน" to "desk workspace",
        // Camera Angles, Framing & Shot Types (Great for Short Videos)
        "ภาพระยะใกล้" to "close up shot",
        "ภาพมุมกว้าง" to "wide angle shot",
        "ภาพมุมสูง" to "aerial view",
        "ภาพมุมต่ำ" to "low angle hero shot",
        "ภาพพอร์ตเทรต" to "portrait framing",
        "ฉากหลังเบลอ" to "shallow depth of field bokeh",
        "ภาพสมมาตร" to "symmetrical composition",
        "มุมมองบุคคลที่หนึ่ง" to "first person pov view",
        // Weather, Sky & Lighting
        "พายุฝนฟ้าคะนอง" to "thunderstorm lightning rain",
        "หมอกหนา" to "dense misty fog",
        "แสงแดดอ่อนๆ" to "soft morning sunlight",
        "แสงไฟนีออน" to "neon light glow",
        "แสงเทียน" to "warm candlelight",
        "แสงจันทร์" to "moonlit night",
        "เงาแดด" to "sunlight shadows",
        "ท้องฟ้าสีส้ม" to "golden orange sky",
        // Professions, Sports & Dynamic Action
        "นักรบเกราะหนัก" to "heavy armored knight",
        "นักธนู" to "archer with bow",
        "นักผจญภัย" to "adventurer",
        "ช่างภาพ" to "photographer with camera",
        "นักดนตรีสตรีท" to "street musician",
        "นักกีฬา" to "athlete in action",
        "กำลังวิ่งหนี" to "running away fast",
        "กำลังปีน" to "climbing",
        "กำลังว่ายน้ำ" to "swimming",
        "กำลังต่อสู้ดวลดาบ" to "sword duel action",
        "กำลังยืนมอง" to "standing looking out",
        // Vehicles, Structures & Storytelling Props
        "เรือประมง" to "fishing boat",
        "เรือยอร์ช" to "luxury yacht",
        "เฮลิคอปเตอร์" to "helicopter",
        "รถบรรทุก" to "cargo truck",
        "รถไฟความเร็วสูง" to "modern bullet train",
        "ประภาคาร" to "coastal lighthouse",
        "ป้อมปราการ" to "stone fortress",
        "หอคอย" to "tall tower",
        "ตลาดกลางคืน" to "night market street food",
        "แผงลอย" to "street market stall",
        "หีบสมบัติ" to "treasure chest",
        "แผนที่เก่า" to "vintage map parchment",
        "ตะเกียง" to "vintage oil lantern"

    ).sortedByDescending { it.first.length }

    private fun isThai(c: Char): Boolean = c in '\u0E00'..'\u0E7F'

    /**
     * Walks the text left to right. At each Thai character takes the longest dictionary entry
     * that starts there (so "ภาพขาวดำ" wins over "ขาวดำ" and is never emitted twice).
     * Unmapped Thai characters (including filler words like "สร้างภาพ", "หน่อย") are dropped.
     * Non-Thai runs are kept and split on commas.
     * Returns whole phrases in their original order.
     */
    private fun segmentToPhrases(text: String): List<String> {
        val out = ArrayList<String>()
        val buffer = StringBuilder()

        fun flushBuffer() {
            if (buffer.isEmpty()) return
            buffer.toString()
                .split(',')
                .map { it.replace(whitespaceRegex, " ").trim() }
                .filter { it.isNotEmpty() }
                .forEach { out.add(it) }
            buffer.setLength(0)
        }

        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (!isThai(c)) {
                buffer.append(c)
                i++
                continue
            }
            flushBuffer()
            val hit = thToEnDict.firstOrNull { text.startsWith(it.first, i) }
            if (hit != null) {
                out.add(hit.second)
                i += hit.first.length
            } else {
                i++
            }
        }
        flushBuffer()
        return out
    }

    fun translateAndEnhance(rawPrompt: String, styleId: String): Result {
        val original = rawPrompt.trim()
        val hasThai = original.any { isThai(it) }

        // Phrase-level dedupe (whole phrase only, keeps first occurrence and original order),
        // then cap by token budget without ever cutting a phrase in half
        // (except a single phrase that alone exceeds the budget).
        val phrases = segmentToPhrases(original).distinctBy { it.lowercase() }
        val picked = ArrayList<String>()
        var budget = 0
        for (phrase in phrases) {
            val words = phrase.split(' ')
            val cost = words.size + 1
            if (picked.isEmpty() && cost > MAX_SUBJECT_TOKENS) {
                picked.add(words.take(MAX_SUBJECT_TOKENS - 1).joinToString(" "))
                break
            }
            if (budget + cost > MAX_SUBJECT_TOKENS) break
            picked.add(phrase)
            budget += cost
        }

        val cleanedSubject = picked
            .joinToString(", ")
            .ifEmpty { "cute cat portrait" }

        val styleSuffix = IMAGE_STYLE_PRESETS.find { it.id == styleId }?.promptSuffix.orEmpty()
        val qualityTags = if (styleSuffix.contains("detailed")) {
            ", best quality"
        } else {
            ", best quality, highly detailed"
        }
        val finalPrompt = buildString {
            append(cleanedSubject)
            if (styleSuffix.isNotEmpty()) append(styleSuffix)
            append(qualityTags)
        }
        return Result(finalPrompt = finalPrompt, wasTranslated = hasThai)
    }
}

private fun showToast(context: Context, message: String) {
    val appContext = context.applicationContext
    Handler(Looper.getMainLooper()).post {
        Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
    }
}

private fun copyStream(
    resolver: ContentResolver,
    source: Uri,
    openOutput: () -> OutputStream?
): Boolean {
    val input = resolver.openInputStream(source) ?: return false
    input.use { ins ->
        val out = openOutput() ?: return false
        out.use { ins.copyTo(it) }
    }
    return true
}

fun saveImageToGallery(context: Context, imageUri: Uri): Boolean {
    val resolver = context.contentResolver
    val filename = "AI_Offline_${System.currentTimeMillis()}.png"
    var pendingUri: Uri? = null

    val success: Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/AI-Offline-LLM")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val destUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (destUri == null) {
                false
            } else {
                pendingUri = destUri
                val copied = copyStream(resolver, imageUri) { resolver.openOutputStream(destUri) }
                if (copied) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(destUri, values, null, null)
                    pendingUri = null
                }
                copied
            }
        } else {
            @Suppress("DEPRECATION")
            val picsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val appDir = File(picsDir, "AI-Offline-LLM").apply { mkdirs() }
            val outFile = File(appDir, filename)
            copyStream(resolver, imageUri) { outFile.outputStream() }
        }
    } catch (e: Exception) {
        pendingUri?.let { runCatching { resolver.delete(it, null, null) } }
        showToast(context, "บันทึกรูปไม่สำเร็จ: ${e.message}")
        return false
    }

    if (success) {
        showToast(context, "บันทึกรูปภาพลงแกลเลอรีแล้ว")
    } else {
        pendingUri?.let { runCatching { resolver.delete(it, null, null) } }
        showToast(context, "บันทึกรูปไม่สำเร็จ")
    }
    return success
}
