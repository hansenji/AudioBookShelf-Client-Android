package dev.vikingsen.skald.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vikingsen.skald.core.model.Book
import dev.vikingsen.skald.core.model.Playlist
import dev.vikingsen.skald.core.model.PlaybackProgress

import dev.vikingsen.skald.core.model.DownloadStatus
import dev.vikingsen.skald.core.model.DownloadStatusState
import dev.vikingsen.skald.core.model.formatDuration
import dev.vikingsen.skald.core.model.formatPosition
import dev.vikingsen.skald.core.player.PlayerManager
import dev.vikingsen.skald.domain.repository.AudiobookshelfRepository
import dev.vikingsen.skald.domain.repository.SettingsRepository
import dev.vikingsen.skald.domain.usecase.GetBookWithProgressUseCase
import dev.vikingsen.skald.domain.usecase.FetchBookDetailsUseCase
import dev.vikingsen.skald.domain.usecase.DownloadAudioFileUseCase
import dev.vikingsen.skald.domain.usecase.GetMiniPlayerStateUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ChapterUiModel(
    val title: String,
    val start: Double,
    val end: Double,
    val startText: String,
    val durationText: String
)

data class SeriesUiModel(
    val id: String,
    val name: String,
    val sequence: String?
)

data class PlaylistUiModel(
    val id: String,
    val name: String
)

data class CollectionUiModel(
    val id: String,
    val name: String
)

