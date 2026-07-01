# Skald - Testing Specification

This document defines the testing strategy, frameworks, and code coverage targets for the Skald project. 

By leveraging **Robolectric**, we enable testing of Android framework-dependent components (such as Room Databases, SharedPreferences, Encrypted Keystores, and Compose UI screens) directly on the local JVM. This drastically reduces reliance on slow and flaky physical/virtual device execution, making high-coverage targets achievable and maintainable.

---

## 1. Testing Architecture & Tiers

Skald employs a three-tiered testing structure to balance speed, fidelity, and cost:

```mermaid
graph TD
    subgraph Local JVM (Fast, Runs in CI)
        PureJVM["Pure JVM Unit Tests<br>(Runs < 1s)<br>- Domain logic<br>- Use cases<br>- Network DTOs"]
        RobolectricTests["Robolectric JVM Tests<br>(Runs in 2-5s)<br>- Databases (Room)<br>- SharedPreferences & Cryptography<br>- Compose UI Interaction<br>- Media Session / Service Callbacks"]
    end
    subgraph Device / Emulator (High Fidelity, Slow)
        InstrumentedTests["On-Device Instrumented Tests<br>(Runs in minutes)<br>- Full E2E flows<br>- Device integration (e.g. system controls)<br>- Performance benchmarks"]
    end
```

### A. Pure JVM Unit Tests (`src/test`)
* **Scope**: Business logic, domain models, network data parsing, and helper classes.
* **Constraints**: No Android framework dependencies. If reference to a simple Android class is required (e.g. `Uri`), we use MockK.
* **Execution**: Local JVM, runs in less than a second.

### B. Robolectric JVM Tests (`src/test`)
* **Scope**: Components that depend directly on Android framework APIs but do not need screen rendering or real hardware (e.g. databases, file storage, encrypted keystores, preferences, custom broadcasts, lifecycle states, and Compose layout semantics).
* **Constraints**: Configured with `@RunWith(RobolectricTestRunner::class)`. Runs on the JVM.
* **Execution**: Local JVM, runs in seconds.

### C. Instrumented Tests (`src/androidTest`)
* **Scope**: Performance benchmarks (e.g. DB query times under load, frame rate drops), real file system I/O speed, and deep integration with system services (e.g. Audio focus changes with real hardware).
* **Constraints**: Runs on a physical Android device or emulator.
* **Execution**: Slower (requires installation, device setup). Kept to a minimum to keep the CI loop fast.

---

## 2. Realistic Code Coverage Targets (with Robolectric)

Historically, Android codebases struggle to pass 50% code coverage without maintaining a complex fleet of emulators for UI and DB testing. With **Robolectric** simulating the Android OS on the JVM, we establish the following target coverage standards:

| Module | Old Target (Pure JVM Only) | Realistic Target (With Robolectric) | Key Robolectric Enablements |
| :--- | :---: | :---: | :--- |
| **`:domain`** | 90% | **95%** | Standard Kotlin business logic and use cases. |
| **`:core:preferences`** | 0% | **90%** | Test Tink encryption, `AndroidKeysetManager`, and custom SharedPreferences interactions. |
| **`:core:database`** | 10% | **85%** | Test Room DAOs, queries, entity converters, and migrations with an in-memory SQLite backend. |
| **`:feature:*:impl`** (Screens/VMs) | 40% | **80%** | Test Jetpack Compose layouts, clicks, text displays, and state transitions on JVM. |
| **`:feature:androidauto`** | 20% | **85%** | Test MediaLibrarySession callbacks and Auto browse tree structures without mocking the framework. |
| **`:core:network`** | 75% | **85%** | Test client interactions using Ktor's `MockEngine` along with local Android Uri configurations. |
| **`:data`** | 50% | **85%** | Test repositories integrating Room DBs and local files. |
| **`:core:player`** | 10% | **60%** | Test playback states and session callbacks. Real hardware limits this module's testability. |
| **`:app`** | 10% | **70%** | Test global Compose navigation, theme configuration, and Koin configurations. |

> [!NOTE]
> Code coverage metrics are computed automatically using Kotlinx Kover and enforced in the CI/CD pull request gate.

---

## 3. Global Robolectric & Kover Setup

