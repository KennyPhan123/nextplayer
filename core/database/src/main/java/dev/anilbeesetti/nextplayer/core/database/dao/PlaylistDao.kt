package dev.anilbeesetti.nextplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import dev.anilbeesetti.nextplayer.core.database.entities.PlaylistEntity
import dev.anilbeesetti.nextplayer.core.database.entities.PlaylistItemEntity
import kotlinx.coroutines.flow.Flow

data class PlaylistWithItemCount(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val itemCount: Int,
)

@Dao
interface PlaylistDao {

    @Insert
    suspend fun createPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlist WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("UPDATE playlist SET name = :name WHERE id = :id")
    suspend fun renamePlaylist(id: Long, name: String)

    @Query("""
        SELECT p.id, p.name, p.createdAt, COUNT(pi.id) as itemCount
        FROM playlist p
        LEFT JOIN playlist_item pi ON p.id = pi.playlistId
        GROUP BY p.id
        ORDER BY p.createdAt DESC
    """)
    fun getAllPlaylists(): Flow<List<PlaylistWithItemCount>>

    @Query("SELECT * FROM playlist_item WHERE playlistId = :playlistId ORDER BY position ASC")
    fun getPlaylistItems(playlistId: Long): Flow<List<PlaylistItemEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_item WHERE playlistId = :playlistId")
    suspend fun getNextPosition(playlistId: Long): Int

    @Insert
    suspend fun insertItem(item: PlaylistItemEntity)

    @Transaction
    suspend fun addItem(playlistId: Long, videoUri: String) {
        val position = getNextPosition(playlistId)
        insertItem(PlaylistItemEntity(playlistId = playlistId, videoUri = videoUri, position = position))
    }

    @Query("DELETE FROM playlist_item WHERE id = :id")
    suspend fun removeItem(id: Long)

    @Query("DELETE FROM playlist_item WHERE playlistId = :playlistId AND videoUri = :videoUri")
    suspend fun removeItemByUri(playlistId: Long, videoUri: String)
}
