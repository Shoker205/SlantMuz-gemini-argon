package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.WaveActivity
import com.example.domain.WaveCharacter
import com.example.domain.WaveLanguage
import com.example.domain.WaveMood
import com.example.domain.WaveSettings
import com.example.ui.theme.SlantCyan
import com.example.ui.theme.SlantMagenta
import com.example.ui.theme.SlantViolet
import kotlin.math.sin

/**
 * Reusable animated wave banner ("Моя волна по треку / коллекции / артисту / плейлисту")
 * matching Screenshots 3, 5, 7, 9, 10 with flowing silk wave curves.
 */
@Composable
fun WaveBannerCard(
    subtitle: String,
    title: String,
    gradientColors: List<Color> = listOf(
        Color(0xFF4C1D95),
        Color(0xFF9333EA),
        Color(0xFFEC4899)
    ),
    onClick: () -> Unit,
    onSettingsClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "wave_banner")
    val wavePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(74.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(gradientColors))
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.10f))
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .testTag("wave_banner_card"),
        contentAlignment = Alignment.Center
    ) {
        // Animated silk ribbon waves
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            for (layer in 0..2) {
                val path = Path()
                val yOffset = h * (0.35f + layer * 0.22f)
                path.moveTo(0f, yOffset)
                var x = 0f
                while (x <= w) {
                    val y = yOffset + sin((x / w) * 5.2f + wavePhase + layer * 1.1f) * (h * 0.18f)
                    path.lineTo(x, y)
                    x += 16f
                }
                drawPath(
                    path = path,
                    color = Color.White.copy(alpha = 0.14f - layer * 0.03f),
                    style = Stroke(width = (3.5f - layer).dp.toPx())
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp)
        ) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.88f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                MarqueeText(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (onSettingsClick != null) {
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 6.dp)
                    .testTag("wave_banner_settings_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Настроить Мою волну",
                    tint = Color.White
                )
            }
        }
    }
}

