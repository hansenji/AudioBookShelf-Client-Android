package dev.vikingsen.skald.feature.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.vikingsen.skald.core.model.Library
import dev.vikingsen.skald.domain.fakes.FakeAudiobookshelfRepository
import dev.vikingsen.skald.domain.fakes.FakeSettingsRepository
import dev.vikingsen.skald.domain.usecase.LogoutUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

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
        fakeSettingsRepository.saveLibraryLastSyncTimestamp(9876543210L)
        fakeSettingsRepository.saveLibraryId("lib-settings")
        fakeSettingsRepository.cachedLibraries.addAll(
            listOf(Library(id = "lib-settings", name = "Settings Library", type = "audiobook"))
        )
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
        
        val activeNet = connectivityManager.activeNetwork
        if (activeNet != null) {
            val networkCapabilities = org.robolectric.shadows.ShadowNetworkCapabilities.newInstance()
            val shadowCapabilities = org.robolectric.Shadows.shadowOf(networkCapabilities)
            shadowCapabilities.addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            shadowConnectivityManager.setNetworkCapabilities(activeNet, networkCapabilities)
        }

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
    fun init_loadsSettingsCorrectly() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("https://my-server.com", viewModel.serverUrl.value)
        assertEquals("settings-user", viewModel.username.value)
        assertEquals(45, viewModel.skipForwardDuration.value)
        assertEquals(15, viewModel.skipBackwardDuration.value)
        assertEquals(1.25f, viewModel.playbackSpeed.value)
        assertFalse(viewModel.goBackOnInterrupt.value)
        assertTrue(viewModel.hideEmptyLibraryTabs.value)
        assertEquals(12, viewModel.syncIntervalHours.value)
        assertEquals(9876543210L, viewModel.lastSyncTimestamp.value)
        assertEquals("Settings Library", viewModel.activeLibraryName.value)
    }

    @Test
    fun updateSkipForwardDuration_persistsAndUpdatesFlow() = runTest {
        viewModel.updateSkipForwardDuration(60)
        assertEquals(60, viewModel.skipForwardDuration.value)
        assertEquals(60, fakeSettingsRepository.getSkipForwardDuration())
    }

    @Test
    fun updateSkipBackwardDuration_persistsAndUpdatesFlow() = runTest {
        viewModel.updateSkipBackwardDuration(5)
        assertEquals(5, viewModel.skipBackwardDuration.value)
        assertEquals(5, fakeSettingsRepository.getSkipBackwardDuration())
    }

    @Test
    fun updatePlaybackSpeed_persistsAndUpdatesFlow() = runTest {
        viewModel.updatePlaybackSpeed(1.5f)
        assertEquals(1.5f, viewModel.playbackSpeed.value)
        assertEquals(1.5f, fakeSettingsRepository.getPlaybackSpeed())
    }

    @Test
    fun updateGoBackOnInterrupt_persistsAndUpdatesFlow() = runTest {
        viewModel.updateGoBackOnInterrupt(true)
        assertTrue(viewModel.goBackOnInterrupt.value)
        assertTrue(fakeSettingsRepository.getGoBackOnInterrupt())
    }

    @Test
    fun updateHideEmptyLibraryTabs_persistsAndUpdatesFlow() = runTest {
        viewModel.updateHideEmptyLibraryTabs(false)
        assertFalse(viewModel.hideEmptyLibraryTabs.value)
        assertFalse(fakeSettingsRepository.getHideEmptyLibraryTabs())
    }

    @Test
    fun updateSyncInterval_persistsAndUpdatesFlow() = runTest {
        viewModel.updateSyncInterval(6)
        assertEquals(6, viewModel.syncIntervalHours.value)
        assertEquals(6, fakeSettingsRepository.getLibrarySyncIntervalHours())
    }

    @Test
    fun logout_clearsRepositoriesAndTriggersCallback() = runTest {
        var onCompleteCalled = false
        viewModel.logout { onCompleteCalled = true }
        
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(onCompleteCalled)
        assertNull(fakeSettingsRepository.getServerUrl())
        assertNull(fakeSettingsRepository.getUsername())
        assertTrue(fakeAudiobookshelfRepository.clearLocalDataCalled)
    }

    @Test
    fun syncNow_executesSyncAndReloadsSettings() = runTest {
        assertFalse(viewModel.isSyncing.value)

        viewModel.syncNow()
        
        // Let background execution finish
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.isSyncing.value)
        assertTrue(fakeAudiobookshelfRepository.syncLibraryBooksCalled)
        assertTrue(fakeAudiobookshelfRepository.syncGlobalProgressCalled)
    }

    @Test
    fun clearCache_deletesDownloadsFolderAndClearsLocalData() = runTest {
        // Create a dummy downloads directory and file
        val downloadsDir = File(context.getExternalFilesDir(null), "downloads")
        downloadsDir.mkdirs()
        val dummyFile = File(downloadsDir, "test_track.mp3")
        dummyFile.createNewFile()
        assertTrue(dummyFile.exists())

        viewModel.clearCache()
        
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(dummyFile.exists())
        assertFalse(downloadsDir.exists())
        assertTrue(fakeAudiobookshelfRepository.clearLocalDataCalled)
    }

    @Test
    fun deleteOrphanedDownloads_callsRepositoryAndDelete() = runTest {
        viewModel.deleteOrphanedDownloads()
        
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(fakeAudiobookshelfRepository.deleteOrphanedDownloadsCalled)
    }
}
