package dev.vikingsen.skald.feature.player

import dev.vikingsen.skald.core.model.Book
import dev.vikingsen.skald.core.model.Chapter
import dev.vikingsen.skald.core.model.PlaybackConstants
import dev.vikingsen.skald.core.player.PlayerManager
import dev.vikingsen.skald.domain.fakes.FakeSettingsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var playerManager: PlayerManager
    private lateinit var settingsRepository: FakeSettingsRepository
    private lateinit var viewModel: PlayerViewModel

    private lateinit var currentBookFlow: MutableStateFlow<Book?>
    private lateinit var isPlayingFlow: MutableStateFlow<Boolean>
    private lateinit var currentPositionFlow: MutableStateFlow<Double>
    private lateinit var durationFlow: MutableStateFlow<Double>
    private lateinit var currentChapterFlow: MutableStateFlow<Chapter?>
    private lateinit var playbackSpeedFlow: MutableStateFlow<Float>
    private lateinit var sleepTimerRemainingFlow: MutableStateFlow<Long>
    private lateinit var isLoadingFlow: MutableStateFlow<Boolean>

    private val dummyBook = Book(
        id = "book-123",
        libraryId = "lib-1",
        title = "The Hobbit",
        author = "J.R.R. Tolkien",
        narrator = "Rob Inglis",
        description = "A great adventure",
        duration = 3600.0,
        coverPath = null,
        isDownloaded = false,
        audioFiles = emptyList(),
        chapters = listOf(
            Chapter(0.0, 1800.0, "An Unexpected Party"),
            Chapter(1800.0, 3600.0, "Roast Mutton")
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        playerManager = mockk(relaxed = true)
        settingsRepository = FakeSettingsRepository()

        currentBookFlow = MutableStateFlow(null)
        isPlayingFlow = MutableStateFlow(false)
        currentPositionFlow = MutableStateFlow(0.0)
        durationFlow = MutableStateFlow(0.0)
        currentChapterFlow = MutableStateFlow(null)
        playbackSpeedFlow = MutableStateFlow(1.0f)
        sleepTimerRemainingFlow = MutableStateFlow(0L)
        isLoadingFlow = MutableStateFlow(false)

        every { playerManager.currentBook } returns currentBookFlow
        every { playerManager.isPlaying } returns isPlayingFlow
        every { playerManager.currentPosition } returns currentPositionFlow
        every { playerManager.duration } returns durationFlow
        every { playerManager.currentChapter } returns currentChapterFlow
        every { playerManager.playbackSpeed } returns playbackSpeedFlow
        every { playerManager.sleepTimerRemaining } returns sleepTimerRemainingFlow
        every { playerManager.isLoading } returns isLoadingFlow

        viewModel = PlayerViewModel(playerManager, settingsRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_isNull_whenNoBookActive() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        currentBookFlow.value = null
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value)
    }

    @Test
    fun uiState_isCorrectlyPopulated_whenBookIsActive() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        settingsRepository.saveConnectionDetails("https://my-server.com", "viking", "fake-token")
        settingsRepository.saveSkipForwardDuration(30)
        settingsRepository.saveSkipBackwardDuration(15)

        currentBookFlow.value = dummyBook
        isPlayingFlow.value = true
        currentPositionFlow.value = 45.0
        durationFlow.value = 3600.0
        currentChapterFlow.value = dummyBook.chapters[0]
        playbackSpeedFlow.value = 1.2f
        sleepTimerRemainingFlow.value = 90000L // 1:30

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state)
        assertEquals("The Hobbit", state!!.title)
        assertEquals("J.R.R. Tolkien", state.author)
        assertEquals("https://my-server.com/api/items/book-123/cover", state.coverUrl)
        assertEquals("Bearer fake-token", state.authorizationHeader)
        assertTrue(state.isPlaying)
        assertEquals(45.0, state.currentPosition, 0.001)
        assertEquals("0:45", state.currentPositionText)
        assertEquals("1:00:00", state.durationText)
        assertEquals("-59:15", state.timeRemainingText)
        assertEquals("An Unexpected Party", state.currentChapterTitle)
        assertEquals(1.2f, state.playbackSpeed, 0.001f)
        assertEquals(90000L, state.sleepTimerRemaining)
        assertEquals("1:30", state.sleepTimerText)
        assertEquals(30, state.skipForwardDuration)
        assertEquals(15, state.skipBackwardDuration)
        assertFalse(state.isLoading)

        assertEquals(2, state.chapters.size)
        assertEquals("An Unexpected Party", state.chapters[0].title)
        assertEquals("0:00", state.chapters[0].startText)
        assertEquals("30m", state.chapters[0].durationText)
    }

    @Test
    fun play_delegatesToPlayerManager() {
        viewModel.play()
        verify { playerManager.play() }
    }

    @Test
    fun pause_delegatesToPlayerManager() {
        viewModel.pause()
        verify { playerManager.pause() }
    }

    @Test
    fun seekTo_delegatesToPlayerManager() {
        viewModel.seekTo(123.45)
        verify { playerManager.seekTo(123.45) }
    }

    @Test
    fun skipForward_seeksToSum_whenUnderDuration() {
        settingsRepository.saveSkipForwardDuration(30)
        currentPositionFlow.value = 100.0
        durationFlow.value = 200.0

        viewModel.skipForward()

        verify { playerManager.seekTo(130.0) }
    }

    @Test
    fun skipForward_seeksToEndAndPauses_whenExceedsDuration() {
        settingsRepository.saveSkipForwardDuration(30)
        currentPositionFlow.value = 180.0
        durationFlow.value = 200.0

        viewModel.skipForward()

        verify { playerManager.seekTo(200.0) }
        verify { playerManager.pause() }
    }

    @Test
    fun skipBackward_seeksToDifference_whenAboveZero() {
        settingsRepository.saveSkipBackwardDuration(15)
        currentPositionFlow.value = 100.0

        viewModel.skipBackward()

        verify { playerManager.seekTo(85.0) }
    }

    @Test
    fun skipBackward_seeksToZero_whenExceedsStart() {
        settingsRepository.saveSkipBackwardDuration(15)
        currentPositionFlow.value = 10.0

        viewModel.skipBackward()

        verify { playerManager.seekTo(0.0) }
    }

    @Test
    fun setPlaybackSpeed_delegatesToPlayerManager() {
        viewModel.setPlaybackSpeed(1.75f)
        verify { playerManager.setPlaybackSpeed(1.75f) }
    }

    @Test
    fun cyclePlaybackSpeed_cyclesThroughSpeeds() {
        // First test speed is 1.0f. Next speed in PLAYBACK_SPEEDS (0.5f, 0.7f, 0.8f, 0.9f, 1.0f, 1.1f, 1.2f, 1.3f, 1.4f, 1.5f, 1.6f, 1.7f, 1.8f, 2.0f) should be 1.1f
        playbackSpeedFlow.value = 1.0f
        viewModel.cyclePlaybackSpeed()
        verify { playerManager.setPlaybackSpeed(1.1f) }

        // If at the end (2.0f), cycles back to 0.5f
        playbackSpeedFlow.value = 2.0f
        viewModel.cyclePlaybackSpeed()
        verify { playerManager.setPlaybackSpeed(0.5f) }
    }

    @Test
    fun sleepTimerControls_delegateToPlayerManager() {
        viewModel.setSleepTimer(30)
        verify { playerManager.setSleepTimer(30) }

        viewModel.setSleepTimerEndOfChapter()
        verify { playerManager.setSleepTimerEndOfChapter() }

        viewModel.cancelSleepTimer()
        verify { playerManager.cancelSleepTimer() }
    }

    @Test
    fun skipDurations_saveToSettings_andTriggerStateRefresh() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        currentBookFlow.value = dummyBook
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setSkipForwardDuration(60)
        assertEquals(60, settingsRepository.getSkipForwardDuration())

        viewModel.setSkipBackwardDuration(30)
        assertEquals(30, settingsRepository.getSkipBackwardDuration())
    }

    @Test
    fun skipToNextChapter_seeksToNextChapterStart() {
        currentBookFlow.value = dummyBook
        currentPositionFlow.value = 500.0 // In first chapter, next starts at 1800.0

        viewModel.skipToNextChapter()

        verify { playerManager.seekTo(1800.0) }
    }

    @Test
    fun skipToNextChapter_seeksToEnd_whenOnLastChapter() {
        currentBookFlow.value = dummyBook
        currentPositionFlow.value = 2000.0 // In second chapter, duration is 3600.0
        durationFlow.value = 3600.0

        viewModel.skipToNextChapter()

        verify { playerManager.seekTo(3600.0) }
    }

    @Test
    fun skipToPreviousChapter_seeksToCurrentChapterStart_whenElapsedMoreThanFiveSeconds() {
        currentBookFlow.value = dummyBook
        // In Roast Mutton chapter (1800.0 to 3600.0). Current position is 1810.0 (10s elapsed in chapter)
        currentPositionFlow.value = 1810.0

        viewModel.skipToPreviousChapter()

        verify { playerManager.seekTo(1800.0) }
    }

    @Test
    fun skipToPreviousChapter_seeksToPrevChapterStart_whenElapsedLessThanFiveSeconds() {
        currentBookFlow.value = dummyBook
        // In Roast Mutton chapter. Current position is 1803.0 (3s elapsed)
        currentPositionFlow.value = 1803.0

        viewModel.skipToPreviousChapter()

        verify { playerManager.seekTo(0.0) } // Prev chapter starts at 0.0
    }
}
