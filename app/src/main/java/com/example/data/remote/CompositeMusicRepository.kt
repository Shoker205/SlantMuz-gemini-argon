package com.example.data.remote

import android.content.Context
import com.example.data.local.LocalMediaScanner
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistTrackCrossRef
import com.example.data.local.PlaylistWithTracks
import com.example.data.local.SearchHistoryEntity
import com.example.data.local.SlantMuzDao
import com.example.data.local.TrackEntity
import com.example.domain.ArtistSuggestion
import com.example.domain.ProviderHealthStatus
import com.example.domain.SlantWaveEngine
import com.example.domain.SourceFilter
import com.example.domain.SourceProvider
import com.example.domain.Track
import com.example.domain.TrackDeduplicationEngine
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
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
import kotlinx.coroutines.withTimeoutOrNull
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

    /**
     * In-memory cache of resolved full-length playable stream URLs so prefetched or previously
     * played tracks start in 0ms without any network delay on track switch.
     */
    private val resolvedStreamCache = ConcurrentHashMap<String, String>()

    /**
     * In-memory cache of artist cover URLs (keyed by lowercase artist name).
     */
    private val artistArtworkCache = ConcurrentHashMap<String, String>()

    /**
     * In-memory cache of collaborative-filtering related artists per artist name.
     */
    private val relatedArtistsCache = ConcurrentHashMap<String, List<String>>()

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
     * with deep limits (25 per provider) followed by intelligent deduplication and ranking.
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
                            source.search(query, limit = 24)
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
     * Queries active providers for a specific artist using dedicated deep artist catalog endpoints
     * (`searchArtistCatalog` with limit = 55) so 25–60+ tracks of the artist are discovered,
     * while keeping each provider's tracks strictly isolated (no cross-source mixing).
     */
    suspend fun fetchArtistCatalogByProvider(
        artistName: String,
        enabledProviders: Set<SourceProvider>,
        existingKnownTracks: List<Track> = emptyList()
    ): Map<SourceProvider, List<Track>> = withContext(Dispatchers.IO) {
        val cleanName = artistName.trim()
        if (cleanName.isBlank()) return@withContext emptyMap()

        val rawTracks = mutableListOf<Track>()
        rawTracks.addAll(existingKnownTracks)

        coroutineScope {
            val activeNetwork = networkSources.filter {
                it.provider in enabledProviders && it.provider != SourceProvider.RADIO_BROWSER
            }
            val deferred = activeNetwork.map { source ->
                async {
                    runCatching {
                        source.searchArtistCatalog(cleanName, limit = 55)
                    }.getOrNull()
                }
            }

            val localDeferred: kotlinx.coroutines.Deferred<List<Track>>? = if (SourceProvider.LOCAL in enabledProviders) {
                async {
                    runCatching {
                        scanLocalTracks().filter { TrackDeduplicationEngine.isTrackByArtist(it, cleanName) }
                    }.getOrDefault(emptyList())
                }
            } else {
                null
            }

            deferred.awaitAll().filterNotNull().forEach { res ->
                updateProviderHealth(
                    provider = res.provider,
                    isOnline = res.isOnline,
                    latencyMs = res.latencyMs,
                    activeMirror = res.activeMirror,
                    tracksCount = res.tracks.size,
                    error = res.error
                )
                rawTracks.addAll(res.tracks)
            }

            localDeferred?.await()?.let { rawTracks.addAll(it) }
        }

        val enriched = enrichWithRoomState(rawTracks)
        TrackDeduplicationEngine.groupArtistTracksByProvider(enriched, cleanName)
    }

    /**
     * Live autocomplete artist search with cover art (`artworkUrl`) for `TasteOnboardingSheet`.
     */
    suspend fun searchArtistSuggestions(query: String, limit: Int = 8): List<ArtistSuggestion> =
        withContext(Dispatchers.IO) {
            val clean = query.trim()
            if (clean.length < 2) return@withContext emptyList()

            coroutineScope {
                val deezerDeferred = async {
                    runCatching { deezerSource.searchArtists(clean, limit = limit) }.getOrDefault(emptyList())
                }
                val itunesDeferred = async {
                    runCatching { itunesSource.searchArtists(clean, limit = 6) }.getOrDefault(emptyList())
                }

                val deezerList = deezerDeferred.await()
                val itunesList = itunesDeferred.await()

                val merged = LinkedHashMap<String, ArtistSuggestion>()
                for (item in (deezerList + itunesList)) {
                    val norm = TrackDeduplicationEngine.normalizeText(item.name)
                    if (norm.isBlank()) continue
                    if (item.artworkUrl.isNotBlank()) {
                        artistArtworkCache[item.name.lowercase()] = item.artworkUrl
                        artistArtworkCache[norm] = item.artworkUrl
                    }
                    val existing = merged[norm]
                    if (existing == null) {
                        merged[norm] = item
                    } else if (existing.artworkUrl.isBlank() && item.artworkUrl.isNotBlank()) {
                        merged[norm] = existing.copy(artworkUrl = item.artworkUrl)
                    }
                }
                merged.values.take(limit).toList()
            }
        }

    /**
     * Resolves and caches cover art URLs for a list of artists (e.g., starter catalog artists and selected artists)
     * so every artist chip/card in Preferences shows its real cover icon.
     */
    suspend fun resolveArtistArtworksBatch(
        artistNames: Collection<String>,
        existingMap: Map<String, String> = emptyMap()
    ): Map<String, String> = withContext(Dispatchers.IO) {
        existingMap.forEach { (k, v) ->
            if (v.isNotBlank()) {
                artistArtworkCache[k.lowercase()] = v
                artistArtworkCache[TrackDeduplicationEngine.normalizeText(k)] = v
            }
        }

        val missing = artistNames
            .map { it.trim() }
            .filter { name ->
                name.isNotBlank() &&
                    artistArtworkCache[name.lowercase()].isNullOrBlank() &&
                    artistArtworkCache[TrackDeduplicationEngine.normalizeText(name)].isNullOrBlank()
            }
            .distinctBy { it.lowercase() }
            .take(16)

        if (missing.isNotEmpty()) {
            coroutineScope {
                missing.chunked(6).forEach { batch ->
                    batch.map { artistName ->
                        async {
                            val suggestion = runCatching {
                                deezerSource.searchArtists(artistName, limit = 1).firstOrNull()
                                    ?: itunesSource.searchArtists(artistName, limit = 1).firstOrNull()
                            }.getOrNull()
                            val artUrl = suggestion?.artworkUrl.orEmpty()
                            if (artUrl.isNotBlank()) {
                                artistArtworkCache[artistName.lowercase()] = artUrl
                                artistArtworkCache[TrackDeduplicationEngine.normalizeText(artistName)] = artUrl
                            }
                        }
                    }.awaitAll()
                }
            }
        }

        val result = HashMap<String, String>(existingMap)
        artistNames.forEach { name ->
            val found = artistArtworkCache[name.lowercase()]
                ?: artistArtworkCache[TrackDeduplicationEngine.normalizeText(name)]
            if (!found.isNullOrBlank()) {
                result[name] = found
            }
        }
        result
    }

    /**
     * Resolves collaborative-filtering similar artists for the user's top artists (combining
     * `SlantWaveEngine.getRelatedArtistsFor` + live Deezer `/artist/{id}/related`).
     */
    suspend fun resolveRelatedArtistsForNames(artistNames: List<String>): List<String> =
        withContext(Dispatchers.IO) {
            val related = mutableListOf<String>()
            // 1. Instant local similarity graph
            artistNames.forEach { name ->
                related.addAll(SlantWaveEngine.getRelatedArtistsFor(name))
            }
            // 2. Live Deezer collaborative filtering for up to 3 top user artists
            coroutineScope {
                artistNames.take(3).map { name ->
                    async {
                        val key = name.lowercase().trim()
                        val cached = relatedArtistsCache[key]
                        if (cached != null) {
                            cached
                        } else {
                            val fetched = runCatching {
                                deezerSource.fetchRelatedArtists(name, limit = 5).map { it.name }
                            }.getOrDefault(emptyList())
                            if (fetched.isNotEmpty()) {
                                relatedArtistsCache[key] = fetched
                            }
                            fetched
                        }
                    }
                }.awaitAll().forEach { related.addAll(it) }
            }
            val inputNorm = artistNames.map { TrackDeduplicationEngine.normalizeText(it) }.toSet()
            related
                .map { it.trim() }
                .filter { it.isNotBlank() && TrackDeduplicationEngine.normalizeText(it) !in inputNorm }
                .distinctBy { TrackDeduplicationEngine.normalizeText(it) }
                .take(10)
        }

    /**
     * Parallel multi-query harvester for the SlantWave recommendation engine.
     */
    suspend fun fetchWavePool(
        queries: List<String>,
        enabledProviders: Set<SourceProvider>
    ): List<Track> = withContext(Dispatchers.IO) {
        if (queries.isEmpty()) return@withContext emptyList()
        val collected = mutableListOf<Track>()
        val targetSources = networkSources.filter {
            it.provider in enabledProviders && it.provider != SourceProvider.RADIO_BROWSER
        }

        coroutineScope {
            val jobs = queries.take(6).flatMap { q ->
                targetSources.map { src ->
                    async {
                        runCatching { src.search(q, limit = 14).tracks }.getOrDefault(emptyList())
                    }
                }
            }
            jobs.awaitAll().forEach { collected.addAll(it) }
        }

        enrichWithRoomState(collected)
    }

    /**
     * Fetches and ranks personalized feeds & curated streams across active open providers in parallel,
     * driven by the user's pre-configured Taste DNA, liked tracks, playlists, albums, and history.
     */
    suspend fun loadExploreSections(
        enabledProviders: Set<SourceProvider>,
        tasteProfile: com.example.domain.UserTasteProfile = com.example.domain.UserTasteProfile(),
        favoriteTracks: List<Track> = emptyList(),
        playlistTracks: List<Track> = emptyList(),
        historyTracks: List<Track> = emptyList(),
        likedArtists: Set<String> = emptySet(),
        dislikedTrackIds: Set<String> = emptySet()
    ): ExploreFeedData = withContext(Dispatchers.IO) {
        val dnaSummary = SlantWaveEngine.computeTasteDnaSummary(
            tasteProfile = tasteProfile,
            favoriteTracks = favoriteTracks,
            playlistTracks = playlistTracks,
            historyTracks = historyTracks,
            likedArtists = likedArtists
        )

        val relatedArtists = resolveRelatedArtistsForNames(dnaSummary.topArtists.take(4))

        val queryPlan = SlantWaveEngine.buildPersonalizedExploreQueries(
            tasteProfile = tasteProfile,
            dnaSummary = dnaSummary,
            relatedArtists = relatedArtists
        )

        val primaryGenreQuery = queryPlan.genreRadarQueries.firstOrNull() ?: "Synthwave Electronic"
        val primaryArtistQuery = queryPlan.favoriteArtistsQueries.firstOrNull() ?: primaryGenreQuery

        coroutineScope {
            val forYouPoolDeferred = async {
                runCatching {
                    fetchWavePool(queryPlan.forYouQueries.take(5), enabledProviders)
                }.getOrDefault(emptyList())
            }
            val artistPoolDeferred = async {
                runCatching {
                    fetchWavePool(queryPlan.favoriteArtistsQueries.take(4), enabledProviders)
                }.getOrDefault(emptyList())
            }
            val genrePoolDeferred = async {
                runCatching {
                    fetchWavePool(queryPlan.genreRadarQueries.take(4), enabledProviders)
                }.getOrDefault(emptyList())
            }

            val jamendoDeferred = async {
                runCatching {
                    if (tasteProfile.hasAnyExplicitPreferences || favoriteTracks.isNotEmpty() || playlistTracks.isNotEmpty()) {
                        jamendoSource.search(primaryGenreQuery, 14)
                    } else {
                        jamendoSource.getTrending(14)
                    }
                }.getOrNull()
            }
            val audiusDeferred = async {
                runCatching {
                    if (tasteProfile.hasAnyExplicitPreferences || favoriteTracks.isNotEmpty() || playlistTracks.isNotEmpty()) {
                        audiusSource.search(primaryGenreQuery, 14)
                    } else {
                        audiusSource.getTrending(14)
                    }
                }.getOrNull()
            }
            val deezerDeferred = async {
                runCatching {
                    if (tasteProfile.hasAnyExplicitPreferences || favoriteTracks.isNotEmpty()) {
                        deezerSource.search(primaryArtistQuery, 16)
                    } else {
                        deezerSource.getTrending(16)
                    }
                }.getOrNull()
            }
            val itunesDeferred = async {
                runCatching { itunesSource.search(primaryGenreQuery, 14) }.getOrNull()
            }
            val radioDeferred = async {
                runCatching { radioSource.search(queryPlan.radioQuery, 8) }.getOrNull()
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

            val forYouRaw = forYouPoolDeferred.await()
            val artistRaw = artistPoolDeferred.await()
            val genreRaw = genrePoolDeferred.await()

            val fullLengthRaw = enrichWithRoomState(
                TrackDeduplicationEngine.deduplicateAndRank(
                    (jamendoDeferred.await()?.tracks.orEmpty()) +
                        (audiusDeferred.await()?.tracks.orEmpty()) +
                        forYouRaw.filter { !it.isPreviewOnly && it.durationSec >= 45 }
                )
            )

            val globalChartsRaw = enrichWithRoomState(
                TrackDeduplicationEngine.deduplicateAndRank(
                    (deezerDeferred.await()?.tracks.orEmpty()) +
                        (itunesDeferred.await()?.tracks.orEmpty())
                )
            )

            val personalForYouRanked = SlantWaveEngine.rankAndFilterWaveCandidates(
                rawCandidates = forYouRaw + artistRaw + genreRaw + favoriteTracks + playlistTracks + fullLengthRaw,
                settings = com.example.domain.WaveSettings(),
                tasteProfile = tasteProfile,
                favoriteTracks = favoriteTracks,
                playlistTracks = playlistTracks,
                historyTracks = historyTracks,
                likedArtists = likedArtists,
                dislikedTrackIds = dislikedTrackIds,
                relatedArtists = relatedArtists.toSet()
            ).take(16)

            val favoriteArtistsRanked = SlantWaveEngine.rankAndFilterWaveCandidates(
                rawCandidates = artistRaw + favoriteTracks + playlistTracks,
                settings = com.example.domain.WaveSettings(character = com.example.domain.WaveCharacter.FAVORITE),
                tasteProfile = tasteProfile,
                favoriteTracks = favoriteTracks,
                playlistTracks = playlistTracks,
                historyTracks = historyTracks,
                likedArtists = likedArtists,
                dislikedTrackIds = dislikedTrackIds,
                relatedArtists = relatedArtists.toSet()
            ).take(14)

            val genreRadarRanked = SlantWaveEngine.rankAndFilterWaveCandidates(
                rawCandidates = genreRaw + forYouRaw + fullLengthRaw,
                settings = com.example.domain.WaveSettings(character = com.example.domain.WaveCharacter.UNFAMILIAR),
                tasteProfile = tasteProfile,
                favoriteTracks = favoriteTracks,
                playlistTracks = playlistTracks,
                historyTracks = historyTracks,
                likedArtists = likedArtists,
                dislikedTrackIds = dislikedTrackIds,
                relatedArtists = relatedArtists.toSet()
            ).take(14)

            val fullLengthRanked = SlantWaveEngine.rankAndFilterWaveCandidates(
                rawCandidates = fullLengthRaw,
                settings = com.example.domain.WaveSettings(),
                tasteProfile = tasteProfile.copy(preferFullLengthTracks = true),
                favoriteTracks = favoriteTracks,
                playlistTracks = playlistTracks,
                historyTracks = historyTracks,
                likedArtists = likedArtists,
                dislikedTrackIds = dislikedTrackIds,
                relatedArtists = relatedArtists.toSet()
            )

            val globalChartsRanked = SlantWaveEngine.rankAndFilterWaveCandidates(
                rawCandidates = globalChartsRaw,
                settings = com.example.domain.WaveSettings(character = com.example.domain.WaveCharacter.POPULAR),
                tasteProfile = tasteProfile,
                favoriteTracks = favoriteTracks,
                playlistTracks = playlistTracks,
                historyTracks = historyTracks,
                likedArtists = likedArtists,
                dislikedTrackIds = dislikedTrackIds,
                relatedArtists = relatedArtists.toSet()
            )

            val liveRadio = radioDeferred.await()?.tracks.orEmpty()

            // Pre-resolve the first 2 personal tracks in the background so initial playback is instant
            personalForYouRanked.take(2).forEach { track ->
                async { runCatching { prefetchTrackStream(track) } }
            }

            ExploreFeedData(
                personalForYouTracks = personalForYouRanked,
                favoriteArtistsTracks = favoriteArtistsRanked,
                genreRadarTracks = genreRadarRanked,
                trendingFullStreams = fullLengthRanked,
                globalChartTracks = globalChartsRanked,
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

    /**
     * Background pre-resolver for upcoming queue tracks so switching tracks is instantaneous (0ms).
     */
    suspend fun prefetchTrackStream(track: Track): String = withContext(Dispatchers.IO) {
        if (track.isLiveStream) return@withContext track.streamUrl
        val cached = resolvedStreamCache[track.id]
        if (!cached.isNullOrBlank() && isDirectPlayableScheme(cached)) {
            return@withContext cached
        }
        val primaryCandidate = track.allStreamCandidates().firstOrNull { !it.isPreview }
            ?: track.allStreamCandidates().firstOrNull()
        val rawUrl = primaryCandidate?.streamUrl ?: track.streamUrl
        val provider = primaryCandidate?.provider ?: track.primaryProvider
        resolvePlayableUrl(provider, rawUrl, track)
    }

    /**
     * Resolves a 100% full-length playable audio URL with:
     * 1. 0ms In-Memory Cache hit (`resolvedStreamCache`)
     * 2. Multi-Engine Full-Length Audio Resolution (`FullLengthAudioResolver`: JioSaavn 320kbps +
     *    Hearthis.at Full MP3 + Audius 320kbps + Jamendo + Internet Archive + Piped/Invidious)
     * 3. Strict 0% tolerance for 30-second chorus snippets (`isPreviewSnippetUrl` is categorically blocked).
     */
    suspend fun resolvePlayableUrl(provider: SourceProvider, rawUrl: String, track: Track): String =
        withContext(Dispatchers.IO) {
            // 0. Local offline file check first
            if (track.isCachedOffline && !track.offlineFilePath.isNullOrBlank()) {
                val file = File(track.offlineFilePath)
                if (file.exists() && file.length() > 1024L) {
                    return@withContext file.absolutePath
                }
            }

            // 1. Check in-memory resolved stream cache first (0ms instant switch!)
            resolvedStreamCache[rawUrl]?.takeIf { isDirectPlayableScheme(it) }?.let {
                return@withContext it
            }
            resolvedStreamCache[track.id]?.takeIf { isDirectPlayableScheme(it) }?.let {
                return@withContext it
            }

            if (isDirectPlayableScheme(rawUrl)) {
                resolvedStreamCache[track.id] = rawUrl
                return@withContext rawUrl
            }

            // 2. Handle `fulltrack://artist|title|duration` OR upgrade any legacy snippet URL
            if (rawUrl.startsWith("fulltrack://") || com.example.domain.isPreviewSnippetUrl(rawUrl)) {
                val payload = rawUrl.removePrefix("fulltrack://")
                val parts = payload.split("|")
                val artist = parts.getOrNull(0)?.takeIf { it.isNotBlank() && !rawUrl.startsWith("http") } ?: track.artist
                val title = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: track.title
                val expectedDur = parts.getOrNull(2)?.toIntOrNull() ?: track.durationSec.coerceAtLeast(180)

                val resolvedCandidate = FullLengthAudioResolver.resolveFullLengthStream(
                    artist = artist,
                    title = title,
                    expectedDurationSec = expectedDur
                )
                val fullStream = resolvedCandidate?.streamUrl
                if (!fullStream.isNullOrBlank() && isDirectPlayableScheme(fullStream)) {
                    resolvedStreamCache[rawUrl] = fullStream
                    resolvedStreamCache[track.id] = fullStream
                    return@withContext fullStream
                }
            }

            // 3. Handle `ytm://` or `archive://` pseudo-URLs
            val candidateTrack = track.copy(primaryProvider = provider, streamUrl = rawUrl)
            val primaryResolved = when {
                rawUrl.startsWith("ytm://") || (provider == SourceProvider.YOUTUBE_PIPED && !rawUrl.startsWith("fulltrack://")) ->
                    youtubeSource.resolvePlayableStreamUrl(candidateTrack)
                rawUrl.startsWith("archive://") || provider == SourceProvider.INTERNET_ARCHIVE ->
                    archiveSource.resolvePlayableStreamUrl(candidateTrack)
                else -> null
            }

            if (primaryResolved != null && isDirectPlayableScheme(primaryResolved)) {
                resolvedStreamCache[rawUrl] = primaryResolved
                resolvedStreamCache[track.id] = primaryResolved
                return@withContext primaryResolved
            }

            // 4. Check any non-preview direct HTTP candidate in the track's fallbackStreams
            val existingDirect = track.allStreamCandidates()
                .filter { !it.isPreview }
                .map { it.streamUrl }
                .firstOrNull { isDirectPlayableScheme(it) }
            if (existingDirect != null) {
                resolvedStreamCache[track.id] = existingDirect
                return@withContext existingDirect
            }

            // 5. Final full-length cross-provider resolution (JioSaavn 320kbps + Hearthis + Audius + Jamendo)
            val finalCandidate = FullLengthAudioResolver.resolveFullLengthStream(
                artist = track.artist,
                title = track.title,
                expectedDurationSec = track.durationSec.coerceAtLeast(180)
            )
            if (finalCandidate != null && isDirectPlayableScheme(finalCandidate.streamUrl)) {
                resolvedStreamCache[track.id] = finalCandidate.streamUrl
                return@withContext finalCandidate.streamUrl
            }

            ""
        }

    private fun isDirectPlayableScheme(url: String): Boolean {
        if (url.isBlank() || com.example.domain.isPreviewSnippetUrl(url)) return false
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
                    track = sanitizeTrackForFullLength(track),
                    isFavorite = newFav
                )
            )
        }
        newFav
    }

    suspend fun recordPlaybackInHistory(track: Track) = withContext(Dispatchers.IO) {
        val sanitized = sanitizeTrackForFullLength(track)
        val existing = dao.getTrackById(sanitized.id)
        val now = System.currentTimeMillis()
        if (existing != null) {
            dao.upsertTrack(
                existing.copy(
                    streamUrl = sanitized.streamUrl,
                    isInHistory = true,
                    lastPlayedAt = now,
                    playCount = existing.playCount + 1
                )
            )
        } else {
            dao.upsertTrack(
                TrackEntity.fromDomain(
                    track = sanitized,
                    isInHistory = true,
                    lastPlayedAt = now,
                    playCount = 1
                )
            )
        }
    }

    /**
     * Merges cloud-synced favorite tracks from Firebase Firestore into local Room storage.
     */
    suspend fun mergeCloudFavoriteTracks(cloudTracks: List<Track>) = withContext(Dispatchers.IO) {
        if (cloudTracks.isEmpty()) return@withContext
        val ids = cloudTracks.map { it.id }
        val existingMap = dao.getTracksByIds(ids).associateBy { it.id }
        cloudTracks.forEach { rawTrack ->
            val track = sanitizeTrackForFullLength(rawTrack)
            val existing = existingMap[track.id]
            if (existing != null) {
                if (!existing.isFavorite) {
                    dao.setFavorite(track.id, true)
                }
            } else {
                dao.upsertTrack(
                    TrackEntity.fromDomain(
                        track = track,
                        isFavorite = true
                    )
                )
            }
        }
    }

    /**
     * Ensures no legacy 30s preview URL is ever persisted as a primary streamUrl.
     */
    private fun sanitizeTrackForFullLength(track: Track): Track {
        return if (com.example.domain.isPreviewSnippetUrl(track.streamUrl)) {
            val fullUri = "fulltrack://${track.artist.trim()}|${track.title.trim()}|${track.durationSec.coerceAtLeast(180)}"
            track.copy(
                streamUrl = fullUri,
                isPreviewOnly = false,
                durationSec = track.durationSec.coerceAtLeast(180),
                fallbackStreams = track.fallbackStreams.filter { !it.isPreview && !com.example.domain.isPreviewSnippetUrl(it.streamUrl) }
            )
        } else {
            track
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
                        track = sanitizeTrackForFullLength(track),
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
        val sanitized = sanitizeTrackForFullLength(track)
        val existing = dao.getTrackById(sanitized.id)
        if (existing == null) {
            dao.upsertTrack(TrackEntity.fromDomain(sanitized))
        }
        dao.insertPlaylistTrackCrossRef(
            PlaylistTrackCrossRef(playlistId = playlistId, id = sanitized.id)
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

    /**
     * Batch-enriches tracks with Room favorite/offline state in a single SQLite query per chunk
     * instead of N+1 individual queries, eliminating UI and background thread contention.
     */
    private suspend fun enrichWithRoomState(tracks: List<Track>): List<Track> {
        if (tracks.isEmpty()) return emptyList()
        val savedMap = runCatching {
            val map = HashMap<String, TrackEntity>(tracks.size)
            tracks.map { it.id }.distinct().chunked(200).forEach { chunk ->
                dao.getTracksByIds(chunk).forEach { entity ->
                    map[entity.id] = entity
                }
            }
            map
        }.getOrDefault(emptyMap())

        return tracks.map { raw ->
            val track = sanitizeTrackForFullLength(raw)
            val saved = savedMap[track.id]
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
    val personalForYouTracks: List<Track> = emptyList(),
    val favoriteArtistsTracks: List<Track> = emptyList(),
    val genreRadarTracks: List<Track> = emptyList(),
    val trendingFullStreams: List<Track> = emptyList(),
    val globalChartTracks: List<Track> = emptyList(),
    val liveRadioStations: List<Track> = emptyList()
)
