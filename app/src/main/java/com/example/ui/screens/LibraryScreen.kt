package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.PlaylistWithTracks
import com.example.domain.AlbumSummary
import com.example.domain.LibrarySubCategory
import com.example.domain.Track
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.MarqueeText
import com.example.ui.components.TrackItemCard
import com.example.ui.components.WaveBannerCard
import com.example.ui.theme.SlantCyan
import com.example.ui.theme.SlantEmerald
import com.example.ui.theme.SlantMagenta
import com.example.ui.theme.SlantViolet

@Composable
fun LibraryScreen(
    favoriteTracks: List<Track>,
    offlineTracks: List<Track>,
    localTracks: List<Track>,
    historyTracks: List<Track>,
    playlists: List<PlaylistWithTracks>,
    likedArtistNames: Set<String> = emptySet(),
    currentTrack: Track?,
    isPlaying: Boolean,
    downloadingIds: Set<String>,
    resetTrigger: Int = 0,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onDownloadOffline: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onCreatePlaylist: (String, String) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onScanLocalMedia: () -> Unit,
    onOpenLikedScreen: () -> Unit = {},
    onOpenSubCategory: (LibrarySubCategory) -> Unit = {},
    onOpenArtist: (String) -> Unit = {},
    onOpenTrackMenu: (Track) -> Unit = {},
    onStartCollectionWave: () -> Unit = {},
    onOpenWaveSettings: () -> Unit = {},
    onNavigateToSearch: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    LaunchedEffect(resetTrigger) {
        if (resetTrigger > 0) {
            listState.animateScrollToItem(0)
        }
    }

    // Build real user artists from liked artists, favorite tracks, and listening history only
    val userCollectionTracks = remember(favoriteTracks, offlineTracks, localTracks, historyTracks) {
        (favoriteTracks + offlineTracks + localTracks + historyTracks).distinctBy { it.id }
    }

    val favoriteArtists = remember(likedArtistNames, favoriteTracks, historyTracks) {
        val fromTracks = (favoriteTracks + historyTracks)
            .groupBy { it.artist }
            .map { (artistName, tracks) ->
                artistName to (tracks.firstOrNull { it.artworkUrl.isNotBlank() }?.artworkUrl.orEmpty())
            }
        val explicitLiked = likedArtistNames.map { name ->
            val art = (favoriteTracks + historyTracks)
                .firstOrNull { it.artist.equals(name, ignoreCase = true) && it.artworkUrl.isNotBlank() }
                ?.artworkUrl.orEmpty()
            name to art
        }
        (explicitLiked + fromTracks).distinctBy { it.first.lowercase() }.take(12)
    }

    val userAlbums = remember(userCollectionTracks) {
        userCollectionTracks
            .filter { it.album.isNotBlank() || it.artist.isNotBlank() }
            .groupBy { it.album.ifBlank { "${it.title} — Single" } }
            .entries
            .mapIndexed { index, (albumTitle, tracks) ->
                AlbumSummary(
                    id = "col_alb_$index",
                    title = albumTitle,
                    artist = tracks.first().artist,
                    artworkUrl = tracks.firstOrNull { it.artworkUrl.isNotBlank() }?.artworkUrl.orEmpty(),
                    releaseYear = tracks.firstOrNull()?.releaseYear.orEmpty(),
                    tracks = tracks
                )
            }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("library_screen")
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        // Top Bar: "Коллекция" | Wave Settings | Search
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Коллекция",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onOpenWaveSettings,
                    modifier = Modifier.testTag("collection_wave_settings_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Настроить Мою волну",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(
                    onClick = { onNavigateToSearch("") },
                    modifier = Modifier.testTag("collection_search_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Поиск",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Quick horizontal chips that open dedicated subcategory windows
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 6.dp)
        ) {
            item {
                Surface(
                    onClick = onOpenLikedScreen,
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.testTag("library_tab_0")
                ) {
                    Text(
                        text = "Мне нравится (${favoriteTracks.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
            item {
                CategoryQuickChip(
                    label = "Плейлисты (${playlists.size})",
                    testTag = "library_tab_1",
                    onClick = { onOpenSubCategory(LibrarySubCategory.PLAYLISTS) }
                )
            }
            item {
                CategoryQuickChip(
                    label = "Скачанное (${offlineTracks.size})",
                    testTag = "library_tab_2",
                    onClick = { onOpenSubCategory(LibrarySubCategory.OFFLINE) }
                )
            }
            item {
                CategoryQuickChip(
                    label = "На устройстве (${localTracks.size})",
                    testTag = "library_tab_3",
                    onClick = { onOpenSubCategory(LibrarySubCategory.LOCAL_FILES) }
                )
            }
            item {
                CategoryQuickChip(
                    label = "История (${historyTracks.size})",
                    testTag = "library_tab_4",
                    onClick = { onOpenSubCategory(LibrarySubCategory.HISTORY) }
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. "Моя волна по коллекции" Banner
            item {
                WaveBannerCard(
                    subtitle = "Моя волна по коллекции",
                    title = "Коллекция",
                    gradientColors = listOf(
                        Color(0xFFC026D3),
                        Color(0xFFEC4899),
                        Color(0xFF9333EA)
                    ),
                    onClick = onStartCollectionWave
                )
            }

            // 2. "Мне нравится >" Section (opens dedicated LikedTracksScreen window, shows only real favoriteTracks)
            item {
                LiquidGlassCard(
                    cornerRadius = 22.dp,
                    tintColor = SlantMagenta,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(onClick = onOpenLikedScreen)
                                .padding(vertical = 4.dp)
                                .testTag("open_liked_tracks_header"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Canvas(modifier = Modifier.size(52.dp)) {
                                val w = size.width
                                val h = size.height
                                val heart = Path().apply {
                                    moveTo(w * 0.5f, h * 0.88f)
                                    cubicTo(w * 0.10f, h * 0.62f, w * 0.04f, h * 0.24f, w * 0.28f, h * 0.14f)
                                    cubicTo(w * 0.42f, h * 0.08f, w * 0.5f, h * 0.22f, w * 0.5f, h * 0.28f)
                                    cubicTo(w * 0.5f, h * 0.22f, w * 0.58f, h * 0.08f, w * 0.72f, h * 0.14f)
                                    cubicTo(w * 0.96f, h * 0.24f, w * 0.90f, h * 0.62f, w * 0.5f, h * 0.88f)
                                    close()
                                }
                                drawPath(
                                    path = heart,
                                    brush = Brush.linearGradient(
                                        listOf(Color(0xFFFFB5A7), Color(0xFFFF4D6D), Color(0xFF9B002C))
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Мне нравится",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = if (favoriteTracks.isEmpty()) {
                                        "Пока нет треков • Нажмите ♥ чтобы сохранить"
                                    } else {
                                        "${favoriteTracks.size} треков • Открыть в отдельном окне"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (favoriteTracks.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            favoriteTracks.take(3).forEach { track ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onPlayTrack(track, favoriteTracks) }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF1F2433)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (track.artworkUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = track.artworkUrl,
                                                contentDescription = track.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.MusicNote,
                                                contentDescription = null,
                                                tint = SlantCyan
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        MarqueeText(
                                            text = track.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = if (currentTrack?.id == track.id) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = track.artist,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    IconButton(
                                        onClick = { onDownloadOffline(track) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (track.isCachedOffline) Icons.Default.CloudDone else Icons.Default.Download,
                                            contentDescription = "Скачать",
                                            tint = SlantEmerald,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { onOpenTrackMenu(track) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Меню трека",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Sorted Subcategories of User Collection (each opens in its own dedicated window)
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Разделы медиатеки",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )

                        TextButton(
                            onClick = { showCreatePlaylistDialog = true },
                            modifier = Modifier.testTag("create_playlist_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Новый плейлист")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Group 1: Плейлисты и Альбомы
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CollectionGridTile(
                            title = "Плейлисты",
                            subtitle = if (playlists.isNotEmpty()) {
                                "${playlists.size} плейлистов"
                            } else {
                                "Создать подборку"
                            },
                            icon = Icons.AutoMirrored.Filled.QueueMusic,
                            iconBgColors = listOf(Color(0xFFFF4D6D), Color(0xFF9B002C)),
                            onClick = { onOpenSubCategory(LibrarySubCategory.PLAYLISTS) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("grid_tile_playlists")
                        )

                        CollectionGridTile(
                            title = "Альбомы",
                            subtitle = if (userAlbums.isNotEmpty()) {
                                "${userAlbums.size} релизов"
                            } else {
                                "Альбомы из коллекции"
                            },
                            icon = Icons.Default.Album,
                            iconBgColors = listOf(Color(0xFF6366F1), Color(0xFF312E81)),
                            onClick = { onOpenSubCategory(LibrarySubCategory.ALBUMS) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("grid_tile_albums")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Group 2: Оффлайн и Локальные файлы
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CollectionGridTile(
                            title = "Скачанное",
                            subtitle = "${offlineTracks.size} треков оффлайн",
                            icon = Icons.Default.CloudDone,
                            iconBgColors = listOf(Color(0xFF0EA5E9), Color(0xFF0369A1)),
                            onClick = { onOpenSubCategory(LibrarySubCategory.OFFLINE) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("grid_tile_downloaded")
                        )

                        CollectionGridTile(
                            title = "На устройстве",
                            subtitle = "${localTracks.size} локальных файлов",
                            icon = Icons.Default.FolderOpen,
                            iconBgColors = listOf(Color(0xFF10B981), Color(0xFF047857)),
                            onClick = { onOpenSubCategory(LibrarySubCategory.LOCAL_FILES) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("grid_tile_local_files")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Group 3: Исполнители и История
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CollectionGridTile(
                            title = "Исполнители",
                            subtitle = "${favoriteArtists.size} артистов",
                            icon = Icons.Default.Person,
                            iconBgColors = listOf(Color(0xFF8B5CF6), Color(0xFF4C1D95)),
                            onClick = { onOpenSubCategory(LibrarySubCategory.ARTISTS) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("grid_tile_artists")
                        )

                        CollectionGridTile(
                            title = "История",
                            subtitle = "${historyTracks.size} прослушано",
                            icon = Icons.Default.History,
                            iconBgColors = listOf(Color(0xFFF59E0B), Color(0xFFB45309)),
                            onClick = { onOpenSubCategory(LibrarySubCategory.HISTORY) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("grid_tile_history")
                        )
                    }
                }
            }

            // 4. "Любимые исполнители >" Carousel (only shown if user has real liked/played artists)
            if (favoriteArtists.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenSubCategory(LibrarySubCategory.ARTISTS) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Ваши исполнители",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(favoriteArtists, key = { it.first }) { (artistName, avatarUrl) ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .width(120.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { onOpenArtist(artistName) }
                                        .padding(4.dp)
                                        .testTag("fav_artist_circle_$artistName")
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(108.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(Color(0xFF334155), Color(0xFF1E1B4B))
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (avatarUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = avatarUrl,
                                                contentDescription = artistName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(44.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    MarqueeText(
                                        text = artistName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onConfirm = { name, desc ->
                onCreatePlaylist(name, desc)
                showCreatePlaylistDialog = false
            },
            onDismiss = { showCreatePlaylistDialog = false }
        )
    }
}

/**
 * Dedicated separate window for each Collection subcategory:
 * - PLAYLISTS
 * - OFFLINE
 * - LOCAL_FILES
 * - ALBUMS
 * - ARTISTS
 * - HISTORY
 */
@Composable
fun LibrarySubCategoryScreen(
    subCategory: LibrarySubCategory,
    favoriteTracks: List<Track>,
    offlineTracks: List<Track>,
    localTracks: List<Track>,
    historyTracks: List<Track>,
    playlists: List<PlaylistWithTracks>,
    likedArtistNames: Set<String>,
    currentTrack: Track?,
    isPlaying: Boolean,
    downloadingIds: Set<String>,
    onBack: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onDownloadOffline: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onOpenTrackMenu: (Track) -> Unit,
    onCreatePlaylist: (String, String) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onScanLocalMedia: () -> Unit,
    onOpenArtist: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    val accentColor = MaterialTheme.colorScheme.primary

    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        onScanLocalMedia()
    }

    val userCollectionTracks = remember(favoriteTracks, offlineTracks, localTracks, historyTracks) {
        (favoriteTracks + offlineTracks + localTracks + historyTracks).distinctBy { it.id }
    }

    val userAlbums = remember(userCollectionTracks) {
        userCollectionTracks
            .filter { it.album.isNotBlank() || it.artist.isNotBlank() }
            .groupBy { it.album.ifBlank { "${it.title} — Single" } }
            .entries
            .mapIndexed { index, (albumTitle, tracks) ->
                AlbumSummary(
                    id = "col_alb_$index",
                    title = albumTitle,
                    artist = tracks.first().artist,
                    artworkUrl = tracks.firstOrNull { it.artworkUrl.isNotBlank() }?.artworkUrl.orEmpty(),
                    releaseYear = tracks.firstOrNull()?.releaseYear.orEmpty(),
                    tracks = tracks
                )
            }
    }

    val userArtists = remember(likedArtistNames, favoriteTracks, historyTracks, localTracks) {
        val fromTracks = (favoriteTracks + historyTracks + localTracks)
            .groupBy { it.artist }
            .map { (artistName, tracks) ->
                Triple(
                    artistName,
                    tracks.firstOrNull { it.artworkUrl.isNotBlank() }?.artworkUrl.orEmpty(),
                    tracks.size
                )
            }
        val explicitLiked = likedArtistNames.map { name ->
            val matching = (favoriteTracks + historyTracks).filter {
                it.artist.equals(name, ignoreCase = true)
            }
            Triple(
                name,
                matching.firstOrNull { it.artworkUrl.isNotBlank() }?.artworkUrl.orEmpty(),
                matching.size
            )
        }
        (explicitLiked + fromTracks).distinctBy { it.first.lowercase() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("library_subcategory_screen_${subCategory.id}")
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        // Dedicated Window Header with Back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("library_sub_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад в Коллекцию",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = subCategory.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subCategory.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (subCategory == LibrarySubCategory.PLAYLISTS) {
                FilledTonalButton(
                    onClick = { showCreatePlaylistDialog = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Создать")
                }
            } else if (subCategory == LibrarySubCategory.LOCAL_FILES) {
                FilledTonalButton(
                    onClick = { permissionLauncher.launch(audioPermission) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("scan_local_audio_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Сканировать")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (subCategory) {
            LibrarySubCategory.PLAYLISTS -> {
                PlaylistsSection(
                    playlists = playlists,
                    onCreateClick = { showCreatePlaylistDialog = true },
                    onDeletePlaylist = onDeletePlaylist,
                    onPlayTrack = onPlayTrack
                )
            }

            LibrarySubCategory.OFFLINE -> {
                TrackCollectionList(
                    tracks = offlineTracks,
                    emptyIcon = Icons.Default.CloudDone,
                    emptyTitle = "Нет скачанных треков",
                    emptySubtitle = "Нажмите на иконку загрузки у любого трека для сохранения в оффлайн-кэш",
                    currentTrack = currentTrack,
                    isPlaying = isPlaying,
                    downloadingIds = downloadingIds,
                    onPlayTrack = onPlayTrack,
                    onToggleFavorite = onToggleFavorite,
                    onDownloadOffline = onDownloadOffline,
                    onAddToPlaylist = onAddToPlaylist,
                    onOpenTrackMenu = onOpenTrackMenu
                )
            }

            LibrarySubCategory.LOCAL_FILES -> {
                TrackCollectionList(
                    tracks = localTracks,
                    emptyIcon = Icons.Default.FolderOpen,
                    emptyTitle = "Локальные аудиофайлы не найдены",
                    emptySubtitle = "Нажмите «Сканировать» сверху для поиска музыки в памяти смартфона",
                    currentTrack = currentTrack,
                    isPlaying = isPlaying,
                    downloadingIds = downloadingIds,
                    onPlayTrack = onPlayTrack,
                    onToggleFavorite = onToggleFavorite,
                    onDownloadOffline = onDownloadOffline,
                    onAddToPlaylist = onAddToPlaylist,
                    onOpenTrackMenu = onOpenTrackMenu
                )
            }

            LibrarySubCategory.HISTORY -> {
                TrackCollectionList(
                    tracks = historyTracks,
                    emptyIcon = Icons.Default.History,
                    emptyTitle = "История прослушиваний пуста",
                    emptySubtitle = "Все прослушанные вами треки автоматически сохраняются здесь",
                    currentTrack = currentTrack,
                    isPlaying = isPlaying,
                    downloadingIds = downloadingIds,
                    onPlayTrack = onPlayTrack,
                    onToggleFavorite = onToggleFavorite,
                    onDownloadOffline = onDownloadOffline,
                    onAddToPlaylist = onAddToPlaylist,
                    onOpenTrackMenu = onOpenTrackMenu
                )
            }

            LibrarySubCategory.ALBUMS -> {
                if (userAlbums.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Album,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "В коллекции пока нет альбомов",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Добавляйте треки в «Мне нравится» или слушайте музыку — альбомы сгруппируются автоматически",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 128.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(userAlbums, key = { it.id }) { album ->
                            LiquidGlassCard(
                                cornerRadius = 18.dp,
                                tintColor = SlantViolet,
                                onClick = {
                                    val first = album.tracks.firstOrNull()
                                    if (first != null) {
                                        onPlayTrack(first, album.tracks)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFF1F2433)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (album.artworkUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = album.artworkUrl,
                                                contentDescription = album.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Album,
                                                contentDescription = null,
                                                tint = SlantCyan
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        MarqueeText(
                                            text = album.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${album.artist} • ${album.tracks.size} треков",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Слушать альбом",
                                        tint = accentColor
                                    )
                                }
                            }
                        }
                    }
                }
            }

            LibrarySubCategory.ARTISTS -> {
                if (userArtists.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Список исполнителей пуст",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Отмечайте любимых артистов или треки, чтобы они появились в этом окне",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 128.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(userArtists, key = { it.first }) { (artistName, avatarUrl, count) ->
                            LiquidGlassCard(
                                cornerRadius = 18.dp,
                                tintColor = SlantCyan,
                                onClick = { onOpenArtist(artistName) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF1F2433)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (avatarUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = avatarUrl,
                                                contentDescription = artistName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = null,
                                                tint = Color.White
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        MarqueeText(
                                            text = artistName,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (count > 0) "$count треков в медиатеке" else "В избранном",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onConfirm = { name, desc ->
                onCreatePlaylist(name, desc)
                showCreatePlaylistDialog = false
            },
            onDismiss = { showCreatePlaylistDialog = false }
        )
    }
}

@Composable
private fun CategoryQuickChip(
    label: String,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.testTag(testTag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun CollectionGridTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBgColors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LiquidGlassCard(
        cornerRadius = 18.dp,
        tintColor = iconBgColors.first(),
        onClick = onClick,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(iconBgColors)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TrackCollectionList(
    tracks: List<Track>,
    emptyIcon: ImageVector,
    emptyTitle: String,
    emptySubtitle: String,
    currentTrack: Track?,
    isPlaying: Boolean,
    downloadingIds: Set<String>,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onDownloadOffline: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onOpenTrackMenu: (Track) -> Unit = {}
) {
    val accentColor = MaterialTheme.colorScheme.primary
    if (tracks.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = emptyIcon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = emptyTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = emptySubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(tracks, key = { it.id }) { track ->
                TrackItemCard(
                    track = track,
                    isCurrentTrack = currentTrack?.id == track.id,
                    isPlaying = isPlaying,
                    isDownloading = track.id in downloadingIds,
                    onTrackClick = { onPlayTrack(track, tracks) },
                    onFavoriteClick = { onToggleFavorite(track) },
                    onDownloadClick = { onDownloadOffline(track) },
                    onAddToPlaylistClick = { onAddToPlaylist(track) },
                    onMoreClick = { onOpenTrackMenu(track) }
                )
            }
        }
    }
}

@Composable
private fun PlaylistsSection(
    playlists: List<PlaylistWithTracks>,
    onCreateClick: () -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit
) {
    val accentColor = MaterialTheme.colorScheme.primary
    if (playlists.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Создайте свой первый плейлист",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(onClick = onCreateClick) {
                    Text("Новый плейлист", maxLines = 1, softWrap = false)
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(playlists, key = { it.playlist.playlistId }) { item ->
                val domainTracks = item.tracks.map { it.toDomain() }
                LiquidGlassCard(
                    cornerRadius = 18.dp,
                    tintColor = SlantViolet,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                MarqueeText(
                                    text = item.playlist.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${domainTracks.size} треков" +
                                        if (item.playlist.description.isNotBlank()) " • ${item.playlist.description}" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (domainTracks.isNotEmpty()) {
                                IconButton(
                                    onClick = { onPlayTrack(domainTracks.first(), domainTracks) },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Воспроизвести плейлист",
                                        tint = accentColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { onDeletePlaylist(item.playlist.playlistId) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Удалить плейлист",
                                    tint = Color(0xFFE11D48),
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }

                        if (domainTracks.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            domainTracks.take(4).forEach { t ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onPlayTrack(t, domainTracks) }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    MarqueeText(
                                        text = "• ${t.title} — ${t.artist}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = t.formattedDuration,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accentColor,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CreatePlaylistDialog(
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Создать плейлист",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название плейлиста", maxLines = 1) },
                    singleLine = true,
                    modifier = Modifier.testTag("playlist_name_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Описание (необязательно)", maxLines = 1) },
                    singleLine = true,
                    modifier = Modifier.testTag("playlist_desc_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name, description) },
                modifier = Modifier.testTag("confirm_create_playlist_btn")
            ) {
                Text("Создать", maxLines = 1)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", maxLines = 1)
            }
        }
    )
}

@Composable
fun AddToPlaylistDialog(
    track: Track,
    playlists: List<PlaylistWithTracks>,
    onSelectPlaylist: (Long) -> Unit,
    onCreateAndAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newPlaylistName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            MarqueeText(
                text = "Добавить «${track.title}» в плейлист",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                playlists.forEach { p ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onSelectPlaylist(p.playlist.playlistId)
                                onDismiss()
                            }
                    ) {
                        MarqueeText(
                            text = "${p.playlist.name} (${p.tracks.size} треков)",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Или создайте новый плейлист", maxLines = 1) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            if (newPlaylistName.isNotBlank()) {
                Button(
                    onClick = {
                        onCreateAndAdd(newPlaylistName)
                        onDismiss()
                    }
                ) {
                    Text("Создать и добавить", maxLines = 1)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть", maxLines = 1)
            }
        }
    )
}
