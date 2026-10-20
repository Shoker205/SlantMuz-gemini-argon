package com.example.data.remote

import com.example.domain.ArtistSuggestion
import com.example.domain.SlantWaveEngine
import com.example.domain.SourceProvider
import com.example.domain.StreamCandidate
import com.example.domain.Track
import com.example.domain.TrackDeduplicationEngine
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
        "SlantMuz/1.0 (Android 14; SlantTech Audiophile Multi-Stream Engine; +https://slanttech.io)"

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
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

    /**
     * Ultra-low-latency client used when racing multiple stream mirrors in parallel during track switch.
     */
    val fastStreamClient: OkHttpClient by lazy {
        client.newBuilder()
            .connectTimeout(2800, TimeUnit.MILLISECONDS)
            .readTimeout(3400, TimeUnit.MILLISECONDS)
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
 * 1. YouTube Music via Piped / Invidious mirror pool with parallel low-latency failover
 * and full-track cross-provider stream resolution.
 */
class YouTubePipedInvidiousSource : MusicSource {
    override val provider: SourceProvider = SourceProvider.YOUTUBE_PIPED

    private val pipedMirrors = listOf(
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.leptons.xyz",
        "https://pipedapi.nosebs.ru",
        "https://pipedapi.tokhmi.xyz",
        "https://pipedapi.ducks.party",
        "https://api.piped.private.coffee"
    )

    private val invidiousMirrors = listOf(
        "https://inv.nadeko.net",
        "https://invidious.nerdvpn.de",
        "https://invidious.privacyredirect.com",
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
                        HttpClientProvider.fastStreamClient.newCall(req).execute().use { it.isSuccessful }
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

            // Order mirrors so the last known fastest mirror is queried first, and race top mirrors in parallel
            val orderedPiped = (listOf(bestMirror).filter { it in pipedMirrors } + pipedMirrors).distinct()

            val pipedBatch = withTimeoutOrNull(4200L) {
                coroutineScope {
                    orderedPiped.take(3).map { mirror ->
                        async {
                            runCatching {
                                val body = HttpClientProvider.getBodyString(
                                    "$mirror/search?q=$encoded&filter=music_songs",
                                    fastTimeout = true
                                )
                                val root = HttpClientProvider.json.parseToJsonElement(body).jsonObject
                                val items = root["items"]?.jsonArray ?: JsonArray(emptyList())
                                val tracks = items.take(limit).mapNotNull { el ->
                                    parsePipedItem(el.jsonObject, mirror)
                                }
                                if (tracks.isNotEmpty()) mirror to tracks else null
                            }.getOrNull()
                        }
                    }.awaitAll().filterNotNull().maxByOrNull { it.second.size }
                }
            }

            if (pipedBatch != null && pipedBatch.second.isNotEmpty()) {
                bestMirror = pipedBatch.first
                return@withContext SourceSearchResult(
                    provider = provider,
                    tracks = pipedBatch.second,
                    latencyMs = System.currentTimeMillis() - start,
                    activeMirror = activeMirrorHost,
                    isOnline = true
                )
            }

            // Race Invidious mirrors in parallel as failover
            val invidiousBatch = withTimeoutOrNull(4200L) {
                coroutineScope {
                    invidiousMirrors.take(3).map { mirror ->
                        async {
                            runCatching {
                                val body = HttpClientProvider.getBodyString(
                                    "$mirror/api/v1/search?q=$encoded&type=video",
                                    fastTimeout = true
                                )
                                val items = HttpClientProvider.json.parseToJsonElement(body).jsonArray
                                val tracks = items.take(limit).mapNotNull { el ->
                                    parseInvidiousItem(el.jsonObject, mirror)
                                }
                                if (tracks.isNotEmpty()) mirror to tracks else null
                            }.getOrNull()
                        }
                    }.awaitAll().filterNotNull().maxByOrNull { it.second.size }
                }
            }

            if (invidiousBatch != null && invidiousBatch.second.isNotEmpty()) {
                bestMirror = invidiousBatch.first
                return@withContext SourceSearchResult(
                    provider = provider,
                    tracks = invidiousBatch.second,
                    latencyMs = System.currentTimeMillis() - start,
                    activeMirror = activeMirrorHost,
                    isOnline = true
                )
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

    override suspend fun searchArtistCatalog(artistName: String, limit: Int): SourceSearchResult =
        search(artistName, limit = limit.coerceAtLeast(35))

    override suspend fun getTrending(limit: Int): SourceSearchResult =
        search("Synthwave Electronic Chill", limit)

    /**
     * Resolves a full-length audio stream for ANY artist + title (used to upgrade 30-second previews
     * from Deezer/iTunes into full songs).
     */
    suspend fun resolveFullTrackByMetadata(
        artist: String,
        title: String,
        expectedDurationSec: Int
    ): String? = withContext(Dispatchers.IO) {
        val searchQuery = "${artist.trim()} ${title.trim()}".trim()
        if (searchQuery.isBlank()) return@withContext null

        val searchResult = search(searchQuery, limit = 8)
        if (searchResult.tracks.isEmpty()) return@withContext null

        val bestMatch = searchResult.tracks
            .filter { candidate ->
                candidate.durationSec >= 48 &&
                    !TrackDeduplicationEngine.isTeaserOrSnippetTitle(candidate.title)
            }
            .maxByOrNull { candidate ->
                val titleSim = TrackDeduplicationEngine.combinedSimilarity(candidate.title, title)
                val artistSim = if (TrackDeduplicationEngine.areArtistNamesEquivalent(candidate.artist, artist)) {
                    1.0
                } else {
                    TrackDeduplicationEngine.combinedSimilarity(candidate.artist, artist)
                }
                val durDelta = if (expectedDurationSec > 45 && candidate.durationSec > 45) {
                    abs(expectedDurationSec - candidate.durationSec)
                } else 0
                val durBonus = when {
                    durDelta <= 5 -> 0.45
                    durDelta <= 15 -> 0.25
                    durDelta <= 45 -> 0.10
                    else -> -0.20
                }
                (titleSim * 1.6) + (artistSim * 1.2) + durBonus
            } ?: return@withContext null

        resolvePlayableStreamUrl(bestMatch)
    }

    override suspend fun resolvePlayableStreamUrl(track: Track): String? =
        withContext(Dispatchers.IO) {
            if (track.streamUrl.startsWith("http://") || track.streamUrl.startsWith("https://")) {
                return@withContext track.streamUrl
            }
            val videoId = track.streamUrl.removePrefix("ytm://").trim()
            if (videoId.isNotBlank() && !track.streamUrl.startsWith("fulltrack://")) {
                val orderedPiped = (listOf(bestMirror).filter { it in pipedMirrors } + pipedMirrors).distinct()

                // Race Piped mirrors in parallel for ultra-fast stream resolution
                val pipedResolved = withTimeoutOrNull(3400L) {
                    coroutineScope {
                        orderedPiped.take(4).map { mirror ->
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
                                            u.startsWith("https://") || u.startsWith("http://")
                                        }
                                        .maxByOrNull { it["bitrate"]?.jsonPrimitive?.intOrNull ?: 0 }
                                        ?.get("url")?.jsonPrimitive?.contentOrNull
                                        ?.takeIf { it.isNotBlank() }
                                        ?.also { bestMirror = mirror }
                                }.getOrNull()
                            }
                        }.awaitAll().firstOrNull { !it.isNullOrBlank() }
                    }
                }
                if (!pipedResolved.isNullOrBlank()) return@withContext pipedResolved

                // Race Invidious mirrors in parallel as backup
                val invidiousResolved = withTimeoutOrNull(3400L) {
                    coroutineScope {
                        invidiousMirrors.take(3).map { mirror ->
                            async {
                                runCatching {
                                    val body = HttpClientProvider.getBodyString(
                                        "$mirror/api/v1/videos/$videoId",
                                        fastTimeout = true
                                    )
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
                                        ?.takeIf { it.isNotBlank() }
                                        ?.also { bestMirror = mirror }
                                }.getOrNull()
                            }
                        }.awaitAll().firstOrNull { !it.isNullOrBlank() }
                    }
                }
                if (!invidiousResolved.isNullOrBlank()) return@withContext invidiousResolved
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
        // Filter out YouTube Shorts / 30s snippets so only full tracks are returned
        if (duration in 1..42) return null

        val (resolvedArtist, resolvedTitle) = TrackDeduplicationEngine.splitArtistAndTitleIfPrefixed(
            rawTitle = rawTitle,
            fallbackArtist = rawUploader
        )

        val thumb = obj["thumbnail"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val pseudoStream = "ytm://$videoId"
        return Track(
            id = "ytm_$videoId",
            title = resolvedTitle,
            artist = resolvedArtist,
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
            isPreviewOnly = false,
            genre = "YouTube Full Audio"
        )
    }

    private fun parseInvidiousItem(obj: JsonObject, mirror: String): Track? {
        val videoId = obj["videoId"]?.jsonPrimitive?.contentOrNull ?: return null
        val rawTitle = obj["title"]?.jsonPrimitive?.contentOrNull ?: return null
        if (TrackDeduplicationEngine.isTeaserOrSnippetTitle(rawTitle)) return null

        val rawAuthor = obj["author"]?.jsonPrimitive?.contentOrNull ?: "YouTube Artist"
        val duration = obj["lengthSeconds"]?.jsonPrimitive?.intOrNull ?: 0
        if (duration in 1..42) return null

        val (resolvedArtist, resolvedTitle) = TrackDeduplicationEngine.splitArtistAndTitleIfPrefixed(
            rawTitle = rawTitle,
            fallbackArtist = rawAuthor
        )

        val thumb = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        val pseudoStream = "ytm://$videoId"
        return Track(
            id = "ytm_$videoId",
            title = resolvedTitle,
            artist = resolvedArtist,
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
            isPreviewOnly = false,
            genre = "YouTube Full Audio"
        )
    }
}

/**
 * 2. Deezer Public API (https://api.deezer.com)
 * Provides 1000x1000 HD album covers, deep artist discographies (/artist/{id}/top?limit=50),
 * collaborative-filtering similar artists (/artist/{id}/related), and artist autocomplete with cover art.
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

    /**
     * Deep Artist Discography fetch: combines `/artist/{id}/top?limit=50` + strict `artist:"Name"` query
     * so 25–60+ tracks of the artist are discovered instead of just 5!
     */
    override suspend fun searchArtistCatalog(artistName: String, limit: Int): SourceSearchResult =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val cleanName = artistName.trim()
            runCatching {
                val encodedName = HttpClientProvider.encode(cleanName)
                val strictQueryEncoded = HttpClientProvider.encode("artist:\"$cleanName\"")

                coroutineScope {
                    // 1. Lookup exact Deezer Artist ID & fetch top 50 tracks
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

                    // 2. Strict `artist:"Name"` search for additional album tracks (limit=50)
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

                    // 3. Standard search fallback
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

    /**
     * Live artist autocomplete search returning official artist names, cover photos (`picture_medium` / `picture_xl`),
     * and album/listener counts for the Preferences Setup Sheet.
     */
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

    /**
     * Fetches collaborative-filtering related artists from Deezer `/artist/{id}/related`
     * to power highly accurate artist-based recommendations!
     */
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

        val previewUrl = obj["preview"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: return null
        val duration = obj["duration"]?.jsonPrimitive?.intOrNull ?: 180
        val artistObj = obj["artist"]?.jsonObject
        val artistName = artistObj?.get("name")?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"
        val albumObj = obj["album"]?.jsonObject
        val albumTitle = albumObj?.get("title")?.jsonPrimitive?.contentOrNull.orEmpty()
        val coverXl = albumObj?.get("cover_xl")?.jsonPrimitive?.contentOrNull
            ?: albumObj?.get("cover_big")?.jsonPrimitive?.contentOrNull
            ?: artistObj?.get("picture_xl")?.jsonPrimitive?.contentOrNull
            ?: fallbackArtistCover

        val fullTrackResolverUri = "fulltrack://${artistName.trim()}|${title.trim()}|${duration.coerceAtLeast(180)}"

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
                    bitrateKbps = 256,
                    isPreview = false,
                    instanceHost = "FULL-AUDIO"
                ),
                StreamCandidate(
                    provider = SourceProvider.DEEZER,
                    streamUrl = previewUrl,
                    bitrateKbps = 320,
                    isPreview = true,
                    instanceHost = activeMirrorHost
                )
            ),
            isPreviewOnly = false,
            genre = "Deezer Full-Match"
        )
    }
}

