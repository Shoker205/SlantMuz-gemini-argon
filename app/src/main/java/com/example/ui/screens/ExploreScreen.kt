package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.remote.ExploreFeedData
import com.example.domain.SlantWaveEngine
import com.example.domain.SmartPlaylistPreset
import com.example.domain.Track
import com.example.domain.WaveActivity
import com.example.domain.WaveCharacter
import com.example.domain.WaveLanguage
import com.example.domain.WaveMood
import com.example.domain.WaveSettings
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.MarqueeText
import com.example.ui.components.ProviderBadgeChip
import com.example.ui.components.RichMediaNotificationPreviewCard
import com.example.ui.components.SlantWaveMascot
import com.example.ui.components.TrackItemCard
import com.example.ui.theme.SlantCyan
import com.example.ui.theme.SlantEmerald
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
    waveSettings: WaveSettings = WaveSettings(),
    isWaveGenerating: Boolean = false,
    waveQueueTracks: List<Track> = emptyList(),
    resetTrigger: Int = 0,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onDownloadOffline: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onOpenTrackMenu: (Track) -> Unit = {},
    onOpenWaveSettings: () -> Unit = {},
    onStartMyWave: () -> Unit = {},
    onResetWaveSettings: () -> Unit = {},
    onQuickGenreClick: (String) -> Unit,
    onGenerateSmartPlaylist: (SmartPlaylistPreset) -> Unit = {},
    onOpenEqualizer: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenSettingsHub: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(resetTrigger) {
        if (resetTrigger > 0) {
            listState.animateScrollToItem(0)
        }
    }

    val quickWaveChips = listOf(
        "Незнакомое" to (waveSettings.character == WaveCharacter.UNFAMILIAR),
        "Любимое" to (waveSettings.character == WaveCharacter.FAVORITE),
        "Популярное" to (waveSettings.character == WaveCharacter.POPULAR),
        "Бодрое" to (waveSettings.mood == WaveMood.ENERGETIC),
        "Весёлое" to (waveSettings.mood == WaveMood.CHEERFUL),
        "Спокойное" to (waveSettings.mood == WaveMood.CALM),
        "Грустное" to (waveSettings.mood == WaveMood.SAD),
        "Русский" to (waveSettings.language == WaveLanguage.RUSSIAN),
        "Иностранный" to (waveSettings.language == WaveLanguage.FOREIGN),
        "Без слов" to (waveSettings.language == WaveLanguage.INSTRUMENTAL),
        "В дороге" to (waveSettings.activity == WaveActivity.ON_THE_ROAD),
        "Работаю" to (waveSettings.activity == WaveActivity.WORKING),
        "Тренируюсь" to (waveSettings.activity == WaveActivity.WORKOUT),
        "Просыпаюсь" to (waveSettings.activity == WaveActivity.WAKING_UP),
        "Засыпаю" to (waveSettings.activity == WaveActivity.FALLING_ASLEEP)
    )
    val accentColor = MaterialTheme.colorScheme.primary

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag("explore_screen"),
        contentPadding = PaddingValues(bottom = 128.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Interactive "Моя волна" (SlantWave) Hero Card with Live Algorithmic Status
        item {
            LiquidGlassCard(
                cornerRadius = 26.dp,
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
                            .alpha(0.32f)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "SlantMuz • Моя волна",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                MarqueeText(
                                    text = if (isWaveGenerating) {
                                        "Синтез волны: ${waveSettings.summarySubtitle()}…"
                                    } else {
                                        waveSettings.summarySubtitle()
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (waveSettings.isCustomized) SlantCyan else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (waveSettings.isCustomized) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val eqPreset = SlantWaveEngine.recommendedEqualizerPreset(waveSettings)
                                    Text(
                                        text = "Фильтров: ${waveSettings.activeFilterCount} • DSP: ${eqPreset.displayName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            SlantWaveMascot(
                                isPlaying = isPlaying,
                                isBuffering = isBuffering || isWaveGenerating,
                                modifier = Modifier.clickable { onStartMyWave() }
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onStartMyWave,
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFACC15),
                                    contentColor = Color(0xFF0E1118)
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .height(38.dp)
                                    .testTag("hero_play_mix_btn")
                            ) {
                                if (isWaveGenerating) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF0E1118),
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Моя волна",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }

                            FilledTonalButton(
                                onClick = onOpenWaveSettings,
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .height(38.dp)
                                    .testTag("hero_customize_wave_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (waveSettings.isCustomized) {
                                        "Настроить (${waveSettings.activeFilterCount})"
                                    } else {
                                        "Настроить"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1
                                )
                            }

                            if (waveSettings.isCustomized) {
                                IconButton(
                                    onClick = onResetWaveSettings,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .testTag("hero_reset_wave_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Сбросить фильтры волны",
                                        tint = SlantMagenta,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            IconButton(
                                onClick = onOpenEqualizer,
                                modifier = Modifier
                                    .size(34.dp)
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
                                onClick = onOpenSettingsHub,
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("hero_open_widgets_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Настройки и прочее",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = onRefresh,
                                modifier = Modifier
                                    .size(34.dp)
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

        // 2. Quick Interactive Algorithmic Wave Filter Chips (shows active state & toggles live)
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(quickWaveChips, key = { it.first }) { (chipLabel, isSelected) ->
                    FilterChip(
                        selected = isSelected,
                        onClick = { onQuickGenreClick(chipLabel) },
                        label = {
                            Text(
                                text = chipLabel,
                                maxLines = 1,
                                softWrap = false,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        shape = RoundedCornerShape(18.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SlantCyan.copy(alpha = 0.25f),
                            selectedLabelColor = SlantCyan,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            labelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.testTag("mood_chip_$chipLabel")
                    )
                }
            }
        }

        // 2b. Live Synthesized Wave Queue Preview (when wave settings are active or wave was launched)
        if (waveQueueTracks.isNotEmpty()) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Поток Моей волны (${waveQueueTracks.size} треков)",
                                style = MaterialTheme.typography.titleMedium,
                                color = SlantCyan,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = waveSettings.summarySubtitle(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(waveQueueTracks.take(12), key = { "wave_q_${it.id}" }) { track ->
                            LiveRadioCard(
                                station = track,
                                isPlaying = currentTrack?.id == track.id && isPlaying,
                                onClick = { onPlayTrack(track, waveQueueTracks) }
                            )
                        }
                    }
                }
            }
        }

        // 3. Convenient Smart Media Notification Control Card
        val previewTrack = currentTrack ?: exploreFeed.trendingFullStreams.firstOrNull()
        if (previewTrack != null) {
            item {
                RichMediaNotificationPreviewCard(
                    track = previewTrack,
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
        }

        // 3b. Algorithmic Smart Mixes ("Умные подборки дня")
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFFFACC15),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Алгоритмические миксы • Собрать и слушать",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(SmartPlaylistPreset.entries, key = { it.name }) { preset ->
                        Surface(
                            onClick = { onGenerateSmartPlaylist(preset) },
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                SlantEmerald.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier
                                .width(205.dp)
                                .testTag("smart_mix_${preset.name}")
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = SlantEmerald,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = preset.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = preset.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
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

        // 4. Live Online Radio Stations Carousel
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

        // 5. Full-Length Open HD Streams (Jamendo 320kbps + Audius Web3)
        if (exploreFeed.trendingFullStreams.isNotEmpty()) {
            item {
                Text(
                    text = "В стиле Моей волны • Полные треки 320kbps",
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
                    onMoreClick = { onOpenTrackMenu(track) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        // 6. Global Charts (Deezer HD 1000x1000 + iTunes)
        if (exploreFeed.globalChartTracks.isNotEmpty()) {
            item {
                Text(
                    text = "Популярные треки & Мировые чарты",
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
                    onMoreClick = { onOpenTrackMenu(track) },
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
