# Feature Specification: Book Details & Operations

This document defines the requirements, behavioral rules, download workflows, and offline resilience for the book detail fetching, progress synchronization, download management, and playback initiation within the `:feature:library` module and its supporting data layers.

---

## 1. Feature Context & Constraints

The book detail screen is responsible for presenting granular metadata, chapter lists, and playback progress for a specific audiobook. It also serves as the management hub for downloading audiobook files for offline playback and launching the media player.

Detail data flows through these layers:
- **`:core:network`**: Fetches book details and server progress from the Audiobookshelf server via Ktor.
- **`:core:database`**: Caches detailed book metadata locally in the `books` table and playback progress in `playback_progress` table (Room).
- **`:data`**: Repository implementations coordinating detail fetches, progress synchronization, and download manager updates.
- **`:domain`**: Use cases (`FetchBookDetailsUseCase`, `GetPlaybackProgressUseCase`) exposing operations to ViewModels.
- **`:feature:library`**: Presentation layer containing `DetailViewModel`, `DetailScreen`, and `BookMenuActionUtil` (contextual menu actions utility).

---

## 2. Book Detail Fetch & Sync

### A. Book Detail Fetch (Per-Book Enrichment)
1. **Trigger**: When the user navigates to a book's detail screen, `DetailViewModel.setBookId()` fires a detail fetch.
2. **Network Endpoint**: `GET /api/items/{bookId}`
3. **ETag-Based Conditional Requests**:
   - Store a per-book ETag value in the `BookEntity` (column: `etag`).
   - On each detail fetch, send an `If-None-Match` header with the stored ETag.
   - If the server responds with `304 Not Modified`, skip the update and use the cached entity.
   - If the server responds with `200 OK`, store the new `ETag` from the response header and proceed with the merge.
4. **Time-Based Refresh**:
   - Store a `lastDetailFetchTimestamp` (epoch milliseconds) in the `BookEntity`.
   - On navigating to the detail screen, if the elapsed time since the last fetch exceeds a threshold (default: **24 hours**), trigger a fresh network fetch even if the user has visited before.
   - If the elapsed time is within the threshold, skip the network call and display the cached entity (unless the user explicitly refreshes).
5. **Force Refresh**:
   - The user can explicitly trigger a refresh (e.g., via swipe to refresh on the detail screen).
   - A force refresh must bypass the `lastDetailFetchTimestamp` 24-hour cache threshold and bypass the ETag-based conditional check (omit the `If-None-Match` header), ensuring a full refresh from the server and updating the local database cache.
6. **Detail Sync Strategy**:
   - Fetch the full book response including `audioFiles` and `chapters` arrays.
   - Merge with existing local entity: update basic metadata (title, author, description, duration) if different, and preserve `coverPath` and per-file `localPath` / `downloadStatus` from previously downloaded files.
   - Compute `duration` from the detail response (or by summing all audio file durations) and store it.
   - Update `etag` and `lastDetailFetchTimestamp`.
   - Insert the updated `BookEntity` to the local database.
7. **Server Progress Fetch**: After storing the book entity, fetch the server-side playback progress via `GET /api/me/progress/{bookId}`.
   - If server progress exists and `currentTime` exceeds the locally stored progress, update the local `PlaybackProgressEntity` to match the server state.
   - If no server progress exists (404), take no action.

---

## 3. Book Detail Screen UI & Features

### A. Detail Content
1. **Displayed Fields**: Cover image, title, author, narrator, description, total duration (formatted), chapter list, and playback progress.
2. **HTML Description Rendering**:
   - The book's description field may contain HTML formatting (e.g., `<b>`, `<i>`, `<p>`, `<br>`, `<ul>`, `<li>` tags).
   - The application must parse and render the supported HTML styling (e.g., bold, italic, paragraphs, lists) to match the layout.
   - Any unsupported or unsafe HTML tags, scripts, or styles must be filtered out, sanitized, or ignored rather than displayed raw to the user.