To keep build configurations clean and uniform, both **Robolectric** and **Kotlinx Kover** are configured globally in the root [build.gradle.kts](file:///home/hansenji/src/abs-client-app/build.gradle.kts) file.

### What is Configured Globally:
1. **Kotlinx Kover Plugin**: Automatically applied to all subprojects (`apply(plugin = "org.jetbrains.kotlinx.kover")`) to track coverage.
2. **Robolectric Configuration**: Configured globally for both library and application modules to include Android resources and return default values for unmocked framework methods:
   ```kotlin
   testOptions {
       unitTests {
           isIncludeAndroidResources = true
           isReturnDefaultValues = true
       }
   }
   ```
3. **Common Test Dependencies**: Injecting standard testing dependencies globally to keep module `build.gradle.kts` files simple:
   * `libs.junit`
   * `libs.robolectric`
   * `libs.mockk`
   * `libs.kotlinx.coroutines.test`

*Note: For modules requiring Compose UI testing under Robolectric, developers only need to specify `testImplementation(libs.androidx.compose.ui.test.junit4)` in the module's dependencies block.*


### Graphics Configuration for Jetpack Compose UI
To run Jetpack Compose UI tests under Robolectric, configure the Robolectric graphics mode to `NATIVE` (which uses Skia to calculate layout bounds and semantic tags). Place this in `robolectric.properties` in your module's test resources, or annotate the test classes:

```properties
# src/test/resources/robolectric.properties
robolectric.graphicsMode=NATIVE
```

---

## 4. Design Patterns & Code Examples

### A. Testing SharedPreferences and Tink Keystores (`:core:preferences`)
You can verify [PreferencesManager.kt](file:///home/hansenji/src/abs-client-app/core/preferences/src/main/kotlin/dev/vikingsen/skald/core/preferences/PreferencesManager.kt) on the JVM using Robolectric's context.

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PreferencesManagerTest {

    private lateinit var context: Context
    private lateinit var preferencesManager: PreferencesManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferencesManager = PreferencesManager(context)
    }

    @Test
    fun saveConnectionDetails_persistsCorrectly() = runBlocking {
        preferencesManager.saveConnectionDetails(
            url = "https://my-audiobooks.com/",
            user = "viking",
            token = "fake-jwt-token"
        )

        // Verifies the URL gets normalized automatically
        assertEquals("https://my-audiobooks.com", preferencesManager.getServerUrl())
        assertEquals("viking", preferencesManager.getUsername())
        assertEquals("fake-jwt-token", preferencesManager.getToken())
        assertTrue(preferencesManager.isLoggedIn())
    }
}
```

### B. Testing Room DAOs and Databases (`:core:database`)
Robolectric supports SQLite databases natively on the local JVM.

```kotlin
@RunWith(RobolectricTestRunner::class)
class BookDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var bookDao: BookDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        bookDao = db.bookDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndReadBook() = runBlocking {
        val book = LibraryEntity(id = "book-1", title = "The Hobbit", author = "J.R.R. Tolkien")
        bookDao.insertAll(listOf(book))
        
        val retrieved = bookDao.getBookById("book-1")
        assertEquals("The Hobbit", retrieved?.title)
    }
}
```

### C. Testing Compose Screens (`:feature:*:impl`)
Verify Compose screen bindings on the JVM without booting an emulator.

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(instrumentedPackages = ["androidx.loader.content"])
class LoginScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun loginScreen_showsServerUrlInputAndConnectButton() {
        var connectClicked = false

        composeTestRule.setContent {
            LoginScreen(
                uiState = LoginUiState(),
                onConnectClick = { _, _, _ -> connectClicked = true }
            )
        }

        // Verify elements are visible
        composeTestRule.onNodeWithText("Connect to your server").assertExists()
        composeTestRule.onNodeWithText("Server URL").assertExists()
        
        // Simulate text input and click
        composeTestRule.onNodeWithTag("server_url_input").performTextInput("https://demo.server")
        composeTestRule.onNodeWithText("Connect").performClick()
        
        assertTrue(connectClicked)
    }
}
```

---

## 5. Running and Generating Coverage Reports

To run the complete test suite and generate coverage reports locally:

```bash
# Run all local JVM tests (including Robolectric tests)
./gradlew test

# Generate JaCoCo coverage reports
./gradlew jacocoTestReport
```
Report outputs are saved to the build directory: `<module-name>/build/reports/jacoco/jacocoTestReport/html/index.html`.
