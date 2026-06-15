# AppFunctions Specification — Skald

**Last Updated:** 2026-06-12  
**Status:** Implemented (v1)  
**Related specs:** [project_spec.md](project_spec.md), [api_spec.md](api_spec.md)

---

## 1. Overview

This spec describes the implementation of Android AppFunctions for the Skald audiobook player. AppFunctions expose discrete app capabilities to the Android system, allowing AI assistants and automation agents to invoke them without opening the app UI.

Skald's AppFunctions cover four domains:

| Domain | Functions |
|---|---|
| **Playback Control** | `resumeCurrentPlayback`, `pauseCurrentPlayback`, `playAudiobook`, `searchAndPlayAudiobook` |
| **Player Settings** | `setSleepTimer`, `setPlaybackSpeed` |
| **Library** | `playPlaylist`, `downloadAudiobook`, `searchAndDownloadAudiobook` |
| **Progress** | `markBookFinished` |

---

## 2. Build Changes

### 2.1 compileSdk Upgrade

**File:** [`app/build.gradle.kts`](../app/build.gradle.kts)

```diff
 android {
-    compileSdk = 36
+    compileSdk = 37    // required for AppFunctions
```

> `targetSdk` remains at 36; only `compileSdk` needs to be 37.

### 2.2 New Dependencies

**File:** [`gradle/libs.versions.toml`](../gradle/libs.versions.toml)

```toml
[versions]
appFunctions = "1.0.0-alpha09"

[libraries]
androidx-appfunctions = { module = "androidx.appfunctions:appfunctions", version.ref = "appFunctions" }
androidx-appfunctions-service = { module = "androidx.appfunctions:appfunctions-service", version.ref = "appFunctions" }
androidx-appfunctions-compiler = { module = "androidx.appfunctions:appfunctions-compiler", version.ref = "appFunctions" }
```

**File:** [`app/build.gradle.kts`](../app/build.gradle.kts)

```kotlin
plugins {
    alias(libs.plugins.ksp)   // added — required by appfunctions-compiler
}

dependencies {
    implementation(libs.androidx.appfunctions)
    implementation(libs.androidx.appfunctions.service)
    ksp(libs.androidx.appfunctions.compiler)
}

ksp {
    arg("appfunctions:aggregateAppFunctions", "true")
}
```

### 2.3 AndroidManifest Additions

**File:** [`app/src/main/AndroidManifest.xml`](../app/src/main/AndroidManifest.xml)

```xml
<!-- App-level AI agent metadata -->
<property
    android:name="android.app.appfunctions.app_metadata"
    android:resource="@xml/app_metadata" />
```

### 2.4 App Metadata XML

**File:** [`app/src/main/res/xml/app_metadata.xml`](../app/src/main/res/xml/app_metadata.xml)

Provides AI agents with contextual usage guidance (search-before-play workflow,
bookId format, login requirement, etc.).

---

## 3. File Layout

```
app/src/main/
├── kotlin/dev/vikingsen/skald/appfunctions/
│   └── SkaldAppFunctions.kt        # @AppFunction implementations (all 10 functions)
└── res/xml/
    └── app_metadata.xml            # AI agent context description
```

---

## 4. Dependency Injection (Koin)

Skald uses **Koin**, not Hilt. The `ABSApplication` class implements `AppFunctionConfiguration.Provider` and retrieves `SkaldAppFunctions` from Koin. `SkaldAppFunctions` is registered as a singleton in `appModule`.

No new Koin modules are required — all underlying dependencies are already registered in the existing modules, and `SkaldAppFunctions` is resolved via Koin's `get()` constructor injection.

```kotlin
// In AppModule.kt
single {
    SkaldAppFunctions(
        playerManager = get(),
        repository = get(),
        settingsRepository = get(),
        fetchBookDetailsUseCase = get(),
        downloadAudioFileUseCase = get(),
        getPlaylistsUseCase = get()
    )
}

// In ABSApplication.kt
class ABSApplication : Application(), AppFunctionConfiguration.Provider {
    override val appFunctionConfiguration: AppFunctionConfiguration
        get() = AppFunctionConfiguration.Builder()
            .addEnclosingClassFactory(SkaldAppFunctions::class.java) {
                GlobalContext.get().get<SkaldAppFunctions>()
            }
            .build()
}
```

