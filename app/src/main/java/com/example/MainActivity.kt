package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.domain.ArtistProfile
import com.example.domain.LibrarySubCategory
import com.example.domain.LrcParser
import com.example.domain.SettingsSubCategory
import com.example.domain.Track
import com.example.ui.components.EqualizerBottomSheet
import com.example.ui.components.LyricsBottomSheet
import com.example.ui.components.MiniPlayerBar
import com.example.ui.components.PastelAuroraBackground
import com.example.ui.components.TasteOnboardingSheet
import com.example.ui.components.TrackActionsBottomSheet
import com.example.ui.components.WaveSettingsBottomSheet
import com.example.ui.screens.AddToPlaylistDialog
import com.example.ui.screens.ArtistScreen
import com.example.ui.screens.DeveloperSettingsScreen
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.LibrarySubCategoryScreen
import com.example.ui.screens.LikedTracksScreen
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsSubCategoryScreen
import com.example.ui.theme.SlantMuzTheme
import com.example.viewmodel.PlayerViewModel
import com.example.widget.SlantMuzWidgetUpdater

object SlantDestinations {
    const val ROUTE_EXPLORE = "explore"
    const val ROUTE_SEARCH = "search"
    const val ROUTE_PLAYER = "player"
    const val ROUTE_LIBRARY = "library"
    const val ROUTE_SETTINGS = "settings"
    const val ROUTE_ARTIST = "artist"
    const val ROUTE_LIKED = "liked_tracks"
    const val ROUTE_LIBRARY_SUB = "library_sub/{category}"
    const val ROUTE_SETTINGS_SUB = "settings_sub/{category}"

    fun librarySubRoute(category: LibrarySubCategory): String = "library_sub/${category.id}"
    fun settingsSubRoute(category: SettingsSubCategory): String = "settings_sub/${category.id}"
}

data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val playerViewModel: PlayerViewModel = viewModel(
                factory = PlayerViewModel.Factory(context)
            )
            val userSettings by playerViewModel.userSettings.collectAsStateWithLifecycle()

            SlantMuzTheme(
                darkTheme = userSettings.darkThemeMode,
                dynamicColor = userSettings.useDynamicColor
            ) {
                SlantMuzRootApp(viewModel = playerViewModel)
            }
        }
    }
}

