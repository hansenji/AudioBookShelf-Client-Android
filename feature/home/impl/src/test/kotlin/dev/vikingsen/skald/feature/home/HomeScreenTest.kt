package dev.vikingsen.skald.feature.home

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.vikingsen.skald.core.model.*
import dev.vikingsen.skald.domain.fakes.FakeAudiobookshelfRepository
import dev.vikingsen.skald.domain.fakes.FakeSettingsRepository
import dev.vikingsen.skald.domain.repository.PlaybackStateProvider
import dev.vikingsen.skald.domain.usecase.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testDispatcher = UnconfinedTestDispatcher()

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
    fun homeScreen_rendersSelectedLibrary() {
        composeTestRule.setContent {
            HomeScreen(
                onBookClick = {},
                viewModel = viewModel
            )
        }

        // Verify selected library title is visible
        composeTestRule.onNodeWithText("Audiobooks").assertExists()
    }

    @Test
    fun homeScreen_rendersShelvesCorrectly() {
        // Seed books and progress
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

        composeTestRule.setContent {
            HomeScreen(
                onBookClick = {},
                viewModel = viewModel
            )
        }

        // Verify shelf label is displayed
        composeTestRule.onNodeWithText("Continue Listening").assertExists()
        // Verify book item title is displayed
        composeTestRule.onNodeWithText("Book Title 1").assertExists()
    }

    // Local Test Doubles
    class HomeFakeAudiobookshelfRepository : FakeAudiobookshelfRepository() {
        val homeShelvesFlow = MutableStateFlow<List<HomeShelf>>(emptyList())
        val booksFlow = MutableStateFlow<List<Book>>(emptyList())
        val progressFlow = MutableStateFlow<List<PlaybackProgress>>(emptyList())
        var syncHomeShelvesResult: Result<Unit> = Result.success(Unit)
        var syncHomeShelvesCalled = false

        override fun getHomeShelvesFlow(libraryId: String): kotlinx.coroutines.flow.Flow<List<HomeShelf>> = homeShelvesFlow
        override fun getBooksFlow(): kotlinx.coroutines.flow.Flow<List<Book>> = booksFlow
        override fun getAllProgressFlow(): kotlinx.coroutines.flow.Flow<List<PlaybackProgress>> = progressFlow
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
