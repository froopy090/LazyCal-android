# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

LazyCal is a single-module (`:app`) Android calorie tracker. The user describes food in natural language (or snaps a photo) and an on-device Gemma model (LiteRT-LM) turns it into structured nutrition entries. Everything is local: there is no backend. Network is only used to download the model once.

Stack: Kotlin, Jetpack Compose (Material 3), Room (via kapt), LiteRT-LM, MVVM. JDK 21 toolchain, minSdk 24, compile/targetSdk 36. Dependency versions live in `gradle/libs.versions.toml`.

## Commands

```bash
./gradlew assembleDebug                 # build debug APK
./gradlew installDebug                  # build + install on connected device/emulator
./gradlew assembleRelease               # minified + resource-shrunk release build (R8)
./gradlew testDebugUnitTest             # JVM unit tests
./gradlew testDebugUnitTest --tests "com.francescocanossi.lazycal.ChatViewModelSafetyTest"   # single test class
./gradlew connectedDebugAndroidTest     # instrumented tests (needs device)
./gradlew lint
```

To check R8/ProGuard problems in a debuggable build, uncomment the `debug { isMinifyEnabled = true ... }` block in `app/build.gradle.kts`. LiteRT JNI classes must stay kept in `app/proguard-rules.pro`, or release builds break at runtime.

## Check docs before writing code

Before writing or changing code that uses an Android, Jetpack (Compose, Room, Lifecycle), Kotlin or LiteRT-LM API, look the API up in the docs MCPs. Don't rely on memory. This project uses very recent versions (AGP 9, Kotlin 2.3, Compose BOM 2026.x, LiteRT-LM 0.11), so APIs you remember may be renamed, deprecated or missing.

- Use the `google-developer-knowledge` MCP (`search_documents`, `get_documents`, `answer_query`) or the `retrieving-developer-knowledge` skill for Android/Jetpack/Kotlin/LiteRT docs.
- Compare the docs against the versions pinned in `gradle/libs.versions.toml`.

## Architecture

All source is in `app/src/main/java/com/francescocanossi/lazycal/`. Screens are in `screens/` and theme is in `ui/theme/`.

- **`MainActivity`** has no Navigation component. It gets three ViewModels with `viewModel()` and switches UI with plain state: `showSettings`, `detailEntry` (FoodDetailScreen overlay), and a `HorizontalPager` over the `TabItem` enum (Tracker/Saved/Progress/History). Which pager content appears depends on `ChatViewModel.uiState` (`ChatState`: CheckingModel → ModelMissing/Downloading → Initializing → Ready/Error). Theme (`auto`/`light`/`dark`) comes from `UserConfig.themeMode`.
- **`ChatViewModel`** is the central ViewModel. It owns:
  - **The LLM engine.** `ensureEngineInitialized()` builds the LiteRT `Engine` + `Conversation` under a `Mutex`, using a CPU or GPU backend depending on `UserConfig.useGpu`. Engine and conversation are cached in the **companion object** for the whole process lifetime, so they are not closed in `onCleared()`. `resetEngine()` / `toggleGpu()` clear that cache.
  - **The system prompt** (`systemInstruction`). It tells the model to return a JSON array of `{food_item, amount, calories, protein, carbs, fats}`, or `{"error":"invalid"}`. `parseAndSave()` cuts the response down to the outermost `[...]`/`{...}` and accepts either an array or a single object. If you change the prompt's output format, update the parser too.
  - **`isPromptSafe()`**, a keyword blocklist that runs before text is sent to the model.
  - **Day selection.** `selectedDay` holds a `yyyy-MM-dd` dayId. Any day other than today is read-only (`isReadOnly`).
  - Model download state (via `ModelManager` + a `DownloadManager` broadcast receiver), plus connectivity tracking.
- **`ProgressViewModel`** computes chart and streak data from day summaries. **`SupportViewModel`** handles launch counting, the donation prompt, and some `UserConfig` writes.
- **`ModelManager`** downloads `gemma-4-E2B-it.litertlm` from Hugging Face into `getExternalFilesDir` with the system `DownloadManager`. A file under 2 GB counts as missing/truncated.
- **`ChatDatabase.kt`** holds all Room entities, DAOs and the DB singleton in one file:
  - `FoodEntry`: entries are grouped by the string `dayId` (`YYYY-MM-DD`), not by timestamp.
  - `UserConfig`: one row, `id = 0`.
  - `SavedFood`: the "Saved"/catalogue items.
  - `fallbackToDestructiveMigration()` is on and `exportSchema = false`, so **bumping the DB `version` wipes user data**. Add a real `Migration` whenever you change a schema that users already have.
- **`CsvManager`** handles export/import with the fixed column order `Date,Food Item,Amount,Calories,Protein(g),Carbs(g),Fats(g),Original Input`. Import **deletes all existing entries** before inserting.

## Notes

- `ChatViewModelSafetyTest` copies the keyword list instead of calling `isPromptSafe` (constructing the `AndroidViewModel` needs an `Application`). If you edit the blocklist in `ChatViewModel`, update the test's copy as well.
- Branching follows git-flow: development happens on `dev`, and PRs target `dev`. `master` receives verified merges from `dev`.
- Bump `versionCode`/`versionName` in `app/build.gradle.kts` for releases. APKs are distributed through GitHub Releases.
