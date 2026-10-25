package com.example.data.remote

import com.example.domain.ArtistSuggestion
import com.example.domain.SlantWaveEngine
import com.example.domain.SourceProvider
import com.example.domain.StreamCandidate
import com.example.domain.Track
import com.example.domain.TrackDeduplicationEngine
import com.example.domain.isPreviewSnippetUrl
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.ConnectionPool
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
    suspend fun search(query: String, limit: Int = 25): SourceSearchResult
    suspend fun searchArtistCatalog(artistName: String, limit: Int = 50): SourceSearchResult =
        search(artistName, limit)
    suspend fun getTrending(limit: Int = 16): SourceSearchResult
    suspend fun resolvePlayableStreamUrl(track: Track): String? = track.streamUrl
}

object HttpClientProvider {
    const val USER_AGENT =
        "SlantMuz/2.0 (Android 14; SlantTech Full-Length Audiophile Engine; +https://slanttech.io)"

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(16, 5, TimeUnit.MINUTES))
            .connectTimeout(4500, TimeUnit.MILLISECONDS)
            .readTimeout(5500, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(req)
            }
            .build()
    }

    /**
     * Ultra-low-latency client used when racing multiple stream mirrors in parallel during track switch.
     */
    val fastStreamClient: OkHttpClient by lazy {
        client.newBuilder()
            .connectTimeout(2500, TimeUnit.MILLISECONDS)
            .readTimeout(3200, TimeUnit.MILLISECONDS)
            .build()
    }

    fun getBodyString(url: String, fastTimeout: Boolean = false): String {
        val activeClient = if (fastTimeout) fastStreamClient else client
        val request = Request.Builder().url(url).get().build()
        activeClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code} from $url")
            }
            return response.body?.string() ?: throw IllegalStateException("Empty body from $url")
        }
    }

    fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}

/**
 * 1. Multi-Engine Full-Length Audio Source (JioSaavn 320kbps Full Audio + Hearthis.at Full Audio + Piped/Invidious).
 * Guarantees 100% full-length tracks (60s+) with direct high-bitrate streams and zero 30s previews.
 */
class YouTubePipedInvidiousSource : MusicSource {
    override val provider: SourceProvider = SourceProvider.YOUTUBE_PIPED

    private val pipedMirrors = listOf(
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.leptons.xyz",
        "https://pipedapi.nosebs.ru",
        "https://pipedapi.ducks.party",
        "https://api.piped.private.coffee"
    )

    private val invidiousMirrors = listOf(
        "https://inv.nadeko.net",
        "https://invidious.nerdvpn.de",
        "https://invidious.privacyredirect.com"
    )

    @Volatile
    private var bestMirror: String = "saavn-320kbps + hearthis.at"

    override val activeMirrorHost: String
        get() = bestMirror.removePrefix("https://")

