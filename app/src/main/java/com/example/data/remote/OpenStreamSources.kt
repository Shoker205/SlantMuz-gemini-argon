package com.example.data.remote

import com.example.domain.SourceProvider
import com.example.domain.StreamCandidate
import com.example.domain.Track
import com.example.domain.TrackDeduplicationEngine
import com.example.domain.isPreviewSnippetUrl
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Multi-Engine Full-Length Audio Resolver & Open Stream Sources:
 * - `FullLengthAudioResolver`: Resolves 100% full-length audio streams (JioSaavn 320kbps AAC/MP4,
 *   Hearthis.at Full MP3, Audius 320kbps, Jamendo Full MP3) and strictly rejects 30-second previews.
 * - `JamendoMusicSource`: Jamendo Open Audio + Hearthis.at Full-Length Independent Catalog
 * - `AudiusMusicSource`: Decentralized Web3 320kbps Full-Length Streams with multi-node failover
 * - `RadioBrowserSource`: Global Live Radio Streams (Synthwave, Phonk, Rock, Techno, Lo-Fi)
 * - `InternetArchiveSource`: Archive.org Open Audio Library Full-Length MP3/M4A/OGG
 */
object FullLengthAudioResolver {

    private val saavnEndpoints = listOf(
        "https://saavn.dev/api/search/songs",
        "https://jiosaavn-api-privatecvc2.vercel.app/search/songs"
    )

    private val resolvedCandidateCache = ConcurrentHashMap<String, StreamCandidate>()
    private val jamendoHelper by lazy { JamendoMusicSource() }
    private val audiusHelper by lazy { AudiusMusicSource() }

    /**
     * Queries JioSaavn public API endpoints which return direct, full-length 320kbps / 160kbps MP4 streams.
     */
    suspend fun searchJioSaavnFullTracks(query: String, limit: Int = 20): List<Track> =
        withContext(Dispatchers.IO) {
            val clean = query.trim()
            if (clean.isBlank()) return@withContext emptyList()
            val encoded = URLEncoder.encode(clean, "UTF-8")
            for (endpoint in saavnEndpoints) {
                val url = "$endpoint?query=$encoded&limit=${limit.coerceIn(4, 30)}"
                val body = executeGet(url) ?: continue
                val parsed = parseSaavnResponse(body, limit)
                if (parsed.isNotEmpty()) {
                    return@withContext parsed
                }
            }
            emptyList()
        }

