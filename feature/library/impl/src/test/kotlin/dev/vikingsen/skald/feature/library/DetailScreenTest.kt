package dev.vikingsen.skald.feature.library

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1080dp-h2400dp-xxhdpi")
class DetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var viewModel: DetailViewModel

    // Mock flows
    private val bookDetailFlow = MutableStateFlow<BookDetailUiModel?>(null)
    private val isLoadingFlow = MutableStateFlow(false)
    private val errorFlow = MutableStateFlow<String?>(null)
    private val isDownloadingFlow = MutableStateFlow(false)
    private val downloadProgressFlow = MutableStateFlow(0f)
    private val downloadingFileNameFlow = MutableStateFlow<String?>(null)
    private val downloadErrorFlow = MutableStateFlow<String?>(null)
    private val showMiniPlayerFlow = MutableStateFlow(false)

    private val testBookDetail = BookDetailUiModel(
        id = "book-1",
        libraryId = "lib-1",
        title = "The Hobbit",
        author = "J.R.R. Tolkien",
        narrator = "Rob Inglis",
        duration = 3600.0,
        durationText = "1:00:00",
        coverUrl = "/hobbit.jpg",
        authorizationHeader = "Bearer jwt",
        isDownloaded = false,
        description = "A great adventure story.",
        chapters = emptyList(),
        progress = null,
        progressLeftText = null
    )

    @Before
    fun setUp() {
        viewModel = mockk(relaxed = true)

        every { viewModel.bookDetail } returns bookDetailFlow
        every { viewModel.isLoading } returns isLoadingFlow
        every { viewModel.error } returns errorFlow
        every { viewModel.isDownloading } returns isDownloadingFlow
        every { viewModel.downloadProgress } returns downloadProgressFlow
        every { viewModel.downloadingFileName } returns downloadingFileNameFlow
        every { viewModel.downloadError } returns downloadErrorFlow
        every { viewModel.showMiniPlayer } returns showMiniPlayerFlow
        every { viewModel.serverUrl } returns "https://my-server.com"
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun detailScreen_loading_showsProgressIndicator() {
        isLoadingFlow.value = true
        bookDetailFlow.value = null

        composeTestRule.setContent {
            DetailScreen(
                bookId = "book-1",
                onBackClick = {},
                onPlayClick = {},
                viewModel = viewModel
            )
        }

        // Verify that standard progress indicator exists
        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()
    }

    @Test
    fun detailScreen_rendersBookDetails() {
        bookDetailFlow.value = testBookDetail
        isLoadingFlow.value = false

        composeTestRule.setContent {
            DetailScreen(
                bookId = "book-1",
                onBackClick = {},
                onPlayClick = {},
                viewModel = viewModel
            )
        }

        // Verify titles and authors are shown
        composeTestRule.onNodeWithText("The Hobbit").assertExists()
        composeTestRule.onNodeWithText("By J.R.R. Tolkien").assertExists()
        composeTestRule.onNodeWithText("Narrated by Rob Inglis").assertExists()
        composeTestRule.onNodeWithText("Duration: 1:00:00").assertExists()
        composeTestRule.onNodeWithText("A great adventure story.").assertExists()
    }

    @Test
    fun detailScreen_playClick_triggersViewModel() {
        bookDetailFlow.value = testBookDetail

        var playClicked = false

        composeTestRule.setContent {
            DetailScreen(
                bookId = "book-1",
                onBackClick = {},
                onPlayClick = { playClicked = true },
                viewModel = viewModel
            )
        }

        // Find play button (labelled "Listen" when progress is null) and click it
        composeTestRule.onNodeWithText("Listen").performClick()

        verify { viewModel.playBook(any()) }
        assertTrue(playClicked)
    }

    @Test
    fun detailScreen_error_displaysErrorMessage() {
        errorFlow.value = "Failed to load book details"
        bookDetailFlow.value = null

        composeTestRule.setContent {
            DetailScreen(
                bookId = "book-1",
                onBackClick = {},
                onPlayClick = {},
                viewModel = viewModel
            )
        }

        // Verify the error label appears on screen
        composeTestRule.onNodeWithText("Failed to load book details").assertExists()
    }

    @Test
    fun detailScreen_backClick_triggersCallback() {
        bookDetailFlow.value = testBookDetail

        var backClicked = false

        composeTestRule.setContent {
            DetailScreen(
                bookId = "book-1",
                onBackClick = { backClicked = true },
                onPlayClick = {},
                viewModel = viewModel
            )
        }

        // Click on the back icon
        composeTestRule.onNodeWithContentDescription("Back").performClick()

        assertTrue(backClicked)
    }
}
