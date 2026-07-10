package dev.vikingsen.skald.domain.repository

import dev.vikingsen.skald.core.model.Book
import dev.vikingsen.skald.core.model.Library
import dev.vikingsen.skald.core.model.LoggedUser
import dev.vikingsen.skald.core.model.PlaybackProgress
import dev.vikingsen.skald.core.model.AudioFile
import dev.vikingsen.skald.core.model.DownloadStatusState
import kotlinx.coroutines.flow.Flow
import androidx.paging.PagingData
import dev.vikingsen.skald.core.model.BookWithProgress
import dev.vikingsen.skald.core.model.ReadStatusFilter
import dev.vikingsen.skald.core.model.SortOption
import dev.vikingsen.skald.core.model.HomeShelf
import dev.vikingsen.skald.core.model.Series
import dev.vikingsen.skald.core.model.Author
import dev.vikingsen.skald.core.model.BookCollection
import dev.vikingsen.skald.core.model.Playlist
import dev.vikingsen.skald.core.model.PlaylistItem

interface AudiobookshelfRepository {
    suspend fun login(url: String, user: String, pass: String): Result<LoggedUser>
    suspend fun fetchLibraries(): Result<List<Library>>
    suspend fun syncLibraryBooks(libraryId: String, forceRefresh: Boolean = false): Result<Unit>
    suspend fun syncLibrarySeries(libraryId: String, forceRefresh: Boolean = false): Result<Unit>
    fun getHomeShelvesFlow(libraryId: String): Flow<List<HomeShelf>>
    suspend fun syncHomeShelves(libraryId: String, forceRefresh: Boolean = false): Result<Unit>
    fun getBooksFlow(): Flow<List<Book>>
    fun getSeriesFlow(libraryId: String): Flow<List<Series>>
    suspend fun getSeriesById(seriesId: String): Series?
    fun getBooksForSeriesFlow(seriesId: String): Flow<List<BookWithProgress>>
    fun getBooksWithProgressForLibraryFlow(libraryId: String): Flow<List<BookWithProgress>>
    fun getAllProgressFlow(): Flow<List<PlaybackProgress>>
    fun getBookWithProgressFlow(bookId: String): Flow<Pair<Book?, PlaybackProgress?>>
    suspend fun fetchBookDetails(bookId: String, forceRefresh: Boolean = false): Result<Book>
    suspend fun enqueueBookDownloads(bookId: String): Result<Unit>
    fun getBookDownloadFlow(bookId: String): Flow<DownloadStatusState>
    suspend fun saveLocalProgress(bookId: String, currentTime: Double, totalDuration: Double)
    suspend fun startPlaybackSession(bookId: String, deviceId: String, deviceName: String): Result<String>
    suspend fun syncPlaybackProgress(sessionId: String, timeListened: Double, currentTime: Double): Result<Unit>
    suspend fun syncStaticProgress(bookId: String, currentTime: Double, progress: Float, isFinished: Boolean): Result<Unit>
    suspend fun syncGlobalProgress(forceRefresh: Boolean = false): Result<Unit>
    suspend fun deleteLocalBookFiles(bookId: String): Result<Unit>
    suspend fun clearLocalData()
    fun getBooksPaged(
        libraryId: String,
        query: String,
        filter: ReadStatusFilter,
        downloadedOnly: Boolean,
        sortBy: SortOption
    ): Flow<PagingData<BookWithProgress>>
    suspend fun getCachedLibraries(): List<Library>
    suspend fun scanAndRelinkDownloads(): Result<Unit>
    suspend fun getOrphanedDownloadsSize(): Long
    suspend fun deleteOrphanedDownloads(): Result<Unit>
    fun getAuthorsFlow(libraryId: String): Flow<List<Author>>
    suspend fun syncLibraryAuthors(libraryId: String, forceRefresh: Boolean = false): Result<Unit>
    suspend fun getAuthorDetails(authorId: String, forceRefresh: Boolean = false): Result<Author>
    fun getBooksForAuthorFlow(authorId: String): Flow<List<BookWithProgress>>
    fun getCollectionsFlow(libraryId: String): Flow<List<BookCollection>>
    suspend fun syncLibraryCollections(libraryId: String, forceRefresh: Boolean = false): Result<Unit>
    suspend fun getCollectionDetails(collectionId: String, forceRefresh: Boolean = false): Result<BookCollection>
    fun getBooksForCollectionFlow(collectionId: String): Flow<List<BookWithProgress>>
    fun getPlaylistsFlow(): Flow<List<Playlist>>
    suspend fun syncPlaylists(forceRefresh: Boolean = false): Result<Unit>
    suspend fun getPlaylistDetails(playlistId: String, forceRefresh: Boolean = false): Result<Playlist>
    suspend fun updatePlaylistItems(playlistId: String, items: List<PlaylistItem>): Result<Unit>
    suspend fun updatePlaybackFinished(bookId: String, isFinished: Boolean): Result<Unit>
    suspend fun discardProgress(bookId: String): Result<Unit>
    suspend fun addBookToPlaylist(playlistId: String, bookId: String): Result<Unit>
    suspend fun createPlaylistWithBook(name: String, libraryId: String, bookId: String): Result<Unit>
    suspend fun removePlaylistItem(playlistId: String, bookId: String): Result<Unit>
    fun getSeriesByIdFlow(seriesId: String): Flow<Series?>
    fun getPlaylistsContainingBookFlow(bookId: String): Flow<List<Playlist>>
    fun getCollectionsContainingBookFlow(bookId: String): Flow<List<BookCollection>>
}


