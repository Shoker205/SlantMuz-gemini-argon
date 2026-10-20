package com.example.data.remote

import com.example.domain.SourceProvider
import com.example.domain.StreamCandidate
import com.example.domain.Track
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

data class SourceSearchResult(
    val provider: SourceProvider,
    val tracks: List<Track>,
    val latencyMs: Long,
    val activeMirror: String,
    val isOnline: Boolean,
    val error: String? = null
)

interface MusicSource {
    val provider: SourceProvider
    val activeMirrorHost: String
    suspend fun search(query: String, limit: Int = 15): SourceSearchResult
    suspend fun getTrending(limit: Int = 12): SourceSearchResult
    suspend fun resolvePlayableStreamUrl(track: Track): String? = track.streamUrl
}

object HttpClientProvider {
    const val USER_AGENT =
        "SlantMuz/1.0 (Android 14; SlantTech Audiophile Multi-Stream Engine; +https://slanttech.io)"

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(7, TimeUnit.SECONDS)
            .readTimeout(9, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(req)
            }
            .build()
    }

    fun getBodyString(url: String): String {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code} from $url")
            }
            return response.body?.string() ?: throw IllegalStateException("Empty body from $url")
        }
    }

    fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}

/**
 * 1. YouTube Music via Piped / Invidious mirror pool with automatic latency failover.
 */
class YouTubePipedInvidiousSource : MusicSource {
    override val provider: SourceProvider = SourceProvider.YOUTUBE_PIPED

    private val pipedMirrors = listOf(
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.tokhmi.xyz",
        "https://pipedapi.ducks.party",
        "https://api.piped.private.coffee"
    )

    private val invidiousMirrors = listOf(
        "https://inv.nadeko.net",
        "https://invidious.nerdvpn.de",
        "https://yewtu.be"
    )

    @Volatile
    private var bestMirror: String = pipedMirrors.first()

    override val activeMirrorHost: String
        get() = bestMirror.removePrefix("https://")

    suspend fun selectFastestInstance(): Pair<String, Long> = withContext(Dispatchers.IO) {
        coroutineScope {
            val allMirrors = pipedMirrors + invidiousMirrors
            val pings = allMirrors.map { baseUrl ->
                async {
                    val start = System.currentTimeMillis()
                    val ok = runCatching {
                        val probeUrl = if (baseUrl in pipedMirrors) {
                            "$baseUrl/trending?region=US"
                        } else {
                            "$baseUrl/api/v1/stats"
                        }
                        val req = Request.Builder().url(probeUrl).get().build()
                        HttpClientProvider.client.newCall(req).execute().use { it.isSuccessful }
                    }.getOrDefault(false)
                    val elapsed = System.currentTimeMillis() - start
                    Triple(baseUrl, ok, elapsed)
                }
            }.awaitAll()

            val fastest = pings.filter { it.second }.minByOrNull { it.third }
            if (fastest != null) {
                bestMirror = fastest.first
                fastest.first to fastest.third
            } else {
                bestMirror to 999L
            }
        }
    }

    override suspend fun search(query: String, limit: Int): SourceSearchResult =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val encoded = HttpClientProvider.encode(query)

            // Try Piped mirrors first
            for (mirror in pipedMirrors) {
                val result = runCatching {
                    val body = HttpClientProvider.getBodyString(
                        "$mirror/search?q=$encoded&filter=music_songs"
                    )
                    val root = HttpClientProvider.json.parseToJsonElement(body).jsonObject
                    val items = root["items"]?.jsonArray ?: JsonArray(emptyList())
                    val tracks = items.take(limit).mapNotNull { el ->
                        parsePipedItem(el.jsonObject, mirror)
                    }
                    if (tracks.isEmpty()) throw IllegalStateException("No items on $mirror")
                    bestMirror = mirror
                    tracks
                }.getOrNull()

                if (!result.isNullOrEmpty()) {
                    val latency = System.currentTimeMillis() - start
                    return@withContext SourceSearchResult(
                        provider = provider,
                        tracks = result,
                        latencyMs = latency,
                        activeMirror = activeMirrorHost,
                        isOnline = true
                    )
                }
            }