---

## 5. API Conventions

### 5.1 Annotations

- All functions are annotated `@AppFunction(isDescribedByKDoc = true)`.
- KDoc is written inline on each function and parameter — not as class-level `@param` tags.

### 5.2 Parameter Rules

- **First parameter** is always `AppFunctionContext` (required by the framework).
- Additional parameters are **typed primitives** (`String`, `Boolean`, `Int`, `Float`) or
  `@AppFunctionSerializable` data classes — never a generic extras bundle.
- Optional parameters use Kotlin default values.

### 5.3 Threading

- All functions are `suspend` and use `withContext(Dispatchers.IO)` or
  `withContext(Dispatchers.Main)` as appropriate.
- Playback control functions (`play`, `pause`, `seekTo`) require `Dispatchers.Main`.
- Network/repository operations use `Dispatchers.IO`.

### 5.4 Error Handling

All errors throw typed subclasses of `AppFunctionException`:

| Exception type | When to use |
|---|---|
| `AppFunctionInvalidArgumentException` | Bad/missing input parameter |
| `AppFunctionElementNotFoundException` | Book, playlist not found |
| `AppFunctionNotEnabledException` | Not logged in, no book loaded |

---

## 6. Function Specifications

---

### 6.1 `resumeCurrentPlayback`

**Agent prompts:** *"Resume my audiobook"*, *"Continue playing in Skald"*

**Parameters:** none (beyond `AppFunctionContext`)

**Logic:**
1. Assert `playerManager.currentBook.first() != null` → else `AppFunctionNotEnabledException`
2. Call `playerManager.play()` (idempotent if already playing)

---

### 6.2 `pauseCurrentPlayback`

**Agent prompts:** *"Pause Skald"*, *"Stop the audiobook"*

**Parameters:** none

**Logic:**
1. Assert book is loaded → else `AppFunctionNotEnabledException`
2. Call `playerManager.pause()`

---

### 6.3 `playAudiobook`

**Agent prompts:** *"Play Project Hail Mary"*, *"Start Dune from the beginning"*

| Parameter | Type | Default | Description |
|---|---|---|---|
| `bookId` | `String` | required | Audiobookshelf library item UUID |
| `startFromBeginning` | `Boolean` | `false` | Ignore saved progress and start at 0 |

**Logic:**
1. `requireLoggedIn()`
2. Validate `bookId` is not blank
3. `fetchBookDetailsUseCase(bookId)` → `AppFunctionElementNotFoundException` on failure
4. Determine start position (saved progress or 0.0)
5. `playerManager.playBook(book, startPosition)` on Main dispatcher

---

### 6.4 `searchAndPlayAudiobook`

**Agent prompts:** *"Play something by Andy Weir"*, *"Play The Hobbit"*

| Parameter | Type | Default | Description |
|---|---|---|---|
| `query` | `String` | required | Title, author, or narrator search text |

**Logic:**
1. `requireLoggedIn()`, validate `query` not blank
2. Get `libraryId` from settings
3. Filter all books in the library containing `query` in title, author, or narrator
4. Check for matches in currently reading books first (unfinished books with progress)
5. If currently reading matches exist, use them as candidates; otherwise fall back to all matching books
6. Rank candidates: exact title (0) > partial title (1) > author match (2)
7. Fetch full book details, determine start position, play

---

### 6.5 `setSleepTimer`

**Agent prompts:** *"Set a 30-minute sleep timer"*, *"Stop at end of chapter"*, *"Cancel the timer"*

