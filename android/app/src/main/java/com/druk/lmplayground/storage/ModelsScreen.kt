@file:OptIn(ExperimentalMaterial3Api::class)

package com.druk.ai_offline_llm.storage

import android.content.Context
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.druk.ai_offline_llm.R
import com.druk.ai_offline_llm.cartridge.AiCartridge
import com.druk.ai_offline_llm.cartridge.CartridgeDetailDialog
import com.druk.ai_offline_llm.cartridge.CartridgeManager
import com.druk.ai_offline_llm.data.SystemPromptEntity
import com.druk.ai_offline_llm.models.*
import com.druk.ai_offline_llm.theme.PlaygroundTheme
import com.druk.ai_offline_llm.tools.createDefault
import com.druk.llamacpp.tools.ToolRegistry

enum class ModelsScreenMode {
    ALL,
    DOWNLOADED_ONLY,
    STORE_ONLY
}

@Composable
fun ModelsScreen(
    storageInfo: StorageInfo?,
    allModels: List<ModelWithStatus>,
    downloadingModels: Map<String, DownloadProgress>,
    snackbarMessage: String?,
    pendingMigration: MigrationState?,
    migrationProgress: MigrationProgress?,
    deviceLanguage: String,
    onBackClick: () -> Unit,
    onChangeFolderClick: () -> Unit,
    onDeleteModel: (ModelInfo) -> Unit,
    onDownloadModel: (ModelInfo, Boolean) -> Unit,
    onDownloadMmproj: (ModelInfo) -> Unit,
    onCancelDownload: (ModelInfo) -> Unit,
    onSnackbarDismiss: () -> Unit,
    onConfirmMigration: () -> Unit,
    onSkipMigration: () -> Unit,
    onCancelMigration: () -> Unit,
    mode: ModelsScreenMode = ModelsScreenMode.ALL,
    activeModelFilename: String? = null,
    installedCartridges: List<AiCartridge> = emptyList(),
    activeCartridgeId: String? = null,
    cartridgeFolderPath: String = "",
    onChangeCartridgeFolderClick: () -> Unit = {},
    onInsertCartridge: (AiCartridge) -> Unit = {},
    onEjectCartridge: () -> Unit = {},
    onInstallCustomCartridge: (AiCartridge) -> Unit = {},
    onRemoveCartridge: (String) -> Unit = {},
    onOpenCartridgeStoreClick: () -> Unit = {},
    onSelectModel: ((ModelInfo) -> Unit)? = null,
    onUnloadModel: (() -> Unit)? = null,
    onOpenStoreClick: (() -> Unit)? = null,
    recentSystemPrompts: List<SystemPromptEntity> = emptyList(),
    onActiveModelProfileUpdated: (() -> Unit)? = null,
    embeddingModel: ModelWithStatus? = null,
    dictationModel: ModelWithStatus? = null,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val screenTitle = when (mode) {
        ModelsScreenMode.DOWNLOADED_ONLY -> "คลังของฉัน"
        ModelsScreenMode.STORE_ONLY -> stringResource(R.string.menu_model_store)
        ModelsScreenMode.ALL -> stringResource(R.string.models)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(screenTitle) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    if (mode == ModelsScreenMode.DOWNLOADED_ONLY && onOpenStoreClick != null) {
                        FilledTonalButton(
                            onClick = onOpenStoreClick,
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Download,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.menu_model_store),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        ModelsContent(
            storageInfo = storageInfo,
            allModels = allModels,
            downloadingModels = downloadingModels,
            snackbarMessage = snackbarMessage,
            pendingMigration = pendingMigration,
            migrationProgress = migrationProgress,
            deviceLanguage = deviceLanguage,
            snackbarHostState = snackbarHostState,
            mode = mode,
            activeModelFilename = activeModelFilename,
            installedCartridges = installedCartridges,
            activeCartridgeId = activeCartridgeId,
            cartridgeFolderPath = cartridgeFolderPath,
            onChangeCartridgeFolderClick = onChangeCartridgeFolderClick,
            onInsertCartridge = onInsertCartridge,
            onEjectCartridge = onEjectCartridge,
            onInstallCustomCartridge = onInstallCustomCartridge,
            onRemoveCartridge = onRemoveCartridge,
            onOpenCartridgeStoreClick = onOpenCartridgeStoreClick,
            onSelectModel = onSelectModel,
            onUnloadModel = onUnloadModel,
            onOpenStoreClick = onOpenStoreClick,
            recentSystemPrompts = recentSystemPrompts,
            onActiveModelProfileUpdated = onActiveModelProfileUpdated,
            onChangeFolderClick = onChangeFolderClick,
            onDeleteModel = onDeleteModel,
            onDownloadModel = onDownloadModel,
            onDownloadMmproj = onDownloadMmproj,
            onCancelDownload = onCancelDownload,
            onSnackbarDismiss = onSnackbarDismiss,
            onConfirmMigration = onConfirmMigration,
            onSkipMigration = onSkipMigration,
            onCancelMigration = onCancelMigration,
            embeddingModel = embeddingModel,
            dictationModel = dictationModel,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}

@Composable
fun ModelsContent(
    storageInfo: StorageInfo?,
    allModels: List<ModelWithStatus>,
    downloadingModels: Map<String, DownloadProgress>,
    snackbarMessage: String?,
    pendingMigration: MigrationState?,
    migrationProgress: MigrationProgress?,
    deviceLanguage: String,
    snackbarHostState: SnackbarHostState,
    onChangeFolderClick: () -> Unit,
    onDeleteModel: (ModelInfo) -> Unit,
    onDownloadModel: (ModelInfo, Boolean) -> Unit,
    onDownloadMmproj: (ModelInfo) -> Unit,
    onCancelDownload: (ModelInfo) -> Unit,
    onSnackbarDismiss: () -> Unit,
    onConfirmMigration: () -> Unit,
    onSkipMigration: () -> Unit,
    onCancelMigration: () -> Unit,
    modifier: Modifier = Modifier,
    mode: ModelsScreenMode = ModelsScreenMode.ALL,
    activeModelFilename: String? = null,
    installedCartridges: List<AiCartridge> = emptyList(),
    activeCartridgeId: String? = null,
    cartridgeFolderPath: String = "",
    onChangeCartridgeFolderClick: () -> Unit = {},
    onInsertCartridge: (AiCartridge) -> Unit = {},
    onEjectCartridge: () -> Unit = {},
    onInstallCustomCartridge: (AiCartridge) -> Unit = {},
    onRemoveCartridge: (String) -> Unit = {},
    onOpenCartridgeStoreClick: () -> Unit = {},
    onSelectModel: ((ModelInfo) -> Unit)? = null,
    onUnloadModel: (() -> Unit)? = null,
    onOpenStoreClick: (() -> Unit)? = null,
    recentSystemPrompts: List<SystemPromptEntity> = emptyList(),
    onActiveModelProfileUpdated: (() -> Unit)? = null,
    maxContentWidth: Dp = 960.dp,
    embeddingModel: ModelWithStatus? = null,
    dictationModel: ModelWithStatus? = null,
) {
    var modelToDelete by remember { mutableStateOf<ModelInfo?>(null) }
    var downloadChoiceModel by remember { mutableStateOf<ModelInfo?>(null) }
    var configuringModel by remember { mutableStateOf<ModelInfo?>(null) }
    var profileRefreshTick by remember { mutableIntStateOf(0) }
    var bagTab by remember { mutableIntStateOf(0) }
    var showCreateCartridgeDialog by remember { mutableStateOf(false) }
    var inspectingBagCartridge by remember { mutableStateOf<AiCartridge?>(null) }
    val bagContext = LocalContext.current
    val availableToolNames = remember { ToolRegistry.createDefault(bagContext).getAllTools().map { it.name } }
    val importCartridgeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            CartridgeManager.importFromUri(bagContext, uri)
                .onSuccess { imported ->
                    onInstallCustomCartridge(imported)
                    Toast.makeText(bagContext, "นำเข้าตลับ ${imported.name} สำเร็จ!", Toast.LENGTH_SHORT).show()
                }
                .onFailure { Toast.makeText(bagContext, "ไฟล์ตลับไม่ถูกต้อง: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    LaunchedEffect(snackbarMessage) {
        if (snackbarMessage != null) {
            snackbarHostState.showSnackbar(snackbarMessage)
            onSnackbarDismiss()
        }
    }

    val splitByLanguage = deviceLanguage != "en"
    val (downloadedModels, supportedModels, otherModels) = remember(allModels, deviceLanguage) {
        val downloaded = allModels.filter { it.isDownloaded }
        val available = allModels.filter { !it.isDownloaded || it.needsVisionModule }
        val supported = if (splitByLanguage) {
            available.filter { it.model.supportsLanguage(deviceLanguage) }
        } else {
            available
        }
        val other = if (splitByLanguage) {
            available.filterNot { it.model.supportsLanguage(deviceLanguage) }
        } else {
            emptyList()
        }
        Triple(downloaded, supported, other)
    }

    val embeddingDownloadProgress = embeddingModel?.let { downloadingModels[it.model.name] }
    val showEmbeddingRow = embeddingModel != null &&
        (embeddingModel.isDownloaded || embeddingDownloadProgress != null)

    val dictationDownloadProgress = dictationModel?.let { downloadingModels[it.model.name] }
    val showDictationRow = dictationModel != null &&
        (dictationModel.isDownloaded || dictationDownloadProgress != null)

    val onAvailableDownload = remember(onDownloadModel, onDownloadMmproj) {
        { mws: ModelWithStatus ->
            val model = mws.model
            when {
                model.isVision && !mws.isDownloaded -> downloadChoiceModel = model
                mws.needsVisionModule -> onDownloadMmproj(model)
                else -> onDownloadModel(model, true)
            }
        }
    }

    val gridState = rememberLazyGridState()
    val isScrolled by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 ||
                gridState.firstVisibleItemScrollOffset > 0
        }
    }

    val showDownloadedSection = mode == ModelsScreenMode.ALL || mode == ModelsScreenMode.DOWNLOADED_ONLY
    val showStoreSection = mode == ModelsScreenMode.ALL || mode == ModelsScreenMode.STORE_ONLY

    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = maxContentWidth)
        ) {
            if (isScrolled) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
            }
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 340.dp),
                modifier = Modifier.fillMaxHeight(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (storageInfo != null && mode != ModelsScreenMode.STORE_ONLY) {
                    item(key = "storage_card", span = { GridItemSpan(maxLineSpan) }) {
                        StorageInfoCard(
                            storageInfo = storageInfo,
                            onChangeFolderClick = onChangeFolderClick,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }



                if (showDownloadedSection) {
                    item(key = "header_downloaded", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = stringResource(R.string.downloaded_models),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                        )
                    }

                    if (downloadedModels.isEmpty() && !showEmbeddingRow && !showDictationRow) {
                        item(key = "empty_downloaded", span = { GridItemSpan(maxLineSpan) }) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.no_downloaded_models),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (onOpenStoreClick != null) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(onClick = onOpenStoreClick) {
                                        Icon(Icons.Outlined.Download, null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(R.string.go_to_model_store))
                                    }
                                }
                            }
                        }
                    } else {
                        items(
                            items = downloadedModels,
                            key = { "dl_" + it.model.filename },
                            contentType = { "downloaded_model" }
                        ) { modelWithStatus ->
                            val isCurrent = activeModelFilename != null &&
                                activeModelFilename == modelWithStatus.model.filename
                            val profileSummary = remember(modelWithStatus.model.filename, profileRefreshTick) {
                                val prof = ModelProfileStore.load(bagContext, modelWithStatus.model.filename, availableToolNames)
                                val cartName = prof.cartridgeId?.let { id ->
                                    CartridgeManager.getAllAvailableCartridges(bagContext).find { it.id == id }?.name
                                }
                                Triple(
                                    cartName,
                                    !prof.systemPromptId.isNullOrBlank() || !prof.customPromptText.isNullOrBlank(),
                                    prof.enabledTools.size
                                )
                            }
                            DownloadedModelItem(
                                model = modelWithStatus.model,
                                vision = modelWithStatus.isMmprojDownloaded,
                                isActive = isCurrent,
                                boundCartridgeName = profileSummary.first,
                                hasCustomPrompt = profileSummary.second,
                                enabledToolsCount = profileSummary.third,
                                onSelectClick = onSelectModel?.let { { it(modelWithStatus.model) } },
                                onUnloadClick = onUnloadModel,
                                onConfigureClick = { configuringModel = modelWithStatus.model },
                                onDeleteClick = { modelToDelete = modelWithStatus.model }
                            )
                        }
                    }

                    if (showEmbeddingRow && embeddingModel != null) {
                        item(key = "embedding_" + embeddingModel.model.filename, contentType = "special_model") {
                            if (embeddingModel.isDownloaded) {
                                DownloadedModelItem(
                                    model = embeddingModel.model,
                                    vision = false,
                                    onDeleteClick = { modelToDelete = embeddingModel.model }
                                )
                            } else {
                                AvailableModelItem(
                                    modelWithStatus = embeddingModel,
                                    moduleOnly = false,
                                    downloadProgress = embeddingDownloadProgress,
                                    onDownloadClick = {},
                                    onCancelClick = { onCancelDownload(embeddingModel.model) }
                                )
                            }
                        }
                    }

                    if (showDictationRow && dictationModel != null) {
                        item(key = "dictation_" + dictationModel.model.filename, contentType = "special_model") {
                            if (dictationModel.isDownloaded) {
                                DownloadedModelItem(
                                    model = dictationModel.model,
                                    vision = false,
                                    onDeleteClick = { modelToDelete = dictationModel.model }
                                )
                            } else {
                                AvailableModelItem(
                                    modelWithStatus = dictationModel,
                                    moduleOnly = false,
                                    downloadProgress = dictationDownloadProgress,
                                    onDownloadClick = {},
                                    onCancelClick = { onCancelDownload(dictationModel.model) }
                                )
                            }
                        }
                    }
                }

                if (showStoreSection) {
                    if (supportedModels.isNotEmpty()) {
                        item(key = "header_supported", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = stringResource(
                                    if (splitByLanguage) R.string.supports_your_language
                                    else R.string.available_models
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = if (showDownloadedSection) 12.dp else 4.dp, bottom = 2.dp)
                            )
                        }
                        items(
                            items = supportedModels,
                            key = { "avail_" + it.model.filename },
                            contentType = { "available_model" }
                        ) { modelWithStatus ->
                            val moduleOnly = modelWithStatus.needsVisionModule
                            val progressKey = if (moduleOnly) modelWithStatus.model.name + " (vision)" else modelWithStatus.model.name
                            AvailableModelItem(
                                modelWithStatus = modelWithStatus,
                                moduleOnly = moduleOnly,
                                downloadProgress = downloadingModels[progressKey],
                                onDownloadClick = { onAvailableDownload(modelWithStatus) },
                                onCancelClick = { onCancelDownload(modelWithStatus.model) }
                            )
                        }
                    }

                    if (otherModels.isNotEmpty()) {
                        item(key = "header_other", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = stringResource(R.string.other_models),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
                            )
                        }
                        items(
                            items = otherModels,
                            key = { "other_" + it.model.filename },
                            contentType = { "available_model" }
                        ) { modelWithStatus ->
                            val moduleOnly = modelWithStatus.needsVisionModule
                            val progressKey = if (moduleOnly) modelWithStatus.model.name + " (vision)" else modelWithStatus.model.name
                            AvailableModelItem(
                                modelWithStatus = modelWithStatus,
                                moduleOnly = moduleOnly,
                                downloadProgress = downloadingModels[progressKey],
                                onDownloadClick = { onAvailableDownload(modelWithStatus) },
                                onCancelClick = { onCancelDownload(modelWithStatus.model) }
                            )
                        }
                    }
                }
            }
        }
    }

    configuringModel?.let { targetModel ->
        ModelConfigBottomSheet(
            model = targetModel,
            isActiveModel = activeModelFilename == targetModel.filename,
            recentSystemPrompts = recentSystemPrompts,
            onProfileChanged = {
                profileRefreshTick++
                if (activeModelFilename == targetModel.filename) {
                    onActiveModelProfileUpdated?.invoke()
                }
            },
            onDismiss = { configuringModel = null }
        )
    }

    modelToDelete?.let { model ->
        AlertDialog(
            onDismissRequest = { modelToDelete = null },
            title = { Text(stringResource(R.string.delete_model_confirm, model.name)) },
            text = { Text(stringResource(R.string.delete_model_message)) },
            confirmButton = {
                TextButton(onClick = { onDeleteModel(model); modelToDelete = null }) {
                    Text(stringResource(R.string.delete_model))
                }
            },
            dismissButton = {
                TextButton(onClick = { modelToDelete = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    downloadChoiceModel?.let { model ->
        AlertDialog(
            onDismissRequest = { downloadChoiceModel = null },
            title = { Text(stringResource(R.string.download_with_vision_title)) },
            text = { Text(stringResource(R.string.download_with_vision_message, model.name)) },
            confirmButton = {
                TextButton(onClick = { onDownloadModel(model, true); downloadChoiceModel = null }) {
                    Text(stringResource(R.string.download_with_images))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { onDownloadModel(model, false); downloadChoiceModel = null }) {
                        Text(stringResource(R.string.download_text_only))
                    }
                    TextButton(onClick = { downloadChoiceModel = null }) { Text(stringResource(R.string.cancel)) }
                }
            }
        )
    }

    pendingMigration?.let { migration ->
        val context = LocalContext.current
        val totalSize = remember(migration) { migration.modelsToMigrate.sumOf { it.sizeBytes } }
        val sizeFormatted = remember(totalSize) { Formatter.formatFileSize(context, totalSize) }

        AlertDialog(
            onDismissRequest = onCancelMigration,
            title = { Text(stringResource(R.string.migrate_models_title)) },
            text = {
                Column {
                    Text(
                        stringResource(
                            if (migration.isFromDownloads) R.string.migrate_models_from_downloads
                            else R.string.migrate_models_message,
                            migration.modelsToMigrate.size,
                            sizeFormatted
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.models_to_migrate), style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    migration.modelsToMigrate.forEach { model ->
                        Text("• ${model.displayName}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = onConfirmMigration) { Text(stringResource(R.string.migrate)) } },
            dismissButton = {
                Row {
                    TextButton(onClick = onSkipMigration) { Text(stringResource(R.string.skip)) }
                    TextButton(onClick = onCancelMigration) { Text(stringResource(R.string.cancel)) }
                }
            }
        )
    }

    migrationProgress?.let { progress ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.migrating_models)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.migration_progress, progress.currentIndex, progress.totalCount))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(progress.currentModel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { }
        )
    }

    inspectingBagCartridge?.let { cart ->
        val isInserted = activeCartridgeId == cart.id
        CartridgeDetailDialog(
            cartridge = cart,
            onDismiss = { inspectingBagCartridge = null },
            primaryActionLabel = if (isInserted) null else "เสียบตลับใช้งาน",
            onPrimaryAction = if (isInserted) null else { { onInsertCartridge(cart) } }
        )
    }

    if (showCreateCartridgeDialog) {
        var cName by remember { mutableStateOf("") }
        var cDesc by remember { mutableStateOf("") }
        var cKnowledge by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateCartridgeDialog = false },
            title = { Text("สร้างตลับข้อมูลใหม่") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = cName, onValueChange = { cName = it }, label = { Text("ชื่อตลับ") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = cDesc, onValueChange = { cDesc = it }, label = { Text("คำอธิบายความสามารถ") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = cKnowledge, onValueChange = { cKnowledge = it }, label = { Text("เนื้อหาคลังความรู้") }, minLines = 4, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onInstallCustomCartridge(
                            AiCartridge(
                                id = "custom_${System.currentTimeMillis()}",
                                name = cName.trim(),
                                description = cDesc.trim().ifBlank { "ตลับกำหนดเอง" },
                                knowledge = AiCartridge.parseRawKnowledgeText(cKnowledge.trim()),
                                colorHex = 0xFF00E5FF,
                                isCustom = true
                            )
                        )
                        showCreateCartridgeDialog = false
                        Toast.makeText(bagContext, "สร้างตลับเรียบร้อย!", Toast.LENGTH_SHORT).show()
                    },
                    enabled = cName.isNotBlank() && cKnowledge.isNotBlank()
                ) { Text("บันทึกตลับ") }
            },
            dismissButton = { TextButton(onClick = { showCreateCartridgeDialog = false }) { Text("ยกเลิก") } }
        )
    }
}

@Composable
private fun CompactFolderCard(
    title: String,
    path: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onChangeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(path, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onChangeClick, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp), modifier = Modifier.height(30.dp)) {
                Text("เปลี่ยนโฟลเดอร์", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun StorageInfoCard(
    storageInfo: StorageInfo,
    onChangeFolderClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val usedFormatted = remember(storageInfo.usedBytes) { Formatter.formatFileSize(context, storageInfo.usedBytes) }
    val availableFormatted = remember(storageInfo.availableBytes) { Formatter.formatFileSize(context, storageInfo.availableBytes) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Folder, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (storageInfo.isCustomFolder) stringResource(R.string.custom_folder) else stringResource(R.string.downloads_folder),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(storageInfo.path, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = onChangeFolderClick) { Text(stringResource(R.string.change_folder)) }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (storageInfo.totalBytes > 0) {
                val progressValue = storageInfo.usedBytes.toFloat() / storageInfo.totalBytes.toFloat()
                LinearProgressIndicator(progress = { progressValue.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.storage_used_models, usedFormatted), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.storage_available, availableFormatted), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text(stringResource(R.string.storage_used_models, usedFormatted), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ModelLogoAvatar(logoRes: Int) {
    if (logoRes != 0) {
        Image(
            painter = painterResource(id = logoRes),
            contentDescription = null,
            modifier = Modifier.size(42.dp).clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Surface(modifier = Modifier.size(42.dp), shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Memory, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(22.dp))
            }
        }
    }
    Spacer(modifier = Modifier.width(12.dp))
}

@Composable
private fun DownloadedModelItem(
    model: ModelInfo,
    vision: Boolean,
    isActive: Boolean = false,
    boundCartridgeName: String? = null,
    hasCustomPrompt: Boolean = false,
    enabledToolsCount: Int = 0,
    onSelectClick: (() -> Unit)? = null,
    onUnloadClick: (() -> Unit)? = null,
    onConfigureClick: (() -> Unit)? = null,
    onDeleteClick: () -> Unit
) {
    val dateLabel = remember(model.releaseDate) { model.releaseDateLabel() }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
        ),
        border = BorderStroke(
            width = if (isActive) 1.5.dp else 1.dp,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth().then(
            if (onSelectClick != null && !isActive) Modifier.clickable(onClick = onSelectClick) else Modifier
        )
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ModelLogoAvatar(logoRes = model.logoRes)
            Column(modifier = Modifier.weight(1f)) {
                Text(model.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(2.dp))
                Text(model.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModelCapabilityIcons(model = model, vision = vision)
                    if (model.releaseDate != null) {
                        Text(dateLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (boundCartridgeName != null || hasCustomPrompt || enabledToolsCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (boundCartridgeName != null) {
                            Text("ตลับ: $boundCartridgeName", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        }
                        if (hasCustomPrompt) {
                            Text("• บุคลิก", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, maxLines = 1)
                        }
                        if (enabledToolsCount > 0) {
                            Text("• เครื่องมือ ($enabledToolsCount)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, maxLines = 1)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (onSelectClick != null) {
                if (isActive && onUnloadClick != null) {
                    OutlinedButton(
                        onClick = onUnloadClick,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.7f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Outlined.StopCircle, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.stop_using_model), style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
                    }
                } else if (isActive) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.model_in_use), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    FilledTonalButton(onClick = onSelectClick, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                        Text(stringResource(R.string.use_model), style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
            }
            if (onConfigureClick != null) {
                FilledTonalIconButton(
                    onClick = onConfigureClick,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                ) {
                    Icon(Icons.Outlined.Tune, "ตั้งค่าโมเดล", modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
            }
            FilledTonalIconButton(
                onClick = onDeleteClick,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(Icons.Outlined.Delete, stringResource(R.string.delete_model), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun AvailableModelItem(
    modelWithStatus: ModelWithStatus,
    moduleOnly: Boolean,
    downloadProgress: DownloadProgress?,
    onDownloadClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    val model = modelWithStatus.model
    val context = LocalContext.current
    val dateLabel = remember(model.releaseDate) { model.releaseDateLabel() }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ModelLogoAvatar(logoRes = model.logoRes)
            Column(modifier = Modifier.weight(1f)) {
                Text(model.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(2.dp))
                if (downloadProgress != null) {
                    Text(formatDownloadStats(context, downloadProgress), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                } else {
                    Text(
                        text = if (moduleOnly) stringResource(R.string.image_module_description) else model.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (downloadProgress == null && !moduleOnly) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModelCapabilityIcons(model = model, vision = model.isVision)
                        if (model.releaseDate != null) {
                            Text(dateLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (downloadProgress != null) {
                Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    if (downloadProgress.progress >= 0f) {
                        CircularProgressIndicator(progress = { downloadProgress.progress }, modifier = Modifier.size(38.dp), strokeWidth = 3.dp)
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(38.dp), strokeWidth = 3.dp)
                    }
                    IconButton(onClick = onCancelClick, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Outlined.Close, stringResource(R.string.cancel), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                FilledTonalIconButton(
                    onClick = onDownloadClick,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = stringResource(if (moduleOnly) R.string.cd_download_image_module else R.string.download_model),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private fun formatDownloadStats(context: Context, progress: DownloadProgress): String {
    if (progress.progress < 0f) return progress.status
    val parts = mutableListOf<String>()
    if (progress.totalBytes > 0) {
        parts.add("${Formatter.formatFileSize(context, progress.bytesDownloaded)} / ${Formatter.formatFileSize(context, progress.totalBytes)}")
    } else if (progress.bytesDownloaded > 0) {
        parts.add(Formatter.formatFileSize(context, progress.bytesDownloaded))
    } else {
        return progress.status
    }
    if (progress.speedBytesPerSec > 0) parts.add("${Formatter.formatFileSize(context, progress.speedBytesPerSec)}/s")
    if (progress.etaSeconds > 0) parts.add(formatEta(progress.etaSeconds))
    return parts.joinToString(" • ")
}

private fun formatEta(seconds: Long): String = when {
    seconds < 60 -> "${seconds}s left"
    seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s left"
    else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m left"
}

@Preview
@Composable
private fun ModelsScreenPreview() {
    PlaygroundTheme {
        ModelsScreen(
            storageInfo = StorageInfo("/storage/emulated/0/Download", 5_000_000_000, 64_000_000_000, 30_000_000_000, false),
            allModels = ModelInfoProvider.allModels.take(5).mapIndexed { index, model -> ModelWithStatus(model = model, isDownloaded = index < 2) },
            downloadingModels = emptyMap(),
            snackbarMessage = null,
            pendingMigration = null,
            migrationProgress = null,
            deviceLanguage = "th",
            onBackClick = {},
            onChangeFolderClick = {},
            onDeleteModel = {},
            onDownloadModel = { _, _ -> },
            onDownloadMmproj = {},
            onCancelDownload = {},
            onSnackbarDismiss = {},
            onConfirmMigration = {},
            onSkipMigration = {},
            onCancelMigration = {}
        )
    }
}
