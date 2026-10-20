package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.domain.Track
import com.example.ui.theme.SlantCyan
import com.example.ui.theme.SlantMagenta
import com.example.ui.theme.SlantSkyPastel
import com.example.ui.theme.SlantViolet
import kotlin.math.cos
import kotlin.math.sin

/**
 * Single-line marquee ("бегущая строка") text that smoothly scrolls when text is too long
 * instead of wrapping or breaking the layout.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontWeight: FontWeight? = null
) {
    Text(
        text = text,
        style = style,
        color = color,
        fontWeight = fontWeight,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.basicMarquee(
            iterations = Int.MAX_VALUE,
            velocity = 28.dp
        )
    )
}

/**
 * Ambient pastel mesh / aurora background that gives the entire app a soft,
 * minimalist Liquid Glass atmosphere.
 */
@Composable
fun PastelAuroraBackground(
    isDark: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val transition = rememberInfiniteTransition(label = "pastel_aurora")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "aurora_angle"
    )

    val baseBg = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseBg)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val alphaScale = if (isDark) 0.14f else 0.22f

            val c1 = Offset(
                x = w * (0.25f + 0.15f * cos(angle)),
                y = h * (0.18f + 0.08f * sin(angle))
            )
            val c2 = Offset(
                x = w * (0.78f - 0.15f * sin(angle)),
                y = h * (0.38f + 0.10f * cos(angle))
            )
            val c3 = Offset(
                x = w * (0.35f + 0.12f * sin(angle * 0.7f)),
                y = h * (0.78f - 0.08f * cos(angle * 0.7f))
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(SlantCyan.copy(alpha = alphaScale), Color.Transparent),
                    center = c1,
                    radius = w * 0.68f
                ),
                radius = w * 0.68f,
                center = c1
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(SlantViolet.copy(alpha = alphaScale), Color.Transparent),
                    center = c2,
                    radius = w * 0.65f
                ),
                radius = w * 0.65f,
                center = c2
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(SlantMagenta.copy(alpha = alphaScale * 0.9f), Color.Transparent),
                    center = c3,
                    radius = w * 0.62f
                ),
                radius = w * 0.62f,
                center = c3
            )
        }

        content()
    }
}

/**
 * Liquid Glass container with translucent gradient fill and specular refraction border.
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    tintColor: Color = Color.White,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape: Shape = RoundedCornerShape(cornerRadius)
    val surfaceColor = MaterialTheme.colorScheme.surface
    val isDark = surfaceColor.red < 0.5f

    val glassFill = Brush.linearGradient(
        colors = if (isDark) {
            listOf(
                surfaceColor.copy(alpha = 0.88f),
                surfaceColor.copy(alpha = 0.78f),
                tintColor.copy(alpha = 0.09f)
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.94f),
                Color.White.copy(alpha = 0.88f),
                tintColor.copy(alpha = 0.14f)
            )
        }
    )

    val specularBorder = Brush.linearGradient(
        colors = if (isDark) {
            listOf(
                Color.White.copy(alpha = 0.28f),
                tintColor.copy(alpha = 0.18f),
                Color.White.copy(alpha = 0.06f)
            )
        } else {
            listOf(
                Color(0xFFCBD5E1).copy(alpha = 0.85f),
                tintColor.copy(alpha = 0.45f),
                Color(0xFFE2E8F0).copy(alpha = 0.7f)
            )
        }
    )

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(brush = glassFill, shape = shape)
            .border(width = 1.dp, brush = specularBorder, shape = shape)
            .then(clickableModifier),
        content = content
    )
}

/**
 * Rich, minimalist Liquid Glass Media Notification card mirroring the enhanced Android MediaSession notification
 * with marquee track title, clear contrast, and properly sized buttons that never overflow.
 */
@Composable
fun RichMediaNotificationPreviewCard(
    track: Track?,
    isPlaying: Boolean,
    progressFraction: Float,
    activeLyricLine: String?,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onOpenLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayTitle = track?.title ?: "SlantMuz Pastel Audio"
    val displayArtist = track?.artist ?: "Выберите трек для запуска плеера"
    val badgeText = track?.primaryProvider?.shortBadge ?: "READY"
    val accentColor = MaterialTheme.colorScheme.primary

    LiquidGlassCard(
        cornerRadius = 22.dp,
        tintColor = SlantViolet,
        modifier = modifier
            .fillMaxWidth()
            .testTag("rich_notification_preview_card")
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            // Top compact status bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Уведомление Media3 • $badgeText",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    onClick = onOpenLyrics,
                    color = accentColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("notif_preview_lyrics_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lyrics,
                            contentDescription = "Караоке текст",
                            tint = accentColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Караоке",
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Row: Cover + Marquee Title/Artist + Compact Transport Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(SlantCyan.copy(alpha = 0.35f), SlantMagenta.copy(alpha = 0.35f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (!track?.artworkUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = track?.artworkUrl,
                            contentDescription = displayTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    MarqueeText(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = displayArtist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!activeLyricLine.isNullOrBlank()) {
                        MarqueeText(
                            text = "♪ $activeLyricLine",
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                if (track != null) {
                    IconButton(
                        onClick = { onToggleFavorite(track) },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("notif_preview_fav_btn")
                    ) {
                        Icon(
                            imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Избранное в уведомлении",
                            tint = if (track.isFavorite) Color(0xFFE11D48) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("notif_preview_prev_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Предыдущий",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(SlantCyan, SlantSkyPastel))
                        )
                        .clickable(onClick = onPlayPause)
                        .testTag("notif_preview_play_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Пауза" else "Играть",
                        tint = Color(0xFF0F172A),
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onNext,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("notif_preview_next_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Следующий",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progressFraction.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = accentColor,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            )
        }
    }
}
