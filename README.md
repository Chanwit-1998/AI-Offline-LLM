<div align="center">

<img src="logo.png" alt="ChatLLM Logo" width="120" height="120" />

# ChatLLM

**ประมวลผล AI บนอุปกรณ์ Galaxy ของคุณโดยตรง ไม่ต้องใช้อินเทอร์เน็ต — 100% Offline, 100% Private**

Exclusively available on Samsung Galaxy Store — Run Large Language Models locally on your Android device powered by llama.cpp and GGUF models from Hugging Face.

[![Distribution](https://img.shields.io/badge/Galaxy%20Store-Exclusive-1428A0?style=for-the-badge&logo=samsung&logoColor=white)](https://galaxystore.samsung.com/)
[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](LICENSE)

[Download](#-download) • [Screenshots](#-screenshots) • [Features](#-features) • [Supported Models](#-supported-models) • [Build Instructions](#-build-instructions) • [License](#-license)

</div>

---

## Overview

ChatLLM is a minimalist, native Android application built with Jetpack Compose for running Large Language Models directly on-device without cloud servers, subscriptions, or API keys. Distributed exclusively via Samsung Galaxy Store, ChatLLM lets you download curated models or import your own .gguf files, load them in one tap, and chat completely offline.

---

## Download

This application is distributed exclusively on the Samsung Galaxy Store:
* Search for ChatLLM (com.chanwit.chatllm) in the Samsung Galaxy Store on your Samsung Galaxy device.

---

## Screenshots

<div align="center">
  <table>
    <tr>
      <td align="center">
        <img src="docs/images/screenshot_home.jpg" alt="ChatLLM Home Screen" width="240" /><br />
        <b>Minimalist Chat Interface</b>
      </td>
      <td align="center">
        <img src="docs/images/screenshot_drawer.jpg" alt="ChatLLM Navigation Drawer" width="240" /><br />
        <b>Clean Navigation & AI Personas</b>
      </td>
      <td align="center">
        <img src="docs/images/screenshot_store.jpg" alt="ChatLLM Model Store" width="240" /><br />
        <b>Built-in Offline Model Store</b>
      </td>
    </tr>
  </table>
</div>

---

## Features

### On-Device AI & Privacy
* 100% Offline Inference — Zero cloud dependencies, no API keys required, and complete data privacy.
* Reasoning Model Support — Step-by-step thinking processes from reasoning models (such as DeepSeek, LFM2.5 Reasoning, and Spark-X2.5) are displayed in a dedicated collapsible block.
* Vision & OCR Capabilities — Attach images from your gallery or camera to analyze photos and extract text using multimodal vision models (mmproj).
* Built-in Model Store & Custom GGUF — Download curated models directly inside the app or import external .gguf files from local storage.

### Knowledge, Skills & Productivity
* Document Q&A (Local RAG) — Attach PDF, Word, EPUB, HTML, Markdown, or plain-text files. On-device semantic search powered by EmbeddingGemma retrieves relevant context automatically.
* AI Skills & Personas — Create and reuse custom system prompts, roles, and output formats across any model.
* Agent Tools — Optional web search, page fetching, and local JavaScript execution mid-conversation (disabled by default and configurable per model).
* Offline Voice Dictation — Speak directly into the microphone using fast, on-device speech recognition.

### Chat Experience & System
* Rich Markdown & Code Blocks — Clean formatting for headers, lists, tables, and syntax-highlighted code.
* Edit, Regenerate & Branch — Long-press messages to edit prompts, regenerate replies, or branch into a new chat session.
* Reliable Background Downloads — Built on OkHttp and WorkManager with real-time download speed and ETA notifications.
* Flexible Storage Management — Store multi-GB models on internal storage or external SD cards via Android's Storage Access Framework (SAF).

---

## Supported Models

ChatLLM includes a curated catalog optimized for mobile devices alongside full support for custom .gguf files:
* Google Gemma 4 (Gemma 4 12B, Gemma 4 E2B, Gemma 4 E4B Vision)
* Liquid AI LFM 2.5 (LFM2.5 1.2B, LFM2.5 2.6B Reasoning, LFM2.5 VL 3B Vision & OCR)
* XHToken Spark-X2.5 (Spark-X2.5 4B Hybrid Reasoning)
* DeepSeek, Qwen, Llama, Mistral, Phi & more

---

## Tech Stack

| Component | Technology |
| :--- | :--- |
| Distribution | Samsung Galaxy Store Exclusive (com.chanwit.chatllm) |
| UI Framework | Kotlin & Jetpack Compose (Material 3) |
| Inference Engine | llama.cpp (Native C++ / JNI / AIDL Service) |
| Local Embeddings | EmbeddingGemma (On-Device Vector RAG) |
| Background Tasks | Android WorkManager & OkHttp |
| Multi-language | 30+ Languages Supported (including Thai & English) |

---

## Build Instructions

### Prerequisites
* Android Studio (Ladybug or newer)
* JDK 17
* Android NDK & CMake

### Build Steps

1. Clone the repository with submodules:
   ```bash
   git clone --recurse-submodules [https://github.com/Chanwit-1998/AI-Offline-LLM.git](https://github.com/Chanwit-1998/AI-Offline-LLM.git)
   cd AI-Offline-LLM

 * Build Release APK for Samsung Galaxy Store:
   cd android
./gradlew assembleRelease -x lintVitalRelease

License
This project is licensed under the MIT License — see the LICENSE file for details.
Copyright (c) 2026 Chanwit

