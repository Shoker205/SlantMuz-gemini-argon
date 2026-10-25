package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.PlaylistWithTracks
import com.example.domain.ArtistBioInfo
import com.example.domain.LrcParser
import com.example.domain.StreamCandidate
import com.example.domain.SyncedLyrics
import com.example.domain.Track
import com.example.player.PlaybackUiState
import com.example.player.RepeatModeState
import com.example.ui.components.AudioOutputDeviceDialog
import com.example.ui.components.MarqueeText
import com.example.ui.components.ProviderBadgeChip
import com.example.ui.components.SleepTimerDialog
import com.example.ui.components.StreamSelectorDialog
import com.example.ui.components.WaveBannerCard
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
    similarTracks: List<Track> = emptyList(),
    playlists: List<PlaylistWithTracks> = emptyList(),
    waveContextLabel: String = "Моя волна",
    resetTrigger: Int = 0,
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
    onPlayTrack: (Track, List<Track>) -> Unit = { _, _ -> },
    onStartTrackWave: (Track) -> Unit = {},
    onDislikeTrack: (Track) -> Unit = {},
    onOpenTrackMenu: (Track) -> Unit = {},
    onOpenArtist: (String) -> Unit = {},
    onQuickAddToPlaylist: (Long, Track) -> Unit = { _, _ -> },
    onOpenAddToPlaylistDialog: (Track) -> Unit = {},
    onCollapsePlayer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val track = playbackState.currentTrack
    val scrollState = rememberScrollState()
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showStreamSelectorDialog by remember { mutableStateOf(false) }
    var showCastDialog by remember { mutableStateOf(false) }
    var showQueueDialog by remember { mutableStateOf(false) }

    LaunchedEffect(resetTrigger) {
        if (resetTrigger > 0) {
            scrollState.animateScrollTo(0)
        }
    }

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
                    text = "Выберите любой трек или запустите «Мою волну» на вкладке Волна",
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
            .background(Color(0xFF0E1B1A))
            .testTag("now_playing_screen")
    ) {
        if (track.artworkUrl.isNotBlank()) {
            AsyncImage(
                model = track.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.18f)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF0E1D1C).copy(alpha = 0.84f),
                            Color(0xFF0B1616).copy(alpha = 0.94f),
                            SlantObsidian
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Top Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCollapsePlayer,
                    modifier = Modifier.testTag("player_collapse_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Свернуть плеер",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = waveContextLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (playbackState.sleepTimerRemainingSec > 0) {
                        Text(
                            text = "Таймер: ${playbackState.sleepTimerRemainingSec / 60}:${"%02d".format(playbackState.sleepTimerRemainingSec % 60)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SlantAmber
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showCastDialog = true },
                        modifier = Modifier.testTag("player_cast_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cast,
                            contentDescription = "Аудиовыходы устройства",
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                    IconButton(
                        onClick = { showQueueDialog = true },
                        modifier = Modifier.testTag("player_queue_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "Очередь воспроизведения",
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Large Rounded Album Cover
            Surface(
                modifier = Modifier.size(286.dp),
                shape = RoundedCornerShape(22.dp),
                tonalElevation = 18.dp,
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
                            .background(
                                Brush.linearGradient(
                                    listOf(SlantCyan.copy(alpha = 0.5f), SlantViolet.copy(alpha = 0.5f))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(72.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Track Row below Cover: Artist Circular Avatar + Title/Artist + Share + More (⋮)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF232B3A))
                        .clickable { onOpenArtist(track.artist) }
                        .testTag("player_artist_avatar_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    if (track.artworkUrl.isNotBlank()) {
                        AsyncImage(
                            model = track.artworkUrl,
                            contentDescription = track.artist,
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

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenArtist(track.artist) }
                ) {
                    MarqueeText(
                        text = track.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    MarqueeText(
                        text = track.artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.65f)
                    )
                }

                IconButton(
                    onClick = {
                        runCatching {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Слушаю в SlantMuz: ${track.artist} — ${track.title}"
                                )
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Поделиться треком"))
                        }
                    },
                    modifier = Modifier.testTag("player_share_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Поделиться",
                        tint = Color.White.copy(alpha = 0.78f)
                    )
                }

                IconButton(
                    onClick = { onOpenTrackMenu(track) },
                    modifier = Modifier.testTag("player_more_menu_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Меню трека",
                        tint = Color.White.copy(alpha = 0.78f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Seek Bar + Time Labels
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
                    .height(34.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTimeMs(playbackState.positionMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.65f)
                )
                Text(
                    text = if (track.isLiveStream) "LIVE" else formatTimeMs(durationMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.65f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5. Primary Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        onDislikeTrack(track)
                        Toast.makeText(context, "Трек пропущен и исключён из Моей волны", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("player_dislike_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.HeartBroken,
                        contentDescription = "Не нравится",
                        tint = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.size(26.dp)
                    )
                }

                IconButton(
                    onClick = onSkipPrevious,
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("player_prev_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Предыдущий трек",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }

                FilledIconButton(
                    onClick = onPlayPause,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color(0xFFFACC15),
                        contentColor = Color(0xFF0B0F1A)
                    ),
                    modifier = Modifier
                        .size(72.dp)
                        .testTag("player_play_pause_btn")
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Пауза" else "Воспроизвести",
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("player_next_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Следующий трек",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }

                IconButton(
                    onClick = { onToggleFavorite(track) },
                    modifier = Modifier.testTag("player_favorite_btn")
                ) {
                    Icon(
                        imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Мне нравится",
                        tint = if (track.isFavorite) SlantMagenta else Color.White.copy(alpha = 0.78f),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 6. Three Rounded Pill Buttons Row: Shuffle/Repeat | Lyrics | Download
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = {
                        onToggleShuffle()
                        onCycleRepeat()
                    },
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White.copy(alpha = 0.10f),
                    modifier = Modifier
                        .width(86.dp)
                        .height(48.dp)
                        .testTag("player_shuffle_btn")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (playbackState.repeatMode == RepeatModeState.ONE) {
                                Icons.Default.RepeatOne
                            } else if (playbackState.shuffleEnabled) {
                                Icons.Default.Shuffle
                            } else {
                                Icons.Default.Repeat
                            },
                            contentDescription = "Повтор и перемешивание",
                            tint = if (playbackState.shuffleEnabled || playbackState.repeatMode != RepeatModeState.OFF) {
                                SlantCyan
                            } else {
                                Color.White.copy(alpha = 0.8f)
                            }
                        )
                    }
                }

                Surface(
                    onClick = onOpenLyricsSheet,
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White.copy(alpha = 0.10f),
                    modifier = Modifier
                        .width(86.dp)
                        .height(48.dp)
                        .testTag("player_open_lyrics_btn")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lyrics,
                            contentDescription = "Караоке текст",
                            tint = if (activeLyricLine != null) SlantCyan else Color.White.copy(alpha = 0.8f)
                        )
                    }
                }

                Surface(
                    onClick = { onDownloadOffline(track) },
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White.copy(alpha = 0.10f),
                    modifier = Modifier
                        .width(86.dp)
                        .height(48.dp)
                        .testTag("player_offline_download_btn")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (track.isCachedOffline) Icons.Default.CloudDone else Icons.Default.Download,
                            contentDescription = "Скачать оффлайн",
                            tint = if (track.isCachedOffline) SlantEmerald else Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            if (!activeLyricLine.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    onClick = onOpenLyricsSheet,
                    color = SlantCyan.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_lyrics_preview_pill")
                ) {
                    MarqueeText(
                        text = "♪ $activeLyricLine",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 7. "Моя волна по треку" Banner
            WaveBannerCard(
                subtitle = "Моя волна по треку",
                title = track.title,
                gradientColors = listOf(
                    Color(0xFF2E3B3A),
                    Color(0xFF4B5E5C),
                    Color(0xFF1F2928)
                ),
                onClick = { onStartTrackWave(track) }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Audiophile Quick Chips Row
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

                ProviderBadgeChip(
                    provider = playbackState.activeStreamCandidate?.provider ?: track.primaryProvider,
                    extraSourceCount = track.allStreamCandidates().size,
                    modifier = Modifier.align(Alignment.CenterVertically)
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
                    label = { Text("DSP Эквалайзер", maxLines = 1, softWrap = false) },
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

            // 8. "Похожие треки" from Real Catalog
            if (similarTracks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Похожие треки",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    similarTracks.take(5).forEach { simTrack ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPlayTrack(simTrack, similarTracks) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1E293B)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (simTrack.artworkUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = simTrack.artworkUrl,
                                        contentDescription = simTrack.title,
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
                                    text = simTrack.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = simTrack.artist,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.65f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(onClick = { onOpenTrackMenu(simTrack) }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Меню трека",
                                    tint = Color.White.copy(alpha = 0.65f)
                                )
                            }
                        }
                    }
                }
            }

            // 9. "Добавить в плейлист >" Section (only real user playlists, no fake presets)
            Spacer(modifier = Modifier.height(24.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenAddToPlaylistDialog(track) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Добавить в плейлист",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (playlists.isEmpty()) {
                    Surface(
                        onClick = { onOpenAddToPlaylistDialog(track) },
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "У вас пока нет созданных плейлистов",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Нажмите, чтобы создать плейлист и добавить в него этот трек",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.65f)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            FilledTonalButton(onClick = { onOpenAddToPlaylistDialog(track) }) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Создать")
                            }
                        }
                    }
                } else {
                    playlists.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val coverUrl = item.tracks.firstOrNull()?.artworkUrl.orEmpty()
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        Brush.linearGradient(listOf(SlantViolet, SlantMagenta))
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (coverUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = coverUrl,
                                        contentDescription = item.playlist.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                MarqueeText(
                                    text = item.playlist.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${item.tracks.size} треков",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                            IconButton(
                                onClick = {
                                    onQuickAddToPlaylist(item.playlist.playlistId, track)
                                    Toast.makeText(
                                        context,
                                        "Добавлено в «${item.playlist.name}»",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Добавить в плейлист",
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (artistBio != null && artistBio.biography.isNotBlank()) {
                Spacer(modifier = Modifier.height(18.dp))
                Surface(
                    color = Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        MarqueeText(
                            text = "Об исполнителе • ${artistBio.artistName}",
                            style = MaterialTheme.typography.titleSmall,
                            color = SlantCyan,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = artistBio.biography,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 10. Live MediaSession Notification Preview & Quick Actions Panel
            Spacer(modifier = Modifier.height(18.dp))
            Surface(
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("player_notification_preview_card")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Системное медиа-уведомление • Media3",
                                style = MaterialTheme.typography.titleSmall,
                                color = SlantCyan,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${track.primaryProvider.shortBadge} • FULL 320kbps • Шторка и экран блокировки",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SlantEmerald.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = "ACTIVE",
                                style = MaterialTheme.typography.labelSmall,
                                color = SlantEmerald,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { onToggleFavorite(track) }) {
                            Icon(
                                imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Лайк в уведомлении",
                                tint = if (track.isFavorite) SlantMagenta else Color.White
                            )
                        }
                        IconButton(onClick = onSkipPrevious) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Предыдущий",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = onPlayPause) {
                            Icon(
                                imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = SlantCyan
                            )
                        }
                        IconButton(onClick = onSkipNext) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Следующий",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = onToggleShuffle) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Перемешать",
                                tint = if (playbackState.shuffleEnabled) SlantCyan else Color.White.copy(alpha = 0.7f)
                            )
                        }
                        FilledTonalButton(
                            onClick = { onStartTrackWave(track) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Волна ДНК", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(90.dp))
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

    if (showCastDialog) {
        AudioOutputDeviceDialog(
            onDismiss = { showCastDialog = false }
        )
    }

    if (showQueueDialog) {
        AlertDialog(
            onDismissRequest = { showQueueDialog = false },
            title = {
                Text(
                    text = "Очередь воспроизведения (${playbackState.queue.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    playbackState.queue.take(8).forEachIndexed { index, qTrack ->
                        val isCur = qTrack.id == track.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onPlayTrack(qTrack, playbackState.queue)
                                    showQueueDialog = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}.",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isCur) SlantCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = qTrack.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isCur) SlantCyan else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isCur) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = qTrack.artist,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQueueDialog = false }) {
                    Text("Закрыть")
                }
            }
        )
    }
}

@Composable
private fun WaveformSeekBar(
    trackId: String,
    progress: Float,
    onSeekFraction: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val bars = remember(trackId) {
        val seed = abs(trackId.hashCode()).coerceAtLeast(1)
        List(48) { idx ->
            val wave = sin(idx * 0.43 + (seed % 17)).toFloat()
            (0.24f + abs(wave) * 0.72f).coerceIn(0.2f, 1.0f)
        }
    }

    Canvas(
        modifier = modifier
            .pointerInput(trackId) {
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    onSeekFraction(frac)
                }
            }
            .testTag("waveform_seekbar")
    ) {
        val barCount = bars.size
        val spacing = 3.dp.toPx()
        val totalSpacing = spacing * (barCount - 1)
        val barWidth = ((size.width - totalSpacing) / barCount).coerceAtLeast(2f)

        bars.forEachIndexed { idx, amplitude ->
            val barFrac = idx.toFloat() / barCount.toFloat()
            val isPlayed = barFrac <= progress
            val barHeight = (size.height * amplitude).coerceAtLeast(4.dp.toPx())
            val top = (size.height - barHeight) / 2f
            val x = idx * (barWidth + spacing)

            drawRoundRect(
                color = if (isPlayed) Color(0xFFFACC15) else Color.White.copy(alpha = 0.24f),
                topLeft = Offset(x, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

private fun formatTimeMs(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0L) / 1000L).toInt()
    val mins = totalSec / 60
    val secs = totalSec % 60
    return "%d:%02d".format(mins, secs)
}
