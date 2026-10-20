package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.DspState
import com.example.domain.EqualizerPreset
import com.example.domain.StreamCandidate
import com.example.ui.theme.SlantCyan
import com.example.ui.theme.SlantEmerald
import com.example.ui.theme.SlantMagenta
import com.example.ui.theme.SlantObsidian
import com.example.ui.theme.SlantViolet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerBottomSheet(
    dspState: DspState,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectPreset: (EqualizerPreset) -> Unit,
    onBandGainChange: (Int, Float) -> Unit,
    onBassBoostChange: (Int) -> Unit,
    onVirtualizerChange: (Int) -> Unit,
    onLoudnessChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SlantObsidian,
        modifier = Modifier.testTag("equalizer_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
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
                        imageVector = Icons.Default.Equalizer,
                        contentDescription = null,
                        tint = SlantCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "5-полосный DSP Эквалайзер",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        MarqueeText(
                            text = if (dspState.hardwareAttached) {
                                "Аппаратный AudioFX подключен (Session #${dspState.activeAudioSessionId})"
                            } else {
                                "Программный профиль DSP активен (Safe HAL Mode)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = SlantCyan
                        )
                    }
                }

                Switch(
                    checked = dspState.enabled,
                    onCheckedChange = onToggleEnabled,
                    modifier = Modifier.testTag("dsp_enable_switch")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Preset chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EqualizerPreset.entries.forEach { preset ->
                    val selected = dspState.preset == preset
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectPreset(preset) },
                        label = {
                            Text(
                                text = preset.displayName,
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SlantCyan.copy(alpha = 0.24f),
                            selectedLabelColor = SlantCyan,
                            labelColor = Color.White
                        ),
                        modifier = Modifier.testTag("eq_preset_${preset.name}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 5 Parametric Frequency Bands
            val freqLabels = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")
            freqLabels.forEachIndexed { index, label ->
                val gain = dspState.bandGainsDb.getOrElse(index) { 0f }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        modifier = Modifier.width(60.dp)
                    )
                    Slider(
                        value = gain,
                        onValueChange = { onBandGainChange(index, it) },
                        valueRange = -12f..12f,
                        enabled = dspState.enabled,
                        colors = SliderDefaults.colors(
                            thumbColor = SlantCyan,
                            activeTrackColor = SlantCyan
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("eq_band_slider_$index")
                    )
                    Text(
                        text = "%+.1f dB".format(gain),
                        style = MaterialTheme.typography.labelSmall,
                        color = SlantCyan,
                        maxLines = 1,
                        modifier = Modifier.width(54.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // BassBoost, Virtualizer, LoudnessEnhancer
            DspEffectSliderRow(
                title = "BassBoost (Усиление баса)",
                valueText = "${dspState.bassBoostPercent}%",
                value = dspState.bassBoostPercent.toFloat(),
                range = 0f..100f,
                enabled = dspState.enabled,
                accent = SlantMagenta,
                onValueChange = { onBassBoostChange(it.toInt()) }
            )

            DspEffectSliderRow(
                title = "Virtualizer (3D Панорама)",
                valueText = "${dspState.virtualizerPercent}%",
                value = dspState.virtualizerPercent.toFloat(),
                range = 0f..100f,
                enabled = dspState.enabled,
                accent = SlantViolet,
                onValueChange = { onVirtualizerChange(it.toInt()) }
            )

            DspEffectSliderRow(
                title = "LoudnessEnhancer (Громкость)",
                valueText = "+%.1f dB".format(dspState.loudnessGainDb),
                value = dspState.loudnessGainDb,
                range = 0f..10f,
                enabled = dspState.enabled,
                accent = SlantEmerald,
                onValueChange = onLoudnessChange
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DspEffectSliderRow(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    accent: Color,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                maxLines = 1
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent
            )
        )
    }
}

@Composable
fun SleepTimerDialog(
    remainingSeconds: Int,
    onSelectMinutes: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val options = listOf(0 to "Выключить таймер", 15 to "15 минут", 30 to "30 минут", 45 to "45 минут", 60 to "60 минут")
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = null,
                tint = accentColor
            )
        },
        title = {
            Text(
                text = "Таймер сна (Sleep Timer)",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (remainingSeconds > 0) {
                    Text(
                        text = "Осталось до паузы: ${remainingSeconds / 60} мин ${remainingSeconds % 60} сек",
                        style = MaterialTheme.typography.bodyMedium,
                        color = accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                options.forEach { (mins, label) ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onSelectMinutes(mins)
                                onDismiss()
                            }
                            .testTag("sleep_timer_option_$mins")
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть", maxLines = 1)
            }
        }
    )
}

@Composable
fun StreamSelectorDialog(
    candidates: List<StreamCandidate>,
    activeCandidate: StreamCandidate?,
    onSelectCandidate: (StreamCandidate) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Агрегированные аудиопотоки",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "TrackDeduplicationEngine объединил зеркала для этого трека. При сбое плеер переключается автоматически.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                candidates.forEachIndexed { idx, candidate ->
                    val isSelected = activeCandidate?.streamUrl == candidate.streamUrl
                    Surface(
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onSelectCandidate(candidate)
                                onDismiss()
                            }
                            .testTag("stream_candidate_$idx")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            ) {
                                MarqueeText(
                                    text = candidate.provider.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                                MarqueeText(
                                    text = "${candidate.instanceHost} • ${candidate.bitrateKbps} kbps" +
                                        if (candidate.isPreview) " • HD Preview" else " • Full Stream",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            ProviderBadgeChip(provider = candidate.provider)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Готово", maxLines = 1)
            }
        }
    )
}