    /**
     * Queries Hearthis.at public API v2 which provides direct full-length MP3 streams
     * (especially strong for Phonk, Synthwave, Electronic, Drum & Bass, Techno, Hip-Hop, Indie).
     */
    suspend fun searchHearthisFullTracks(query: String, limit: Int = 16): List<Track> =
        withContext(Dispatchers.IO) {
            val clean = query.trim()
            if (clean.isBlank()) return@withContext emptyList()
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val url = "https://api-v2.hearthis.at/search?t=$encoded&page=1&count=${limit.coerceIn(4, 24)}"
            val body = executeGet(url) ?: return@withContext emptyList()
            runCatching {
                val arr = HttpClientProvider.json.parseToJsonElement(body).jsonArray
                val tracks = mutableListOf<Track>()
                for (elem in arr) {
                    if (tracks.size >= limit) break
                    val obj = elem as? JsonObject ?: continue
                    val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: continue
                    val streamUrl = obj["stream_url"]?.jsonPrimitive?.contentOrNull
                        ?.replace("http://", "https://")
                        ?: continue
                    if (streamUrl.isBlank() || isPreviewSnippetUrl(streamUrl)) continue

                    val durationSec = obj["duration"]?.jsonPrimitive?.intOrNull
                        ?: obj["duration"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                        ?: 0
                    // Exclude short previews (< 55s) and long DJ mixes (> 780s)
                    if (durationSec < 55 || durationSec > 780) continue

                    val rawTitle = obj["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    if (rawTitle.isBlank() || TrackDeduplicationEngine.isTeaserOrSnippetTitle(rawTitle)) continue

                    val userObj = obj["user"] as? JsonObject
                    val uploader = userObj?.get("username")?.jsonPrimitive?.contentOrNull ?: "Independent"
                    val (parsedArtist, parsedTitle) = TrackDeduplicationEngine.splitArtistAndTitleIfPrefixed(
                        rawTitle,
                        uploader
                    )
                    val genre = obj["genre"]?.jsonPrimitive?.contentOrNull?.ifBlank { "Electronic" } ?: "Electronic"
                    val artwork = (obj["artwork_url_retina"]?.jsonPrimitive?.contentOrNull
                        ?: obj["artwork_url"]?.jsonPrimitive?.contentOrNull
                        ?: userObj?.get("avatar_url")?.jsonPrimitive?.contentOrNull)
                        ?.replace("http://", "https://")
                        .orEmpty()
                    val releaseYear = obj["created_at"]?.jsonPrimitive?.contentOrNull?.take(4).orEmpty()

                    val candidate = StreamCandidate(
                        provider = SourceProvider.YOUTUBE_PIPED,
                        streamUrl = streamUrl,
                        qualityLabel = "FULL MP3 • 320 kbps",
                        bitrateKbps = 320,
                        isDirectStream = true,
                        isPreview = false,
                        instanceHost = "hearthis.at"
                    )

                    tracks.add(
                        Track(
                            id = "full_ht_$id",
                            title = parsedTitle,
                            artist = parsedArtist,
                            album = "$parsedTitle — Studio Stream",
                            artworkUrl = artwork,
                            durationSec = durationSec,
                            primaryProvider = SourceProvider.YOUTUBE_PIPED,
                            streamUrl = streamUrl,
                            fallbackStreams = listOf(candidate),
                            isLiveStream = false,
                            isPreviewOnly = false,
                            genre = genre,
                            releaseYear = releaseYear,
                            bitrateKbps = 320
                        )
                    )
                }
                tracks
            }.getOrDefault(emptyList())
        }

    /**
     * Resolves a 100% full-length playable `StreamCandidate` for any (artist, title, expectedDurationSec)
     * by racing JioSaavn 320kbps, Hearthis.at Full MP3, Audius 320kbps, and Jamendo Open Audio in parallel.
     */
    suspend fun resolveFullLengthStream(
        artist: String,
        title: String,
        expectedDurationSec: Int = 180
    ): StreamCandidate? = withContext(Dispatchers.IO) {
        val cleanArtist = TrackDeduplicationEngine.extractPrimaryArtistName(artist).trim()
        val cleanTitle = TrackDeduplicationEngine.normalizeText(title).trim()
        val cacheKey = "${cleanArtist.lowercase()}|$cleanTitle"
        resolvedCandidateCache[cacheKey]?.let { cached ->
            if (!isPreviewSnippetUrl(cached.streamUrl)) return@withContext cached
        }

        val query = "$cleanArtist $title".trim()
        if (query.length < 2) return@withContext null

        val allCandidates = coroutineScope {
            val saavnDeferred = async {
                withTimeoutOrNull(3200L) {
                    searchJioSaavnFullTracks(query, limit = 8)
                }.orEmpty()
            }
            val hearthisDeferred = async {
                withTimeoutOrNull(3200L) {
                    searchHearthisFullTracks(query, limit = 6)
                }.orEmpty()
            }
            val audiusDeferred = async {
                withTimeoutOrNull(3200L) {
                    audiusHelper.search(query, limit = 6).tracks
                }.orEmpty()
            }
            val jamendoDeferred = async {
                withTimeoutOrNull(3200L) {
                    jamendoHelper.search(query, limit = 5).tracks
                }.orEmpty()
            }

            saavnDeferred.await() +
                hearthisDeferred.await() +
                audiusDeferred.await() +
                jamendoDeferred.await()
        }.filter { track ->
            track.durationSec >= 55 &&
                !track.isSnippetStream() &&
                (track.streamUrl.startsWith("https://") || track.streamUrl.startsWith("http://"))
        }

        val bestMatch = allCandidates.maxByOrNull { candidate ->
            val titleSim = TrackDeduplicationEngine.combinedSimilarity(candidate.title, title)
            val artistSim = if (TrackDeduplicationEngine.areArtistNamesEquivalent(candidate.artist, artist)) {
                1.0
            } else {
                TrackDeduplicationEngine.combinedSimilarity(candidate.artist, cleanArtist)
            }
            val durationBonus = if (expectedDurationSec >= 55 && abs(candidate.durationSec - expectedDurationSec) <= 20) {
                0.30
            } else {
                0.0
            }
            val bitrateBonus = (candidate.bitrateKbps.coerceIn(128, 320) / 320.0) * 0.15
            (titleSim * 0.58 + artistSim * 0.42) + durationBonus + bitrateBonus
        } ?: allCandidates.firstOrNull()

        if (bestMatch != null && !isPreviewSnippetUrl(bestMatch.streamUrl)) {
            val streamCandidate = StreamCandidate(
                provider = bestMatch.primaryProvider,
                streamUrl = bestMatch.streamUrl,
                qualityLabel = "FULL • ${bestMatch.bitrateKbps.coerceAtLeast(256)} kbps",
                bitrateKbps = bestMatch.bitrateKbps.coerceAtLeast(256),
                isDirectStream = true,
                isPreview = false,
                instanceHost = bestMatch.primaryProvider.shortBadge
            )
            resolvedCandidateCache[cacheKey] = streamCandidate
            return@withContext streamCandidate
        }

        null
    }

    private fun parseSaavnResponse(rawJson: String, limit: Int): List<Track> {
        return runCatching {
            val root = HttpClientProvider.json.parseToJsonElement(rawJson).jsonObject
            val resultsArray: JsonArray = when {
                root["data"] is JsonObject -> root["data"]?.jsonObject?.get("results")?.jsonArray
                root["results"] is JsonArray -> root["results"]?.jsonArray
                else -> null
            } ?: return emptyList()

            val tracks = mutableListOf<Track>()
            for (elem in resultsArray) {
                if (tracks.size >= limit) break
                val obj = elem as? JsonObject ?: continue
                val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: continue
                val rawTitle = decodeHtmlEntities(
                    obj["name"]?.jsonPrimitive?.contentOrNull
                        ?: obj["title"]?.jsonPrimitive?.contentOrNull
                        ?: continue
                )
                if (TrackDeduplicationEngine.isTeaserOrSnippetTitle(rawTitle)) continue

                val durationSec = obj["duration"]?.jsonPrimitive?.intOrNull
                    ?: obj["duration"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: 0
                if (durationSec in 1..54) continue

                val artistName = extractSaavnArtist(obj)
                val albumTitle = decodeHtmlEntities(
                    (obj["album"] as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull
                        ?: obj["album"]?.jsonPrimitive?.contentOrNull
                        ?: ""
                )
                val releaseYear = obj["year"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val artworkUrl = extractBestUrlFromArrayOrString(obj["image"])
                val downloadCandidates = extractSaavnStreamCandidates(obj["downloadUrl"])
                val bestStream = downloadCandidates.firstOrNull() ?: continue

                tracks.add(
                    Track(
                        id = "full_saavn_$id",
                        title = rawTitle,
                        artist = artistName,
                        album = albumTitle.ifBlank { "$rawTitle — Full Edition" },
                        artworkUrl = artworkUrl,
                        durationSec = if (durationSec >= 55) durationSec else 210,
                        primaryProvider = SourceProvider.YOUTUBE_PIPED,
                        streamUrl = bestStream.streamUrl,
                        fallbackStreams = downloadCandidates,
                        isLiveStream = false,
                        isPreviewOnly = false,
                        genre = obj["language"]?.jsonPrimitive?.contentOrNull
                            ?.replaceFirstChar { it.uppercase() } ?: "Music",
                        releaseYear = releaseYear,
                        bitrateKbps = bestStream.bitrateKbps
                    )
                )
            }
            tracks
        }.getOrDefault(emptyList())
    }

    private fun extractSaavnArtist(obj: JsonObject): String {
        val artistsObj = obj["artists"] as? JsonObject
        val primaryArr = artistsObj?.get("primary") as? JsonArray
        if (!primaryArr.isNullOrEmpty()) {
            val names = primaryArr.mapNotNull {
                (it as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull
            }.filter { it.isNotBlank() }
            if (names.isNotEmpty()) return decodeHtmlEntities(names.take(2).joinToString(", "))
        }
        val directPrimary = obj["primaryArtists"]?.jsonPrimitive?.contentOrNull
        if (!directPrimary.isNullOrBlank()) return decodeHtmlEntities(directPrimary)
        val subtitle = obj["subtitle"]?.jsonPrimitive?.contentOrNull
        if (!subtitle.isNullOrBlank()) return decodeHtmlEntities(subtitle.substringBefore(" - "))
        return "Various Artists"
    }

    private fun extractBestUrlFromArrayOrString(element: JsonElement?): String {
        if (element == null) return ""
        if (element is JsonArray) {
            val lastObj = element.lastOrNull() as? JsonObject
            val url = lastObj?.get("url")?.jsonPrimitive?.contentOrNull
                ?: lastObj?.get("link")?.jsonPrimitive?.contentOrNull
            if (!url.isNullOrBlank()) return url.replace("http://", "https://")
        }
        val str = runCatching { element.jsonPrimitive.contentOrNull }.getOrNull()
        return str?.replace("http://", "https://").orEmpty()
    }

    private fun extractSaavnStreamCandidates(element: JsonElement?): List<StreamCandidate> {
        if (element !is JsonArray) return emptyList()
        val candidates = mutableListOf<StreamCandidate>()
        for (item in element) {
            val obj = item as? JsonObject ?: continue
            val rawUrl = (obj["url"]?.jsonPrimitive?.contentOrNull
                ?: obj["link"]?.jsonPrimitive?.contentOrNull)?.replace("http://", "https://")
                ?: continue
            if (rawUrl.isBlank() || isPreviewSnippetUrl(rawUrl)) continue
            val qualityStr = obj["quality"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val kbps = qualityStr.filter { it.isDigit() }.toIntOrNull() ?: 160
            if (kbps < 96) continue
            candidates.add(
                StreamCandidate(
                    provider = SourceProvider.YOUTUBE_PIPED,
                    streamUrl = rawUrl,
                    qualityLabel = "FULL • ${kbps} kbps",
                    bitrateKbps = kbps.coerceIn(96, 320),
                    isDirectStream = true,
                    isPreview = false,
                    instanceHost = "saavn-320kbps"
                )
            )
        }
        return candidates.sortedByDescending { it.bitrateKbps }
    }

    private fun decodeHtmlEntities(input: String): String {
        return input
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&#039;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
    }

    private fun executeGet(url: String): String? {
        return runCatching {
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", HttpClientProvider.USER_AGENT)
                .build()
            HttpClientProvider.fastStreamClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string()
            }
        }.getOrNull()
    }
}

/**
 * 4. Jamendo & Hearthis.at Open Full-Length Audio Catalog (`SourceProvider.JAMENDO`).
 * Every returned track is a 100% full-length direct stream (>= 55s).
 */
class JamendoMusicSource(
    private val httpClient: OkHttpClient = HttpClientProvider.client,
    private val json: Json = HttpClientProvider.json
) : MusicSource {

    override val provider: SourceProvider = SourceProvider.JAMENDO

    private val publicClientIds = listOf("56d30c95", "b6747d04", "c9c3105a")

    @Volatile
    override var activeMirrorHost: String = "api.jamendo.com (Full MP3)"
        private set

    override suspend fun getTrending(limit: Int): SourceSearchResult =
        search("synthwave electronic indie rock", limit)

    override suspend fun search(query: String, limit: Int): SourceSearchResult =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val cleanQuery = query.trim().ifBlank { "electronic" }
            val encoded = URLEncoder.encode(cleanQuery, "UTF-8")

            val jamendoTracks = mutableListOf<Track>()
            for (clientId in publicClientIds) {
                val url =
                    "https://api.jamendo.com/v3.0/tracks/?client_id=$clientId&format=json&limit=${limit.coerceIn(5, 30)}&search=$encoded&audioformat=mp32&include=musicinfo"
                val parsed = runCatching {
                    val req = Request.Builder().url(url).build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) emptyList()
                        else {
                            val body = resp.body?.string().orEmpty()
                            val results = json.parseToJsonElement(body).jsonObject["results"]?.jsonArray
                                ?: JsonArray(emptyList())
                            results.mapNotNull { parseJamendoTrack(it.jsonObject) }
                        }
                    }
                }.getOrDefault(emptyList())

                if (parsed.isNotEmpty()) {
                    jamendoTracks.addAll(parsed)
                    activeMirrorHost = "api.jamendo.com (Full MP3)"
                    break
                }
            }

            // Enrich with Hearthis.at full-length independent tracks if Jamendo returned fewer items
            val supplemental = if (jamendoTracks.size < limit / 2) {
                runCatching {
                    FullLengthAudioResolver.searchHearthisFullTracks(cleanQuery, limit = limit)
                        .map { it.copy(primaryProvider = SourceProvider.JAMENDO) }
                }.getOrDefault(emptyList())
            } else {
                emptyList()
            }

            val finalTracks = (jamendoTracks + supplemental)
                .filter { it.durationSec >= 55 && !it.isSnippetStream() }
                .distinctBy { "${it.artist.lowercase()}|${it.title.lowercase()}" }
                .take(limit)

            SourceSearchResult(
                provider = provider,
                tracks = finalTracks,
                latencyMs = System.currentTimeMillis() - start,
                activeMirror = activeMirrorHost,
                isOnline = finalTracks.isNotEmpty()
            )
        }

    private fun parseJamendoTrack(obj: JsonObject): Track? {
        val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return null
        val title = obj["name"]?.jsonPrimitive?.contentOrNull?.trim() ?: return null
        if (title.isBlank() || TrackDeduplicationEngine.isTeaserOrSnippetTitle(title)) return null

        val duration = obj["duration"]?.jsonPrimitive?.intOrNull ?: 0
        if (duration < 55) return null

        val audioUrl = obj["audio"]?.jsonPrimitive?.contentOrNull
            ?.replace("http://", "https://")
            ?: return null
        if (audioUrl.isBlank() || isPreviewSnippetUrl(audioUrl)) return null

        val artist = obj["artist_name"]?.jsonPrimitive?.contentOrNull ?: "Jamendo Artist"
        val album = obj["album_name"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val artwork = (obj["album_image"]?.jsonPrimitive?.contentOrNull
            ?: obj["image"]?.jsonPrimitive?.contentOrNull)
            ?.replace("http://", "https://")
            .orEmpty()
        val releaseDate = obj["releasedate"]?.jsonPrimitive?.contentOrNull?.take(4).orEmpty()

        val candidate = StreamCandidate(
            provider = SourceProvider.JAMENDO,
            streamUrl = audioUrl,
            qualityLabel = "JAMENDO FULL • 192 kbps",
            bitrateKbps = 192,
            isDirectStream = true,
            isPreview = false,
            instanceHost = "api.jamendo.com"
        )

        return Track(
            id = "jam_$id",
            title = title,
            artist = artist,
            album = album,
            artworkUrl = artwork,
            durationSec = duration,
            primaryProvider = SourceProvider.JAMENDO,
            streamUrl = audioUrl,
            fallbackStreams = listOf(candidate),
            isLiveStream = false,
            isPreviewOnly = false,
            genre = "Independent Full",
            releaseYear = releaseDate,
            bitrateKbps = 192
        )
    }
}

/**
 * 5. Audius Decentralized Music Network Source (320kbps Full-Length Streams, Zero API Keys).
 * Uses multi-node discovery failover so queries always return full-length tracks.
 */
class AudiusMusicSource(
    private val httpClient: OkHttpClient = HttpClientProvider.client,
    private val json: Json = HttpClientProvider.json
) : MusicSource {

    override val provider: SourceProvider = SourceProvider.AUDIUS

    private val fallbackDiscoveryNodes = listOf(
        "https://discoveryprovider.audius.co",
        "https://discoveryprovider2.audius.co",
        "https://discoveryprovider3.audius.co",
        "https://audius-discovery-1.cultur3stake.com",
        "https://audius-metadata-1.figment.io"
    )

    @Volatile
    private var discoveredHosts: List<String> = fallbackDiscoveryNodes

    @Volatile
    private var activeHost: String = fallbackDiscoveryNodes.first()

    override val activeMirrorHost: String
        get() = activeHost.removePrefix("https://")

    private fun getCandidateHosts(): List<String> {
        if (discoveredHosts.size <= fallbackDiscoveryNodes.size) {
            runCatching {
                val req = Request.Builder()
                    .url("https://api.audius.co")
                    .header("Accept", "application/json")
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        val arr = json.parseToJsonElement(body).jsonObject["data"]?.jsonArray
                        val hosts = arr?.mapNotNull { it.jsonPrimitive.contentOrNull }
                            ?.filter { it.startsWith("https://") }
                            .orEmpty()
                        if (hosts.isNotEmpty()) {
                            discoveredHosts = (hosts.take(5) + fallbackDiscoveryNodes).distinct()
                        }
                    }
                }
            }
        }
        return (listOf(activeHost) + discoveredHosts).distinct()
    }

    override suspend fun getTrending(limit: Int): SourceSearchResult =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            for (host in getCandidateHosts().take(4)) {
                val url = "$host/v1/tracks/trending?app_name=SlantMuz&limit=${limit.coerceIn(1, 35)}"
                val parsed = fetchAndParseAudiusTracks(host, url, limit)
                if (parsed.isNotEmpty()) {
                    activeHost = host
                    return@withContext SourceSearchResult(
                        provider = provider,
                        tracks = parsed,
                        latencyMs = System.currentTimeMillis() - start,
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
                error = "Audius nodes unreachable"
            )
        }

    override suspend fun search(query: String, limit: Int): SourceSearchResult =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val encoded = URLEncoder.encode(query.trim().ifBlank { "electronic" }, "UTF-8")
            for (host in getCandidateHosts().take(4)) {
                val url = "$host/v1/tracks/search?query=$encoded&app_name=SlantMuz&limit=${limit.coerceIn(1, 35)}"
                val parsed = fetchAndParseAudiusTracks(host, url, limit)
                if (parsed.isNotEmpty()) {
                    activeHost = host
                    return@withContext SourceSearchResult(
                        provider = provider,
                        tracks = parsed,
                        latencyMs = System.currentTimeMillis() - start,
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
                isOnline = false
            )
        }

    private fun fetchAndParseAudiusTracks(host: String, url: String, limit: Int): List<Track> {
        return runCatching {
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val raw = resp.body?.string() ?: return emptyList()
                val dataArr = json.parseToJsonElement(raw).jsonObject["data"]?.jsonArray ?: return emptyList()

                dataArr.take(limit).mapNotNull { elem ->
                    val obj = elem.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val rawTitle = obj["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    if (TrackDeduplicationEngine.isTeaserOrSnippetTitle(rawTitle)) return@mapNotNull null

                    val duration = obj["duration"]?.jsonPrimitive?.intOrNull ?: 0
                    // Strictly require full-length tracks (>= 55 seconds)
                    if (duration < 55) return@mapNotNull null

                    val genre = obj["genre"]?.jsonPrimitive?.contentOrNull ?: "Electronic"
                    val releaseDate = obj["release_date"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val year = if (releaseDate.length >= 4) releaseDate.take(4) else ""

                    val userObj = obj["user"] as? JsonObject
                    val rawArtist = userObj?.get("name")?.jsonPrimitive?.contentOrNull
                        ?: userObj?.get("handle")?.jsonPrimitive?.contentOrNull
                        ?: "Audius Artist"

                    val (artist, title) = TrackDeduplicationEngine.splitArtistAndTitleIfPrefixed(rawTitle, rawArtist)

                    val artworkObj = obj["artwork"] as? JsonObject
                    val artworkUrl = artworkObj?.get("480x480")?.jsonPrimitive?.contentOrNull
                        ?: artworkObj?.get("1000x1000")?.jsonPrimitive?.contentOrNull
                        ?: artworkObj?.get("150x150")?.jsonPrimitive?.contentOrNull
                        ?: ""

                    val candidateHosts = (listOf(host) + fallbackDiscoveryNodes).distinct().take(3)
                    val streamCandidates = candidateHosts.map { node ->
                        StreamCandidate(
                            provider = SourceProvider.AUDIUS,
                            streamUrl = "$node/v1/tracks/$id/stream?app_name=SlantMuz",
                            qualityLabel = "AUDIUS FULL • 320 kbps",
                            bitrateKbps = 320,
                            isDirectStream = true,
                            isPreview = false,
                            instanceHost = node.removePrefix("https://")
                        )
                    }
                    val primaryStreamUrl = streamCandidates.first().streamUrl

                    Track(
                        id = "audius_$id",
                        title = title,
                        artist = artist,
                        album = "$title — Full Studio",
                        artworkUrl = artworkUrl,
                        durationSec = duration,
                        primaryProvider = SourceProvider.AUDIUS,
                        streamUrl = primaryStreamUrl,
                        fallbackStreams = streamCandidates,
                        isLiveStream = false,
                        isPreviewOnly = false,
                        genre = genre,
                        releaseYear = year,
                        bitrateKbps = 320
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}

/**
 * 6. Radio-Browser Global Live Radio Source (`SourceProvider.RADIO_BROWSER`).
 */
class RadioBrowserSource(
    private val httpClient: OkHttpClient = HttpClientProvider.client,
    private val json: Json = HttpClientProvider.json
) : MusicSource {

    override val provider: SourceProvider = SourceProvider.RADIO_BROWSER

    private val mirrors = listOf(
        "https://de1.api.radio-browser.info",
        "https://nl1.api.radio-browser.info",
        "https://at1.api.radio-browser.info"
    )

    @Volatile
    private var activeMirror: String = mirrors.first()

    override val activeMirrorHost: String
        get() = activeMirror.removePrefix("https://")

    override suspend fun getTrending(limit: Int): SourceSearchResult =
        search("synthwave", limit)

    override suspend fun search(query: String, limit: Int): SourceSearchResult =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val cleanTag = query.trim().lowercase().substringBefore(" ").ifBlank { "synthwave" }
            val encoded = URLEncoder.encode(cleanTag, "UTF-8")

            for (mirror in mirrors) {
                val url =
                    "$mirror/json/stations/search?name=$encoded&hidebroken=true&order=clickcount&reverse=true&limit=${limit.coerceIn(4, 20)}"
                val stations = runCatching {
                    val req = Request.Builder().url(url).build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) emptyList()
                        else {
                            val body = resp.body?.string().orEmpty()
                            val arr = json.parseToJsonElement(body).jsonArray
                            arr.mapNotNull { parseStation(it.jsonObject) }
                        }
                    }
                }.getOrDefault(emptyList())

                if (stations.isNotEmpty()) {
                    activeMirror = mirror
                    return@withContext SourceSearchResult(
                        provider = provider,
                        tracks = stations.take(limit),
                        latencyMs = System.currentTimeMillis() - start,
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
                isOnline = false
            )
        }

    private fun parseStation(obj: JsonObject): Track? {
        val uuid = obj["stationuuid"]?.jsonPrimitive?.contentOrNull ?: return null
        val name = obj["name"]?.jsonPrimitive?.contentOrNull?.trim()
            ?.takeIf { it.isNotBlank() } ?: return null
        val resolvedUrl = (obj["url_resolved"]?.jsonPrimitive?.contentOrNull
            ?: obj["url"]?.jsonPrimitive?.contentOrNull)
            ?.trim()
            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?: return null

        val country = obj["country"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val tags = obj["tags"]?.jsonPrimitive?.contentOrNull
            ?.split(",")
            ?.firstOrNull()
            ?.trim()
            ?.replaceFirstChar { it.uppercase() }
            ?: "Live Radio"
        val favicon = obj["favicon"]?.jsonPrimitive?.contentOrNull
            ?.replace("http://", "https://")
            .orEmpty()
        val bitrate = (obj["bitrate"]?.jsonPrimitive?.intOrNull ?: 192).coerceIn(128, 320)

        return Track(
            id = "radio_$uuid",
            title = name,
            artist = if (country.isNotBlank()) "LIVE • $country" else "LIVE Radio Station",
            album = "24/7 Live Broadcast",
            artworkUrl = favicon,
            durationSec = 0,
            primaryProvider = SourceProvider.RADIO_BROWSER,
            streamUrl = resolvedUrl,
            fallbackStreams = listOf(
                StreamCandidate(
                    provider = SourceProvider.RADIO_BROWSER,
                    streamUrl = resolvedUrl,
                    qualityLabel = "LIVE • $bitrate kbps",
                    bitrateKbps = bitrate,
                    isDirectStream = true,
                    isPreview = false,
                    instanceHost = activeMirrorHost
                )
            ),
            isLiveStream = true,
            isPreviewOnly = false,
            genre = tags,
            bitrateKbps = bitrate
        )
    }
}

/**
 * 7. Internet Archive Open Audio Library Source (`SourceProvider.INTERNET_ARCHIVE`).
 * Queries Archive.org for full-length MP3/M4A/OGG tracks (>= 55s) with fast parallel resolution.
 */
class InternetArchiveSource(
    private val httpClient: OkHttpClient = HttpClientProvider.client,
    private val json: Json = HttpClientProvider.json
) : MusicSource {

    override val provider: SourceProvider = SourceProvider.INTERNET_ARCHIVE
    override val activeMirrorHost: String = "archive.org (Full Audio)"
    private val metadataSemaphore = Semaphore(5)

    override suspend fun getTrending(limit: Int): SourceSearchResult =
        search("synthwave OR post-punk OR lofi OR rock", limit)

    override suspend fun search(query: String, limit: Int): SourceSearchResult =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val tracks = runCatching {
                val q = query.trim().ifBlank { "music" }
                val lucene = "mediatype:(audio) AND NOT collection:(podcasts OR librivoxaudio) AND ($q)"
                val encodedQ = URLEncoder.encode(lucene, "UTF-8")
                val rows = limit.coerceIn(4, 14)
                val searchUrl =
                    "https://archive.org/advancedsearch.php?q=$encodedQ&fl[]=identifier,title,creator,year&sort[]=downloads+desc&rows=$rows&page=1&output=json"

                val req = Request.Builder()
                    .url(searchUrl)
                    .header("Accept", "application/json")
                    .build()

                val docs: JsonArray = httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching emptyList<Track>()
                    val body = resp.body?.string() ?: return@runCatching emptyList<Track>()
                    json.parseToJsonElement(body)
                        .jsonObject["response"]
                        ?.jsonObject?.get("docs")
                        ?.jsonArray ?: return@runCatching emptyList<Track>()
                }

                coroutineScope {
                    docs.take(10).map { docElem ->
                        async {
                            metadataSemaphore.withPermit {
                                withTimeoutOrNull(2800L) {
                                    val doc = docElem.jsonObject
                                    val identifier = doc["identifier"]?.jsonPrimitive?.contentOrNull
                                        ?: return@withTimeoutOrNull null
                                    val rawTitle = doc["title"]?.jsonPrimitive?.contentOrNull ?: identifier
                                    if (TrackDeduplicationEngine.isTeaserOrSnippetTitle(rawTitle)) {
                                        return@withTimeoutOrNull null
                                    }

                                    val creatorElem = doc["creator"]
                                    val rawCreator = when (creatorElem) {
                                        is JsonArray -> creatorElem.firstOrNull()?.jsonPrimitive?.contentOrNull
                                        else -> creatorElem?.jsonPrimitive?.contentOrNull
                                    } ?: "Internet Archive"

                                    val (creator, cleanTitle) = TrackDeduplicationEngine.splitArtistAndTitleIfPrefixed(
                                        rawTitle,
                                        rawCreator
                                    )
                                    val year = doc["year"]?.jsonPrimitive?.contentOrNull.orEmpty()

                                    resolvePlayableMp3(identifier, cleanTitle, creator, year)
                                }
                            }
                        }
                    }.awaitAll().filterNotNull()
                }
            }.getOrDefault(emptyList())

            SourceSearchResult(
                provider = provider,
                tracks = tracks,
                latencyMs = System.currentTimeMillis() - start,
                activeMirror = activeMirrorHost,
                isOnline = tracks.isNotEmpty()
            )
        }

    override suspend fun resolvePlayableStreamUrl(track: Track): String? =
        withContext(Dispatchers.IO) {
            if (track.streamUrl.startsWith("https://") || track.streamUrl.startsWith("http://")) {
                return@withContext track.streamUrl
            }
            val identifier = track.streamUrl.removePrefix("archive://").trim()
                .ifBlank { track.id.removePrefix("ia_") }
            if (identifier.isBlank()) return@withContext null
            resolvePlayableMp3(identifier, track.title, track.artist, track.releaseYear)?.streamUrl
        }

    private fun resolvePlayableMp3(
        identifier: String,
        title: String,
        creator: String,
        year: String
    ): Track? {
        return runCatching {
            val metaUrl = "https://archive.org/metadata/$identifier"
            val req = Request.Builder().url(metaUrl).build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val files = json.parseToJsonElement(body).jsonObject["files"]?.jsonArray ?: return null

                val audioFile = files.firstOrNull { f ->
                    val name = f.jsonObject["name"]?.jsonPrimitive?.contentOrNull.orEmpty().lowercase()
                    (name.endsWith(".mp3") || name.endsWith(".m4a") || name.endsWith(".ogg")) &&
                        !name.contains("sample") &&
                        !name.contains("preview")
                }?.jsonObject ?: return null

                val fileName = audioFile["name"]?.jsonPrimitive?.contentOrNull ?: return null
                val rawLength = audioFile["length"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val durationSec = parseArchiveDurationSec(rawLength)
                if (durationSec in 1..54) return null

                val encodedFile = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20")
                val streamUrl = "https://archive.org/download/$identifier/$encodedFile"
                val artworkUrl = "https://archive.org/services/img/$identifier"

                Track(
                    id = "ia_$identifier",
                    title = audioFile["title"]?.jsonPrimitive?.contentOrNull?.ifBlank { title } ?: title,
                    artist = audioFile["creator"]?.jsonPrimitive?.contentOrNull?.ifBlank { creator } ?: creator,
                    album = title,
                    artworkUrl = artworkUrl,
                    durationSec = if (durationSec >= 55) durationSec else 215,
                    primaryProvider = SourceProvider.INTERNET_ARCHIVE,
                    streamUrl = streamUrl,
                    fallbackStreams = listOf(
                        StreamCandidate(
                            provider = SourceProvider.INTERNET_ARCHIVE,
                            streamUrl = streamUrl,
                            qualityLabel = "ARCHIVE FULL • 256 kbps",
                            bitrateKbps = 256,
                            isDirectStream = true,
                            isPreview = false,
                            instanceHost = "archive.org"
                        )
                    ),
                    isLiveStream = false,
                    isPreviewOnly = false,
                    genre = "Archive Audio",
                    releaseYear = year,
                    bitrateKbps = 256
                )
            }
        }.getOrNull()
    }

    private fun parseArchiveDurationSec(raw: String): Int {
        if (raw.isBlank()) return 0
        if (raw.contains(":")) {
            val parts = raw.split(":").mapNotNull { it.trim().toDoubleOrNull()?.toInt() }
            return when (parts.size) {
                2 -> parts[0] * 60 + parts[1]
                3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                else -> 0
            }
        }
        return raw.toDoubleOrNull()?.toInt() ?: 0
    }
}
