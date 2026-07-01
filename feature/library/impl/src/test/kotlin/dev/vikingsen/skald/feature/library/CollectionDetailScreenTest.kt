package dev.vikingsen.skald.feature.library

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.vikingsen.skald.core.model.BookCollection
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
class CollectionDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var viewModel: CollectionDetailViewModel

    // Mock flows
    private val collectionFlow = MutableStateFlow<BookCollection?>(null)
    private val booksFlow = MutableStateFlow<List<BookCardUiModel>>(emptyList())
    private val showMiniPlayerFlow = MutableStateFlow(false)

    private val testCollection = BookCollection(
        id = "coll-1",
        libraryId = "lib-1",
        name = "Sci-Fi Favorites",
        description = "A collection of awesome space operas.",
        bookIds = listOf("book-1"),
        lastUpdated = 123456789L
    )

    private val testBookCard = BookCardUiModel(
        id = "book-1",
        title = "Dune",
        author = "Frank Herbert",
        narrator = "George Guidall",
        coverUrl = "/dune.jpg",
        authorizationHeader = "Bearer jwt",
        isDownloaded = true,
        duration = 3600.0,
        progress = null
    )

    @Before
    fun setUp() {
        viewModel = mockk(relaxed = true)

        every { viewModel.collection } returns collectionFlow
        every { viewModel.books } returns booksFlow
        every { viewModel.showMiniPlayer } returns showMiniPlayerFlow
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun collectionDetailScreen_rendersCollectionInfo() {
        collectionFlow.value = testCollection
        booksFlow.value = listOf(testBookCard)

        composeTestRule.setContent {
            CollectionDetailScreen(
                collectionId = "coll-1",
                onBackClick = {},
                onBookClick = {},
                viewModel = viewModel
            )
        }

        // Verify collection name and description
        composeTestRule.onNodeWithText("Sci-Fi Favorites").assertExists()
        composeTestRule.onNodeWithText("A collection of awesome space operas.").assertExists()

        // Verify book card renders (Dune and Frank Herbert)
        composeTestRule.onNodeWithText("Dune").assertExists()
        composeTestRule.onNodeWithText("Frank Herbert").assertExists()
    }

    @Test
    fun collectionDetailScreen_backClick_triggersCallback() {
        collectionFlow.value = testCollection
        var backClicked = false

        composeTestRule.setContent {
            CollectionDetailScreen(
                collectionId = "coll-1",
                onBackClick = { backClicked = true },
                onBookClick = {},
                viewModel = viewModel
            )
        }

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        assertTrue(backClicked)
    }

    @Test
    fun collectionDetailScreen_bookClick_triggersCallback() {
        collectionFlow.value = testCollection
        booksFlow.value = listOf(testBookCard)
        var clickedBookId: String? = null

        composeTestRule.setContent {
            CollectionDetailScreen(
                collectionId = "coll-1",
                onBackClick = {},
                onBookClick = { clickedBookId = it },
                viewModel = viewModel
            )
        }

        // Click on book item row
        composeTestRule.onNodeWithText("Dune").performClick()
        assertEquals("book-1", clickedBookId)
    }
}
