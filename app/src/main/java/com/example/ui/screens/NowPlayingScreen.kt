package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.domain.ArtistBioInfo
import com.example.domain.LrcParser
import com.example.domain.StreamCandidate
import com.example.domain.SyncedLyrics
import com.example.domain.Track
import com.example.player.PlaybackUiState
import com.example.player.RepeatModeState
import com.example.ui.components.MarqueeText
import com.example.ui.components.ProviderBadgeChip
import com.example.ui.components.SleepTimerDialog
import com.example.ui.components.StreamSelectorDialog
import com.example.ui.theme.SlantAmber
import com.example.ui.theme.SlantCyan
import com.example.ui.theme.SlantEmerald
import com.example.ui.theme.SlantMagenta
import com.example.ui.theme.SlantObsidian
import com.example.ui.theme.SlantViolet
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun NowPlayingScreen(
    playbackState: PlaybackUiState,
    currentLyrics: SyncedLyrics?,
    artistBio: ArtistBioInfo?,
    isDownloading: Boolean,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onDownloadOffline: (Track) -> Unit,
    onOpenLyricsSheet: () -> Unit,
    onOpenEqualizerSheet: () -> Unit,
    onSelectStreamCandidate: (StreamCandidate) -> Unit,
    onStartSleepTimer: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val track = playbackState.currentTrack
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showStreamSelectorDialog by remember { mutableStateOf(false) }

    if (track == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp)
                .testTag("now_playing_empty"),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(52.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Выберите любой трек или радиостанцию в Обзоре или Поиске",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    val activeLyricLine = remember(currentLyrics, playbackState.positionMs) {
        if (currentLyrics != null && currentLyrics.isSynced) {
            val idx = LrcParser.findActiveLineIndex(currentLyrics.lines, playbackState.positionMs)
            currentLyrics.lines.getOrNull(idx)?.text
        } else {
            null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SlantObsidian)
            .testTag("now_playing_screen")
    ) {
        // Blurred dynamic artwork background
        if (track.artworkUrl.isNotBlank()) {
            AsyncImage(
                model = track.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(55.dp)
                    .alpha(0.32f)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            SlantObsidian.copy(alpha = 0.72f),
                            SlantObsidian.copy(alpha = 0.90f),
                            SlantObsidian
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Source & Failover Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProviderBadgeChip(
                    provider = playbackState.activeStreamCandidate?.provider ?: track.primaryProvider,
                    extraSourceCount = track.allStreamCandidates().size
                )

                if (playbackState.sleepTimerRemainingSec > 0) {
                    Surface(
                        color = SlantAmber.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Сон: ${playbackState.sleepTimerRemainingSec / 60}:${"%02d".format(playbackState.sleepTimerRemainingSec % 60)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SlantAmber,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            playbackState.failoverNotice?.let { notice ->
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = SlantViolet.copy(alpha = 0.24f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MarqueeText(
                        text = notice,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // High-Resolution Album Cover Card
            Surface(
                modifier = Modifier.size(236.dp),
                shape = RoundedCornerShape(26.dp),
                tonalElevation = 16.dp,
                color = Color(0xFF1E2538)
            ) {
                if (track.artworkUrl.isNotBlank()) {
                    AsyncImage(
                        model = track.artworkUrl,
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.linearGradient(listOf(SlantCyan.copy(alpha = 0.5f), SlantViolet.copy(alpha = 0.5f)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(68.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title (Marquee), Artist & Favorite/Offline actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    MarqueeText(
                        text = track.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    MarqueeText(
                        text = buildString {
                            append(track.artist)
                            if (track.album.isNotBlank()) append(" • ${track.album}")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.76f)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = { onDownloadOffline(track) },
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("player_offline_download_btn")
                ) {
                    Icon(
                        imageVector = if (track.isCachedOffline) Icons.Default.CloudDone else Icons.Default.Download,
                        contentDescription = "Кэшировать оффлайн",
                        tint = if (track.isCachedOffline) SlantEmerald else Color.White
                    )
                }

                IconButton(
                    onClick = { onToggleFavorite(track) },
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("player_favorite_btn")
                ) {
                    Icon(
                        imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Избранное",
                        tint = if (track.isFavorite) SlantMagenta else Color.White
                    )
                }
            }

            // Live synchronized lyric preview pill with MarqueeText
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                onClick = onOpenLyricsSheet,
                color = SlantCyan.copy(alpha = 0.14f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_lyrics_preview_pill")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lyrics,
                        contentDescription = null,
                        tint = SlantCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    MarqueeText(
                        text = activeLyricLine ?: "Открыть синхронизированное караоке LRCLIB",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Interactive Waveform Progress Bar
            val durationMs = playbackState.durationMs.coerceAtLeast(track.durationSec * 1000L).coerceAtLeast(1L)
            val progressFraction = if (track.isLiveStream) {
                1f
            } else {
                (playbackState.positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            }

            WaveformSeekBar(
                trackId = track.id,
                progress = progressFraction,
                onSeekFraction = { frac ->
                    if (!track.isLiveStream) {
                        onSeekTo((frac * durationMs).toLong())
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTimeMs(playbackState.positionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.72f)
                )
                Text(
                    text = if (track.isLiveStream) "LIVE STREAM" else formatTimeMs(durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.72f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Primary Playback Controls (Shuffle, Previous, Play/Pause, Next, Repeat)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier.testTag("player_shuffle_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Шаффл",
                        tint = if (playbackState.shuffleEnabled) SlantCyan else Color.White.copy(alpha = 0.65f)
                    )
                }

                IconButton(
                    onClick = onSkipPrevious,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("player_prev_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Предыдущий трек",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                FilledIconButton(
                    onClick = onPlayPause,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = SlantCyan,
                        contentColor = SlantObsidian
                    ),
                    modifier = Modifier
                        .size(64.dp)
                        .testTag("player_play_pause_btn")
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Пауза" else "Воспроизвести",
                        modifier = Modifier.size(34.dp)
                    )
                }

                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("player_next_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Следующий трек",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                IconButton(
                    onClick = onCycleRepeat,
                    modifier = Modifier.testTag("player_repeat_btn")
                ) {
                    Icon(
                        imageVector = if (playbackState.repeatMode == RepeatModeState.ONE) {
                            Icons.Default.RepeatOne
                        } else {
                            Icons.Default.Repeat
                        },
                        contentDescription = "Режим повтора",
                        tint = if (playbackState.repeatMode != RepeatModeState.OFF) {
                            SlantCyan
                        } else {
                            Color.White.copy(alpha = 0.65f)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Horizontally scrollable Quick Audiophile Tool Row so chips never squish or wrap
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val chipColors = AssistChipDefaults.assistChipColors(
                    containerColor = Color.White.copy(alpha = 0.08f),
                    labelColor = Color.White
                )

                AssistChip(
                    onClick = onOpenLyricsSheet,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lyrics,
                            contentDescription = null,
                            tint = SlantCyan,
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    label = { Text("Караоке", maxLines = 1, softWrap = false) },
                    colors = chipColors,
                    modifier = Modifier.testTag("player_open_lyrics_btn")
                )

                AssistChip(
                    onClick = onOpenEqualizerSheet,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Equalizer,
                            contentDescription = null,
                            tint = SlantMagenta,
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    label = { Text("Эквалайзер", maxLines = 1, softWrap = false) },
                    colors = chipColors,
                    modifier = Modifier.testTag("player_open_eq_btn")
                )

                AssistChip(
                    onClick = { showSleepTimerDialog = true },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = SlantAmber,
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    label = { Text("Таймер сна", maxLines = 1, softWrap = false) },
                    colors = chipColors,
                    modifier = Modifier.testTag("player_sleep_timer_btn")
                )

                AssistChip(
                    onClick = { showStreamSelectorDialog = true },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = null,
                            tint = SlantViolet,
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "Потоки (${track.allStreamCandidates().size})",
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    colors = chipColors,
                    modifier = Modifier.testTag("player_streams_btn")
                )
            }

            // TheAudioDB Artist Biography Enrichment Card
            if (artistBio != null && artistBio.biography.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    color = Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        MarqueeText(
                            text = "Об исполнителе • ${artistBio.artistName} (TheAudioDB)",
                            style = MaterialTheme.typography.titleSmall,
                            color = SlantCyan,
                            fontWeight = FontWeight.Bold
                        )
                        if (artistBio.style.isNotBlank() || artistBio.country.isNotBlank()) {
                            Text(
                                text = listOf(artistBio.style, artistBio.country, artistBio.formedYear)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" • "),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.68f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = artistBio.biography,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            remainingSeconds = playbackState.sleepTimerRemainingSec,
            onSelectMinutes = onStartSleepTimer,
            onDismiss = { showSleepTimerDialog = false }
        )
    }

    if (showStreamSelectorDialog) {
        StreamSelectorDialog(
            candidates = track.allStreamCandidates(),
            activeCandidate = playbackState.activeStreamCandidate,
            onSelectCandidate = onSelectStreamCandidate,
            onDismiss = { showStreamSelectorDialog = false }
        )
    }
}

@Composable
fun WaveformSeekBar(
    trackId: String,
    progress: Float,
    onSeekFraction: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val seed = trackId.hashCode()
    Canvas(
        modifier = modifier
            .pointerInput(trackId) {
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeekFraction(frac)
                }
            }
            .testTag("waveform_seek_bar")
    ) {
        val barCount = 46
        val barWidth = size.width / (barCount * 1.55f)
        val gap = (size.width - barWidth * barCount) / (barCount - 1)
        for (i in 0 until barCount) {
            val barFraction = i.toFloat() / barCount.toFloat()
            val normalizedAmp = 0.22f + 0.78f * abs(sin((i * 0.63f) + (seed % 17)))
            val barH = size.height * normalizedAmp
            val x = i * (barWidth + gap)
            val y = (size.height - barH) / 2f
            val isPlayed = barFraction <= progress
            drawRoundRect(
                color = if (isPlayed) SlantCyan else Color.White.copy(alpha = 0.25f),
                topLeft = Offset(x, y),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

private fun formatTimeMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return "%d:%02d".format(mins, secs)
}
