package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.domain.LrcParser
import com.example.domain.SyncedLyrics
import com.example.domain.Track
import com.example.ui.theme.SlantCyan
import com.example.ui.theme.SlantMagenta
import com.example.ui.theme.SlantObsidian
import com.example.ui.theme.SlantViolet
import kotlin.math.abs
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsBottomSheet(
    track: Track,
    lyrics: SyncedLyrics?,
    isLoading: Boolean,
    positionMs: Long,
    onSeekToMs: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()

    val activeIndex = if (lyrics != null && lyrics.isSynced) {
        LrcParser.findActiveLineIndex(lyrics.lines, positionMs)
    } else {
        -1
    }

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && lyrics != null && activeIndex < lyrics.lines.size) {
            val targetScrollIndex = (activeIndex - 2).coerceAtLeast(0)
            runCatching {
                listState.animateScrollToItem(targetScrollIndex)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SlantObsidian,
        dragHandle = null,
        modifier = Modifier.testTag("lyrics_bottom_sheet")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(620.dp)
        ) {
            if (track.artworkUrl.isNotBlank()) {
                AsyncImage(
                    model = track.artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(48.dp)
                        .alpha(0.32f)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                SlantObsidian.copy(alpha = 0.78f),
                                SlantObsidian.copy(alpha = 0.90f),
                                SlantObsidian.copy(alpha = 0.98f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                // Header with weighted column + MarqueeText so close button never clips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lyrics,
                            contentDescription = null,
                            tint = SlantCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            MarqueeText(
                                text = track.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${track.artist} • LRCLIB Караоке",
                                style = MaterialTheme.typography.bodySmall,
                                color = SlantCyan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("close_lyrics_sheet_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть текст песни",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = SlantCyan)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Загрузка синхронизированных таймкодов LRCLIB…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.8f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    lyrics == null || (lyrics.lines.isEmpty() && lyrics.plainLyrics.isBlank()) || lyrics.isInstrumental -> {
                        InstrumentalWaveformView(
                            title = track.title,
                            isInstrumental = lyrics?.isInstrumental == true
                        )
                    }

                    lyrics.isSynced -> {
                        Text(
                            text = "Нажмите на любую строчку для мгновенной перемотки по таймкоду",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.55f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("synced_lyrics_list"),
                            contentPadding = PaddingValues(vertical = 28.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(lyrics.lines) { index, line ->
                                val isActive = index == activeIndex
                                val isPast = index < activeIndex

                                val scale by animateFloatAsState(
                                    targetValue = if (isActive) 1.03f else 0.97f,
                                    animationSpec = tween(250),
                                    label = "lyric_scale"
                                )
                                val textColor by animateColorAsState(
                                    targetValue = when {
                                        isActive -> SlantCyan
                                        isPast -> Color.White.copy(alpha = 0.45f)
                                        else -> Color.White.copy(alpha = 0.75f)
                                    },
                                    animationSpec = tween(250),
                                    label = "lyric_color"
                                )

                                Surface(
                                    color = if (isActive) {
                                        SlantCyan.copy(alpha = 0.14f)
                                    } else {
                                        Color.Transparent
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                        }
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { onSeekToMs(line.timestampMs) }
                                        .testTag("lyric_line_$index")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = formatLyricTimestamp(line.timestampMs),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isActive) SlantCyan else Color.White.copy(alpha = 0.38f),
                                            maxLines = 1,
                                            modifier = Modifier.width(48.dp)
                                        )
                                        Text(
                                            text = line.text,
                                            style = if (isActive) {
                                                MaterialTheme.typography.titleMedium.copy(
                                                    fontSize = 17.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            } else {
                                                MaterialTheme.typography.bodyLarge
                                            },
                                            color = textColor,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("plain_lyrics_list"),
                            contentPadding = PaddingValues(vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                Surface(
                                    color = SlantViolet.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                ) {
                                    Text(
                                        text = "Текст без таймкодов (Plain Lyrics)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                            val lines = lyrics.plainLyrics.lines()
                            itemsIndexed(lines) { _, textLine ->
                                Text(
                                    text = textLine,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White.copy(alpha = 0.88f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InstrumentalWaveformView(
    title: String,
    isInstrumental: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "instrumental_wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "inst_phase"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("instrumental_waveform_indicator"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = SlantCyan,
            modifier = Modifier.size(42.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = if (isInstrumental) {
                "Инструментальная композиция ♪"
            } else {
                "Синхронный текст для «$title» ещё не индексирован в LRCLIB"
            },
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Активен акустический визуализатор спектра SlantWave",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.65f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
        ) {
            val bars = 28
            val barWidth = size.width / (bars * 1.6f)
            val gap = (size.width - barWidth * bars) / (bars - 1)
            for (i in 0 until bars) {
                val amp = 0.2f + 0.8f * abs(sin(phase + i * 0.35f))
                val barH = size.height * amp
                val x = i * (barWidth + gap)
                val y = (size.height - barH) / 2f
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(SlantCyan, SlantViolet, SlantMagenta)),
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barH),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                )
            }
        }
    }
}

private fun formatLyricTimestamp(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return "%02d:%02d".format(mins, secs)
}
