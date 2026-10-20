package com.example.ui.screens

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.domain.CollectionSortMode
import com.example.domain.SlantWaveEngine
import com.example.domain.Track
import com.example.ui.components.MarqueeText
import com.example.ui.components.ProviderBadgeChip
import com.example.ui.components.WaveBannerCard
import com.example.ui.theme.SlantCyan
import com.example.ui.theme.SlantEmerald
import com.example.ui.theme.SlantMagenta
import com.example.ui.theme.SlantObsidian

@Composable
fun LikedTracksScreen(
    likedTracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onDownloadOffline: (Track) -> Unit,
    onOpenTrackMenu: (Track) -> Unit,
    onStartLikedWave: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val filters = listOf(
        "Всё",
        "Скачанные",
        "Полные треки",
        "Русские",
        "Иностранные",
        "Без слов"
    )
    var selectedFilter by remember { mutableStateOf("Всё") }
    var selectedSort by remember { mutableStateOf(CollectionSortMode.RECENT) }
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredTracks = remember(likedTracks, selectedFilter, selectedSort, searchQuery) {
        val base = likedTracks.filter { track ->
            val matchesQuery = searchQuery.isBlank() ||
                track.title.contains(searchQuery, ignoreCase = true) ||
                track.artist.contains(searchQuery, ignoreCase = true)
            val matchesCategory = when (selectedFilter) {
                "Всё" -> true
                "Скачанные" -> track.isCachedOffline || !track.localUri.isNullOrBlank()
                "Полные треки" -> !track.isPreviewOnly && track.durationSec > 35
                "Русские" -> SlantWaveEngine.isRussianTrack(track)
                "Иностранные" -> !SlantWaveEngine.isRussianTrack(track)
                "Без слов" -> SlantWaveEngine.isInstrumentalTrack(track)
                else -> true
            }
            matchesQuery && matchesCategory
        }
        when (selectedSort) {
            CollectionSortMode.RECENT -> base
            CollectionSortMode.TITLE_AZ -> base.sortedBy { it.title.lowercase() }
            CollectionSortMode.ARTIST_AZ -> base.sortedBy { it.artist.lowercase() }
            CollectionSortMode.DURATION -> base.sortedByDescending { it.durationSec }
            CollectionSortMode.SOURCE -> base.sortedBy { it.primaryProvider.displayName }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SlantObsidian)
            .testTag("liked_tracks_screen")
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("liked_back_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Мне нравится",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${filteredTracks.size} из ${likedTracks.size} треков • ${selectedSort.title}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.65f)
                )
            }

            IconButton(
                onClick = { showSearchBar = !showSearchBar },
                modifier = Modifier.testTag("liked_search_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Поиск в Мне нравится",
                    tint = Color.White
                )
            }

            IconButton(
                onClick = onStartLikedWave,
                modifier = Modifier.testTag("liked_more_wave_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Моя волна по плейлисту",
                    tint = Color.White
                )
            }
        }

        if (showSearchBar) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Поиск среди любимых треков…") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // Filter Pills Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filters.forEach { filter ->
                val isSelected = selectedFilter == filter
                Surface(
                    onClick = { selectedFilter = filter },
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFF171A24),
                    border = if (isSelected) {
                        androidx.compose.foundation.BorderStroke(1.8.dp, Color.White)
                    } else {
                        androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                    },
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("liked_filter_$filter")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = filter,
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Sort Modes Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CollectionSortMode.entries.forEach { sortMode ->
                val active = selectedSort == sortMode
                Surface(
                    onClick = { selectedSort = sortMode },
                    shape = RoundedCornerShape(12.dp),
                    color = if (active) SlantCyan.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f)
                ) {
                    Text(
                        text = sortMode.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) SlantCyan else Color.White.copy(alpha = 0.7f),
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        if (likedTracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = SlantMagenta,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Список любимых треков пуст",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Нажмите на значок сердечка у любого трека в плеере, поиске или ленте, чтобы добавить его в «Мне нравится».",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.65f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 140.dp)
            ) {
                val splitIndex = filteredTracks.size.coerceAtMost(4)
                val topSlice = filteredTracks.take(splitIndex)
                val bottomSlice = if (filteredTracks.size > splitIndex) {
                    filteredTracks.drop(splitIndex)
                } else {
                    emptyList()
                }

                itemsIndexed(topSlice, key = { _, t -> "liked_top_${t.id}" }) { _, track ->
                    LikedTrackRowItem(
                        track = track,
                        isCurrent = currentTrack?.id == track.id,
                        onClick = { onPlayTrack(track, filteredTracks) },
                        onDownloadClick = { onDownloadOffline(track) },
                        onMoreClick = { onOpenTrackMenu(track) }
                    )
                }

                item {
                    WaveBannerCard(
                        subtitle = "Моя волна по плейлисту",
                        title = "Мне нравится",
                        gradientColors = listOf(
                            Color(0xFF7C4A3A),
                            Color(0xFFB0725E),
                            Color(0xFF5E372B)
                        ),
                        onClick = onStartLikedWave,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                    )
                }

                itemsIndexed(bottomSlice, key = { _, t -> "liked_bottom_${t.id}" }) { _, track ->
                    LikedTrackRowItem(
                        track = track,
                        isCurrent = currentTrack?.id == track.id,
                        onClick = { onPlayTrack(track, filteredTracks) },
                        onDownloadClick = { onDownloadOffline(track) },
                        onMoreClick = { onOpenTrackMenu(track) }
                    )
                }
            }
        }
    }
}

@Composable
fun LikedTrackRowItem(
    track: Track,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("liked_track_row_${track.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
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

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            MarqueeText(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) SlantCyan else Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${track.artist} • ${track.formattedDuration}",
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
            onClick = onDownloadClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (track.isCachedOffline) Icons.Default.CloudDone else Icons.Default.Download,
                contentDescription = "Скачать трек",
                tint = SlantEmerald,
                modifier = Modifier.size(20.dp)
            )
        }

        IconButton(
            onClick = onMoreClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Меню трека",
                tint = Color.White.copy(alpha = 0.65f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
