package dev.vikingsen.skald.feature.library

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.vikingsen.skald.core.model.Author
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
class AuthorDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var viewModel: AuthorDetailViewModel

    // Mock flows
    private val authorFlow = MutableStateFlow<Author?>(null)
    private val booksFlow = MutableStateFlow<List<BookCardUiModel>>(emptyList())
    private val isRefreshingFlow = MutableStateFlow(false)
    private val errorFlow = MutableStateFlow<String?>(null)
    private val showMiniPlayerFlow = MutableStateFlow(false)

    private val testAuthor = Author(
        id = "author-1",
        libraryId = "lib-1",
        name = "Brandon Sanderson",
        description = "A fantasy author.",
        imagePath = null,
        bookCount = 5
    )

    private val testBookCard = BookCardUiModel(
        id = "book-1",
        title = "Mistborn",
        author = "Brandon Sanderson",
        narrator = "Michael Kramer",
        coverUrl = "/mistborn.jpg",
        authorizationHeader = "Bearer jwt",
        isDownloaded = true,
        duration = 3600.0,
        progress = null
    )

    @Before
    fun setUp() {
        viewModel = mockk(relaxed = true)

        every { viewModel.author } returns authorFlow
        every { viewModel.books } returns booksFlow
        every { viewModel.isRefreshing } returns isRefreshingFlow
        every { viewModel.error } returns errorFlow
        every { viewModel.showMiniPlayer } returns showMiniPlayerFlow
        every { viewModel.serverUrl } returns "https://my-server.com"
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun authorDetailScreen_rendersAuthorInfo() {
        authorFlow.value = testAuthor
        booksFlow.value = listOf(testBookCard)

        composeTestRule.setContent {
            AuthorDetailScreen(
                authorId = "author-1",
                onBackClick = {},
                onBookClick = {},
                viewModel = viewModel
            )
        }

        // Verify author details render
        composeTestRule.onAllNodesWithText("Brandon Sanderson").assertCountEquals(2)
        composeTestRule.onNodeWithText("A fantasy author.").assertExists()

        // Verify book card renders
        composeTestRule.onNodeWithText("Mistborn").assertExists()
    }

    @Test
    fun authorDetailScreen_backClick_triggersCallback() {
        authorFlow.value = testAuthor
        var backClicked = false

        composeTestRule.setContent {
            AuthorDetailScreen(
                authorId = "author-1",
                onBackClick = { backClicked = true },
                onBookClick = {},
                viewModel = viewModel
            )
        }

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        assertTrue(backClicked)
    }

    @Test
    fun authorDetailScreen_refreshClick_triggersViewModel() {
        authorFlow.value = testAuthor

        composeTestRule.setContent {
            AuthorDetailScreen(
                authorId = "author-1",
                onBackClick = {},
                onBookClick = {},
                viewModel = viewModel
            )
        }

        composeTestRule.onNodeWithContentDescription("Refresh").performClick()
        verify { viewModel.refresh() }
    }

    @Test
    fun authorDetailScreen_error_displaysMessage() {
        errorFlow.value = "Unable to connect to server"
        authorFlow.value = null

        composeTestRule.setContent {
            AuthorDetailScreen(
                authorId = "author-1",
                onBackClick = {},
                onBookClick = {},
                viewModel = viewModel
            )
        }

        composeTestRule.onNodeWithText("Unable to connect to server").assertExists()
    }

    @Test
    fun authorDetailScreen_bookClick_triggersCallback() {
        authorFlow.value = testAuthor
        booksFlow.value = listOf(testBookCard)
        var clickedBookId: String? = null

        composeTestRule.setContent {
            AuthorDetailScreen(
                authorId = "author-1",
                onBackClick = {},
                onBookClick = { clickedBookId = it },
                viewModel = viewModel
            )
        }

        // Click on book item
        composeTestRule.onNodeWithText("Mistborn").performClick()
        assertEquals("book-1", clickedBookId)
    }
}
