package com.example.data.remote

import com.example.domain.SourceProvider
import com.example.domain.StreamCandidate
import com.example.domain.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 4. Jamendo Music API — Open Creative Commons full-length MP3 streaming.
 */
class JamendoMusicSource : MusicSource {
    override val provider: SourceProvider = SourceProvider.JAMENDO
    override val activeMirrorHost: String = "api.jamendo.com"
    private val publicClientId = "56d30c95" // Jamendo public read-only test client ID

    override suspend fun search(query: String, limit: Int): SourceSearchResult =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            runCatching {
                val encoded = HttpClientProvider.encode(query)
                val url = "https://api.jamendo.com/v3.0/tracks/?client_id=$publicClientId&format=json&limit=$limit&namesearch=$encoded&audioformat=mp32"
                val body = HttpClientProvider.getBodyString(url)
                val root = HttpClientProvider.json.parseToJsonElement(body).jsonObject
                val results = root["results"]?.jsonArray ?: JsonArray(emptyList())
                val tracks = results.mapNotNull { el ->
                    val obj = el.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val title = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val artist = obj["artist_name"]?.jsonPrimitive?.contentOrNull ?: "Jamendo Artist"
                    val album = obj["album_name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val duration = obj["duration"]?.jsonPrimitive?.intOrNull ?: 180
                    val image = obj["album_image"]?.jsonPrimitive?.contentOrNull
                        ?: obj["image"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val audio = obj["audio"]?.jsonPrimitive?.contentOrNull
                        ?.takeIf { it.isNotBlank() }
                        ?: "https://mp3d.jamendo.com/download/track/$id/mp32/"

                    Track(
                        id = "jam_$id",
                        title = title,
                        artist = artist,
                        album = album,
                        artworkUrl = image,
                        durationSec = duration,
                        primaryProvider = SourceProvider.JAMENDO,
                        streamUrl = audio,
                        fallbackStreams = listOf(
                            StreamCandidate(
                                provider = SourceProvider.JAMENDO,
                                streamUrl = audio,
                                bitrateKbps = 320,
                                isPreview = false,
                                instanceHost = activeMirrorHost
                            )
                        ),
                        isPreviewOnly = false,
                        genre = "Creative Commons HD"
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

    override suspend fun searchArtistCatalog(artistName: String, limit: Int): SourceSearchResult =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val clean = artistName.trim()
            runCatching {
                val encoded = HttpClientProvider.encode(clean)
                val byArtistUrl = "https://api.jamendo.com/v3.0/tracks/?client_id=$publicClientId&format=json&limit=${limit.coerceAtLeast(40)}&artist_name=$encoded&audioformat=mp32"
                val body = HttpClientProvider.getBodyString(byArtistUrl)
                val root = HttpClientProvider.json.parseToJsonElement(body).jsonObject
                val results = root["results"]?.jsonArray ?: JsonArray(emptyList())
                val tracks = results.mapNotNull { el ->
                    val obj = el.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val title = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val artist = obj["artist_name"]?.jsonPrimitive?.contentOrNull ?: clean
                    val album = obj["album_name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val duration = obj["duration"]?.jsonPrimitive?.intOrNull ?: 180
                    if (duration in 1..38) return@mapNotNull null
                    val image = obj["album_image"]?.jsonPrimitive?.contentOrNull
                        ?: obj["image"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val audio = obj["audio"]?.jsonPrimitive?.contentOrNull
                        ?.takeIf { it.isNotBlank() }
                        ?: "https://mp3d.jamendo.com/download/track/$id/mp32/"
                    Track(
                        id = "jam_$id",
                        title = title,
                        artist = artist,
                        album = album,
                        artworkUrl = image,
                        durationSec = duration,
                        primaryProvider = SourceProvider.JAMENDO,
                        streamUrl = audio,
                        fallbackStreams = listOf(
                            StreamCandidate(
                                provider = SourceProvider.JAMENDO,
                                streamUrl = audio,
                                bitrateKbps = 320,
                                isPreview = false,
                                instanceHost = activeMirrorHost
                            )
                        ),
                        isPreviewOnly = false,
                        genre = "Creative Commons HD"
                    )
                }
                if (tracks.isNotEmpty()) {
                    SourceSearchResult(
                        provider = provider,
                        tracks = tracks,
                        latencyMs = System.currentTimeMillis() - start,
                        activeMirror = activeMirrorHost,
                        isOnline = true
                    )
                } else {
                    search(clean, limit.coerceAtLeast(35))
                }
            }.getOrElse {
                search(clean, limit.coerceAtLeast(35))
            }
        }

    override suspend fun getTrending(limit: Int): SourceSearchResult =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            runCatching {
                val url = "https://api.jamendo.com/v3.0/tracks/?client_id=$publicClientId&format=json&limit=$limit&order=popularity_week&audioformat=mp32"
                val body = HttpClientProvider.getBodyString(url)
                val root = HttpClientProvider.json.parseToJsonElement(body).jsonObject
                val results = root["results"]?.jsonArray ?: JsonArray(emptyList())
                val tracks = results.mapNotNull { el ->
                    val obj = el.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val title = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val artist = obj["artist_name"]?.jsonPrimitive?.contentOrNull ?: "Jamendo Artist"
                    val album = obj["album_name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val duration = obj["duration"]?.jsonPrimitive?.intOrNull ?: 180
                    val image = obj["album_image"]?.jsonPrimitive?.contentOrNull
                        ?: obj["image"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val audio = obj["audio"]?.jsonPrimitive?.contentOrNull
                        ?.takeIf { it.isNotBlank() }
                        ?: "https://mp3d.jamendo.com/download/track/$id/mp32/"
                    Track(
                        id = "jam_$id",
                        title = title,
                        artist = artist,
                        album = album,
                        artworkUrl = image,
                        durationSec = duration,
                        primaryProvider = SourceProvider.JAMENDO,
                        streamUrl = audio,
                        fallbackStreams = listOf(
                            StreamCandidate(
                                provider = SourceProvider.JAMENDO,
                                streamUrl = audio,
                                bitrateKbps = 320,
                                isPreview = false,
                                instanceHost = activeMirrorHost
                            )
                        ),
                        isPreviewOnly = false,
                        genre = "Jamendo Top"
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
}

/**
 * 5. Audius Decentralized Music API — Direct full-length streaming from gateway nodes.
 */
class AudiusMusicSource : MusicSource {
    override val provider: SourceProvider = SourceProvider.AUDIUS

    @Volatile
    private var discoveryHost: String = "https://discoveryprovider.audius.co"

    override val activeMirrorHost: String
        get() = discoveryHost.removePrefix("https://")

    private fun resolveDiscoveryNode(): String {
        runCatching {
            val body = HttpClientProvider.getBodyString("https://api.audius.co")
            val root = HttpClientProvider.json.parseToJsonElement(body).jsonObject
            val hosts = root["data"]?.jsonArray ?: JsonArray(emptyList())
            val first = hosts.firstOrNull()?.jsonPrimitive?.contentOrNull
            if (!first.isNullOrBlank()) {
                discoveryHost = first.trimEnd('/')
            }
        }
        return discoveryHost
    }

    override suspend fun search(query: String, limit: Int): SourceSearchResult =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            runCatching {
                val host = resolveDiscoveryNode()
                val encoded = HttpClientProvider.encode(query)
                val body = HttpClientProvider.getBodyString(
                    "$host/v1/tracks/search?query=$encoded&app_name=SlantMuz"
                )
                val tracks = parseAudiusResponse(body, host, limit)
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
        search(artistName, limit = limit.coerceAtLeast(40))

    override suspend fun getTrending(limit: Int): SourceSearchResult =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            runCatching {
                val host = resolveDiscoveryNode()
                val body = HttpClientProvider.getBodyString(
                    "$host/v1/tracks/trending?app_name=SlantMuz"
                )
                val tracks = parseAudiusResponse(body, host, limit)
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

    private fun parseAudiusResponse(body: String, host: String, limit: Int): List<Track> {
        val root = HttpClientProvider.json.parseToJsonElement(body).jsonObject
        val data = root["data"]?.jsonArray ?: JsonArray(emptyList())
        return data.take(limit).mapNotNull { el ->
            val obj = el.jsonObject
            val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val rawTitle = obj["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val duration = obj["duration"]?.jsonPrimitive?.intOrNull ?: 180
            if (duration in 1..40) return@mapNotNull null
            val genre = obj["genre"]?.jsonPrimitive?.contentOrNull ?: "Electronic"
            val userObj = obj["user"]?.jsonObject
            val rawArtist = userObj?.get("name")?.jsonPrimitive?.contentOrNull ?: "Audius Creator"
            val (resolvedArtist, resolvedTitle) = com.example.domain.TrackDeduplicationEngine.splitArtistAndTitleIfPrefixed(
                rawTitle = rawTitle,
                fallbackArtist = rawArtist
            )
            val artworkObj = obj["artwork"]?.jsonObject
            val artwork = artworkObj?.get("480x480")?.jsonPrimitive?.contentOrNull
                ?: artworkObj?.get("1000x1000")?.jsonPrimitive?.contentOrNull.orEmpty()
            val streamUrl = "$host/v1/tracks/$id/stream?app_name=SlantMuz"

            Track(
                id = "aud_$id",
                title = resolvedTitle,
                artist = resolvedArtist,
                album = "Audius Network",
                artworkUrl = artwork,
                durationSec = duration,
                primaryProvider = SourceProvider.AUDIUS,
                streamUrl = streamUrl,
                fallbackStreams = listOf(
                    StreamCandidate(
                        provider = SourceProvider.AUDIUS,
                        streamUrl = streamUrl,
                        bitrateKbps = 320,
                        isPreview = false,
                        instanceHost = host.removePrefix("https://")
                    )
                ),
                isPreviewOnly = false,
                genre = genre
            )
        }
    }
}

/**
 * 6. Radio Browser API — Live online radio stations worldwide.
 */
class RadioBrowserSource : MusicSource {
    override val provider: SourceProvider = SourceProvider.RADIO_BROWSER

    private val radioMirrors = listOf(
        "https://de1.api.radio-browser.info",
        "https://fi1.api.radio-browser.info",
        "https://at1.api.radio-browser.info"
    )

    @Volatile
    private var currentMirror = radioMirrors.first()

    override val activeMirrorHost: String
        get() = currentMirror.removePrefix("https://")

    override suspend fun search(query: String, limit: Int): SourceSearchResult =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val encoded = HttpClientProvider.encode(query)
            for (mirror in radioMirrors) {
                val res = runCatching {
                    val url = "$mirror/json/stations/search?name=$encoded&limit=$limit&hidebroken=true&order=clickcount&reverse=true"
                    val body = HttpClientProvider.getBodyString(url)
                    val arr = HttpClientProvider.json.parseToJsonElement(body).jsonArray
                    val tracks = arr.mapNotNull { el ->
                        val obj = el.jsonObject
                        val uuid = obj["stationuuid"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                        val name = obj["name"]?.jsonPrimitive?.contentOrNull?.trim()
                            ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        val streamUrl = obj["url_resolved"]?.jsonPrimitive?.contentOrNull
                            ?: obj["url"]?.jsonPrimitive?.contentOrNull
                            ?: return@mapNotNull null
                        if (!streamUrl.startsWith("http")) return@mapNotNull null
                        val country = obj["country"]?.jsonPrimitive?.contentOrNull ?: "Global"
                        val tags = obj["tags"]?.jsonPrimitive?.contentOrNull ?: "Live Radio"
                        val favicon = obj["favicon"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val bitrate = obj["bitrate"]?.jsonPrimitive?.intOrNull ?: 128

                        Track(
                            id = "radio_$uuid",
                            title = name,
                            artist = "$country • ${bitrate}kbps LIVE",
                            album = tags.split(",").take(2).joinToString(" / "),
                            artworkUrl = favicon,
                            durationSec = 0,
                            primaryProvider = SourceProvider.RADIO_BROWSER,
                            streamUrl = streamUrl,
                            fallbackStreams = listOf(
                                StreamCandidate(
                                    provider = SourceProvider.RADIO_BROWSER,
                                    streamUrl = streamUrl,
                                    bitrateKbps = bitrate.coerceAtLeast(128),
                                    isPreview = false,
                                    instanceHost = mirror.removePrefix("https://")
                                )
                            ),
                            isLiveStream = true,
                            genre = "LIVE Radio"
                        )
                    }
                    currentMirror = mirror
                    tracks
                }.getOrNull()

                if (res != null) {
                    return@withContext SourceSearchResult(
                        provider = provider,
                        tracks = res,
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
                error = "Radio mirrors unreachable"
            )
        }

    override suspend fun getTrending(limit: Int): SourceSearchResult =
        search("Synth", limit)
}

/**
 * 7. Internet Archive (archive.org) — Open live concerts, rare audio & podcasts.
 */
class InternetArchiveSource : MusicSource {
    override val provider: SourceProvider = SourceProvider.INTERNET_ARCHIVE
    override val activeMirrorHost: String = "archive.org"

    override suspend fun search(query: String, limit: Int): SourceSearchResult =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            runCatching {
                val encoded = HttpClientProvider.encode("($query) AND mediatype:(audio)")
                val url = "https://archive.org/advancedsearch.php?q=$encoded&fl[]=identifier,title,creator&rows=$limit&page=1&output=json"
                val body = HttpClientProvider.getBodyString(url)
                val root = HttpClientProvider.json.parseToJsonElement(body).jsonObject
                val docs = root["response"]?.jsonObject?.get("docs")?.jsonArray
                    ?: JsonArray(emptyList())

                val tracks = docs.take(8).mapNotNull { el ->
                    val doc = el.jsonObject
                    val identifier = doc["identifier"]?.jsonPrimitive?.contentOrNull
                        ?: return@mapNotNull null
                    val title = doc["title"]?.jsonPrimitive?.contentOrNull ?: identifier
                    val creator = doc["creator"]?.jsonPrimitive?.contentOrNull ?: "Internet Archive"
                    val artwork = "https://archive.org/services/img/$identifier"
                    val pseudoStream = "archive://$identifier"

                    Track(
                        id = "arch_$identifier",
                        title = title,
                        artist = creator,
                        album = "Internet Archive Audio",
                        artworkUrl = artwork,
                        durationSec = 210,
                        primaryProvider = SourceProvider.INTERNET_ARCHIVE,
                        streamUrl = pseudoStream,
                        fallbackStreams = listOf(
                            StreamCandidate(
                                provider = SourceProvider.INTERNET_ARCHIVE,
                                streamUrl = pseudoStream,
                                bitrateKbps = 256,
                                isPreview = false,
                                instanceHost = activeMirrorHost
                            )
                        ),
                        genre = "Archive Audio"
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
        search(" Ambient Electronic Live", limit)

    override suspend fun resolvePlayableStreamUrl(track: Track): String? =
        withContext(Dispatchers.IO) {
            if (!track.streamUrl.startsWith("archive://")) return@withContext track.streamUrl
            val identifier = track.streamUrl.removePrefix("archive://")
            runCatching {
                val metaBody = HttpClientProvider.getBodyString("https://archive.org/metadata/$identifier")
                val metaObj = HttpClientProvider.json.parseToJsonElement(metaBody).jsonObject
                val files = metaObj["files"]?.jsonArray ?: JsonArray(emptyList())
                val mp3File = files.map { it.jsonObject }.firstOrNull { fileObj ->
                    val name = fileObj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    name.endsWith(".mp3", ignoreCase = true) || name.endsWith(".ogg", ignoreCase = true)
                }?.get("name")?.jsonPrimitive?.contentOrNull
                if (!mp3File.isNullOrBlank()) {
                    "https://archive.org/download/$identifier/${HttpClientProvider.encode(mp3File).replace("+", "%20")}"
                } else {
                    null
                }
            }.getOrNull()
        }
}
