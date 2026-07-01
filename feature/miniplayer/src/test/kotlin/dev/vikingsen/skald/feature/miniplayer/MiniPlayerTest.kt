package dev.vikingsen.skald.feature.miniplayer

import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.vikingsen.skald.core.model.MiniPlayerState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MiniPlayerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var viewModel: MiniPlayerViewModel
    private lateinit var uiStateFlow: MutableStateFlow<MiniPlayerState?>

    private val dummyState = MiniPlayerState(
        bookId = "book-123",
        title = "Narnia",
        author = "C.S. Lewis",
        coverUrl = "https://my-server.com/cover.jpg",
        authorizationHeader = "Bearer token",
        isPlaying = false,
        progress = 0.3f,
        isLoading = false
    )

    @Before
    fun setUp() {
        viewModel = mockk(relaxed = true)
        uiStateFlow = MutableStateFlow(null)
        every { viewModel.uiState } returns uiStateFlow
    }

    @Test
    fun miniPlayerLayout_hidesMiniPlayer_whenShowMiniPlayerIsFalse() {
        uiStateFlow.value = dummyState

        composeTestRule.setContent {
            MiniPlayerLayout(
                showMiniPlayer = false,
                onMiniPlayerClick = {},
                viewModel = viewModel
            ) {
                Text("Main Content")
            }
        }

        composeTestRule.onNodeWithText("Main Content").assertExists()
        composeTestRule.onNodeWithText("Narnia").assertDoesNotExist()
    }

    @Test
    fun miniPlayerLayout_hidesMiniPlayer_whenUiStateIsNull() {
        uiStateFlow.value = null

        composeTestRule.setContent {
            MiniPlayerLayout(
                showMiniPlayer = true,
                onMiniPlayerClick = {},
                viewModel = viewModel
            ) {
                Text("Main Content")
            }
        }

        composeTestRule.onNodeWithText("Main Content").assertExists()
        composeTestRule.onNodeWithText("Narnia").assertDoesNotExist()
    }

    @Test
    fun miniPlayerLayout_showsMiniPlayer_whenShowMiniPlayerIsTrueAndUiStateIsSet() {
        uiStateFlow.value = dummyState

        composeTestRule.setContent {
            MiniPlayerLayout(
                showMiniPlayer = true,
                onMiniPlayerClick = {},
                viewModel = viewModel
            ) {
                Text("Main Content")
            }
        }

        composeTestRule.onNodeWithText("Main Content").assertExists()
        composeTestRule.onNodeWithText("Narnia").assertExists()
        composeTestRule.onNodeWithText("C.S. Lewis").assertExists()
    }

    @Test
    fun miniPlayerView_triggersPlayPauseToggle_onClick() {
        uiStateFlow.value = dummyState

        composeTestRule.setContent {
            MiniPlayerLayout(
                showMiniPlayer = true,
                onMiniPlayerClick = {},
                viewModel = viewModel
            ) {
                Text("Content")
            }
        }

        // Play/Pause button initially shows "Play"
        composeTestRule.onNodeWithContentDescription("Play").performClick()
        verify { viewModel.togglePlayPause() }

        // Update state to playing
        uiStateFlow.value = dummyState.copy(isPlaying = true)
        composeTestRule.onNodeWithContentDescription("Pause").performClick()
        verify { viewModel.togglePlayPause() }
    }

    @Test
    fun miniPlayerView_triggersDismiss_onClick() {
        uiStateFlow.value = dummyState

        composeTestRule.setContent {
            MiniPlayerLayout(
                showMiniPlayer = true,
                onMiniPlayerClick = {},
                viewModel = viewModel
            ) {
                Text("Content")
            }
        }

        composeTestRule.onNodeWithContentDescription("Dismiss").performClick()
        verify { viewModel.dismiss() }
    }

    @Test
    fun miniPlayerView_triggersNavigation_onClick() {
        uiStateFlow.value = dummyState
        var clicked = false

        composeTestRule.setContent {
            MiniPlayerLayout(
                showMiniPlayer = true,
                onMiniPlayerClick = { clicked = true },
                viewModel = viewModel
            ) {
                Text("Content")
            }
        }

        // Clicking the container (we can target by text "Narnia")
        composeTestRule.onNodeWithText("Narnia").performClick()
        assert(clicked)
    }
}
