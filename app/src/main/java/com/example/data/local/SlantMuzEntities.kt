package com.example.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.example.domain.SourceProvider
import com.example.domain.StreamCandidate
import com.example.domain.Track
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val entityJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val artworkUrl: String = "",
    val durationSec: Int = 0,
    val primaryProviderName: String = SourceProvider.ITUNES.name,
    val streamUrl: String = "",
    val fallbackStreamsJson: String = "[]",
    val isLiveStream: Boolean = false,
    val isPreviewOnly: Boolean = false,
    val genre: String = "Music",
    val releaseYear: String = "",
    val localUri: String? = null,
    val isFavorite: Boolean = false,
    val isInHistory: Boolean = false,
    val lastPlayedAt: Long = 0L,
    val playCount: Int = 0,
    val isCachedOffline: Boolean = false,
    val offlineFilePath: String? = null,
    val offlineSizeBytes: Long = 0L
) {
    fun toDomain(): Track {
        val provider = runCatching { SourceProvider.valueOf(primaryProviderName) }
            .getOrDefault(SourceProvider.ITUNES)
        val candidates = runCatching {
            entityJson.decodeFromString<List<StreamCandidate>>(fallbackStreamsJson)
        }.getOrDefault(emptyList())

        return Track(
            id = id,
            title = title,
            artist = artist,
            album = album,
            artworkUrl = artworkUrl,
            durationSec = durationSec,
            primaryProvider = provider,
            streamUrl = streamUrl,
            fallbackStreams = candidates,
            isLiveStream = isLiveStream,
            isPreviewOnly = isPreviewOnly,
            genre = genre,
            releaseYear = releaseYear,
            localUri = localUri,
            isFavorite = isFavorite,
            isCachedOffline = isCachedOffline && !offlineFilePath.isNullOrBlank(),
            offlineFilePath = offlineFilePath
        )
    }

    companion object {
        fun fromDomain(
            track: Track,
            isFavorite: Boolean = track.isFavorite,
            isInHistory: Boolean = false,
            lastPlayedAt: Long = 0L,
            playCount: Int = 0,
            isCachedOffline: Boolean = track.isCachedOffline,
            offlineFilePath: String? = track.offlineFilePath,
            offlineSizeBytes: Long = 0L
        ): TrackEntity {
            val fallbackJson = runCatching {
                entityJson.encodeToString(track.allStreamCandidates())
            }.getOrDefault("[]")

            return TrackEntity(
                id = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                artworkUrl = track.artworkUrl,
                durationSec = track.durationSec,
                primaryProviderName = track.primaryProvider.name,
                streamUrl = track.streamUrl,
                fallbackStreamsJson = fallbackJson,
                isLiveStream = track.isLiveStream,
                isPreviewOnly = track.isPreviewOnly,
                genre = track.genre,
                releaseYear = track.releaseYear,
                localUri = track.localUri,
                isFavorite = isFavorite,
                isInHistory = isInHistory,
                lastPlayedAt = lastPlayedAt,
                playCount = playCount,
                isCachedOffline = isCachedOffline,
                offlineFilePath = offlineFilePath,
                offlineSizeBytes = offlineSizeBytes
            )
        }
    }
}

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val playlistId: Long = 0L,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_track_cross_ref",
    primaryKeys = ["playlistId", "id"],
    indices = [Index("id"), Index("playlistId")]
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val id: String,
    val addedAt: Long = System.currentTimeMillis()
)

data class PlaylistWithTracks(
    @Embedded val playlist: PlaylistEntity,
    @Relation(
        parentColumn = "playlistId",
        entityColumn = "id",
        associateBy = Junction(PlaylistTrackCrossRef::class)
    )
    val tracks: List<TrackEntity>
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "cached_lyrics")
data class CachedLyricsEntity(
    @PrimaryKey val trackKey: String,
    val title: String,
    val artist: String,
    val syncedLrc: String,
    val plainLyrics: String,
    val isInstrumental: Boolean,
    val updatedAt: Long = System.currentTimeMillis()
)
