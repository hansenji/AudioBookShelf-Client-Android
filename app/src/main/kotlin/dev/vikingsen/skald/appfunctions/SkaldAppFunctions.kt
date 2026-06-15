package dev.vikingsen.skald.appfunctions

import androidx.appfunctions.service.AppFunction
import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionDisabledException
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import dev.vikingsen.skald.core.model.PlaybackConstants
import dev.vikingsen.skald.core.player.PlayerManager
import dev.vikingsen.skald.domain.repository.AudiobookshelfRepository
import dev.vikingsen.skald.domain.repository.SettingsRepository
import dev.vikingsen.skald.domain.usecase.DownloadAudioFileUseCase
import dev.vikingsen.skald.domain.usecase.FetchBookDetailsUseCase
import dev.vikingsen.skald.domain.usecase.GetPlaylistsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Exposes audiobook and podcast capabilities.
 *
 * All functions require the user to be logged in to their Audiobookshelf server.
 */
class SkaldAppFunctions(
    private val playerManager: PlayerManager,
    private val repository: AudiobookshelfRepository,
    private val settingsRepository: SettingsRepository,
    private val fetchBookDetailsUseCase: FetchBookDetailsUseCase,
    private val downloadAudioFileUseCase: DownloadAudioFileUseCase,
    private val getPlaylistsUseCase: GetPlaylistsUseCase,
) {

    // -------------------------------------------------------------------------
    // Playback Control
    // -------------------------------------------------------------------------

    /**
     * Resumes playback of the currently loaded audiobook or podcast.
     *
     * @param appFunctionContext The execution context.
     * @throws AppFunctionDisabledException If no audiobook is currently loaded. Suggest the user play an audiobook first.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun resumeCurrentPlayback(appFunctionContext: AppFunctionContext) =
        withContext(Dispatchers.Main) {
            val current = playerManager.currentBook.first()
            if (current != null) {
                playerManager.play()
                return@withContext
            }

            // Fallback: If no book is loaded in the player, find the most recently played unfinished book
            val libraryId = settingsRepository.getLibraryId()
                ?: throw AppFunctionDisabledException(
                    "No library is selected. Please open Skald to choose a library."
                )

            val allBooks = withContext(Dispatchers.IO) {
                repository.getBooksWithProgressForLibraryFlow(libraryId).first()
            }

            val mostRecent = allBooks
                .filter { bwp ->
                    val p = bwp.progress
                    p != null && !p.isFinished && p.currentTime > 0.0
                }
                .maxByOrNull { bwp -> bwp.progress?.lastUpdated ?: 0L }

            if (mostRecent != null) {
                val book = withContext(Dispatchers.IO) {
                    fetchBookDetailsUseCase(mostRecent.book.id).getOrNull()
                }
                if (book != null) {
                    playerManager.playBook(book, mostRecent.progress?.currentTime ?: 0.0)
                    return@withContext
                }
            }

            throw AppFunctionDisabledException(
                "No audiobook is currently loaded or in-progress. Use playAudiobook or searchAndPlayAudiobook first."
            )
        }

    /**
     * Pauses the currently playing audiobook or podcast.
     *
     * @param appFunctionContext The execution context.
     * @throws AppFunctionDisabledException If no audiobook is currently playing. Suggest the user resume playback first.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun pauseCurrentPlayback(appFunctionContext: AppFunctionContext) =
        withContext(Dispatchers.Main) {
            if (playerManager.currentBook.first() == null) {
                throw AppFunctionDisabledException(
                    "No audiobook is currently playing."
                )
            }
            playerManager.pause()
        }

    /**
     * Plays a specific audiobook by its Audiobookshelf library item ID.
     *
     * Required workflow: Call [searchAndPlayAudiobook] first if only the title, author, or narrator is known.
     *
     * @param appFunctionContext The execution context.
     * @param bookId The Audiobookshelf library item ID (UUID) of the audiobook to play.
     * @param startFromBeginning True to start playback from position 0, ignoring saved progress. Defaults to false.
     * @throws AppFunctionDisabledException If the user is not logged in. Suggest the user log in.
     * @throws AppFunctionInvalidArgumentException If the bookId is blank.
     * @throws AppFunctionElementNotFoundException If the book cannot be found. Suggest the user verify the book ID.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun playAudiobook(
        appFunctionContext: AppFunctionContext,
        bookId: String,
        startFromBeginning: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        requireLoggedIn()
        if (bookId.isBlank()) {
            throw AppFunctionInvalidArgumentException("bookId must not be blank.")
        }

        val book = fetchBookDetailsUseCase(bookId).getOrElse { cause ->
            throw AppFunctionElementNotFoundException(
                "Could not load book details: ${cause.message}"
            )
        }

        val startPosition = if (startFromBeginning) {
            0.0
        } else {
            repository.getBookWithProgressFlow(bookId).first().second?.currentTime ?: 0.0
        }

        withContext(Dispatchers.Main) {
            playerManager.playBook(book, startPosition)
        }
    }

    /**
     * Searches the local library for an audiobook matching the query and plays it.
     * Searches across title, author, and narrator.
     *
     * @param appFunctionContext The execution context.
     * @param query The search text (title, author, or narrator name).
     * @throws AppFunctionDisabledException If the user is not logged in or no library is selected. Suggest logging in or selecting a library.
     * @throws AppFunctionInvalidArgumentException If the query is blank.
     * @throws AppFunctionElementNotFoundException If no matching book is found. Suggest the user search for a different query.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun searchAndPlayAudiobook(
        appFunctionContext: AppFunctionContext,
        query: String,
    ) = withContext(Dispatchers.IO) {
        requireLoggedIn()
        if (query.isBlank()) {
            throw AppFunctionInvalidArgumentException("A search query is required.")
        }
        val libraryId = settingsRepository.getLibraryId()
            ?: throw AppFunctionDisabledException(
                "No library is selected. Please open Skald to choose a library."
            )

        // Find all books in the library matching the query
        val allBooks = repository.getBooksWithProgressForLibraryFlow(libraryId).first()
        val matchingBooks = allBooks.filter { bwp ->
            val b = bwp.book
            b.title.contains(query, ignoreCase = true) ||
                b.author.contains(query, ignoreCase = true) ||
                b.narrator.contains(query, ignoreCase = true)
        }

        if (matchingBooks.isEmpty()) {
            throw AppFunctionElementNotFoundException(
                "No audiobook found matching '$query'."
            )
        }

        // Prioritize currently reading matches (unfinished books with progress)
        val currentlyReadingMatches = matchingBooks.filter { bwp ->
            val p = bwp.progress
            p != null && !p.isFinished && p.currentTime > 0.0
        }
        val candidates = currentlyReadingMatches.ifEmpty { matchingBooks }

        // Prefer exact title match, then partial title, then author/narrator, then most recently played
        val best = candidates.sortedWith(
            compareBy<dev.vikingsen.skald.core.model.BookWithProgress> { bwp ->
                when {
                    bwp.book.title.equals(query, ignoreCase = true) -> 0
                    bwp.book.title.contains(query, ignoreCase = true) -> 1
                    bwp.book.author.contains(query, ignoreCase = true) -> 2
                    else -> 3
                }
            }.thenByDescending { bwp ->
                bwp.progress?.lastUpdated ?: 0L
            }
        ).first()

        val book = fetchBookDetailsUseCase(best.book.id).getOrElse { cause ->
            throw AppFunctionElementNotFoundException(
                "Could not load book details: ${cause.message}"
            )
        }
        val startPosition = best.progress?.currentTime ?: 0.0

        withContext(Dispatchers.Main) {
            playerManager.playBook(book, startPosition)
        }
    }

    // -------------------------------------------------------------------------
    // Player Settings
    // -------------------------------------------------------------------------

    /**
     * Sets or cancels a sleep timer for the currently playing audiobook.
     *
     * @param appFunctionContext The execution context.
     * @param minutes Duration in minutes before playback pauses. Use 0 to cancel. Must be between 0 and 480.
     * @param endOfChapter True to pause playback at the end of the current chapter. Takes priority over minutes.
     * @throws AppFunctionDisabledException If no audiobook is loaded. Suggest playing an audiobook first.
     * @throws AppFunctionInvalidArgumentException If minutes is not between 0 and 480.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun setSleepTimer(
        appFunctionContext: AppFunctionContext,
        minutes: Int = 0,
        endOfChapter: Boolean = false,
    ) = withContext(Dispatchers.Main) {
        if (playerManager.currentBook.first() == null) {
            throw AppFunctionDisabledException(
                "No audiobook is currently loaded."
            )
        }
        when {
            endOfChapter -> playerManager.setSleepTimerEndOfChapter()
            minutes == 0 -> playerManager.cancelSleepTimer()
            minutes < 0 || minutes > 480 -> throw AppFunctionInvalidArgumentException(
                "minutes must be between 1 and 480 (or 0 to cancel)."
            )
            else -> playerManager.setSleepTimer(minutes)
        }
    }

    /**
     * Sets the playback speed.
     *
     * @param appFunctionContext The execution context.
     * @param speed The playback speed multiplier. Must be between 0.5 and 2.0 inclusive.
     * @throws AppFunctionInvalidArgumentException If the speed is outside the valid range.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun setPlaybackSpeed(
        appFunctionContext: AppFunctionContext,
        speed: Float,
    ) = withContext(Dispatchers.Main) {
        if (speed !in PlaybackConstants.SPEED_RANGE) {
            throw AppFunctionInvalidArgumentException(
                "speed must be between ${PlaybackConstants.SPEED_RANGE.start} " +
                    "and ${PlaybackConstants.SPEED_RANGE.endInclusive}."
            )
        }
        // Snap to the nearest valid 0.1-step value
        val snapped = PlaybackConstants.PLAYBACK_SPEEDS.minByOrNull { kotlin.math.abs(it - speed) }!!
        playerManager.setPlaybackSpeed(snapped)
    }

    // -------------------------------------------------------------------------
    // Library Actions
    // -------------------------------------------------------------------------

    /**
     * Plays a saved playlist from the beginning.
     *
     * @param appFunctionContext The execution context.
     * @param playlistId The Audiobookshelf playlist ID. Preferred for precision.
     * @param playlistName The playlist name used for lookup when playlistId is unavailable.
     * @throws AppFunctionDisabledException If the user is not logged in. Suggest the user log in.
     * @throws AppFunctionInvalidArgumentException If both playlistId and playlistName are blank, or the playlist is empty.
     * @throws AppFunctionElementNotFoundException If no matching playlist is found. Suggest checking the playlist name or ID.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun playPlaylist(
        appFunctionContext: AppFunctionContext,
        playlistId: String? = null,
        playlistName: String? = null,
    ) = withContext(Dispatchers.IO) {
        requireLoggedIn()
        if (playlistId.isNullOrBlank() && playlistName.isNullOrBlank()) {
            throw AppFunctionInvalidArgumentException(
                "At least one of playlistId or playlistName must be provided."
            )
        }

        val playlists = getPlaylistsUseCase().first()

        val playlist = when {
            !playlistId.isNullOrBlank() -> playlists.find { it.id == playlistId }
            else -> {
                val matches = playlists.filter { it.name.contains(playlistName ?: "", ignoreCase = true) }
                matches.sortedWith(
                    compareByDescending<dev.vikingsen.skald.core.model.Playlist> { it.name.equals(playlistName, ignoreCase = true) }
                        .thenByDescending { it.lastUpdated }
                ).firstOrNull()
            }
        } ?: throw AppFunctionElementNotFoundException(
            "No playlist found matching '${playlistId ?: playlistName}'."
        )

        if (playlist.items.isEmpty()) {
            throw AppFunctionInvalidArgumentException(
                "Playlist '${playlist.name}' is empty."
            )
        }

        withContext(Dispatchers.Main) {
            playerManager.playPlaylist(playlist, startIndex = 0, startPosition = 0.0)
        }
    }

    /**
     * Enqueues an audiobook for offline download.
     *
     * @param appFunctionContext The execution context.
     * @param bookId The Audiobookshelf library item ID (UUID) of the audiobook to download.
     * @throws AppFunctionDisabledException If the user is not logged in, or the download fails to start. Suggest checking the server connection.
     * @throws AppFunctionInvalidArgumentException If the bookId is blank.
     * @throws AppFunctionElementNotFoundException If the book cannot be found.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun downloadAudiobook(
        appFunctionContext: AppFunctionContext,
        bookId: String,
    ) = withContext(Dispatchers.IO) {
        requireLoggedIn()
        if (bookId.isBlank()) {
            throw AppFunctionInvalidArgumentException("bookId must not be blank.")
        }

        val book = fetchBookDetailsUseCase(bookId).getOrElse { cause ->
            throw AppFunctionElementNotFoundException(
                "Could not find book details: ${cause.message}"
            )
        }

        if (book.isDownloaded) return@withContext   // already downloaded — idempotent

        downloadAudioFileUseCase(bookId).getOrElse { cause ->
            throw AppFunctionDisabledException(
                "Failed to start download: ${cause.message}"
            )
        }
    }

    /**
     * Searches the local library for an audiobook matching the query and starts downloading it.
     * Searches across title, author, and narrator.
     *
     * @param appFunctionContext The execution context.
     * @param query The search text (title, author, or narrator name).
     * @throws AppFunctionDisabledException If the user is not logged in, no library is selected, or the download fails to start.
     * @throws AppFunctionInvalidArgumentException If the query is blank.
     * @throws AppFunctionElementNotFoundException If no matching book is found.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun searchAndDownloadAudiobook(
        appFunctionContext: AppFunctionContext,
        query: String,
    ) = withContext(Dispatchers.IO) {
        requireLoggedIn()
        if (query.isBlank()) {
            throw AppFunctionInvalidArgumentException("A search query is required.")
        }
        val libraryId = settingsRepository.getLibraryId()
            ?: throw AppFunctionDisabledException(
                "No library is selected. Please open Skald to choose a library."
            )

        // Find all books in the library matching the query
        val allBooks = repository.getBooksWithProgressForLibraryFlow(libraryId).first()
        val matchingBooks = allBooks.filter { bwp ->
            val b = bwp.book
            b.title.contains(query, ignoreCase = true) ||
                b.author.contains(query, ignoreCase = true) ||
                b.narrator.contains(query, ignoreCase = true)
        }

        if (matchingBooks.isEmpty()) {
            throw AppFunctionElementNotFoundException(
                "No audiobook found matching '$query'."
            )
        }

        // Prioritize currently reading matches (unfinished books with progress)
        val currentlyReadingMatches = matchingBooks.filter { bwp ->
            val p = bwp.progress
            p != null && !p.isFinished && p.currentTime > 0.0
        }
        val candidates = currentlyReadingMatches.ifEmpty { matchingBooks }

        // Prefer exact title match, then partial title, then author/narrator, then most recently played
        val best = candidates.sortedWith(
            compareBy<dev.vikingsen.skald.core.model.BookWithProgress> { bwp ->
                when {
                    bwp.book.title.equals(query, ignoreCase = true) -> 0
                    bwp.book.title.contains(query, ignoreCase = true) -> 1
                    bwp.book.author.contains(query, ignoreCase = true) -> 2
                    else -> 3
                }
            }.thenByDescending { bwp ->
                bwp.progress?.lastUpdated ?: 0L
            }
        ).first()

        val book = fetchBookDetailsUseCase(best.book.id).getOrElse { cause ->
            throw AppFunctionElementNotFoundException(
                "Could not load book details: ${cause.message}"
            )
        }

        if (book.isDownloaded) return@withContext   // already downloaded — idempotent

        downloadAudioFileUseCase(best.book.id).getOrElse { cause ->
            throw AppFunctionDisabledException(
                "Failed to start download: ${cause.message}"
            )
        }
    }


    // -------------------------------------------------------------------------
    // Progress Management
    // -------------------------------------------------------------------------

    /**
     * Marks an audiobook as finished or unfinished.
     *
     * @param appFunctionContext The execution context.
     * @param bookId The Audiobookshelf library item ID (UUID) of the audiobook.
     * @param isFinished True to mark the book as finished; false to mark it as unfinished.
     * @throws AppFunctionDisabledException If not logged in or the update failed. Suggest checking the server connection.
     * @throws AppFunctionInvalidArgumentException If the bookId is blank.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun markBookFinished(
        appFunctionContext: AppFunctionContext,
        bookId: String,
        isFinished: Boolean = true,
    ) = withContext(Dispatchers.IO) {
        requireLoggedIn()
        if (bookId.isBlank()) {
            throw AppFunctionInvalidArgumentException("bookId must not be blank.")
        }

        repository.updatePlaybackFinished(bookId, isFinished).getOrElse { cause ->
            throw AppFunctionDisabledException(
                "Failed to update finished status: ${cause.message}"
            )
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun requireLoggedIn() {
        if (!settingsRepository.isLoggedIn()) {
            throw AppFunctionDisabledException(
                "Not logged in to Skald. Please open the app and connect to your Audiobookshelf server."
            )
        }
    }
}
