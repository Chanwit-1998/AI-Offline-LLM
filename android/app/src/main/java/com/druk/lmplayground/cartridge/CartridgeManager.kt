package com.druk.ai_offline_llm.cartridge

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.druk.ai_offline_llm.data.EmbeddingCodec
import com.druk.ai_offline_llm.rag.TextEmbedder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object CartridgeManager {
    private const val PREFS_NAME = "ai_cartridge_prefs"
    private const val KEY_ACTIVE_ID = "active_cartridge_id"
    private const val KEY_MODEL_PREFIX = "active_cartridge_model_"
    private const val KEY_FOLDER_URI = "custom_cartridge_folder_uri"
    private const val TAG = "CartridgeManager"
    private const val DIR_NAME = "cartridges"
    private const val VECTOR_CACHE_DIR = "vector_cache"
    private const val FILE_INSTALLED = "installed_cartridges_rag_v1.json"
    private const val CACHE_MAGIC = 0x43564543
    private const val CACHE_VERSION = 1
    private const val EMBED_BATCH_SIZE = 16

    private data class CachedCartridgeVectors(
        val modelFilename: String,
        val contentHash: Int,
        val vectors: List<FloatArray>
    )

    @Volatile
    private var embedder: TextEmbedder? = null
    private val indexScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val memoryVectorCache = ConcurrentHashMap<String, CachedCartridgeVectors>()
    private val indexMutexes = ConcurrentHashMap<String, Mutex>()

    fun init(context: Context, textEmbedder: TextEmbedder) {
        embedder = textEmbedder
        val appCtx = context.applicationContext
        indexScope.launch {
            if (!textEmbedder.isModelOnDisk()) return@launch
            val toIndex = LinkedHashMap<String, AiCartridge>()
            val available = getAllAvailableCartridges(appCtx)
            getActiveCartridge(appCtx)?.let { toIndex[it.id] = it }
            getInstalledCartridges(appCtx).forEach { toIndex[it.id] = it }
            val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.all.forEach { (k, v) ->
                if (k.startsWith(KEY_MODEL_PREFIX) && v is String) {
                    available.find { it.id == v }?.let { toIndex[it.id] = it }
                }
            }
            for (cart in toIndex.values) {
                runCatching { getOrBuildCartridgeVectors(appCtx, cart) }
            }
        }
    }

    fun preindexCartridgeAsync(context: Context, cartridge: AiCartridge) {
        val currentEmbedder = embedder ?: return
        if (cartridge.knowledge.isEmpty() || !currentEmbedder.isModelOnDisk()) return
        val appCtx = context.applicationContext
        indexScope.launch {
            runCatching { getOrBuildCartridgeVectors(appCtx, cartridge) }
                .onFailure { Log.w(TAG, "Background indexing skipped for ${cartridge.id}: ${it.message}") }
        }
    }

    val storeCatalog: List<AiCartridge> = listOf(
        AiCartridge(
            id = "rag_termux_linux",
            name = "ตลับฐานข้อมูล Termux & Linux Commands",
            version = "v2.0 RAG",
            category = "เขียนโค้ด & ระบบ",
            colorHex = 0xFF00BFA5,
            description = "คลังคำสั่งลัด Termux, Git, Python, SSH และวิธีแก้ Error พื้นฐานบน Android แบบออฟไลน์",
            knowledge = listOf(
                CartridgeKnowledgeEntry("อัปเดตแพ็กเกจและสิทธิ์พื้นที่เก็บข้อมูล Termux", "ใช้คำสั่ง pkg update && pkg upgrade -y เพื่ออัปเดตคลังโปรแกรม และใช้ termux-setup-storage เพื่อขอสิทธิ์เข้าถึงโฟลเดอร์ /sdcard"),
                CartridgeKnowledgeEntry("คำสั่ง Git พื้นฐานสำหรับอัปโหลดโค้ดขึ้น GitHub", "ตรวจสอบสถานะด้วย git status เพิ่มไฟล์ทั้งหมดด้วย git add . บันทึกด้วย git commit -m \"ข้อความ\" และส่งขึ้นเซิร์ฟเวอร์ด้วย git push origin main"),
                CartridgeKnowledgeEntry("ค้นหาไฟล์ขนาดใหญ่และเช็กพื้นที่ว่าง", "เช็กพื้นที่ดิสก์ด้วย df -h เช็กขนาดโฟลเดอร์ด้วย du -sh * และค้นหาไฟล์ .gguf ทั้งหมดในเครื่องด้วย find /sdcard -name \"*.gguf\" 2>/dev/null"),
                CartridgeKnowledgeEntry("รัน Python HTTP Server แชร์ไฟล์ในวง Wi-Fi", "ใช้คำสั่ง python3 -m http.server 8080 เพื่อเปิดเว็บแชร์ไฟล์ในโฟลเดอร์ปัจจุบันผ่านพอร์ต 8080")
            ),
            bestFor = "• ค้นหาคำสั่ง Termux / Linux / Git โดยไม่ต้องต่อเน็ตเปิดเว็บหา\n• ใช้ร่วมกับบุคลิก 'นักเขียนโค้ด' เพื่อให้ AI ตอบคำสั่งที่ตรงไวยากรณ์เป๊ะ",
            usageTips = "• ถามเจาะจงชื่อคำสั่งหรือสิ่งที่ต้องการทำ เช่น 'คำสั่ง termux แชร์ไฟล์ผ่าน wifi'",
            samplePrompt = "ขอคำสั่ง Termux สำหรับค้นหาไฟล์ .gguf ในเครื่องและเช็กขนาดพื้นที่ว่าง"
        ),
        AiCartridge(
            id = "rag_emulator_bible",
            name = "ตลับคัมภีร์ตั้งค่า Emulator (PSP / PS2 / GC)",
            version = "v2.0 RAG",
            category = "เกม & Emulator",
            colorHex = 0xFFFF1744,
            description = "ฐานข้อมูลค่าตั้งต้น PPSSPP, AetherSX2/NetherSX2 และ Dolphin สำหรับชิป Exynos/Snapdragon บนมือถือ",
            knowledge = listOf(
                CartridgeKnowledgeEntry("การตั้งค่า PPSSPP (PSP) ให้ภาพชัดและลื่น 60FPS", "ตั้งค่า Backend = Vulkan, Rendering Resolution = 2x–3x PSP, เปิด Lazy Texture Caching (speedup), ตั้ง Spline/Bezier curves quality = Low"),
                CartridgeKnowledgeEntry("การตั้งค่า AetherSX2 / NetherSX2 (PS2) ลดอาการหน่วง", "ใช้ GPU Renderer = Vulkan, Upscale Multiplier = 1.5x–2x Native, ตั้ง EE Cycle Rate = 75%, EE Cycle Skip = Normal (0) และเปิด Multi-Threaded VU1"),
                CartridgeKnowledgeEntry("การตั้งค่า Dolphin Emulator (GameCube / Wii)", "เลือก Video Backend = Vulkan, เปิด Compile Shaders Before Starting และ Skip EFB Access from CPU เพื่อเพิ่มเฟรมเรต"),
                CartridgeKnowledgeEntry("ตำแหน่งโฟลเดอร์เซฟเกมและไฟล์ BIOS", "PPSSPP เก็บเซฟไว้ที่ PSP/SAVEDATA และสูตรโกงไว้ที่ PSP/Cheats (เปิด Enable Cheats ก่อน) ส่วน AetherSX2 ต้องนำเข้าไฟล์ BIOS PS2 ก่อนบูตเกม")
            ),
            bestFor = "• ปรับจูนภาพและเฟรมเรตเกม PSP, PS2 และ GameCube บนมือถือ Android\n• แก้ปัญหาภาพแตก เสียงกระตุก และหาตำแหน่งวางไฟล์เซฟ",
            usageTips = "• ระบุชื่อโปรแกรม Emulator (PPSSPP, AetherSX2, Dolphin) ในคำถาม ระบบจะดึงตารางตั้งค่ามาตอบทันที",
            samplePrompt = "ตั้งค่า PPSSPP ยังไงให้เล่นเกมลื่นไม่กระตุกและภาพคมชัด"
        ),
        AiCartridge(
            id = "rag_electric_std",
            name = "ตลับคู่มือมาตรฐานไฟฟ้ากำลัง (วสท.)",
            version = "v2.0 RAG",
            category = "งานช่าง & วิศวกรรม",
            colorHex = 0xFFFFB300,
            description = "ฐานข้อมูลพิกัดกระแสสายไฟ THW ขนาดเบรกเกอร์ กันดูด RCBO และสูตรคำนวณโหลดไฟฟ้าในบ้าน",
            knowledge = listOf(
                CartridgeKnowledgeEntry("สูตรคำนวณกระแสไฟฟ้า 1 เฟส 220V", "กระแสไฟฟ้า I (แอมป์) = กำลังไฟฟ้า P (วัตต์) หารด้วย [แรงดัน V (220V) x PF (0.85–1.0)] เช่น เครื่องทำน้ำอุ่น 4,500W กินกระแสประมาณ 4500 / 220 = 20.45 แอมป์"),
                CartridgeKnowledgeEntry("ตารางขนาดสายไฟทองแดง THW เดินในท่อร้อยสาย", "สาย 1.5 ตร.มม. รับได้ 15A (เบรกเกอร์ 10A–16A), สาย 2.5 ตร.มม. รับได้ 21A (เบรกเกอร์ 16A–20A), สาย 4.0 ตร.มม. รับได้ 28A (เบรกเกอร์ 25A–32A), สาย 6.0 ตร.มม. รับได้ 36A (เบรกเกอร์ 32A–40A)"),
                CartridgeKnowledgeEntry("มาตรฐานเบรกเกอร์เครื่องทำน้ำอุ่น", "เครื่องทำน้ำอุ่น 3,500W ใช้สายไฟ 2.5 ตร.มม. คู่กับเบรกเกอร์กันดูด (RCBO) 20A ส่วน 4,500W ต้องใช้สายไฟ 4.0 ตร.มม. คู่กับ RCBO 32A และต่อสายดินเสมอ"),
                CartridgeKnowledgeEntry("กฎความปลอดภัยก่อนซ่อมระบบไฟฟ้า", "ต้องตัดเมนเบรกเกอร์ลงตำแหน่ง OFF ใช้ไขควงลองไฟวัดยืนยันว่าไม่มีไฟค้าง และห้ามใช้เบรกเกอร์แอมป์สูงกว่าพิกัดสายไฟ")
            ),
            bestFor = "• คำนวณขนาดสายไฟและเลือกขนาดเบรกเกอร์ให้ปลอดภัยตามมาตรฐาน\n• ตรวจสอบโหลดเครื่องทำน้ำอุ่น แอร์ และวงจรปลั๊กไฟในบ้าน",
            usageTips = "• พิมพ์กำลังวัตต์ (W) หรือขนาดสายไฟที่ต้องการทราบ",
            samplePrompt = "เครื่องทำน้ำอุ่น 4,500 วัตต์ ต้องใช้สายไฟกี่ ตร.มม. และเบรกเกอร์กันดูดกี่แอมป์"
        ),
        AiCartridge(
            id = "rag_finance_formula",
            name = "ตลับสูตรคำนวณธุรกิจ การเงิน & ภาษี",
            version = "v2.0 RAG",
            category = "การเงิน & บัญชี",
            colorHex = 0xFF00C853,
            description = "คลังสูตรคิด Margin, Markup, จุดคุ้มทุน (Break-Even), VAT 7% และดอกเบี้ยทบต้นสำหรับค้าขาย",
            knowledge = listOf(
                CartridgeKnowledgeEntry("ความแตกต่างระหว่าง Profit Margin กับ Markup", "Markup (%) = [(ราคาขาย - ต้นทุน) / ต้นทุน] x 100 ส่วน Profit Margin (%) = [(ราคาขาย - ต้นทุน) / ราคาขาย] x 100 หากต้องการกำไร Margin สุทธิ M% จากต้นทุน C ให้ตั้งราคาขาย = C / (1 - M)"),
                CartridgeKnowledgeEntry("สูตรคำนวณจุดคุ้มทุน (Break-Even Point)", "จำนวนชิ้นที่ต้องขายให้คุ้มทุน = ต้นทุนคงที่ทั้งหมด หารด้วย [ราคาขายต่อหน่วย - ต้นทุนผันแปรต่อหน่วย]"),
                CartridgeKnowledgeEntry("วิธีถอดและบวกภาษีมูลค่าเพิ่ม VAT 7%", "บวก VAT 7%: คูณ 1.07 (เช่น 1,000 x 1.07 = 1,070) | ถอด VAT ออกจากราคารวม VAT: หาร 1.07 เพื่อหามูลค่าสินค้า และคูณ 7 หาร 107 เพื่อหายอดภาษี"),
                CartridgeKnowledgeEntry("สูตรดอกเบี้ยทบต้น (Compound Interest)", "เงินรวมในอนาคต A = P x (1 + r)^n โดย P คือเงินต้น, r คืออัตราดอกเบี้ยต่องวด และ n คือจำนวนงวดเวลา")
            ),
            bestFor = "• คำนวณราคาขายสินค้าให้ได้อัตรากำไรตามเป้าหมายจริง\n• ถอดยอด VAT 7% และคำนวณจุดคุ้มทุนของร้านค้า",
            usageTips = "• ใส่ตัวเลขต้นทุนและคำถามต่อท้ายได้เลย",
            samplePrompt = "ต้นทุนสินค้า 140 บาท อยากได้กำไร Margin 30% ต้องตั้งราคาขายกี่บาท และถ้ารวม VAT 7% จะเป็นกี่บาท"
        ),
        AiCartridge(
            id = "rag_jp_en_vocab",
            name = "ตลับคลังศัพท์ & สำนวนเกม/ชีวิตจริง (TH-EN-JP)",
            version = "v2.0 RAG",
            category = "ภาษา & การแปล",
            colorHex = 0xFFFF9100,
            description = "ฐานข้อมูลคำศัพท์เฉพาะทางในเกม RPG คำสุภาพภาษาญี่ปุ่น (Keigo) และสำนวนแชทภาษาอังกฤษ",
            knowledge = listOf(
                CartridgeKnowledgeEntry("คำศัพท์ค่าสถานะในเกม RPG (อังกฤษ - ญี่ปุ่น - ไทย)", "HP = พลังชีวิต, MP = พลังเวท, ATK (攻撃力 - โคเงคิเรียวคุ) = พลังโจมตี, DEF (防御力 - โบเกียวเรียวคุ) = พลังป้องกัน, CRIT (会心 - ไคชิน) = โจมตีติดคริติคอล"),
                CartridgeKnowledgeEntry("ประโยคภาษาญี่ปุ่นที่ใช้บ่อยในชีวิตประจำวัน", "ありがとうございます (Arigatou gozaimasu) = ขอบคุณมาก, すみません (Sumimasen) = ขอโทษ / รบกวนสอบถาม, よろしくお願いします (Yoroshiku onegaishimasu) = ขอฝากตัว / รบกวนด้วย"),
                CartridgeKnowledgeEntry("สำนวนแชทภาษาอังกฤษแบบมืออาชีพและเป็นกันเอง", "FYI = แจ้งเพื่อทราบ, ASAP = เร็วที่สุดเท่าที่เป็นไปได้, No worries = ไม่ต้องกังวล/ไม่เป็นไรเลย, Keep me posted = อัปเดตความคืบหน้าให้รู้ด้วยนะ")
            ),
            bestFor = "• แปลเมนูเกมภาษาญี่ปุ่น/อังกฤษและศัพท์สเตตัสตัวละคร\n• ค้นหาสำนวนตอบแชทภาษาอังกฤษและญี่ปุ่นให้ตรงบริบท",
            usageTips = "• พิมพ์คำศัพท์หรือประโยคที่เจอในเกม/แชท แล้วถามความหมายหรือวิธีใช้",
            samplePrompt = "คำว่า 攻撃力 และ 会心 ในเกมภาษาญี่ปุ่นแปลว่าอะไร และคำว่า Keep me posted ในแชทภาษาอังกฤษใช้ยังไง"
        ),
        AiCartridge(
            id = "rag_cooking_ratio",
            name = "ตลับตารางสัดส่วนเครื่องปรุง & แคลอรีอาหารไทย",
            version = "v2.0 RAG",
            category = "อาหาร & โภชนาการ",
            colorHex = 0xFFFF7043,
            description = "ฐานข้อมูลสูตรซอสผัดอเนกประสงค์ มาตราวัดตวงในครัว และตารางแคลอรี/โปรตีนของวัตถุดิบหลัก",
            knowledge = listOf(
                CartridgeKnowledgeEntry("สูตรซอสผัดกะเพรา / ผัดอเนกประสงค์ (ต่อเนื้อสัตว์ 200 กรัม)", "ซอสหอยนางรม 1.5 ช้อนโต๊ะ + ซีอิ๊วขาว 1 ช้อนโต๊ะ + น้ำปลา 0.5 ช้อนโต๊ะ + ซีอิ๊วดำหวาน 0.5 ช้อนชา + น้ำตาลทราย 0.5–1 ช้อนชา + น้ำเปล่า 2 ช้อนโต๊ะ"),
                CartridgeKnowledgeEntry("มาตราวัดตวงมาตรฐานในครัว", "1 ช้อนโต๊ะ (Tbsp) = 15 มล. (3 ช้อนชา), 1 ช้อนชา (Tsp) = 5 มล., 1 ถ้วยตวง (Cup) = 240 มล. (16 ช้อนโต๊ะ)"),
                CartridgeKnowledgeEntry("ตารางพลังงานและโปรตีนของวัตถุดิบหลัก (ต่อ 100 กรัม)", "อกไก่ลอกหนังดิบ: 110 kcal (โปรตีน 23g), สันในหมู: 143 kcal (โปรตีน 21g), ไข่ไก่ 1 ฟอง: 70–75 kcal (โปรตีน 6g), ข้าวสวย 1 ทัพพี (60g): 80 kcal")
            ),
            bestFor = "• คำนวณแคลอรีและโปรตีนต่อมื้ออาหาร\n• ดูสูตรสัดส่วนเครื่องปรุงอาหารไทยและเทียบมาตราวัดช้อนตวง",
            usageTips = "• บอกชื่อวัตถุดิบและปริมาณที่มี",
            samplePrompt = "อกไก่ 200 กรัม กับไข่ไก่ 2 ฟอง ให้โปรตีนและพลังงานกี่แคลอรี และขอสูตรซอสผัดกะเพราสำหรับปริมาณนี้"
        ),
        AiCartridge(
            id = "rag_first_aid",
            name = "ตลับคู่มือปฐมพยาบาล & เบอร์ฉุกเฉิน",
            version = "v2.0 RAG",
            category = "สุขภาพ & ความปลอดภัย",
            colorHex = 0xFFEC407A,
            description = "ฐานข้อมูลขั้นตอนปฐมพยาบาลเบื้องต้น แผลไฟไหม้ น้ำร้อนลวก ไฟดูด และหมายเลขสายด่วนฉุกเฉินในไทย",
            knowledge = listOf(
                CartridgeKnowledgeEntry("หมายเลขโทรศัพท์แจ้งเหตุฉุกเฉินในประเทศไทย", "1669 = เจ็บป่วยฉุกเฉิน / กู้ชีพทั่วประเทศ, 191 = ตำรวจ / เหตุด่วนเหตุร้าย, 199 = แจ้งเหตุเพลิงไหม้, 1129 = การไฟฟ้าส่วนภูมิภาค (PEA), 1130 = การไฟฟ้านครหลวง (MEA)"),
                CartridgeKnowledgeEntry("การปฐมพยาบาลแผลไฟไหม้และน้ำร้อนลวก", "เปิดน้ำสะอาดอุณหภูมิห้องไหลผ่านแผลเบาๆ นาน 10–20 นาที ห้ามใช้น้ำแข็งประคบโดยตรง ห้ามทายาสีฟัน น้ำปลา หรือน้ำมัน ห้ามเจาะตุ่มน้ำพอง"),
                CartridgeKnowledgeEntry("การช่วยเหลือผู้ถูกไฟฟ้าดูดอย่างปลอดภัย", "ห้ามสัมผัสตัวผู้ถูกไฟดูดด้วยมือเปล่าเด็ดขาด ให้รีบตัดแหล่งจ่ายไฟ หากตัดไฟไม่ได้ให้ใช้ไม้แห้งหรือฉนวนแห้งเขี่ยสายไฟออก แล้วโทร 1669 ทันที")
            ),
            bestFor = "• ค้นหาขั้นตอนปฐมพยาบาลที่ถูกต้องในสถานการณ์ฉุกเฉินแบบออฟไลน์\n• เช็กเบอร์สายด่วนฉุกเฉินในประเทศไทย",
            usageTips = "• ถามอาการหรือเหตุการณ์ตรงๆ เช่น 'วิธีปฐมพยาบาลแผลน้ำร้อนลวก'",
            samplePrompt = "โดนน้ำร้อนลวกที่แขนควรปฐมพยาบาลเบื้องต้นยังไง และมีข้อห้ามอะไรบ้าง"
        ),
        AiCartridge(
            id = "rag_android_kotlin",
            name = "ตลับคู่มือพัฒนาแอป Android & Jetpack Compose",
            version = "v2.0 RAG",
            category = "เขียนโค้ด & ระบบ",
            colorHex = 0xFF2979FF,
            description = "คลังรูปแบบโค้ด Jetpack Compose, Coroutines, Room Database และ Android Service สำหรับนักพัฒนา",
            knowledge = listOf(
                CartridgeKnowledgeEntry("การจัดการ State และ Coroutines ใน Jetpack Compose", "ใช้ remember { mutableStateOf(...) } สำหรับ State ภายใน Composable ใช้ collectAsState() สำหรับ StateFlow และใช้ LaunchedEffect(key) เมื่อต้องการรันงานแบบ Asynchronous"),
                CartridgeKnowledgeEntry("ข้อควรระวังเรื่อง Foreign Key ใน Room Database", "หากตารางลูกตั้งค่า @ForeignKey อ้างอิงไปยังตารางแม่ ห้ามส่งค่า ID ที่ไม่มีอยู่จริงในตารางแม่ มิฉะนั้น SQLite จะโยน SQLiteConstraintException ให้ส่ง null แทนเมื่อไม่ต้องการผูก"),
                CartridgeKnowledgeEntry("การเปิด Activity จาก Service หรือ VoiceInteractionSession", "เมื่อเรียก startActivity(intent) จากภายนอก Activity ต้องเพิ่ม Flag Intent.FLAG_ACTIVITY_NEW_TASK เสมอ และเรียกผ่าน context.startActivity(intent)")
            ),
            bestFor = "• อ้างอิงรูปแบบโค้ด Jetpack Compose, Room และ Android Framework\n• แก้ปัญหา Crash ยอดฮิตบน Android อย่างรวดเร็ว",
            usageTips = "• ถามเรื่องโครงสร้าง Compose, Room หรือ Service ได้ทันที",
            samplePrompt = "วิธีแก้ปัญหา SQLiteConstraintException Foreign Key failed ใน Room Database และวิธีเรียก startActivity จาก Service"
        ),
        AiCartridge(
            id = "rag_summary_framework",
            name = "ตลับแม่แบบสรุปงาน & เอกสารมืออาชีพ",
            version = "v2.0 RAG",
            category = "เครื่องมือทำงาน",
            colorHex = 0xFF26A69A,
            description = "คลังโครงสร้างการสรุปรายงานการประชุม บทความ และการเขียนอีเมลธุรกิจให้กระชับอ่านจบใน 10 วินาที",
            knowledge = listOf(
                CartridgeKnowledgeEntry("โครงสร้างสรุปรายงานการประชุมแบบ 3 ส่วน (Actionable Minutes)", "1. ข้อสรุปและมติสำคัญ (Key Decisions) ไม่เกิน 3 ข้อ | 2. รายการสิ่งที่ต้องทำต่อ (Action Items) ระบุผู้รับผิดชอบและกำหนดส่ง | 3. ประเด็นที่ยังค้างหรือต้องติดตามรอบหน้า"),
                CartridgeKnowledgeEntry("เทคนิคสรุปบทความยาวด้วยหลัก BLUF (Bottom Line Up Front)", "นำข้อสรุปหรือผลลัพธ์สำคัญที่สุดขึ้นบรรทัดแรกสุด ตามด้วยเหตุผลสนับสนุน 3 ข้อ และตัวเลขสถิติสำคัญ โดยตัดคำเกริ่นนำออกทั้งหมด")
            ),
            bestFor = "• ใช้เป็นแม่แบบจัดระเบียบข้อความยาวๆ หรือรายงานการประชุม\n• ทำงานร่วมกับไฟล์เอกสาร RAG ที่แนบในห้องแชท",
            usageTips = "• วางข้อความยาวๆ แล้วสั่งให้สรุปตามโครงสร้าง BLUF",
            samplePrompt = "สรุปข้อความต่อไปนี้ตามหลัก BLUF โดยเอาข้อสรุปสำคัญขึ้นบรรทัดแรกและแยกเหตุผลเป็น 3 ข้อ:"
        ),
        AiCartridge(
            id = "rag_ai_llm_guide",
            name = "ตลับคู่มือระบบ AI Local LLM & การจูนโมเดล",
            version = "v2.0 RAG",
            category = "AI & เทคโนโลยี",
            colorHex = 0xFF7C4DFF,
            description = "ฐานข้อมูลอธิบายค่า Temperature, Top-P, Context Size, Quantization (Q4_K_M / Q8_0) และ KV Cache",
            knowledge = listOf(
                CartridgeKnowledgeEntry("ความหมายของค่า Temperature และ Top-P ใน LLM", "Temperature (0.0–1.5) ควบคุมความสร้างสรรค์: ค่าต่ำ (0.1–0.3) เหมาะกับโค้ดและคำนวณ ค่าสูง (0.7–0.9) เหมาะกับแต่งนิยาย ส่วน Top-P ตัดตัวเลือกคำความน่าจะเป็นต่ำทิ้ง โดยทั่วไปตั้งไว้ที่ 0.85–0.95"),
                CartridgeKnowledgeEntry("รูปแบบไฟล์บีบอัด GGUF Quantization (Q4_K_M vs Q8_0)", "Q4_K_M (4-bit) ใช้แรมน้อยและรันเร็วที่สุดบนมือถือ รักษาความฉลาดได้ >95% ส่วน Q8_0 (8-bit) แม่นยำใกล้เคียงต้นฉบับ 99.9% แต่ใช้แรมมากกว่าเกือบ 2 เท่า"),
                CartridgeKnowledgeEntry("หลักการทำงานของ Context Size และ KV Cache", "Context Size คือความยาวความจำสูงสุดของบทสนทนา ยิ่งตั้งค่าสูงยิ่งกิน RAM มากขึ้นตามขนาด KV Cache มือถือแรม 6GB–8GB แนะนำตั้งไว้ที่ 2048–4096")
            ),
            bestFor = "• ทำความเข้าใจการตั้งค่าโมเดลในแอป ChatLLM (Temp, Top-P, Context Size, GGUF)\n• เลือกดาวน์โหลดไฟล์โมเดล GGUF ให้เหมาะกับสเปกแรมมือถือ",
            usageTips = "• ถามเรื่องการปรับแต่งค่าพารามิเตอร์หรือเลือกขนาดไฟล์โมเดลได้ทันที",
            samplePrompt = "ไฟล์ GGUF แบบ Q4_K_M ต่างจาก Q8_0 ยังไง และมือถือแรม 8GB ควรตั้ง Context Size เท่าไหร่"
        )
    )

    private fun getInstalledFile(context: Context): File =
        File(File(context.filesDir, DIR_NAME).apply { mkdirs() }, FILE_INSTALLED)

    fun getCartridgeFolderPath(context: Context): String {
        val rawUri = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_FOLDER_URI, null)
        if (rawUri.isNullOrBlank()) return "โฟลเดอร์แอป/cartridges"
        val decoded = Uri.decode(rawUri)
        val segment = decoded.substringAfterLast("primary:", decoded.substringAfterLast("/"))
        return if (segment.isNotBlank()) "Internal Storage/$segment" else decoded
    }

    fun setCustomCartridgeFolderUri(context: Context, uri: Uri): Int {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_FOLDER_URI, uri.toString()).apply()
        val tree = DocumentFile.fromTreeUri(context, uri) ?: return 0
        var importedCount = 0
        tree.listFiles().forEach { doc ->
            if (doc.isFile && (doc.name?.endsWith(".json", ignoreCase = true) == true)) {
                if (importFromUri(context, doc.uri).isSuccess) importedCount++
            }
        }
        return importedCount
    }

    fun getInstalledCartridges(context: Context): List<AiCartridge> {
        val file = getInstalledFile(context)
        if (!file.exists()) {
            val defaults = storeCatalog.take(2)
            saveInstalledCartridges(context, defaults)
            return defaults
        }
        return AiCartridge.listFromJsonString(file.readText(Charsets.UTF_8))
    }

    fun saveInstalledCartridges(context: Context, list: List<AiCartridge>) {
        getInstalledFile(context).writeText(AiCartridge.listToJsonString(list), Charsets.UTF_8)
    }

    fun installCartridge(context: Context, cartridge: AiCartridge): List<AiCartridge> {
        val current = getInstalledCartridges(context).toMutableList()
        val idx = current.indexOfFirst { it.id == cartridge.id }
        if (idx >= 0) current[idx] = cartridge else current.add(0, cartridge)
        saveInstalledCartridges(context, current)
        preindexCartridgeAsync(context, cartridge)
        return current
    }

    fun removeInstalledCartridge(context: Context, cartridgeId: String): List<AiCartridge> {
        val updated = getInstalledCartridges(context).filterNot { it.id == cartridgeId }
        saveInstalledCartridges(context, updated)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_ACTIVE_ID, null) == cartridgeId) {
            prefs.edit().remove(KEY_ACTIVE_ID).apply()
        }
        val keysToClear = prefs.all.filter { (k, v) -> k.startsWith(KEY_MODEL_PREFIX) && v == cartridgeId }.keys
        if (keysToClear.isNotEmpty()) {
            prefs.edit().apply {
                keysToClear.forEach { remove(it) }
            }.apply()
        }
        deleteVectorCache(context, cartridgeId)
        return updated
    }

    fun getAllAvailableCartridges(context: Context): List<AiCartridge> {
        val map = LinkedHashMap<String, AiCartridge>()
        getInstalledCartridges(context).forEach { map[it.id] = it }
        storeCatalog.forEach { if (!map.containsKey(it.id)) map[it.id] = it }
        return map.values.toList()
    }

    fun getActiveCartridgeId(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_ACTIVE_ID, null)

    fun setActiveCartridgeId(context: Context, cartridgeId: String?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_ACTIVE_ID, cartridgeId).apply()
        if (!cartridgeId.isNullOrBlank()) {
            getAllAvailableCartridges(context).find { it.id == cartridgeId }?.let {
                preindexCartridgeAsync(context, it)
            }
        }
    }

    fun getActiveCartridgeId(context: Context, modelFilename: String?): String? {
        if (modelFilename.isNullOrBlank()) return getActiveCartridgeId(context)
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("$KEY_MODEL_PREFIX$modelFilename", null)
    }

    fun setActiveCartridgeId(context: Context, modelFilename: String?, cartridgeId: String?) {
        if (modelFilename.isNullOrBlank()) {
            setActiveCartridgeId(context, cartridgeId)
            return
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            if (cartridgeId.isNullOrBlank()) {
                remove("$KEY_MODEL_PREFIX$modelFilename")
            } else {
                putString("$KEY_MODEL_PREFIX$modelFilename", cartridgeId)
            }
        }.apply()
        if (!cartridgeId.isNullOrBlank()) {
            getAllAvailableCartridges(context).find { it.id == cartridgeId }?.let {
                preindexCartridgeAsync(context, it)
            }
        }
    }

    fun getModelsBoundToCartridge(context: Context, cartridgeId: String): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.all.mapNotNull { (key, value) ->
            if (key.startsWith(KEY_MODEL_PREFIX) && value == cartridgeId) {
                key.removePrefix(KEY_MODEL_PREFIX)
            } else {
                null
            }
        }
    }

    fun getActiveCartridge(context: Context): AiCartridge? =
        getActiveCartridgeId(context)?.let { id -> getAllAvailableCartridges(context).find { it.id == id } }

    fun getActiveCartridge(context: Context, modelFilename: String?): AiCartridge? =
        getActiveCartridgeId(context, modelFilename)?.let { id -> getAllAvailableCartridges(context).find { it.id == id } }

    private val greetingOnlySet = setOf(
        "สวัสดี", "สวัสดีครับ", "สวัสดีค่ะ", "สวัสดีจ้า", "หวัดดี", "ดีครับ", "ดีค่ะ",
        "hi", "hello", "hey", "yo", "ขอบคุณ", "ขอบคุณครับ", "ขอบคุณค่ะ", "ok", "โอเค", "เค"
    )

    fun retrieveKnowledge(
        context: Context,
        query: String,
        maxChars: Int = 3000,
        modelFilename: String? = null
    ): List<Pair<String, CartridgeKnowledgeEntry>> {
        val cartridge = getActiveCartridge(context, modelFilename) ?: return emptyList()
        if (cartridge.knowledge.isEmpty() || maxChars <= 0) return emptyList()
        if (isGreetingOnly(query)) return emptyList()

        val keywordScores = computeKeywordScores(query, cartridge.knowledge)
        val scored = cartridge.knowledge.mapIndexedNotNull { index, entry ->
            val score = keywordScores[index]
            if (score >= 6f) Triple(score, -index, entry) else null
        }.sortedWith(
            compareByDescending<Triple<Float, Int, CartridgeKnowledgeEntry>> { it.first }
                .thenByDescending { it.second }
        )
        return selectWithinBudget(cartridge, scored, maxChars)
    }

    suspend fun retrieveKnowledgeHybrid(
        context: Context,
        query: String,
        maxChars: Int = 3000,
        modelFilename: String? = null,
        searchQuery: String = query
    ): List<Pair<String, CartridgeKnowledgeEntry>> {
        val cartridge = getActiveCartridge(context, modelFilename) ?: return emptyList()
        if (cartridge.knowledge.isEmpty() || maxChars <= 0) return emptyList()
        if (isGreetingOnly(query)) return emptyList()

        val effectiveQuery = searchQuery.ifBlank { query }
        val keywordScores = computeKeywordScores(effectiveQuery, cartridge.knowledge)

        val activeEmbedder = embedder
        val entryVectors = if (activeEmbedder != null && activeEmbedder.isModelOnDisk()) {
            runCatching { getOrBuildCartridgeVectors(context.applicationContext, cartridge) }.getOrNull()
        } else {
            null
        }

        val queryVector = if (entryVectors != null && activeEmbedder != null) {
            withContext(Dispatchers.Default) {
                runCatching { activeEmbedder.embedQuery(effectiveQuery) }.getOrNull()
            }
        } else {
            null
        }

        if (queryVector == null || entryVectors == null || entryVectors.size != cartridge.knowledge.size) {
            val fallbackScored = cartridge.knowledge.mapIndexedNotNull { index, entry ->
                val score = keywordScores[index]
                if (score >= 6f) Triple(score, -index, entry) else null
            }.sortedWith(
                compareByDescending<Triple<Float, Int, CartridgeKnowledgeEntry>> { it.first }
                    .thenByDescending { it.second }
            )
            return selectWithinBudget(cartridge, fallbackScored, maxChars)
        }

        val maxKeyword = maxOf(14f, keywordScores.maxOrNull() ?: 0f)
        val scored = cartridge.knowledge.mapIndexedNotNull { index, entry ->
            val rawKw = keywordScores[index]
            val normKw = (rawKw / maxKeyword).coerceIn(0f, 1f)
            val docVec = entryVectors[index]
            val semSim = if (docVec.size == queryVector.size) {
                dotProduct(queryVector, docVec).coerceAtLeast(0f)
            } else {
                0f
            }

            val qualified = semSim >= 0.42f || rawKw >= 6f || (semSim >= 0.34f && rawKw >= 2f)
            if (!qualified) return@mapIndexedNotNull null

            val hybridScore = (0.65f * semSim) + (0.35f * normKw)
            Triple(hybridScore, -index, entry)
        }.sortedWith(
            compareByDescending<Triple<Float, Int, CartridgeKnowledgeEntry>> { it.first }
                .thenByDescending { it.second }
        )

        val bestScore = scored.firstOrNull()?.first ?: return emptyList()
        val pruned = scored.filter { it.first >= bestScore - 0.16f }
        return selectWithinBudget(cartridge, pruned, maxChars)
    }

    private fun isGreetingOnly(query: String): Boolean {
        val trimmed = query.trim().lowercase()
        return trimmed.isEmpty() || trimmed in greetingOnlySet
    }

    private fun selectWithinBudget(
        cartridge: AiCartridge,
        scored: List<Triple<Float, Int, CartridgeKnowledgeEntry>>,
        maxChars: Int
    ): List<Pair<String, CartridgeKnowledgeEntry>> {
        if (scored.isEmpty()) return emptyList()
        val results = mutableListOf<Pair<String, CartridgeKnowledgeEntry>>()
        var usedChars = 0
        for ((_, _, entry) in scored) {
            val blockLen = cartridge.name.length + entry.topic.length + entry.content.length + 40
            if (usedChars + blockLen > maxChars && results.isNotEmpty()) break
            results.add(cartridge.name to entry)
            usedChars += blockLen
            if (results.size >= 3) break
        }
        return results
    }

    private fun computeKeywordScores(
        query: String,
        entries: List<CartridgeKnowledgeEntry>
    ): FloatArray {
        val qTokens = extractSearchTokens(query)
        val rawWords = query.lowercase()
            .replace(Regex("[^a-z0-9ก-๙\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 3 }
            .distinct()

        return FloatArray(entries.size) { idx ->
            val entry = entries[idx]
            val topicLower = entry.topic.lowercase()
            val bodyLower = entry.content.lowercase()
            val topicTokens = extractSearchTokens(entry.topic)
            val bodyTokens = extractSearchTokens(entry.content)

            var score = 0f
            for (qt in qTokens) {
                if (topicTokens.contains(qt)) score += 4f
                if (bodyTokens.contains(qt)) score += 2f
            }
            for (word in rawWords) {
                if (topicLower.contains(word)) score += 3f
                if (bodyLower.contains(word)) score += 1.5f
            }
            score
        }
    }

    private fun extractSearchTokens(text: String): Set<String> {
        val clean = text.lowercase().replace(Regex("[^a-z0-9ก-๙\\s]"), " ")
        val tokens = HashSet<String>()
        for (word in clean.split(Regex("\\s+"))) {
            if (word.length >= 2) {
                tokens.add(word)
                if (word.length > 4) {
                    for (i in 0..(word.length - 3)) tokens.add(word.substring(i, i + 3))
                }
            }
        }
        return tokens
    }

    private suspend fun getOrBuildCartridgeVectors(
        context: Context,
        cartridge: AiCartridge
    ): List<FloatArray>? {
        val activeEmbedder = embedder ?: return null
        if (cartridge.knowledge.isEmpty() || !activeEmbedder.isModelOnDisk()) return null

        val modelName = activeEmbedder.modelInfo.filename
        val contentHash = computeCartridgeContentHash(cartridge)

        memoryVectorCache[cartridge.id]?.let { cached ->
            if (cached.modelFilename == modelName &&
                cached.contentHash == contentHash &&
                cached.vectors.size == cartridge.knowledge.size
            ) {
                return cached.vectors
            }
        }

        val mutex = indexMutexes.getOrPut(cartridge.id) { Mutex() }
        return mutex.withLock {
            memoryVectorCache[cartridge.id]?.let { cached ->
                if (cached.modelFilename == modelName &&
                    cached.contentHash == contentHash &&
                    cached.vectors.size == cartridge.knowledge.size
                ) {
                    return@withLock cached.vectors
                }
            }

            withContext(Dispatchers.IO) {
                readVectorCacheFromDisk(context, cartridge.id, modelName, contentHash, cartridge.knowledge.size)
            }?.let { diskVectors ->
                memoryVectorCache[cartridge.id] = CachedCartridgeVectors(modelName, contentHash, diskVectors)
                return@withLock diskVectors
            }

            val texts = cartridge.knowledge.map { "${it.topic}\n${it.content}" }
            val builtVectors = ArrayList<FloatArray>(texts.size)
            for (batch in texts.chunked(EMBED_BATCH_SIZE)) {
                val batchVecs = withContext(Dispatchers.Default) {
                    activeEmbedder.embedDocumentChunks(batch)
                } ?: return@withLock null
                builtVectors.addAll(batchVecs)
            }

            if (builtVectors.size != cartridge.knowledge.size) return@withLock null

            withContext(Dispatchers.IO) {
                writeVectorCacheToDisk(context, cartridge.id, modelName, contentHash, builtVectors)
            }
            memoryVectorCache[cartridge.id] = CachedCartridgeVectors(modelName, contentHash, builtVectors)
            Log.i(TAG, "Indexed cartridge '${cartridge.id}' (${builtVectors.size} entries)")
            builtVectors
        }
    }

    private fun computeCartridgeContentHash(cartridge: AiCartridge): Int {
        var h = 17
        for (entry in cartridge.knowledge) {
            h = 31 * h + entry.topic.hashCode()
            h = 31 * h + entry.content.hashCode()
        }
        return h
    }

    private fun getVectorCacheFile(context: Context, cartridgeId: String): File {
        val safeId = cartridgeId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val dir = File(File(context.filesDir, DIR_NAME), VECTOR_CACHE_DIR).apply { mkdirs() }
        return File(dir, "$safeId.bin")
    }

    private fun deleteVectorCache(context: Context, cartridgeId: String) {
        memoryVectorCache.remove(cartridgeId)
        runCatching { getVectorCacheFile(context, cartridgeId).delete() }
    }

    private fun readVectorCacheFromDisk(
        context: Context,
        cartridgeId: String,
        expectedModel: String,
        expectedHash: Int,
        expectedCount: Int
    ): List<FloatArray>? = runCatching {
        val file = getVectorCacheFile(context, cartridgeId)
        if (!file.exists()) return null
        DataInputStream(file.inputStream().buffered()).use { input ->
            if (input.readInt() != CACHE_MAGIC) return null
            if (input.readInt() != CACHE_VERSION) return null
            if (input.readUTF() != expectedModel) return null
            if (input.readInt() != expectedHash) return null
            val count = input.readInt()
            val dim = input.readInt()
            if (count != expectedCount || dim <= 0 || dim > 8192) return null
            val byteLen = dim * 4
            val list = ArrayList<FloatArray>(count)
            repeat(count) {
                val bytes = ByteArray(byteLen)
                input.readFully(bytes)
                list.add(EmbeddingCodec.decode(bytes))
            }
            list
        }
    }.getOrNull()

    private fun writeVectorCacheToDisk(
        context: Context,
        cartridgeId: String,
        modelFilename: String,
        contentHash: Int,
        vectors: List<FloatArray>
    ) = runCatching {
        val dim = vectors.firstOrNull()?.size ?: return@runCatching
        val file = getVectorCacheFile(context, cartridgeId)
        val tmpFile = File(file.parentFile, "${file.name}.tmp")
        DataOutputStream(tmpFile.outputStream().buffered()).use { out ->
            out.writeInt(CACHE_MAGIC)
            out.writeInt(CACHE_VERSION)
            out.writeUTF(modelFilename)
            out.writeInt(contentHash)
            out.writeInt(vectors.size)
            out.writeInt(dim)
            for (vec in vectors) {
                out.write(EmbeddingCodec.encode(vec))
            }
        }
        if (file.exists()) file.delete()
        tmpFile.renameTo(file)
    }

    private fun dotProduct(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) sum += a[i] * b[i]
        return sum
    }

    fun importFromUri(context: Context, uri: Uri): Result<AiCartridge> = runCatching {
        val raw = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: error("ไม่สามารถอ่านไฟล์ตลับได้")
        val json = JSONObject(raw)
        val imported = AiCartridge.fromJson(json).copy(
            id = json.optString("id").ifBlank { "custom_${System.currentTimeMillis()}" },
            isCustom = true
        )
        installCartridge(context, imported)
        imported
    }
}
