package dev.vikingsen.skald.feature.home

import dev.vikingsen.skald.core.model.*
import dev.vikingsen.skald.domain.fakes.FakeAudiobookshelfRepository
import dev.vikingsen.skald.domain.fakes.FakeSettingsRepository
import dev.vikingsen.skald.domain.repository.PlaybackStateProvider
import dev.vikingsen.skald.domain.usecase.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeSettingsRepository: FakeSettingsRepository
    private lateinit var fakeAudiobookshelfRepository: HomeFakeAudiobookshelfRepository
    private lateinit var fakePlaybackStateProvider: FakePlaybackStateProvider

    private lateinit var getPersonalizedShelvesUseCase: GetPersonalizedShelvesUseCase
    private lateinit var syncPersonalizedShelvesUseCase: SyncPersonalizedShelvesUseCase
    private lateinit var getBooksUseCase: GetBooksUseCase
    private lateinit var getPlaybackProgressUseCase: GetPlaybackProgressUseCase
    private lateinit var fetchLibrariesUseCase: FetchLibrariesUseCase
    private lateinit var logoutUseCase: LogoutUseCase
    private lateinit var getMiniPlayerStateUseCase: GetMiniPlayerStateUseCase

    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        fakeSettingsRepository = FakeSettingsRepository()
        fakeAudiobookshelfRepository = HomeFakeAudiobookshelfRepository()
        fakePlaybackStateProvider = FakePlaybackStateProvider()

        // Seed settings
        fakeSettingsRepository.fakeUrl = "https://demo.server.com"
        fakeSettingsRepository.fakeToken = "token123"
        fakeSettingsRepository.saveLibraryId("lib-1")
        fakeSettingsRepository.cachedLibraries.addAll(
            listOf(
                Library(id = "lib-1", name = "Audiobooks", type = "audiobook"),
                Library(id = "lib-2", name = "Podcasts", type = "podcast")
            )
        )

        getPersonalizedShelvesUseCase = GetPersonalizedShelvesUseCase(fakeAudiobookshelfRepository)
        syncPersonalizedShelvesUseCase = SyncPersonalizedShelvesUseCase(fakeAudiobookshelfRepository)
        getBooksUseCase = GetBooksUseCase(fakeAudiobookshelfRepository)
        getPlaybackProgressUseCase = GetPlaybackProgressUseCase(fakeAudiobookshelfRepository)
        fetchLibrariesUseCase = FetchLibrariesUseCase(fakeAudiobookshelfRepository)
        logoutUseCase = LogoutUseCase(fakeAudiobookshelfRepository, fakeSettingsRepository)
        getMiniPlayerStateUseCase = GetMiniPlayerStateUseCase(fakePlaybackStateProvider, fakeSettingsRepository)

        // Seed mock responses
        fakeAudiobookshelfRepository.fetchLibrariesResult = Result.success(
            listOf(
                Library(id = "lib-1", name = "Audiobooks", type = "audiobook"),
                Library(id = "lib-2", name = "Podcasts", type = "podcast")
            )
        )

        viewModel = HomeViewModel(
            getPersonalizedShelvesUseCase = getPersonalizedShelvesUseCase,
            syncPersonalizedShelvesUseCase = syncPersonalizedShelvesUseCase,
            getBooksUseCase = getBooksUseCase,
            getPlaybackProgressUseCase = getPlaybackProgressUseCase,
            fetchLibrariesUseCase = fetchLibrariesUseCase,
            logoutUseCase = logoutUseCase,
            settingsRepository = fakeSettingsRepository,
            getMiniPlayerStateUseCase = getMiniPlayerStateUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_loadsLibrariesAndAutoSyncs() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.libraries.value.size)
        assertEquals("lib-1", viewModel.selectedLibraryId.value)
        assertTrue(fakeAudiobookshelfRepository.syncHomeShelvesCalled)
    }

    @Test
    fun setLibraryId_updatesRepositoryAndTriggersRefresh() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        fakeAudiobookshelfRepository.syncHomeShelvesCalled = false

        viewModel.setLibraryId("lib-2")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("lib-2", viewModel.selectedLibraryId.value)
        assertEquals("lib-2", fakeSettingsRepository.getLibraryId())
        assertTrue(fakeAudiobookshelfRepository.syncHomeShelvesCalled)
    }

    @Test
    fun refresh_success_updatesRefreshingState() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.errorMessage)

        viewModel.refresh(forceRefresh = true)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRefreshing)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun refresh_failure_propagatesErrorMessage() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        testDispatcher.scheduler.advanceUntilIdle()
        fakeAudiobookshelfRepository.syncHomeShelvesResult = Result.failure(Exception("Network error"))

        viewModel.refresh(forceRefresh = true)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRefreshing)
        assertEquals("Network error", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun uiState_mapsShelvesCorrectly() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        fakeAudiobookshelfRepository.booksFlow.value = listOf(
            Book(
                id = "book-1",
                libraryId = "lib-1",
                title = "Book Title 1",
                author = "Author 1",
                narrator = "Narrator 1",
                description = "Description 1",
                duration = 1000.0,
                coverPath = null,
                isDownloaded = true,
                audioFiles = emptyList(),
                chapters = emptyList()
            )
        )
        fakeAudiobookshelfRepository.progressFlow.value = listOf(
            PlaybackProgress(
                bookId = "book-1",
                currentTime = 450.0,
                progress = 0.45f,
                isFinished = false,
                lastUpdated = 123456789L
            )
        )
        fakeAudiobookshelfRepository.homeShelvesFlow.value = listOf(
            HomeShelf(
                id = "shelf-1",
                libraryId = "lib-1",
                label = "Continue Listening",
                total = 1,
                type = "book",
                items = listOf(
                    HomeShelfItem(
                        entityId = "book-1",
                        title = "Book Title 1",
                        subtitle = "Author 1",
                        imageUrl = "/api/cover/book-1"
                    )
                )
            )
        )

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.shelves.size)
        val shelf = state.shelves[0]
        assertEquals("shelf-1", shelf.id)
        assertEquals("Continue Listening", shelf.label)
        assertEquals(1, shelf.items.size)

        val item = shelf.items[0]
        assertEquals("book-1", item.entityId)
        assertEquals("Book Title 1", item.title)
        assertEquals("Author 1", item.subtitle)
        assertEquals("https://demo.server.com/api/cover/book-1", item.imageUrl)
        assertEquals("Bearer token123", item.authorizationHeader)
        assertEquals(0.45f, item.progress)
        assertFalse(item.isFinished)
        assertTrue(item.isDownloaded)
    }

    @Test
    fun logout_clearsRepositoriesAndTriggersCallback() = runTest {
        var logoutComplete = false
        viewModel.logout { logoutComplete = true }
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(logoutComplete)
        assertNull(fakeSettingsRepository.getServerUrl())
        assertTrue(fakeAudiobookshelfRepository.clearLocalDataCalled)
    }

    // Local Test Doubles
    class HomeFakeAudiobookshelfRepository : FakeAudiobookshelfRepository() {
        val homeShelvesFlow = MutableStateFlow<List<HomeShelf>>(emptyList())
        val booksFlow = MutableStateFlow<List<Book>>(emptyList())
        val progressFlow = MutableStateFlow<List<PlaybackProgress>>(emptyList())
        var syncHomeShelvesResult: Result<Unit> = Result.success(Unit)
        var syncHomeShelvesCalled = false

        override fun getHomeShelvesFlow(libraryId: String): Flow<List<HomeShelf>> = homeShelvesFlow
        override fun getBooksFlow(): Flow<List<Book>> = booksFlow
        override fun getAllProgressFlow(): Flow<List<PlaybackProgress>> = progressFlow
        override suspend fun syncHomeShelves(libraryId: String, forceRefresh: Boolean): Result<Unit> {
            syncHomeShelvesCalled = true
            return syncHomeShelvesResult
        }
    }

    class FakePlaybackStateProvider : PlaybackStateProvider {
        override val currentBook = MutableStateFlow<Book?>(null)
        override val isPlaying = MutableStateFlow(false)
        override val currentPosition = MutableStateFlow(0.0)
        override val duration = MutableStateFlow(0.0)
        override val isLoading = MutableStateFlow(false)
    }
}
