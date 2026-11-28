<div align="center">

<img src="logo.png" alt="ChatLLM Logo" width="120" height="120" />

# ChatLLM

**ประมวลผล AI บนอุปกรณ์ Galaxy ของคุณโดยตรง ไม่ต้องใช้อินเทอร์เน็ต — 100% Offline, 100% Private**

Native Android application (`com.chanwit.chatllm`) for running Large Language Models, Vision/OCR, Image Generation, Speech Dictation, and Local RAG on-device powered by `llama.cpp` and `stable-diffusion.cpp`.

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Offline](https://img.shields.io/badge/Mode-100%25%20Offline-0F766E?style=for-the-badge)]()
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](LICENSE)

[Overview](#-overview) • [Features](#-features) • [Supported Models](#-supported-models) • [App Architecture](#-app-architecture) • [Build Instructions](#-build-instructions) • [License](#-license)

</div>

---

## Overview

ChatLLM (`com.chanwit.chatllm`) is a native Android application built with Kotlin and Jetpack Compose (Material 3) designed to run AI workloads directly on-device without cloud servers or subscriptions. It integrates `llama.cpp` for text and multimodal inference, `stable-diffusion.cpp` for local image generation, on-device speech dictation and voice conversation, and local vector embeddings for document Q&A (RAG).

---

## Features

### 100% Offline AI & Multimodal Capabilities
* **On-Device Inference** — Runs quantized GGUF models locally via native C++ / JNI and `LlamaService` with configurable sampling parameters.
* **Reasoning & Thinking Blocks** — Displays reasoning/thinking output in collapsible sections alongside final responses.
* **Vision & OCR (`mmproj`)** — Attach images from your gallery or camera to inspect photos and extract text with vision-capable models.
* **Offline Image Generation** — Built-in support for diffusion models (`stable-diffusion.cpp`) such as SDXS 512 TinySD Distilled.

### Voice, Knowledge & Skills
* **Offline Voice Dictation & Live Voice Mode** — Speak into the microphone with configurable silence timeouts, continuous listening in voice mode, and emoji-filtered text-to-speech playback.
* **Local Document Q&A (RAG)** — Attach supported documents (such as PDF, Word, EPUB, HTML, Markdown, and plain text) with on-device embeddings (`EmbeddingGemma 300M`) to retrieve relevant context.
* **AI Skills, System Prompts & Packs** — Manage reusable AI Skills, system prompts, and offline packs/cartridges across sessions.
* **Local Utilities** — Includes local JavaScript execution and HTML-to-Markdown conversion helpers within the app's tool modules.

### Chat Experience, Storage & Settings
* **Rich Chat Controls** — Markdown and syntax-highlighted code rendering, message editing, regenerating replies, and branching conversations.
* **Background Downloads & Storage Management** — Download models via `WorkManager` / `OkHttp` with progress notifications, and manage storage folders or external SD cards via Android Storage Access Framework (SAF).
* **Customization & Licenses** — Multi-language UI support (including Thai and English), sound and haptic feedback settings, privacy policy, and an in-app **Open Source Licenses** screen (`© 2026 Chanwit™`).

---

## Supported Models

The built-in model catalog in `ModelInfoProvider` includes mobile-friendly models and companion `mmproj` files alongside custom `.gguf` support:
* **Image Generation** — SDXS 512 TinySD Distilled (`sdxs-512-tinySDdistilled_Q8_0.gguf`)
* **Qwen & Qwen VL** — Qwen 3 (0.6B, 1.7B, 4B), Qwen 3.5 (0.8B, 2B, 4B with `mmproj`), Qwen2.5 (0.5B, 1.5B)
* **Gemma** — Gemma 3 (1B, 4B with `mmproj`), Gemma 3n (E2B, E4B), Gemma 4 (E2B, E4B with `mmproj`, 12B, plus Q4_K_M variants), Gemma2 9B
* **Liquid AI LFM2.5** — LFM2.5 (350M, 1.2B, 1.2B Thinking, 2.6B) and LFM2.5 VL (450M, 1.6B, 3B with `mmproj`)
* **Reasoning & Other Families** — DeepSeek R1 Distill (1.5B, 7B), Spark-X2.5 4B, Llama 3.2 (1B, 3B) & Llama 3.1 8B, Phi-4 mini & Phi3.5 mini, Ministral 3 (3B / 8B Instruct & Reasoning with `mmproj`), Granite 4.0 / 4.1 / 4.2, Nemotron 3 Nano 4B, SmolLM3 3B, MiniCPM5 (1B, 2B), Mistral 7B, GPT-OSS 20B
* **Embeddings & ASR** — EmbeddingGemma 300M (`embeddinggemma-300m-Q4_0.gguf`), Nemotron 3.5 ASR Streaming 0.6B (`nemotron-3.5-asr-streaming-0.6b-q4_k.gguf`)

---

## App Architecture

| Module / Layer | Responsibility |
| :--- | :--- |
| `com.druk.lmplayground.conversation` | Chat UI, session storage, image attachments, rewind/branching, and live voice overlay |
| `com.druk.llamacpp` & `inference` | JNI bindings, `LlamaService`, generation/embedding sessions, and background generation |
| `android/sdcpp` & `stable-diffusion.cpp` | Native C++ / JNI diffusion image generation engine |
| `com.druk.lmplayground.dictation` & `assistant` | Audio recording, speech recognition lifecycle, earcons, and voice assistant services |
| `com.druk.lmplayground.rag` & `cartridge` | Document text extraction, chunking, vector embeddings/RAG, and offline packs |
| `com.druk.lmplayground.data`, `storage` & `download` | Room database persistence, SAF storage management, and `WorkManager` downloads |
| `com.druk.lmplayground.settings` & `tools` | AI Skills, system prompts, language, sound/haptic, open-source licenses, and local tools |

---

## Build Instructions

### Prerequisites
* JDK 17
* Android SDK, NDK, and CMake

### Build Steps

1. Clone the repository with submodules:
   ```bash
   git clone --recurse-submodules [https://github.com/Chanwit-1998/AI-Offline-LLM.git](https://github.com/Chanwit-1998/AI-Offline-LLM.git)
   cd AI-Offline-LLM