3. **Chapter List**: Displayed as a **collapsible section** on the detail screen. The section header shows "Chapters ({count})" and can be expanded or collapsed by tapping. Default state is **collapsed**. When expanded, each chapter shows:
   - Title (with fallback to `"Chapter {index + 1}"` if empty).
   - Start time (formatted as `HH:MM:SS`).
   - Duration (formatted).
4. **Progress Display**: If progress exists, show remaining time formatted as a duration string.
5. **Series Association**: If the book is part of a series, display the series name (and sequence if available, e.g., "Series Name #Sequence"). Tapping it navigates to that series detail screen.
6. **Playlists Association**: Display a list of all playlists this book is a part of. Tapping a playlist navigates to its detail screen.
7. **Collections Association**: Display a list of all collections this book is a part of. Tapping a collection navigates to its detail screen.
8. **UI Presentation**: Associated groups (Series, Playlists, Collections) should be displayed as interactive, tappable chips or links that provide clear navigation visual feedback.

### B. Download Management
1. **Download Action**: Initiates download of all audio files not yet marked `COMPLETED` using the Android `DownloadManager`.
2. **Download URL Format**: `{serverUrl}/api/items/{bookId}/file/{ino}/download` with `Bearer {token}` authorization header.
3. **Download Progress**: A reactive flow polls the `DownloadManager` every second and reports aggregate progress across all files as a `0f` to `1f` float.
4. **Delete Downloaded Files**: Cancels any active downloads for the book, recursively deletes the local file directory (`downloads/{bookId}`), and resets all `audioFiles` entries to `NOT_DOWNLOADED` status.

### C. Playback Initiation
1. Tapping the play action calls `PlayerManager.playBook(book, startPosition)` where `startPosition` is derived from saved progress (`currentTime`) or `0.0` for new books.

### D. Swipe to Refresh
1. **Behavior**: The detail screen must support swipe-to-refresh gesture.
2. **Force Refresh Integration**: Triggering swipe-to-refresh initiates a force refresh of the book detail network request, bypassing the ETag check and time-based threshold, forcing the database cache to be updated with fresh metadata from the server (as defined in §2.A.5).

#### E. Contextual Action Menu
1. **Trigger**: Tapping the "More Options" (`Icons.Default.MoreVert`) icon button in the top app bar of the detail screen.
2. **Behavior**: Delegated to `BookMenuActionUtil`. Offers quick actions on the selected book item:
   - **Mark as Finished / Mark as Unfinished**: Toggles the audiobook's completed status. If marking as finished while progress is under 100%, displays a confirmation dialog. Updates the progress to the server using `PATCH /api/me/progress/{bookId}` (or local DB if offline).
   - **Discard Progress**: Clears all playback progress. Displays a confirmation dialog: "Are you sure you want to discard progress?". On confirmation, removes progress from server (`DELETE /api/me/progress/{progressId}`) and local database, and updates the UI. Hidden if the book is finished (i.e., when "Mark Unfinished" is showing).
   - **Add to Playlist**: Launches the self-contained `AddToPlaylistDialog` (passing `bookId` and `libraryId`). The dialog handles its own playlist list state, syncing, creation, and item insertion logic internally, independent of the host screen's ViewModel.
   - **Delete Download**: Recursively deletes the downloaded audiobook files from local storage (`downloads/{bookId}`) and resets the download status, keeping the metadata cache intact. Disabled/hidden if the book is not downloaded.
   - **Go to Web Client**: Launches a browser/Custom Tab to `{serverUrl}/item/{bookId}` using the active credentials.

---

## 4. Offline Resilience

1. **Cached Book Details**: The `books` table and related playback progress tables in Room serve as the offline source of truth. When the network is unavailable, the locally cached details are displayed.
2. **Offline Fallback**: If the detail network request fails, attempt to load the book from the local Room cache. If a cached entity exists, return it. If no cached entity exists, propagate the error to the UI.
3. **Graceful Degradation**: Failed network calls surface user-facing error messages but never clear or corrupt the existing local cache.
