package dev.vikingsen.skald.domain.fakes

import dev.vikingsen.skald.core.model.Library
import dev.vikingsen.skald.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

open class FakeSettingsRepository : SettingsRepository {
    var fakeUrl: String? = null
    var fakeUsername: String? = null
    var fakeToken: String? = null
    var fakeLibraryId: String? = null
    var fakeReadStatusFilter: String? = null
    var fakeSortOption: String? = null
    var fakeDownloadedOnly: Boolean = false
    var fakeSkipForwardDuration: Int = 30
    var fakeSkipBackwardDuration: Int = 10
    var fakePlaybackSpeed: Float = 1.0f
    var fakeGoBackOnInterrupt: Boolean = true
    var syncIntervalHours: Int = 24
    var lastSyncTimestamp: Long = 0L
    val etags = mutableMapOf<String, String>()
    var fakeAuthorsSortOption: String? = null
    var fakeSeriesFilter: String? = null
    var fakeSeriesSortOption: String? = null
    var cachedLibraries = mutableListOf<Library>()
    var hideEmptyLibraryTabs = MutableStateFlow(false)
    var fakeCollectionsSortOption: String? = null
    var fakePlaylistsSortOption: String? = null

    override fun getServerUrl(): String? = fakeUrl
    override fun getUsername(): String? = fakeUsername
    override fun getToken(): String? = fakeToken
    override fun getLibraryId(): String? = fakeLibraryId

    override suspend fun saveConnectionDetails(url: String, username: String, token: String) {
        this.fakeUrl = url
        this.fakeUsername = username
        this.fakeToken = token
    }

    override fun saveLibraryId(libraryId: String) {
        this.fakeLibraryId = libraryId
    }

    override fun getReadStatusFilter(): String? = fakeReadStatusFilter
    override fun saveReadStatusFilter(filter: String) {
        this.fakeReadStatusFilter = filter
    }

    override fun getSortOption(): String? = fakeSortOption
    override fun saveSortOption(sort: String) {
        this.fakeSortOption = sort
    }

    override fun getDownloadedOnlyFilter(): Boolean = fakeDownloadedOnly
    override fun saveDownloadedOnlyFilter(downloadedOnly: Boolean) {
        this.fakeDownloadedOnly = downloadedOnly
    }

    override fun isLoggedIn(): Boolean = !fakeToken.isNullOrEmpty() && !fakeUrl.isNullOrEmpty()

    override suspend fun clear() {
        fakeUrl = null
        fakeUsername = null
        fakeToken = null
        fakeLibraryId = null
        fakeReadStatusFilter = null
        fakeSortOption = null
        fakeDownloadedOnly = false
        etags.clear()
        cachedLibraries.clear()
    }

    override fun getSkipForwardDuration(): Int = fakeSkipForwardDuration
    override fun saveSkipForwardDuration(duration: Int) {
        this.fakeSkipForwardDuration = duration
    }

    override fun getSkipBackwardDuration(): Int = fakeSkipBackwardDuration
    override fun saveSkipBackwardDuration(duration: Int) {
        this.fakeSkipBackwardDuration = duration
    }

    override fun getPlaybackSpeed(): Float = fakePlaybackSpeed
    override fun savePlaybackSpeed(speed: Float) {
        this.fakePlaybackSpeed = speed
    }

    override fun getGoBackOnInterrupt(): Boolean = fakeGoBackOnInterrupt
    override fun saveGoBackOnInterrupt(enabled: Boolean) {
        this.fakeGoBackOnInterrupt = enabled
    }

    override fun getLibrarySyncIntervalHours(): Int = syncIntervalHours
    override fun saveLibrarySyncIntervalHours(hours: Int) {
        this.syncIntervalHours = hours
    }

    override fun getLibraryLastSyncTimestamp(): Long = lastSyncTimestamp
    override fun saveLibraryLastSyncTimestamp(timestamp: Long) {
        this.lastSyncTimestamp = timestamp
    }

    override fun getLibraryETag(libraryId: String): String? = etags["library_$libraryId"]
    override fun saveLibraryETag(libraryId: String, etag: String) {
        etags["library_$libraryId"] = etag
    }

    override fun getLibrarySeriesETag(libraryId: String): String? = etags["series_$libraryId"]
    override fun saveLibrarySeriesETag(libraryId: String, etag: String) {
        etags["series_$libraryId"] = etag
    }

    override fun getLibraryAuthorsETag(libraryId: String): String? = etags["authors_$libraryId"]
    override fun saveLibraryAuthorsETag(libraryId: String, etag: String) {
        etags["authors_$libraryId"] = etag
    }

    override fun getAuthorsSortOption(): String? = fakeAuthorsSortOption
    override fun saveAuthorsSortOption(sort: String) {
        this.fakeAuthorsSortOption = sort
    }

    override fun getSeriesFilter(): String? = fakeSeriesFilter
    override fun saveSeriesFilter(filter: String) {
        this.fakeSeriesFilter = filter
    }

    override fun getSeriesSortOption(): String? = fakeSeriesSortOption
    override fun saveSeriesSortOption(sort: String) {
        this.fakeSeriesSortOption = sort
    }

    override suspend fun getCachedLibraries(): List<Library> = cachedLibraries
    override suspend fun saveCachedLibraries(libraries: List<Library>) {
        this.cachedLibraries = libraries.toMutableList()
    }

    override fun getHideEmptyLibraryTabs(): Boolean = hideEmptyLibraryTabs.value
    override fun saveHideEmptyLibraryTabs(enabled: Boolean) {
        this.hideEmptyLibraryTabs.value = enabled
    }

    override fun observeHideEmptyLibraryTabs(): Flow<Boolean> = hideEmptyLibraryTabs

    override fun getLibraryCollectionsETag(libraryId: String): String? = etags["collections_$libraryId"]
    override fun saveLibraryCollectionsETag(libraryId: String, etag: String) {
        etags["collections_$libraryId"] = etag
    }

    override fun getCollectionsSortOption(): String? = fakeCollectionsSortOption
    override fun saveCollectionsSortOption(sort: String) {
        this.fakeCollectionsSortOption = sort
    }

    override fun getPlaylistsSortOption(): String? = fakePlaylistsSortOption
    override fun savePlaylistsSortOption(sort: String) {
        this.fakePlaylistsSortOption = sort
    }
}
