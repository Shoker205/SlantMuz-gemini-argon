package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SlantMuzDao {

    @Query("SELECT * FROM tracks WHERE isFavorite = 1 ORDER BY lastPlayedAt DESC, title ASC")
    fun observeFavoriteTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isInHistory = 1 ORDER BY lastPlayedAt DESC LIMIT 100")
    fun observeHistoryTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isCachedOffline = 1 ORDER BY lastPlayedAt DESC")
    fun observeOfflineCachedTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :trackId LIMIT 1")
    suspend fun getTrackById(trackId: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun getTracksByIds(ids: List<String>): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrack(track: TrackEntity)

    @Query("UPDATE tracks SET isFavorite = :isFavorite WHERE id = :trackId")
    suspend fun setFavorite(trackId: String, isFavorite: Boolean)

    @Query("UPDATE tracks SET isCachedOffline = :cached, offlineFilePath = :filePath, offlineSizeBytes = :sizeBytes WHERE id = :trackId")
    suspend fun updateOfflineCacheState(
        trackId: String,
        cached: Boolean,
        filePath: String?,
        sizeBytes: Long
    )

    @Query("UPDATE tracks SET isCachedOffline = 0, offlineFilePath = NULL, offlineSizeBytes = 0")
    suspend fun clearAllOfflineTrackRecords()

    @Query("SELECT COALESCE(SUM(offlineSizeBytes), 0) FROM tracks WHERE isCachedOffline = 1")
    fun observeTotalOfflineBytes(): Flow<Long>

    @Transaction
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observePlaylistsWithTracks(): Flow<List<PlaylistWithTracks>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE playlistId = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("DELETE FROM playlist_track_cross_ref WHERE playlistId = :playlistId")
    suspend fun deletePlaylistCrossRefs(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTrackCrossRef(ref: PlaylistTrackCrossRef)

    @Query("DELETE FROM playlist_track_cross_ref WHERE playlistId = :playlistId AND id = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String)

    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 15")
    fun observeSearchHistory(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchQuery(item: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteSearchQuery(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()

    @Query("SELECT * FROM cached_lyrics WHERE trackKey = :trackKey LIMIT 1")
    suspend fun getCachedLyrics(trackKey: String): CachedLyricsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedLyrics(lyrics: CachedLyricsEntity)
}
