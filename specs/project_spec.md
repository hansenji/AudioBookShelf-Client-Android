# Skald - Project Specification

This document serves as the high-level source of truth for the Skald project's purpose, design principles, supported form factors, global architectural constraints, and success measurements.

---

## 1. Application Purpose & User Context

The Skald is an Android client for **Audiobookshelf**, a self-hosted audiobook and podcast server. 

### User Context
Users self-host their own media collections and expect a premium mobile listening experience. Typical usage patterns:
- Listening during commutes, exercise, or in transit where cellular network availability is intermittent.
- Switching devices (e.g., phone to desktop web player) and expecting their playback position to resume exactly where they left off.
- Pre-downloading audiobooks to local device storage to listen offline (e.g., on flights or in remote areas).

---

## 2. Core App Goals & Value Proposition

- **Seamless Synchronization**: Media playback progress must stay in sync with the Audiobookshelf server automatically and transparently.
- **Offline-First Resilience**: Cached library metadata, database records, and downloaded audio tracks must allow uninterrupted playback and offline operation.
- **Host Agility & Robust Login**: Support arbitrary self-hosted servers (local IPs, custom domains/ports, reverse proxies) with automatic formatting of host inputs.
- **Background Playback Stability**: Maintain reliable, long-running audio playback sessions when the screen is off or the app is minimized.
- **Multi-Device Adaptability**: Scale, format, and optimize the UI/UX cleanly across the following supported device types:
  - **Phone**: Responsive touch layout, portrait lock option, notification controls.
  - **Tablet**: Expanded multi-pane layout, optimal screen real estate utilization.
  - **Desktop (Chromebook/Googlebook)**: Android app compatibility, window resizing, keyboard navigation support.
  - **Android Auto**: Driver-safe UI, large tap targets, standard media player templates.

---

## 3. Global Architectural Constraints

To ensure consistency, maintainability, and compatibility, the codebase must adhere strictly to these architectural boundaries:
- **Architecture Style**: Clean Architecture with strict separation of concerns (Presentation, Domain, and Data layers).
- **Design Patterns**: Repository pattern isolating data access operations from the business domain.
- **Code Organization**: Multi-Module Gradle Architecture. The project is split into independent Gradle modules representing feature boundaries and architectural layers:
  - **App Module (`:app`)**: App entry point, Main Activity, Compose Navigation routing, global styling/theme, and Koin dependency injection configuration.
  - **Feature Modules**: Self-contained user experience flows containing Presentation layer components (UI views, ViewModels, and Compose layouts):
    - `:feature:login`: Login flows, server connection configuration, and credentials processing.
    - `:feature:library`: Library browsing, search grid, and book detail screens.
    - `:feature:player`: Audiobook player controls, sleep timer, speed controller, and chapters bottom sheet.
    - `:feature:androidauto`: Android Auto media integration, browsing content tree, and media session delegation.
  - **Data Module (`:data`)**: Repository implementations coordinating network, local database, and preferences data access.
  - **Domain Module (`:domain`)**: Use Cases and repository interfaces (pure Kotlin, free of Android platform dependencies).
  - **Core Utility & Infrastructure Modules**: Core cross-cutting services and platform interfaces:
    - `:core:model`: Shared Kotlin domain entities (e.g. `Book`, `Library`) and pure-Kotlin formatting helpers.
    - `:core:preferences`: Storage configuration and token persistence (`PreferencesManager`).
    - `:core:database`: Room local cache configuration, tables, and entities DTO (`AppDatabase`, `LocalEntities`).
    - `:core:network`: Ktor API client calling and response deserialization DTOs (`AudiobookshelfRemoteDataSource`).
    - `:core:player`: Media3 ExoPlayer orchestration, `AudiobookPlayerService` background service, and `PlayerManager`.
- **Language**: Developed using Kotlin, with experimental features enabled: explicit backing properties and guarded when clauses.
- UI Framework: Built with Jetpack Compose using Material Design 3 guidelines. To maintain strict separation of concerns, repositories, entities, DTOs, network objects, and data sources must not be referenced or used directly within UI code composables. Composable screens and components should only consume UI-specific state models and communicate user actions via event callbacks or ViewModels.
- **Shared Overlay Components (Decoupled Dialogs)**: Common overlay dialogues, sheet pickers, and multi-screen actions (such as "Add to Playlist") must be implemented as self-contained, reusable Compose components powered by their own dedicated ViewModels (or shared state holders). Host screen ViewModels must not fetch, cache, or process data for these auxiliary flows; they should only trigger the overlay by passing the target identifiers (e.g., `bookId` and `libraryId`).
- **State Management & Coroutines**: Use standard, consistent parameters for converting reactive streams (`Flow`) to stateful streams (`StateFlow`). For ViewModels exposing UI state, prefer using `stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5_000), initialValue = ...)` to prevent resource waste during background transitions or screen rotation.
- **Navigation**: Controlled globally using the **Androidx Navigation3** library (`androidx.navigation3.runtime` and `androidx.navigation3.ui`). Feature routing configuration follows the `api`/`impl` pattern of the `nowinandroid` project:
  - **Feature API**: Exposes navigation keys, destination routes, arguments, and public action interfaces.
  - **Feature Implementation**: Encapsulates internal screen composables, ViewModels, and UI fragments. Client modules interact with features exclusively through their public API, minimizing inter-module coupling.