| Parameter | Type | Default | Description |
|---|---|---|---|
| `minutes` | `Int` | `0` | Duration. `0` = cancel. Range 1–480. |
| `endOfChapter` | `Boolean` | `false` | If true, stop at end of current chapter (overrides `minutes`) |

**Logic:**
1. Assert book is loaded
2. `endOfChapter=true` → `setSleepTimerEndOfChapter()`
3. `minutes=0` → `cancelSleepTimer()`
4. `minutes < 0 || minutes > 480` → `AppFunctionInvalidArgumentException`
5. else → `setSleepTimer(minutes)`

---

### 6.6 `setPlaybackSpeed`

**Agent prompts:** *"Set speed to 1.5x"*, *"Speed up my audiobook"*

| Parameter | Type | Default | Description |
|---|---|---|---|
| `speed` | `Float` | required | Multiplier in range `0.5..2.0` |

**Logic:**
1. Validate `speed` in `PlaybackConstants.SPEED_RANGE` → else `AppFunctionInvalidArgumentException`
2. Snap to nearest 0.1-step value from `PlaybackConstants.PLAYBACK_SPEEDS`
3. `playerManager.setPlaybackSpeed(snapped)` (also persists via settings)

---

### 6.7 `playPlaylist`

**Agent prompts:** *"Play my Sci-Fi Classics playlist"*

| Parameter | Type | Default | Description |
|---|---|---|---|
| `playlistId` | `String` | `""` | Exact playlist UUID (preferred) |
| `playlistName` | `String` | `""` | Fuzzy-matched name if ID unavailable |

At least one must be non-blank.

**Logic:**
1. `requireLoggedIn()`, validate at least one param provided
2. `getPlaylistsUseCase().first()` — search by ID then name
3. Assert playlist has items
4. `playerManager.playPlaylist(playlist, startIndex=0, startPosition=0.0)`

---

### 6.8 `downloadAudiobook`

**Agent prompts:** *"Download Project Hail Mary for offline listening"*

| Parameter | Type | Default | Description |
|---|---|---|---|
| `bookId` | `String` | required | Audiobookshelf library item UUID |

**Logic:**
1. `requireLoggedIn()`, validate `bookId`
2. Fetch book details
3. If `book.isDownloaded` → return (idempotent)
4. `downloadAudioFileUseCase(bookId)`

---

### 6.9 `markBookFinished`

**Agent prompts:** *"Mark Dune as finished"*, *"Mark The Martian as unfinished"*

| Parameter | Type | Default | Description |
|---|---|---|---|
| `bookId` | `String` | required | Audiobookshelf library item UUID |
| `isFinished` | `Boolean` | `true` | `true` = finished, `false` = unfinished |

**Logic:**
1. `requireLoggedIn()`, validate `bookId`
2. `repository.updatePlaybackFinished(bookId, isFinished)`

---

### 6.10 `searchAndDownloadAudiobook`

**Agent prompts:** *"Download Dune"*, *"Download something by Andy Weir"*

| Parameter | Type | Default | Description |
|---|---|---|---|
| `query` | `String` | required | Title, author, or narrator search text |

**Logic:**
1. `requireLoggedIn()`, validate `query` not blank
2. Get `libraryId` from settings
3. Filter all books in the library containing `query` in title, author, or narrator
4. Check for matches in currently reading books first (unfinished books with progress)
5. If currently reading matches exist, use them as candidates; otherwise fall back to all matching books
6. Rank candidates: exact title (0) > partial title (1) > author match (2)
7. Fetch book details, check if already downloaded. If so, return early (idempotent)
8. Call `downloadAudioFileUseCase(bookId)`

---

## 7. Security Constraints

| Rule | Detail |
|---|---|
| No credentials in responses | Tokens from `SettingsRepository` are never serialized into AppFunction outputs |
| Login gate on all mutating functions | `requireLoggedIn()` called before any network operation |
| No irreversible destruction | `downloadAudiobook` and `markBookFinished` are reversible; no delete functions exposed |

---

## 8. Testing

### Unit tests (`:app` module)

