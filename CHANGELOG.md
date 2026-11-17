# AI Offline LLM — CHANGELOG

Fork of [LMPlayground](https://github.com/andriydruk/LMPlayground) (andriydruk/LMPlayground),
modified to produce a signed, installable Android APK named **AI Offline LLM**.

All changes below were applied inside the `android/` Gradle subproject (and the shared
`core/` native sources, which are pulled in via the `core/llama.cpp` git submodule).

---

## 1. App name → "AI Offline LLM"

- `android/app/src/main/res/values/strings.xml` and every `res/values-*/strings.xml`
  that defined `app_name` (12 locales total: default, ar, bn, cs, da, fil, fi, iw, ms,
  nb, nl, sv, th) now read:
  ```xml
  <string name="app_name" translatable="false">AI Offline LLM</string>
  ```
  (`translatable="false"` was preserved; only the default + the locales that overrode it
  were changed — other locales fall back to the default.)

## 2. App icon → user-provided rubber-duck image

- Source: user-provided 480×480 JPEG (yellow rubber-duck face on yellow background).
- Converted to PNG, 432×432 px (108 dp @ xxxhdpi), scaled to fit the inner 72 dp safe
  zone with transparent padding, placed at:
  `android/app/src/main/res/drawable-nodpi/ic_launcher_foreground.png`
- Deleted the old vector foreground:
  `android/app/src/main/res/drawable/ic_launcher_foreground.xml`
- Adaptive icon background (`res/values/ic_launcher_background.xml`) set to the dominant
  color sampled from the image: `#FFD527` (solid yellow), so it blends seamlessly with
  the foreground's yellow field.
- `res/mipmap/ic_launcher.xml` (adaptive-icon XML) unchanged — still references
  `@drawable/ic_launcher_foreground` + `@color/ic_launcher_background` +
  `@drawable/ic_launcher_monochrome`. No density-specific mipmap PNGs exist to override.
- `res/drawable/ic_launcher_monochrome.xml` (original penrose-triangle vector) kept
  as-is; it is self-contained and references no removed drawables.

## 3. Default Digital Assistant support

The app is now selectable as the **Default assistant app** in Android Settings
(Settings > Apps > Default apps > Digital assistant app).

### 3a. MainActivity ASSIST intent filter
`android/app/src/main/AndroidManifest.xml` — the `MainActivity` `<intent-filter>` now
also contains:
```xml
<action android:name="android.intent.action.ASSIST" />
<category android:name="android.intent.category.DEFAULT" />
```
(alongside the existing `MAIN`/`LAUNCHER` entries, which were kept).

### 3b. Stub VoiceInteractionService
New files (minimal no-op stubs — selection only; invocation does nothing):
- `android/app/src/main/java/com/druk/lmplayground/assistant/AssistantVoiceInteractionService.kt`
  — extends `android.service.voice.VoiceInteractionService`, empty `onReady()`.
- `android/app/src/main/java/com/druk/lmplayground/assistant/AssistantVoiceInteractionSessionService.kt`
  — extends `VoiceInteractionSessionService`, returns a minimal
  `VoiceInteractionSession` subclass.
- `android/app/src/main/res/xml/voice_interaction_service.xml` — declares
  `sessionService=".assistant.AssistantVoiceInteractionSessionService"` and
  `supportsAssist="true"`.

Manifest registration (inside `<application>`):
```xml
<service android:name=".assistant.AssistantVoiceInteractionService"
         android:permission="android.permission.BIND_VOICE_INTERACTION"
         android:exported="true">
    <intent-filter>
        <action android:name="android.service.voice.VoiceInteractionService" />
    </intent-filter>
    <meta-data android:name="android.voice_interaction"
               android:resource="@xml/voice_interaction_service" />
</service>
```

---

## 4. Build-scoping deviations (build only; no runtime behavior change)

Applied to make the native (llama.cpp) build feasible on a 2-core / 4 GB headless
machine. Documented here so they can be reverted for a full CI build.

| Change | File | Original | Modified | Reason |
|---|---|---|---|---|
| ABI filter | `android/app/build.gradle.kts` | `arm64-v8a` + `x86_64` | `arm64-v8a` only | Phone-only APK; halves native compile time. |
| Vulkan backend | `android/app/build.gradle.kts` | `-DGGML_VULKAN=ON` | `-DGGML_VULKAN=OFF` | Machine lacks Vulkan/SPIRV headers + glslc; CPU-only inference works. |
| CPU variants | `android/app/build.gradle.kts` | `-DGGML_CPU_ALL_VARIANTS=ON` | `-DGGML_CPU_ALL_VARIANTS=OFF` | Single CPU variant instead of one `.so` per ARM feature level (NEON/dotprod/i8mm/SVE/…); huge compile-time saving. |
| ABI splits | `android/app/build.gradle.kts` | `splits.abi.isEnable = true` | `isEnable = false` | Produce one universal APK at the standard `app-debug.apk` path (not per-ABI splits). |
| Gradle heap | `android/gradle.properties` | `-Xmx2048m` | `-Xmx1536m` | Machine has 4 GB RAM; avoid OOM-kill of the Gradle daemon. |
| Gradle parallel | `android/gradle.properties` | `org.gradle.parallel=true` | `false` | Avoid concurrent native compile jobs overwhelming 2 cores. |
| Paparazzi plugin | `android/app/build.gradle.kts` | `alias(libs.plugins.paparazzi)` applied | commented out | Paparazzi (Play Store screenshot tests) not needed for `assembleDebug`; alpha plugin is flaky in headless config. Screenshot-organizer tasks left in place (no-op when plugin absent). |

Other CMake args (`-DBUILD_SHARED_LIBS=ON`, `-DGGML_BACKEND_DL=ON`,
`-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON`, etc.) were left unchanged.

## 5. Build environment used

- **JDK**: Eclipse Temurin OpenJDK 21.0.13 (linux-x64) installed to `~/jdk21`
  (`JAVA_HOME=~/jdk21`). Project requires `JavaVersion.VERSION_21`.
- **Android SDK**: installed to `~/android-sdk` (`ANDROID_HOME`/`ANDROID_SDK_ROOT`):
  - `platform-tools`
  - `build-tools;36.0.0`
  - `platforms;android-36` (compileSdk = 36, targetSdk = 36, minSdk = 30)
  - `ndk;27.2.12479018` (pinned in `build.gradle.kts` as `ndkVersionPin`)
  - `cmake;3.31.6` (required by `cmake_minimum_required(VERSION 3.31.6)` and
    `externalNativeBuild.cmake.version`)
- **Git submodule**: `core/llama.cpp` (andriydruk/llama.cpp-android, branch
  `lmplayground-0.7.0`) initialized via `git submodule update --init --recursive`.
- `android/local.properties` created with `sdk.dir=/home/user/android-sdk`.

## 6. Exact rebuild commands

```bash
# Environment (machine with only Java 11 preinstalled; no sudo)
export JAVA_HOME=$HOME/jdk21
export ANDROID_HOME=$HOME/android-sdk
export ANDROID_SDK_ROOT=$HOME/android-sdk
export PATH=$JAVA_HOME/bin:$PATH

# One-time SDK setup (cmdline-tools extracted to $ANDROID_HOME/cmdline-tools/latest)
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager \
    "platform-tools" "build-tools;36.0.0" "platforms;android-36" \
    "ndk;27.2.12479018" "cmake;3.31.6"

# Submodule (from repo root)
git submodule update --init --recursive

# Build (from android/ dir; low-parallel flags for 2-core/4GB machine)
cd android
CMAKE_BUILD_PARALLEL_LEVEL=1 ./gradlew :app:assembleDebug \
    --max-workers=1 --no-daemon

# Output: android/app/build/outputs/apk/debug/app-debug.apk
# (debug build auto-signed with the bundled android/debug.keystore → installable)
```

## 7. Verification

- `aapt dump badging` confirms `application-label:'AI Offline LLM'`, a
  `launchable-activity`, and an icon.
- `apksigner verify --print-certs` passes (v1/v2/v3 signing scheme via bundled
  `debug.keystore`).
- APK contains `arm64-v8a` native libs only (`libllamacpp.so`, `libggml*.so`, etc.).
