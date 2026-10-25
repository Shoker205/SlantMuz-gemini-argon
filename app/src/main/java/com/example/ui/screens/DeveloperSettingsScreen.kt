package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.example.data.preferences.AppUserSettings
import com.example.domain.CloudAccountState
import com.example.domain.ProviderHealthStatus
import com.example.domain.RemoteStreamingConfig
import com.example.domain.SettingsSubCategory
import com.example.domain.SourceProvider
import com.example.domain.Track
import com.example.ui.components.AccountCloudSummaryCard
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

/**
 * Main "Настройки и прочее" Hub Screen:
 * Organizes all settings and tools into logical sorted groups and subcategories,
 * each opening in its own dedicated window (`SettingsSubCategoryScreen`).
 */
@Composable
fun DeveloperSettingsScreen(
    userSettings: AppUserSettings,
    providerStatuses: List<ProviderHealthStatus>,
    remoteConfig: RemoteStreamingConfig,
    exoCacheBytes: Long,
    roomOfflineBytes: Long,
    cloudAccountState: CloudAccountState = CloudAccountState(),
    resetTrigger: Int = 0,
    onOpenSubCategory: (SettingsSubCategory) -> Unit = {},
    onOpenEqualizer: () -> Unit = {},
    onOpenTasteSetup: () -> Unit = {},
    onOpenAccountAuth: () -> Unit = {},
    onSyncAccountNow: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(resetTrigger) {
        if (resetTrigger > 0) {
            listState.animateScrollToItem(0)
        }
    }

    val totalMb = (exoCacheBytes + roomOfflineBytes) / (1024f * 1024f)
    val onlineCount = providerStatuses.count { it.isOnline && it.provider in userSettings.enabledProviders }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("developer_settings_screen"),
        contentPadding = PaddingValues(top = 6.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Настройки и прочее",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Все параметры отсортированы по категориям — выберите раздел для открытия в отдельном окне",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        // GROUP 0: АККАУНТ, ОБЛАКО И МУЗЫКАЛЬНЫЙ ДНК
        item {
            SettingsGroupHeader(title = "Аккаунт, облако и персонализация")
        }

        item {
            AccountCloudSummaryCard(
                accountState = cloudAccountState,
                onOpenAuthSheet = onOpenAccountAuth,
                onSyncNow = onSyncAccountNow
            )
        }

        item {
            val genresCount = userSettings.tasteProfile.selectedGenreIds.size
            val subgenresCount = userSettings.tasteProfile.selectedSubgenreIds.size
            val artistsCount = userSettings.tasteProfile.selectedArtists.size
            SettingsCategoryCard(
                category = SettingsSubCategory.TASTE_DNA,
                statusBadge = "Жанров: $genresCount • Поджанров: $subgenresCount • Авторов: $artistsCount",
                icon = Icons.Default.AutoAwesome,
                tintColor = SlantCyan,
                onClick = { onOpenSubCategory(SettingsSubCategory.TASTE_DNA) },
                testTag = "settings_cat_taste_dna"
            )
        }

        // GROUP 1: АУДИО И ВОСПРОИЗВЕДЕНИЕ
        item {
            SettingsGroupHeader(title = "1. Аудио и воспроизведение")
        }

        item {
            SettingsCategoryCard(
                category = SettingsSubCategory.AUDIO_DSP,
                statusBadge = "Пресет: ${userSettings.dspState.preset.displayName}",
                icon = Icons.Default.Equalizer,
                tintColor = SlantMagenta,
                onClick = { onOpenSubCategory(SettingsSubCategory.AUDIO_DSP) },
                testTag = "settings_cat_audio_dsp"
            )
        }

        item {
            SettingsCategoryCard(
                category = SettingsSubCategory.STREAMING_QUALITY,
                statusBadge = "${remoteConfig.preferredBitrateKbps} kbps • Failover ${remoteConfig.pipedFailoverTimeoutMs} мс",
                icon = Icons.Default.Speed,
                tintColor = SlantSkyPastel,
                onClick = { onOpenSubCategory(SettingsSubCategory.STREAMING_QUALITY) },
                testTag = "settings_cat_streaming_quality"
            )
        }

        // GROUP 2: ИСТОЧНИКИ И ХРАНИЛИЩЕ
        item {
            SettingsGroupHeader(title = "2. Источники и память")
        }

        item {
            SettingsCategoryCard(
                category = SettingsSubCategory.PROVIDERS_HUB,
                statusBadge = "Активно: $onlineCount из ${SourceProvider.entries.size} источников",
                icon = Icons.Default.Hub,
                tintColor = SlantCyan,
                onClick = { onOpenSubCategory(SettingsSubCategory.PROVIDERS_HUB) },
                testTag = "settings_cat_providers_hub"
            )
        }

        item {
            SettingsCategoryCard(
                category = SettingsSubCategory.STORAGE_CACHE,
                statusBadge = "Занято %.1f МБ из %d МБ".format(totalMb, userSettings.maxCacheSizeMb),
                icon = Icons.Default.Storage,
                tintColor = SlantEmerald,
                onClick = { onOpenSubCategory(SettingsSubCategory.STORAGE_CACHE) },
                testTag = "settings_cat_storage_cache"
            )
        }

        // GROUP 3: ОФОРМЛЕНИЕ И ВИДЖЕТЫ
        item {
            SettingsGroupHeader(title = "3. Интерфейс и виджеты")
        }

        item {
            SettingsCategoryCard(
                category = SettingsSubCategory.APPEARANCE_THEME,
                statusBadge = if (userSettings.darkThemeMode) "Тёмное стекло (Obsidian)" else "Светлое стекло (Pearl)",
                icon = Icons.Default.Palette,
                tintColor = SlantViolet,
                onClick = { onOpenSubCategory(SettingsSubCategory.APPEARANCE_THEME) },
                testTag = "settings_cat_appearance_theme"
            )
        }

        item {
            SettingsCategoryCard(
                category = SettingsSubCategory.HOME_WIDGETS,
                statusBadge = "Размеры: 2×1 Компактный, 4×2 Средний, 4×3 Большой",
                icon = Icons.Default.Widgets,
                tintColor = SlantAmber,
                onClick = { onOpenSubCategory(SettingsSubCategory.HOME_WIDGETS) },
                testTag = "settings_cat_home_widgets"
            )
        }

        // GROUP 4: О ПРИЛОЖЕНИИ
        item {
            SettingsGroupHeader(title = "4. Система и сведения")
        }

        item {
            SettingsCategoryCard(
                category = SettingsSubCategory.ABOUT_APP,
                statusBadge = "SlantMuz • Media3 ExoPlayer • Без API-ключей",
                icon = Icons.Default.Info,
                tintColor = SlantCyan,
                onClick = { onOpenSubCategory(SettingsSubCategory.ABOUT_APP) },
                testTag = "settings_cat_about_app"
            )
        }
    }
}

