package com.druk.ai_offline_llm.storage

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri

class StoragePreferences(context: Context) {

    private val prefs = context.getSharedPreferences("storage_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_URI = "model_storage_uri"
    }

    var modelStorageUri: Uri?
        get() = prefs.getString(KEY_URI, null)?.toUri()
        set(value) = prefs.edit { putString(KEY_URI, value?.toString()) }

    /** Play a chime when generation finishes while the app is backgrounded. */
    var soundOnCompletion: Boolean
        get() = prefs.getBoolean("sound_on_completion", true)
        set(value) = prefs.edit { putBoolean("sound_on_completion", value) }

    /** Per-token typewriter haptic while a response streams in. */
    var hapticOnGeneration: Boolean
        get() = prefs.getBoolean("haptic_on_generation", true)
        set(value) = prefs.edit { putBoolean("haptic_on_generation", value) }

    /**
     * Disable llama.cpp CPU weight repacking (load weights memory-mapped only).
     * OFF by default -> repacking stays on, which is faster but allocates a
     * second resident copy of the weights (~2x footprint) and can OOM-crash
     * large models on low-RAM devices.
     */
    var repackEnabled: Boolean
        get() = !prefs.getBoolean("disable_repack", false)
        set(value) = prefs.edit { putBoolean("disable_repack", !value) }

    fun getCustomModelMetadata(filename: String): Pair<String, Boolean>? {
        val value = prefs.getString("custom_model_$filename", null) ?: return null
        val parts = value.split("|", limit = 2)
        if (parts.size != 2) return null
        return Pair(parts[0], parts[1].toBoolean())
    }

    fun setCustomModelMetadata(filename: String, name: String, hasChatTemplate: Boolean) {
        prefs.edit { putString("custom_model_$filename", "$name|$hasChatTemplate") }
    }

    fun removeCustomModelMetadata(filename: String) {
        prefs.edit { remove("custom_model_$filename") }
    }

    fun getModelGenerationParams(filename: String): Map<String, Float>? {
        val json = prefs.getString("gen_params_$filename", null) ?: return null
        return try {
            json.split(",").associate {
                val (k, v) = it.split("=", limit = 2)
                k to v.toFloat()
            }
        } catch (_: Exception) { null }
    }

    fun setModelGenerationParams(filename: String, params: Map<String, Float>) {
        val encoded = params.entries.joinToString(",") { "${it.key}=${it.value}" }
        prefs.edit { putString("gen_params_$filename", encoded) }
    }

    // --- Per-Model Tool Enablement (Isolated per model, OFF by default) ---

    fun isModelToolEnabled(filename: String, toolName: String): Boolean {
        val perModelKey = "model_tool_${filename}_$toolName"
        if (prefs.contains(perModelKey)) {
            return prefs.getBoolean(perModelKey, false)
        }
        val legacyOverrideKey = "tool_override_${filename}_$toolName"
        if (prefs.contains(legacyOverrideKey)) {
            return prefs.getBoolean(legacyOverrideKey, false)
        }
        return false
    }

    fun setModelToolEnabled(filename: String, toolName: String, enabled: Boolean) {
        prefs.edit {
            putBoolean("model_tool_${filename}_$toolName", enabled)
            remove("tool_override_${filename}_$toolName")
        }
    }

    // Legacy compatibility wrappers (mapped to per-model isolation)
    fun isToolEnabledDefault(toolName: String): Boolean {
        return prefs.getBoolean("tool_enabled_$toolName", false)
    }

    fun setToolEnabledDefault(toolName: String, enabled: Boolean) {
        prefs.edit { putBoolean("tool_enabled_$toolName", enabled) }
    }

    fun hasToolOverride(filename: String, toolName: String): Boolean {
        return prefs.contains("model_tool_${filename}_$toolName") ||
            prefs.contains("tool_override_${filename}_$toolName")
    }

    fun setToolOverride(filename: String, toolName: String, enabled: Boolean) {
        setModelToolEnabled(filename, toolName, enabled)
    }

    fun effectiveToolEnabled(filename: String, toolName: String): Boolean {
        return isModelToolEnabled(filename, toolName)
    }

    // --- Per-Model System Prompt Binding ---

    fun getModelSystemPromptId(filename: String): String? {
        return prefs.getString("model_prompt_id_$filename", null)
    }

    fun setModelSystemPromptId(filename: String, promptId: String?) {
        prefs.edit {
            if (promptId.isNullOrBlank()) {
                remove("model_prompt_id_$filename")
            } else {
                putString("model_prompt_id_$filename", promptId)
            }
        }
    }

    fun getModelCustomPromptText(filename: String): String? {
        return prefs.getString("model_prompt_text_$filename", null)
    }

    fun setModelCustomPromptText(filename: String, text: String?) {
        prefs.edit {
            if (text.isNullOrBlank()) {
                remove("model_prompt_text_$filename")
            } else {
                putString("model_prompt_text_$filename", text)
            }
        }
    }

    fun resetModelProfile(filename: String) {
        val toolPrefix = "model_tool_${filename}_"
        val legacyToolPrefix = "tool_override_${filename}_"
        val keysToRemove = prefs.all.keys.filter {
            it == "gen_params_$filename" ||
                it == "model_prompt_id_$filename" ||
                it == "model_prompt_text_$filename" ||
                it.startsWith(toolPrefix) ||
                it.startsWith(legacyToolPrefix)
        }
        prefs.edit {
            keysToRemove.forEach { remove(it) }
        }
    }

    // --- Detected model capabilities cache ---

    fun getDetectedCaps(filename: String): Pair<Boolean, Boolean>? {
        if (!prefs.contains("caps_tools_$filename")) return null
        return Pair(
            prefs.getBoolean("caps_tools_$filename", false),
            prefs.getBoolean("caps_thinking_$filename", false),
        )
    }

    fun setDetectedCaps(filename: String, supportsTools: Boolean, supportsThinking: Boolean) {
        prefs.edit {
            putBoolean("caps_tools_$filename", supportsTools)
            putBoolean("caps_thinking_$filename", supportsThinking)
        }
    }

    // --- One-time "image support available" chat nudge ---

    fun wasVisionModuleHintShown(filename: String): Boolean {
        return prefs.getBoolean("vision_hint_$filename", false)
    }

    fun setVisionModuleHintShown(filename: String) {
        prefs.edit { putBoolean("vision_hint_$filename", true) }
    }

    fun clear() {
        prefs.edit { clear() }
    }
}
