package dev.vikingsen.skald.feature.player

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1080dp-h2400dp-xxhdpi")
class PlayerScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var viewModel: PlayerViewModel
    private lateinit var uiStateFlow: MutableStateFlow<PlayerUiState?>

    private val dummyChapters = listOf(
        ChapterUiModel("Chapter 1", 0.0, 10.0, "00:00", "00:10"),
        ChapterUiModel("Chapter 2", 10.0, 20.0, "00:10", "00:10")
    )

    private val dummyState = PlayerUiState(
        title = "Test Audiobook",
        author = "Test Author",
        coverUrl = "https://my-server.com/cover.jpg",
        authorizationHeader = "Bearer token",
        isPlaying = false,
        currentPosition = 5.0,
        currentPositionText = "00:05",
        duration = 20.0,
        durationText = "00:20",
        timeRemainingText = "-00:15",
        currentChapterTitle = "Chapter 1",
        playbackSpeed = 1.0f,
        sleepTimerRemaining = 0L,
        sleepTimerText = "Timer",
        chapters = dummyChapters,
        skipForwardDuration = 30,
        skipBackwardDuration = 10,
        isLoading = false
    )

    @Before
    fun setUp() {
        viewModel = mockk(relaxed = true)
        uiStateFlow = MutableStateFlow(null)
        every { viewModel.uiState } returns uiStateFlow
    }

    @Test
    fun playerScreen_showsNoAudiobookActive_whenStateIsNull() {
        uiStateFlow.value = null

        composeTestRule.setContent {
            PlayerScreen(
                onBackClick = {},
                viewModel = viewModel
            )
        }

        composeTestRule.onNodeWithText("No audiobook active.").assertExists()
    }

    @Test
    fun playerScreen_rendersBookInfoAndChapter() {
        uiStateFlow.value = dummyState

        composeTestRule.setContent {
            PlayerScreen(
                onBackClick = {},
                viewModel = viewModel
            )
        }

        // Verify book info is shown
        composeTestRule.onNodeWithText("Test Audiobook").assertExists()
        composeTestRule.onNodeWithText("Test Author").assertExists()
        composeTestRule.onNodeWithText("Chapter 1").assertExists()
        
        // Verify time labels
        composeTestRule.onNodeWithText("00:05").assertExists()
        composeTestRule.onNodeWithText("-00:15").assertExists()
    }

    @Test
    fun playerScreen_clickingPlayPause_triggersViewModel() {
        uiStateFlow.value = dummyState

        composeTestRule.setContent {
            PlayerScreen(
                onBackClick = {},
                viewModel = viewModel
            )
        }

        // Initially paused, click play
        composeTestRule.onNodeWithContentDescription("Play").performClick()
        verify { viewModel.play() }

        // Change state to playing, click pause
        uiStateFlow.value = dummyState.copy(isPlaying = true)
        composeTestRule.onNodeWithContentDescription("Pause").performClick()
        verify { viewModel.pause() }
    }

    @Test
    fun playerScreen_clickingSkipButtons_triggersViewModel() {
        uiStateFlow.value = dummyState

        composeTestRule.setContent {
            PlayerScreen(
                onBackClick = {},
                viewModel = viewModel
            )
        }

        composeTestRule.onNodeWithContentDescription("Skip Forward 30s").performClick()
        verify { viewModel.skipForward() }

        composeTestRule.onNodeWithContentDescription("Skip Back 10s").performClick()
        verify { viewModel.skipBackward() }
    }

    @Test
    fun playerScreen_clickingChapterButtons_triggersViewModel() {
        uiStateFlow.value = dummyState

        composeTestRule.setContent {
            PlayerScreen(
                onBackClick = {},
                viewModel = viewModel
            )
        }

        composeTestRule.onNodeWithContentDescription("Next Chapter").performClick()
        verify { viewModel.skipToNextChapter() }

        composeTestRule.onNodeWithContentDescription("Previous Chapter").performClick()
        verify { viewModel.skipToPreviousChapter() }
    }

    @Test
    fun playerScreen_showsSpeedDialogAndChangesSpeed() {
        uiStateFlow.value = dummyState

        composeTestRule.setContent {
            PlayerScreen(
                onBackClick = {},
                viewModel = viewModel
            )
        }

        // Open Dialog
        composeTestRule.onNodeWithContentDescription("Playback Speed").performClick()
        composeTestRule.waitForIdle()
        
        // Check speed dialog title exists
        composeTestRule.onNodeWithText("Playback Speed").assertExists()
        
        // Click Increase/Decrease buttons
        composeTestRule.onNodeWithContentDescription("Increase Speed").performClick()
        verify { viewModel.setPlaybackSpeed(1.1f) }

        composeTestRule.onNodeWithContentDescription("Decrease Speed").performClick()
        composeTestRule.onNodeWithContentDescription("Decrease Speed").performClick()
        verify { viewModel.setPlaybackSpeed(0.9f) }
    }
}