            // Failover to Invidious mirrors
            for (mirror in invidiousMirrors) {
                val result = runCatching {
                    val body = HttpClientProvider.getBodyString(
                        "$mirror/api/v1/search?q=$encoded&type=video"
                    )
                    val items = HttpClientProvider.json.parseToJsonElement(body).jsonArray
                    val tracks = items.take(limit).mapNotNull { el ->
                        parseInvidiousItem(el.jsonObject, mirror)
                    }
                    if (tracks.isEmpty()) throw IllegalStateException("No items on $mirror")
                    bestMirror = mirror
                    tracks
                }.getOrNull()

                if (!result.isNullOrEmpty()) {
                    val latency = System.currentTimeMillis() - start
                    return@withContext SourceSearchResult(
                        provider = provider,
                        tracks = result,
                        latencyMs = latency,
                        activeMirror = activeMirrorHost,
                        isOnline = true
                    )
                }
            }

            SourceSearchResult(
                provider = provider,
                tracks = emptyList(),
                latencyMs = System.currentTimeMillis() - start,
                activeMirror = activeMirrorHost,
                isOnline = false,
                error = "All Piped/Invidious mirrors busy"
            )
        }

    override suspend fun getTrending(limit: Int): SourceSearchResult =
        search("Synthwave Electronic Chill", limit)

    override suspend fun resolvePlayableStreamUrl(track: Track): String? =
        withContext(Dispatchers.IO) {
            if (track.streamUrl.startsWith("http://") || track.streamUrl.startsWith("https://")) {
                return@withContext track.streamUrl
            }
            val videoId = track.streamUrl.removePrefix("ytm://").trim()
            if (videoId.isNotBlank()) {
                for (mirror in pipedMirrors) {
                    val direct = runCatching {
                        val body = HttpClientProvider.getBodyString("$mirror/streams/$videoId")
                        val obj = HttpClientProvider.json.parseToJsonElement(body).jsonObject
                        val audioStreams = obj["audioStreams"]?.jsonArray ?: JsonArray(emptyList())
                        audioStreams
                            .map { it.jsonObject }
                            .filter {
                                val u = it["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
                                u.startsWith("https://") || u.startsWith("http://")
                            }
                            .maxByOrNull { it["bitrate"]?.jsonPrimitive?.intOrNull ?: 0 }
                            ?.get("url")?.jsonPrimitive?.contentOrNull
                    }.getOrNull()
                    if (!direct.isNullOrBlank()) return@withContext direct
                }
                for (mirror in invidiousMirrors) {
                    val direct = runCatching {
                        val body = HttpClientProvider.getBodyString("$mirror/api/v1/videos/$videoId")
                        val obj = HttpClientProvider.json.parseToJsonElement(body).jsonObject
                        val adaptive = obj["adaptiveFormats"]?.jsonArray ?: JsonArray(emptyList())
                        adaptive
                            .map { it.jsonObject }
                            .filter {
                                val mime = it["type"]?.jsonPrimitive?.contentOrNull.orEmpty()
                                val u = it["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
                                mime.startsWith("audio/") && (u.startsWith("https://") || u.startsWith("http://"))
                            }
                            .firstOrNull()
                            ?.get("url")?.jsonPrimitive?.contentOrNull
                    }.getOrNull()
                    if (!direct.isNullOrBlank()) return@withContext direct
                }
            }

            // Fallback: check if track already has a direct HTTP fallback candidate
            track.fallbackStreams.firstOrNull {
                it.streamUrl.startsWith("https://") || it.streamUrl.startsWith("http://")
            }?.streamUrl?.let { return@withContext it }

            null
        }

    private fun parsePipedItem(obj: JsonObject, mirror: String): Track? {
        val rawUrl = obj["url"]?.jsonPrimitive?.contentOrNull ?: return null
        val videoId = rawUrl.substringAfter("v=", "").takeIf { it.isNotBlank() } ?: return null
        val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: return null
        val artist = obj["uploaderName"]?.jsonPrimitive?.contentOrNull ?: "YouTube Artist"
        val duration = obj["duration"]?.jsonPrimitive?.intOrNull ?: 0
        val thumb = obj["thumbnail"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val pseudoStream = "ytm://$videoId"
        return Track(
            id = "ytm_$videoId",
            title = title,
            artist = artist.removeSuffix(" - Topic"),
            album = "YouTube Music",
            artworkUrl = thumb,
            durationSec = duration.coerceAtLeast(0),
            primaryProvider = SourceProvider.YOUTUBE_PIPED,
            streamUrl = pseudoStream,
            fallbackStreams = listOf(
                StreamCandidate(
                    provider = SourceProvider.YOUTUBE_PIPED,
                    streamUrl = pseudoStream,
                    bitrateKbps = 256,
                    isPreview = false,
                    instanceHost = mirror.removePrefix("https://")
                )
            ),
            genre = "YouTube Stream"
        )
    }

    private fun parseInvidiousItem(obj: JsonObject, mirror: String): Track? {
        val videoId = obj["videoId"]?.jsonPrimitive?.contentOrNull ?: return null
        val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: return null
        val artist = obj["author"]?.jsonPrimitive?.contentOrNull ?: "YouTube Artist"
        val duration = obj["lengthSeconds"]?.jsonPrimitive?.intOrNull ?: 0
        val thumb = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        val pseudoStream = "ytm://$videoId"
        return Track(
            id = "ytm_$videoId",
            title = title,
            artist = artist.removeSuffix(" - Topic"),
            album = "YouTube Music",
            artworkUrl = thumb,
            durationSec = duration.coerceAtLeast(0),
            primaryProvider = SourceProvider.YOUTUBE_PIPED,
            streamUrl = pseudoStream,
            fallbackStreams = listOf(
                StreamCandidate(
                    provider = SourceProvider.YOUTUBE_PIPED,
                    streamUrl = pseudoStream,
                    bitrateKbps = 256,
                    isPreview = false,
                    instanceHost = mirror.removePrefix("https://")
                )
            ),
            genre = "YouTube Stream"
        )
    }
}

/**
 * 2. Deezer Public API (https://api.deezer.com/search)
 * Provides 1000x1000 HD album covers and 30s studio MP3 streams without API keys.
 */
class DeezerMusicSource : MusicSource {
    override val provider: SourceProvider = SourceProvider.DEEZER
    override val activeMirrorHost: String = "api.deezer.com"

    override suspend fun search(query: String, limit: Int): SourceSearchResult =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            runCatching {
                val encoded = HttpClientProvider.encode(query)
                val body = HttpClientProvider.getBodyString(
                    "https://api.deezer.com/search?q=$encoded&limit=$limit"
                )
                val root = HttpClientProvider.json.parseToJsonElement(body).jsonObject
                val data = root["data"]?.jsonArray ?: JsonArray(emptyList())
                val tracks = data.mapNotNull { parseDeezerTrack(it.jsonObject) }
                SourceSearchResult(
                    provider = provider,
                    tracks = tracks,
                    latencyMs = System.currentTimeMillis() - start,
                    activeMirror = activeMirrorHost,
                    isOnline = true
                )
            }.getOrElse { e ->
                SourceSearchResult(
                    provider = provider,
                    tracks = emptyList(),
                    latencyMs = System.currentTimeMillis() - start,
                    activeMirror = activeMirrorHost,
                    isOnline = false,
                    error = e.message
                )
            }
        }

    override suspend fun getTrending(limit: Int): SourceSearchResult =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            runCatching {
                val body = HttpClientProvider.getBodyString(
                    "https://api.deezer.com/chart/0/tracks?limit=$limit"
                )
                val root = HttpClientProvider.json.parseToJsonElement(body).jsonObject
                val data = root["data"]?.jsonArray ?: JsonArray(emptyList())
                val tracks = data.mapNotNull { parseDeezerTrack(it.jsonObject) }
                SourceSearchResult(
                    provider = provider,
                    tracks = tracks,
                    latencyMs = System.currentTimeMillis() - start,
                    activeMirror = activeMirrorHost,
                    isOnline = true
                )
            }.getOrElse {
                search("Daft Punk Weeknd", limit)
            }
        }

    private fun parseDeezerTrack(obj: JsonObject): Track? {
        val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return null
        val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: return null
        val previewUrl = obj["preview"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: return null
        val duration = obj["duration"]?.jsonPrimitive?.intOrNull ?: 30
        val artistObj = obj["artist"]?.jsonObject
        val artistName = artistObj?.get("name")?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"
        val albumObj = obj["album"]?.jsonObject
        val albumTitle = albumObj?.get("title")?.jsonPrimitive?.contentOrNull.orEmpty()
        val coverXl = albumObj?.get("cover_xl")?.jsonPrimitive?.contentOrNull
            ?: albumObj?.get("cover_big")?.jsonPrimitive?.contentOrNull
            ?: artistObj?.get("picture_xl")?.jsonPrimitive?.contentOrNull.orEmpty()

        return Track(
            id = "dzr_$id",
            title = title,
            artist = artistName,
            album = albumTitle,
            artworkUrl = coverXl,
            durationSec = duration,
            primaryProvider = SourceProvider.DEEZER,
            streamUrl = previewUrl,
            fallbackStreams = listOf(
                StreamCandidate(
                    provider = SourceProvider.DEEZER,
                    streamUrl = previewUrl,
                    bitrateKbps = 320,
                    isPreview = true,
                    instanceHost = activeMirrorHost
                )
            ),
            isPreviewOnly = true,
            genre = "Deezer HD"
        )
    }
}

/**
 * 3. Apple iTunes Search API (https://itunes.apple.com/search?media=music)
 */
class ITunesMusicSource : MusicSource {
    override val provider: SourceProvider = SourceProvider.ITUNES
    override val activeMirrorHost: String = "itunes.apple.com"

    override suspend fun search(query: String, limit: Int): SourceSearchResult =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            runCatching {
                val encoded = HttpClientProvider.encode(query)
                val body = HttpClientProvider.getBodyString(
                    "https://itunes.apple.com/search?media=music&entity=song&limit=$limit&term=$encoded"
                )
                val root = HttpClientProvider.json.parseToJsonElement(body).jsonObject
                val results = root["results"]?.jsonArray ?: JsonArray(emptyList())
                val tracks = results.mapNotNull { el ->
                    val obj = el.jsonObject
                    val id = obj["trackId"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val title = obj["trackName"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val artist = obj["artistName"]?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"
                    val album = obj["collectionName"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val preview = obj["previewUrl"]?.jsonPrimitive?.contentOrNull
                        ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val art100 = obj["artworkUrl100"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val art600 = art100.replace("100x100bb", "600x600bb")
                    val durationMs = obj["trackTimeMillis"]?.jsonPrimitive?.intOrNull ?: 30_000
                    val genre = obj["primaryGenreName"]?.jsonPrimitive?.contentOrNull ?: "Pop"
                    val releaseDate = obj["releaseDate"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        .take(4)

                    Track(
                        id = "itn_$id",
                        title = title,
                        artist = artist,
                        album = album,
                        artworkUrl = art600,
                        durationSec = (durationMs / 1000).coerceAtLeast(1),
                        primaryProvider = SourceProvider.ITUNES,
                        streamUrl = preview,
                        fallbackStreams = listOf(
                            StreamCandidate(
                                provider = SourceProvider.ITUNES,
                                streamUrl = preview,
                                bitrateKbps = 256,
                                isPreview = true,
                                instanceHost = activeMirrorHost
                            )
                        ),
                        isPreviewOnly = true,
                        genre = genre,
                        releaseYear = releaseDate
                    )
                }
                SourceSearchResult(
                    provider = provider,
                    tracks = tracks,
                    latencyMs = System.currentTimeMillis() - start,
                    activeMirror = activeMirrorHost,
                    isOnline = true
                )
            }.getOrElse { e ->
                SourceSearchResult(
                    provider = provider,
                    tracks = emptyList(),
                    latencyMs = System.currentTimeMillis() - start,
                    activeMirror = activeMirrorHost,
                    isOnline = false,
                    error = e.message
                )
            }
        }

    override suspend fun getTrending(limit: Int): SourceSearchResult =
        search("Electronic Melodic", limit)
}
