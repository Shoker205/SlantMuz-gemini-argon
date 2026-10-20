package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.domain.AlbumSummary
import com.example.domain.ArtistProfile
import com.example.domain.SourceProvider
import com.example.domain.Track
import com.example.ui.components.MarqueeText
import com.example.ui.components.ProviderBadgeChip
import com.example.ui.components.WaveBannerCard
import com.example.ui.theme.SlantCyan
import com.example.ui.theme.SlantMagenta
import com.example.ui.theme.SlantObsidian
import com.example.ui.theme.SlantViolet

@Composable
fun ArtistScreen(
    artistProfile: ArtistProfile,
    currentTrack: Track?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayTrailer: (Track) -> Unit,
    onStartArtistWave: (String) -> Unit,
    onToggleLikeArtist: (String) -> Unit,
    onSelectProvider: (SourceProvider) -> Unit = {},
    onOpenTrackMenu: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    var showSearchField by remember { mutableStateOf(false) }
    var filterQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf("По умолчанию") }
    var showBioDialog by remember { mutableStateOf(false) }

    val displayedTracks = remember(artistProfile.popularTracks, filterQuery, sortMode) {
        val filtered = if (filterQuery.isBlank()) {
            artistProfile.popularTracks
        } else {
            artistProfile.popularTracks.filter {
                it.title.contains(filterQuery, ignoreCase = true) ||
                    it.album.contains(filterQuery, ignoreCase = true)
            }
        }
        when (sortMode) {
            "По длительности" -> filtered.sortedByDescending { it.durationSec }
            "А–Я" -> filtered.sortedBy { it.title.lowercase() }
            else -> filtered
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SlantObsidian)
            .testTag("artist_screen"),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        // 1. Hero Header + 4 Circular Action Buttons
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(410.dp)
            ) {
                if (artistProfile.avatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = artistProfile.avatarUrl,
                        contentDescription = artistProfile.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF334155), Color(0xFF1E1B4B), SlantObsidian)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.size(120.dp)
                        )
                    }
                }

                // Gradient vignette fading smoothly into SlantObsidian
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.30f),
                                    Color.Transparent,
                                    SlantObsidian.copy(alpha = 0.86f),
                                    SlantObsidian
                                )
                            )
                        )
                )

                // Top Navigation Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("artist_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = Color.White
                        )
                    }

                    Row {
                        IconButton(
                            onClick = { showSearchField = !showSearchField },
                            modifier = Modifier.testTag("artist_search_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Поиск по артисту",
                                tint = Color.White
                            )
                        }
                        IconButton(
                            onClick = { showBioDialog = true },
                            modifier = Modifier.testTag("artist_more_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Об артисте",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Artist Title, Isolated Source Summary & 4 Circular Action Buttons
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    MarqueeText(
                        text = artistProfile.name,
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.78f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (artistProfile.isLoading && artistProfile.popularTracks.isEmpty()) {
                                "Загрузка дискографии…"
                            } else {
                                "${artistProfile.activeProvider.shortBadge} • ${artistProfile.popularTracks.size} треков • ${artistProfile.albums.size} релизов"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 4 Circular Buttons: Инфо | Нравится | Трейлер | Слушать
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ArtistCircleAction(
                            icon = Icons.Default.Info,
                            label = "Инфо",
                            containerColor = Color.White.copy(alpha = 0.16f),
                            contentColor = Color.White,
                            onClick = { showBioDialog = true },
                            testTag = "artist_info_btn"
                        )

                        ArtistCircleAction(
                            icon = if (artistProfile.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            label = if (artistProfile.isLiked) "В любимом" else "Нравится",
                            containerColor = Color.White.copy(alpha = 0.16f),
                            contentColor = if (artistProfile.isLiked) SlantMagenta else Color.White,
                            onClick = { onToggleLikeArtist(artistProfile.name) },
                            testTag = "artist_like_btn"
                        )

                        ArtistCircleAction(
                            icon = Icons.Default.GraphicEq,
                            label = "Трейлер",
                            containerColor = Color.White.copy(alpha = 0.16f),
                            contentColor = Color.White,
                            onClick = {
                                val first = artistProfile.popularTracks.firstOrNull()
                                if (first != null) {
                                    onPlayTrailer(first)
                                    Toast.makeText(
                                        context,
                                        "Трейлер артиста ${artistProfile.name}…",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            testTag = "artist_trailer_btn"
                        )

                        ArtistCircleAction(
                            icon = Icons.Default.PlayArrow,
                            label = "Слушать",
                            containerColor = Color(0xFFFACC15),
                            contentColor = Color(0xFF0E1118),
                            onClick = {
                                val first = artistProfile.popularTracks.firstOrNull()
                                if (first != null) {
                                    onPlayTrack(first, displayedTracks)
                                }
                            },
                            testTag = "artist_play_all_btn"
                        )
                    }
                }
            }
        }

        // 1b. Single-Source Catalog Isolation Selector (ensures tracks from different sources NEVER mix)
        if (artistProfile.availableProviders.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Источник дискографии (без смешивания источников):",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.65f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        artistProfile.availableProviders.forEach { provider ->
                            val count = artistProfile.tracksByProvider[provider]?.size ?: 0
                            val isSelected = provider == artistProfile.activeProvider
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSelectProvider(provider) },
                                label = {
                                    Text(
                                        text = "${provider.displayName} ($count)",
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SlantCyan.copy(alpha = 0.25f),
                                    selectedLabelColor = SlantCyan,
                                    containerColor = Color(0xFF171C28),
                                    labelColor = Color.White.copy(alpha = 0.8f)
                                ),
                                modifier = Modifier.testTag("artist_provider_chip_${provider.name}")
                            )
                        }
                    }
                }
            }
        }

        if (showSearchField) {
            item {
                OutlinedTextField(
                    value = filterQuery,
                    onValueChange = { filterQuery = it },
                    placeholder = { Text("Поиск треков ${artistProfile.name}…") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
        }

        // 2. Featured Single / Release Card (from the active isolated source)
        artistProfile.latestRelease?.let { latest ->
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Избранный релиз (${artistProfile.activeProvider.shortBadge})",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onPlayTrack(latest, displayedTracks) }
                            .padding(vertical = 4.dp)
                            .testTag("artist_latest_release_card"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1E2536)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (latest.artworkUrl.isNotBlank()) {
                                AsyncImage(
                                    model = latest.artworkUrl,
                                    contentDescription = latest.title,
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

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            MarqueeText(
                                text = latest.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = listOfNotNull(
                                    latest.album.takeIf { it.isNotBlank() },
                                    latest.releaseYear.takeIf { it.isNotBlank() }
                                ).joinToString(" • ").ifBlank { latest.primaryProvider.displayName },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.65f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${latest.formattedDuration} • ${latest.genre}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.55f)
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.55f)
                        )
                    }
                }
            }
        }

        // 3. "Популярные треки >" Section + Sort Chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Треки (${displayedTracks.size})",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("По умолчанию", "По длительности", "А–Я").forEach { mode ->
                        val active = sortMode == mode
                        Surface(
                            onClick = { sortMode = mode },
                            shape = RoundedCornerShape(12.dp),
                            color = if (active) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f)
                        ) {
                            Text(
                                text = mode,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (active) Color(0xFFFACC15) else Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        if (artistProfile.isLoading && displayedTracks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = SlantCyan)
                }
            }
        } else if (displayedTracks.isEmpty()) {
            item {
                Text(
                    text = "Треки исполнителя не найдены в выбранном источнике",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.65f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                )
            }
        } else {
            items(displayedTracks, key = { "artist_pop_${it.id}" }) { track ->
                val isCurrent = currentTrack?.id == track.id
                ArtistTrackRow(
                    track = track,
                    isCurrent = isCurrent,
                    isPlaying = isPlaying,
                    onClick = { onPlayTrack(track, displayedTracks) },
                    onMoreClick = { onOpenTrackMenu(track) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp)
                )
            }
        }

        // 4. "Моя волна по артисту" Banner
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Text(
                    text = "Моя волна по артисту",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                WaveBannerCard(
                    subtitle = "Моя волна по артисту",
                    title = artistProfile.name,
                    gradientColors = listOf(
                        Color(0xFF3F3F46),
                        Color(0xFF71717A),
                        Color(0xFF27272A)
                    ),
                    onClick = { onStartArtistWave(artistProfile.name) }
                )
            }
        }

        // 5. "Альбомы и релизы >" Carousel (strictly from the active provider)
        if (artistProfile.albums.isNotEmpty()) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Альбомы и релизы (${artistProfile.activeProvider.shortBadge})",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(artistProfile.albums, key = { it.id }) { album ->
                            AlbumCarouselCard(
                                album = album,
                                onClick = {
                                    val first = album.tracks.firstOrNull()
                                    if (first != null) {
                                        onPlayTrack(first, album.tracks)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showBioDialog) {
        AlertDialog(
            onDismissRequest = { showBioDialog = false },
            title = {
                Text(
                    text = artistProfile.name,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = artistProfile.biography.ifBlank {
                        "Исполнитель: ${artistProfile.name}. Активный источник каталога: ${artistProfile.activeProvider.displayName} (${artistProfile.popularTracks.size} треков, ${artistProfile.albums.size} релизов)."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { showBioDialog = false }) {
                    Text("Закрыть")
                }
            }
        )
    }
}

@Composable
private fun ArtistCircleAction(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(6.dp)
            .testTag(testTag)
    ) {
        Surface(
            shape = CircleShape,
            color = containerColor,
            modifier = Modifier.size(62.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.9f),
            maxLines = 1
        )
    }
}

@Composable
private fun ArtistTrackRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = if (isCurrent) Color.White.copy(alpha = 0.08f) else Color.Transparent,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
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

                if (isCurrent) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isPlaying) Color(0xFFFACC15) else SlantCyan
                                )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                MarqueeText(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCurrent) Color(0xFFFACC15) else Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = listOfNotNull(
                        track.artist,
                        track.formattedDuration.takeIf { it != "--:--" },
                        track.album.takeIf { it.isNotBlank() }
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            ProviderBadgeChip(
                provider = track.primaryProvider,
                modifier = Modifier.padding(end = 4.dp)
            )

            IconButton(
                onClick = onMoreClick,
                modifier = Modifier.testTag("artist_track_more_${track.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Меню трека",
                    tint = Color.White.copy(alpha = 0.65f)
                )
            }
        }
    }
}

@Composable
private fun AlbumCarouselCard(
    album: AlbumSummary,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(148.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(148.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(listOf(SlantViolet.copy(alpha = 0.4f), SlantCyan.copy(alpha = 0.4f)))
                ),
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
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        MarqueeText(
            text = album.title,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = buildString {
                if (album.releaseYear.isNotBlank()) append("${album.releaseYear} • ")
                append("${album.tracks.size} треков")
            },
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f),
            maxLines = 1
        )
    }
}
