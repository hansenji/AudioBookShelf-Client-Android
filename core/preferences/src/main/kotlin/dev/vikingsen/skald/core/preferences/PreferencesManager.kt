package dev.vikingsen.skald.core.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import java.io.File
import androidx.datastore.tink.AeadSerializer
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplate
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.PredefinedAeadParameters
import com.google.crypto.tink.config.TinkConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

open class PreferencesManager(context: Context) {
    init {
        TinkConfig.register()
    }

    private val secureDataStore: DataStore<SecureTokens> = DataStoreFactory.create(
        serializer = AeadSerializer(
            aead = AndroidKeysetManager.Builder()
                .withSharedPref(context, "datastore_keyset_prefs", "datastore_keyset")
                .withKeyTemplate(KeyTemplate.createFrom(PredefinedAeadParameters.AES256_GCM))
                .withMasterKeyUri("android-keystore://skald_datastore_master_key")
                .build()
                .keysetHandle
                .getPrimitive(RegistryConfiguration.get(), Aead::class.java),
            wrappedSerializer = SecureTokensSerializer,
            associatedData = "skald_auth_tokens".encodeToByteArray()
        ),
        produceFile = { File(context.filesDir, "datastore/secure_tokens.pb") }
    )

    @Volatile
    private var cachedTokens: SecureTokens = runBlocking {
        try {
            secureDataStore.data.first()
        } catch (e: Exception) {
            SecureTokens()
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences("skald_prefs", Context.MODE_PRIVATE)

    private val _serverUrl = MutableStateFlow(getServerUrl())
    @get:JvmName("getServerUrlFlow")
    val serverUrl: StateFlow<String?> = _serverUrl.asStateFlow()

    private val _username = MutableStateFlow(getUsername())
    @get:JvmName("getUsernameFlow")
    val username: StateFlow<String?> = _username.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(isLoggedIn())
    val isLoggedInFlow: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _hideEmptyLibraryTabs = MutableStateFlow(getHideEmptyLibraryTabs())
    @get:JvmName("getHideEmptyLibraryTabsFlow")
    val hideEmptyLibraryTabs: StateFlow<Boolean> = _hideEmptyLibraryTabs.asStateFlow()

    suspend fun saveConnectionDetails(url: String, user: String, token: String, refreshToken: String? = null, userId: String? = null) {
        val sanitizedUrl = if (url.endsWith("/")) url.substring(0, url.length - 1) else url
        prefs.edit().apply {
            putString("server_url", sanitizedUrl)
            putString("username", user)
            putString("user_id", userId)
            apply()
        }
        saveTokensInternal(token, refreshToken)
        _serverUrl.value = sanitizedUrl
        _username.value = user
        _isLoggedIn.value = isLoggedIn()
    }

    open suspend fun saveTokens(accessToken: String?, refreshToken: String?) {
        saveTokensInternal(accessToken, refreshToken)
        _isLoggedIn.value = isLoggedIn()
    }

    open suspend fun clearTokens() {
        saveTokensInternal(null, null)
        _isLoggedIn.value = isLoggedIn()
    }

    private suspend fun saveTokensInternal(accessToken: String?, refreshToken: String?) {
        secureDataStore.updateData {
            val newTokens = SecureTokens(accessToken, refreshToken)
            cachedTokens = newTokens
            newTokens
        }
    }

    open fun getServerUrl(): String? = prefs.getString("server_url", null)
    fun getUsername(): String? = prefs.getString("username", null)
    fun getUserId(): String? = prefs.getString("user_id", null)
    open fun getToken(): String? = cachedTokens.accessToken
    open fun getRefreshToken(): String? = cachedTokens.refreshToken

    fun saveLibraryId(libraryId: String) {
        prefs.edit().putString("library_id", libraryId).apply()
    }

    fun getLibraryId(): String? = prefs.getString("library_id", null)

    fun saveReadStatusFilter(filter: String) {
        prefs.edit().putString("filter_read_status", filter).apply()
    }

    fun getReadStatusFilter(): String? = prefs.getString("filter_read_status", null)

    fun saveSortOption(sort: String) {
        prefs.edit().putString("sort_option", sort).apply()
    }

    fun getSortOption(): String? = prefs.getString("sort_option", null)

    fun saveDownloadedOnlyFilter(downloadedOnly: Boolean) {
        prefs.edit().putBoolean("filter_downloaded_only", downloadedOnly).apply()
    }

    fun getDownloadedOnlyFilter(): Boolean = prefs.getBoolean("filter_downloaded_only", false)

    suspend fun clear() {
        prefs.edit().clear().apply()
        saveTokensInternal(null, null)
        _serverUrl.value = null
        _username.value = null
        _isLoggedIn.value = false
        _hideEmptyLibraryTabs.value = false
    }

    fun getSkipForwardDuration(): Int = prefs.getInt("skip_forward_duration", 30)
    fun saveSkipForwardDuration(duration: Int) {
        prefs.edit().putInt("skip_forward_duration", duration).apply()
    }

    fun getSkipBackwardDuration(): Int = prefs.getInt("skip_backward_duration", 10)
    fun saveSkipBackwardDuration(duration: Int) {
        prefs.edit().putInt("skip_backward_duration", duration).apply()
    }

    fun getPlaybackSpeed(): Float = prefs.getFloat("playback_speed", 1.0f)
    fun savePlaybackSpeed(speed: Float) {
        prefs.edit().putFloat("playback_speed", speed).apply()
    }

    fun getGoBackOnInterrupt(): Boolean = prefs.getBoolean("go_back_on_interrupt", true)
    fun saveGoBackOnInterrupt(enabled: Boolean) {
        prefs.edit().putBoolean("go_back_on_interrupt", enabled).apply()
    }

    fun isLoggedIn(): Boolean = !getToken().isNullOrEmpty() && !getServerUrl().isNullOrEmpty()

    fun getLibrarySyncIntervalHours(): Int = prefs.getInt("library_sync_interval_hours", 24)
    fun saveLibrarySyncIntervalHours(hours: Int) {
        prefs.edit().putInt("library_sync_interval_hours", hours).apply()
    }

    fun getLibraryLastSyncTimestamp(): Long = prefs.getLong("library_last_sync_timestamp", 0L)
    fun saveLibraryLastSyncTimestamp(timestamp: Long) {
        prefs.edit().putLong("library_last_sync_timestamp", timestamp).apply()
    }

    fun getLibraryETag(libraryId: String): String? = prefs.getString("etag_library_$libraryId", null)
    fun saveLibraryETag(libraryId: String, etag: String) {
        prefs.edit().putString("etag_library_$libraryId", etag).apply()
    }

    fun getLibraryHomeETag(libraryId: String): String? = prefs.getString("etag_library_home_$libraryId", null)
    fun saveLibraryHomeETag(libraryId: String, etag: String) {
        prefs.edit().putString("etag_library_home_$libraryId", etag).apply()
    }

    fun getLibrarySeriesETag(libraryId: String): String? = prefs.getString("etag_library_series_$libraryId", null)
    fun saveLibrarySeriesETag(libraryId: String, etag: String) {
        prefs.edit().putString("etag_library_series_$libraryId", etag).apply()
    }

    fun getLibraryAuthorsETag(libraryId: String): String? = prefs.getString("etag_library_authors_$libraryId", null)
    fun saveLibraryAuthorsETag(libraryId: String, etag: String) {
        prefs.edit().putString("etag_library_authors_$libraryId", etag).apply()
    }

    fun getAuthorsSortOption(): String? = prefs.getString("sort_authors_option", null)
    fun saveAuthorsSortOption(sort: String) {
        prefs.edit().putString("sort_authors_option", sort).apply()
    }

    fun getSeriesFilter(): String? = prefs.getString("filter_series_status", null)
    fun saveSeriesFilter(filter: String) {
        prefs.edit().putString("filter_series_status", filter).apply()
    }

    fun getSeriesSortOption(): String? = prefs.getString("sort_series_option", null)
    fun saveSeriesSortOption(sort: String) {
        prefs.edit().putString("sort_series_option", sort).apply()
    }

    fun getUserETag(): String? = prefs.getString("etag_user", null)
    fun saveUserETag(etag: String) {
        prefs.edit().putString("etag_user", etag).apply()
    }

    fun getHideEmptyLibraryTabs(): Boolean = prefs.getBoolean("hide_empty_library_tabs", false)
    fun saveHideEmptyLibraryTabs(enabled: Boolean) {
        prefs.edit().putBoolean("hide_empty_library_tabs", enabled).apply()
        _hideEmptyLibraryTabs.value = enabled
    }

    fun getLibraryCollectionsETag(libraryId: String): String? = prefs.getString("etag_library_collections_$libraryId", null)
    fun saveLibraryCollectionsETag(libraryId: String, etag: String) {
        prefs.edit().putString("etag_library_collections_$libraryId", etag).apply()
    }

    fun getCollectionsSortOption(): String? = prefs.getString("sort_collections_option", null)
    fun saveCollectionsSortOption(sort: String) {
        prefs.edit().putString("sort_collections_option", sort).apply()
    }

    fun getPlaylistsETag(): String? = prefs.getString("etag_playlists", null)
    fun savePlaylistsETag(etag: String) {
        prefs.edit().putString("etag_playlists", etag).apply()
    }

    fun getPlaylistsSortOption(): String? = prefs.getString("sort_playlists_option", null)
    fun savePlaylistsSortOption(sort: String) {
        prefs.edit().putString("sort_playlists_option", sort).apply()
    }
}
