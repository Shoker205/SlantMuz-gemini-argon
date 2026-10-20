package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.preferences.AppUserSettings
import com.example.domain.ProviderHealthStatus
import com.example.domain.RemoteStreamingConfig
import com.example.domain.SourceProvider
import com.example.domain.Track
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.MarqueeText
import com.example.ui.components.ProviderBadgeChip
import com.example.ui.theme.SlantAmber
import com.example.ui.theme.SlantCyan
import com.example.ui.theme.SlantEmerald
import com.example.ui.theme.SlantMagenta
import com.example.ui.theme.SlantSkyPastel
import com.example.ui.theme.SlantViolet
import com.example.widget.SlantMuzWidgetUpdater
import com.example.widget.WidgetSizePreset

@Composable
fun DeveloperSettingsScreen(
    userSettings: AppUserSettings,
    providerStatuses: List<ProviderHealthStatus>,
    remoteConfig: RemoteStreamingConfig,
    exoCacheBytes: Long,
    roomOfflineBytes: Long,
    currentTrack: Track?,
    isPlaying: Boolean,
    activeLyricLine: String?,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleDarkTheme: (Boolean) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onToggleProvider: (SourceProvider, Boolean) -> Unit,
    onPingAllProviders: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onMaxCacheSizeChange: (Int) -> Unit,
    onClearAllCaches: () -> Unit,
    onSyncRemoteConfig: () -> Unit,
    onOverrideBitrate: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accentColor = MaterialTheme.colorScheme.primary

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("developer_settings_screen"),
        contentPadding = PaddingValues(top = 6.dp, bottom = 128.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Виджеты, DSP & Сетевой Хаб",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Виджеты рабочего стола (2×1, 4×2, 4×3), Liquid Glass дизайн и монитор провайдеров",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 1. Home Screen Widgets Gallery (2x1 Small, 4x2 Medium, 4x3 Large)
        item {
            LiquidGlassCard(
                cornerRadius = 22.dp,
                tintColor = SlantCyan,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_widgets_gallery_card")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Widgets,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Виджеты для рабочего стола (3 размера)",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Живой предпросмотр и закрепление на домашнем экране",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // 2x1 Small Widget Preview + Pin
                    WidgetPreviewShowcaseItem(
                        preset = WidgetSizePreset.SMALL,
                        track = currentTrack,
                        isPlaying = isPlaying,
                        activeLyricLine = activeLyricLine,
                        onPlayPause = onPlayPause,
                        onPrevious = onPrevious,
                        onNext = onNext,
                        onPinClick = {
                            val pinned = SlantMuzWidgetUpdater.requestPinWidget(context, WidgetSizePreset.SMALL)
                            val msg = if (pinned) {
                                "Запрос закрепления виджета 2×1 отправлен лаунчеру"
                            } else {
                                "Виджет 2×1 доступен в меню виджетов вашего рабочего стола"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    )

                    // 4x2 Medium Widget Preview + Pin
                    WidgetPreviewShowcaseItem(
                        preset = WidgetSizePreset.MEDIUM,
                        track = currentTrack,
                        isPlaying = isPlaying,
                        activeLyricLine = activeLyricLine,
                        onPlayPause = onPlayPause,
                        onPrevious = onPrevious,
                        onNext = onNext,
                        onPinClick = {
                            val pinned = SlantMuzWidgetUpdater.requestPinWidget(context, WidgetSizePreset.MEDIUM)
                            val msg = if (pinned) {
                                "Запрос закрепления виджета 4×2 отправлен лаунчеру"
                            } else {
                                "Виджет 4×2 доступен в меню виджетов вашего рабочего стола"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    )

                    // 4x3 Large Widget Preview + Pin
                    WidgetPreviewShowcaseItem(
                        preset = WidgetSizePreset.LARGE,
                        track = currentTrack,
                        isPlaying = isPlaying,
                        activeLyricLine = activeLyricLine,
                        onPlayPause = onPlayPause,
                        onPrevious = onPrevious,
                        onNext = onNext,
                        onPinClick = {
                            val pinned = SlantMuzWidgetUpdater.requestPinWidget(context, WidgetSizePreset.LARGE)
                            val msg = if (pinned) {
                                "Запрос закрепления виджета 4×3 отправлен лаунчеру"
                            } else {
                                "Виджет 4×3 доступен в меню виджетов вашего рабочего стола"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        // 2. Theme & Pastel Liquid Glass + Material 3 Settings
        item {
            LiquidGlassCard(
                cornerRadius = 22.dp,
                tintColor = SlantViolet,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Оформление Pastel Liquid Glass + M3",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 10.dp)
                        ) {
                            Text(
                                text = "Тёмное матовое стекло (Frosted Slate)",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Переключение между тёмным и светлым жемчужным стеклом",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Switch(
                            checked = userSettings.darkThemeMode,
                            onCheckedChange = onToggleDarkTheme,
                            modifier = Modifier.testTag("dark_theme_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 10.dp)
                        ) {
                            Text(
                                text = "Material You (Dynamic Color)",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Адаптивные пастельные тона обоев Android 12+",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Switch(
                            checked = userSettings.useDynamicColor,
                            onCheckedChange = onToggleDynamicColor,
                            modifier = Modifier.testTag("dynamic_color_switch")
                        )
                    }

                    FilledTonalButton(
                        onClick = onOpenEqualizer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .testTag("settings_open_dsp_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Equalizer,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "5-полосный DSP Эквалайзер (${userSettings.dspState.preset.displayName})",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 3. Firebase Remote Config Streaming Control
        item {
            LiquidGlassCard(
                cornerRadius = 22.dp,
                tintColor = SlantSkyPastel,
                modifier = Modifier.fillMaxWidth()
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Firebase Remote Config",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                MarqueeText(
                                    text = remoteConfig.configSource,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accentColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        FilledTonalButton(
                            onClick = onSyncRemoteConfig,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("sync_remote_config_btn")
                        ) {
                            Text(
                                text = "Sync",
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Text(
                        text = "Битрейт: ${remoteConfig.preferredBitrateKbps} kbps • Failover: ${remoteConfig.pipedFailoverTimeoutMs} ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(128, 256, 320).forEach { kbps ->
                            FilterChip(
                                selected = remoteConfig.preferredBitrateKbps == kbps,
                                onClick = { onOverrideBitrate(kbps) },
                                label = {
                                    Text(
                                        text = "$kbps kbps",
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.testTag("bitrate_chip_$kbps")
                            )
                        }
                    }
                }
            }
        }

        // 4. ExoPlayer SimpleCache & Room Offline Audio Storage Manager
        item {
            val totalMb = (exoCacheBytes + roomOfflineBytes) / (1024f * 1024f)
            val exoMb = exoCacheBytes / (1024f * 1024f)
            val roomMb = roomOfflineBytes / (1024f * 1024f)

            LiquidGlassCard(
                cornerRadius = 22.dp,
                tintColor = SlantEmerald,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Кэш аудио (SimpleCache + Room Offline)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    MarqueeText(
                        text = "Использовано: %.2f МБ (ExoPlayer: %.2f МБ • Room Offline: %.2f МБ) из %d МБ".format(
                            totalMb,
                            exoMb,
                            roomMb,
                            userSettings.maxCacheSizeMb
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Slider(
                        value = userSettings.maxCacheSizeMb.toFloat(),
                        onValueChange = { onMaxCacheSizeChange(it.toInt()) },
                        valueRange = 128f..2048f,
                        steps = 6,
                        modifier = Modifier.testTag("cache_limit_slider")
                    )

                    Button(
                        onClick = onClearAllCaches,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .testTag("clear_all_cache_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Очистить кэш ExoPlayer и оффлайн-файлы",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 5. Real-Time Multi-Source Provider Monitor
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Hub,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Провайдеры данных (${userSettings.enabledProviders.size}/8)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                FilledTonalButton(
                    onClick = onPingAllProviders,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("ping_all_providers_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.NetworkCheck,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Пинг нод",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        items(providerStatuses, key = { it.provider.name }) { status ->
            val isEnabled = status.provider in userSettings.enabledProviders
            val dotColor = when {
                !isEnabled -> MaterialTheme.colorScheme.outline
                status.isOnline && status.latencyMs in 1..450 -> Color(0xFF10B981)
                status.isOnline -> Color(0xFFF59E0B)
                else -> Color(0xFFE11D48)
            }

            LiquidGlassCard(
                cornerRadius = 18.dp,
                tintColor = if (isEnabled) SlantCyan else SlantViolet,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("provider_status_card_${status.provider.name}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ProviderBadgeChip(provider = status.provider)
                            MarqueeText(
                                text = status.provider.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text(
                            text = status.provider.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        MarqueeText(
                            text = buildString {
                                append("Нода: ${status.activeMirror}")
                                if (status.latencyMs > 0) append(" • Ping: ${status.latencyMs} ms")
                                if (status.tracksReturnedLastQuery > 0) {
                                    append(" • Ответов: ${status.tracksReturnedLastQuery}")
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor
                        )
                    }

                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { onToggleProvider(status.provider, it) },
                        modifier = Modifier.testTag("provider_toggle_${status.provider.name}")
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetPreviewShowcaseItem(
    preset: WidgetSizePreset,
    track: Track?,
    isPlaying: Boolean,
    activeLyricLine: String?,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPinClick: () -> Unit
) {
    val title = track?.title ?: "SlantMuz Pastel Stream"
    val artist = track?.artist ?: "SlantTech • 320kbps"

    LiquidGlassCard(
        cornerRadius = 18.dp,
        tintColor = when (preset) {
            WidgetSizePreset.SMALL -> SlantCyan
            WidgetSizePreset.MEDIUM -> SlantViolet
            WidgetSizePreset.LARGE -> SlantMagenta
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("widget_showcase_${preset.name}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    MarqueeText(
                        text = "${preset.gridSpec} • ${preset.title}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = preset.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                FilledTonalButton(
                    onClick = onPinClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("pin_widget_btn_${preset.name}")
                ) {
                    Icon(
                        imageVector = Icons.Default.AddToHomeScreen,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = preset.gridSpec,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Interactive miniature replica of the actual home screen widget size
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF1A2133),
                                Color(0xFF251F38)
                            )
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 6.dp)
                        ) {
                            Text(
                                text = "SLANTMUZ • ${preset.gridSpec}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SlantCyan,
                                maxLines = 1
                            )
                            MarqueeText(
                                text = title,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.74f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (preset != WidgetSizePreset.SMALL) {
                            IconButton(
                                onClick = onPrevious,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Предыдущий",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = onPlayPause,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = SlantCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = onNext,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Следующий",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (preset == WidgetSizePreset.MEDIUM || preset == WidgetSizePreset.LARGE) {
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { 0.42f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                            color = SlantCyan,
                            trackColor = Color.White.copy(alpha = 0.15f)
                        )
                    }

                    if (preset == WidgetSizePreset.LARGE) {
                        Spacer(modifier = Modifier.height(6.dp))
                        MarqueeText(
                            text = activeLyricLine?.let { "♪ $it" }
                                ?: "♪ Синхронный текст песни LRCLIB отображается прямо в виджете 4×3",
                            style = MaterialTheme.typography.labelSmall,
                            color = SlantViolet
                        )
                    }
                }
            }
        }
    }
}
