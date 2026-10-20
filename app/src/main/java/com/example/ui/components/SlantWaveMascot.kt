package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.domain.SourceProvider
import com.example.domain.Track
import com.example.ui.theme.SlantCyan
import com.example.ui.theme.SlantEmerald
import com.example.ui.theme.SlantMagenta
import com.example.ui.theme.SlantViolet
import kotlin.math.abs
import kotlin.math.sin

/**
 * Minimalist Pastel Liquid Glass animated visualizer mascot "SlantWaveMascot".
 */
@Composable
fun SlantWaveMascot(
    isPlaying: Boolean,
    isBuffering: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mascot_wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isPlaying) 1400 else 3600,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = if (isPlaying) 1.08f else 0.97f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isPlaying) 640 else 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mascot_pulse"
    )

    Box(
        modifier = modifier
            .size(58.dp)
            .testTag("slant_wave_mascot"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val center = Offset(w / 2f, h / 2f)
            val baseRadius = (w.coerceAtMost(h) / 2f) * 0.84f * pulse

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        SlantCyan.copy(alpha = if (isPlaying) 0.36f else 0.16f),
                        SlantViolet.copy(alpha = if (isPlaying) 0.24f else 0.10f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 1.2f
                ),
                radius = baseRadius * 1.2f,
                center = center
            )

            drawCircle(
                color = Color(0xFF1A2234).copy(alpha = 0.85f),
                radius = baseRadius * 0.85f,
                center = center
            )
            drawCircle(
                brush = Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.6f), SlantCyan.copy(alpha = 0.4f), SlantMagenta.copy(alpha = 0.3f))
                ),
                radius = baseRadius * 0.85f,
                center = center,
                style = Stroke(width = 2.2f)
            )

            val barCount = 5
            val totalWidth = w * 0.44f
            val barWidth = totalWidth / (barCount * 1.7f)
            val gap = (totalWidth - barWidth * barCount) / (barCount - 1).coerceAtLeast(1)
            val startX = (w - totalWidth) / 2f

            for (i in 0 until barCount) {
                val waveFactor = if (isPlaying) {
                    (0.32f + 0.68f * abs(sin(phase + i * 0.85f)))
                } else if (isBuffering) {
                    (0.28f + 0.38f * abs(sin(phase * 1.4f + i)))
                } else {
                    0.26f
                }
                val barHeight = (h * 0.36f) * waveFactor
                val x = startX + i * (barWidth + gap)
                val y = (h - barHeight) / 2f
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(SlantCyan, SlantViolet, SlantMagenta)
                    ),
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                )
            }
        }
    }
}

