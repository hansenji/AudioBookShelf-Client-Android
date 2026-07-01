package dev.vikingsen.skald.feature.miniplayer

import dev.vikingsen.skald.core.model.Book
import dev.vikingsen.skald.core.model.MiniPlayerState
import dev.vikingsen.skald.core.player.PlayerManager
import dev.vikingsen.skald.domain.fakes.FakeSettingsRepository
import dev.vikingsen.skald.domain.usecase.GetMiniPlayerStateUseCase
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
class MiniPlayerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var playerManager: PlayerManager
    private lateinit var settingsRepository: FakeSettingsRepository
    private lateinit var getMiniPlayerStateUseCase: GetMiniPlayerStateUseCase
    private lateinit var viewModel: MiniPlayerViewModel

    private lateinit var currentBookFlow: MutableStateFlow<Book?>
    private lateinit var isPlayingFlow: MutableStateFlow<Boolean>
    private lateinit var currentPositionFlow: MutableStateFlow<Double>
    private lateinit var durationFlow: MutableStateFlow<Double>
    private lateinit var isLoadingFlow: MutableStateFlow<Boolean>

    private val dummyBook = Book(
        id = "book-456",
        libraryId = "lib-1",
        title = "Narnia",
        author = "C.S. Lewis",
        narrator = "Narrator",
        description = "A classic tale",
        duration = 1000.0,
        coverPath = "/local/path/cover.jpg",
        isDownloaded = true,
        audioFiles = emptyList(),
        chapters = emptyList()
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
        isLoadingFlow = MutableStateFlow(false)

        every { playerManager.currentBook } returns currentBookFlow
        every { playerManager.isPlaying } returns isPlayingFlow
        every { playerManager.currentPosition } returns currentPositionFlow
        every { playerManager.duration } returns durationFlow
        every { playerManager.isLoading } returns isLoadingFlow

        getMiniPlayerStateUseCase = GetMiniPlayerStateUseCase(playerManager, settingsRepository)
        viewModel = MiniPlayerViewModel(playerManager, getMiniPlayerStateUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_isNull_initially() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        assertNull(viewModel.uiState.value)
    }

    @Test
    fun uiState_isCorrectlyMapped_whenBookIsActive() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        currentBookFlow.value = dummyBook
        isPlayingFlow.value = true
        currentPositionFlow.value = 250.0
        durationFlow.value = 1000.0
        isLoadingFlow.value = false

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state)
        assertEquals("book-456", state!!.bookId)
        assertEquals("Narnia", state.title)
        assertEquals("C.S. Lewis", state.author)
        assertEquals("/local/path/cover.jpg", state.coverUrl)
        assertTrue(state.isPlaying)
        assertEquals(0.25f, state.progress, 0.001f)
        assertFalse(state.isLoading)
    }

    @Test
    fun togglePlayPause_callsPause_whenPlaying() {
        isPlayingFlow.value = true
        viewModel.togglePlayPause()
        verify { playerManager.pause() }
    }

    @Test
    fun togglePlayPause_callsPlay_whenPaused() {
        isPlayingFlow.value = false
        viewModel.togglePlayPause()
        verify { playerManager.play() }
    }

    @Test
    fun dismiss_callsStop() {
        viewModel.dismiss()
        verify { playerManager.stop() }
    }
}
