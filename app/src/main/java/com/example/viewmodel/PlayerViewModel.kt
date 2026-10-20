package com.example.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.LocalMediaScanner
import com.example.data.local.PlaylistWithTracks
import com.example.data.local.SlantMuzDatabase
import com.example.data.preferences.AppUserSettings
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.remote.CompositeMusicRepository
import com.example.data.remote.ExploreFeedData
import com.example.data.remote.FirebaseRemoteConfigManager
import com.example.data.remote.LyricsRepository
import com.example.domain.AlbumSummary
import com.example.domain.ArtistBioInfo
import com.example.domain.ArtistProfile
import com.example.domain.ArtistSuggestion
import com.example.domain.DspState
import com.example.domain.EqualizerPreset
import com.example.domain.LrcParser
import com.example.domain.ProviderHealthStatus
import com.example.domain.RemoteStreamingConfig
import com.example.domain.SearchSortMode
import com.example.domain.SlantWaveEngine
import com.example.domain.SmartPlaylistPreset
import com.example.domain.SourceFilter
import com.example.domain.SourceProvider
import com.example.domain.StreamCandidate
import com.example.domain.SyncedLyrics
import com.example.domain.Track
import com.example.domain.TrackDeduplicationEngine
import com.example.domain.UserTasteDnaSummary
import com.example.domain.UserTasteProfile
import com.example.domain.WaveActivity
import com.example.domain.WaveCharacter
import com.example.domain.WaveLanguage
import com.example.domain.WaveMood
import com.example.domain.WaveSettings
import com.example.player.ExoPlayerCacheProvider
import com.example.player.PlaybackUiState
import com.example.player.PlayerController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val appContext: Context,
    private val musicRepository: CompositeMusicRepository,
    private val lyricsRepository: LyricsRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val remoteConfigManager: FirebaseRemoteConfigManager,
    private val playerController: PlayerController
) : ViewModel() {

    val playbackState: StateFlow<PlaybackUiState> = playerController.playbackState
    val dspState: StateFlow<DspState> = playerController.audioEffectsManager.dspState
    val providerStatuses: StateFlow<List<ProviderHealthStatus>> = musicRepository.providerStatuses
    val remoteConfig: StateFlow<RemoteStreamingConfig> = remoteConfigManager.configFlow

    val userSettings: StateFlow<AppUserSettings> = preferencesRepository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppUserSettings()
        )

    val favoriteTracks: StateFlow<List<Track>> = musicRepository.favoriteTracksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val historyTracks: StateFlow<List<Track>> = musicRepository.historyTracksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val offlineCachedTracks: StateFlow<List<Track>> = musicRepository.offlineCachedTracksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalOfflineBytes: StateFlow<Long> = musicRepository.totalOfflineBytesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val playlists: StateFlow<List<PlaylistWithTracks>> = musicRepository.playlistsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchHistory: StateFlow<List<String>> = musicRepository.searchHistoryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _exploreFeed = MutableStateFlow(ExploreFeedData())
    val exploreFeed: StateFlow<ExploreFeedData> = _exploreFeed.asStateFlow()

    private val _isExploreLoading = MutableStateFlow(false)
    val isExploreLoading: StateFlow<Boolean> = _isExploreLoading.asStateFlow()

    private val _localTracks = MutableStateFlow<List<Track>>(emptyList())
    val localTracks: StateFlow<List<Track>> = _localTracks.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedSourceFilter = MutableStateFlow(SourceFilter.ALL)
    val selectedSourceFilter: StateFlow<SourceFilter> = _selectedSourceFilter.asStateFlow()

    private val _searchSortMode = MutableStateFlow(SearchSortMode.RELEVANCE)
    val searchSortMode: StateFlow<SearchSortMode> = _searchSortMode.asStateFlow()

    private val _rawSearchResults = MutableStateFlow<List<Track>>(emptyList())
    private val _searchResults = MutableStateFlow<List<Track>>(emptyList())
    val searchResults: StateFlow<List<Track>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _currentLyrics = MutableStateFlow<SyncedLyrics?>(null)
    val currentLyrics: StateFlow<SyncedLyrics?> = _currentLyrics.asStateFlow()

    private val _isLyricsLoading = MutableStateFlow(false)
    val isLyricsLoading: StateFlow<Boolean> = _isLyricsLoading.asStateFlow()

    private val _artistBio = MutableStateFlow<ArtistBioInfo?>(null)
    val artistBio: StateFlow<ArtistBioInfo?> = _artistBio.asStateFlow()

    private val _downloadingTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingTrackIds: StateFlow<Set<String>> = _downloadingTrackIds.asStateFlow()

    private val _exoCacheUsedBytes = MutableStateFlow(0L)
    val exoCacheUsedBytes: StateFlow<Long> = _exoCacheUsedBytes.asStateFlow()

    private val _waveSettings = MutableStateFlow(WaveSettings())
    val waveSettings: StateFlow<WaveSettings> = _waveSettings.asStateFlow()

    private val _isWaveGenerating = MutableStateFlow(false)
    val isWaveGenerating: StateFlow<Boolean> = _isWaveGenerating.asStateFlow()

    private val _waveQueueTracks = MutableStateFlow<List<Track>>(emptyList())
    val waveQueueTracks: StateFlow<List<Track>> = _waveQueueTracks.asStateFlow()

    private val _dislikedTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val dislikedTrackIds: StateFlow<Set<String>> = _dislikedTrackIds.asStateFlow()

    private val _likedArtistNames = MutableStateFlow<Set<String>>(emptySet())
    val likedArtistNames: StateFlow<Set<String>> = _likedArtistNames.asStateFlow()

    private val _selectedArtistProfile = MutableStateFlow<ArtistProfile?>(null)
    val selectedArtistProfile: StateFlow<ArtistProfile?> = _selectedArtistProfile.asStateFlow()

    private val _artistAutocompleteSuggestions = MutableStateFlow<List<ArtistSuggestion>>(emptyList())
    val artistAutocompleteSuggestions: StateFlow<List<ArtistSuggestion>> = _artistAutocompleteSuggestions.asStateFlow()

    private val _isArtistSuggestLoading = MutableStateFlow(false)
    val isArtistSuggestLoading: StateFlow<Boolean> = _isArtistSuggestLoading.asStateFlow()

    private val _artistArtworksMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val artistArtworksMap: StateFlow<Map<String, String>> = _artistArtworksMap.asStateFlow()

    /**
     * Live reactive Musical DNA summary computed from pre-configured preferences,
     * liked tracks, user playlists, liked artists, liked albums, and listening history.
     */
    val tasteDnaSummary: StateFlow<UserTasteDnaSummary> = combine(
        userSettings,
        favoriteTracks,
        playlists,
        historyTracks,
        _likedArtistNames
    ) { settings, favs, plList, hist, likedArtists ->
        val plTracks = plList.flatMap { pw -> pw.tracks.map { it.toDomain() } }
        SlantWaveEngine.computeTasteDnaSummary(
            tasteProfile = settings.tasteProfile,
            favoriteTracks = favs,
            playlistTracks = plTracks,
            historyTracks = hist,
            likedArtists = likedArtists + settings.tasteProfile.selectedArtists
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserTasteDnaSummary()
    )

    private var searchDebounceJob: Job? = null
    private var artistSuggestDebounceJob: Job? = null
    private var waveGenerationJob: Job? = null

    init {
        playerController.attachResolvers(
            resolver = { provider, rawUrl, track ->
                musicRepository.resolvePlayableUrl(provider, rawUrl, track)
            },
            prefetcher = { track ->
                musicRepository.prefetchTrackStream(track)
            },
            onTrackPlayed = { track ->
                musicRepository.recordPlaybackInHistory(track)
                loadLyricsAndBioForTrack(track)
                refreshExoCacheSize()
            }
        )

        viewModelScope.launch {
            preferencesRepository.settingsFlow.collect { settings ->
                playerController.audioEffectsManager.applyDspState(settings.dspState)
                _likedArtistNames.value = settings.tasteProfile.selectedArtists
                _dislikedTrackIds.value = settings.dislikedTrackIds
                if (settings.tasteProfile.artistArtworks.isNotEmpty()) {
                    _artistArtworksMap.value = _artistArtworksMap.value + settings.tasteProfile.artistArtworks
                }
            }
        }

        refreshExploreFeed()
        scanLocalDeviceAudio()
        refreshExoCacheSize()
        preloadArtistArtworksInBackground()
    }

    private fun currentPlaylistDomainTracks(): List<Track> {
        return playlists.value.flatMap { pw -> pw.tracks.map { it.toDomain() } }
    }

    fun refreshExploreFeed() {
        viewModelScope.launch {
            _isExploreLoading.value = true
            val settings = userSettings.value
            val plTracks = currentPlaylistDomainTracks()
            val feed = musicRepository.loadExploreSections(
                enabledProviders = settings.enabledProviders,
                tasteProfile = settings.tasteProfile,
                favoriteTracks = favoriteTracks.value,
                playlistTracks = plTracks,
                historyTracks = historyTracks.value,
                likedArtists = _likedArtistNames.value + settings.tasteProfile.selectedArtists,
                dislikedTrackIds = _dislikedTrackIds.value
            )
            _exploreFeed.value = feed
            _isExploreLoading.value = false

            if (playbackState.value.currentTrack == null) {
                val initialQueue = feed.personalForYouTracks
                    .ifEmpty { feed.trendingFullStreams }
                    .ifEmpty { feed.globalChartTracks }
                val first = initialQueue.firstOrNull()
                if (first != null) {
                    loadLyricsAndBioForTrack(first)
                }
            }
        }
    }

    // --- PRE-CONFIGURATION & USER TASTE DNA MANAGEMENT ---

    fun saveUserTasteProfile(profile: UserTasteProfile, launchWaveImmediately: Boolean = false) {
        viewModelScope.launch {
            val mergedArtworks = _artistArtworksMap.value + profile.artistArtworks
            val enrichedProfile = profile.copy(
                isOnboardingCompleted = true,
                artistArtworks = mergedArtworks
            )
            preferencesRepository.saveTasteProfile(enrichedProfile)
            _likedArtistNames.value = enrichedProfile.selectedArtists
            _artistArtworksMap.value = mergedArtworks
            refreshExploreFeed()
            if (launchWaveImmediately) {
                launchWave(_waveSettings.value)
            }
        }
    }

    /**
     * Live autocomplete suggestions with cover art when the user types an artist name in TasteOnboardingSheet.
     */
    fun onArtistPreferenceQueryChanged(query: String) {
        artistSuggestDebounceJob?.cancel()
        val clean = query.trim()
        if (clean.isBlank()) {
            _artistAutocompleteSuggestions.value = emptyList()
            _isArtistSuggestLoading.value = false
            return
        }

        // 1. Instant local matches from starter catalog & user collection (0ms response)
        val knownArtworks = buildCurrentArtistArtworksMap()
        val localStarterMatches = SlantWaveEngine.starterArtistsCatalog
            .filter { seed ->
                seed.name.contains(clean, ignoreCase = true) ||
                    TrackDeduplicationEngine.areArtistNamesEquivalent(seed.name, clean)
            }
            .map { seed ->
                ArtistSuggestion(
                    name = seed.name,
                    subtitle = seed.subtitle,
                    artworkUrl = knownArtworks[seed.name]
                        ?: knownArtworks[seed.name.lowercase()].orEmpty(),
                    genreId = seed.genreId,
                    isRussian = seed.isRussian
                )
            }

        val collectionTracks = favoriteTracks.value + currentPlaylistDomainTracks() + historyTracks.value
        val localCollectionMatches = collectionTracks
            .filter { it.artist.contains(clean, ignoreCase = true) && it.artist.length >= 2 }
            .distinctBy { TrackDeduplicationEngine.normalizeText(it.artist) }
            .map { t ->
                val primary = TrackDeduplicationEngine.extractPrimaryArtistName(t.artist)
                ArtistSuggestion(
                    name = primary,
                    subtitle = "В вашей коллекции • ${t.genre}",
                    artworkUrl = t.artworkUrl.ifBlank { knownArtworks[primary].orEmpty() },
                    isRussian = SlantWaveEngine.containsCyrillic(primary)
                )
            }

        val instantSuggestions = (localStarterMatches + localCollectionMatches)
            .distinctBy { TrackDeduplicationEngine.normalizeText(it.name) }
            .take(6)

        _artistAutocompleteSuggestions.value = instantSuggestions

        if (clean.length < 2) {
            _isArtistSuggestLoading.value = false
            return
        }

        // 2. Debounced live API search across Deezer & iTunes for any artist in the world with cover art
        artistSuggestDebounceJob = viewModelScope.launch {
            _isArtistSuggestLoading.value = true
            delay(200L)
            val remoteSuggestions = runCatching {
                musicRepository.searchArtistSuggestions(clean, limit = 8)
            }.getOrDefault(emptyList())

            // Update artworks cache with newly discovered artist covers
            val newArtworks = remoteSuggestions
                .filter { it.artworkUrl.isNotBlank() }
                .associate { it.name to it.artworkUrl }
            if (newArtworks.isNotEmpty()) {
                _artistArtworksMap.value = _artistArtworksMap.value + newArtworks
            }

            val combined = LinkedHashMap<String, ArtistSuggestion>()
            for (item in (instantSuggestions + remoteSuggestions)) {
                val norm = TrackDeduplicationEngine.normalizeText(item.name)
                if (norm.isBlank()) continue
                val existing = combined[norm]
                if (existing == null) {
                    combined[norm] = item
                } else if (existing.artworkUrl.isBlank() && item.artworkUrl.isNotBlank()) {
                    combined[norm] = existing.copy(artworkUrl = item.artworkUrl)
                }
            }
            _artistAutocompleteSuggestions.value = combined.values.take(8).toList()
            _isArtistSuggestLoading.value = false
        }
    }

    fun clearArtistAutocompleteSuggestions() {
        artistSuggestDebounceJob?.cancel()
        _artistAutocompleteSuggestions.value = emptyList()
        _isArtistSuggestLoading.value = false
    }

    private fun buildCurrentArtistArtworksMap(): Map<String, String> {
        val map = HashMap<String, String>(_artistArtworksMap.value)
        val allTracks = favoriteTracks.value +
            currentPlaylistDomainTracks() +
            historyTracks.value +
            _exploreFeed.value.personalForYouTracks +
            _exploreFeed.value.favoriteArtistsTracks +
            _exploreFeed.value.globalChartTracks
        for (t in allTracks) {
            if (t.artworkUrl.isNotBlank()) {
                val primary = TrackDeduplicationEngine.extractPrimaryArtistName(t.artist)
                map.putIfAbsent(primary, t.artworkUrl)
                map.putIfAbsent(primary.lowercase(), t.artworkUrl)
            }
        }
        return map
    }

    fun preloadArtistArtworksInBackground() {
        viewModelScope.launch {
            val seedNames = SlantWaveEngine.starterArtistsCatalog.map { it.name }
            val userSelected = _likedArtistNames.value + userSettings.value.tasteProfile.selectedArtists
            val resolved = runCatching {
                musicRepository.resolveArtistArtworksBatch(
                    artistNames = (userSelected + seedNames).distinct(),
                    existingMap = buildCurrentArtistArtworksMap()
                )
            }.getOrDefault(emptyMap())
            if (resolved.isNotEmpty()) {
                _artistArtworksMap.value = _artistArtworksMap.value + resolved
            }
        }
    }

    fun toggleLikeAlbum(artistName: String, albumTitle: String) {
        val albumKey = "${artistName.trim()}|${albumTitle.trim()}"
        viewModelScope.launch {
            val updatedAlbums = preferencesRepository.toggleLikedAlbum(albumKey)
            _selectedArtistProfile.value = _selectedArtistProfile.value?.let { current ->
                current.copy(
                    albums = current.albums.map { alb ->
                        val key = "${alb.artist.trim()}|${alb.title.trim()}"
                        alb.copy(isLiked = updatedAlbums.any { it.equals(key, ignoreCase = true) })
                    }
                )
            }
            refreshExploreFeed()
        }
    }

    // --- "МОЯ ВОЛНА" (SLANTWAVE) MULTI-DIMENSIONAL ALGORITHMIC ENGINE ---

    fun updateWaveSettings(newSettings: WaveSettings) {
        _waveSettings.value = newSettings
    }

    fun resetWaveSettings() {
        val reset = WaveSettings(contextLabel = "Моя волна")
        _waveSettings.value = reset
        if (_waveQueueTracks.value.isNotEmpty()) {
            launchWave(reset)
        }
    }

    /**
     * Toggles a quick mood/activity/character/language chip from ExploreScreen and immediately
     * synthesizes and plays the updated Wave stream.
     */
    fun toggleQuickWaveChip(chipLabel: String) {
        val current = _waveSettings.value
        val updated = when (chipLabel) {
            "Бодрое" -> current.copy(mood = if (current.mood == WaveMood.ENERGETIC) null else WaveMood.ENERGETIC)
            "Весёлое" -> current.copy(mood = if (current.mood == WaveMood.CHEERFUL) null else WaveMood.CHEERFUL)
            "Спокойное" -> current.copy(mood = if (current.mood == WaveMood.CALM) null else WaveMood.CALM)
            "Грустное" -> current.copy(mood = if (current.mood == WaveMood.SAD) null else WaveMood.SAD)
            "Просыпаюсь" -> current.copy(activity = if (current.activity == WaveActivity.WAKING_UP) null else WaveActivity.WAKING_UP)
            "В дороге" -> current.copy(activity = if (current.activity == WaveActivity.ON_THE_ROAD) null else WaveActivity.ON_THE_ROAD)
            "Работаю" -> current.copy(activity = if (current.activity == WaveActivity.WORKING) null else WaveActivity.WORKING)
            "Тренируюсь" -> current.copy(activity = if (current.activity == WaveActivity.WORKOUT) null else WaveActivity.WORKOUT)
            "Засыпаю" -> current.copy(activity = if (current.activity == WaveActivity.FALLING_ASLEEP) null else WaveActivity.FALLING_ASLEEP)
            "Любимое" -> current.copy(character = if (current.character == WaveCharacter.FAVORITE) null else WaveCharacter.FAVORITE)
            "Незнакомое" -> current.copy(character = if (current.character == WaveCharacter.UNFAMILIAR) null else WaveCharacter.UNFAMILIAR)
            "Популярное" -> current.copy(character = if (current.character == WaveCharacter.POPULAR) null else WaveCharacter.POPULAR)
            "Русский" -> current.copy(language = if (current.language == WaveLanguage.RUSSIAN) null else WaveLanguage.RUSSIAN)
            "Иностранный" -> current.copy(language = if (current.language == WaveLanguage.FOREIGN) null else WaveLanguage.FOREIGN)
            "Без слов" -> current.copy(language = if (current.language == WaveLanguage.INSTRUMENTAL) null else WaveLanguage.INSTRUMENTAL)
            else -> current
        }
        launchWave(updated)
    }

    fun launchWave(settings: WaveSettings = _waveSettings.value) {
        val tasteProfile = userSettings.value.tasteProfile
        val label = if (settings.isCustomized) {
            "Моя волна • ${settings.summarySubtitle()}"
        } else {
            "Моя волна • ДНК ${tasteDnaSummary.value.personalizationPercent}%"
        }
        val activeSettings = settings.copy(contextLabel = label)
        _waveSettings.value = activeSettings

        // Auto-sync DSP equalizer preset to match the wave's acoustic character or user's top genre
        if (activeSettings.autoSyncEqualizer) {
            val recommendedPreset = SlantWaveEngine.recommendedEqualizerPreset(activeSettings, tasteProfile)
            playerController.audioEffectsManager.selectPreset(recommendedPreset)
            saveCurrentDsp()
        }

        waveGenerationJob?.cancel()
        waveGenerationJob = viewModelScope.launch {
            _isWaveGenerating.value = true
            val plTracks = currentPlaylistDomainTracks()
            val allLikedArtists = _likedArtistNames.value + tasteProfile.selectedArtists

            val targetedQueries = SlantWaveEngine.buildTargetedWaveQueries(
                settings = activeSettings,
                tasteProfile = tasteProfile,
                favoriteTracks = favoriteTracks.value,
                playlistTracks = plTracks,
                historyTracks = historyTracks.value,
                likedArtists = allLikedArtists
            )

            val remoteHarvested = runCatching {
                musicRepository.fetchWavePool(
                    queries = targetedQueries,
                    enabledProviders = userSettings.value.enabledProviders
                )
            }.getOrDefault(emptyList())

            val basePool = buildList {
                when (activeSettings.character) {
                    WaveCharacter.FAVORITE -> {
                        addAll(favoriteTracks.value)
                        addAll(plTracks)
                        addAll(_exploreFeed.value.favoriteArtistsTracks)
                        addAll(historyTracks.value)
                        addAll(remoteHarvested)
                        addAll(_localTracks.value)
                    }
                    WaveCharacter.UNFAMILIAR -> {
                        addAll(remoteHarvested)
                        addAll(_exploreFeed.value.genreRadarTracks)
                        addAll(_exploreFeed.value.trendingFullStreams)
                        addAll(_exploreFeed.value.globalChartTracks)
                    }
                    WaveCharacter.POPULAR -> {
                        addAll(_exploreFeed.value.globalChartTracks)
                        addAll(remoteHarvested)
                        addAll(_exploreFeed.value.trendingFullStreams)
                    }
                    null -> {
                        addAll(remoteHarvested)
                        addAll(_exploreFeed.value.personalForYouTracks)
                        addAll(_exploreFeed.value.favoriteArtistsTracks)
                        addAll(favoriteTracks.value)
                        addAll(plTracks)
                        addAll(_exploreFeed.value.trendingFullStreams)
                        addAll(_exploreFeed.value.globalChartTracks)
                        addAll(_localTracks.value)
                    }
                }
            }

            val relatedArtists = runCatching {
                musicRepository.resolveRelatedArtistsForNames(
                    (allLikedArtists + tasteDnaSummary.value.topArtists).distinct().take(4)
                )
            }.getOrDefault(emptyList())

            val rankedWaveQueue = SlantWaveEngine.rankAndFilterWaveCandidates(
                rawCandidates = basePool,
                settings = activeSettings,
                tasteProfile = tasteProfile,
                favoriteTracks = favoriteTracks.value,
                playlistTracks = plTracks,
                historyTracks = historyTracks.value,
                likedArtists = allLikedArtists,
                dislikedTrackIds = _dislikedTrackIds.value,
                relatedArtists = relatedArtists.toSet()
            )

            _waveQueueTracks.value = rankedWaveQueue
            _isWaveGenerating.value = false

            val first = rankedWaveQueue.firstOrNull()
            if (first != null) {
                playTrack(first, rankedWaveQueue)
            }
        }
    }

    fun launchTrackWave(seedTrack: Track) {
        val currentSettings = _waveSettings.value.copy(
            contextLabel = "Волна по треку • ${seedTrack.title}"
        )
        _waveSettings.value = currentSettings
        waveGenerationJob?.cancel()
        waveGenerationJob = viewModelScope.launch {
            _isWaveGenerating.value = true
            val primaryArtist = TrackDeduplicationEngine.extractPrimaryArtistName(seedTrack.artist)
            val queries = listOfNotNull(
                "$primaryArtist ${seedTrack.genre}".trim(),
                primaryArtist,
                seedTrack.genre.takeIf { it.isNotBlank() && it != "Music" }
            ).distinct()

            val similar = runCatching {
                musicRepository.fetchWavePool(
                    queries = queries,
                    enabledProviders = userSettings.value.enabledProviders
                )
            }.getOrDefault(emptyList())

            val plTracks = currentPlaylistDomainTracks()
            val tasteProfile = userSettings.value.tasteProfile

            val filteredSimilar = SlantWaveEngine.rankAndFilterWaveCandidates(
                rawCandidates = similar + _exploreFeed.value.personalForYouTracks + _exploreFeed.value.trendingFullStreams,
                settings = currentSettings,
                tasteProfile = tasteProfile,
                favoriteTracks = favoriteTracks.value,
                playlistTracks = plTracks,
                historyTracks = historyTracks.value,
                likedArtists = _likedArtistNames.value + tasteProfile.selectedArtists,
                dislikedTrackIds = _dislikedTrackIds.value
            )

            val waveQueue = (listOf(seedTrack) + filteredSimilar)
                .distinctBy { it.id }
                .filterNot { it.id in _dislikedTrackIds.value }

            _waveQueueTracks.value = waveQueue
            _isWaveGenerating.value = false
            playTrack(seedTrack, waveQueue)
        }
    }

    fun launchArtistWave(artistName: String) {
        val cleanArtist = TrackDeduplicationEngine.extractPrimaryArtistName(artistName)
        val currentSettings = _waveSettings.value.copy(
            contextLabel = "Волна артиста • $cleanArtist"
        )
        _waveSettings.value = currentSettings
        waveGenerationJob?.cancel()
        waveGenerationJob = viewModelScope.launch {
            _isWaveGenerating.value = true
            val artistTracks = runCatching {
                musicRepository.searchMultiSource(
                    query = cleanArtist,
                    enabledProviders = userSettings.value.enabledProviders,
                    filter = SourceFilter.ALL
                )
            }.getOrDefault(emptyList())

            val verifiedArtistTracks = artistTracks.filter {
                TrackDeduplicationEngine.isTrackByArtist(it, cleanArtist)
            }

            val waveQueue = (verifiedArtistTracks.ifEmpty { artistTracks })
                .distinctBy { "${TrackDeduplicationEngine.normalizeText(it.artist)}|${TrackDeduplicationEngine.normalizeText(it.title)}" }
                .filterNot { it.id in _dislikedTrackIds.value || it.isLiveStream }

            _waveQueueTracks.value = waveQueue
            _isWaveGenerating.value = false
            val first = waveQueue.firstOrNull()
            if (first != null) {
                playTrack(first, waveQueue)
            }
        }
    }

    fun launchCollectionWave() {
        val settings = _waveSettings.value.copy(
            character = WaveCharacter.FAVORITE,
            contextLabel = "Моя волна • Коллекция"
        )
        launchWave(settings)
    }

    fun launchLikedPlaylistWave() {
        val settings = _waveSettings.value.copy(
            character = WaveCharacter.FAVORITE,
            contextLabel = "Волна • Мне нравится"
        )
        launchWave(settings)
    }

    // --- ARTIST PROFILE BUILDER (STRICT SINGLE-SOURCE ISOLATION — NO CROSS-SOURCE MIXING) ---

    fun openArtistProfile(artistName: String, preferredProvider: SourceProvider? = null) {
        val cleanName = TrackDeduplicationEngine.extractPrimaryArtistName(artistName).ifBlank {
            artistName.trim()
        }
        if (cleanName.isBlank()) return

        val inferredProvider = preferredProvider ?: playbackState.value.currentTrack
            ?.takeIf { TrackDeduplicationEngine.isTrackByArtist(it, cleanName) }
            ?.primaryProvider

        val allKnown = (
            favoriteTracks.value +
                currentPlaylistDomainTracks() +
                historyTracks.value +
                _localTracks.value +
                _rawSearchResults.value +
                _exploreFeed.value.personalForYouTracks +
                _exploreFeed.value.trendingFullStreams +
                _exploreFeed.value.globalChartTracks
            )

        val initialGrouped = TrackDeduplicationEngine.groupArtistTracksByProvider(allKnown, cleanName)
        val initialActiveProvider = inferredProvider?.takeIf { it in initialGrouped.keys }
            ?: initialGrouped.keys.firstOrNull()
            ?: inferredProvider
            ?: SourceProvider.ITUNES

        val isLiked = _likedArtistNames.value.any { it.equals(cleanName, ignoreCase = true) }

        _selectedArtistProfile.value = buildArtistProfileObject(
            name = cleanName,
            tracksByProvider = initialGrouped,
            activeProvider = initialActiveProvider,
            isLiked = isLiked,
            isLoading = true,
            bioText = _artistBio.value?.takeIf {
                it.artistName.equals(cleanName, ignoreCase = true)
            }?.biography.orEmpty()
        )

        viewModelScope.launch {
            val groupedByProvider = runCatching {
                musicRepository.fetchArtistCatalogByProvider(
                    artistName = cleanName,
                    enabledProviders = userSettings.value.enabledProviders,
                    existingKnownTracks = allKnown
                )
            }.getOrDefault(initialGrouped)

            val bio = runCatching {
                lyricsRepository.fetchArtistBio(cleanName)
            }.getOrNull()

            val chosenProvider = inferredProvider?.takeIf { it in groupedByProvider.keys }
                ?: groupedByProvider.keys.firstOrNull()
                ?: initialActiveProvider

            _selectedArtistProfile.value = buildArtistProfileObject(
                name = cleanName,
                tracksByProvider = groupedByProvider,
                activeProvider = chosenProvider,
                isLiked = _likedArtistNames.value.any { it.equals(cleanName, ignoreCase = true) },
                isLoading = false,
                bioText = bio?.biography.orEmpty(),
                bannerOverride = bio?.bannerUrl.orEmpty()
            )
        }
    }

    /**
     * Switches the active source provider in the Artist Profile so only tracks from
     * that single selected provider are displayed (no mixing across providers).
     */
    fun selectArtistProvider(provider: SourceProvider) {
        val current = _selectedArtistProfile.value ?: return
        if (provider !in current.tracksByProvider.keys) return
        _selectedArtistProfile.value = buildArtistProfileObject(
            name = current.name,
            tracksByProvider = current.tracksByProvider,
            activeProvider = provider,
            isLiked = current.isLiked,
            isLoading = current.isLoading,
            bioText = current.biography,
            bannerOverride = current.avatarUrl
        )
    }

    private fun buildArtistProfileObject(
        name: String,
        tracksByProvider: Map<SourceProvider, List<Track>>,
        activeProvider: SourceProvider,
        isLiked: Boolean,
        isLoading: Boolean,
        bioText: String,
        bannerOverride: String = ""
    ): ArtistProfile {
        val isolatedTracks = tracksByProvider[activeProvider].orEmpty()
        val allVerifiedTracks = tracksByProvider.values.flatten()
        val likedAlbumSet = userSettings.value.tasteProfile.likedAlbums

        val heroUrl = bannerOverride.ifBlank {
            isolatedTracks.firstOrNull { it.artworkUrl.isNotBlank() }?.artworkUrl
                ?: allVerifiedTracks.firstOrNull { it.artworkUrl.isNotBlank() }?.artworkUrl
                ?: ""
        }

        val latest = isolatedTracks.firstOrNull()
        val albumGroups = isolatedTracks
            .groupBy { it.album.ifBlank { "${it.title} — Single" } }
            .entries
            .mapIndexed { idx, (albumTitle, albumTracks) ->
                val albumKey = "${name.trim()}|${albumTitle.trim()}"
                AlbumSummary(
                    id = "alb_${name}_${activeProvider.name}_$idx",
                    title = albumTitle,
                    artist = name,
                    artworkUrl = albumTracks.firstOrNull { it.artworkUrl.isNotBlank() }?.artworkUrl ?: heroUrl,
                    releaseYear = albumTracks.firstOrNull()?.releaseYear.orEmpty(),
                    tracks = albumTracks,
                    isLiked = likedAlbumSet.any { it.equals(albumKey, ignoreCase = true) }
                )
            }

        return ArtistProfile(
            name = name,
            avatarUrl = heroUrl,
            isLiked = isLiked,
            isLoading = isLoading,
            biography = bioText,
            activeProvider = activeProvider,
            availableProviders = tracksByProvider.keys.toList(),
            tracksByProvider = tracksByProvider,
            latestRelease = latest,
            popularTracks = isolatedTracks,
            albums = albumGroups
        )
    }

    fun toggleLikeArtist(artistName: String) {
        viewModelScope.launch {
            val updated = preferencesRepository.toggleLikedArtist(artistName)
            _likedArtistNames.value = updated
            _selectedArtistProfile.value = _selectedArtistProfile.value?.copy(
                isLiked = updated.any { it.equals(artistName.trim(), ignoreCase = true) }
            )
            refreshExploreFeed()
        }
    }

    // --- SMART ALGORITHMIC PLAYLIST GENERATOR ---

    fun generateSmartPlaylist(preset: SmartPlaylistPreset, playImmediately: Boolean = true) {
        viewModelScope.launch {
            val tasteProfile = userSettings.value.tasteProfile
            val dna = tasteDnaSummary.value
            val plTracks = currentPlaylistDomainTracks()

            val dynamicQuery = when (preset) {
                SmartPlaylistPreset.PERSONAL_DNA_MIX -> {
                    val topArt = dna.topArtists.firstOrNull().orEmpty()
                    val topGen = dna.topGenres.firstOrNull()?.primaryQuery ?: preset.defaultQuery
                    "$topArt $topGen".trim()
                }
                SmartPlaylistPreset.MY_TOP_HITS -> {
                    dna.topArtists.take(2).joinToString(" ").ifBlank { preset.defaultQuery }
                }
                SmartPlaylistPreset.DISCOVERY_RADAR -> {
                    val topGen = dna.topGenres.firstOrNull()?.primaryQuery ?: preset.defaultQuery
                    "Underground Fresh $topGen"
                }
                else -> preset.defaultQuery
            }

            val remotePool = runCatching {
                musicRepository.searchMultiSource(
                    query = dynamicQuery,
                    enabledProviders = userSettings.value.enabledProviders,
                    filter = SourceFilter.ALL
                )
            }.getOrDefault(emptyList())

            val presetSettings = when (preset) {
                SmartPlaylistPreset.PERSONAL_DNA_MIX -> WaveSettings(contextLabel = preset.title)
                SmartPlaylistPreset.MY_TOP_HITS -> WaveSettings(character = WaveCharacter.FAVORITE)
                SmartPlaylistPreset.NIGHT_FOCUS -> WaveSettings(
                    activity = WaveActivity.WORKING,
                    mood = WaveMood.CALM,
                    language = WaveLanguage.INSTRUMENTAL
                )
                SmartPlaylistPreset.WORKOUT_ENERGY -> WaveSettings(
                    activity = WaveActivity.WORKOUT,
                    mood = WaveMood.ENERGETIC
                )
                SmartPlaylistPreset.DISCOVERY_RADAR -> WaveSettings(
                    character = WaveCharacter.UNFAMILIAR
                )
            }

            val combined = (
                favoriteTracks.value +
                    plTracks +
                    historyTracks.value +
                    remotePool +
                    _exploreFeed.value.personalForYouTracks +
                    _exploreFeed.value.trendingFullStreams +
                    _exploreFeed.value.globalChartTracks +
                    _localTracks.value
                )

            val curated = SlantWaveEngine.rankAndFilterWaveCandidates(
                rawCandidates = combined,
                settings = presetSettings,
                tasteProfile = tasteProfile,
                favoriteTracks = favoriteTracks.value,
                playlistTracks = plTracks,
                historyTracks = historyTracks.value,
                likedArtists = _likedArtistNames.value + tasteProfile.selectedArtists,
                dislikedTrackIds = _dislikedTrackIds.value
            ).take(22)

            if (curated.isNotEmpty()) {
                val playlistId = musicRepository.createPlaylist(
                    name = preset.title,
                    description = "${preset.description} (${curated.size} треков • ДНК ${dna.personalizationPercent}%)"
                )
                curated.forEach { track ->
                    musicRepository.addTrackToPlaylist(playlistId, track)
                }
                if (playImmediately) {
                    playTrack(curated.first(), curated)
                }
            }
        }
    }

    // --- TRAILER (30s HIGHLIGHT), DISLIKE, QUEUE MANAGEMENT ---

    fun playTrailerHighlight(track: Track) {
        playTrack(track, listOf(track))
        viewModelScope.launch {
            delay(450L)
            val targetMs = ((track.durationSec.coerceAtLeast(90)) * 350L).coerceAtLeast(25_000L)
            playerController.seekTo(targetMs)
        }
    }

    fun dislikeTrack(track: Track) {
        _dislikedTrackIds.value = _dislikedTrackIds.value + track.id
        _waveQueueTracks.value = _waveQueueTracks.value.filterNot { it.id == track.id }
        viewModelScope.launch {
            preferencesRepository.addDislikedTrackId(track.id)
        }
        if (playbackState.value.currentTrack?.id == track.id) {
            playerController.skipNext()
        }
    }

    fun removeTrackFromQueue(track: Track) {
        val currentQueue = playbackState.value.queue.filterNot { it.id == track.id }
        if (playbackState.value.currentTrack?.id == track.id) {
            val next = currentQueue.firstOrNull()
            if (next != null) {
                playTrack(next, currentQueue)
            }
        }
    }

    fun scanLocalDeviceAudio() {
        viewModelScope.launch {
            val scanned = musicRepository.scanLocalTracks()
            _localTracks.value = scanned
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchDebounceJob?.cancel()
        if (query.isBlank()) {
            _rawSearchResults.value = emptyList()
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        searchDebounceJob = viewModelScope.launch {
            delay(380L)
            executeSearch(query)
        }
    }

    fun submitSearchNow(query: String = _searchQuery.value) {
        _searchQuery.value = query
        searchDebounceJob?.cancel()
        if (query.isBlank()) return
        viewModelScope.launch {
            musicRepository.saveSearchQuery(query)
            executeSearch(query)
        }
    }

    fun selectSourceFilter(filter: SourceFilter) {
        _selectedSourceFilter.value = filter
        val q = _searchQuery.value
        if (q.isNotBlank()) {
            submitSearchNow(q)
        }
    }

    fun selectSearchSortMode(sortMode: SearchSortMode) {
        _searchSortMode.value = sortMode
        _searchResults.value = applySearchSort(_rawSearchResults.value, sortMode)
    }

    private fun applySearchSort(tracks: List<Track>, sortMode: SearchSortMode): List<Track> {
        return when (sortMode) {
            SearchSortMode.RELEVANCE -> {
                val tasteProfile = userSettings.value.tasteProfile
                val plTracks = currentPlaylistDomainTracks()
                val favArtistsNorm = (_likedArtistNames.value + tasteProfile.selectedArtists + favoriteTracks.value.map { it.artist } + plTracks.map { it.artist })
                    .map { TrackDeduplicationEngine.normalizeText(TrackDeduplicationEngine.extractPrimaryArtistName(it)) }
                    .toSet()
                val targetGenres = tasteProfile.selectedGenres

                tracks.sortedByDescending { track ->
                    var boost = 0
                    val artNorm = TrackDeduplicationEngine.normalizeText(TrackDeduplicationEngine.extractPrimaryArtistName(track.artist))
                    if (artNorm in favArtistsNorm) boost += 300
                    if (SlantWaveEngine.inferTrackGenres(track).any { it in targetGenres }) boost += 200
                    if (!track.isPreviewOnly && track.durationSec > 40) boost += 150
                    boost
                }
            }
            SearchSortMode.FULL_TRACKS_FIRST -> tracks.sortedWith(
                compareByDescending<Track> { !it.isPreviewOnly && it.durationSec > 35 }
                    .thenByDescending { it.durationSec }
            )
            SearchSortMode.QUALITY_HIGH -> tracks.sortedByDescending {
                it.primaryProvider.defaultBitrateKbps + (if (!it.isPreviewOnly) 200 else 0)
            }
            SearchSortMode.DURATION_DESC -> tracks.sortedByDescending { it.durationSec }
            SearchSortMode.ALPHABETICAL -> tracks.sortedBy { it.title.lowercase() }
        }
    }

    private suspend fun executeSearch(query: String) {
        _isSearching.value = true
        val results = musicRepository.searchMultiSource(
            query = query,
            enabledProviders = userSettings.value.enabledProviders,
            filter = _selectedSourceFilter.value
        )
        _rawSearchResults.value = results
        _searchResults.value = applySearchSort(results, _searchSortMode.value)
        _isSearching.value = false
    }

    fun playTrack(track: Track, queue: List<Track> = listOf(track)) {
        playerController.playTrack(track, queue)
    }

    fun togglePlayPause() {
        val current = playbackState.value.currentTrack
        if (current == null) {
            launchWave(_waveSettings.value)
            return
        }
        playerController.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        playerController.seekTo(positionMs)
    }

    fun skipNext() {
        playerController.skipNext()
    }

    fun skipPrevious() {
        playerController.skipPrevious()
    }

    fun toggleShuffle() {
        playerController.toggleShuffle()
    }

    fun cycleRepeatMode() {
        playerController.cycleRepeatMode()
    }

    fun switchStreamCandidate(candidate: StreamCandidate) {
        playerController.switchStreamCandidate(candidate)
    }

    fun startSleepTimer(minutes: Int) {
        playerController.startSleepTimer(minutes)
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            val isFav = musicRepository.toggleFavorite(track)
            if (playbackState.value.currentTrack?.id == track.id) {
                playerController.updateCurrentTrackFavoriteState(isFav)
            }
            _rawSearchResults.value = _rawSearchResults.value.map {
                if (it.id == track.id) it.copy(isFavorite = isFav) else it
            }
            _searchResults.value = _searchResults.value.map {
                if (it.id == track.id) it.copy(isFavorite = isFav) else it
            }
        }
    }

    fun downloadTrackForOffline(track: Track) {
        if (track.id in _downloadingTrackIds.value) return
        viewModelScope.launch {
            _downloadingTrackIds.value = _downloadingTrackIds.value + track.id
            val updated = musicRepository.downloadTrackForOffline(track)
            _downloadingTrackIds.value = _downloadingTrackIds.value - track.id
            _rawSearchResults.value = _rawSearchResults.value.map {
                if (it.id == track.id) updated else it
            }
            _searchResults.value = _searchResults.value.map {
                if (it.id == track.id) updated else it
            }
            refreshExoCacheSize()
        }
    }

    fun createPlaylist(name: String, description: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            musicRepository.createPlaylist(name, description)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            musicRepository.deletePlaylist(playlistId)
        }
    }

    fun addTrackToPlaylist(playlistId: Long, track: Track) {
        viewModelScope.launch {
            musicRepository.addTrackToPlaylist(playlistId, track)
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: String) {
        viewModelScope.launch {
            musicRepository.removeTrackFromPlaylist(playlistId, trackId)
        }
    }

    fun deleteSearchHistoryItem(query: String) {
        viewModelScope.launch {
            musicRepository.deleteSearchQuery(query)
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            musicRepository.clearSearchHistory()
        }
    }

    private fun loadLyricsAndBioForTrack(track: Track) {
        viewModelScope.launch {
            _isLyricsLoading.value = true
            val lyrics = lyricsRepository.getLyricsForTrack(track)
            _currentLyrics.value = lyrics
            _isLyricsLoading.value = false
        }
        viewModelScope.launch {
            _artistBio.value = lyricsRepository.fetchArtistBio(track.artist)
        }
    }

    fun getActiveLyricLineIndex(positionMs: Long): Int {
        val lines = _currentLyrics.value?.lines ?: return -1
        return LrcParser.findActiveLineIndex(lines, positionMs)
    }

    // DSP & Equalizer controls
    fun setDspEnabled(enabled: Boolean) {
        playerController.audioEffectsManager.setEnabled(enabled)
        saveCurrentDsp()
    }

    fun selectEqualizerPreset(preset: EqualizerPreset) {
        playerController.audioEffectsManager.selectPreset(preset)
        saveCurrentDsp()
    }

    fun setEqualizerBandGain(bandIndex: Int, gainDb: Float) {
        playerController.audioEffectsManager.setBandGain(bandIndex, gainDb)
        saveCurrentDsp()
    }

    fun setBassBoostPercent(percent: Int) {
        playerController.audioEffectsManager.setBassBoost(percent)
        saveCurrentDsp()
    }

    fun setVirtualizerPercent(percent: Int) {
        playerController.audioEffectsManager.setVirtualizer(percent)
        saveCurrentDsp()
    }

    fun setLoudnessGainDb(gainDb: Float) {
        playerController.audioEffectsManager.setLoudnessGain(gainDb)
        saveCurrentDsp()
    }

    private fun saveCurrentDsp() {
        viewModelScope.launch {
            preferencesRepository.updateDspState(playerController.audioEffectsManager.dspState.value)
        }
    }

    // Theme & Developer API settings
    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setDarkTheme(enabled)
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setDynamicColor(enabled)
        }
    }

    fun toggleSourceProvider(provider: SourceProvider, enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.toggleProvider(provider, enabled)
        }
    }

    fun pingAllProvidersNow() {
        viewModelScope.launch {
            musicRepository.pingAllProviders(userSettings.value.enabledProviders)
        }
    }

    fun setMaxCacheSizeMb(sizeMb: Int) {
        viewModelScope.launch {
            preferencesRepository.setMaxCacheSizeMb(sizeMb)
        }
    }

    fun clearAllCaches() {
        viewModelScope.launch {
            ExoPlayerCacheProvider.clearCacheFiles(appContext)
            musicRepository.clearAllOfflineDownloads()
            refreshExoCacheSize()
        }
    }

    fun refreshExoCacheSize() {
        _exoCacheUsedBytes.value = ExoPlayerCacheProvider.getCacheUsedBytes(appContext)
    }

    fun syncFirebaseRemoteConfig() {
        remoteConfigManager.syncRemoteConfig()
    }

    fun setRemoteBitrateOverride(bitrateKbps: Int) {
        remoteConfigManager.updateOverrideBitrate(bitrateKbps)
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val appCtx = context.applicationContext
            val db = SlantMuzDatabase.getInstance(appCtx)
            val scanner = LocalMediaScanner(appCtx)
            val musicRepo = CompositeMusicRepository(appCtx, db.dao(), scanner)
            val lyricsRepo = LyricsRepository(db.dao())
            val prefsRepo = UserPreferencesRepository(appCtx)
            val remoteConfig = FirebaseRemoteConfigManager()
            val controller = PlayerController.getInstance(appCtx)
            return PlayerViewModel(
                appContext = appCtx,
                musicRepository = musicRepo,
                lyricsRepository = lyricsRepo,
                preferencesRepository = prefsRepo,
                remoteConfigManager = remoteConfig,
                playerController = controller
            ) as T
        }
    }
}