- **Networking**: All remote API communications are handled using Ktor Client with `kotlinx.serialization`.
- **Dependency Injection**: Application services and ViewModels are wired and managed via Koin using the Koin Compiler Plugin DSL NO annotations.
- **Local Caching & Persistence**: Cached media files, libraries, progress, and download queues are persisted locally in a SQLite database via Room.
- **Media Playback**: Audio playback and OS media controls integration must utilize Android Media3 (ExoPlayer and MediaSession).
- **Image Loading**: Image loading, dynamic cover rendering, and caching are managed using the **Coil** library (`io.coil-kt:coil-compose`).
- **Localization**: Always use localizable strings (Android resource strings) for displayable user-facing content that does not come directly from the server. Hardcoded string literals in UI layouts, Compose components, and platform integration logic are prohibited.

---

## 4. Main Navigation

The app uses an adaptive top-level navigation pattern that adjusts to the device's screen size:

- **Phone (Compact width)**: A **Material 3 Bottom Navigation Bar** (`NavigationBar`) is displayed at the bottom of the screen.
- **Larger screens (Medium and Expanded width)**: A **Material 3 Navigation Rail** (`NavigationRail`) is displayed along the leading edge of the screen.

Use the Material 3 `NavigationSuiteScaffold` (from `androidx.compose.material3.adaptive.navigationsuite`) to handle the adaptive switching automatically based on window size class.

### Navigation Destinations

The following top-level destinations are presented in order:

| Destination  | Icon (Outlined / Filled)                          | Label       |
|--------------|----------------------------------------------------|-------------|
| **Home**     | `Icons.Outlined.Home` / `Icons.Filled.Home`        | Home        |
| **Library**  | `Icons.Outlined.LocalLibrary` / `Icons.Filled.LocalLibrary` | Library     |
| **Settings** | `Icons.Outlined.Settings` / `Icons.Filled.Settings`| Settings    |

- The **outlined** icon variant is shown when the destination is unselected.
- The **filled** icon variant is shown when the destination is selected.
- Each destination label must use a localizable string resource.

### Navigation Behavior
- Selecting a destination navigates to its corresponding feature graph using Androidx Navigation3.
- Re-selecting the currently active destination should pop its back stack to the root of that destination's graph (standard single-top, restore-state behavior).

---

## 5. Success Metrics

The overall quality and performance of the application will be measured against the following targets:
- **Playback Start Delay**: Audio output must start within 1.5 seconds under standard network conditions (>10Mbps) for online streaming. Offline playback must start within 500ms.
- **Sync Fidelity**: 100% of finished book statuses and playback positions must be successfully synced to the server. No listening progress should ever be lost due to application crashes or abrupt closures.
- **Offline Transition Time**: The app must transition from online to offline mode (rendering downloaded and cached items) in under 300ms upon network loss.
- **Battery Draw**: Active background audio playback must not consume more than 5% of device battery per hour on standard modern devices.
- **Data Minimization**: Playback synchronization payloads sent over Ktor must remain lightweight (under 1KB per sync request) to conserve user bandwidth.

---

## 6. API Source of Truth & Local Configuration

Since the official Audiobookshelf server API documentation can be out of date, the **Audiobookshelf mobile app repository** is designated as the primary source of truth for API schemas, endpoints, and expectations:
- **Repository URL**: [audiobookshelf-app](https://github.com/advplyr/audiobookshelf-app)

### Local Configuration
To help developers easily inspect the API source of truth, you can configure a local reference to a clone of the `audiobookshelf-app` repository on your local machine.

1. Clone the repository:
   ```bash
   git clone https://github.com/advplyr/audiobookshelf-app.git
   ```
2. Add the path to your clone in the `local.properties` file at the root of the project (this file is excluded from git):
   ```properties
   # Path to local clone of the audiobookshelf mobile app repository
   audiobookshelf.app.dir=/path/to/audiobookshelf-app
   ```

---

## 7. Related Specifications

To inspect detailed configurations and workflows for specific features, refer to the following sub-specifications:
- **[Authentication Specification](file:///home/hansenji/src/abs-client-app/specs/auth_spec.md)**: Outlines secure token storage (Tink-encrypted DataStore), proactive & reactive token refresh, and login validation policies.
- **[Design Specification](file:///home/hansenji/src/abs-client-app/specs/design_spec.md)**: Outlines the premium dark theme, typography system, custom icons, blurred backgrounds, component designs, and adaptive layout mappings.
- **[Testing Specification](file:///home/hansenji/src/abs-client-app/specs/testing_spec.md)**: Outlines the testing tiers (JVM, Robolectric, and Instrumented), Robolectric setups, and target code coverage rates per module.


