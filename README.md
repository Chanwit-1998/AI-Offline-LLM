# AI-OFFLINE-LLM

**AI-OFFLINE-LLM** is an Android application for running Large Language Models locally on-device. Download models, load them in one tap, and chat - all offline, all private. Powered by llama.cpp with GGUF-format models from Hugging Face[span_5](start_span)[span_5](end_span).

## Features

- **On-device inference** - no cloud, no API keys, fully offline[span_6](start_span)[span_6](end_span)
- **Document Q&A (RAG)** - attach PDF, Word, EPUB, HTML, Markdown, or plain-text files and ask about them; on-device semantic search with EmbeddingGemma feeds the most relevant passages to any chat model[span_7](start_span)[span_7](end_span)
- **Vision/image input** - attach a photo from your gallery or camera and ask vision-capable models about it[span_8](start_span)[span_8](end_span)
- **Rich markdown** in chat responses - headers, code blocks, lists, and more[span_9](start_span)[span_9](end_span)
- **Reasoning model support** - thinking steps from models like DeepSeek are displayed in a styled section[span_10](start_span)[span_10](end_span)
- **Tools** - capable models can search the web, fetch a page, and run JavaScript mid-reply, each tool is off by default and enabled per model[span_11](start_span)[span_11](end_span)
- **Reusable system prompts** - save a persona, tone, or output format once and apply it to any model[span_12](start_span)[span_12](end_span)
- **Edit, regenerate & branch** - long-press a message to edit and resend it, regenerate a reply, or continue from any reply in a new chat[span_13](start_span)[span_13](end_span)
- **Voice dictation** - tap the mic and speak; on-device speech recognition, fully offline[span_14](start_span)[span_14](end_span)
- **Chat history** - conversations are saved and organized; pin, rename, delete, and resume sessions from the sidebar[span_15](start_span)[span_15](end_span)
- **Custom GGUF models** - load your own model files from any source alongside the built-in catalog[span_16](start_span)[span_16](end_span)
- **Reliable background downloads** - custom download engine with OkHttp and WorkManager, progress notifications with speed and ETA[span_17](start_span)[span_17](end_span)
- **Storage management** - choose where to keep multi-GB model files with Android's Storage Access Framework[span_18](start_span)[span_18](end_span)

## Build Instructions

1. Clone the repository with submodules:
   ```bash
   git clone --recurse-submodules [https://github.com/Chanwit-1998/AI-Offline-LLM.git](https://github.com/Chanwit-1998/AI-Offline-LLM.git)
