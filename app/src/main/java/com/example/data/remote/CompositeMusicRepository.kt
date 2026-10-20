package com.example.data.remote

import android.content.Context
import com.example.data.local.LocalMediaScanner
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistTrackCrossRef
import com.example.data.local.PlaylistWithTracks
import com.example.data.local.SearchHistoryEntity
import com.example.data.local.SlantMuzDao
import com.example.data.local.TrackEntity
import com.example.domain.ProviderHealthStatus
import com.example.domain.SourceFilter
import com.example.domain.SourceProvider
import com.example.domain.Track
import com.example.domain.TrackDeduplicationEngine
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.Request

class CompositeMusicRepository(
    private val context: Context,
    private val dao: SlantMuzDao,
    private val localScanner: LocalMediaScanner
) {
    private val youtubeSource = YouTubePipedInvidiousSource()
    private val deezerSource = DeezerMusicSource()
    private val itunesSource = ITunesMusicSource()
    private val jamendoSource = JamendoMusicSource()
    private val audiusSource = AudiusMusicSource()
    private val radioSource = RadioBrowserSource()
    private val archiveSource = InternetArchiveSource()

    private val networkSources: List<MusicSource> = listOf(
        jamendoSource,
        audiusSource,
        deezerSource,
        itunesSource,
        youtubeSource,
        radioSource,
        archiveSource
    )

    private val _providerStatuses = MutableStateFlow<List<ProviderHealthStatus>>(
        SourceProvider.entries.map { provider ->
            ProviderHealthStatus(
                provider = provider,
                enabled = true,
                isOnline = true,
                latencyMs = if (provider == SourceProvider.LOCAL) 2L else 0L,
                activeMirror = when (provider) {
                    SourceProvider.LOCAL -> "MediaStore.Audio"
                    SourceProvider.YOUTUBE_PIPED -> youtubeSource.activeMirrorHost
                    SourceProvider.JAMENDO -> jamendoSource.activeMirrorHost
                    SourceProvider.AUDIUS -> audiusSource.activeMirrorHost
                    SourceProvider.DEEZER -> deezerSource.activeMirrorHost
                    SourceProvider.ITUNES -> itunesSource.activeMirrorHost
                    SourceProvider.RADIO_BROWSER -> radioSource.activeMirrorHost
                    SourceProvider.INTERNET_ARCHIVE -> archiveSource.activeMirrorHost
                }
            )
        }
    )
    val providerStatuses: StateFlow<List<ProviderHealthStatus>> = _providerStatuses.asStateFlow()

    val favoriteTracksFlow: Flow<List<Track>> = dao.observeFavoriteTracks()
        .map { list -> list.map { it.toDomain() } }

    val historyTracksFlow: Flow<List<Track>> = dao.observeHistoryTracks()
        .map { list -> list.map { it.toDomain() } }

    val offlineCachedTracksFlow: Flow<List<Track>> = dao.observeOfflineCachedTracks()
        .map { list -> list.map { it.toDomain() } }

    val totalOfflineBytesFlow: Flow<Long> = dao.observeTotalOfflineBytes()

    val playlistsFlow: Flow<List<PlaylistWithTracks>> = dao.observePlaylistsWithTracks()

    val searchHistoryFlow: Flow<List<String>> = dao.observeSearchHistory()
        .map { list -> list.map { it.query } }

    suspend fun scanLocalTracks(): List<Track> {
        val start = System.currentTimeMillis()
        val tracks = localScanner.scanDeviceAudio()
        val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
        updateProviderHealth(
            provider = SourceProvider.LOCAL,
            isOnline = true,
            latencyMs = elapsed,
            activeMirror = "MediaStore (${tracks.size} файлов)",
            tracksCount = tracks.size,
            error = null
        )
        return tracks
    }

    /**
     * Multi-source fault-tolerant parallel search using async {} and awaitAll(),
     * followed by intelligent deduplication and ranking.
     */
    suspend fun searchMultiSource(
        query: String,
        enabledProviders: Set<SourceProvider>,
        filter: SourceFilter = SourceFilter.ALL
    ): List<Track> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val targetProviders = when (filter) {
            SourceFilter.ALL -> enabledProviders
            SourceFilter.LOCAL -> setOf(SourceProvider.LOCAL)
            SourceFilter.YOUTUBE -> setOf(SourceProvider.YOUTUBE_PIPED)
            SourceFilter.JAMENDO_AUDIUS -> setOf(SourceProvider.JAMENDO, SourceProvider.AUDIUS)
            SourceFilter.DEEZER_ITUNES -> setOf(SourceProvider.DEEZER, SourceProvider.ITUNES)
            SourceFilter.RADIO -> setOf(SourceProvider.RADIO_BROWSER)
            SourceFilter.ARCHIVE -> setOf(SourceProvider.INTERNET_ARCHIVE)
        }

        val collectedTracks = mutableListOf<Track>()

        coroutineScope {
            val deferredResults = networkSources
                .filter { it.provider in targetProviders }
                .map { source ->
                    async {
                        runCatching {
                            source.search(query, limit = 12)
                        }.getOrElse { e ->
                            SourceSearchResult(
                                provider = source.provider,
                                tracks = emptyList(),
                                latencyMs = 0L,
                                activeMirror = source.activeMirrorHost,
                                isOnline = false,
                                error = e.message
                            )
                        }
                    }
                }

            val localDeferred: kotlinx.coroutines.Deferred<List<Track>>? = if (SourceProvider.LOCAL in targetProviders) {
                async {
                    runCatching {
                        val qLower = query.lowercase()
                        scanLocalTracks().filter {
                            it.title.lowercase().contains(qLower) ||
                                it.artist.lowercase().contains(qLower) ||
                                it.album.lowercase().contains(qLower)
                        }
                    }.getOrDefault(emptyList())
                }
            } else {
                null
            }

            val remoteResults = deferredResults.awaitAll()
            remoteResults.forEach { res ->
                updateProviderHealth(
                    provider = res.provider,
                    isOnline = res.isOnline,
                    latencyMs = res.latencyMs,
                    activeMirror = res.activeMirror,
                    tracksCount = res.tracks.size,
                    error = res.error
                )
                collectedTracks.addAll(res.tracks)
            }

            localDeferred?.await()?.let { collectedTracks.addAll(it) }
        }

        val deduplicated = TrackDeduplicationEngine.deduplicateAndRank(collectedTracks, query)
        enrichWithRoomState(deduplicated)
    }

    /**
     * Fetches real trending & curated streams across active open providers in parallel.
     */
    suspend fun loadExploreSections(
        enabledProviders: Set<SourceProvider>
    ): ExploreFeedData = withContext(Dispatchers.IO) {
        coroutineScope {
            val jamendoDeferred = async {
                runCatching { jamendoSource.getTrending(10) }.getOrNull()
            }
            val audiusDeferred = async {
                runCatching { audiusSource.getTrending(10) }.getOrNull()
            }
            val deezerDeferred = async {
                runCatching { deezerSource.getTrending(10) }.getOrNull()
            }
            val itunesDeferred = async {
                runCatching { itunesSource.search("Synthwave Cyberpunk Electronic", 10) }.getOrNull()
            }
            val radioDeferred = async {
                runCatching { radioSource.search("Chill", 8) }.getOrNull()
            }

            val results = listOfNotNull(
                jamendoDeferred.await(),
                audiusDeferred.await(),
                deezerDeferred.await(),
                itunesDeferred.await(),
                radioDeferred.await()
            )

            results.forEach { res ->
                updateProviderHealth(
                    provider = res.provider,
                    isOnline = res.isOnline,
                    latencyMs = res.latencyMs,
                    activeMirror = res.activeMirror,
                    tracksCount = res.tracks.size,
                    error = res.error
                )
            }

            val fullLengthTracks = enrichWithRoomState(
                TrackDeduplicationEngine.deduplicateAndRank(
                    (jamendoDeferred.await()?.tracks.orEmpty()) +
                        (audiusDeferred.await()?.tracks.orEmpty())
                )
            )

            val globalCharts = enrichWithRoomState(
                TrackDeduplicationEngine.deduplicateAndRank(
                    (deezerDeferred.await()?.tracks.orEmpty()) +
                        (itunesDeferred.await()?.tracks.orEmpty())
                )
            )

            val liveRadio = radioDeferred.await()?.tracks.orEmpty()

            ExploreFeedData(
                trendingFullStreams = fullLengthTracks,
                globalChartTracks = globalCharts,
                liveRadioStations = liveRadio
            )
        }
    }

    suspend fun pingAllProviders(enabledProviders: Set<SourceProvider>) = withContext(Dispatchers.IO) {
        coroutineScope {
            val ytPing = async {
                runCatching { youtubeSource.selectFastestInstance() }.getOrNull()
            }
            val jobs = networkSources.map { src ->
                async {
                    val res = runCatching { src.search("Music", limit = 3) }.getOrNull()
                    if (res != null) {
                        updateProviderHealth(
                            provider = src.provider,
                            enabled = src.provider in enabledProviders,
                            isOnline = res.isOnline,
                            latencyMs = res.latencyMs,
                            activeMirror = res.activeMirror,
                            tracksCount = res.tracks.size,
                            error = res.error
                        )
                    }
                }
            }
            ytPing.await()
            jobs.awaitAll()
        }
    }

    suspend fun resolvePlayableUrl(provider: SourceProvider, rawUrl: String, track: Track): String =
        withContext(Dispatchers.IO) {
            if (isDirectPlayableScheme(rawUrl)) {
                return@withContext rawUrl
            }

            val candidateTrack = track.copy(primaryProvider = provider, streamUrl = rawUrl)
            val primaryResolved = when {
                rawUrl.startsWith("ytm://") || provider == SourceProvider.YOUTUBE_PIPED ->
                    youtubeSource.resolvePlayableStreamUrl(candidateTrack)
                rawUrl.startsWith("archive://") || provider == SourceProvider.INTERNET_ARCHIVE ->
                    archiveSource.resolvePlayableStreamUrl(candidateTrack)
                else -> rawUrl
            }

            if (primaryResolved != null && isDirectPlayableScheme(primaryResolved)) {
                return@withContext primaryResolved
            }

            // 1. Check any existing direct HTTP candidate in the track's fallbackStreams
            val existingDirect = track.allStreamCandidates()
                .map { it.streamUrl }
                .firstOrNull { isDirectPlayableScheme(it) }
            if (existingDirect != null) {
                return@withContext existingDirect
            }

            // 2. Live cross-provider resolution by artist + title (Deezer -> iTunes -> Jamendo)
            val searchQuery = "${track.artist} ${track.title}".trim()
            val deezerFallback = runCatching {
                deezerSource.search(searchQuery, limit = 3).tracks
                    .firstOrNull { isDirectPlayableScheme(it.streamUrl) }?.streamUrl
            }.getOrNull()
            if (deezerFallback != null) return@withContext deezerFallback

            val itunesFallback = runCatching {
                itunesSource.search(searchQuery, limit = 3).tracks
                    .firstOrNull { isDirectPlayableScheme(it.streamUrl) }?.streamUrl
            }.getOrNull()
            if (itunesFallback != null) return@withContext itunesFallback

            val jamendoFallback = runCatching {
                jamendoSource.search(track.title, limit = 3).tracks
                    .firstOrNull { isDirectPlayableScheme(it.streamUrl) }?.streamUrl
            }.getOrNull()
            if (jamendoFallback != null) return@withContext jamendoFallback

            ""
        }

    private fun isDirectPlayableScheme(url: String): Boolean {
        return url.startsWith("https://") ||
            url.startsWith("http://") ||
            url.startsWith("content://") ||
            url.startsWith("file://") ||
            url.startsWith("/")
    }

    suspend fun toggleFavorite(track: Track): Boolean = withContext(Dispatchers.IO) {
        val existing = dao.getTrackById(track.id)
        val newFav = !(existing?.isFavorite ?: track.isFavorite)
        if (existing != null) {
            dao.setFavorite(track.id, newFav)
        } else {
            dao.upsertTrack(
                TrackEntity.fromDomain(
                    track = track,
                    isFavorite = newFav
                )
            )
        }
        newFav
    }

    suspend fun recordPlaybackInHistory(track: Track) = withContext(Dispatchers.IO) {
        val existing = dao.getTrackById(track.id)
        val now = System.currentTimeMillis()
        if (existing != null) {
            dao.upsertTrack(
                existing.copy(
                    isInHistory = true,
                    lastPlayedAt = now,
                    playCount = existing.playCount + 1
                )
            )
        } else {
            dao.upsertTrack(
                TrackEntity.fromDomain(
                    track = track,
                    isInHistory = true,
                    lastPlayedAt = now,
                    playCount = 1
                )
            )
        }
    }

    /**
     * Downloads an audio track to local app storage for offline playback and indexes it in Room.
     */
    suspend fun downloadTrackForOffline(track: Track): Track = withContext(Dispatchers.IO) {
        if (track.isLiveStream) return@withContext track
        val playableUrl = resolvePlayableUrl(track.primaryProvider, track.streamUrl, track)
        if (!playableUrl.startsWith("http")) return@withContext track

        val offlineDir = File(context.filesDir, "offline_audio").apply { mkdirs() }
        val safeId = track.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val targetFile = File(offlineDir, "$safeId.mp3")

        val success = runCatching {
            val request = Request.Builder().url(playableUrl).get().build()
            HttpClientProvider.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use false
                val body = response.body ?: return@use false
                body.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                targetFile.exists() && targetFile.length() > 1024L
            }
        }.getOrDefault(false)

        if (success) {
            val localPath = targetFile.absolutePath
            val sizeBytes = targetFile.length()
            val existing = dao.getTrackById(track.id)
            if (existing != null) {
                dao.updateOfflineCacheState(
                    trackId = track.id,
                    cached = true,
                    filePath = localPath,
                    sizeBytes = sizeBytes
                )
            } else {
                dao.upsertTrack(
                    TrackEntity.fromDomain(
                        track = track,
                        isCachedOffline = true,
                        offlineFilePath = localPath,
                        offlineSizeBytes = sizeBytes
                    )
                )
            }
            track.copy(isCachedOffline = true, offlineFilePath = localPath)
        } else {
            track
        }
    }

    suspend fun clearAllOfflineDownloads() = withContext(Dispatchers.IO) {
        runCatching {
            val offlineDir = File(context.filesDir, "offline_audio")
            offlineDir.listFiles()?.forEach { it.delete() }
            dao.clearAllOfflineTrackRecords()
        }
    }

    suspend fun createPlaylist(name: String, description: String): Long =
        withContext(Dispatchers.IO) {
            dao.insertPlaylist(
                PlaylistEntity(
                    name = name.trim(),
                    description = description.trim()
                )
            )
        }

    suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        dao.deletePlaylistCrossRefs(playlistId)
        dao.deletePlaylist(playlistId)
    }

    suspend fun addTrackToPlaylist(playlistId: Long, track: Track) = withContext(Dispatchers.IO) {
        val existing = dao.getTrackById(track.id)
        if (existing == null) {
            dao.upsertTrack(TrackEntity.fromDomain(track))
        }
        dao.insertPlaylistTrackCrossRef(
            PlaylistTrackCrossRef(playlistId = playlistId, id = track.id)
        )
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String) =
        withContext(Dispatchers.IO) {
            dao.removeTrackFromPlaylist(playlistId, trackId)
        }

    suspend fun saveSearchQuery(query: String) = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.length >= 2) {
            dao.insertSearchQuery(SearchHistoryEntity(query = clean))
        }
    }

    suspend fun deleteSearchQuery(query: String) = withContext(Dispatchers.IO) {
        dao.deleteSearchQuery(query)
    }

    suspend fun clearSearchHistory() = withContext(Dispatchers.IO) {
        dao.clearSearchHistory()
    }

    private suspend fun enrichWithRoomState(tracks: List<Track>): List<Track> {
        return tracks.map { track ->
            val saved = runCatching { dao.getTrackById(track.id) }.getOrNull()
            if (saved != null) {
                track.copy(
                    isFavorite = saved.isFavorite,
                    isCachedOffline = saved.isCachedOffline && !saved.offlineFilePath.isNullOrBlank(),
                    offlineFilePath = saved.offlineFilePath
                )
            } else {
                track
            }
        }
    }

    private fun updateProviderHealth(
        provider: SourceProvider,
        enabled: Boolean? = null,
        isOnline: Boolean,
        latencyMs: Long,
        activeMirror: String,
        tracksCount: Int,
        error: String?
    ) {
        val now = System.currentTimeMillis()
        _providerStatuses.value = _providerStatuses.value.map { current ->
            if (current.provider == provider) {
                current.copy(
                    enabled = enabled ?: current.enabled,
                    isOnline = isOnline,
                    latencyMs = latencyMs,
                    activeMirror = activeMirror,
                    lastCheckedMs = now,
                    tracksReturnedLastQuery = tracksCount,
                    errorMessage = error
                )
            } else {
                current
            }
        }
    }
}

data class ExploreFeedData(
    val trendingFullStreams: List<Track> = emptyList(),
    val globalChartTracks: List<Track> = emptyList(),
    val liveRadioStations: List<Track> = emptyList()
)