/**
 * Full-featured "Настроить Мою волну" bottom sheet (Screenshots 1 & 2):
 * - под занятие: Просыпаюсь, В дороге, Работаю, Тренируюсь, Засыпаю
 * - по характеру: Любимое (Red 3D Heart), Незнакомое (Gold 4-Point Star), Популярное (Neon Lightning)
 * - под настроение: Бодрое, Весёлое, Спокойное, Грустное (4 glowing gradient spheres)
 * - По языку: Русский, Иностранный, Без слов
 * - Сбросить / Запустить
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaveSettingsBottomSheet(
    waveSettings: WaveSettings,
    onUpdateSettings: (WaveSettings) -> Unit,
    onLaunchWave: (WaveSettings) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0E1118),
        contentColor = Color.White,
        modifier = Modifier.testTag("wave_settings_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Настроить Мою волну",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 1. ПОД ЗАНЯТИЕ
            Text(
                text = "под занятие",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                WaveActivity.entries.forEach { act ->
                    val selected = waveSettings.activity == act
                    WavePillButton(
                        text = act.title,
                        selected = selected,
                        onClick = {
                            val updated = waveSettings.copy(
                                activity = if (selected) null else act
                            )
                            onUpdateSettings(updated)
                        },
                        modifier = Modifier.testTag("wave_activity_${act.name}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // 2. ПО ХАРАКТЕРУ (3 square cards with 3D icons)
            Text(
                text = "по характеру",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                WaveCharacter.entries.forEach { character ->
                    val selected = waveSettings.character == character
                    WaveCharacterCard(
                        character = character,
                        selected = selected,
                        onClick = {
                            val updated = waveSettings.copy(
                                character = if (selected) null else character
                            )
                            onUpdateSettings(updated)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("wave_character_${character.name}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // 3. ПОД НАСТРОЕНИЕ (4 glowing gradient spheres)
            Text(
                text = "под настроение",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                WaveMood.entries.forEach { mood ->
                    val selected = waveSettings.mood == mood
                    WaveMoodSphere(
                        mood = mood,
                        selected = selected,
                        onClick = {
                            val updated = waveSettings.copy(
                                mood = if (selected) null else mood
                            )
                            onUpdateSettings(updated)
                        },
                        modifier = Modifier.testTag("wave_mood_${mood.name}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // 4. ПО ЯЗЫКУ
            Text(
                text = "По языку",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                WaveLanguage.entries.forEach { lang ->
                    val selected = waveSettings.language == lang
                    WavePillButton(
                        text = lang.title,
                        selected = selected,
                        onClick = {
                            val updated = waveSettings.copy(
                                language = if (selected) null else lang
                            )
                            onUpdateSettings(updated)
                        },
                        modifier = Modifier.testTag("wave_lang_${lang.name}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 5. ALGORITHMIC SUMMARY & DSP AUTO-ADAPTATION
            val recommendedPreset = com.example.domain.SlantWaveEngine.recommendedEqualizerPreset(waveSettings)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF171A24),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlantCyan.copy(alpha = 0.28f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Алгоритм SlantWave: ${waveSettings.summarySubtitle()}",
                                style = MaterialTheme.typography.labelLarge,
                                color = SlantCyan,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = buildString {
                                    when (waveSettings.character) {
                                        WaveCharacter.FAVORITE -> append("Приоритет избранного и любимых артистов • ")
                                        WaveCharacter.UNFAMILIAR -> append("Строгое исключение знакомых треков • ")
                                        WaveCharacter.POPULAR -> append("Приоритет мировых чартов и хитов • ")
                                        null -> append("Баланс вкуса и открытий • ")
                                    }
                                    when (waveSettings.language) {
                                        WaveLanguage.RUSSIAN -> append("Фильтр кириллицы (RU)")
                                        WaveLanguage.FOREIGN -> append("Только зарубежные (EN/Global)")
                                        WaveLanguage.INSTRUMENTAL -> append("Без вокала (Instrumental)")
                                        null -> append("Мульти-языковой поток")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onUpdateSettings(
                                    waveSettings.copy(autoSyncEqualizer = !waveSettings.autoSyncEqualizer)
                                )
                            }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Авто-пресет DSP под волну: ${recommendedPreset.displayName}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.88f)
                        )
                        Text(
                            text = if (waveSettings.autoSyncEqualizer) "ВКЛ" else "ВЫКЛ",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (waveSettings.autoSyncEqualizer) Color(0xFFFACC15) else Color.White.copy(alpha = 0.45f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons: Launch Wave + Reset
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        onReset()
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(0.42f)
                        .height(48.dp)
                        .testTag("wave_reset_btn")
                ) {
                    Text(
                        text = "Сбросить",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (waveSettings.isCustomized) Color.White else Color.White.copy(alpha = 0.45f)
                    )
                }

                Button(
                    onClick = {
                        onLaunchWave(waveSettings)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SlantCyan,
                        contentColor = Color(0xFF0B0F1A)
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    modifier = Modifier
                        .weight(0.58f)
                        .height(48.dp)
                        .testTag("wave_apply_play_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Слушать волну",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun WavePillButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(26.dp),
        color = if (selected) Color.White else Color(0xFF1A1E29),
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(1.5.dp, SlantCyan)
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        },
        modifier = modifier.height(50.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 22.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) Color(0xFF0E1118) else Color.White,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun WaveCharacterCard(
    character: WaveCharacter,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = if (selected) Color(0xFF252B3B) else Color(0xFF171A24),
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) SlantCyan else Color.White.copy(alpha = 0.07f)
        ),
        modifier = modifier.height(124.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Custom 3D-styled graphic matching Screenshot 1 & 2
            Canvas(modifier = Modifier.size(54.dp)) {
                val w = size.width
                val h = size.height
                when (character) {
                    WaveCharacter.FAVORITE -> {
                        // 3D Ruby Heart
                        val heartPath = Path().apply {
                            moveTo(w * 0.5f, h * 0.88f)
                            cubicTo(w * 0.12f, h * 0.62f, w * 0.05f, h * 0.25f, w * 0.28f, h * 0.16f)
                            cubicTo(w * 0.42f, h * 0.10f, w * 0.5f, h * 0.24f, w * 0.5f, h * 0.30f)
                            cubicTo(w * 0.5f, h * 0.24f, w * 0.58f, h * 0.10f, w * 0.72f, h * 0.16f)
                            cubicTo(w * 0.95f, h * 0.25f, w * 0.88f, h * 0.62f, w * 0.5f, h * 0.88f)
                            close()
                        }
                        drawPath(
                            path = heartPath,
                            brush = Brush.linearGradient(
                                listOf(Color(0xFFFF4D6D), Color(0xFFC9184A), Color(0xFF800F2F))
                            )
                        )
                    }

                    WaveCharacter.UNFAMILIAR -> {
                        // 3D Golden 4-Point Sparkle Star
                        val starPath = Path().apply {
                            moveTo(w * 0.5f, h * 0.06f)
                            quadraticTo(w * 0.56f, h * 0.44f, w * 0.94f, h * 0.5f)
                            quadraticTo(w * 0.56f, h * 0.56f, w * 0.5f, h * 0.94f)
                            quadraticTo(w * 0.44f, h * 0.56f, w * 0.06f, h * 0.5f)
                            quadraticTo(w * 0.44f, h * 0.44f, w * 0.5f, h * 0.06f)
                            close()
                        }
                        drawPath(
                            path = starPath,
                            brush = Brush.linearGradient(
                                listOf(Color(0xFFFDE047), Color(0xFFF59E0B), Color(0xFFB45309))
                            )
                        )
                    }

                    WaveCharacter.POPULAR -> {
                        // 3D Neon Mint-Green Lightning Bolt
                        val boltPath = Path().apply {
                            moveTo(w * 0.28f, h * 0.14f)
                            lineTo(w * 0.82f, h * 0.14f)
                            lineTo(w * 0.58f, h * 0.44f)
                            lineTo(w * 0.78f, h * 0.44f)
                            lineTo(w * 0.22f, h * 0.90f)
                            lineTo(w * 0.42f, h * 0.54f)
                            lineTo(w * 0.24f, h * 0.54f)
                            close()
                        }
                        drawPath(
                            path = boltPath,
                            brush = Brush.linearGradient(
                                listOf(Color(0xFFA7F3D0), Color(0xFF10B981), Color(0xFF047857))
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = character.title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WaveMoodSphere(
    mood: WaveMood,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sphereColors = when (mood) {
        WaveMood.ENERGETIC -> listOf(Color(0xFFFACC15), Color(0xFFF97316), Color(0xFFEA580C))
        WaveMood.CHEERFUL -> listOf(Color(0xFFFEF08A), Color(0xFF84CC16), Color(0xFF22C55E))
        WaveMood.CALM -> listOf(Color(0xFF67E8F9), Color(0xFF14B8A6), Color(0xFF0284C7))
        WaveMood.SAD -> listOf(Color(0xFF38BDF8), Color(0xFF6366F1), Color(0xFF7C3AED))
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = sphereColors,
                        start = Offset(0f, 0f),
                        end = Offset(140f, 140f)
                    )
                )
                .then(
                    if (selected) {
                        Modifier.border(3.dp, Color.White, CircleShape)
                    } else {
                        Modifier
                    }
                )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = mood.title,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) SlantCyan else Color.White,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}