- [ ] `resumeCurrentPlayback` — no book → `AppFunctionNotEnabledException`
- [ ] `resumeCurrentPlayback` — already playing → idempotent success
- [ ] `pauseCurrentPlayback` — no book → `AppFunctionNotEnabledException`
- [ ] `playAudiobook` — blank `bookId` → `AppFunctionInvalidArgumentException`
- [ ] `playAudiobook` — `startFromBeginning=true` → `startPosition=0.0`
- [ ] `playAudiobook` — saved progress exists → resumes at saved position
- [ ] `searchAndPlayAudiobook` — blank query → `AppFunctionInvalidArgumentException`
- [ ] `searchAndPlayAudiobook` — no match → `AppFunctionElementNotFoundException`
- [ ] `searchAndPlayAudiobook` — exact title preferred over partial match
- [ ] `setSleepTimer` — no book → `AppFunctionNotEnabledException`
- [ ] `setSleepTimer` — `endOfChapter=true` → `setSleepTimerEndOfChapter()` called
- [ ] `setSleepTimer` — `minutes=0` → `cancelSleepTimer()` called
- [ ] `setSleepTimer` — `minutes=600` → `AppFunctionInvalidArgumentException`
- [ ] `setPlaybackSpeed` — `speed=3.0` → `AppFunctionInvalidArgumentException`
- [ ] `setPlaybackSpeed` — `speed=1.4` → snapped to `1.4f` from speed list
- [ ] `playPlaylist` — both params blank → `AppFunctionInvalidArgumentException`
- [ ] `playPlaylist` — empty playlist → `AppFunctionInvalidArgumentException`
- [ ] `downloadAudiobook` — already downloaded → early return, no download call
- [ ] `markBookFinished` — not logged in → `AppFunctionNotEnabledException`
- [ ] `searchAndDownloadAudiobook` — blank query → `AppFunctionInvalidArgumentException`
- [ ] `searchAndDownloadAudiobook` — no match → `AppFunctionElementNotFoundException`
- [ ] `searchAndDownloadAudiobook` — already downloaded → early return, no download call

### ADB integration tests (on-device, API 36+ device or emulator)

```bash
# Verify functions are registered
adb shell cmd appfunctions list-app-functions dev.vikingsen.skald

# Resume playback (requires a book already loaded)
adb shell cmd appfunctions execute dev.vikingsen.skald \
    --function-id dev.vikingsen.skald.appfunctions.SkaldAppFunctions#resumeCurrentPlayback

# Play a specific book
adb shell cmd appfunctions execute dev.vikingsen.skald \
    --function-id dev.vikingsen.skald.appfunctions.SkaldAppFunctions#playAudiobook \
    --param bookId:<your-book-id> \
    --param startFromBeginning:false

# Set sleep timer for 30 minutes
adb shell cmd appfunctions execute dev.vikingsen.skald \
    --function-id dev.vikingsen.skald.appfunctions.SkaldAppFunctions#setSleepTimer \
    --param minutes:30

# Set playback speed to 1.5x
adb shell cmd appfunctions execute dev.vikingsen.skald \
    --function-id dev.vikingsen.skald.appfunctions.SkaldAppFunctions#setPlaybackSpeed \
    --param speed:1.5

# Search and play
adb shell cmd appfunctions execute dev.vikingsen.skald \
    --function-id dev.vikingsen.skald.appfunctions.SkaldAppFunctions#searchAndPlayAudiobook \
    --param query:"Andy Weir"

# Mark book as finished
adb shell cmd appfunctions execute dev.vikingsen.skald \
    --function-id dev.vikingsen.skald.appfunctions.SkaldAppFunctions#markBookFinished \
    --param bookId:<your-book-id> \
    --param isFinished:true

# Search and download
adb shell cmd appfunctions execute dev.vikingsen.skald \
    --function-id dev.vikingsen.skald.appfunctions.SkaldAppFunctions#searchAndDownloadAudiobook \
    --param query:"Andy Weir"
```
