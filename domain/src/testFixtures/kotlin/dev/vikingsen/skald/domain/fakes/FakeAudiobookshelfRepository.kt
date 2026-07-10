package dev.vikingsen.skald.domain.fakes

import androidx.paging.PagingData
import dev.vikingsen.skald.core.model.Author
import dev.vikingsen.skald.core.model.Book
import dev.vikingsen.skald.core.model.BookCollection
import dev.vikingsen.skald.core.model.BookWithProgress
import dev.vikingsen.skald.core.model.DownloadStatusState
import dev.vikingsen.skald.core.model.HomeShelf
import dev.vikingsen.skald.core.model.Library
import dev.vikingsen.skald.core.model.LoggedUser
import dev.vikingsen.skald.core.model.PlaybackProgress
import dev.vikingsen.skald.core.model.Playlist
import dev.vikingsen.skald.core.model.PlaylistItem
import dev.vikingsen.skald.core.model.ReadStatusFilter
import dev.vikingsen.skald.core.model.Series
import dev.vikingsen.skald.core.model.SortOption
import dev.vikingsen.skald.domain.repository.AudiobookshelfRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

open class FakeAudiobookshelfRepository : AudiobookshelfRepository {
    var loginResult: Result<LoggedUser> = Result.failure(Exception("Not configured"))
    var fetchLibrariesResult: Result<List<Library>> = Result.failure(Exception("Not configured"))
    var syncLibraryBooksResult: Result<Unit> = Result.success(Unit)

    var syncLibraryBooksCalled = false
    var syncGlobalProgressCalled = false
    var clearLocalDataCalled = false
    var deleteOrphanedDownloadsCalled = false

    override suspend fun login(url: String, user: String, pass: String): Result<LoggedUser> {
        return loginResult
    }

    override suspend fun fetchLibraries(): Result<List<Library>> {
        return fetchLibrariesResult
    }

    override suspend fun syncLibraryBooks(libraryId: String, forceRefresh: Boolean): Result<Unit> {
        syncLibraryBooksCalled = true
        return syncLibraryBooksResult
    }

    // Stub remaining 50 methods with defaults or exceptions
    override suspend fun syncLibrarySeries(libraryId: String, forceRefresh: Boolean): Result<Unit> = Result.success(Unit)
    override fun getHomeShelvesFlow(libraryId: String): Flow<List<HomeShelf>> = emptyFlow()
    override suspend fun syncHomeShelves(libraryId: String, forceRefresh: Boolean): Result<Unit> = Result.success(Unit)
    override fun getBooksFlow(): Flow<List<Book>> = emptyFlow()
    override fun getSeriesFlow(libraryId: String): Flow<List<Series>> = emptyFlow()
    override suspend fun getSeriesById(seriesId: String): Series? = null
    override fun getBooksForSeriesFlow(seriesId: String): Flow<List<BookWithProgress>> = emptyFlow()
    override fun getBooksWithProgressForLibraryFlow(libraryId: String): Flow<List<BookWithProgress>> = emptyFlow()
    override fun getAllProgressFlow(): Flow<List<PlaybackProgress>> = emptyFlow()
    override fun getBookWithProgressFlow(bookId: String): Flow<Pair<Book?, PlaybackProgress?>> = emptyFlow()
    override suspend fun fetchBookDetails(bookId: String, forceRefresh: Boolean): Result<Book> = Result.failure(Exception("Not stubbed"))
    override suspend fun enqueueBookDownloads(bookId: String): Result<Unit> = Result.success(Unit)
    override fun getBookDownloadFlow(bookId: String): Flow<DownloadStatusState> = emptyFlow()
    override suspend fun saveLocalProgress(bookId: String, currentTime: Double, totalDuration: Double) {}
    override suspend fun startPlaybackSession(bookId: String, deviceId: String, deviceName: String): Result<String> = Result.success("fake-session")
    override suspend fun syncPlaybackProgress(sessionId: String, timeListened: Double, currentTime: Double): Result<Unit> = Result.success(Unit)
    override suspend fun syncStaticProgress(bookId: String, currentTime: Double, progress: Float, isFinished: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun syncGlobalProgress(forceRefresh: Boolean): Result<Unit> {
        syncGlobalProgressCalled = true
        return Result.success(Unit)
    }
    override suspend fun deleteLocalBookFiles(bookId: String): Result<Unit> = Result.success(Unit)
    override suspend fun clearLocalData() {
        clearLocalDataCalled = true
    }
    override fun getBooksPaged(libraryId: String, query: String, filter: ReadStatusFilter, downloadedOnly: Boolean, sortBy: SortOption): Flow<PagingData<BookWithProgress>> = emptyFlow()
    override suspend fun getCachedLibraries(): List<Library> = emptyList()
    override suspend fun scanAndRelinkDownloads(): Result<Unit> = Result.success(Unit)
    override suspend fun getOrphanedDownloadsSize(): Long = 0L
    override suspend fun deleteOrphanedDownloads(): Result<Unit> {
        deleteOrphanedDownloadsCalled = true
        return Result.success(Unit)
    }
    override fun getAuthorsFlow(libraryId: String): Flow<List<Author>> = emptyFlow()
    override suspend fun syncLibraryAuthors(libraryId: String, forceRefresh: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun getAuthorDetails(authorId: String, forceRefresh: Boolean): Result<Author> = Result.failure(Exception("Not stubbed"))
    override fun getBooksForAuthorFlow(authorId: String): Flow<List<BookWithProgress>> = emptyFlow()
    override fun getCollectionsFlow(libraryId: String): Flow<List<BookCollection>> = emptyFlow()
    override suspend fun syncLibraryCollections(libraryId: String, forceRefresh: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun getCollectionDetails(collectionId: String, forceRefresh: Boolean): Result<BookCollection> = Result.failure(Exception("Not stubbed"))
    override fun getBooksForCollectionFlow(collectionId: String): Flow<List<BookWithProgress>> = emptyFlow()
    override fun getPlaylistsFlow(): Flow<List<Playlist>> = emptyFlow()
    override suspend fun syncPlaylists(forceRefresh: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun getPlaylistDetails(playlistId: String, forceRefresh: Boolean): Result<Playlist> = Result.failure(Exception("Not stubbed"))
    override suspend fun updatePlaylistItems(playlistId: String, items: List<PlaylistItem>): Result<Unit> = Result.success(Unit)
    override suspend fun updatePlaybackFinished(bookId: String, isFinished: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun discardProgress(bookId: String): Result<Unit> = Result.success(Unit)
    override suspend fun addBookToPlaylist(playlistId: String, bookId: String): Result<Unit> = Result.success(Unit)
    override suspend fun createPlaylistWithBook(name: String, libraryId: String, bookId: String): Result<Unit> = Result.success(Unit)
    override suspend fun removePlaylistItem(playlistId: String, bookId: String): Result<Unit> = Result.success(Unit)
    override fun getSeriesByIdFlow(seriesId: String): Flow<Series?> = emptyFlow()
    override fun getPlaylistsContainingBookFlow(bookId: String): Flow<List<Playlist>> = emptyFlow()
    override fun getCollectionsContainingBookFlow(bookId: String): Flow<List<BookCollection>> = emptyFlow()
}