@Composable
fun SlantMuzRootApp(viewModel: PlayerViewModel) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: SlantDestinations.ROUTE_EXPLORE

    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val dspState by viewModel.dspState.collectAsStateWithLifecycle()
    val userSettings by viewModel.userSettings.collectAsStateWithLifecycle()
    val providerStatuses by viewModel.providerStatuses.collectAsStateWithLifecycle()
    val remoteConfig by viewModel.remoteConfig.collectAsStateWithLifecycle()
    val exploreFeed by viewModel.exploreFeed.collectAsStateWithLifecycle()
    val isExploreLoading by viewModel.isExploreLoading.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedSourceFilter.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val favoriteTracks by viewModel.favoriteTracks.collectAsStateWithLifecycle()
    val offlineTracks by viewModel.offlineCachedTracks.collectAsStateWithLifecycle()
    val localTracks by viewModel.localTracks.collectAsStateWithLifecycle()
    val historyTracks by viewModel.historyTracks.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val currentLyrics by viewModel.currentLyrics.collectAsStateWithLifecycle()
    val isLyricsLoading by viewModel.isLyricsLoading.collectAsStateWithLifecycle()
    val artistBio by viewModel.artistBio.collectAsStateWithLifecycle()
    val downloadingIds by viewModel.downloadingTrackIds.collectAsStateWithLifecycle()
    val exoCacheBytes by viewModel.exoCacheUsedBytes.collectAsStateWithLifecycle()
    val roomOfflineBytes by viewModel.totalOfflineBytes.collectAsStateWithLifecycle()
    val waveSettings by viewModel.waveSettings.collectAsStateWithLifecycle()
    val isWaveGenerating by viewModel.isWaveGenerating.collectAsStateWithLifecycle()
    val waveQueueTracks by viewModel.waveQueueTracks.collectAsStateWithLifecycle()
    val searchSortMode by viewModel.searchSortMode.collectAsStateWithLifecycle()
    val selectedArtistProfile by viewModel.selectedArtistProfile.collectAsStateWithLifecycle()
    val likedArtistNames by viewModel.likedArtistNames.collectAsStateWithLifecycle()
    val tasteDnaSummary by viewModel.tasteDnaSummary.collectAsStateWithLifecycle()
    val artistAutocompleteSuggestions by viewModel.artistAutocompleteSuggestions.collectAsStateWithLifecycle()
    val isArtistSuggestLoading by viewModel.isArtistSuggestLoading.collectAsStateWithLifecycle()
    val artistArtworksMap by viewModel.artistArtworksMap.collectAsStateWithLifecycle()
    val cloudAccountState by viewModel.cloudAccountState.collectAsStateWithLifecycle()
    val likedAlbumKeys = userSettings.tasteProfile.likedAlbums
    val playlistDomainTracks = remember(playlists) {
        playlists.flatMap { pw -> pw.tracks.map { it.toDomain() } }
    }

    // Reset triggers incremented when user taps/re-taps a dockbar section
    var exploreResetTrigger by remember { mutableIntStateOf(0) }
    var searchResetTrigger by remember { mutableIntStateOf(0) }
    var playerResetTrigger by remember { mutableIntStateOf(0) }
    var libraryResetTrigger by remember { mutableIntStateOf(0) }
    var settingsResetTrigger by remember { mutableIntStateOf(0) }

    var showLyricsSheet by remember { mutableStateOf(false) }
    var showEqualizerSheet by remember { mutableStateOf(false) }
    var showWaveSettingsSheet by remember { mutableStateOf(false) }
    var showTasteSetupSheet by remember { mutableStateOf(false) }
    var showAccountAuthSheet by remember { mutableStateOf(false) }
    var trackForActionsSheet by remember { mutableStateOf<Track?>(null) }
    var trackToAddToPlaylist by remember { mutableStateOf<Track?>(null) }

    val activeLyricLine = remember(currentLyrics, playbackState.positionMs) {
        if (currentLyrics != null && currentLyrics?.isSynced == true) {
            val lines = currentLyrics?.lines.orEmpty()
            val idx = LrcParser.findActiveLineIndex(lines, playbackState.positionMs)
            lines.getOrNull(idx)?.text
        } else {
            null
        }
    }

    LaunchedEffect(activeLyricLine, playbackState.currentTrack, playbackState.isPlaying) {
        SlantMuzWidgetUpdater.updateAllWidgets(context, playbackState, activeLyricLine)
    }

    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(playbackState.isPlaying) {
        if (playbackState.isPlaying && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val navItems = remember {
        listOf(
            BottomNavItem(
                route = SlantDestinations.ROUTE_EXPLORE,
                label = "Волна",
                selectedIcon = Icons.Filled.AutoAwesome,
                unselectedIcon = Icons.Outlined.AutoAwesome,
                testTag = "nav_tab_explore"
            ),
            BottomNavItem(
                route = SlantDestinations.ROUTE_SEARCH,
                label = "Поиск",
                selectedIcon = Icons.Filled.Search,
                unselectedIcon = Icons.Outlined.Search,
                testTag = "nav_tab_search"
            ),
            BottomNavItem(
                route = SlantDestinations.ROUTE_PLAYER,
                label = "Плеер",
                selectedIcon = Icons.Filled.PlayCircleFilled,
                unselectedIcon = Icons.Outlined.PlayCircleOutline,
                testTag = "nav_tab_player"
            ),
            BottomNavItem(
                route = SlantDestinations.ROUTE_LIBRARY,
                label = "Коллекция",
                selectedIcon = Icons.Filled.Favorite,
                unselectedIcon = Icons.Outlined.LibraryMusic,
                testTag = "nav_tab_library"
            ),
            BottomNavItem(
                route = SlantDestinations.ROUTE_SETTINGS,
                label = "Настройки",
                selectedIcon = Icons.Filled.Settings,
                unselectedIcon = Icons.Outlined.Settings,
                testTag = "nav_tab_settings"
            )
        )
    }

    val allCatalogPool = remember(exploreFeed, favoriteTracks, historyTracks) {
        (exploreFeed.personalForYouTracks +
            exploreFeed.favoriteArtistsTracks +
            exploreFeed.genreRadarTracks +
            exploreFeed.trendingFullStreams +
            exploreFeed.globalChartTracks +
            favoriteTracks +
            historyTracks)
            .distinctBy { it.id }
    }

    val activeTrack = playbackState.currentTrack
    val totalDurationMs = playbackState.durationMs
        .coerceAtLeast((activeTrack?.durationSec ?: 1) * 1000L)
        .coerceAtLeast(1L)
    val progressFraction = (playbackState.positionMs.toFloat() / totalDurationMs.toFloat())
        .coerceIn(0f, 1f)

    PastelAuroraBackground(isDark = userSettings.darkThemeMode) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (activeTrack != null && currentRoute != SlantDestinations.ROUTE_PLAYER) {
                        MiniPlayerBar(
                            track = activeTrack,
                            isPlaying = playbackState.isPlaying,
                            progressFraction = progressFraction,
                            onBarClick = {
                                navController.navigate(SlantDestinations.ROUTE_PLAYER) {
                                    launchSingleTop = true
                                }
                            },
                            onPlayPauseClick = { viewModel.togglePlayPause() },
                            onNextClick = { viewModel.skipNext() },
                            onPreviousClick = { viewModel.skipPrevious() },
                            onToggleFavoriteClick = { viewModel.toggleFavorite(activeTrack) }
                        )
                    }

                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        tonalElevation = 0.dp,
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(24.dp))
                    ) {
                        navItems.forEach { item ->
                            val isLibraryChild = currentRoute == SlantDestinations.ROUTE_LIKED ||
                                currentRoute == SlantDestinations.ROUTE_LIBRARY_SUB ||
                                currentRoute == SlantDestinations.ROUTE_ARTIST
                            val isSettingsChild = currentRoute == SlantDestinations.ROUTE_SETTINGS_SUB

                            val selected = currentRoute == item.route ||
                                (item.route == SlantDestinations.ROUTE_LIBRARY && isLibraryChild) ||
                                (item.route == SlantDestinations.ROUTE_SETTINGS && isSettingsChild)

                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    // Always trigger reset to initial state/page of this section
                                    when (item.route) {
                                        SlantDestinations.ROUTE_EXPLORE -> exploreResetTrigger++
                                        SlantDestinations.ROUTE_SEARCH -> searchResetTrigger++
                                        SlantDestinations.ROUTE_PLAYER -> playerResetTrigger++
                                        SlantDestinations.ROUTE_LIBRARY -> libraryResetTrigger++
                                        SlantDestinations.ROUTE_SETTINGS -> settingsResetTrigger++
                                    }

                                    // If currently on a sub-window or a different tab, pop back to the root screen of this tab
                                    if (currentRoute != item.route) {
                                        val popped = navController.popBackStack(item.route, inclusive = false)
                                        if (!popped) {
                                            navController.navigate(item.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = false
                                                }
                                                launchSingleTop = true
                                                restoreState = false
                                            }
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label,
                                        modifier = Modifier.size(21.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                NavHost(
                    navController = navController,
                    startDestination = SlantDestinations.ROUTE_EXPLORE,
                    enterTransition = {
                        fadeIn(animationSpec = tween(220)) +
                            slideIntoContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                                animationSpec = tween(220)
                            )
                    },
                    exitTransition = {
                        fadeOut(animationSpec = tween(180))
                    },
                    popEnterTransition = {
                        fadeIn(animationSpec = tween(220))
                    },
                    popExitTransition = {
                        fadeOut(animationSpec = tween(180))
                    }
                ) {
                    composable(SlantDestinations.ROUTE_EXPLORE) {
                        ExploreScreen(
                            exploreFeed = exploreFeed,
                            isLoading = isExploreLoading,
                            currentTrack = playbackState.currentTrack,
                            isPlaying = playbackState.isPlaying,
                            isBuffering = playbackState.isBuffering,
                            progressFraction = progressFraction,
                            activeLyricLine = activeLyricLine,
                            downloadingIds = downloadingIds,
                            waveSettings = waveSettings,
                            tasteProfile = userSettings.tasteProfile,
                            isWaveGenerating = isWaveGenerating,
                            waveQueueTracks = waveQueueTracks,
                            tasteDnaSummary = tasteDnaSummary,
                            cloudAccountState = cloudAccountState,
                            resetTrigger = exploreResetTrigger,
                            onPlayTrack = { track, queue -> viewModel.playTrack(track, queue) },
                            onPlayPause = { viewModel.togglePlayPause() },
                            onPrevious = { viewModel.skipPrevious() },
                            onNext = { viewModel.skipNext() },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onDownloadOffline = { viewModel.downloadTrackForOffline(it) },
                            onAddToPlaylist = { trackToAddToPlaylist = it },
                            onOpenTrackMenu = { trackForActionsSheet = it },
                            onOpenWaveSettings = { showWaveSettingsSheet = true },
                            onOpenTasteSetup = { showTasteSetupSheet = true },
                            onOpenAccountAuth = { showAccountAuthSheet = true },
                            onOpenArtist = { artistName ->
                                viewModel.openArtistProfile(artistName)
                                navController.navigate(SlantDestinations.ROUTE_ARTIST) {
                                    launchSingleTop = true
                                }
                            },
                            onStartMyWave = {
                                viewModel.launchWave(waveSettings)
                                navController.navigate(SlantDestinations.ROUTE_PLAYER) {
                                    launchSingleTop = true
                                }
                            },
                            onResetWaveSettings = { viewModel.resetWaveSettings() },
                            onQuickGenreClick = { chipLabel ->
                                viewModel.toggleQuickWaveChip(chipLabel)
                            },
                            onGenerateSmartPlaylist = { preset ->
                                viewModel.generateSmartPlaylist(preset, playImmediately = true)
                                navController.navigate(SlantDestinations.ROUTE_PLAYER) {
                                    launchSingleTop = true
                                }
                            },
                            onOpenEqualizer = { showEqualizerSheet = true },
                            onOpenLyrics = { showLyricsSheet = true },
                            onOpenSettingsHub = {
                                navController.navigate(SlantDestinations.ROUTE_SETTINGS) {
                                    launchSingleTop = true
                                }
                            },
                            onRefresh = { viewModel.refreshExploreFeed() }
                        )
                    }

                    composable(SlantDestinations.ROUTE_SEARCH) {
                        SearchScreen(
                            query = searchQuery,
                            selectedFilter = selectedFilter,
                            selectedSortMode = searchSortMode,
                            searchResults = searchResults,
                            searchHistory = searchHistory,
                            isSearching = isSearching,
                            currentTrack = playbackState.currentTrack,
                            isPlaying = playbackState.isPlaying,
                            downloadingIds = downloadingIds,
                            resetTrigger = searchResetTrigger,
                            onQueryChange = { viewModel.onSearchQueryChanged(it) },
                            onSubmitSearch = { viewModel.submitSearchNow(it) },
                            onFilterChange = { viewModel.selectSourceFilter(it) },
                            onSortModeChange = { viewModel.selectSearchSortMode(it) },
                            onOpenArtistProfile = { artistName, preferredProvider ->
                                viewModel.openArtistProfile(artistName, preferredProvider)
                                navController.navigate(SlantDestinations.ROUTE_ARTIST) {
                                    launchSingleTop = true
                                }
                            },
                            onDeleteHistoryQuery = { viewModel.deleteSearchHistoryItem(it) },
                            onClearHistory = { viewModel.clearSearchHistory() },
                            onPlayTrack = { track, queue -> viewModel.playTrack(track, queue) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onDownloadOffline = { viewModel.downloadTrackForOffline(it) },
                            onAddToPlaylist = { trackToAddToPlaylist = it },
                            onOpenTrackMenu = { trackForActionsSheet = it }
                        )
                    }

                    composable(SlantDestinations.ROUTE_PLAYER) {
                        val currentTrack = playbackState.currentTrack
                        val similarForPlayer = remember(
                            currentTrack,
                            allCatalogPool,
                            userSettings.tasteProfile,
                            favoriteTracks,
                            playlistDomainTracks,
                            historyTracks,
                            likedArtistNames,
                            waveSettings
                        ) {
                            if (currentTrack == null) {
                                allCatalogPool.take(6)
                            } else {
                                val targetGenre = currentTrack.genre.lowercase()
                                val isTargetRu = com.example.domain.SlantWaveEngine.isRussianTrack(currentTrack)
                                val candidates = allCatalogPool.filter { it.id != currentTrack.id && !it.isLiveStream }
                                val dnaRanked = com.example.domain.SlantWaveEngine.rankAndFilterWaveCandidates(
                                    rawCandidates = candidates,
                                    settings = waveSettings,
                                    tasteProfile = userSettings.tasteProfile,
                                    favoriteTracks = favoriteTracks,
                                    playlistTracks = playlistDomainTracks,
                                    historyTracks = historyTracks,
                                    likedArtists = likedArtistNames + userSettings.tasteProfile.selectedArtists,
                                    dislikedTrackIds = userSettings.dislikedTrackIds
                                )
                                dnaRanked
                                    .sortedByDescending { candidate ->
                                        var bonus = 0
                                        if (com.example.domain.TrackDeduplicationEngine.isTrackByArtist(candidate, currentTrack.artist)) bonus += 75
                                        if (candidate.genre.lowercase() == targetGenre) bonus += 40
                                        if (com.example.domain.SlantWaveEngine.isRussianTrack(candidate) == isTargetRu) bonus += 20
                                        bonus
                                    }
                                    .take(6)
                            }
                        }
                        NowPlayingScreen(
                            playbackState = playbackState,
                            currentLyrics = currentLyrics,
                            artistBio = artistBio,
                            isDownloading = playbackState.currentTrack?.id in downloadingIds,
                            similarTracks = similarForPlayer,
                            playlists = playlists,
                            waveContextLabel = waveSettings.contextLabel,
                            resetTrigger = playerResetTrigger,
                            onPlayPause = { viewModel.togglePlayPause() },
                            onSkipNext = { viewModel.skipNext() },
                            onSkipPrevious = { viewModel.skipPrevious() },
                            onSeekTo = { viewModel.seekTo(it) },
                            onToggleShuffle = { viewModel.toggleShuffle() },
                            onCycleRepeat = { viewModel.cycleRepeatMode() },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onDownloadOffline = { viewModel.downloadTrackForOffline(it) },
                            onOpenLyricsSheet = { showLyricsSheet = true },
                            onOpenEqualizerSheet = { showEqualizerSheet = true },
                            onSelectStreamCandidate = { viewModel.switchStreamCandidate(it) },
                            onStartSleepTimer = { viewModel.startSleepTimer(it) },
                            onPlayTrack = { track, queue -> viewModel.playTrack(track, queue) },
                            onStartTrackWave = { track -> viewModel.launchTrackWave(track) },
                            onDislikeTrack = { track -> viewModel.dislikeTrack(track) },
                            onOpenTrackMenu = { track -> trackForActionsSheet = track },
                            onOpenArtist = { artistName ->
                                viewModel.openArtistProfile(artistName, playbackState.currentTrack?.primaryProvider)
                                navController.navigate(SlantDestinations.ROUTE_ARTIST) {
                                    launchSingleTop = true
                                }
                            },
                            onQuickAddToPlaylist = { playlistId, track ->
                                viewModel.addTrackToPlaylist(playlistId, track)
                            },
                            onOpenAddToPlaylistDialog = { track ->
                                trackToAddToPlaylist = track
                            },
                            onCollapsePlayer = {
                                navController.popBackStack()
                            }
                        )
                    }

                    composable(SlantDestinations.ROUTE_LIBRARY) {
                        LibraryScreen(
                            favoriteTracks = favoriteTracks,
                            offlineTracks = offlineTracks,
                            localTracks = localTracks,
                            historyTracks = historyTracks,
                            playlists = playlists,
                            likedArtistNames = likedArtistNames,
                            likedAlbumKeys = likedAlbumKeys,
                            currentTrack = playbackState.currentTrack,
                            isPlaying = playbackState.isPlaying,
                            downloadingIds = downloadingIds,
                            resetTrigger = libraryResetTrigger,
                            onPlayTrack = { track, queue -> viewModel.playTrack(track, queue) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onDownloadOffline = { viewModel.downloadTrackForOffline(it) },
                            onAddToPlaylist = { trackToAddToPlaylist = it },
                            onCreatePlaylist = { name, desc -> viewModel.createPlaylist(name, desc) },
                            onDeletePlaylist = { viewModel.deletePlaylist(it) },
                            onScanLocalMedia = { viewModel.scanLocalDeviceAudio() },
                            onOpenLikedScreen = {
                                navController.navigate(SlantDestinations.ROUTE_LIKED) {
                                    launchSingleTop = true
                                }
                            },
                            onOpenSubCategory = { subCat ->
                                navController.navigate(SlantDestinations.librarySubRoute(subCat)) {
                                    launchSingleTop = true
                                }
                            },
                            onOpenArtist = { artistName ->
                                viewModel.openArtistProfile(artistName)
                                navController.navigate(SlantDestinations.ROUTE_ARTIST) {
                                    launchSingleTop = true
                                }
                            },
                            onOpenTrackMenu = { trackForActionsSheet = it },
                            onStartCollectionWave = {
                                viewModel.launchCollectionWave()
                                navController.navigate(SlantDestinations.ROUTE_PLAYER) {
                                    launchSingleTop = true
                                }
                            },
                            onOpenWaveSettings = { showWaveSettingsSheet = true },
                            onOpenTasteSetup = { showTasteSetupSheet = true },
                            onNavigateToSearch = { q ->
                                if (q.isNotBlank()) {
                                    viewModel.submitSearchNow(q)
                                }
                                navController.navigate(SlantDestinations.ROUTE_SEARCH) {
                                    launchSingleTop = true
                                }
                            }
                        )
                    }

                    composable(SlantDestinations.ROUTE_LIBRARY_SUB) { backStackEntry ->
                        val catId = backStackEntry.arguments?.getString("category")
                        val subCat = LibrarySubCategory.fromId(catId)
                        LibrarySubCategoryScreen(
                            subCategory = subCat,
                            favoriteTracks = favoriteTracks,
                            offlineTracks = offlineTracks,
                            localTracks = localTracks,
                            historyTracks = historyTracks,
                            playlists = playlists,
                            likedArtistNames = likedArtistNames,
                            likedAlbumKeys = likedAlbumKeys,
                            currentTrack = playbackState.currentTrack,
                            isPlaying = playbackState.isPlaying,
                            downloadingIds = downloadingIds,
                            onBack = { navController.popBackStack() },
                            onPlayTrack = { track, queue -> viewModel.playTrack(track, queue) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onDownloadOffline = { viewModel.downloadTrackForOffline(it) },
                            onAddToPlaylist = { trackToAddToPlaylist = it },
                            onOpenTrackMenu = { trackForActionsSheet = it },
                            onCreatePlaylist = { name, desc -> viewModel.createPlaylist(name, desc) },
                            onDeletePlaylist = { viewModel.deletePlaylist(it) },
                            onScanLocalMedia = { viewModel.scanLocalDeviceAudio() },
                            onOpenArtist = { artistName ->
                                viewModel.openArtistProfile(artistName)
                                navController.navigate(SlantDestinations.ROUTE_ARTIST) {
                                    launchSingleTop = true
                                }
                            }
                        )
                    }

                    composable(SlantDestinations.ROUTE_LIKED) {
                        LikedTracksScreen(
                            likedTracks = favoriteTracks,
                            currentTrack = playbackState.currentTrack,
                            isPlaying = playbackState.isPlaying,
                            onBack = { navController.popBackStack() },
                            onPlayTrack = { track, queue -> viewModel.playTrack(track, queue) },
                            onDownloadOffline = { viewModel.downloadTrackForOffline(it) },
                            onOpenTrackMenu = { trackForActionsSheet = it },
                            onStartLikedWave = {
                                viewModel.launchLikedPlaylistWave()
                                navController.navigate(SlantDestinations.ROUTE_PLAYER) {
                                    launchSingleTop = true
                                }
                            }
                        )
                    }

                    composable(SlantDestinations.ROUTE_ARTIST) {
                        val profile = selectedArtistProfile ?: ArtistProfile(
                            name = playbackState.currentTrack?.artist ?: "",
                            avatarUrl = playbackState.currentTrack?.artworkUrl.orEmpty()
                        )
                        ArtistScreen(
                            artistProfile = profile,
                            currentTrack = playbackState.currentTrack,
                            isPlaying = playbackState.isPlaying,
                            onBack = { navController.popBackStack() },
                            onPlayTrack = { track, queue -> viewModel.playTrack(track, queue) },
                            onPlayTrailer = { track -> viewModel.playTrailerHighlight(track) },
                            onStartArtistWave = { artistName ->
                                viewModel.launchArtistWave(artistName)
                                navController.navigate(SlantDestinations.ROUTE_PLAYER) {
                                    launchSingleTop = true
                                }
                            },
                            onToggleLikeArtist = { viewModel.toggleLikeArtist(it) },
                            onToggleLikeAlbum = { artist, album -> viewModel.toggleLikeAlbum(artist, album) },
                            onSelectProvider = { viewModel.selectArtistProvider(it) },
                            onOpenTrackMenu = { trackForActionsSheet = it }
                        )
                    }

                    composable(SlantDestinations.ROUTE_SETTINGS) {
                        DeveloperSettingsScreen(
                            userSettings = userSettings,
                            providerStatuses = providerStatuses,
                            remoteConfig = remoteConfig,
                            exoCacheBytes = exoCacheBytes,
                            roomOfflineBytes = roomOfflineBytes,
                            cloudAccountState = cloudAccountState,
                            resetTrigger = settingsResetTrigger,
                            onOpenSubCategory = { subCat ->
                                navController.navigate(SlantDestinations.settingsSubRoute(subCat)) {
                                    launchSingleTop = true
                                }
                            },
                            onOpenEqualizer = { showEqualizerSheet = true },
                            onOpenTasteSetup = { showTasteSetupSheet = true },
                            onOpenAccountAuth = { showAccountAuthSheet = true },
                            onSyncAccountNow = { viewModel.syncAccountWithCloud() }
                        )
                    }

                    composable(SlantDestinations.ROUTE_SETTINGS_SUB) { backStackEntry ->
                        val catId = backStackEntry.arguments?.getString("category")
                        val subCat = SettingsSubCategory.fromId(catId)
                        SettingsSubCategoryScreen(
                            subCategory = subCat,
                            userSettings = userSettings,
                            providerStatuses = providerStatuses,
                            remoteConfig = remoteConfig,
                            exoCacheBytes = exoCacheBytes,
                            roomOfflineBytes = roomOfflineBytes,
                            currentTrack = playbackState.currentTrack,
                            isPlaying = playbackState.isPlaying,
                            activeLyricLine = activeLyricLine,
                            cloudAccountState = cloudAccountState,
                            onBack = { navController.popBackStack() },
                            onPlayPause = { viewModel.togglePlayPause() },
                            onPrevious = { viewModel.skipPrevious() },
                            onNext = { viewModel.skipNext() },
                            onToggleDarkTheme = { viewModel.setDarkTheme(it) },
                            onToggleDynamicColor = { viewModel.setDynamicColor(it) },
                            onToggleProvider = { prov, enabled ->
                                viewModel.toggleSourceProvider(prov, enabled)
                            },
                            onPingAllProviders = { viewModel.pingAllProvidersNow() },
                            onOpenEqualizer = { showEqualizerSheet = true },
                            onMaxCacheSizeChange = { viewModel.setMaxCacheSizeMb(it) },
                            onClearAllCaches = { viewModel.clearAllCaches() },
                            onSyncRemoteConfig = { viewModel.syncFirebaseRemoteConfig() },
                            onOverrideBitrate = { viewModel.setRemoteBitrateOverride(it) },
                            onOpenTasteSetup = { showTasteSetupSheet = true },
                            onOpenAccountAuth = { showAccountAuthSheet = true },
                            onSyncAccountNow = { viewModel.syncAccountWithCloud() }
                        )
                    }
                }
            }
        }
    }

    if (showWaveSettingsSheet) {
        WaveSettingsBottomSheet(
            waveSettings = waveSettings,
            onUpdateSettings = { viewModel.updateWaveSettings(it) },
            onLaunchWave = { settings ->
                viewModel.launchWave(settings)
                navController.navigate(SlantDestinations.ROUTE_PLAYER) {
                    launchSingleTop = true
                }
            },
            onReset = { viewModel.resetWaveSettings() },
            onOpenTasteSetup = { showTasteSetupSheet = true },
            onDismiss = { showWaveSettingsSheet = false }
        )
    }

    if (showTasteSetupSheet) {
        TasteOnboardingSheet(
            currentProfile = userSettings.tasteProfile,
            dnaSummary = tasteDnaSummary,
            favoriteTracks = favoriteTracks,
            playlistTracks = playlistDomainTracks,
            historyTracks = historyTracks,
            artistSuggestions = artistAutocompleteSuggestions,
            isArtistSuggestLoading = isArtistSuggestLoading,
            artistArtworks = artistArtworksMap,
            onArtistQueryChange = { viewModel.onArtistPreferenceQueryChanged(it) },
            onClearArtistSuggestions = { viewModel.onArtistPreferenceQueryChanged("") },
            onSaveProfile = { newProfile, launchWaveNow ->
                viewModel.saveUserTasteProfile(newProfile, launchWaveImmediately = launchWaveNow)
                showTasteSetupSheet = false
                if (launchWaveNow) {
                    navController.navigate(SlantDestinations.ROUTE_PLAYER) {
                        launchSingleTop = true
                    }
                }
            },
            onDismiss = { showTasteSetupSheet = false }
        )
    }

    trackForActionsSheet?.let { targetTrack ->
        TrackActionsBottomSheet(
            track = targetTrack,
            onDismiss = { trackForActionsSheet = null },
            onStartTrackWave = { track ->
                viewModel.launchTrackWave(track)
                navController.navigate(SlantDestinations.ROUTE_PLAYER) {
                    launchSingleTop = true
                }
            },
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onDownloadOffline = { viewModel.downloadTrackForOffline(it) },
            onPlayTrailerHighlight = { viewModel.playTrailerHighlight(it) },
            onAddToPlaylist = { trackToAddToPlaylist = it },
            onRemoveFromQueue = { viewModel.removeTrackFromQueue(it) },
            onDislikeTrack = { viewModel.dislikeTrack(it) },
            onOpenArtist = { artistName ->
                viewModel.openArtistProfile(artistName, targetTrack.primaryProvider)
                navController.navigate(SlantDestinations.ROUTE_ARTIST) {
                    launchSingleTop = true
                }
            }
        )
    }

    val currentTrackForLyrics = playbackState.currentTrack
        ?: exploreFeed.trendingFullStreams.firstOrNull()
    if (showLyricsSheet && currentTrackForLyrics != null) {
        LyricsBottomSheet(
            track = currentTrackForLyrics,
            lyrics = currentLyrics,
            isLoading = isLyricsLoading,
            positionMs = playbackState.positionMs,
            onSeekToMs = { viewModel.seekTo(it) },
            onDismiss = { showLyricsSheet = false }
        )
    }

    if (showEqualizerSheet) {
        EqualizerBottomSheet(
            dspState = dspState,
            onToggleEnabled = { viewModel.setDspEnabled(it) },
            onSelectPreset = { viewModel.selectEqualizerPreset(it) },
            onBandGainChange = { band, gain -> viewModel.setEqualizerBandGain(band, gain) },
            onBassBoostChange = { viewModel.setBassBoostPercent(it) },
            onVirtualizerChange = { viewModel.setVirtualizerPercent(it) },
            onLoudnessChange = { viewModel.setLoudnessGainDb(it) },
            onDismiss = { showEqualizerSheet = false }
        )
    }

    if (showAccountAuthSheet) {
        com.example.ui.components.AccountAuthSheet(
            accountState = cloudAccountState,
            onSignInWithGoogle = { viewModel.signInWithGoogle(context) },
            onSignInOrRegisterWithEmail = { email, password, displayName, isRegister ->
                viewModel.signInOrRegisterWithEmail(email, password, displayName, isRegister)
            },
            onSyncNow = { viewModel.syncAccountWithCloud() },
            onSignOut = { viewModel.signOutAccount() },
            onDismiss = { showAccountAuthSheet = false }
        )
    }

    trackToAddToPlaylist?.let { targetTrack ->
        AddToPlaylistDialog(
            track = targetTrack,
            playlists = playlists,
            onSelectPlaylist = { playlistId ->
                viewModel.addTrackToPlaylist(playlistId, targetTrack)
            },
            onCreateAndAdd = { newName ->
                viewModel.createPlaylist(newName, "SlantMuz Mix")
            },
            onDismiss = { trackToAddToPlaylist = null }
        )
    }
}