@Composable
fun ProviderBadgeChip(
    provider: SourceProvider,
    extraSourceCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.red < 0.5f
    val badgeColor = if (isDark) {
        when (provider) {
            SourceProvider.LOCAL -> Color(0xFF6EE7B7)
            SourceProvider.YOUTUBE_PIPED -> Color(0xFFFDA4AF)
            SourceProvider.JAMENDO -> Color(0xFF99F6E4)
            SourceProvider.AUDIUS -> Color(0xFFD8B4FE)
            SourceProvider.DEEZER -> Color(0xFFF9A8D4)
            SourceProvider.ITUNES -> Color(0xFF7DD3FC)
            SourceProvider.RADIO_BROWSER -> Color(0xFFFDE047)
            SourceProvider.INTERNET_ARCHIVE -> Color(0xFFA7F3D0)
        }
    } else {
        when (provider) {
            SourceProvider.LOCAL -> Color(0xFF047857)
            SourceProvider.YOUTUBE_PIPED -> Color(0xFFBE123C)
            SourceProvider.JAMENDO -> Color(0xFF0F766E)
            SourceProvider.AUDIUS -> Color(0xFF6D28D9)
            SourceProvider.DEEZER -> Color(0xFF9D174D)
            SourceProvider.ITUNES -> Color(0xFF0369A1)
            SourceProvider.RADIO_BROWSER -> Color(0xFFB45309)
            SourceProvider.INTERNET_ARCHIVE -> Color(0xFF15803D)
        }
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(badgeColor.copy(alpha = if (isDark) 0.16f else 0.12f))
            .border(0.8.dp, badgeColor.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 1.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (extraSourceCount > 1) {
                "${provider.shortBadge} +${extraSourceCount - 1}"
            } else {
                provider.shortBadge
            },
            style = MaterialTheme.typography.labelSmall,
            color = badgeColor,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun TrackItemCard(
    track: Track,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    isDownloading: Boolean,
    onTrackClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
    onMoreClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val tint = if (isCurrentTrack) SlantCyan else SlantViolet

    LiquidGlassCard(
        cornerRadius = 18.dp,
        tintColor = tint,
        onClick = onTrackClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag("track_item_card_${track.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(SlantCyan.copy(alpha = 0.3f), SlantViolet.copy(alpha = 0.3f))
                        )
                    ),
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
                        tint = accentColor
                    )
                }

                if (isCurrentTrack) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.42f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Сейчас играет",
                            tint = Color(0xFFFACC15)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                MarqueeText(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCurrentTrack) accentColor else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = buildString {
                        append(track.artist)
                        if (track.album.isNotBlank()) append(" • ${track.album}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ProviderBadgeChip(
                        provider = track.primaryProvider,
                        extraSourceCount = track.allStreamCandidates().size
                    )
                    Text(
                        text = track.formattedDuration,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    if (track.isCachedOffline) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = "Сохранено оффлайн",
                            tint = SlantEmerald,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    if (track.recommendationReason.isNotBlank()) {
                        Text(
                            text = track.recommendationReason,
                            style = MaterialTheme.typography.labelSmall,
                            color = SlantCyan,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            if (!track.isLiveStream && track.primaryProvider != SourceProvider.LOCAL) {
                IconButton(
                    onClick = onDownloadClick,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("download_track_btn_${track.id}")
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
                            strokeWidth = 2.dp,
                            color = accentColor
                        )
                    } else {
                        Icon(
                            imageVector = if (track.isCachedOffline) {
                                Icons.Default.CloudDone
                            } else {
                                Icons.Default.Download
                            },
                            contentDescription = "Кэшировать оффлайн",
                            tint = if (track.isCachedOffline) {
                                SlantEmerald
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            IconButton(
                onClick = onFavoriteClick,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("favorite_track_btn_${track.id}")
            ) {
                Icon(
                    imageVector = if (track.isFavorite) {
                        Icons.Default.Favorite
                    } else {
                        Icons.Default.FavoriteBorder
                    },
                    contentDescription = "В избранное",
                    tint = if (track.isFavorite) {
                        Color(0xFFE11D48)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(18.dp)
                )
            }

            if (onMoreClick != null) {
                IconButton(
                    onClick = onMoreClick,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("more_track_btn_${track.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Меню трека",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
            } else {
                IconButton(
                    onClick = onAddToPlaylistClick,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("playlist_add_btn_${track.id}")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                        contentDescription = "Добавить в плейлист",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

/**
 * Floating MiniPlayerBar matching the sleek dark floating card at the bottom of Screenshots 4, 5, 6, 8, 9, 10:
 * Rounded cover + Title + Artist + Heart icon + Play/Pause icon + progress indicator.
 */
@Composable
fun MiniPlayerBar(
    track: Track,
    isPlaying: Boolean,
    progressFraction: Float,
    onBarClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onPreviousClick: () -> Unit = {},
    onToggleFavoriteClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val accentColor = MaterialTheme.colorScheme.primary

    LiquidGlassCard(
        cornerRadius = 18.dp,
        tintColor = SlantCyan,
        onClick = onBarClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag("mini_player_bar")
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(listOf(SlantCyan.copy(alpha = 0.35f), SlantViolet.copy(alpha = 0.35f)))
                        ),
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
                            tint = accentColor
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    MarqueeText(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
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

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onToggleFavoriteClick,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("mini_player_fav_btn")
                ) {
                    Icon(
                        imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Мне нравится",
                        tint = if (track.isFavorite) Color(0xFFE11D48) else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(21.dp)
                    )
                }

                IconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("mini_player_play_pause_btn")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Пауза" else "Воспроизвести",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = onNextClick,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("mini_player_next_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Следующий трек",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { progressFraction.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp),
                color = accentColor,
                trackColor = Color.Transparent
            )
        }
    }
}
