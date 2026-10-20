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
import com.example.domain.ArtistBioInfo
import com.example.domain.DspState
import com.example.domain.EqualizerPreset
import com.example.domain.LrcParser
import com.example.domain.ProviderHealthStatus
import com.example.domain.RemoteStreamingConfig
import com.example.domain.SourceFilter
import com.example.domain.SourceProvider
import com.example.domain.StreamCandidate
import com.example.domain.SyncedLyrics
import com.example.domain.Track
import com.example.player.ExoPlayerCacheProvider
import com.example.player.PlaybackUiState
import com.example.player.PlayerController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private var searchDebounceJob: Job? = null

    init {
        playerController.attachResolvers(
            resolver = { provider, rawUrl, track ->
                musicRepository.resolvePlayableUrl(provider, rawUrl, track)
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
            }
        }

        refreshExploreFeed()
        scanLocalDeviceAudio()
        refreshExoCacheSize()
    }

    fun refreshExploreFeed() {
        viewModelScope.launch {
            _isExploreLoading.value = true
            val feed = musicRepository.loadExploreSections(userSettings.value.enabledProviders)
            _exploreFeed.value = feed
            _isExploreLoading.value = false

            // Pre-select first track in player state if none is loaded yet (without auto-playing)
            if (playbackState.value.currentTrack == null) {
                val initialQueue = feed.trendingFullStreams.ifEmpty { feed.globalChartTracks }
                val first = initialQueue.firstOrNull()
                if (first != null) {
                    loadLyricsAndBioForTrack(first)
                }
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

    private suspend fun executeSearch(query: String) {
        _isSearching.value = true
        val results = musicRepository.searchMultiSource(
            query = query,
            enabledProviders = userSettings.value.enabledProviders,
            filter = _selectedSourceFilter.value
        )
        _searchResults.value = results
        _isSearching.value = false
    }

    fun playTrack(track: Track, queue: List<Track> = listOf(track)) {
        playerController.playTrack(track, queue)
        loadLyricsAndBioForTrack(track)
    }

    fun togglePlayPause() {
        val current = playbackState.value.currentTrack
        if (current == null) {
            val fallbackQueue = _exploreFeed.value.trendingFullStreams
                .ifEmpty { _exploreFeed.value.globalChartTracks }
            val first = fallbackQueue.firstOrNull()
            if (first != null) {
                playTrack(first, fallbackQueue)
            }
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