/**
 * Dedicated Window for each Settings & More Subcategory.
 */
@Composable
fun SettingsSubCategoryScreen(
    subCategory: SettingsSubCategory,
    userSettings: AppUserSettings,
    providerStatuses: List<ProviderHealthStatus>,
    remoteConfig: RemoteStreamingConfig,
    exoCacheBytes: Long,
    roomOfflineBytes: Long,
    currentTrack: Track?,
    isPlaying: Boolean,
    activeLyricLine: String?,
    cloudAccountState: CloudAccountState = CloudAccountState(),
    onBack: () -> Unit,
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
    onOpenTasteSetup: () -> Unit = {},
    onOpenAccountAuth: () -> Unit = {},
    onSyncAccountNow: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val accentColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("settings_sub_screen_${subCategory.id}")
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        // Top Bar with Back Arrow
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("settings_sub_back_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад в Настройки",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subCategory.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subCategory.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (subCategory) {
                SettingsSubCategory.ACCOUNT_CLOUD -> {
                    item {
                        AccountCloudSummaryCard(
                            accountState = cloudAccountState,
                            onOpenAuthSheet = onOpenAccountAuth,
                            onSyncNow = onSyncAccountNow
                        )
                    }
                    item {
                        LiquidGlassCard(
                            cornerRadius = 22.dp,
                            tintColor = SlantEmerald,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Облачная синхронизация Firebase Firestore",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "• Автоматическое резервное копирование любимых треков, плейлистов и Музыкального ДНК\n• Вход через Google (Credential Manager) или по Email/паролю\n• Все 56 узких поджанров и любимые авторы сохраняются при переустановке",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = onOpenAccountAuth,
                                    modifier = Modifier.fillMaxWidth().testTag("open_account_sheet_from_sub")
                                ) {
                                    Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (cloudAccountState.isSignedIn) "Управление аккаунтом и синхронизацией" else "Войти или зарегистрироваться")
                                }
                            }
                        }
                    }
                }

                SettingsSubCategory.TASTE_DNA -> {
                    item {
                        LiquidGlassCard(
                            cornerRadius = 22.dp,
                            tintColor = SlantCyan,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Музыкальный ДНК и предварительная настройка",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Выбранные жанры: ${userSettings.tasteProfile.selectedGenres.joinToString(", ") { it.title }.ifBlank { "Авто-определение по коллекции" }}\n" +
                                        "Точечные поджанры: ${userSettings.tasteProfile.selectedSubgenres.joinToString(", ") { it.title }.ifBlank { "Не выбраны (рекомендуется выбрать)" }}\n" +
                                        "Выбранные авторы: ${userSettings.tasteProfile.selectedArtists.joinToString(", ").ifBlank { "Из ваших лайков и плейлистов" }}\n" +
                                        "Любимых альбомов: ${userSettings.tasteProfile.likedAlbums.size}\n" +
                                        "Режим рекомендаций: ${userSettings.tasteProfile.discoveryBalance.title}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = onOpenTasteSetup,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("settings_open_taste_wizard_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Открыть мастер настройки вкусов и ДНК")
                                }
                            }
                        }
                    }
                }

                SettingsSubCategory.AUDIO_DSP -> {
                    item {
                        LiquidGlassCard(
                            cornerRadius = 22.dp,
                            tintColor = SlantMagenta,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Аппаратный звуковой процессор (DSP)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Активный профиль: ${userSettings.dspState.preset.displayName} • Bass Boost: ${userSettings.dspState.bassBoostPercent}% • Virtualizer: ${userSettings.dspState.virtualizerPercent}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = onOpenEqualizer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("settings_open_dsp_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Equalizer,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Открыть пульт 5-полосного эквалайзера")
                                }
                            }
                        }
                    }
                }

                SettingsSubCategory.STREAMING_QUALITY -> {
                    item {
                        LiquidGlassCard(
                            cornerRadius = 22.dp,
                            tintColor = SlantSkyPastel,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Параметры потокового аудио",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = remoteConfig.configSource,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = accentColor
                                            )
                                        }
                                    }

                                    FilledTonalButton(
                                        onClick = onSyncRemoteConfig,
                                        modifier = Modifier.testTag("sync_remote_config_btn")
                                    ) {
                                        Text("Обновить")
                                    }
                                }

                                Text(
                                    text = "Предпочтительный битрейт: ${remoteConfig.preferredBitrateKbps} kbps • Таймаут зеркал: ${remoteConfig.pipedFailoverTimeoutMs} мс",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(128, 256, 320).forEach { kbps ->
                                        FilterChip(
                                            selected = remoteConfig.preferredBitrateKbps == kbps,
                                            onClick = { onOverrideBitrate(kbps) },
                                            label = { Text("$kbps kbps") },
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
                }

                SettingsSubCategory.PROVIDERS_HUB -> {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Активно источников: ${userSettings.enabledProviders.size}/${SourceProvider.entries.size}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )

                            FilledTonalButton(
                                onClick = onPingAllProviders,
                                modifier = Modifier.testTag("ping_all_providers_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NetworkCheck,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Пинг всех нод")
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
                                            if (status.latencyMs > 0) append(" • Ping: ${status.latencyMs} мс")
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

                SettingsSubCategory.STORAGE_CACHE -> {
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
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Хранилище ExoPlayer SimpleCache и Room Offline",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = "Использовано: %.2f МБ (Потоковый кэш: %.2f МБ • Скачано оффлайн: %.2f МБ) из %d МБ".format(
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
                                        .testTag("clear_all_cache_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteSweep,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Очистить кэш ExoPlayer и оффлайн-файлы")
                                }
                            }
                        }
                    }
                }

                SettingsSubCategory.APPEARANCE_THEME -> {
                    item {
                        LiquidGlassCard(
                            cornerRadius = 22.dp,
                            tintColor = SlantViolet,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
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
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Переключение между тёмным обсидиановым и светлым жемчужным оформлением",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Адаптивные пастельные оттенки на основе системных обоев Android 12+",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = userSettings.useDynamicColor,
                                        onCheckedChange = onToggleDynamicColor,
                                        modifier = Modifier.testTag("dynamic_color_switch")
                                    )
                                }
                            }
                        }
                    }
                }

                SettingsSubCategory.HOME_WIDGETS -> {
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
                }

                SettingsSubCategory.ABOUT_APP -> {
                    item {
                        LiquidGlassCard(
                            cornerRadius = 22.dp,
                            tintColor = SlantCyan,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "SlantMuz by SlantTech",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "Версия 2.4 • Нативный аудиостриминговый комбайн и локальный плеер без обязательных API-ключей.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "• Аудиоядро: AndroidX Media3 ExoPlayer + MediaSessionService\n• Источники: Jamendo CC HD, Audius Web3, YouTube Piped/Invidious, Deezer, Apple iTunes, Radio Browser, Internet Archive и MediaStore\n• Караоке-лирика: LRCLIB + Lyrics.ovh\n• База данных: Room + DataStore Preferences",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
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
private fun SettingsGroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
    )
}

@Composable
private fun SettingsCategoryCard(
    category: SettingsSubCategory,
    statusBadge: String,
    icon: ImageVector,
    tintColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    LiquidGlassCard(
        cornerRadius = 20.dp,
        tintColor = tintColor,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(tintColor.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = category.title,
                    tint = tintColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = category.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = statusBadge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Открыть окно",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    val title = track?.title ?: "Нет активного трека"
    val artist = track?.artist ?: "Выберите трек для отображения в виджете"

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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF141B2D),
                                Color(0xFF231C3A),
                                Color(0xFF0E2936)
                            )
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "widget_eq_${preset.name}")
                val bar1 = infiniteTransition.animateFloat(
                    initialValue = 0.28f,
                    targetValue = if (isPlaying) 0.95f else 0.32f,
                    animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "b1"
                )
                val bar2 = infiniteTransition.animateFloat(
                    initialValue = 0.45f,
                    targetValue = if (isPlaying) 0.85f else 0.45f,
                    animationSpec = infiniteRepeatable(tween(580, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "b2"
                )
                val bar3 = infiniteTransition.animateFloat(
                    initialValue = 0.22f,
                    targetValue = if (isPlaying) 1.0f else 0.25f,
                    animationSpec = infiniteRepeatable(tween(360, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "b3"
                )

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Animated equalizer spectrum badge inside widget preview
                        Row(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(SlantCyan.copy(alpha = 0.16f))
                                .padding(horizontal = 7.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            listOf(bar1.value, bar2.value, bar3.value, bar1.value).forEach { frac ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height((22f * frac).dp)
                                        .clip(CircleShape)
                                        .background(SlantCyan)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 6.dp)
                        ) {
                            Text(
                                text = "SLANTMUZ • ${preset.gridSpec} • 320KBPS FULL",
                                style = MaterialTheme.typography.labelSmall,
                                color = SlantCyan,
                                fontWeight = FontWeight.Bold,
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
                                modifier = Modifier.size(32.dp)
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
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SlantCyan.copy(alpha = 0.22f))
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
                            modifier = Modifier.size(32.dp)
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
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { if (track != null) 0.42f else 0.15f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape),
                            color = SlantCyan,
                            trackColor = Color.White.copy(alpha = 0.15f)
                        )
                    }

                    if (preset == WidgetSizePreset.LARGE) {
                        Spacer(modifier = Modifier.height(6.dp))
                        MarqueeText(
                            text = if (!activeLyricLine.isNullOrBlank()) "♪ $activeLyricLine" else "♪ Полный аудиопоток 320 kbps • Караоке LRCLIB активно",
                            style = MaterialTheme.typography.labelSmall,
                            color = SlantEmerald
                        )
                    }
                }
            }
        }
    }
}