data class BookDetailUiModel(
    val id: String,
    val libraryId: String,
    val title: String,
    val author: String,
    val narrator: String,
    val duration: Double,
    val durationText: String,
    val coverUrl: String,
    val authorizationHeader: String?,
    val isDownloaded: Boolean,
    val description: String,
    val chapters: List<ChapterUiModel>,
    val progress: PlaybackProgressUiModel?,
    val progressLeftText: String?,
    val series: SeriesUiModel? = null,
    val playlists: List<PlaylistUiModel> = emptyList(),
    val collections: List<CollectionUiModel> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModel(
    private val getBookWithProgressUseCase: GetBookWithProgressUseCase,
    private val fetchBookDetailsUseCase: FetchBookDetailsUseCase,
    private val downloadAudioFileUseCase: DownloadAudioFileUseCase,
    private val bookMenuActionUtil: BookMenuActionUtil,
    private val repository: AudiobookshelfRepository,
    private val settingsRepository: SettingsRepository,
    private val playerManager: PlayerManager,
    private val getMiniPlayerStateUseCase: GetMiniPlayerStateUseCase
) : ViewModel() {
    val bookId: StateFlow<String?>
        field = MutableStateFlow<String?>(null)

    val bookAndProgress: StateFlow<Pair<Book?, PlaybackProgress?>?> = bookId
        .filterNotNull()
        .flatMapLatest { id -> getBookWithProgressUseCase(id) }
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5_000), initialValue = null)

    val bookSeries: Flow<SeriesUiModel?> = bookAndProgress
        .map { it?.first?.seriesId }
        .distinctUntilChanged()
        .flatMapLatest { seriesId ->
            if (seriesId != null) {
                repository.getSeriesByIdFlow(seriesId).map { series ->
                    series?.let {
                        SeriesUiModel(
                            id = it.id,
                            name = it.name,
                            sequence = bookAndProgress.value?.first?.seriesSequence
                        )
                    }
                }
            } else {
                flowOf(null)
            }
        }

    val bookPlaylists: Flow<List<PlaylistUiModel>> = bookId
        .flatMapLatest { id ->
            if (id != null) {
                repository.getPlaylistsContainingBookFlow(id).map { list ->
                    list.map { PlaylistUiModel(id = it.id, name = it.name) }
                }
            } else {
                flowOf(emptyList())
            }
        }

    val bookCollections: Flow<List<CollectionUiModel>> = bookId
        .flatMapLatest { id ->
            if (id != null) {
                repository.getCollectionsContainingBookFlow(id).map { list ->
                    list.map { CollectionUiModel(id = it.id, name = it.name) }
                }
            } else {
                flowOf(emptyList())
            }
        }

    val bookDetail: StateFlow<BookDetailUiModel?> = combine(
        bookAndProgress,
        bookSeries,
        bookPlaylists,
        bookCollections
    ) { pair, series, playlists, collections ->
        val book = pair?.first ?: return@combine null
        val progress = pair.second
        
        val serverUrl = settingsRepository.getServerUrl() ?: ""
        val token = settingsRepository.getToken() ?: ""
        
        val coverUrl = if (!book.coverPath.isNullOrEmpty()) book.coverPath!!
                       else "${serverUrl.trimEnd('/')}/api/items/${book.id}/cover"
        val authHeader = if (!book.coverPath.isNullOrEmpty()) null
                         else "Bearer $token"
                         
        val progressLeftText = progress?.let {
            val left = book.duration - it.currentTime
            formatDuration(left)
        }

        BookDetailUiModel(
            id = book.id,
            libraryId = book.libraryId,
            title = book.title,
            author = book.author,
            narrator = book.narrator,
            duration = book.duration,
            durationText = formatDuration(book.duration),
            coverUrl = coverUrl,
            authorizationHeader = authHeader,
            isDownloaded = book.isDownloaded,
            description = book.description,
            chapters = book.chapters.mapIndexed { index, chapter ->
                ChapterUiModel(
                    title = chapter.title.ifEmpty { "Chapter ${index + 1}" },
                    start = chapter.start,
                    end = chapter.end,
                    startText = formatPosition(chapter.start),
                    durationText = formatDuration(chapter.end - chapter.start)
                )
            },
            progress = progress?.let {
                PlaybackProgressUiModel(
                    progress = it.progress,
                    isFinished = it.isFinished,
                    currentTime = it.currentTime,
                    lastUpdated = it.lastUpdated
                )
            },
            progressLeftText = progressLeftText,
            series = series,
            playlists = playlists,
            collections = collections
        )
    }
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5_000), initialValue = null)

    val showMiniPlayer: StateFlow<Boolean> = getMiniPlayerStateUseCase()
        .map { it != null }
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5_000), initialValue = false)

    val isLoading: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val error: StateFlow<String?>
        field = MutableStateFlow<String?>(null)

    val isDownloading: StateFlow<Boolean> = bookAndProgress
        .map { pair ->
            val book = pair?.first
            book?.audioFiles?.any { it.downloadStatus == DownloadStatus.DOWNLOADING } ?: false
        }
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5_000), initialValue = false)

    val downloadProgress: StateFlow<Float> = bookId
        .filterNotNull()
        .flatMapLatest { id -> repository.getBookDownloadFlow(id) }
        .map { state ->
            when (state) {
                is DownloadStatusState.Progress -> state.progress
                is DownloadStatusState.Completed -> 1f
                is DownloadStatusState.Error -> 0f
            }
        }
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5_000), initialValue = 0f)

    val downloadingFileName: StateFlow<String?> = bookAndProgress
        .map { pair ->
            val book = pair?.first
            book?.audioFiles?.find { it.downloadStatus == DownloadStatus.DOWNLOADING }?.filename
        }
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5_000), initialValue = null)

    val downloadError: StateFlow<String?>
        field = MutableStateFlow<String?>(null)

    fun setBookId(id: String) {
        if (bookId.value == id) return
        bookId.value = id
        fetchBookDetails(id)
    }

    private fun fetchBookDetails(id: String) {
        isLoading.value = true
        error.value = null
        viewModelScope.launch {
            val result = fetchBookDetailsUseCase(id)
            if (result.isFailure) {
                error.value = result.exceptionOrNull()?.message ?: "Failed to load details"
            }
            isLoading.value = false
        }
    }

    fun refresh() {
        val id = bookId.value ?: return
        isLoading.value = true
        error.value = null
        viewModelScope.launch {
            val result = fetchBookDetailsUseCase(id, forceRefresh = true)
            if (result.isFailure) {
                error.value = result.exceptionOrNull()?.message ?: "Failed to refresh details"
            }
            isLoading.value = false
        }
    }

    fun downloadBook() {
        val book = bookAndProgress.value?.first ?: return
        viewModelScope.launch {
            downloadError.value = null
            
            val filesToDownload = book.audioFiles.filter { it.downloadStatus != DownloadStatus.COMPLETED }
            if (filesToDownload.isEmpty()) return@launch
            
            val result = downloadAudioFileUseCase(book.id)
            if (result.isFailure) {
                downloadError.value = result.exceptionOrNull()?.message ?: "Failed to start download"
            }
        }
    }

    fun deleteDownloadedBook() {
        val book = bookAndProgress.value?.first ?: return
        viewModelScope.launch {
            isLoading.value = true
            downloadError.value = null
            val result = bookMenuActionUtil.deleteDownload(book.id)
            if (result.isFailure) {
                downloadError.value = result.exceptionOrNull()?.message ?: "Failed to delete downloaded files"
            }
            isLoading.value = false
        }
    }

    fun playBook(startPosition: Double) {
        val book = bookAndProgress.value?.first ?: return
        playerManager.playBook(book, startPosition)
    }

    val serverUrl: String = settingsRepository.getServerUrl() ?: ""

    fun toggleFinished() {
        val pair = bookAndProgress.value ?: return
        val book = pair.first ?: return
        val progress = pair.second
        val isFinished = progress?.isFinished ?: false
        viewModelScope.launch {
            isLoading.value = true
            val result = bookMenuActionUtil.toggleFinished(book.id, isFinished)
            if (result.isFailure) {
                error.value = result.exceptionOrNull()?.message ?: "Failed to update finished status"
            }
            isLoading.value = false
        }
    }

    fun discardProgress() {
        val pair = bookAndProgress.value ?: return
        val book = pair.first ?: return
        viewModelScope.launch {
            isLoading.value = true
            val result = bookMenuActionUtil.discardProgress(book.id)
            if (result.isFailure) {
                error.value = result.exceptionOrNull()?.message ?: "Failed to discard progress"
            }
            isLoading.value = false
        }
    }
}

