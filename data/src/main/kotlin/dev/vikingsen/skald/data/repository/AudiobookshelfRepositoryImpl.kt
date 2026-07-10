package dev.vikingsen.skald.data.repository

import android.content.Context
import android.util.Log
import dev.vikingsen.skald.core.preferences.PreferencesManager
import dev.vikingsen.skald.core.database.AppDatabaseProvider
import dev.vikingsen.skald.core.database.BookEntity
import dev.vikingsen.skald.core.database.LocalAudioFile
import dev.vikingsen.skald.core.database.LocalChapter
import dev.vikingsen.skald.core.database.PlaybackProgressEntity
import dev.vikingsen.skald.data.mapper.*
import dev.vikingsen.skald.core.database.PlaylistEntity
import dev.vikingsen.skald.core.database.PlaylistItemEntity
import dev.vikingsen.skald.core.model.Playlist
import dev.vikingsen.skald.core.model.PlaylistItem
import dev.vikingsen.skald.core.network.PlaylistUpdatePayload
import dev.vikingsen.skald.core.network.PlaylistUpdateItem
import dev.vikingsen.skald.core.network.NetworkPlaylistResponse
import dev.vikingsen.skald.core.network.AudiobookshelfRemoteDataSource

import dev.vikingsen.skald.core.network.NetworkResult
import dev.vikingsen.skald.core.model.AudioFile
import dev.vikingsen.skald.core.model.Book
import dev.vikingsen.skald.core.model.DownloadStatusState
import dev.vikingsen.skald.core.model.Library
import dev.vikingsen.skald.core.model.LoggedUser
import dev.vikingsen.skald.core.model.PlaybackProgress
import dev.vikingsen.skald.core.database.SeriesEntity
import dev.vikingsen.skald.core.model.Series
import dev.vikingsen.skald.domain.repository.AudiobookshelfRepository
import dev.vikingsen.skald.core.model.Author
import dev.vikingsen.skald.core.database.AuthorEntity
import dev.vikingsen.skald.core.database.AuthorBookCrossRef
import dev.vikingsen.skald.core.database.CollectionEntity
import dev.vikingsen.skald.core.database.CollectionBookCrossRef
import dev.vikingsen.skald.core.database.CollectionBookCoverInfo
import dev.vikingsen.skald.core.model.BookCollection
import dev.vikingsen.skald.core.network.NetworkCollectionResponse
import android.app.DownloadManager
import android.net.Uri
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import dev.vikingsen.skald.core.model.HomeShelf
import dev.vikingsen.skald.core.model.HomeShelfItem
import dev.vikingsen.skald.core.model.HomeEpisodeMetadata
import dev.vikingsen.skald.core.database.HomeShelfEntity
import dev.vikingsen.skald.core.database.HomeShelfItemEntity
import dev.vikingsen.skald.core.database.HomeShelfWithItems
import dev.vikingsen.skald.core.network.NetworkLibraryShelf
import dev.vikingsen.skald.core.network.NetworkBookShelf
import dev.vikingsen.skald.core.network.NetworkPodcastShelf
import dev.vikingsen.skald.core.network.NetworkEpisodeShelf
import dev.vikingsen.skald.core.network.NetworkSeriesShelf
import dev.vikingsen.skald.core.network.NetworkAuthorShelf
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import dev.vikingsen.skald.core.model.BookWithProgress
import dev.vikingsen.skald.core.model.ReadStatusFilter
import dev.vikingsen.skald.core.model.SortOption
import dev.vikingsen.skald.core.network.LibraryItemsResponse
import dev.vikingsen.skald.core.network.LibraryItem
import dev.vikingsen.skald.core.network.BookResponse
import dev.vikingsen.skald.core.network.UserProgressResponse
import dev.vikingsen.skald.core.network.NetworkMediaProgress
import java.io.File
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.sqlite.db.SimpleSQLiteQuery

