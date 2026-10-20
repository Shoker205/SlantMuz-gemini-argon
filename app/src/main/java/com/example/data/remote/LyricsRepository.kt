package com.example.data.remote

import com.example.data.local.CachedLyricsEntity
import com.example.data.local.SlantMuzDao
import com.example.domain.ArtistBioInfo
import com.example.domain.LrcParser
import com.example.domain.SyncedLyrics
import com.example.domain.Track
import com.example.domain.TrackDeduplicationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 8. LRCLIB API (https://lrclib.net/api/get & /api/search) + TheAudioDB artist biography enrichment.
 */
class LyricsRepository(private val dao: SlantMuzDao) {

    suspend fun getLyricsForTrack(track: Track): SyncedLyrics = withContext(Dispatchers.IO) {
        val cleanTitle = TrackDeduplicationEngine.normalizeText(track.title)
            .ifBlank { track.title }
        val cleanArtist = TrackDeduplicationEngine.normalizeText(track.artist)
            .ifBlank { track.artist }
        val cacheKey = "${cleanArtist}_${cleanTitle}".lowercase()

        // 1. Check Room Cache first
        val cached = runCatching { dao.getCachedLyrics(cacheKey) }.getOrNull()
        if (cached != null) {
            if (cached.syncedLrc.isNotBlank()) {
                return@withContext LrcParser.parseLrc(
                    rawLrc = cached.syncedLrc,
                    trackId = track.id,
                    title = track.title,
                    artist = track.artist
                )
            }
            return@withContext SyncedLyrics(
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                isInstrumental = cached.isInstrumental,
                isSynced = false,
                lines = emptyList(),
                plainLyrics = cached.plainLyrics
            )
        }

        // 2. Query LRCLIB /api/get first, then fallback to /api/search
        val fetched = fetchFromLrclib(track, cleanTitle, cleanArtist)
        if (fetched != null) {
            runCatching {
                dao.insertCachedLyrics(
                    CachedLyricsEntity(
                        trackKey = cacheKey,
                        title = track.title,
                        artist = track.artist,
                        syncedLrc = fetched.first,
                        plainLyrics = fetched.second,
                        isInstrumental = fetched.third
                    )
                )
            }
            if (fetched.first.isNotBlank()) {
                return@withContext LrcParser.parseLrc(
                    rawLrc = fetched.first,
                    trackId = track.id,
                    title = track.title,
                    artist = track.artist
                )
            }
            return@withContext SyncedLyrics(
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                isInstrumental = fetched.third,
                isSynced = false,
                lines = emptyList(),
                plainLyrics = fetched.second
            )
        }

        SyncedLyrics(
            trackId = track.id,
            title = track.title,
            artist = track.artist,
            isInstrumental = track.isLiveStream,
            isSynced = false,
            lines = emptyList(),
            plainLyrics = if (track.isLiveStream) {
                "Прямой радиоэфир (${track.title})\nСинхронизированный текст недоступен для LIVE-потоков."
            } else {
                ""
            }
        )
    }

    private fun fetchFromLrclib(
        track: Track,
        cleanTitle: String,
        cleanArtist: String
    ): Triple<String, String, Boolean>? {
        // Attempt direct /api/get
        val directResult = runCatching {
            val tEnc = HttpClientProvider.encode(track.title)
            val aEnc = HttpClientProvider.encode(track.artist)
            val url = "https://lrclib.net/api/get?track_name=$tEnc&artist_name=$aEnc"
            val body = HttpClientProvider.getBodyString(url)
            val obj = HttpClientProvider.json.parseToJsonElement(body).jsonObject
            val synced = obj["syncedLyrics"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val plain = obj["plainLyrics"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val instrumental = obj["instrumental"]?.jsonPrimitive?.booleanOrNull ?: false
            if (synced.isNotBlank() || plain.isNotBlank() || instrumental) {
                Triple(synced, plain, instrumental)
            } else {
                null
            }
        }.getOrNull()

        if (directResult != null) return directResult

        // Fallback to /api/search?q=...
        return runCatching {
            val qEnc = HttpClientProvider.encode("$cleanArtist $cleanTitle")
            val url = "https://lrclib.net/api/search?q=$qEnc"
            val body = HttpClientProvider.getBodyString(url)
            val arr = HttpClientProvider.json.parseToJsonElement(body).jsonArray
            if (arr.isEmpty()) return@runCatching null

            val bestObj = arr.map { it.jsonObject }.firstOrNull {
                !it["syncedLyrics"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()
            } ?: arr.first().jsonObject

            val synced = bestObj["syncedLyrics"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val plain = bestObj["plainLyrics"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val instrumental = bestObj["instrumental"]?.jsonPrimitive?.booleanOrNull ?: false
            Triple(synced, plain, instrumental)
        }.getOrNull()
    }

    suspend fun fetchArtistBio(artistName: String): ArtistBioInfo? = withContext(Dispatchers.IO) {
        val cleanName = artistName.substringBefore("•").substringBefore(",").trim()
        if (cleanName.isBlank()) return@withContext null
        runCatching {
            val encoded = HttpClientProvider.encode(cleanName)
            val url = "https://www.theaudiodb.com/api/v1/json/2/search.php?s=$encoded"
            val body = HttpClientProvider.getBodyString(url)
            val root = HttpClientProvider.json.parseToJsonElement(body).jsonObject
            val artists = root["artists"] as? JsonArray ?: return@runCatching null
            val first = artists.firstOrNull()?.jsonObject ?: return@runCatching null

            val bio = first["strBiographyRU"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: first["strBiographyEN"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val banner = first["strArtistFanart"]?.jsonPrimitive?.contentOrNull
                ?: first["strArtistBanner"]?.jsonPrimitive?.contentOrNull
                ?: first["strArtistThumb"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val country = first["strCountry"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val style = first["strGenre"]?.jsonPrimitive?.contentOrNull
                ?: first["strStyle"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val year = first["intFormedYear"]?.jsonPrimitive?.contentOrNull.orEmpty()

            if (bio.isBlank() && banner.isBlank()) null
            else ArtistBioInfo(
                artistName = first["strArtist"]?.jsonPrimitive?.contentOrNull ?: cleanName,
                biography = bio,
                bannerUrl = banner,
                country = country,
                style = style,
                formedYear = year
            )
        }.getOrNull()
    }
}
