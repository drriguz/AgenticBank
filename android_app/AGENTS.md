# AgenticBank — Agent Instructions

## Build & Verify

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleDebug --rerun  # Bypass config cache if stuck
```

## Project Overview

- Single-module Android app (`app/`), **minSdk 34** (Android 14), **compileSdk 37**
- Kotlin 2.2.10, Jetpack Compose + Material 3 (BOM `2026.02.01`)
- Dependencies managed via **Gradle version catalog** (`gradle/libs.versions.toml`)
- Gradle 9.6.1 with **configuration cache enabled** (`gradle.properties`)

## Architecture

- **Navigation**: Two-state enum (`Screen.Splash → Screen.ATM`) in `MainActivity.kt` — no Navigation component
- **Model layer**: `ModelManager` (AndroidViewModel) owns the LiteRT-LM `Engine` lifecycle
- **Screens**: `SplashScreen` (model loading + file picker fallback), `ChatScreen` (streaming chat with tool calling, voice, image input)
- **Tool calling**: Manual (automaticToolCalling=false). LLM emits JSON → `ToolExecutor` parses → calls backend REST API → feeds results back. Financial ops require user confirmation via AlertDialog.

## UI Convention

- **Always use English** for all visible strings, button labels, system prompts, and error messages
- No emoji-only icon libraries; use Unicode emoji characters in `Text()` composables

## LiteRT-LM (On-Device LLM)

- Dependency: `com.google.ai.edge.litertlm:litertlm-android:0.14.0`
- Model file: `gemma-4-E2B-it.litertlm` (~5 GB)
- On first launch, the app copies the model from external storage to internal `filesDir`
- Source locations searched (in order): app's external files dir, `/sdcard/Download/`, MediaStore
- **Fallback**: If auto-detect fails, the error screen offers a file picker (`ActivityResultContracts.OpenDocument`) to select the model manually
- **Push via ADB** (no permissions needed):
  ```
  adb push gemma-4-E2B-it.litertlm /sdcard/Android/data/com.riguz.agenticbank/files/
  ```
- `engine.initialize()` takes ~10 seconds — must run on a background thread/coroutine

## Hardcoded Backend Config

The backend URL and card number are hardcoded in two files — update both when changing environments:
- `agent/Tools.kt` — `BASE_URL` and `CARD_NUMBER`
- `agent/ToolExecutor.kt` — `API_BASE` and `DEFAULT_CARD_NUMBER`

Default: `http://192.168.31.66:8080`, card `4242424242424242` (John Smith's Visa).

## Gotchas

- API 34+ has no `READ_EXTERNAL_STORAGE` effect; use app-specific external files dir via ADB or MediaStore
- `compileSdk` must be ≥ what `androidx.core` requires (currently 37, matching `core-ktx:1.19.0`)
- Composable delegate properties can't be smart-cast; capture with `when (val x = delegated) { is Foo -> x.field }`
