package dev.vikingsen.skald.feature.settings

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import dev.vikingsen.skald.core.model.Library
import dev.vikingsen.skald.domain.fakes.FakeAudiobookshelfRepository
import dev.vikingsen.skald.domain.fakes.FakeSettingsRepository
import dev.vikingsen.skald.domain.usecase.LogoutUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1080dp-h2400dp-xxhdpi")
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var context: Context
    private lateinit var fakeSettingsRepository: FakeSettingsRepository
    private lateinit var fakeAudiobookshelfRepository: FakeAudiobookshelfRepository
    private lateinit var logoutUseCase: LogoutUseCase
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()

        fakeSettingsRepository = FakeSettingsRepository()
        fakeAudiobookshelfRepository = FakeAudiobookshelfRepository()
        logoutUseCase = LogoutUseCase(fakeAudiobookshelfRepository, fakeSettingsRepository)

        // Seed default preferences in fake repo
        fakeSettingsRepository.fakeUrl = "https://my-server.com"
        fakeSettingsRepository.fakeUsername = "settings-user"
        fakeSettingsRepository.saveSkipForwardDuration(45)
        fakeSettingsRepository.saveSkipBackwardDuration(15)
        fakeSettingsRepository.savePlaybackSpeed(1.25f)
        fakeSettingsRepository.saveGoBackOnInterrupt(false)
        fakeSettingsRepository.saveHideEmptyLibraryTabs(true)
        fakeSettingsRepository.saveLibrarySyncIntervalHours(12)
        fakeSettingsRepository.saveLibraryLastSyncTimestamp(0L) // Never synced
        fakeSettingsRepository.saveLibraryId("lib-settings")
        fakeSettingsRepository.cachedLibraries.addAll(
            listOf(Library(id = "lib-settings", name = "Settings Library", type = "audiobook"))
        )

        // Setup offline/online status to default online
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val shadowConnectivityManager = org.robolectric.Shadows.shadowOf(connectivityManager)
        val networkInfo = org.robolectric.shadows.ShadowNetworkInfo.newInstance(
            android.net.NetworkInfo.DetailedState.CONNECTED,
            android.net.ConnectivityManager.TYPE_WIFI,
            0,
            true,
            true
        )
        shadowConnectivityManager.setActiveNetworkInfo(networkInfo)

        viewModel = SettingsViewModel(
            logoutUseCase = logoutUseCase,
            settingsRepository = fakeSettingsRepository,
            audiobookshelfRepository = fakeAudiobookshelfRepository,
            context = context,
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun settingsScreen_rendersCorrectly() {
        composeTestRule.setContent {
            SettingsScreen(
                onLogout = {},
                viewModel = viewModel
            )
        }

        // Verify account info section
        composeTestRule.onNodeWithText("Account").assertExists()
        composeTestRule.onNodeWithText("Server Address").assertExists()
        composeTestRule.onNodeWithText("https://my-server.com").assertExists()
        composeTestRule.onNodeWithText("Username").assertExists()
        composeTestRule.onNodeWithText("settings-user").assertExists()
        composeTestRule.onNodeWithText("Active Library").assertExists()
        composeTestRule.onNodeWithText("Settings Library").assertExists()

        // Verify buttons
        composeTestRule.onNodeWithText("Log Out").assertExists()
        composeTestRule.onNodeWithText("Sync Now").assertExists()
        composeTestRule.onNodeWithText("Clear Cache").assertExists()
    }

    @Test
    fun settingsScreen_toggleHideEmptyLibraryTabs_updatesPreferences() {
        composeTestRule.setContent {
            SettingsScreen(
                onLogout = {},
                viewModel = viewModel
            )
        }

        // Verify initial state is checked/true
        assertTrue(viewModel.hideEmptyLibraryTabs.value)

        // Toggle the Hide Empty Tabs switch sibling
        composeTestRule.onNode(hasParent(hasAnyDescendant(hasText("Hide Empty Tabs"))) and isToggleable())
            .performScrollTo()
            .performClick()

        // Verify it was updated
        assertFalse(viewModel.hideEmptyLibraryTabs.value)
        assertFalse(fakeSettingsRepository.getHideEmptyLibraryTabs())
    }

    @Test
    fun settingsScreen_toggleGoBackOnInterrupt_updatesPreferences() {
        composeTestRule.setContent {
            SettingsScreen(
                onLogout = {},
                viewModel = viewModel
            )
        }

        // Verify initial state is unchecked/false
        assertFalse(viewModel.goBackOnInterrupt.value)

        // Toggle Go Back on Interrupt switch sibling
        composeTestRule.onNode(hasParent(hasAnyDescendant(hasText("Go Back on Interrupt"))) and isToggleable())
            .performScrollTo()
            .performClick()

        // Verify it was updated
        assertTrue(viewModel.goBackOnInterrupt.value)
        assertTrue(fakeSettingsRepository.getGoBackOnInterrupt())
    }

    @Test
    fun settingsScreen_logoutDialog_showsAndConfirms() {
        var onLogoutCalled = false
        composeTestRule.setContent {
            SettingsScreen(
                onLogout = { onLogoutCalled = true },
                viewModel = viewModel
            )
        }

        // Click the Log Out button
        composeTestRule.onNodeWithText("Log Out").performScrollTo().performClick()

        // Verify Dialog title and message are visible
        composeTestRule.onNodeWithText("Log Out?").assertExists()
        composeTestRule.onNodeWithText("Are you sure you want to log out? Unsynced listening progress may be lost.").assertExists()

        // Confirm log out in the dialog (confirm button also says "Log Out")
        composeTestRule.onAllNodesWithText("Log Out").onLast().performClick()

        // Verify logout callback and repo clear
        assertTrue(onLogoutCalled)
        assertTrue(fakeAudiobookshelfRepository.clearLocalDataCalled)
    }

    @Test
    fun settingsScreen_clearCacheDialog_showsAndConfirms() {
        // Seed dummy downloaded files to clear
        val downloadsDir = File(context.getExternalFilesDir(null), "downloads")
        downloadsDir.mkdirs()
        val dummyFile = File(downloadsDir, "test_track.mp3")
        dummyFile.createNewFile()
        assertTrue(dummyFile.exists())

        composeTestRule.setContent {
            SettingsScreen(
                onLogout = {},
                viewModel = viewModel
            )
        }

        // Click Clear Cache button
        composeTestRule.onNodeWithText("Clear Cache").performScrollTo().performClick()

        // Verify confirmation dialog title
        composeTestRule.onNodeWithText("Clear Cached Data?").assertExists()

        // Click "Clear" confirmation button
        composeTestRule.onNodeWithText("Clear").performClick()

        // Let background processes run
        composeTestRule.waitForIdle()

        // Verify directory is deleted and repository clear is called
        assertFalse(dummyFile.exists())
        assertTrue(fakeAudiobookshelfRepository.clearLocalDataCalled)
    }

    @Test
    fun settingsScreen_dialogSyncInterval_updatesValue() {
        composeTestRule.setContent {
            SettingsScreen(
                onLogout = {},
                viewModel = viewModel
            )
        }

        // Periodic Sync Interval initially shows "12 hours"
        composeTestRule.onNodeWithText("12 hours").assertExists()

        // Click on sync interval settings item
        composeTestRule.onNodeWithText("Periodic Sync Interval").performScrollTo().performClick()

        // Verify dialog pops up (has "Disabled" option)
        composeTestRule.onNodeWithText("Disabled").assertExists()

        // Click on "6 hours" option in the dialog
        composeTestRule.onNodeWithText("6 hours").performClick()

        // Verify it updates state and settings repo
        assertEquals(6, viewModel.syncIntervalHours.value)
        assertEquals(6, fakeSettingsRepository.getLibrarySyncIntervalHours())
        composeTestRule.onNodeWithText("6 hours").assertExists()
    }
}
