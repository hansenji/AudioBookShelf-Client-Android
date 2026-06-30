# Skald - App Module Specification

This specification details the specifics of the `:app` module, which acts as the main orchestrator, application entry point, styling theme provider, and navigation host.

---

## 1. Application & Activity Entry Point

- **`ABSApplication`**: Inherits from `Application`. It is responsible for initializing the root Dependency Injection container via Koin:
  - Invokes `startKoin` in `onCreate`.
  - Configures standard Android loggers and links context.
  - Loads `appModule` which merges all sub-module DI definitions.
- **`MainActivity`**: Inherits from `ComponentActivity`. It serves as the single window frame:
  - Configured with `android:launchMode="singleTop"` to reuse the running instance when launched from external entry points (like notifications).
  - Enables edge-to-edge rendering with custom transparent system status bar and navigation bar styles.
  - Hosts the main Compose UI hierarchy wrapped in the root application theme (`SkaldTheme`) and a base Surface container.
  - Overrides `onNewIntent` to call `setIntent(intent)` to propagate incoming intents to the Compose layer.
  - Calls `MainNavigation()` to launch the navigation entry point.

---

## 2. Root Dependency Injection Orchestration

The app module coordinates and merges DI modules across the entire project structure in `AppModule.kt`:
- Uses Koin's `includes()` operator to load sub-project modules:
  - `corePreferencesModule` (token and preferences settings).
  - `coreDatabaseModule` (Room database caching).
  - `coreNetworkModule` (Ktor endpoints).
  - `corePlayerModule` (Media3 service & playback controllers).
  - `domainModule` (Use Cases).
  - `dataModule` (Repository implementations).
  - `featureLoginModule`, `featureLibraryModule`, and `featurePlayerModule` (ViewModels).

---

## 3. Global Navigation Graph & Routing

The `:app` module is responsible for defining page destinations and handling transition routes:
- **Routes & Keys** (`NavigationKeys.kt`):
  - `Login`: Navigate to credentials login screen.
  - `Library`: Navigate to library book lists and cover grids.
  - `Detail(val bookId: String)`: Navigate to detailed metadata page for a specific audiobook.
  - `Player`: Navigate to active playback page.
- **Navigation Graph Controller** (`Navigation.kt`):
  - Uses `rememberNavBackStack` and `NavDisplay` from Androidx Navigation3.
  - Reads `PreferencesManager.isLoggedIn()` to dynamically resolve the starting destination (starts on `Library` if logged in, otherwise `Login`).
  - Observes `onLoginSuccess` to route to the main `Library` page.
  - Coordinates screen transition callbacks (e.g. clicking a book to navigate to `Detail`, clicking logout to return to `Login`).
  - Injects `PlayerManager` to trigger media playback initialization when routing from book details to the player interface.
  - Observes the activity's intent during the `ON_RESUME` lifecycle event; if the intent action is `dev.vikingsen.skald.ACTION_PLAYER`, it routes to the `Player` destination and clears the action.

---

## 4. Global Styling & Theme System

App-wide design elements and branding are declared in the `theme/` package:
- **`Color.kt`**: Tailored dark-mode primary, secondary, and tertiary theme color maps (e.g., sleek HSL gradients, high-contrast text shades).
- **`Type.kt`**: Material 3 typography style guidelines mapped to system fonts.
- **`Theme.kt`**: Main `SkaldTheme` composable wrapper providing dynamic or fixed dark styling schemes matching application layouts.