/**
 * 3. Apple iTunes Search & Lookup API (https://itunes.apple.com)
 * Supports deep artist catalog lookup (`/lookup?id={artistId}&entity=song&limit=60` + `attribute=artistTerm`).
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

    /**
     * Deep Artist Discography fetch on iTunes:
     * Combines `/lookup?id={artistId}&entity=song&limit=60` with `attribute=artistTerm&limit=50`
     * so all 25–60 tracks of the artist are returned instead of just 5!
     */
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

    /**
     * Fallback/complementary artist suggestions with album cover art from iTunes.
     */
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

        val artist = obj["artistName"]?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"
        val album = obj["collectionName"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val preview = obj["previewUrl"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() } ?: return null
        val art100 = obj["artworkUrl100"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val art600 = art100.replace("100x100bb", "600x600bb")
        val durationMs = obj["trackTimeMillis"]?.jsonPrimitive?.intOrNull ?: 180_000
        val durationSec = (durationMs / 1000).coerceAtLeast(1)
        val genre = obj["primaryGenreName"]?.jsonPrimitive?.contentOrNull ?: "Pop"
        val releaseDate = obj["releaseDate"]?.jsonPrimitive?.contentOrNull.orEmpty().take(4)

        val fullTrackResolverUri = "fulltrack://${artist.trim()}|${title.trim()}|${durationSec.coerceAtLeast(180)}"

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
                    bitrateKbps = 256,
                    isPreview = false,
                    instanceHost = "FULL-AUDIO"
                ),
                StreamCandidate(
                    provider = SourceProvider.ITUNES,
                    streamUrl = preview,
                    bitrateKbps = 256,
                    isPreview = true,
                    instanceHost = activeMirrorHost
                )
            ),
            isPreviewOnly = false,
            genre = genre,
            releaseYear = releaseDate
        )
    }

    override suspend fun getTrending(limit: Int): SourceSearchResult =
        search("Electronic Melodic", limit)
}
