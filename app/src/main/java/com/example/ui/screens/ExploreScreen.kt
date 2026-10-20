package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.remote.ExploreFeedData
import com.example.domain.Track
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.MarqueeText
import com.example.ui.components.ProviderBadgeChip
import com.example.ui.components.RichMediaNotificationPreviewCard
import com.example.ui.components.SlantWaveMascot
import com.example.ui.components.TrackItemCard
import com.example.ui.theme.SlantCyan
import com.example.ui.theme.SlantMagenta
import com.example.ui.theme.SlantViolet

@Composable
fun ExploreScreen(
    exploreFeed: ExploreFeedData,
    isLoading: Boolean,
    currentTrack: Track?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    progressFraction: Float,
    activeLyricLine: String?,
    downloadingIds: Set<String>,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onDownloadOffline: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onQuickGenreClick: (String) -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenWidgetsHub: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val moodGenres = listOf(
        "Dream Pop",
        "Lo-Fi Pastel",
        "Ambient Glass",
        "Synthwave",
        "Acoustic Indie",
        "Deep Chill",
        "Neo Classical"
    )
    val accentColor = MaterialTheme.colorScheme.primary

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("explore_screen"),
        contentPadding = PaddingValues(bottom = 128.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Minimalist Liquid Glass Hero Card — Structured in 2 clean rows so buttons never wrap
        item {
            LiquidGlassCard(
                cornerRadius = 24.dp,
                tintColor = SlantCyan,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_hero_wave),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .alpha(0.28f)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "SlantMuz",
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                MarqueeText(
                                    text = "Pastel Liquid Glass • 8 источников • Караоке LRC • 5-Band DSP",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            SlantWaveMascot(
                                isPlaying = isPlaying,
                                isBuffering = isBuffering
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    val queue = exploreFeed.trendingFullStreams
                                        .ifEmpty { exploreFeed.globalChartTracks }
                                    queue.firstOrNull()?.let { onPlayTrack(it, queue) }
                                },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("hero_play_mix_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Поток дня",
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            IconButton(
                                onClick = onOpenEqualizer,
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("hero_open_eq_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Equalizer,
                                    contentDescription = "Открыть DSP эквалайзер",
                                    tint = accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = onOpenWidgetsHub,
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("hero_open_widgets_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Widgets,
                                    contentDescription = "Виджеты рабочего стола",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = onRefresh,
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("hero_refresh_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Обновить ленту",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Convenient Smart Media Notification Control Card
        item {
            RichMediaNotificationPreviewCard(
                track = currentTrack ?: exploreFeed.trendingFullStreams.firstOrNull(),
                isPlaying = isPlaying,
                progressFraction = progressFraction,
                activeLyricLine = activeLyricLine,
                onPlayPause = onPlayPause,
                onPrevious = onPrevious,
                onNext = onNext,
                onToggleFavorite = onToggleFavorite,
                onOpenLyrics = onOpenLyrics,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // Mood / Genre Pastel Pills
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(moodGenres) { genre ->
                    AssistChip(
                        onClick = { onQuickGenreClick(genre) },
                        label = {
                            Text(
                                text = genre,
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            labelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.testTag("mood_chip_$genre")
                    )
                }
            }
        }

        if (isLoading && exploreFeed.trendingFullStreams.isEmpty() && exploreFeed.globalChartTracks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = accentColor)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Синхронизация открытых аудио-нод…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Live Online Radio Stations Carousel
        if (exploreFeed.liveRadioStations.isNotEmpty()) {
            item {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radio,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Живое радио (Radio Browser)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(exploreFeed.liveRadioStations, key = { it.id }) { station ->
                            LiveRadioCard(
                                station = station,
                                isPlaying = currentTrack?.id == station.id && isPlaying,
                                onClick = { onPlayTrack(station, exploreFeed.liveRadioStations) }
                            )
                        }
                    }
                }
            }
        }

        // Full-Length Open HD Streams (Jamendo 320kbps + Audius Web3)
        if (exploreFeed.trendingFullStreams.isNotEmpty()) {
            item {
                Text(
                    text = "Полные треки 320kbps (Jamendo & Audius)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            items(
                exploreFeed.trendingFullStreams.take(8),
                key = { "full_${it.id}" }
            ) { track ->
                TrackItemCard(
                    track = track,
                    isCurrentTrack = currentTrack?.id == track.id,
                    isPlaying = isPlaying,
                    isDownloading = track.id in downloadingIds,
                    onTrackClick = { onPlayTrack(track, exploreFeed.trendingFullStreams) },
                    onFavoriteClick = { onToggleFavorite(track) },
                    onDownloadClick = { onDownloadOffline(track) },
                    onAddToPlaylistClick = { onAddToPlaylist(track) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        // Global Charts (Deezer HD 1000x1000 + iTunes)
        if (exploreFeed.globalChartTracks.isNotEmpty()) {
            item {
                Text(
                    text = "Мировые чарты & HD-превью (Deezer + iTunes)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            items(
                exploreFeed.globalChartTracks.take(10),
                key = { "chart_${it.id}" }
            ) { track ->
                TrackItemCard(
                    track = track,
                    isCurrentTrack = currentTrack?.id == track.id,
                    isPlaying = isPlaying,
                    isDownloading = track.id in downloadingIds,
                    onTrackClick = { onPlayTrack(track, exploreFeed.globalChartTracks) },
                    onFavoriteClick = { onToggleFavorite(track) },
                    onDownloadClick = { onDownloadOffline(track) },
                    onAddToPlaylistClick = { onAddToPlaylist(track) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun LiveRadioCard(
    station: Track,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val accentColor = MaterialTheme.colorScheme.primary
    LiquidGlassCard(
        cornerRadius = 18.dp,
        tintColor = if (isPlaying) SlantCyan else SlantViolet,
        onClick = onClick,
        modifier = Modifier
            .width(150.dp)
            .testTag("radio_station_card_${station.id}")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(SlantViolet.copy(alpha = 0.32f), SlantMagenta.copy(alpha = 0.32f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (station.artworkUrl.isNotBlank()) {
                    AsyncImage(
                        model = station.artworkUrl,
                        contentDescription = station.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Radio,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
                ProviderBadgeChip(
                    provider = station.primaryProvider,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            MarqueeText(
                text = station.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isPlaying) accentColor else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = station.artist,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