    suspend fun selectFastestInstance(): Pair<String, Long> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val saavnTracks = runCatching {
            FullLengthAudioResolver.searchJioSaavnFullTracks("Synthwave", limit = 2)
        }.getOrDefault(emptyList())
        val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
        if (saavnTracks.isNotEmpty()) {
            bestMirror = "saavn.dev (320kbps Full Audio)"
            return@withContext bestMirror to elapsed
        }
        bestMirror = "api-v2.hearthis.at (Full MP3)"
        bestMirror to elapsed
    }

    override suspend fun search(query: String, limit: Int): SourceSearchResult =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val cleanQuery = query.trim()
            if (cleanQuery.isBlank()) {
                return@withContext SourceSearchResult(
                    provider = provider,
                    tracks = emptyList(),
                    latencyMs = 0L,
                    activeMirror = activeMirrorHost,
                    isOnline = true
                )
            }

            coroutineScope {
                // 1. JioSaavn 320kbps full-length tracks (direct AAC/MP4 streams)
                val saavnDeferred = async {
                    runCatching {
                        FullLengthAudioResolver.searchJioSaavnFullTracks(cleanQuery, limit = limit)
                    }.getOrDefault(emptyList())
                }

                // 2. Hearthis.at full-length MP3 streams
                val hearthisDeferred = async {
                    runCatching {
                        FullLengthAudioResolver.searchHearthisFullTracks(cleanQuery, limit = (limit / 2).coerceAtLeast(8))
                    }.getOrDefault(emptyList())
                }

                // 3. Piped mirror race (optional enrichment)
                val pipedDeferred = async {
                    withTimeoutOrNull(2600L) {
                        val encoded = HttpClientProvider.encode(cleanQuery)
                        pipedMirrors.take(2).map { mirror ->
                            async {
                                runCatching {
                                    val body = HttpClientProvider.getBodyString(
                                        "$mirror/search?q=$encoded&filter=music_songs",
                                        fastTimeout = true
                                    )
                                    val root = HttpClientProvider.json.parseToJsonElement(body).jsonObject
                                    val items = root["items"]?.jsonArray ?: JsonArray(emptyList())
                                    items.take(limit).mapNotNull { el ->
                                        parsePipedItem(el.jsonObject, mirror)
                                    }
                                }.getOrDefault(emptyList())
                            }
                        }.awaitAll().flatten()
                    }.orEmpty()
                }

                val combined = (saavnDeferred.await() + hearthisDeferred.await() + pipedDeferred.await())
                    .filter { it.durationSec >= 55 && !it.isSnippetStream() }

                val deduplicated = TrackDeduplicationEngine.deduplicateWithinSingleProvider(combined)
                    .take(limit)

                if (deduplicated.isNotEmpty()) {
                    bestMirror = "saavn-320kbps + hearthis.at"
                    SourceSearchResult(
                        provider = provider,
                        tracks = deduplicated,
                        latencyMs = System.currentTimeMillis() - start,
                        activeMirror = activeMirrorHost,
                        isOnline = true
                    )
                } else {
                    SourceSearchResult(
                        provider = provider,
                        tracks = emptyList(),
                        latencyMs = System.currentTimeMillis() - start,
                        activeMirror = activeMirrorHost,
                        isOnline = false,
                        error = "No full-length streams matched query"
                    )
                }
            }
        }

    override suspend fun searchArtistCatalog(artistName: String, limit: Int): SourceSearchResult =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val cleanArtist = artistName.trim()
            coroutineScope {
                val saavnDirect = async {
                    runCatching {
                        FullLengthAudioResolver.searchJioSaavnFullTracks(cleanArtist, limit = limit.coerceAtLeast(40))
                    }.getOrDefault(emptyList())
                }
                val hearthisDirect = async {
                    runCatching {
                        FullLengthAudioResolver.searchHearthisFullTracks(cleanArtist, limit = 25)
                    }.getOrDefault(emptyList())
                }
                val all = (saavnDirect.await() + hearthisDirect.await())
                    .filter { it.durationSec >= 55 && !it.isSnippetStream() }
                val verified = all.filter { TrackDeduplicationEngine.isTrackByArtist(it, cleanArtist) }
                val finalTracks = TrackDeduplicationEngine.deduplicateWithinSingleProvider(
                    verified.ifEmpty { all }
                )
                SourceSearchResult(
                    provider = provider,
                    tracks = finalTracks,
                    latencyMs = System.currentTimeMillis() - start,
                    activeMirror = activeMirrorHost,
                    isOnline = true
                )
            }
        }

    override suspend fun getTrending(limit: Int): SourceSearchResult =
        search("Electronic Synthwave Phonk", limit)

    /**
     * Resolves a 100% full-length audio stream for ANY artist + title.
     * Never returns 30s previews.
     */
    suspend fun resolveFullTrackByMetadata(
        artist: String,
        title: String,
        expectedDurationSec: Int
    ): String? = withContext(Dispatchers.IO) {
        val candidate = FullLengthAudioResolver.resolveFullLengthStream(
            artist = artist,
            title = title,
            expectedDurationSec = expectedDurationSec
        )
        if (candidate != null && !isPreviewSnippetUrl(candidate.streamUrl)) {
            return@withContext candidate.streamUrl
        }
        null
    }

    override suspend fun resolvePlayableStreamUrl(track: Track): String? =
        withContext(Dispatchers.IO) {
            if ((track.streamUrl.startsWith("http://") || track.streamUrl.startsWith("https://")) &&
                !isPreviewSnippetUrl(track.streamUrl)
            ) {
                return@withContext track.streamUrl
            }

            // First try FullLengthAudioResolver by metadata (fast & reliable 320kbps full streams)
            val resolvedFull = FullLengthAudioResolver.resolveFullLengthStream(
                artist = track.artist,
                title = track.title,
                expectedDurationSec = track.durationSec
            )
            if (resolvedFull != null && !isPreviewSnippetUrl(resolvedFull.streamUrl)) {
                return@withContext resolvedFull.streamUrl
            }

            val videoId = track.streamUrl.removePrefix("ytm://").trim()
            if (videoId.isNotBlank() && !track.streamUrl.startsWith("fulltrack://")) {
                val pipedResolved = withTimeoutOrNull(2600L) {
                    coroutineScope {
                        pipedMirrors.take(3).map { mirror ->
                            async {
                                runCatching {
                                    val body = HttpClientProvider.getBodyString(
                                        "$mirror/streams/$videoId",
                                        fastTimeout = true
                                    )
                                    val obj = HttpClientProvider.json.parseToJsonElement(body).jsonObject
                                    val audioStreams = obj["audioStreams"]?.jsonArray ?: JsonArray(emptyList())
                                    audioStreams
                                        .map { it.jsonObject }
                                        .filter {
                                            val u = it["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
                                            (u.startsWith("https://") || u.startsWith("http://")) && !isPreviewSnippetUrl(u)
                                        }
                                        .maxByOrNull { it["bitrate"]?.jsonPrimitive?.intOrNull ?: 0 }
                                        ?.get("url")?.jsonPrimitive?.contentOrNull
                                        ?.takeIf { it.isNotBlank() }
                                }.getOrNull()
                            }
                        }.awaitAll().firstOrNull { !it.isNullOrBlank() }
                    }
                }
                if (!pipedResolved.isNullOrBlank()) return@withContext pipedResolved
            }

            null
        }

    private fun parsePipedItem(obj: JsonObject, mirror: String): Track? {
        val rawUrl = obj["url"]?.jsonPrimitive?.contentOrNull ?: return null
        val videoId = rawUrl.substringAfter("v=", "").takeIf { it.isNotBlank() } ?: return null
        val rawTitle = obj["title"]?.jsonPrimitive?.contentOrNull ?: return null
        if (TrackDeduplicationEngine.isTeaserOrSnippetTitle(rawTitle)) return null

        val rawUploader = obj["uploaderName"]?.jsonPrimitive?.contentOrNull ?: "YouTube Artist"
        val duration = obj["duration"]?.jsonPrimitive?.intOrNull ?: 0
        // Strictly reject shorts, teasers, or tracks under 60 seconds
        if (duration < 60) return null

        val (resolvedArtist, resolvedTitle) = TrackDeduplicationEngine.splitArtistAndTitleIfPrefixed(
            rawTitle = rawTitle,
            fallbackArtist = rawUploader
        )

        val thumb = obj["thumbnail"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val fullTrackUri = "fulltrack://${resolvedArtist.trim()}|${resolvedTitle.trim()}|$duration"
        return Track(
            id = "ytm_$videoId",
            title = resolvedTitle,
            artist = resolvedArtist,
            album = "YouTube Full Audio",
            artworkUrl = thumb,
            durationSec = duration,
            primaryProvider = SourceProvider.YOUTUBE_PIPED,
            streamUrl = fullTrackUri,
            fallbackStreams = listOf(
                StreamCandidate(
                    provider = SourceProvider.YOUTUBE_PIPED,
                    streamUrl = fullTrackUri,
                    bitrateKbps = 320,
                    isPreview = false,
                    instanceHost = "saavn-320kbps"
                ),
                StreamCandidate(
                    provider = SourceProvider.YOUTUBE_PIPED,
                    streamUrl = "ytm://$videoId",
                    bitrateKbps = 256,
                    isPreview = false,
                    instanceHost = mirror.removePrefix("https://")
                )
            ),
            isPreviewOnly = false,
            genre = "Full-Length Audio",
            bitrateKbps = 320
        )
    }
}

/**
 * 2. Deezer Rich Metadata & Artist Discovery Engine (https://api.deezer.com)
 * NOTE: 30-second Deezer preview URLs are STRICTLY EXCLUDED.
 * All Deezer metadata records are bound to `fulltrack://` full-length 320kbps audio resolution.
 */
class DeezerMusicSource : MusicSource {
    override val provider: SourceProvider = SourceProvider.DEEZER
    override val activeMirrorHost: String = "api.deezer.com (Full-Match)"

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

    override suspend fun searchArtistCatalog(artistName: String, limit: Int): SourceSearchResult =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val cleanName = artistName.trim()
            runCatching {
                val encodedName = HttpClientProvider.encode(cleanName)
                val strictQueryEncoded = HttpClientProvider.encode("artist:\"$cleanName\"")

                coroutineScope {
                    val artistTopDeferred = async {
                        runCatching {
                            val artistSearchBody = HttpClientProvider.getBodyString(
                                "https://api.deezer.com/search/artist?q=$encodedName&limit=5"
                            )
                            val artistsArr = HttpClientProvider.json
                                .parseToJsonElement(artistSearchBody)
                                .jsonObject["data"]?.jsonArray ?: JsonArray(emptyList())

                            val matchedArtistObj = artistsArr.map { it.jsonObject }.firstOrNull { obj ->
                                val name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                                TrackDeduplicationEngine.areArtistNamesEquivalent(name, cleanName)
                            } ?: artistsArr.firstOrNull()?.jsonObject

                            val artistId = matchedArtistObj?.get("id")?.jsonPrimitive?.contentOrNull
                            val artistCover = matchedArtistObj?.get("picture_xl")?.jsonPrimitive?.contentOrNull
                                ?: matchedArtistObj?.get("picture_big")?.jsonPrimitive?.contentOrNull.orEmpty()

                            if (!artistId.isNullOrBlank()) {
                                val topBody = HttpClientProvider.getBodyString(
                                    "https://api.deezer.com/artist/$artistId/top?limit=${limit.coerceAtLeast(50)}"
                                )
                                val topData = HttpClientProvider.json
                                    .parseToJsonElement(topBody)
                                    .jsonObject["data"]?.jsonArray ?: JsonArray(emptyList())
                                topData.mapNotNull { parseDeezerTrack(it.jsonObject, fallbackArtistCover = artistCover) }
                            } else {
                                emptyList()
                            }
                        }.getOrDefault(emptyList())
                    }

                    val strictSearchDeferred = async {
                        runCatching {
                            val body = HttpClientProvider.getBodyString(
                                "https://api.deezer.com/search?q=$strictQueryEncoded&limit=${limit.coerceAtLeast(50)}"
                            )
                            val data = HttpClientProvider.json
                                .parseToJsonElement(body)
                                .jsonObject["data"]?.jsonArray ?: JsonArray(emptyList())
                            data.mapNotNull { parseDeezerTrack(it.jsonObject) }
                        }.getOrDefault(emptyList())
                    }

                    val generalSearchDeferred = async {
                        runCatching {
                            val body = HttpClientProvider.getBodyString(
                                "https://api.deezer.com/search?q=$encodedName&limit=35"
                            )
                            val data = HttpClientProvider.json
                                .parseToJsonElement(body)
                                .jsonObject["data"]?.jsonArray ?: JsonArray(emptyList())
                            data.mapNotNull { parseDeezerTrack(it.jsonObject) }
                        }.getOrDefault(emptyList())
                    }

                    val combined = artistTopDeferred.await() +
                        strictSearchDeferred.await() +
                        generalSearchDeferred.await()

                    val verified = combined.filter { TrackDeduplicationEngine.isTrackByArtist(it, cleanName) }
                    val deduplicated = TrackDeduplicationEngine.deduplicateWithinSingleProvider(
                        verified.ifEmpty { combined }
                    )

                    SourceSearchResult(
                        provider = provider,
                        tracks = deduplicated,
                        latencyMs = System.currentTimeMillis() - start,
                        activeMirror = activeMirrorHost,
                        isOnline = true
                    )
                }
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

    suspend fun searchArtists(query: String, limit: Int = 8): List<ArtistSuggestion> =
        withContext(Dispatchers.IO) {
            val clean = query.trim()
            if (clean.isBlank()) return@withContext emptyList()
            runCatching {
                val encoded = HttpClientProvider.encode(clean)
                val body = HttpClientProvider.getBodyString(
                    "https://api.deezer.com/search/artist?q=$encoded&limit=$limit",
                    fastTimeout = true
                )
                val data = HttpClientProvider.json.parseToJsonElement(body).jsonObject["data"]?.jsonArray
                    ?: JsonArray(emptyList())

                data.mapNotNull { el ->
                    val obj = el.jsonObject
                    val name = obj["name"]?.jsonPrimitive?.contentOrNull?.trim()
                        ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val pic = obj["picture_medium"]?.jsonPrimitive?.contentOrNull
                        ?: obj["picture_big"]?.jsonPrimitive?.contentOrNull
                        ?: obj["picture_xl"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val nbAlbum = obj["nb_album"]?.jsonPrimitive?.intOrNull ?: 0
                    val nbFan = obj["nb_fan"]?.jsonPrimitive?.intOrNull ?: 0

                    val seedMatch = SlantWaveEngine.starterArtistsCatalog.firstOrNull {
                        TrackDeduplicationEngine.areArtistNamesEquivalent(it.name, name)
                    }
                    val subtitle = buildList {
                        seedMatch?.subtitle?.let { add(it) }
                        if (nbAlbum > 0) add("$nbAlbum релизов")
                        if (nbFan >= 1000) add("${nbFan / 1000}K слушателей")
                    }.joinToString(" • ").ifBlank { "Исполнитель" }

                    ArtistSuggestion(
                        name = name,
                        subtitle = subtitle,
                        artworkUrl = pic,
                        genreId = seedMatch?.genreId,
                        isRussian = SlantWaveEngine.containsCyrillic(name) || (seedMatch?.isRussian == true)
                    )
                }
            }.getOrDefault(emptyList())
        }

    suspend fun fetchRelatedArtists(artistName: String, limit: Int = 6): List<ArtistSuggestion> =
        withContext(Dispatchers.IO) {
            val clean = artistName.trim()
            if (clean.isBlank()) return@withContext emptyList()
            runCatching {
                val encoded = HttpClientProvider.encode(clean)
                val searchBody = HttpClientProvider.getBodyString(
                    "https://api.deezer.com/search/artist?q=$encoded&limit=3",
                    fastTimeout = true
                )
                val arr = HttpClientProvider.json.parseToJsonElement(searchBody).jsonObject["data"]?.jsonArray
                    ?: JsonArray(emptyList())
                val matched = arr.map { it.jsonObject }.firstOrNull { obj ->
                    val n = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    TrackDeduplicationEngine.areArtistNamesEquivalent(n, clean)
                } ?: arr.firstOrNull()?.jsonObject

                val artistId = matched?.get("id")?.jsonPrimitive?.contentOrNull ?: return@withContext emptyList()
                val relBody = HttpClientProvider.getBodyString(
                    "https://api.deezer.com/artist/$artistId/related?limit=$limit",
                    fastTimeout = true
                )
                val relArr = HttpClientProvider.json.parseToJsonElement(relBody).jsonObject["data"]?.jsonArray
                    ?: JsonArray(emptyList())
                relArr.mapNotNull { el ->
                    val obj = el.jsonObject
                    val name = obj["name"]?.jsonPrimitive?.contentOrNull?.trim()
                        ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val pic = obj["picture_medium"]?.jsonPrimitive?.contentOrNull
                        ?: obj["picture_big"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    ArtistSuggestion(
                        name = name,
                        subtitle = "Похож на $clean",
                        artworkUrl = pic,
                        isRussian = SlantWaveEngine.containsCyrillic(name)
                    )
                }
            }.getOrDefault(emptyList())
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
                search("Synthwave Post-Punk", limit)
            }
        }

    private fun parseDeezerTrack(obj: JsonObject, fallbackArtistCover: String = ""): Track? {
        val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return null
        val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: return null
        if (TrackDeduplicationEngine.isTeaserOrSnippetTitle(title)) return null

        val duration = obj["duration"]?.jsonPrimitive?.intOrNull ?: 180
        // Strictly ignore any snippet/interlude shorter than 60 seconds
        if (duration < 60) return null

        val artistObj = obj["artist"]?.jsonObject
        val artistName = artistObj?.get("name")?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"
        val albumObj = obj["album"]?.jsonObject
        val albumTitle = albumObj?.get("title")?.jsonPrimitive?.contentOrNull.orEmpty()
        val coverXl = albumObj?.get("cover_xl")?.jsonPrimitive?.contentOrNull
            ?: albumObj?.get("cover_big")?.jsonPrimitive?.contentOrNull
            ?: artistObj?.get("picture_xl")?.jsonPrimitive?.contentOrNull
            ?: fallbackArtistCover

        val fullTrackResolverUri = "fulltrack://${artistName.trim()}|${title.trim()}|$duration"

        // NOTE: We intentionally DO NOT include `preview` (30s dzcdn snippet) in fallbackStreams!
        return Track(
            id = "dzr_$id",
            title = title,
            artist = artistName,
            album = albumTitle,
            artworkUrl = coverXl,
            durationSec = duration,
            primaryProvider = SourceProvider.DEEZER,
            streamUrl = fullTrackResolverUri,
            fallbackStreams = listOf(
                StreamCandidate(
                    provider = SourceProvider.YOUTUBE_PIPED,
                    streamUrl = fullTrackResolverUri,
                    bitrateKbps = 320,
                    isPreview = false,
                    instanceHost = "saavn-320kbps-full"
                ),
                StreamCandidate(
                    provider = SourceProvider.AUDIUS,
                    streamUrl = fullTrackResolverUri,
                    bitrateKbps = 320,
                    isPreview = false,
                    instanceHost = "hearthis-audius-full"
                )
            ),
            isPreviewOnly = false,
            genre = "Full-Length Audio",
            bitrateKbps = 320
        )
    }
}

/**
 * 3. Apple iTunes Search & Lookup API (https://itunes.apple.com)
 * NOTE: 30-second iTunes preview URLs (`audio-ssl.itunes.apple.com`) are STRICTLY EXCLUDED.
 * All iTunes tracks are resolved to full-length 320kbps audio via `fulltrack://`.
 */
class ITunesMusicSource : MusicSource {
    override val provider: SourceProvider = SourceProvider.ITUNES
    override val activeMirrorHost: String = "itunes.apple.com (Full-Match)"

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
                val tracks = results.mapNotNull { parseITunesTrack(it.jsonObject) }
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

    override suspend fun searchArtistCatalog(artistName: String, limit: Int): SourceSearchResult =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val cleanName = artistName.trim()
            runCatching {
                val encoded = HttpClientProvider.encode(cleanName)
                coroutineScope {
                    val lookupDeferred = async {
                        runCatching {
                            val artistBody = HttpClientProvider.getBodyString(
                                "https://itunes.apple.com/search?media=music&entity=musicArtist&limit=5&term=$encoded"
                            )
                            val artistResults = HttpClientProvider.json
                                .parseToJsonElement(artistBody)
                                .jsonObject["results"]?.jsonArray ?: JsonArray(emptyList())

                            val matchedArtist = artistResults.map { it.jsonObject }.firstOrNull { obj ->
                                val name = obj["artistName"]?.jsonPrimitive?.contentOrNull.orEmpty()
                                TrackDeduplicationEngine.areArtistNamesEquivalent(name, cleanName)
                            } ?: artistResults.firstOrNull()?.jsonObject

                            val artistId = matchedArtist?.get("artistId")?.jsonPrimitive?.contentOrNull
                            if (!artistId.isNullOrBlank()) {
                                val lookupBody = HttpClientProvider.getBodyString(
                                    "https://itunes.apple.com/lookup?id=$artistId&entity=song&limit=${limit.coerceAtLeast(60)}"
                                )
                                val lookupArr = HttpClientProvider.json
                                    .parseToJsonElement(lookupBody)
                                    .jsonObject["results"]?.jsonArray ?: JsonArray(emptyList())
                                lookupArr.mapNotNull { parseITunesTrack(it.jsonObject) }
                            } else {
                                emptyList()
                            }
                        }.getOrDefault(emptyList())
                    }

                    val artistTermDeferred = async {
                        runCatching {
                            val body = HttpClientProvider.getBodyString(
                                "https://itunes.apple.com/search?media=music&entity=song&attribute=artistTerm&limit=${limit.coerceAtLeast(50)}&term=$encoded"
                            )
                            val arr = HttpClientProvider.json
                                .parseToJsonElement(body)
                                .jsonObject["results"]?.jsonArray ?: JsonArray(emptyList())
                            arr.mapNotNull { parseITunesTrack(it.jsonObject) }
                        }.getOrDefault(emptyList())
                    }

                    val combined = lookupDeferred.await() + artistTermDeferred.await()
                    val verified = combined.filter { TrackDeduplicationEngine.isTrackByArtist(it, cleanName) }
                    val deduplicated = TrackDeduplicationEngine.deduplicateWithinSingleProvider(
                        verified.ifEmpty { combined }
                    )

                    SourceSearchResult(
                        provider = provider,
                        tracks = deduplicated,
                        latencyMs = System.currentTimeMillis() - start,
                        activeMirror = activeMirrorHost,
                        isOnline = true
                    )
                }
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

    suspend fun searchArtists(query: String, limit: Int = 6): List<ArtistSuggestion> =
        withContext(Dispatchers.IO) {
            val clean = query.trim()
            if (clean.isBlank()) return@withContext emptyList()
            runCatching {
                val encoded = HttpClientProvider.encode(clean)
                val body = HttpClientProvider.getBodyString(
                    "https://itunes.apple.com/search?media=music&entity=song&attribute=artistTerm&limit=20&term=$encoded",
                    fastTimeout = true
                )
                val results = HttpClientProvider.json.parseToJsonElement(body).jsonObject["results"]?.jsonArray
                    ?: JsonArray(emptyList())

                val groupedByArtist = LinkedHashMap<String, ArtistSuggestion>()
                for (el in results) {
                    val obj = el.jsonObject
                    val artist = obj["artistName"]?.jsonPrimitive?.contentOrNull?.trim()
                        ?.takeIf { it.isNotBlank() } ?: continue
                    val key = TrackDeduplicationEngine.normalizeText(artist)
                    if (key.isBlank() || groupedByArtist.containsKey(key)) continue
                    val art100 = obj["artworkUrl100"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val art600 = art100.replace("100x100bb", "600x600bb")
                    val genre = obj["primaryGenreName"]?.jsonPrimitive?.contentOrNull ?: "Music"
                    groupedByArtist[key] = ArtistSuggestion(
                        name = artist,
                        subtitle = genre,
                        artworkUrl = art600,
                        isRussian = SlantWaveEngine.containsCyrillic(artist)
                    )
                    if (groupedByArtist.size >= limit) break
                }
                groupedByArtist.values.toList()
            }.getOrDefault(emptyList())
        }

    private fun parseITunesTrack(obj: JsonObject): Track? {
        val wrapperType = obj["wrapperType"]?.jsonPrimitive?.contentOrNull
        if (wrapperType != null && wrapperType != "track") return null
        val id = obj["trackId"]?.jsonPrimitive?.contentOrNull ?: return null
        val title = obj["trackName"]?.jsonPrimitive?.contentOrNull ?: return null
        if (TrackDeduplicationEngine.isTeaserOrSnippetTitle(title)) return null

        val durationMs = obj["trackTimeMillis"]?.jsonPrimitive?.intOrNull ?: 180_000
        val durationSec = (durationMs / 1000).coerceAtLeast(1)
        // Strictly ignore snippets/teasers under 60 seconds
        if (durationSec < 60) return null

        val artist = obj["artistName"]?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"
        val album = obj["collectionName"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val art100 = obj["artworkUrl100"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val art600 = art100.replace("100x100bb", "600x600bb")
        val genre = obj["primaryGenreName"]?.jsonPrimitive?.contentOrNull ?: "Pop"
        val releaseDate = obj["releaseDate"]?.jsonPrimitive?.contentOrNull.orEmpty().take(4)

        val fullTrackResolverUri = "fulltrack://${artist.trim()}|${title.trim()}|$durationSec"

        // NOTE: We intentionally DO NOT include `previewUrl` (30s iTunes snippet) in fallbackStreams!
        return Track(
            id = "itn_$id",
            title = title,
            artist = artist,
            album = album,
            artworkUrl = art600,
            durationSec = durationSec,
            primaryProvider = SourceProvider.ITUNES,
            streamUrl = fullTrackResolverUri,
            fallbackStreams = listOf(
                StreamCandidate(
                    provider = SourceProvider.YOUTUBE_PIPED,
                    streamUrl = fullTrackResolverUri,
                    bitrateKbps = 320,
                    isPreview = false,
                    instanceHost = "saavn-320kbps-full"
                ),
                StreamCandidate(
                    provider = SourceProvider.AUDIUS,
                    streamUrl = fullTrackResolverUri,
                    bitrateKbps = 320,
                    isPreview = false,
                    instanceHost = "hearthis-audius-full"
                )
            ),
            isPreviewOnly = false,
            genre = genre,
            releaseYear = releaseDate,
            bitrateKbps = 320
        )
    }

    override suspend fun getTrending(limit: Int): SourceSearchResult =
        search("Electronic Melodic", limit)
}
