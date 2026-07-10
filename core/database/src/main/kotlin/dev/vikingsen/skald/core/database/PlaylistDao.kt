package dev.vikingsen.skald.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllPlaylists(playlists: List<PlaylistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllPlaylistItems(items: List<PlaylistItemEntity>)

    @Query("DELETE FROM playlists")
    suspend fun deleteAllPlaylists()

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun deletePlaylistItemsForPlaylist(playlistId: String)

    @Query("SELECT * FROM playlists")
    fun getPlaylistsFlow(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: String): PlaylistEntity?

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY sequence ASC")
    fun getPlaylistItemsFlow(playlistId: String): Flow<List<PlaylistItemEntity>>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY sequence ASC")
    suspend fun getPlaylistItems(playlistId: String): List<PlaylistItemEntity>

    @Transaction
    suspend fun replacePlaylists(playlists: List<PlaylistEntity>, items: List<PlaylistItemEntity>) {
        deleteAllPlaylists()
        insertAllPlaylists(playlists)
        insertAllPlaylistItems(items)
    }

    @Transaction
    suspend fun replacePlaylistItems(playlistId: String, items: List<PlaylistItemEntity>) {
        deletePlaylistItemsForPlaylist(playlistId)
        insertAllPlaylistItems(items)
    }

    @Query("UPDATE playlists SET duration = :duration, itemCount = :itemCount, lastUpdated = :lastUpdated WHERE id = :playlistId")
    suspend fun updatePlaylistStats(playlistId: String, duration: Double, itemCount: Int, lastUpdated: Long)

    @Query("""
        SELECT p.* FROM playlists p
        INNER JOIN playlist_items pi ON p.id = pi.playlistId
        WHERE pi.libraryItemId = :bookId
    """)
    fun getPlaylistsContainingBookFlow(bookId: String): Flow<List<PlaylistEntity>>
}
