# Feature Specification: Library - Series Tab

This document defines the requirements, behavioral rules, and caching patterns for the **Series Tab** within the library browsing screen. It is a sub-specification of the main [Library Specification](library_spec.md).

---

## 1. Context & Dependencies

The Series Tab lists the audiobooks grouped into their respective series collections. It allows the user to browse series-level metadata, view completion metrics, and drill down to series-specific book lists.

It depends on:
- **`:core:database`**: Local Room database caching using `series` and `series_books` association tables.
- **`:domain`**: Use cases `GetSeriesUseCase`, `SyncLibrarySeriesUseCase`, and `GetSeriesDetailsUseCase`.
- **`:feature:library`**: The `SeriesTabContent`, `SeriesDetailScreen` presentation layouts, and `BookMenuActionUtil`.

---

## 2. Data Integration & Sync

### A. API Endpoints
- **List Library Series**: `GET /api/libraries/{libraryId}/series?minified=0`
  - *Returns*: An array of series metadata blocks containing series ID, name, description, and the list of books in the series. `minified=0` is required to obtain the books array, which contains ordering information (`seriesSequence`).
- **Fetch Specific Series Items**: `GET /api/series/{seriesId}` (or constructed locally by matching books in the local cache with the corresponding series identifier).

### B. Sync Strategy & Caching
1. **Trigger**: Sync occurs when:
   - The Series Tab is initialized (if cached data is older than the 24-hour sync threshold).
   - A global swipe-to-refresh is initiated on any library tab (triggers global force sync).
2. **ETag Check & Force Sync**: Works identically to book sync using `If-None-Match` header. If `304 Not Modified` is returned, the local cache is retained. A global swipe-to-refresh bypasses the ETag check (omits `If-None-Match`) to force update all series data.
3. **Database Schema**:
   - `SeriesEntity`: `id` (String, PK), `libraryId` (String), `name` (String), `description` (String?), `bookCount` (Int), `etag` (String?).
   - A relationship is maintained between books and series (e.g., `BookEntity` having a nullable `seriesId` and a `seriesSequence` string representing order in the series).
   - **Fallback Sequence Logic**: During sync, if `seriesSequence` is null or empty in the metadata, the sync process must fallback to using the book's index in the returned `books` array (1-indexed) to preserve the correct series ordering.

---

## 3. UI & Interaction Rules

### A. Grid Layout
- Series are displayed in an adaptive card grid.
- **Card Content**:
  - **Cover Collage**: A `1:1` cover artwork card showing either a stacked collage of the first 3 book covers in the series, or the cover of the first book in the series.
  - **Series Name**: Bold title text (max 2 lines).
  - **Metadata Row**: Displaying total book count (e.g. `"7 books"`) and completion progress (e.g. `"3/7 read"`).
  - **Progress Bar**: A progress indicator representing the ratio of read books to total books in the series.

### B. Navigation (Series Detail Screen)
- Tapping a Series Card navigates to the **Series Detail Screen**.
- **Series Detail UI**:
  - Header showing the series name and description text.
  - Scrollable list of books belonging to the series.
  - **Ordering**: Books must be sorted by their series sequence number (e.g. `1`, `2`, `2.5`, `3`) instead of alphabetical order.
  - Tapping any book in the list navigates to the **Book Detail Screen**.
  - **Contextual Action Menu**: Long-pressing any book item in the list or tapping its contextual overflow icon opens the `ItemMoreMenuBottomSheet` offering quick operations (Mark Finished/Unfinished, Discard Progress, Add to Playlist, Delete Download, and Go to Web Client) as defined in [book_detail_spec.md](book_detail_spec.md#e-contextual-action-menu).

### C. Search, Filtering, and Sorting
- **Search**: Scopes queries to the series name. Matches are fetched from database.
- **Filtering**:
  - `ALL`: Show all series.
  - `IN_PROGRESS`: Series where at least 1 book is read/in-progress, but not all books are completed.
  - `COMPLETED`: Series where all books in the series have been read.
- **Sorting**:
  - `NAME_ASC` / `NAME_DESC`: Alphabetical by series name.
  - `BOOKS_COUNT_DESC`: By number of books (largest to smallest).
  - `RECENTLY_UPDATED`: By the last played timestamp of any book within the series.

---

## 4. UI States & Offline Resilience

- **Scrollable Empty State**: If no series are found in the active library, the Series tab renders a scrollable empty state (via `LibraryEmptyState` supporting vertical scroll). This ensures that swipe-to-refresh gestures propagate properly, and renders a manual "Sync Now" button to trigger a database refresh.
