package com.druk.ai_offline_llm.tools

import android.content.Context
import com.druk.llamacpp.tools.ToolRegistry

fun ToolRegistry.Companion.createDefault(context: Context): ToolRegistry =
    ToolRegistry().apply {
        register(DeviceStatusTool(context))
        register(DateTimeTool())
        register(AlarmTimerTool(context))
        register(NotesTool(context))
        register(ContactsTool(context))
        register(JavaScriptTool(context))
        register(WebSearchTool(webLinkStore))
        register(WebFetchTool(webLinkStore))
    }
