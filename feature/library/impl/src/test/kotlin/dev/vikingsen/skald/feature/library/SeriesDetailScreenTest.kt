package dev.vikingsen.skald.feature.library

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.vikingsen.skald.core.model.Series
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
class SeriesDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var viewModel: SeriesDetailViewModel

    // Mock flows
    private val seriesFlow = MutableStateFlow<Series?>(null)
    private val booksFlow = MutableStateFlow<List<BookCardUiModel>>(emptyList())
    private val showMiniPlayerFlow = MutableStateFlow(false)

    private val testSeries = Series(
        id = "series-1",
        libraryId = "lib-1",
        name = "Harry Potter",
        description = "A series about a young wizard.",
        bookCount = 7
    )

    private val testBookCard = BookCardUiModel(
        id = "book-1",
        title = "Sorcerer's Stone",
        author = "J.K. Rowling",
        narrator = "Jim Dale",
        coverUrl = "/potter1.jpg",
        authorizationHeader = "Bearer jwt",
        isDownloaded = true,
        duration = 3600.0,
        progress = null,
        seriesSequence = "1"
    )

    @Before
    fun setUp() {
        viewModel = mockk(relaxed = true)

        every { viewModel.series } returns seriesFlow
        every { viewModel.books } returns booksFlow
        every { viewModel.showMiniPlayer } returns showMiniPlayerFlow
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun seriesDetailScreen_rendersSeriesInfo() {
        seriesFlow.value = testSeries
        booksFlow.value = listOf(testBookCard)

        composeTestRule.setContent {
            SeriesDetailScreen(
                seriesId = "series-1",
                onBackClick = {},
                onBookClick = {},
                viewModel = viewModel
            )
        }

        // Verify series details render
        composeTestRule.onNodeWithText("Harry Potter").assertExists()
        composeTestRule.onNodeWithText("A series about a young wizard.").assertExists()

        // Verify book card renders (specifically book title and author)
        composeTestRule.onNodeWithText("Sorcerer's Stone").assertExists()
        composeTestRule.onNodeWithText("J.K. Rowling").assertExists()
    }

    @Test
    fun seriesDetailScreen_backClick_triggersCallback() {
        seriesFlow.value = testSeries
        var backClicked = false

        composeTestRule.setContent {
            SeriesDetailScreen(
                seriesId = "series-1",
                onBackClick = { backClicked = true },
                onBookClick = {},
                viewModel = viewModel
            )
        }

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        assertTrue(backClicked)
    }

    @Test
    fun seriesDetailScreen_bookClick_triggersCallback() {
        seriesFlow.value = testSeries
        booksFlow.value = listOf(testBookCard)
        var clickedBookId: String? = null

        composeTestRule.setContent {
            SeriesDetailScreen(
                seriesId = "series-1",
                onBackClick = {},
                onBookClick = { clickedBookId = it },
                viewModel = viewModel
            )
        }

        // Click on book item row
        composeTestRule.onNodeWithText("Sorcerer's Stone").performClick()
        assertEquals("book-1", clickedBookId)
    }
}
