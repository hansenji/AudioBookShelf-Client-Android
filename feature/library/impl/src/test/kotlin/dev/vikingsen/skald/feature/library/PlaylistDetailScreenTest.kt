package dev.vikingsen.skald.feature.library

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.vikingsen.skald.core.model.Playlist
import dev.vikingsen.skald.core.model.PlaylistItem
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
class PlaylistDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var viewModel: PlaylistDetailViewModel

    // Mock flows
    private val playlistFlow = MutableStateFlow<Playlist?>(null)
    private val playlistItemsFlow = MutableStateFlow<List<PlaylistItem>>(emptyList())
    private val isRefreshingFlow = MutableStateFlow(false)
    private val errorFlow = MutableStateFlow<String?>(null)
    private val showMiniPlayerFlow = MutableStateFlow(false)
    private val activeBookDetailFlow = MutableStateFlow<BookDetailUiModel?>(null)

    private val testPlaylist = Playlist(
        id = "playlist-1",
        name = "My Ultimate Playlist",
        description = "A playlist of fantasy favorites.",
        duration = 7200.0,
        itemCount = 2,
        items = emptyList(),
        lastUpdated = 123456789L
    )

    private val testPlaylistItem = PlaylistItem(
        id = "item-1",
        playlistId = "playlist-1",
        libraryItemId = "book-1",
        sequence = 1,
        title = "Way of Kings",
        duration = 3600.0,
        coverPath = "/wok.jpg"
    )

    @Before
    fun setUp() {
        viewModel = mockk(relaxed = true)

        every { viewModel.playlist } returns playlistFlow
        every { viewModel.playlistItems } returns playlistItemsFlow
        every { viewModel.isRefreshing } returns isRefreshingFlow
        every { viewModel.error } returns errorFlow
        every { viewModel.showMiniPlayer } returns showMiniPlayerFlow
        every { viewModel.activeBookDetail } returns activeBookDetailFlow
        every { viewModel.authorizationHeader } returns "Bearer jwt"
        every { viewModel.serverUrl } returns "https://my-server.com"
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun playlistDetailScreen_rendersPlaylistInfo() {
        playlistFlow.value = testPlaylist
        playlistItemsFlow.value = listOf(testPlaylistItem)

        composeTestRule.setContent {
            PlaylistDetailScreen(
                playlistId = "playlist-1",
                onBackClick = {},
                onBookClick = {},
                onPlayClick = {},
                viewModel = viewModel
            )
        }

        // Verify playlist details render
        composeTestRule.onNodeWithText("My Ultimate Playlist").assertExists()
        composeTestRule.onNodeWithText("A playlist of fantasy favorites.").assertExists()
        composeTestRule.onNodeWithText("2 tracks").assertExists()
        composeTestRule.onNodeWithText("Duration: 2h 0m").assertExists()

        // Verify item row renders
        composeTestRule.onNodeWithText("Way of Kings").assertExists()
        composeTestRule.onNodeWithText("1h 0m").assertExists()
    }

    @Test
    fun playlistDetailScreen_backClick_triggersCallback() {
        playlistFlow.value = testPlaylist
        var backClicked = false

        composeTestRule.setContent {
            PlaylistDetailScreen(
                playlistId = "playlist-1",
                onBackClick = { backClicked = true },
                onBookClick = {},
                onPlayClick = {},
                viewModel = viewModel
            )
        }

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        assertTrue(backClicked)
    }

    @Test
    fun playlistDetailScreen_playPlaylist_triggersViewModelAndCallback() {
        playlistFlow.value = testPlaylist
        var playClicked = false

        composeTestRule.setContent {
            PlaylistDetailScreen(
                playlistId = "playlist-1",
                onBackClick = {},
                onBookClick = {},
                onPlayClick = { playClicked = true },
                viewModel = viewModel
            )
        }

        composeTestRule.onNodeWithText("Play Playlist").performClick()
        verify { viewModel.playPlaylist(0) }
        assertTrue(playClicked)
    }

    @Test
    fun playlistDetailScreen_deleteItem_triggersViewModel() {
        playlistFlow.value = testPlaylist
        playlistItemsFlow.value = listOf(testPlaylistItem)

        composeTestRule.setContent {
            PlaylistDetailScreen(
                playlistId = "playlist-1",
                onBackClick = {},
                onBookClick = {},
                onPlayClick = {},
                viewModel = viewModel
            )
        }

        composeTestRule.onNodeWithContentDescription("Remove item").performClick()
        verify { viewModel.deleteItem(testPlaylistItem) }
    }
}
