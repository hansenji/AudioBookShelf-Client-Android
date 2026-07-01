package dev.vikingsen.skald.feature.library

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.vikingsen.skald.core.model.Playlist
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AddToPlaylistDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var viewModel: AddToPlaylistViewModel
    private val playlistsFlow = MutableStateFlow<List<Playlist>>(emptyList())

    @Before
    fun setUp() {
        viewModel = mockk(relaxed = true)
        every { viewModel.playlists } returns playlistsFlow
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun addToPlaylistDialog_rendersEmptyState_whenNoPlaylists() {
        playlistsFlow.value = emptyList()

        composeTestRule.setContent {
            AddToPlaylistDialog(
                bookId = "book-1",
                libraryId = "lib-1",
                onDismiss = {},
                onDismissAll = {},
                viewModel = viewModel
            )
        }

        // Verify title and empty label
        composeTestRule.onNodeWithText("Select Playlist").assertExists()
        composeTestRule.onNodeWithText("No playlists found").assertExists()
        composeTestRule.onNodeWithText("Create New Playlist").assertExists()
    }

    @Test
    fun addToPlaylistDialog_rendersPlaylists_andClicks() {
        val testPlaylist = Playlist(
            id = "playlist-1",
            name = "My Faves",
            description = null,
            duration = 1000.0,
            itemCount = 2,
            items = emptyList(),
            lastUpdated = 0L
        )
        playlistsFlow.value = listOf(testPlaylist)

        var dismissAllCalled = false

        composeTestRule.setContent {
            AddToPlaylistDialog(
                bookId = "book-1",
                libraryId = "lib-1",
                onDismiss = {},
                onDismissAll = { dismissAllCalled = true },
                viewModel = viewModel
            )
        }

        // Verify playlist name is rendered
        composeTestRule.onNodeWithText("My Faves").assertExists()

        // Capture callback passed to addToPlaylist
        val callbackSlot = slot<(Result<Unit>) -> Unit>()
        every {
            viewModel.addToPlaylist(
                playlistId = "playlist-1",
                bookId = "book-1",
                onResult = capture(callbackSlot)
            )
        } answers {
            callbackSlot.captured(Result.success(Unit))
        }

        // Click on playlist item
        composeTestRule.onNodeWithText("My Faves").performClick()

        // Verify view model method is executed and success triggers dismiss
        verify { viewModel.addToPlaylist("playlist-1", "book-1", any()) }
        assertTrue(dismissAllCalled)
    }

    @Test
    fun addToPlaylistDialog_createNewPlaylist_showsSubDialogAndCreates() {
        playlistsFlow.value = emptyList()

        var dismissAllCalled = false

        composeTestRule.setContent {
            AddToPlaylistDialog(
                bookId = "book-1",
                libraryId = "lib-1",
                onDismiss = {},
                onDismissAll = { dismissAllCalled = true },
                viewModel = viewModel
            )
        }

        // Click on "Create New Playlist"
        composeTestRule.onNodeWithText("Create New Playlist").performClick()

        // Verify the creation dialog popped up
        composeTestRule.onNodeWithText("New Playlist").assertExists()
        composeTestRule.onNodeWithText("Playlist Name").assertExists()

        // Type new playlist name
        composeTestRule.onNode(hasSetTextAction()).performTextInput("Sci-Fi")

        // Capture callback passed to createPlaylistAndAdd
        val callbackSlot = slot<(Result<Unit>) -> Unit>()
        every {
            viewModel.createPlaylistAndAdd(
                name = "Sci-Fi",
                libraryId = "lib-1",
                bookId = "book-1",
                onResult = capture(callbackSlot)
            )
        } answers {
            callbackSlot.captured(Result.success(Unit))
        }

        // Click "Create" button
        composeTestRule.onNodeWithText("Create").performClick()

        // Verify ViewModel call and dismiss trigger
        verify { viewModel.createPlaylistAndAdd("Sci-Fi", "lib-1", "book-1", any()) }
        assertTrue(dismissAllCalled)
    }
}
