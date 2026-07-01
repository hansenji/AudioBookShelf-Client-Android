package dev.vikingsen.skald.feature.library

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.paging.PagingData
import dev.vikingsen.skald.core.model.*
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var viewModel: LibraryViewModel

    // Mock flows
    private val booksFlow = MutableStateFlow<PagingData<BookCardUiModel>>(PagingData.empty())
    private val isRefreshingFlow = MutableStateFlow(false)
    private val errorFlow = MutableStateFlow<String?>(null)
    private val filterStatusFlow = MutableStateFlow(ReadStatusFilter.ALL)
    private val filterDownloadedOnlyFlow = MutableStateFlow(false)
    private val sortByFlow = MutableStateFlow(SortOption.TITLE_ASC)
    private val selectedLibraryIdFlow = MutableStateFlow("lib-1")
    private val librariesFlow = MutableStateFlow(
        listOf(
            LibraryUiModel("lib-1", "My Audiobooks"),
            LibraryUiModel("lib-2", "My Podcasts")
        )
    )
    private val syncIntervalHoursFlow = MutableStateFlow(24)
    private val searchQueryFlow = MutableStateFlow("")
    private val currentTabFlow = MutableStateFlow(LibraryTab.BOOKS)
    private val visibleTabsFlow = MutableStateFlow(LibraryTab.entries.toList())
    private val showMiniPlayerFlow = MutableStateFlow(false)
    private val allPlaylistsFlow = MutableStateFlow<List<Playlist>>(emptyList())
    private val seriesFlow = MutableStateFlow<List<SeriesCardUiModel>>(emptyList())
    private val seriesFilterFlow = MutableStateFlow(SeriesFilter.ALL)
    private val seriesSortFlow = MutableStateFlow(SeriesSortOption.NAME_ASC)
    private val authorsFlow = MutableStateFlow<List<Author>>(emptyList())
    private val authorsSortFlow = MutableStateFlow(AuthorsSortOption.NAME_ASC)
    private val collectionsFlow = MutableStateFlow<List<BookCollection>>(emptyList())
    private val collectionsSortFlow = MutableStateFlow(CollectionsSortOption.NAME_ASC)
    private val playlistsFlow = MutableStateFlow<List<Playlist>>(emptyList())
    private val playlistsSortFlow = MutableStateFlow(PlaylistsSortOption.NAME_ASC)

    private val testBookCard = BookCardUiModel(
        id = "book-1",
        title = "The Hobbit",
        author = "J.R.R. Tolkien",
        narrator = "Rob Inglis",
        coverUrl = "/hobbit.jpg",
        authorizationHeader = "Bearer jwt",
        isDownloaded = true,
        duration = 3600.0,
        progress = PlaybackProgressUiModel(
            progress = 0.5f,
            isFinished = false,
            currentTime = 1800.0,
            lastUpdated = 123456789L
        )
    )

    @Before
    fun setUp() {
        viewModel = mockk(relaxed = true)

        // Stub state/flow properties in ViewModel
        every { viewModel.books } returns booksFlow
        every { viewModel.isRefreshing } returns isRefreshingFlow
        every { viewModel.error } returns errorFlow
        every { viewModel.filterStatus } returns filterStatusFlow
        every { viewModel.filterDownloadedOnly } returns filterDownloadedOnlyFlow
        every { viewModel.sortBy } returns sortByFlow
        every { viewModel.selectedLibraryId } returns selectedLibraryIdFlow
        every { viewModel.libraries } returns librariesFlow
        every { viewModel.syncIntervalHours } returns syncIntervalHoursFlow
        every { viewModel.searchQuery } returns searchQueryFlow
        every { viewModel.currentTab } returns currentTabFlow
        every { viewModel.visibleTabs } returns visibleTabsFlow
        every { viewModel.showMiniPlayer } returns showMiniPlayerFlow
        every { viewModel.allPlaylists } returns allPlaylistsFlow
        every { viewModel.series } returns seriesFlow
        every { viewModel.seriesFilter } returns seriesFilterFlow
        every { viewModel.seriesSort } returns seriesSortFlow
        every { viewModel.authors } returns authorsFlow
        every { viewModel.authorsSort } returns authorsSortFlow
        every { viewModel.collections } returns collectionsFlow
        every { viewModel.collectionsSort } returns collectionsSortFlow
        every { viewModel.playlists } returns playlistsFlow
        every { viewModel.playlistsSort } returns playlistsSortFlow
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun libraryScreen_rendersActiveLibraryNameAndTabs() {
        composeTestRule.setContent {
            LibraryScreen(
                onBookClick = {},
                onSeriesClick = {},
                onAuthorClick = {},
                onCollectionClick = {},
                onPlaylistClick = {},
                onPlayClick = {},
                viewModel = viewModel
            )
        }

        // Verify selected library title is visible
        composeTestRule.onNodeWithText("My Audiobooks").assertExists()

        // Verify tab names are visible
        composeTestRule.onNodeWithText("Books").assertExists()
        composeTestRule.onNodeWithText("Series").assertExists()
        composeTestRule.onNodeWithText("Collections").assertExists()
        composeTestRule.onNodeWithText("Authors").assertExists()
        composeTestRule.onNodeWithText("Playlists").assertExists()
    }

    @Test
    fun libraryScreen_switchingTabs_triggersViewModel() {
        composeTestRule.setContent {
            LibraryScreen(
                onBookClick = {},
                onSeriesClick = {},
                onAuthorClick = {},
                onCollectionClick = {},
                onPlaylistClick = {},
                onPlayClick = {},
                viewModel = viewModel
            )
        }

        // Click "Series" tab
        composeTestRule.onNodeWithText("Series").performClick()

        // Verify setcurrentTab was called on view model with Series tab
        verify { viewModel.setCurrentTab(LibraryTab.SERIES) }
    }

    @Test
    fun libraryScreen_showsBooksInBookTab() {
        // Seed paging data with a test book
        booksFlow.value = PagingData.from(listOf(testBookCard))
        currentTabFlow.value = LibraryTab.BOOKS

        composeTestRule.setContent {
            LibraryScreen(
                onBookClick = {},
                onSeriesClick = {},
                onAuthorClick = {},
                onCollectionClick = {},
                onPlaylistClick = {},
                onPlayClick = {},
                viewModel = viewModel
            )
        }

        // Verify book card renders title and author
        composeTestRule.onNodeWithText("The Hobbit").assertExists()
        composeTestRule.onNodeWithText("J.R.R. Tolkien").assertExists()
    }

    @Test
    fun libraryScreen_searchQuery_updatesViewModel() {
        composeTestRule.setContent {
            LibraryScreen(
                onBookClick = {},
                onSeriesClick = {},
                onAuthorClick = {},
                onCollectionClick = {},
                onPlaylistClick = {},
                onPlayClick = {},
                viewModel = viewModel
            )
        }

        val searchField = composeTestRule.onNodeWithText("Search by title, author, narrator...")
        searchField.assertExists()
        searchField.performTextInput("Hobbit")

        // Verify query state is updated
        assertEquals("Hobbit", searchQueryFlow.value)
    }

    @Test
    fun libraryScreen_clickLibraryDropdown_allowsSwitchingLibrary() {
        composeTestRule.setContent {
            LibraryScreen(
                onBookClick = {},
                onSeriesClick = {},
                onAuthorClick = {},
                onCollectionClick = {},
                onPlaylistClick = {},
                onPlayClick = {},
                viewModel = viewModel
            )
        }

        // Click on dropdown trigger (shows "My Audiobooks" and dropdown arrow)
        composeTestRule.onNodeWithText("My Audiobooks").performClick()

        // Verify that the dropdown menu options are shown
        composeTestRule.onNodeWithText("My Podcasts").assertExists()

        // Click on the other library
        composeTestRule.onNodeWithText("My Podcasts").performClick()

        // Verify setLibraryId was called with lib-2 ID
        verify { viewModel.setLibraryId("lib-2") }
    }
}
