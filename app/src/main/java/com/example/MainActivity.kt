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
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Widgets
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
import com.example.domain.LrcParser
import com.example.domain.Track
import com.example.ui.components.EqualizerBottomSheet
import com.example.ui.components.LyricsBottomSheet
import com.example.ui.components.MiniPlayerBar
import com.example.ui.components.PastelAuroraBackground
import com.example.ui.screens.AddToPlaylistDialog
import com.example.ui.screens.DeveloperSettingsScreen
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.theme.SlantMuzTheme
import com.example.viewmodel.PlayerViewModel
import com.example.widget.SlantMuzWidgetUpdater

object SlantDestinations {
    const val ROUTE_EXPLORE = "explore"
    const val ROUTE_SEARCH = "search"
    const val ROUTE_PLAYER = "player"
    const val ROUTE_LIBRARY = "library"
    const val ROUTE_SETTINGS = "settings"
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

    var showLyricsSheet by remember { mutableStateOf(false) }
    var showEqualizerSheet by remember { mutableStateOf(false) }
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
                label = "Обзор",
                selectedIcon = Icons.Filled.Explore,
                unselectedIcon = Icons.Outlined.Explore,
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
                label = "Медиатека",
                selectedIcon = Icons.Filled.LibraryMusic,
                unselectedIcon = Icons.Outlined.LibraryMusic,
                testTag = "nav_tab_library"
            ),
            BottomNavItem(
                route = SlantDestinations.ROUTE_SETTINGS,
                label = "Виджеты",
                selectedIcon = Icons.Filled.Widgets,
                unselectedIcon = Icons.Outlined.Widgets,
                testTag = "nav_tab_settings"
            )
        )
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
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                        tonalElevation = 0.dp,
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(24.dp))
                    ) {
                        navItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (currentRoute != item.route) {
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
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
                            onPlayTrack = { track, queue -> viewModel.playTrack(track, queue) },
                            onPlayPause = { viewModel.togglePlayPause() },
                            onPrevious = { viewModel.skipPrevious() },
                            onNext = { viewModel.skipNext() },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onDownloadOffline = { viewModel.downloadTrackForOffline(it) },
                            onAddToPlaylist = { trackToAddToPlaylist = it },
                            onQuickGenreClick = { genre ->
                                viewModel.submitSearchNow(genre)
                                navController.navigate(SlantDestinations.ROUTE_SEARCH) {
                                    launchSingleTop = true
                                }
                            },
                            onOpenEqualizer = { showEqualizerSheet = true },
                            onOpenLyrics = { showLyricsSheet = true },
                            onOpenWidgetsHub = {
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
                            searchResults = searchResults,
                            searchHistory = searchHistory,
                            isSearching = isSearching,
                            currentTrack = playbackState.currentTrack,
                            isPlaying = playbackState.isPlaying,
                            downloadingIds = downloadingIds,
                            onQueryChange = { viewModel.onSearchQueryChanged(it) },
                            onSubmitSearch = { viewModel.submitSearchNow(it) },
                            onFilterChange = { viewModel.selectSourceFilter(it) },
                            onDeleteHistoryQuery = { viewModel.deleteSearchHistoryItem(it) },
                            onClearHistory = { viewModel.clearSearchHistory() },
                            onPlayTrack = { track, queue -> viewModel.playTrack(track, queue) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onDownloadOffline = { viewModel.downloadTrackForOffline(it) },
                            onAddToPlaylist = { trackToAddToPlaylist = it }
                        )
                    }

                    composable(SlantDestinations.ROUTE_PLAYER) {
                        NowPlayingScreen(
                            playbackState = playbackState,
                            currentLyrics = currentLyrics,
                            artistBio = artistBio,
                            isDownloading = playbackState.currentTrack?.id in downloadingIds,
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
                            onStartSleepTimer = { viewModel.startSleepTimer(it) }
                        )
                    }

                    composable(SlantDestinations.ROUTE_LIBRARY) {
                        LibraryScreen(
                            favoriteTracks = favoriteTracks,
                            offlineTracks = offlineTracks,
                            localTracks = localTracks,
                            historyTracks = historyTracks,
                            playlists = playlists,
                            currentTrack = playbackState.currentTrack,
                            isPlaying = playbackState.isPlaying,
                            downloadingIds = downloadingIds,
                            onPlayTrack = { track, queue -> viewModel.playTrack(track, queue) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onDownloadOffline = { viewModel.downloadTrackForOffline(it) },
                            onAddToPlaylist = { trackToAddToPlaylist = it },
                            onCreatePlaylist = { name, desc -> viewModel.createPlaylist(name, desc) },
                            onDeletePlaylist = { viewModel.deletePlaylist(it) },
                            onScanLocalMedia = { viewModel.scanLocalDeviceAudio() }
                        )
                    }

                    composable(SlantDestinations.ROUTE_SETTINGS) {
                        DeveloperSettingsScreen(
                            userSettings = userSettings,
                            providerStatuses = providerStatuses,
                            remoteConfig = remoteConfig,
                            exoCacheBytes = exoCacheBytes,
                            roomOfflineBytes = roomOfflineBytes,
                            currentTrack = playbackState.currentTrack,
                            isPlaying = playbackState.isPlaying,
                            activeLyricLine = activeLyricLine,
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
                            onOverrideBitrate = { viewModel.setRemoteBitrateOverride(it) }
                        )
                    }
                }
            }
        }
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

    trackToAddToPlaylist?.let { targetTrack ->
        AddToPlaylistDialog(
            track = targetTrack,
            playlists = playlists,
            onSelectPlaylist = { playlistId ->
                viewModel.addTrackToPlaylist(playlistId, targetTrack)
            },
            onCreateAndAdd = { newName ->
                viewModel.createPlaylist(newName, "SlantMuz Pastel Mix")
            },
            onDismiss = { trackToAddToPlaylist = null }
        )
    }
}
