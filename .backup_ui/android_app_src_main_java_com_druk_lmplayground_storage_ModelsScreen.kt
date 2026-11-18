@file:OptIn(ExperimentalMaterial3Api::class)

package com.druk.ai_offline_llm.storage

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.druk.ai_offline_llm.models.ModelCapabilityIcons
import com.druk.ai_offline_llm.models.ModelInfo
import com.druk.ai_offline_llm.models.ModelInfoProvider
import com.druk.ai_offline_llm.models.ModelWithStatus
import com.druk.ai_offline_llm.models.releaseDateLabel
import com.druk.ai_offline_llm.models.supportsLanguage
import com.druk.ai_offline_llm.theme.PlaygroundTheme

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
    embeddingModel: ModelWithStatus? = null,
    dictationModel: ModelWithStatus? = null,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.models)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
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
    maxContentWidth: Dp = 960.dp,
    embeddingModel: ModelWithStatus? = null,
    dictationModel: ModelWithStatus? = null,
) {
    var modelToDelete by remember { mutableStateOf<ModelInfo?>(null) }
    var downloadChoiceModel by remember { mutableStateOf<ModelInfo?>(null) }

    LaunchedEffect(snackbarMessage) {
        if (snackbarMessage != null) {
            snackbarHostState.showSnackbar(snackbarMessage)
            onSnackbarDismiss()
        }
    }

    // แคชรายการที่กรองแล้วด้วย remember เพื่อลดการคำนวณซ้ำตอนเลื่อนหน้าจอหรือตอนดาวน์โหลด
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
                // Storage info card
                if (storageInfo != null) {
                    item(key = "storage_card", span = { GridItemSpan(maxLineSpan) }) {
                        StorageInfoCard(
                            storageInfo = storageInfo,
                            onChangeFolderClick = onChangeFolderClick,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }

                // Downloaded section header
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
                        Text(
                            text = stringResource(R.string.no_downloaded_models),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    items(
                        items = downloadedModels,
                        key = { "dl_" + it.model.filename },
                        contentType = { "downloaded_model" }
                    ) { modelWithStatus ->
                        DownloadedModelItem(
                            model = modelWithStatus.model,
                            vision = modelWithStatus.isMmprojDownloaded,
                            onDeleteClick = { modelToDelete = modelWithStatus.model }
                        )
                    }
                }

                if (showEmbeddingRow && embeddingModel != null) {
                    item(
                        key = "embedding_" + embeddingModel.model.filename,
                        contentType = "special_model"
                    ) {
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
                    item(
                        key = "dictation_" + dictationModel.model.filename,
                        contentType = "special_model"
                    ) {
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

                // Supported / Available models
                if (supportedModels.isNotEmpty()) {
                    item(key = "header_supported", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = stringResource(
                                if (splitByLanguage) R.string.supports_your_language
                                else R.string.available_models
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
                        )
                    }

                    items(
                        items = supportedModels,
                        key = { "avail_" + it.model.filename },
                        contentType = { "available_model" }
                    ) { modelWithStatus ->
                        val moduleOnly = modelWithStatus.needsVisionModule
                        val progressKey = if (moduleOnly)
                            modelWithStatus.model.name + " (vision)"
                        else
                            modelWithStatus.model.name
                        AvailableModelItem(
                            modelWithStatus = modelWithStatus,
                            moduleOnly = moduleOnly,
                            downloadProgress = downloadingModels[progressKey],
                            onDownloadClick = { onAvailableDownload(modelWithStatus) },
                            onCancelClick = { onCancelDownload(modelWithStatus.model) }
                        )
                    }
                }

                // Other models
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
                        val progressKey = if (moduleOnly)
                            modelWithStatus.model.name + " (vision)"
                        else
                            modelWithStatus.model.name
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

    // Delete confirmation dialog
    modelToDelete?.let { model ->
        AlertDialog(
            onDismissRequest = { modelToDelete = null },
            title = { Text(stringResource(R.string.delete_model_confirm, model.name)) },
            text = { Text(stringResource(R.string.delete_model_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteModel(model)
                        modelToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.delete_model))
                }
            },
            dismissButton = {
                TextButton(onClick = { modelToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Vision download choice
    downloadChoiceModel?.let { model ->
        AlertDialog(
            onDismissRequest = { downloadChoiceModel = null },
            title = { Text(stringResource(R.string.download_with_vision_title)) },
            text = { Text(stringResource(R.string.download_with_vision_message, model.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDownloadModel(model, true)
                        downloadChoiceModel = null
                    }
                ) {
                    Text(stringResource(R.string.download_with_images))
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            onDownloadModel(model, false)
                            downloadChoiceModel = null
                        }
                    ) {
                        Text(stringResource(R.string.download_text_only))
                    }
                    TextButton(onClick = { downloadChoiceModel = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        )
    }

    // Migration confirmation dialog
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
                            if (migration.isFromDownloads) {
                                R.string.migrate_models_from_downloads
                            } else {
                                R.string.migrate_models_message
                            },
                            migration.modelsToMigrate.size,
                            sizeFormatted
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.models_to_migrate),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    migration.modelsToMigrate.forEach { model ->
                        Text(
                            text = "• ${model.displayName}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirmMigration) {
                    Text(stringResource(R.string.migrate))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = onSkipMigration) {
                        Text(stringResource(R.string.skip))
                    }
                    TextButton(onClick = onCancelMigration) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        )
    }

    // Migration progress dialog
    migrationProgress?.let { progress ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.migrating_models)) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(
                            R.string.migration_progress,
                            progress.currentIndex,
                            progress.totalCount
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = progress.currentModel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = { }
        )
    }
}

@Composable
private fun StorageInfoCard(
    storageInfo: StorageInfo,
    onChangeFolderClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val usedFormatted = remember(storageInfo.usedBytes) {
        Formatter.formatFileSize(context, storageInfo.usedBytes)
    }
    val availableFormatted = remember(storageInfo.availableBytes) {
        Formatter.formatFileSize(context, storageInfo.availableBytes)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (storageInfo.isCustomFolder)
                            stringResource(R.string.custom_folder)
                        else
                            stringResource(R.string.downloads_folder),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = storageInfo.path,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = onChangeFolderClick) {
                    Text(stringResource(R.string.change_folder))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (storageInfo.totalBytes > 0) {
                val progressValue = storageInfo.usedBytes.toFloat() / storageInfo.totalBytes.toFloat()
                LinearProgressIndicator(
                    progress = { progressValue.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.storage_used_models, usedFormatted),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.storage_available, availableFormatted),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.storage_used_models, usedFormatted),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DownloadedModelItem(
    model: ModelInfo,
    vision: Boolean,
    onDeleteClick: () -> Unit
) {
    val dateLabel = remember(model.releaseDate) { model.releaseDateLabel() }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (model.logoRes != 0) {
                Image(
                    painter = painterResource(id = model.logoRes),
                    contentDescription = null,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = model.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ModelCapabilityIcons(
                        model = model,
                        vision = vision
                    )
                    if (model.releaseDate != null) {
                        Text(
                            text = dateLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalIconButton(
                onClick = onDeleteClick,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.delete_model),
                    modifier = Modifier.size(20.dp)
                )
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (model.logoRes != 0) {
                Image(
                    painter = painterResource(id = model.logoRes),
                    contentDescription = null,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (downloadProgress != null) {
                    Text(
                        text = formatDownloadStats(context, downloadProgress),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = if (moduleOnly)
                            stringResource(R.string.image_module_description)
                        else
                            model.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (downloadProgress == null && !moduleOnly) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ModelCapabilityIcons(
                            model = model,
                            vision = model.isVision
                        )
                        if (model.releaseDate != null) {
                            Text(
                                text = dateLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (downloadProgress != null) {
                Box(
                    modifier = Modifier.size(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (downloadProgress.progress >= 0f) {
                        CircularProgressIndicator(
                            progress = { downloadProgress.progress },
                            modifier = Modifier.size(38.dp),
                            strokeWidth = 3.dp
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(38.dp),
                            strokeWidth = 3.dp
                        )
                    }
                    IconButton(
                        onClick = onCancelClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.cancel),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                        contentDescription = stringResource(
                            if (moduleOnly) R.string.cd_download_image_module
                            else R.string.download_model
                        ),
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
        val downloaded = Formatter.formatFileSize(context, progress.bytesDownloaded)
        val total = Formatter.formatFileSize(context, progress.totalBytes)
        parts.add("$downloaded / $total")
    } else if (progress.bytesDownloaded > 0) {
        parts.add(Formatter.formatFileSize(context, progress.bytesDownloaded))
    } else {
        return progress.status
    }

    if (progress.speedBytesPerSec > 0) {
        parts.add("${Formatter.formatFileSize(context, progress.speedBytesPerSec)}/s")
    }

    if (progress.etaSeconds > 0) {
        parts.add(formatEta(progress.etaSeconds))
    }

    return parts.joinToString(" \u2022 ")
}

private fun formatEta(seconds: Long): String {
    return when {
        seconds < 60 -> "${seconds}s left"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s left"
        else -> {
            val hours = seconds / 3600
            val mins = (seconds % 3600) / 60
            "${hours}h ${mins}m left"
        }
    }
}

@Preview
@Composable
private fun ModelsScreenPreview() {
    PlaygroundTheme {
        ModelsScreen(
            storageInfo = StorageInfo(
                path = "/storage/emulated/0/Download",
                usedBytes = 5_000_000_000,
                totalBytes = 64_000_000_000,
                availableBytes = 30_000_000_000,
                isCustomFolder = false
            ),
            allModels = ModelInfoProvider.allModels.take(5).mapIndexed { index, model ->
                ModelWithStatus(model = model, isDownloaded = index < 2)
            },
            downloadingModels = mapOf(
                "Gemma 3 4B" to DownloadProgress(
                    modelName = "Gemma 3 4B",
                    progress = 0.45f,
                    status = "Downloading…",
                    bytesDownloaded = 1_200_000_000L,
                    totalBytes = 2_700_000_000L,
                    speedBytesPerSec = 15_000_000L,
                    etaSeconds = 100L
                )
            ),
            snackbarMessage = null,
            pendingMigration = null,
            migrationProgress = null,
            deviceLanguage = "en",
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