class AudiobookshelfRepositoryImpl(
    private val context: Context,
    private val dbProvider: AppDatabaseProvider,
    private val preferencesManager: PreferencesManager,
    private val remoteDataSource: AudiobookshelfRemoteDataSource
) : AudiobookshelfRepository {
    private val db = dbProvider.database
    private val bookDao = db.bookDao()
    private val progressDao = db.playbackProgressDao()
    private val libraryDao = db.libraryDao()
    private val homeShelfDao = db.homeShelfDao()

    override suspend fun login(url: String, user: String, pass: String): Result<LoggedUser> = withContext(Dispatchers.IO) {
        runCatching {
            val (response, formattedUrl) = remoteDataSource.login(url, user, pass)
            
            val previousUserId = preferencesManager.getUserId()
            val previousUsername = preferencesManager.getUsername()

            val isSameUser = if (!previousUserId.isNullOrEmpty() && !response.user.id.isNullOrEmpty()) {
                previousUserId == response.user.id
            } else if (!previousUsername.isNullOrEmpty()) {
                previousUsername.equals(response.user.username, ignoreCase = true)
            } else {
                true
            }

            if (!isSameUser) {
                clearLocalData()
                val downloadsFolder = File(context.getExternalFilesDir(null), "downloads")
                if (downloadsFolder.exists()) {
                    downloadsFolder.deleteRecursively()
                }
                preferencesManager.clear()
            }

            preferencesManager.saveConnectionDetails(
                url = formattedUrl,
                user = response.user.username,
                token = response.user.accessToken ?: response.user.token,
                refreshToken = response.user.refreshToken,
                userId = response.user.id
            )
            response.toDomain()
        }
    }

    override suspend fun fetchLibraries(): Result<List<Library>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = remoteDataSource.fetchLibraries()
            val domainLibs = response.libraries.map { it.toDomain() }
            libraryDao.insertAll(domainLibs.map { it.toEntity() })
            domainLibs
        }
    }

    override suspend fun syncLibraryBooks(libraryId: String, forceRefresh: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val storedEtag = if (forceRefresh) null else preferencesManager.getLibraryETag(libraryId)
            val limit = 100
            var currentPage = 0
            val allItems = mutableListOf<LibraryItem>()
            var total = 0
            
            // Fetch first page with ETag
            val firstResult = remoteDataSource.fetchLibraryItems(libraryId, limit, currentPage, storedEtag)
            
            when (firstResult) {
                is NetworkResult.NotModified -> {
                    // Not modified, skip sync!
                    return@runCatching
                }
                is NetworkResult.Error -> {
                    throw Exception(firstResult.message)
                }
                is NetworkResult.Success -> {
                    val newEtag = firstResult.etag
                    if (!newEtag.isNullOrEmpty()) {
                        preferencesManager.saveLibraryETag(libraryId, newEtag)
                    }
                    val firstPageData = firstResult.data
                    allItems.addAll(firstPageData.results)
                    total = firstPageData.total
                }
            }
            
            // Fetch subsequent pages
            var fetched = allItems.size
            while (fetched < total) {
                currentPage++
                val pageResult = remoteDataSource.fetchLibraryItems(libraryId, limit, currentPage, null)
                when (pageResult) {
                    is NetworkResult.Success -> {
                        val pageData = pageResult.data
                        if (pageData.results.isEmpty()) break
                        allItems.addAll(pageData.results)
                        fetched += pageData.results.size
                    }
                    is NetworkResult.Error -> {
                        throw Exception("Failed to sync library books page $currentPage: ${pageResult.message}")
                    }
                    is NetworkResult.NotModified -> {
                        break
                    }
                }
            }
            
            // Map and upsert
            val books = allItems.map { item ->
                val existing = bookDao.getBookById(item.id)
                val author = item.media.metadata.authorName
                    ?: item.media.metadata.authors?.mapNotNull { it.name }?.joinToString(", ")
                    ?: existing?.author
                    ?: "Unknown Author"
                val narrator = item.media.metadata.narratorName
                    ?: item.media.metadata.narrators?.joinToString(", ")
                    ?: existing?.narrator
                    ?: "Unknown Narrator"
                val seriesInfo = item.media.metadata.series?.firstOrNull()
                val seriesId = seriesInfo?.id
                val seriesSequence = seriesInfo?.sequence

                BookEntity(
                    id = item.id,
                    libraryId = libraryId,
                    title = item.media.metadata.title ?: "Unknown Title",
                    author = author,
                    narrator = narrator,
                    description = existing?.description ?: "",
                    duration = existing?.duration ?: 0.0,
                    coverPath = existing?.coverPath,
                    isDownloaded = existing?.isDownloaded ?: false,
                    audioFiles = existing?.audioFiles ?: emptyList(),
                    chapters = existing?.chapters ?: emptyList(),
                    etag = existing?.etag,
                    lastDetailFetchTimestamp = existing?.lastDetailFetchTimestamp ?: 0L,
                    seriesId = seriesId ?: existing?.seriesId,
                    seriesSequence = seriesSequence ?: existing?.seriesSequence
                )
            }
            bookDao.insertAll(books)
            preferencesManager.saveLibraryLastSyncTimestamp(System.currentTimeMillis())
            runCatching { scanAndRelinkDownloads() }
            Unit
        }
    }

    override fun getBooksFlow(): Flow<List<Book>> {
        return bookDao.getAllBooksFlow().map { list -> list.map { it.toDomain() } }
    }

    override fun getAllProgressFlow(): Flow<List<PlaybackProgress>> {
        return progressDao.getAllProgressFlow().map { list -> list.map { it.toDomain() } }
    }

    override fun getBookWithProgressFlow(bookId: String): Flow<Pair<Book?, PlaybackProgress?>> {
        return bookDao.getBookByIdFlow(bookId).combine(progressDao.getProgressForBookFlow(bookId)) { bookEntity, progressEntity ->
            bookEntity?.toDomain() to progressEntity?.toDomain()
        }
    }

    override suspend fun fetchBookDetails(bookId: String, forceRefresh: Boolean): Result<Book> = withContext(Dispatchers.IO) {
        runCatching {
            val existing = bookDao.getBookById(bookId)
            val now = System.currentTimeMillis()
            
            // Check time-based refresh threshold (24 hours)
            val threshold = 24L * 60L * 60L * 1000L
            val needsRefresh = forceRefresh || existing == null || (now - existing.lastDetailFetchTimestamp > threshold)
            
            if (!needsRefresh) {
                return@runCatching existing.toDomain()
            }
            
            val storedEtag = if (forceRefresh) null else existing?.etag
            val result = remoteDataSource.fetchBookDetails(bookId, storedEtag)
            
            val bookEntity = when (result) {
                is NetworkResult.NotModified -> {
                    if (existing == null) throw Exception("Cached book detail missing on 304 response")
                    existing.copy(lastDetailFetchTimestamp = now)
                }
                is NetworkResult.Error -> {
                    throw Exception(result.message)
                }
                is NetworkResult.Success -> {
                    val detailResponse = result.data
                    val newEtag = result.etag
                    
                    val author = detailResponse.media.metadata.authorName
                        ?: detailResponse.media.metadata.authors?.mapNotNull { it.name }?.joinToString(", ")
                        ?: existing?.author
                        ?: "Unknown Author"
                    val narrator = detailResponse.media.metadata.narratorName
                        ?: detailResponse.media.metadata.narrators?.joinToString(", ")
                        ?: existing?.narrator
                        ?: "Unknown Narrator"
                    val seriesInfo = detailResponse.media.metadata.series?.firstOrNull()
                    val seriesId = seriesInfo?.id
                    val seriesSequence = seriesInfo?.sequence

                    BookEntity(
                        id = detailResponse.id,
                        libraryId = existing?.libraryId ?: preferencesManager.getLibraryId() ?: "",
                        title = detailResponse.media.metadata.title,
                        author = author,
                        narrator = narrator,
                        description = detailResponse.media.metadata.description ?: "",
                        duration = detailResponse.media.audioFiles?.sumOf { it.duration ?: 0.0 } ?: 0.0,
                        coverPath = existing?.coverPath,
                        isDownloaded = existing?.isDownloaded ?: false,
                        audioFiles = detailResponse.media.audioFiles?.map { file ->
                            val existingFile = existing?.audioFiles?.find { it.ino == file.ino }
                            LocalAudioFile(
                                index = file.index,
                                ino = file.ino,
                                duration = file.duration ?: 0.0,
                                mimeType = file.mimeType,
                                filename = file.metadata?.filename ?: "file_${file.index}",
                                size = file.metadata?.size ?: 0L,
                                localPath = existingFile?.localPath,
                                downloadStatus = existingFile?.downloadStatus ?: "NOT_DOWNLOADED"
                            )
                        } ?: emptyList(),
                        chapters = detailResponse.media.chapters?.map { chapter ->
                            LocalChapter(
                                start = chapter.start,
                                end = chapter.end,
                                title = chapter.title
                            )
                        } ?: emptyList(),
                        etag = newEtag,
                        lastDetailFetchTimestamp = now,
                        seriesId = seriesId ?: existing?.seriesId,
                        seriesSequence = seriesSequence ?: existing?.seriesSequence
                    )
                }
            }
            
            bookDao.insertBook(bookEntity)
            
            // Sync progress from server if exists
            fetchProgressFromServer(bookId)
            
            bookEntity.toDomain()
        }.recoverCatching { exception ->
            val local = bookDao.getBookById(bookId)
            local?.toDomain() ?: throw exception
        }
    }

    private suspend fun fetchProgressFromServer(bookId: String) {
        runCatching {
            val response = remoteDataSource.fetchProgressFromServer(bookId) ?: return@runCatching
            val currentProgress = progressDao.getProgressForBook(bookId)
            if (currentProgress == null || response.currentTime > currentProgress.currentTime) {
                progressDao.insertProgress(
                    PlaybackProgressEntity(
                        bookId = bookId,
                        currentTime = response.currentTime,
                        progress = response.progress,
                        isFinished = response.isFinished,
                        lastUpdated = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    override suspend fun saveLocalProgress(bookId: String, currentTime: Double, totalDuration: Double) {
        val progress = if (totalDuration > 0) (currentTime / totalDuration).toFloat() else 0f
        val entity = PlaybackProgressEntity(
            bookId = bookId,
            currentTime = currentTime,
            progress = progress,
            isFinished = progress >= 0.99f,
            lastUpdated = System.currentTimeMillis()
        )
        progressDao.insertProgress(entity)
    }

    override suspend fun startPlaybackSession(bookId: String, deviceId: String, deviceName: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val response = remoteDataSource.startPlaybackSession(bookId, deviceId, deviceName)
            response.id
        }
    }

    override suspend fun syncPlaybackProgress(sessionId: String, timeListened: Double, currentTime: Double): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            remoteDataSource.syncPlaybackProgress(sessionId, timeListened, currentTime)
        }
    }

    override suspend fun syncStaticProgress(bookId: String, currentTime: Double, progress: Float, isFinished: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            remoteDataSource.syncStaticProgress(bookId, currentTime, progress, isFinished)
        }
    }

    override suspend fun syncGlobalProgress(forceRefresh: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val storedEtag = if (forceRefresh) null else preferencesManager.getUserETag()
            val result = remoteDataSource.fetchCurrentUserProgress(storedEtag)
            
            when (result) {
                is NetworkResult.NotModified -> {
                    // Local cache is up-to-date, nothing to sync
                }
                is NetworkResult.Error -> {
                    throw Exception(result.message)
                }
                is NetworkResult.Success -> {
                    val newEtag = result.etag
                    if (!newEtag.isNullOrEmpty()) {
                        preferencesManager.saveUserETag(newEtag)
                    }
                    val userResponse = result.data
                    
                    userResponse.mediaProgress.forEach { serverProgress ->
                        val bookId = serverProgress.libraryItemId
                        val localProgress = progressDao.getProgressForBook(bookId)
                        
                        if (localProgress == null) {
                            progressDao.insertProgress(
                                PlaybackProgressEntity(
                                    bookId = bookId,
                                    currentTime = serverProgress.currentTime,
                                    progress = serverProgress.progress.toFloat(),
                                    isFinished = serverProgress.isFinished,
                                    lastUpdated = serverProgress.lastUpdate
                                )
                            )
                        } else if (serverProgress.lastUpdate > localProgress.lastUpdated) {
                            progressDao.insertProgress(
                                PlaybackProgressEntity(
                                    bookId = bookId,
                                    currentTime = serverProgress.currentTime,
                                    progress = serverProgress.progress.toFloat(),
                                    isFinished = serverProgress.isFinished,
                                    lastUpdated = serverProgress.lastUpdate
                                )
                            )
                        } else if (localProgress.lastUpdated > serverProgress.lastUpdate) {
                            // Local progress is newer, sync to server
                            remoteDataSource.syncStaticProgress(
                                bookId = bookId,
                                currentTime = localProgress.currentTime,
                                progress = localProgress.progress,
                                isFinished = localProgress.isFinished
                            )
                        }
                    }
                }
            }
        }
    }

    override suspend fun enqueueBookDownloads(bookId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val book = bookDao.getBookById(bookId) ?: throw Exception("Book not found")
            val serverUrl = preferencesManager.getServerUrl() ?: throw Exception("Server URL not configured")
            val token = preferencesManager.getToken() ?: throw Exception("Auth token not configured")
            
            val filesToDownload = book.audioFiles.filter { it.downloadStatus != "COMPLETED" }
            if (filesToDownload.isEmpty()) return@runCatching

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val sanitizedBase = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"

            for (file in filesToDownload) {
                val downloadUrl = "${sanitizedBase}api/items/$bookId/file/${file.ino}/download"
                val folder = File(context.getExternalFilesDir(null), "downloads/$bookId")
                if (!folder.exists()) folder.mkdirs()
                
                val extension = file.filename.substringAfterLast('.', "mp3")
                val destinationFile = File(folder, "${file.ino}.$extension.tmp")

                val request = DownloadManager.Request(Uri.parse(downloadUrl))
                    .setTitle(file.filename)
                    .setDescription("Downloading file for ${book.title}")
                    .setDestinationUri(Uri.fromFile(destinationFile))
                    .addRequestHeader("Authorization", "Bearer $token")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)

                downloadManager.enqueue(request)
                updateAudioFileDownloadStatus(bookId, file.ino, "DOWNLOADING", null)
            }
        }
    }

    override fun getBookDownloadFlow(bookId: String): Flow<DownloadStatusState> = flow {
        while (true) {
            val book = bookDao.getBookById(bookId)
            if (book == null) {
                emit(DownloadStatusState.Progress(0f))
                delay(1000)
                continue
            }

            val files = book.audioFiles
            val totalFiles = files.size
            if (totalFiles == 0) {
                emit(DownloadStatusState.Progress(0f))
                delay(1000)
                continue
            }

            val completedCount = files.count { it.downloadStatus == "COMPLETED" }
            val downloadingFiles = files.filter { it.downloadStatus == "DOWNLOADING" }

            if (completedCount == totalFiles) {
                val path = files.firstOrNull()?.localPath?.let { File(it).parent } ?: ""
                emit(DownloadStatusState.Completed(path))
                break
            }

            if (downloadingFiles.isEmpty()) {
                if (completedCount > 0) {
                    emit(DownloadStatusState.Progress(completedCount.toFloat() / totalFiles))
                } else {
                    emit(DownloadStatusState.Progress(0f))
                }
                delay(1000)
                continue
            }

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterByStatus(
                DownloadManager.STATUS_RUNNING or
                DownloadManager.STATUS_PAUSED or
                DownloadManager.STATUS_PENDING
            )

            var activeProgressSum = 0f
            var activeCount = 0

            runCatching {
                downloadManager.query(query).use { cursor ->
                    while (cursor.moveToNext()) {
                        val uriColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_URI)
                        val remoteUri = cursor.getString(uriColumn) ?: continue

                        val regex = Regex(".*/api/items/([^/]+)/file/([^/]+)/download.*")
                        val match = regex.matchEntire(remoteUri) ?: continue
                        val itemBookId = match.groupValues[1]
                        val itemIno = match.groupValues[2]

                        if (itemBookId == bookId) {
                            val bytesDownloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                            val bytesTotal = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                            if (bytesTotal > 0) {
                                activeProgressSum += bytesDownloaded.toFloat() / bytesTotal
                            } else {
                                activeProgressSum += 0.1f
                            }
                            activeCount++
                        }
                    }
                }
            }

            val progressSum = completedCount.toFloat() + activeProgressSum.coerceAtMost(downloadingFiles.size.toFloat())
            val totalProgress = (progressSum / totalFiles).coerceIn(0f, 1f)
            emit(DownloadStatusState.Progress(totalProgress))
            delay(1000)
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun updateAudioFileDownloadStatus(bookId: String, ino: String, status: String, localPath: String?) {
        val book = bookDao.getBookById(bookId) ?: return
        val updatedFiles = book.audioFiles.map {
            if (it.ino == ino) {
                it.copy(localPath = localPath, downloadStatus = status)
            } else {
                it
            }
        }
        val isAllDownloaded = updatedFiles.all { it.downloadStatus == "COMPLETED" }
        bookDao.insertBook(book.copy(audioFiles = updatedFiles, isDownloaded = isAllDownloaded))
    }

    override suspend fun deleteLocalBookFiles(bookId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val book = bookDao.getBookById(bookId) ?: throw Exception("Book not found")

            // 1. Cancel active downloads in DownloadManager
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query()
            val idsToRemove = mutableListOf<Long>()
            runCatching {
                downloadManager.query(query).use { cursor ->
                    val uriColumn = cursor.getColumnIndex(DownloadManager.COLUMN_URI)
                    val idColumn = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
                    if (uriColumn != -1 && idColumn != -1) {
                        while (cursor.moveToNext()) {
                            val remoteUri = cursor.getString(uriColumn) ?: continue
                            val id = cursor.getLong(idColumn)

                            val regex = Regex(".*/api/items/([^/]+)/file/([^/]+)/download.*")
                            val match = regex.matchEntire(remoteUri) ?: continue
                            val itemBookId = match.groupValues[1]

                            if (itemBookId == bookId) {
                                idsToRemove.add(id)
                            }
                        }
                    }
                }
            }
            if (idsToRemove.isNotEmpty()) {
                downloadManager.remove(*idsToRemove.toLongArray())
            }

            // 2. Delete local directory recursively
            val folder = File(context.getExternalFilesDir(null), "downloads/$bookId")
            if (folder.exists()) {
                val deleted = folder.deleteRecursively()
                if (!deleted) {
                    throw Exception("Failed to delete local book files directory")
                }
            }

            // 3. Update database
            val updatedFiles = book.audioFiles.map {
                it.copy(localPath = null, downloadStatus = "NOT_DOWNLOADED")
            }
            val updatedBook = book.copy(audioFiles = updatedFiles, isDownloaded = false)
            bookDao.insertBook(updatedBook)
        }
    }

    override suspend fun clearLocalData() = withContext(Dispatchers.IO) {
        db.clearAllTables()
    }

    override fun getBooksPaged(
        libraryId: String,
        query: String,
        filter: ReadStatusFilter,
        downloadedOnly: Boolean,
        sortBy: SortOption
    ): Flow<PagingData<BookWithProgress>> {
        val sqliteQuery = buildLibraryQuery(libraryId, query, filter, downloadedOnly, sortBy)
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { bookDao.getBooksPaged(sqliteQuery) }
        ).flow.map { pagingData ->
            pagingData.map { entity -> entity.toDomain() }
        }
    }

    override suspend fun getCachedLibraries(): List<Library> = withContext(Dispatchers.IO) {
        libraryDao.getAllLibraries().map { it.toDomain() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getHomeShelvesFlow(libraryId: String): Flow<List<HomeShelf>> {
        return homeShelfDao.getShelvesWithItemsFlow(libraryId).flatMapLatest { dbShelves ->
            if (dbShelves.isNotEmpty()) {
                flowOf(dbShelves.map { it.toDomain() })
            } else {
                combine(
                    bookDao.getBooksInProgressFlow(libraryId),
                    bookDao.getDownloadedBooksFlow(libraryId)
                ) { inProgressBooks, downloadedBooks ->
                    val fallbackShelves = mutableListOf<HomeShelf>()
                    
                    if (inProgressBooks.isNotEmpty()) {
                        fallbackShelves.add(
                            HomeShelf(
                                id = "local-books-continue",
                                libraryId = libraryId,
                                label = "Continue Listening",
                                total = inProgressBooks.size,
                                type = "book",
                                items = inProgressBooks.map { book ->
                                    HomeShelfItem(
                                        entityId = book.id,
                                        title = book.title,
                                        subtitle = book.author,
                                        imageUrl = book.coverPath,
                                        additionalData = null
                                    )
                                }
                            )
                        )
                    }
                    
                    if (downloadedBooks.isNotEmpty()) {
                        fallbackShelves.add(
                            HomeShelf(
                                id = "local-books",
                                libraryId = libraryId,
                                label = "Downloaded Books",
                                total = downloadedBooks.size,
                                type = "book",
                                items = downloadedBooks.map { book ->
                                    HomeShelfItem(
                                        entityId = book.id,
                                        title = book.title,
                                        subtitle = book.author,
                                        imageUrl = book.coverPath,
                                        additionalData = null
                                    )
                                }
                            )
                        )
                    }
                    
                    fallbackShelves
                }
            }
        }
    }

    override suspend fun syncHomeShelves(libraryId: String, forceRefresh: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val storedEtag = if (forceRefresh) null else preferencesManager.getLibraryHomeETag(libraryId)
            val result = remoteDataSource.fetchPersonalizedShelves(libraryId, storedEtag)
            
            when (result) {
                is NetworkResult.NotModified -> {
                    return@runCatching
                }
                is NetworkResult.Error -> {
                    throw Exception(result.message)
                }
                is NetworkResult.Success -> {
                    val newEtag = result.etag
                    if (!newEtag.isNullOrEmpty()) {
                        preferencesManager.saveLibraryHomeETag(libraryId, newEtag)
                    }
                    val networkShelves = result.data
                    
                    val shelvesList = mutableListOf<HomeShelfEntity>()
                    val itemsList = mutableListOf<HomeShelfItemEntity>()
                    
                    networkShelves.forEachIndexed { verticalIndex, shelf ->
                        val shelfEntity = HomeShelfEntity(
                            id = shelf.id,
                            libraryId = libraryId,
                            label = shelf.label,
                            total = shelf.total,
                            type = shelf.type,
                            verticalSortOrder = verticalIndex
                        )
                        shelvesList.add(shelfEntity)
                        
                        when (shelf) {
                            is NetworkBookShelf -> {
                                shelf.entities?.forEachIndexed { horizontalIndex, item ->
                                    val author = item.media.metadata.authorName
                                        ?: item.media.metadata.authors?.mapNotNull { it.name }?.joinToString(", ")
                                        ?: "Unknown Author"
                                    itemsList.add(
                                        HomeShelfItemEntity(
                                            compositeId = "${shelf.id}_${item.id}",
                                            shelfId = shelf.id,
                                            entityId = item.id,
                                            title = item.media.metadata.title ?: "Unknown Title",
                                            subtitle = author,
                                            imageUrl = null,
                                            horizontalIndex = horizontalIndex,
                                            additionalData = null
                                        )
                                    )
                                }
                            }
                            is NetworkPodcastShelf -> {
                                shelf.entities?.forEachIndexed { horizontalIndex, item ->
                                    val author = item.media.metadata.authorName ?: "Unknown Author"
                                    itemsList.add(
                                        HomeShelfItemEntity(
                                            compositeId = "${shelf.id}_${item.id}",
                                            shelfId = shelf.id,
                                            entityId = item.id,
                                            title = item.media.metadata.title ?: "Unknown Title",
                                            subtitle = author,
                                            imageUrl = null,
                                            horizontalIndex = horizontalIndex,
                                            additionalData = null
                                        )
                                    )
                                }
                            }
                            is NetworkEpisodeShelf -> {
                                shelf.entities?.forEachIndexed { horizontalIndex, item ->
                                    val episodeMeta = item.recentEpisode?.let {
                                        HomeEpisodeMetadata(
                                            id = it.id,
                                            title = it.title,
                                            pubDate = it.pubDate,
                                            duration = it.duration
                                        )
                                    }
                                    val episodeData = episodeMeta?.let { Json.encodeToString(it) }
                                    itemsList.add(
                                        HomeShelfItemEntity(
                                            compositeId = "${shelf.id}_${item.id}",
                                            shelfId = shelf.id,
                                            entityId = item.id,
                                            title = item.recentEpisode?.title ?: item.media.metadata.title ?: "Unknown Episode",
                                            subtitle = item.media.metadata.title,
                                            imageUrl = null,
                                            horizontalIndex = horizontalIndex,
                                            additionalData = episodeData
                                        )
                                    )
                                }
                            }
                            is NetworkSeriesShelf -> {
                                shelf.entities?.forEachIndexed { horizontalIndex, item ->
                                    val booksList = item.books
                                    val subtitle = if (!booksList.isNullOrEmpty()) "${booksList.size} Books" else "Series"
                                    val firstBookId = booksList?.firstOrNull()?.id
                                    val seriesCoverPath = firstBookId?.let { "/api/items/$it/cover" }
                                    itemsList.add(
                                        HomeShelfItemEntity(
                                            compositeId = "${shelf.id}_${item.id}",
                                            shelfId = shelf.id,
                                            entityId = item.id,
                                            title = item.name,
                                            subtitle = subtitle,
                                            imageUrl = seriesCoverPath,
                                            horizontalIndex = horizontalIndex,
                                            additionalData = null
                                        )
                                    )
                                }
                            }
                            is NetworkAuthorShelf -> {
                                shelf.entities?.forEachIndexed { horizontalIndex, item ->
                                    itemsList.add(
                                        HomeShelfItemEntity(
                                            compositeId = "${shelf.id}_${item.id}",
                                            shelfId = shelf.id,
                                            entityId = item.id,
                                            title = item.name,
                                            subtitle = null,
                                            imageUrl = item.coverPath,
                                            horizontalIndex = horizontalIndex,
                                            additionalData = null
                                        )
                                    )
                                }
                            }
                        }
                    }
                    
                    homeShelfDao.replaceShelvesForLibrary(libraryId, shelvesList, itemsList)
                }
            }
        }
    }

    private fun buildLibraryQuery(
        libraryId: String,
        searchQuery: String,
        filterStatus: ReadStatusFilter,
        downloadedOnly: Boolean,
        sortBy: SortOption
    ): SimpleSQLiteQuery {
        val sql = java.lang.StringBuilder()
        val bindArgs = ArrayList<Any>()
        
        sql.append("SELECT b.*, " +
                   "p.bookId AS progress_bookId, p.currentTime AS progress_currentTime, " +
                   "p.progress AS progress_progress, p.isFinished AS progress_isFinished, " +
                   "p.lastUpdated AS progress_lastUpdated " +
                   "FROM books b LEFT JOIN playback_progress p ON b.id = p.bookId " +
                   "WHERE b.libraryId = ?")
        bindArgs.add(libraryId)
        
        if (searchQuery.isNotEmpty()) {
            sql.append(" AND (b.title LIKE ? OR b.author LIKE ? OR b.narrator LIKE ?)")
            val wildcardQuery = "%$searchQuery%"
            bindArgs.add(wildcardQuery)
            bindArgs.add(wildcardQuery)
            bindArgs.add(wildcardQuery)
        }
        
        if (downloadedOnly) {
            sql.append(" AND b.isDownloaded = 1")
        }
        
        when (filterStatus) {
            ReadStatusFilter.ALL -> {}
            ReadStatusFilter.UNREAD -> {
                sql.append(" AND (p.progress IS NULL OR (p.progress = 0 AND p.isFinished = 0))")
            }
            ReadStatusFilter.IN_PROGRESS -> {
                sql.append(" AND (p.progress IS NOT NULL AND p.progress > 0 AND p.isFinished = 0)")
            }
            ReadStatusFilter.READ -> {
                sql.append(" AND (p.isFinished = 1 OR p.progress >= 0.99)")
            }
        }
        
        sql.append(" ORDER BY ")
        when (sortBy) {
            SortOption.TITLE_ASC -> sql.append("b.title COLLATE NOCASE ASC")
            SortOption.TITLE_DESC -> sql.append("b.title COLLATE NOCASE DESC")
            SortOption.AUTHOR_ASC -> sql.append("b.author COLLATE NOCASE ASC")
            SortOption.AUTHOR_DESC -> sql.append("b.author COLLATE NOCASE DESC")
            SortOption.DURATION_ASC -> sql.append("b.duration ASC")
            SortOption.DURATION_DESC -> sql.append("b.duration DESC")
            SortOption.LAST_PLAYED -> {
                sql.append("CASE WHEN p.lastUpdated IS NULL THEN 1 ELSE 0 END, p.lastUpdated DESC, b.title COLLATE NOCASE ASC")
            }
        }
        
        return SimpleSQLiteQuery(sql.toString(), bindArgs.toArray())
    }

    override suspend fun syncLibrarySeries(libraryId: String, forceRefresh: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val storedEtag = if (forceRefresh) null else preferencesManager.getLibrarySeriesETag(libraryId)
            val result = remoteDataSource.fetchLibrarySeries(libraryId, storedEtag)
            
            when (result) {
                is NetworkResult.NotModified -> return@runCatching
                is NetworkResult.Error -> throw Exception(result.message)
                is NetworkResult.Success -> {
                    val newEtag = result.etag
                    if (!newEtag.isNullOrEmpty()) {
                        preferencesManager.saveLibrarySeriesETag(libraryId, newEtag)
                    }
                    
                    val seriesResponseList = result.data.results
                    val entities = seriesResponseList.map {
                        SeriesEntity(
                            id = it.id,
                            libraryId = libraryId,
                            name = it.name,
                            description = it.description,
                            bookCount = it.books?.size ?: 0,
                            etag = newEtag
                        )
                    }
                    
                    // Update book items relation
                    seriesResponseList.forEach { seriesItem ->
                        seriesItem.books?.forEach { bookItem ->
                            val existing = bookDao.getBookById(bookItem.id)
                            val sequence = bookItem.media.metadata.series?.firstOrNull { it.id == seriesItem.id }?.sequence
                            if (existing != null) {
                                if (existing.seriesId != seriesItem.id || existing.seriesSequence != sequence) {
                                    bookDao.insertBook(existing.copy(
                                        seriesId = seriesItem.id,
                                        seriesSequence = sequence
                                    ))
                                }
                            }
                        }
                    }
                    
                    db.seriesDao().replaceSeriesForLibrary(libraryId, entities)
                }
            }
        }
    }

    override fun getSeriesFlow(libraryId: String): Flow<List<Series>> {
        return db.seriesDao().getSeriesFlow(libraryId).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getSeriesById(seriesId: String): Series? {
        return db.seriesDao().getSeriesById(seriesId)?.toDomain()
    }

    override fun getBooksForSeriesFlow(seriesId: String): Flow<List<BookWithProgress>> {
        return bookDao.getBooksForSeriesWithProgressFlow(seriesId).map { list -> list.map { it.toDomain() } }
    }

    override fun getBooksWithProgressForLibraryFlow(libraryId: String): Flow<List<BookWithProgress>> {
        return bookDao.getBooksWithProgressForLibraryFlow(libraryId).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun scanAndRelinkDownloads(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val downloadsFolder = File(context.getExternalFilesDir(null), "downloads")
            if (!downloadsFolder.exists() || !downloadsFolder.isDirectory) {
                return@runCatching
            }

            val bookDirs = downloadsFolder.listFiles { file -> file.isDirectory } ?: return@runCatching
            for (bookDir in bookDirs) {
                val bookId = bookDir.name
                var book = bookDao.getBookById(bookId)
                
                // If the book details are not yet populated (empty audioFiles list), fetch them from the server first
                if (book != null && book.audioFiles.isEmpty()) {
                    runCatching { fetchBookDetails(bookId, forceRefresh = true) }
                    book = bookDao.getBookById(bookId)
                }

                if (book != null && book.audioFiles.isNotEmpty()) {
                    val filesOnDisk = bookDir.listFiles { file -> file.isFile && !file.name.endsWith(".tmp") } ?: continue
                    val diskInos = filesOnDisk.map { it.name.substringBeforeLast('.') }.toSet()
                    
                    var updated = false
                    val updatedFiles = book.audioFiles.map { file ->
                        if (diskInos.contains(file.ino)) {
                            val matchingFile = filesOnDisk.first { it.name.substringBeforeLast('.') == file.ino }
                            val absolutePath = matchingFile.absolutePath
                            if (file.downloadStatus != "COMPLETED" || file.localPath != absolutePath) {
                                updated = true
                                file.copy(localPath = absolutePath, downloadStatus = "COMPLETED")
                            } else {
                                file
                            }
                        } else {
                            if (file.downloadStatus == "COMPLETED" || file.localPath != null) {
                                updated = true
                                file.copy(localPath = null, downloadStatus = "NOT_DOWNLOADED")
                            } else {
                                file
                            }
                        }
                    }

                    if (updated) {
                        val isAllDownloaded = updatedFiles.all { it.downloadStatus == "COMPLETED" }
                        bookDao.insertBook(book.copy(audioFiles = updatedFiles, isDownloaded = isAllDownloaded))
                        Log.d("DownloadMaintenance", "Reconciled and updated book $bookId download status in database")
                    }
                }
            }
        }
    }

    override suspend fun getOrphanedDownloadsSize(): Long = withContext(Dispatchers.IO) {
        val downloadsFolder = File(context.getExternalFilesDir(null), "downloads")
        if (!downloadsFolder.exists() || !downloadsFolder.isDirectory) {
            return@withContext 0L
        }

        val bookDirs = downloadsFolder.listFiles { file -> file.isDirectory } ?: return@withContext 0L
        var totalSize = 0L
        for (bookDir in bookDirs) {
            val bookId = bookDir.name
            val book = bookDao.getBookById(bookId)
            if (book == null) {
                totalSize += getDirSize(bookDir)
            }
        }
        totalSize
    }

    override suspend fun deleteOrphanedDownloads(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val downloadsFolder = File(context.getExternalFilesDir(null), "downloads")
            if (!downloadsFolder.exists() || !downloadsFolder.isDirectory) {
                return@runCatching
            }

            val bookDirs = downloadsFolder.listFiles { file -> file.isDirectory } ?: return@runCatching
            for (bookDir in bookDirs) {
                val bookId = bookDir.name
                val book = bookDao.getBookById(bookId)
                if (book == null) {
                    bookDir.deleteRecursively()
                    Log.d("DownloadMaintenance", "Deleted orphaned downloads folder: $bookId")
                }
            }
        }
    }

    private fun getDirSize(dir: File): Long {
        var size = 0L
        if (dir.exists() && dir.isDirectory) {
            dir.listFiles()?.forEach { file ->
                size += if (file.isDirectory) getDirSize(file) else file.length()
            }
        } else if (dir.exists()) {
            size += dir.length()
        }
        return size
    }

    override fun getAuthorsFlow(libraryId: String): Flow<List<Author>> {
        return db.authorDao().getAuthorsFlow(libraryId).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun syncLibraryAuthors(libraryId: String, forceRefresh: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val storedEtag = if (forceRefresh) null else preferencesManager.getLibraryAuthorsETag(libraryId)
            val result = remoteDataSource.fetchLibraryAuthors(libraryId, storedEtag)
            
            when (result) {
                is NetworkResult.NotModified -> return@runCatching
                is NetworkResult.Error -> throw Exception(result.message)
                is NetworkResult.Success -> {
                    val newEtag = result.etag
                    if (!newEtag.isNullOrEmpty()) {
                        preferencesManager.saveLibraryAuthorsETag(libraryId, newEtag)
                    }
                    
                    val authorsResponseList = result.data.authors
                    val entities = authorsResponseList.map {
                        AuthorEntity(
                            id = it.id,
                            libraryId = libraryId,
                            name = it.name,
                            description = it.description,
                            imagePath = it.imagePath,
                            bookCount = it.bookCount ?: it.numBooks ?: 0,
                            etag = newEtag
                        )
                    }
                    
                    db.authorDao().replaceAuthorsForLibrary(libraryId, entities)
                }
            }
        }
    }

    override suspend fun getAuthorDetails(authorId: String, forceRefresh: Boolean): Result<Author> = withContext(Dispatchers.IO) {
        runCatching {
            val local = db.authorDao().getAuthorById(authorId)
            if (local != null && !forceRefresh && !local.description.isNullOrEmpty()) {
                return@runCatching local.toDomain()
            }
            
            val result = remoteDataSource.fetchAuthorDetails(authorId)
            when (result) {
                is NetworkResult.NotModified -> {
                    local?.toDomain() ?: throw Exception("Author details not found")
                }
                is NetworkResult.Error -> {
                    local?.toDomain() ?: throw Exception(result.message)
                }
                is NetworkResult.Success -> {
                    val detail = result.data
                    val libraryId = local?.libraryId ?: preferencesManager.getLibraryId() ?: ""
                    
                    val entity = AuthorEntity(
                        id = detail.id,
                        libraryId = libraryId,
                        name = detail.name,
                        description = detail.description,
                        imagePath = detail.imagePath,
                        bookCount = detail.libraryItems?.size ?: local?.bookCount ?: 0,
                        etag = local?.etag
                    )
                    
                    db.authorDao().insertAll(listOf(entity))
                    
                    val crossRefs = detail.libraryItems?.map { item ->
                        AuthorBookCrossRef(
                            authorId = detail.id,
                            bookId = item.id
                        )
                    } ?: emptyList()
                    
                    db.authorDao().replaceAuthorBookCrossRefsForAuthor(detail.id, crossRefs)
                    
                    entity.toDomain()
                }
            }
        }
    }

    override fun getBooksForAuthorFlow(authorId: String): Flow<List<BookWithProgress>> {
        return bookDao.getBooksForAuthorWithProgressFlow(authorId).map { list -> list.map { it.toDomain() } }
    }

    override fun getCollectionsFlow(libraryId: String): Flow<List<BookCollection>> {
        val serverUrl = preferencesManager.getServerUrl() ?: ""
        return combine(
            db.collectionDao().getCollectionsFlow(libraryId),
            db.collectionDao().getCollectionBookCoversFlow()
        ) { collections, covers ->
            val coversMap = covers.groupBy { it.collectionId }
            collections.map { col ->
                val colCovers = coversMap[col.id]?.map { coverInfo ->
                    coverInfo.coverPath.takeIf { !it.isNullOrEmpty() }
                        ?: "${serverUrl.trimEnd('/')}/api/items/${coverInfo.bookId}/cover"
                } ?: emptyList()
                val colBookIds = coversMap[col.id]?.map { it.bookId } ?: emptyList()
                col.toDomain(bookIds = colBookIds, bookCovers = colCovers)
            }
        }
    }

    override suspend fun syncLibraryCollections(libraryId: String, forceRefresh: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val storedEtag = if (forceRefresh) null else preferencesManager.getLibraryCollectionsETag(libraryId)
            val result = remoteDataSource.fetchLibraryCollections(libraryId, storedEtag)
            
            when (result) {
                is NetworkResult.NotModified -> return@runCatching
                is NetworkResult.Error -> throw Exception(result.message)
                is NetworkResult.Success -> {
                    val newEtag = result.etag
                    if (!newEtag.isNullOrEmpty()) {
                        preferencesManager.saveLibraryCollectionsETag(libraryId, newEtag)
                    }
                    
                    val collectionsResponseList = result.data
                    val timestamp = System.currentTimeMillis()
                    val entities = collectionsResponseList.map {
                        it.toEntity(libraryId, timestamp)
                    }
                    
                    val crossRefs = collectionsResponseList.flatMap { collectionDto ->
                        collectionDto.books?.mapIndexed { index, bookItem ->
                            CollectionBookCrossRef(
                                collectionId = collectionDto.id,
                                bookId = bookItem.id,
                                sortOrder = index
                            )
                        } ?: emptyList()
                    }
                    
                    db.collectionDao().replaceCollectionsForLibrary(libraryId, entities, crossRefs)
                }
            }
        }
    }

    override suspend fun getCollectionDetails(collectionId: String, forceRefresh: Boolean): Result<BookCollection> = withContext(Dispatchers.IO) {
        runCatching {
            val local = db.collectionDao().getCollectionById(collectionId)
            if (local != null && !forceRefresh) {
                val bookIds = db.collectionDao().getBookIdsForCollection(collectionId)
                val serverUrl = preferencesManager.getServerUrl() ?: ""
                val bookCovers = bookIds.map { bookId ->
                    val book = bookDao.getBookById(bookId)
                    book?.coverPath.takeIf { !it.isNullOrEmpty() }
                        ?: "${serverUrl.trimEnd('/')}/api/items/$bookId/cover"
                }
                return@runCatching local.toDomain(bookIds, bookCovers)
            }
            
            val result = remoteDataSource.fetchCollectionDetails(collectionId)
            when (result) {
                is NetworkResult.NotModified -> {
                    if (local == null) throw Exception("Cached collection missing on 304 response")
                    val bookIds = db.collectionDao().getBookIdsForCollection(collectionId)
                    val serverUrl = preferencesManager.getServerUrl() ?: ""
                    val bookCovers = bookIds.map { bookId ->
                        val book = bookDao.getBookById(bookId)
                        book?.coverPath.takeIf { !it.isNullOrEmpty() }
                            ?: "${serverUrl.trimEnd('/')}/api/items/$bookId/cover"
                    }
                    local.toDomain(bookIds, bookCovers)
                }
                is NetworkResult.Error -> {
                    if (local == null) throw Exception(result.message)
                    val bookIds = db.collectionDao().getBookIdsForCollection(collectionId)
                    val serverUrl = preferencesManager.getServerUrl() ?: ""
                    val bookCovers = bookIds.map { bookId ->
                        val book = bookDao.getBookById(bookId)
                        book?.coverPath.takeIf { !it.isNullOrEmpty() }
                            ?: "${serverUrl.trimEnd('/')}/api/items/$bookId/cover"
                    }
                    local.toDomain(bookIds, bookCovers)
                }
                is NetworkResult.Success -> {
                    val detail = result.data
                    val libraryId = local?.libraryId ?: preferencesManager.getLibraryId() ?: ""
                    val entity = detail.toEntity(libraryId, System.currentTimeMillis())
                    
                    db.collectionDao().insertCollection(entity)
                    
                    val crossRefs = detail.books?.mapIndexed { index, bookItem ->
                        CollectionBookCrossRef(
                            collectionId = detail.id,
                            bookId = bookItem.id,
                            sortOrder = index
                        )
                    } ?: emptyList()
                    
                    db.collectionDao().replaceCrossRefsForCollection(detail.id, crossRefs)
                    
                    val bookIds = crossRefs.map { it.bookId }
                    val serverUrl = preferencesManager.getServerUrl() ?: ""
                    val bookCovers = bookIds.map { bookId ->
                        val book = bookDao.getBookById(bookId)
                        book?.coverPath.takeIf { !it.isNullOrEmpty() }
                            ?: "${serverUrl.trimEnd('/')}/api/items/$bookId/cover"
                    }
                    entity.toDomain(bookIds, bookCovers)
                }
            }
        }
    }

    override fun getBooksForCollectionFlow(collectionId: String): Flow<List<BookWithProgress>> {
        return bookDao.getBooksForCollectionWithProgressFlow(collectionId).map { list -> list.map { it.toDomain() } }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getPlaylistsFlow(): Flow<List<Playlist>> {
        val serverUrl = preferencesManager.getServerUrl() ?: ""
        return db.playlistDao().getPlaylistsFlow().flatMapLatest { playlists ->
            if (playlists.isEmpty()) return@flatMapLatest flowOf(emptyList())
            val flows = playlists.map { entity ->
                db.playlistDao().getPlaylistItemsFlow(entity.id).map { itemEntities ->
                    val items = itemEntities.map { item ->
                        val book = bookDao.getBookById(item.libraryItemId)
                        val coverPath = book?.coverPath.takeIf { !it.isNullOrEmpty() }
                            ?: "${serverUrl.trimEnd('/')}/api/items/${item.libraryItemId}/cover"
                        item.toDomain(coverPath)
                    }
                    entity.toDomain(items)
                }
            }
            combine(flows) { it.toList() }
        }
    }

    override suspend fun syncPlaylists(forceRefresh: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val storedEtag = if (forceRefresh) null else preferencesManager.getPlaylistsETag()
            val result = remoteDataSource.fetchPlaylists(storedEtag)
            when (result) {
                is NetworkResult.NotModified -> {
                    return@runCatching
                }
                is NetworkResult.Error -> {
                    throw Exception(result.message)
                }
                is NetworkResult.Success -> {
                    val newEtag = result.etag
                    if (!newEtag.isNullOrEmpty()) {
                        preferencesManager.savePlaylistsETag(newEtag)
                    }
                    val playlistsResponseList = result.data
                    val entities = playlistsResponseList.map { it.toEntity(System.currentTimeMillis()) }
                    val itemEntities = playlistsResponseList.flatMap { playlistDto ->
                        playlistDto.items.mapIndexed { index, itemDto ->
                            itemDto.toEntity(playlistDto.id, index)
                        }
                    }
                    db.playlistDao().replacePlaylists(entities, itemEntities)
                }
            }
        }
    }

    override suspend fun getPlaylistDetails(playlistId: String, forceRefresh: Boolean): Result<Playlist> = withContext(Dispatchers.IO) {
        runCatching {
            val local = db.playlistDao().getPlaylistById(playlistId)
            val serverUrl = preferencesManager.getServerUrl() ?: ""
            if (local != null && !forceRefresh) {
                val itemEntities = db.playlistDao().getPlaylistItems(playlistId)
                val items = itemEntities.map { item ->
                    val book = bookDao.getBookById(item.libraryItemId)
                    val coverPath = book?.coverPath.takeIf { !it.isNullOrEmpty() }
                        ?: "${serverUrl.trimEnd('/')}/api/items/${item.libraryItemId}/cover"
                    item.toDomain(coverPath)
                }
                return@runCatching local.toDomain(items)
            }
            val result = remoteDataSource.fetchPlaylistDetails(playlistId)
            when (result) {
                is NetworkResult.NotModified -> {
                    val itemEntities = db.playlistDao().getPlaylistItems(playlistId)
                    val items = itemEntities.map { item ->
                        val book = bookDao.getBookById(item.libraryItemId)
                        val coverPath = book?.coverPath.takeIf { !it.isNullOrEmpty() }
                            ?: "${serverUrl.trimEnd('/')}/api/items/${item.libraryItemId}/cover"
                        item.toDomain(coverPath)
                    }
                    local?.toDomain(items) ?: throw Exception("Playlist not found")
                }
                is NetworkResult.Error -> throw Exception(result.message)
                is NetworkResult.Success -> {
                    val detail = result.data
                    val entity = detail.toEntity(System.currentTimeMillis())
                    val itemEntities = detail.items.mapIndexed { index, itemDto ->
                        itemDto.toEntity(detail.id, index)
                    }
                    db.playlistDao().insertPlaylist(entity)
                    db.playlistDao().replacePlaylistItems(detail.id, itemEntities)
                    
                    val items = itemEntities.map { item ->
                        val book = bookDao.getBookById(item.libraryItemId)
                        val coverPath = book?.coverPath.takeIf { !it.isNullOrEmpty() }
                            ?: "${serverUrl.trimEnd('/')}/api/items/${item.libraryItemId}/cover"
                        item.toDomain(coverPath)
                    }
                    entity.toDomain(items)
                }
            }
        }
    }

    override suspend fun updatePlaylistItems(playlistId: String, items: List<PlaylistItem>): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val localPlaylist = db.playlistDao().getPlaylistById(playlistId) ?: throw Exception("Playlist not found")
            val newDuration = items.sumOf { it.duration }
            val itemEntities = items.mapIndexed { index, item ->
                PlaylistItemEntity(
                    id = item.id,
                    playlistId = playlistId,
                    libraryItemId = item.libraryItemId,
                    sequence = index,
                    title = item.title,
                    duration = item.duration
                )
            }
            db.playlistDao().replacePlaylistItems(playlistId, itemEntities)
            db.playlistDao().updatePlaylistStats(
                playlistId = playlistId,
                duration = newDuration,
                itemCount = items.size,
                lastUpdated = System.currentTimeMillis()
            )

            val updatePayload = PlaylistUpdatePayload(
                name = localPlaylist.name,
                description = localPlaylist.description,
                items = items.map { PlaylistUpdateItem(it.libraryItemId, null) }
            )
            val result = remoteDataSource.updatePlaylist(playlistId, updatePayload)
            if (result is NetworkResult.Error) {
                throw Exception(result.message)
            }
        }
    }

    override suspend fun updatePlaybackFinished(bookId: String, isFinished: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // 1. Sync to server
            remoteDataSource.updateProgressFinished(bookId, isFinished)

            // 2. Update local DB
            val book = bookDao.getBookById(bookId)
            val duration = book?.duration ?: 0.0
            val currentTime = if (isFinished) duration else 0.0
            val progress = if (isFinished) 1.0f else 0.0f
            
            val currentProgress = progressDao.getProgressForBook(bookId)
            if (currentProgress != null) {
                progressDao.insertProgress(
                    currentProgress.copy(
                        currentTime = currentTime,
                        progress = progress,
                        isFinished = isFinished,
                        lastUpdated = System.currentTimeMillis()
                    )
                )
            } else {
                progressDao.insertProgress(
                    PlaybackProgressEntity(
                        bookId = bookId,
                        currentTime = currentTime,
                        progress = progress,
                        isFinished = isFinished,
                        lastUpdated = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    override suspend fun discardProgress(bookId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // 1. Delete progress locally from database
            progressDao.deleteProgressForBook(bookId)

            // 2. Fetch server progress ID and delete from server
            val result = remoteDataSource.fetchCurrentUserProgress()
            if (result is NetworkResult.Success) {
                val progressList = result.data.mediaProgress
                val progressItem = progressList.find { it.libraryItemId == bookId }
                if (progressItem != null) {
                    remoteDataSource.deleteProgressFromServer(progressItem.id)
                }
            } else if (result is NetworkResult.Error) {
                throw Exception(result.message)
            }
        }
    }

    private suspend fun updateLocalPlaylist(dto: NetworkPlaylistResponse) {
        val entity = dto.toEntity(System.currentTimeMillis())
        val itemEntities = dto.items.mapIndexed { index, itemDto ->
            itemDto.toEntity(dto.id, index)
        }
        db.playlistDao().insertPlaylist(entity)
        db.playlistDao().replacePlaylistItems(dto.id, itemEntities)
    }

    override suspend fun addBookToPlaylist(playlistId: String, bookId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val items = listOf(PlaylistUpdateItem(libraryItemId = bookId, episodeId = null))
            val result = remoteDataSource.addItemsToPlaylist(playlistId, items)
            when (result) {
                is NetworkResult.Success -> {
                    updateLocalPlaylist(result.data)
                }
                is NetworkResult.Error -> throw Exception(result.message)
                is NetworkResult.NotModified -> {}
            }
        }
    }

    override suspend fun createPlaylistWithBook(name: String, libraryId: String, bookId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val items = listOf(PlaylistUpdateItem(libraryItemId = bookId, episodeId = null))
            val result = remoteDataSource.createPlaylist(name, libraryId, items)
            when (result) {
                is NetworkResult.Success -> {
                    updateLocalPlaylist(result.data)
                }
                is NetworkResult.Error -> throw Exception(result.message)
                is NetworkResult.NotModified -> {}
            }
        }
    }

    override suspend fun removePlaylistItem(playlistId: String, bookId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val result = remoteDataSource.removePlaylistItem(playlistId, bookId)
            when (result) {
                is NetworkResult.Success -> {
                    updateLocalPlaylist(result.data)
                }
                is NetworkResult.Error -> throw Exception(result.message)
                is NetworkResult.NotModified -> {}
            }
        }
    }

    override fun getSeriesByIdFlow(seriesId: String): Flow<Series?> {
        return db.seriesDao().getSeriesByIdFlow(seriesId).map { it?.toDomain() }
    }

    override fun getPlaylistsContainingBookFlow(bookId: String): Flow<List<Playlist>> {
        return db.playlistDao().getPlaylistsContainingBookFlow(bookId).map { list ->
            list.map { entity -> entity.toDomain(items = emptyList()) }
        }
    }

    override fun getCollectionsContainingBookFlow(bookId: String): Flow<List<BookCollection>> {
        return db.collectionDao().getCollectionsContainingBookFlow(bookId).map { list ->
            list.map { entity -> entity.toDomain(bookIds = emptyList(), bookCovers = emptyList()) }
        }
    }
}


