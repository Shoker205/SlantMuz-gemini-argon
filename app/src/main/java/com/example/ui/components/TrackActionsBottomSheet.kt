package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.domain.Track
import com.example.ui.theme.SlantCyan
import com.example.ui.theme.SlantEmerald
import com.example.ui.theme.SlantMagenta
import com.example.ui.theme.SlantViolet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackActionsBottomSheet(
    track: Track,
    onDismiss: () -> Unit,
    onStartTrackWave: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onDownloadOffline: (Track) -> Unit,
    onPlayTrailerHighlight: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onRemoveFromQueue: (Track) -> Unit,
    onDislikeTrack: (Track) -> Unit,
    onOpenArtist: (String) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showTrackInfoDialog by remember { mutableStateOf(false) }
    var showAudioDeviceSheet by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF14161F),
        contentColor = Color.White,
        modifier = Modifier.testTag("track_actions_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            // 1. Track Header Row (Cover + Title + Duration + >)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { showTrackInfoDialog = true }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(listOf(SlantCyan.copy(alpha = 0.4f), SlantViolet.copy(alpha = 0.4f)))
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
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    MarqueeText(
                        text = track.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${track.formattedDuration} • ${track.primaryProvider.displayName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "О треке",
                    tint = Color.White.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Tappable Artist Avatar + Artist Name Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        onDismiss()
                        onOpenArtist(track.artist)
                    }
                    .padding(vertical = 6.dp)
                    .testTag("sheet_open_artist_row"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF282D3E)),
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
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                MarqueeText(
                    text = track.artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. "Моя волна по треку" Banner
            WaveBannerCard(
                subtitle = "Моя волна по треку",
                title = track.title,
                gradientColors = listOf(
                    Color(0xFF5C4033),
                    Color(0xFF8B6F56),
                    Color(0xFF4A3728)
                ),
                onClick = {
                    onStartTrackWave(track)
                    onDismiss()
                }
            )

            Spacer(modifier = Modifier.height(22.dp))

            // 4. Three Circular Action Buttons: Нравится | Скачать | Трейлер
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularSheetActionButton(
                    icon = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    label = "Нравится",
                    iconTint = if (track.isFavorite) SlantMagenta else Color.White,
                    onClick = { onToggleFavorite(track) },
                    testTag = "sheet_action_like"
                )

                CircularSheetActionButton(
                    icon = if (track.isCachedOffline) Icons.Default.CloudDone else Icons.Default.Download,
                    label = if (track.isCachedOffline) "Скачано" else "Скачать",
                    iconTint = if (track.isCachedOffline) SlantEmerald else Color.White,
                    onClick = {
                        onDownloadOffline(track)
                        Toast.makeText(context, "Загрузка «${track.title}» в оффлайн-кэш…", Toast.LENGTH_SHORT).show()
                    },
                    testTag = "sheet_action_download"
                )

                CircularSheetActionButton(
                    icon = Icons.Default.GraphicEq,
                    label = "Трейлер",
                    iconTint = SlantCyan,
                    onClick = {
                        onPlayTrailerHighlight(track)
                        Toast.makeText(context, "Воспроизведение фрагмента «${track.title}»…", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    testTag = "sheet_action_trailer"
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 5. Menu Action Rows
            SheetMenuRow(
                icon = Icons.Default.Share,
                title = "Поделиться",
                onClick = {
                    runCatching {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Слушаю в SlantMuz: ${track.artist} — ${track.title} (${track.primaryProvider.displayName})"
                            )
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Поделиться треком"))
                    }
                    onDismiss()
                },
                testTag = "sheet_menu_share"
            )

            SheetMenuRow(
                icon = Icons.Default.Add,
                title = "Добавить в плейлист",
                onClick = {
                    onDismiss()
                    onAddToPlaylist(track)
                },
                testTag = "sheet_menu_add_playlist"
            )

            SheetMenuRow(
                icon = Icons.Default.Cast,
                title = "Аудиовыходы устройства",
                onClick = { showAudioDeviceSheet = true },
                testTag = "sheet_menu_cast_device"
            )

            SheetMenuRow(
                icon = Icons.Default.DeleteOutline,
                title = "Удалить из очереди",
                tint = Color.White.copy(alpha = 0.72f),
                onClick = {
                    onRemoveFromQueue(track)
                    Toast.makeText(context, "Удалено из очереди", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                testTag = "sheet_menu_remove_queue"
            )

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier.padding(vertical = 8.dp)
            )

            SheetMenuRow(
                icon = Icons.Default.Info,
                title = "О треке",
                tint = Color.White.copy(alpha = 0.72f),
                onClick = { showTrackInfoDialog = true },
                testTag = "sheet_menu_track_info"
            )

            SheetMenuRow(
                icon = Icons.Default.HeartBroken,
                title = "Не нравится",
                tint = Color.White.copy(alpha = 0.72f),
                onClick = {
                    onDislikeTrack(track)
                    Toast.makeText(context, "Трек скрыт из рекомендаций Моей волны", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                testTag = "sheet_menu_dislike"
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showTrackInfoDialog) {
        TrackTechnicalInfoDialog(
            track = track,
            onDismiss = { showTrackInfoDialog = false }
        )
    }

    if (showAudioDeviceSheet) {
        AudioOutputDeviceDialog(
            onDismiss = { showAudioDeviceSheet = false }
        )
    }
}

@Composable
private fun CircularSheetActionButton(
    icon: ImageVector,
    label: String,
    iconTint: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
            .testTag(testTag)
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFF232734),
            modifier = Modifier.size(60.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White
        )
    }
}

@Composable
private fun SheetMenuRow(
    icon: ImageVector,
    title: String,
    tint: Color = Color.White,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp, horizontal = 4.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = tint,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun TrackTechnicalInfoDialog(
    track: Track,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            MarqueeText(
                text = "О треке • ${track.title}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Исполнитель: ${track.artist}", color = MaterialTheme.colorScheme.onSurface)
                if (track.album.isNotBlank()) {
                    Text("Альбом: ${track.album}", color = MaterialTheme.colorScheme.onSurface)
                }
                Text("Жанр: ${track.genre}", color = MaterialTheme.colorScheme.onSurface)
                Text("Длительность: ${track.formattedDuration}", color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "Основной источник: ${track.primaryProvider.displayName} (${track.primaryProvider.defaultBitrateKbps} kbps)",
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Доступно зеркал потока: ${track.allStreamCandidates().size}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Оффлайн-кэш Room: ${if (track.isCachedOffline) "Сохранён на устройстве" else "Потоковый режим"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}

@Composable
fun AudioOutputDeviceDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val realDevices = remember(context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val outputs = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty()
        outputs.map { info ->
            val typeName = when (info.type) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Динамик устройства"
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_USB_HEADSET -> "Проводные наушники"
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BLE_SPEAKER -> "Bluetooth аудиоустройство"
                else -> "Системный аудиовыход"
            }
            val icon = when (info.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BLE_SPEAKER -> Icons.Default.BluetoothAudio
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_USB_HEADSET -> Icons.Default.Headphones
                else -> Icons.Default.Speaker
            }
            val product = info.productName?.toString()?.takeIf { it.isNotBlank() } ?: typeName
            Triple(product, typeName, icon)
        }.distinctBy { it.first + it.second }
            .ifEmpty {
                listOf(Triple("Основной динамик Android", "Системный аудиовыход по умолчанию", Icons.Default.Speaker))
            }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Аудиовыходы устройства",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                realDevices.forEachIndexed { idx, (name, sub, icon) ->
                    Surface(
                        onClick = {
                            Toast.makeText(context, "Активный выход: $name", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (idx == 0) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = sub,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Готово")
            }
        }
    )
}
