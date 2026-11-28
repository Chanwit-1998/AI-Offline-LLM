package com.druk.ai_offline_llm.models

import android.content.Context
import com.druk.ai_offline_llm.cartridge.CartridgeManager
import com.druk.ai_offline_llm.conversation.GenerationParams
import com.druk.ai_offline_llm.storage.StoragePreferences

data class ModelProfile(
    val filename: String,
    val params: GenerationParams = GenerationParams(),
    val systemPromptId: String? = null,
    val customPromptText: String? = null,
    val enabledTools: Set<String> = emptySet(),
    val cartridgeId: String? = null
)

object ModelProfileStore {

    fun load(
        context: Context,
        filename: String,
        availableToolNames: List<String> = emptyList()
    ): ModelProfile {
        val prefs = StoragePreferences(context)
        val paramsMap = prefs.getModelGenerationParams(filename)
        val params = if (paramsMap != null) GenerationParams.fromMap(paramsMap) else GenerationParams()
        val enabledTools = availableToolNames.filter { prefs.isModelToolEnabled(filename, it) }.toSet()
        return ModelProfile(
            filename = filename,
            params = params,
            systemPromptId = prefs.getModelSystemPromptId(filename),
            customPromptText = prefs.getModelCustomPromptText(filename),
            enabledTools = enabledTools,
            cartridgeId = CartridgeManager.getActiveCartridgeId(context, filename)
        )
    }

    fun saveParams(context: Context, filename: String, params: GenerationParams) {
        StoragePreferences(context).setModelGenerationParams(filename, params.toMap())
    }

    fun savePrompt(context: Context, filename: String, promptId: String?, customText: String?) {
        val prefs = StoragePreferences(context)
        prefs.setModelSystemPromptId(filename, promptId)
        prefs.setModelCustomPromptText(filename, customText)
    }

    fun saveToolEnabled(context: Context, filename: String, toolName: String, enabled: Boolean) {
        StoragePreferences(context).setModelToolEnabled(filename, toolName, enabled)
    }

    fun saveCartridge(context: Context, filename: String, cartridgeId: String?) {
        CartridgeManager.setActiveCartridgeId(context, filename, cartridgeId)
    }

    fun reset(context: Context, filename: String) {
        StoragePreferences(context).resetModelProfile(filename)
        CartridgeManager.setActiveCartridgeId(context, filename, null)
    }
}
