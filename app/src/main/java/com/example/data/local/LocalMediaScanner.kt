package com.example.data.local

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.domain.SourceProvider
import com.example.domain.StreamCandidate
import com.example.domain.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scans local audio files on the device via Android MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.
 */
class LocalMediaScanner(private val context: Context) {

    suspend fun scanDeviceAudio(): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()
        runCatching {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION
            )

            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                val albumArtBaseUri = Uri.parse("content://media/external/audio/albumart")

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: continue
                    val rawArtist = cursor.getString(artistCol).orEmpty()
                    val artist = if (rawArtist.isBlank() || rawArtist == "<unknown>") {
                        "Локальный исполнитель"
                    } else {
                        rawArtist
                    }
                    val album = cursor.getString(albumCol).orEmpty()
                    val albumId = cursor.getLong(albumIdCol)
                    val durationMs = cursor.getLong(durationCol)
                    if (durationMs < 3_000L) continue

                    val contentUri = ContentUris.withAppendedId(collection, id).toString()
                    val artworkUri = ContentUris.withAppendedId(albumArtBaseUri, albumId).toString()

                    tracks.add(
                        Track(
                            id = "local_$id",
                            title = title,
                            artist = artist,
                            album = album,
                            artworkUrl = artworkUri,
                            durationSec = (durationMs / 1000L).toInt(),
                            primaryProvider = SourceProvider.LOCAL,
                            streamUrl = contentUri,
                            fallbackStreams = listOf(
                                StreamCandidate(
                                    provider = SourceProvider.LOCAL,
                                    streamUrl = contentUri,
                                    bitrateKbps = 1411,
                                    isPreview = false,
                                    instanceHost = "DEVICE_STORAGE"
                                )
                            ),
                            localUri = contentUri,
                            genre = "Local Audio"
                        )
                    )
                }
            }
        }
        tracks
    }
}
