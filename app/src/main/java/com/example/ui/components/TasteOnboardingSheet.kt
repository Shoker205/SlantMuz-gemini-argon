package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.domain.ArtistSuggestion
import com.example.domain.SlantWaveEngine
import com.example.domain.TasteDiscoveryBalance
import com.example.domain.TasteEraOption
import com.example.domain.TasteGenreOption
import com.example.domain.Track
import com.example.domain.UserTasteDnaSummary
import com.example.domain.UserTasteProfile
import com.example.domain.WaveLanguage
import com.example.ui.theme.SlantCyan
import com.example.ui.theme.SlantEmerald
import com.example.ui.theme.SlantMagenta
import com.example.ui.theme.SlantViolet

/**
 * Interactive Pre-Configuration & Musical DNA Wizard ("Предварительная настройка вкусов и ДНК").
 * Includes live artist autocomplete dropdown with cover art, starter artist avatars,
 * genre selection, and full-length track prioritization.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TasteOnboardingSheet(
    currentProfile: UserTasteProfile,
    dnaSummary: UserTasteDnaSummary,
    favoriteTracks: List<Track>,
    playlistTracks: List<Track>,
    historyTracks: List<Track>,
    artistSuggestions: List<ArtistSuggestion> = emptyList(),
    isArtistSuggestLoading: Boolean = false,
    artistArtworks: Map<String, String> = emptyMap(),
    onArtistQueryChange: (String) -> Unit = {},
    onClearArtistSuggestions: () -> Unit = {},
    onSaveProfile: (UserTasteProfile, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedGenres by remember(currentProfile) { mutableStateOf(currentProfile.selectedGenreIds) }
    var selectedArtists by remember(currentProfile) { mutableStateOf(currentProfile.selectedArtists) }
    var draftArtistArtworks by remember(currentProfile, artistArtworks) {
        mutableStateOf(currentProfile.artistArtworks + artistArtworks)
    }
    var preferredLanguages by remember(currentProfile) { mutableStateOf(currentProfile.preferredLanguages) }
    var preferredEras by remember(currentProfile) { mutableStateOf(currentProfile.preferredEras) }
    var discoveryBalance by remember(currentProfile) { mutableStateOf(currentProfile.discoveryBalance) }
    var preferFullLength by remember(currentProfile) { mutableStateOf(currentProfile.preferFullLengthTracks) }
    var autoAdaptCollection by remember(currentProfile) { mutableStateOf(currentProfile.autoAdaptFromCollection) }

    var activeTab by remember { mutableIntStateOf(0) }
    var customArtistInput by remember { mutableStateOf("") }
    var artistGenreFilter by remember { mutableStateOf<String?>(null) }

    val collectionArtistArtworkMap = remember(favoriteTracks, playlistTracks, historyTracks) {
        val map = mutableMapOf<String, String>()
        (favoriteTracks + playlistTracks + historyTracks).forEach { track ->
            val key = track.artist.trim().lowercase()
            if (key.isNotBlank() && track.artworkUrl.isNotBlank() && key !in map) {
                map[key] = track.artworkUrl
            }
        }
        map
    }

    val collectionArtists = remember(favoriteTracks, playlistTracks, historyTracks) {
        (favoriteTracks.map { it.artist } + playlistTracks.map { it.artist } + historyTracks.map { it.artist })
            .map { it.trim() }
            .filter { it.length >= 2 && !it.equals("Unknown", ignoreCase = true) }
            .distinctBy { it.lowercase() }
            .take(18)
    }

    fun resolveArtworkForArtist(artistName: String): String {
        val key = artistName.trim().lowercase()
        return draftArtistArtworks[key]
            ?: artistArtworks[key]
            ?: collectionArtistArtworkMap[key]
            ?: artistSuggestions.firstOrNull { it.name.equals(artistName, ignoreCase = true) }?.artworkUrl
            ?: ""
    }

    val liveDraftProfile = remember(
        selectedGenres,
        selectedArtists,
        draftArtistArtworks,
        preferredLanguages,
        preferredEras,
        discoveryBalance,
        preferFullLength,
        autoAdaptCollection
    ) {
        currentProfile.copy(
            isOnboardingCompleted = true,
            selectedGenreIds = selectedGenres,
            selectedArtists = selectedArtists,
            artistArtworks = draftArtistArtworks,
            preferredLanguages = preferredLanguages,
            preferredEras = preferredEras,
            discoveryBalance = discoveryBalance,
            preferFullLengthTracks = preferFullLength,
            autoAdaptFromCollection = autoAdaptCollection
        )
    }

    val liveDna = remember(liveDraftProfile, favoriteTracks, playlistTracks, historyTracks) {
        SlantWaveEngine.computeTasteDnaSummary(
            tasteProfile = liveDraftProfile,
            favoriteTracks = favoriteTracks,
            playlistTracks = playlistTracks,
            historyTracks = historyTracks,
            likedArtists = selectedArtists
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF101420),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 18.dp)
                .testTag("taste_onboarding_sheet")
        ) {
            // Top Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(listOf(SlantCyan, SlantViolet, SlantMagenta))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFF0E1118),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Музыкальный ДНК • Предпочтения",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Точность рекомендаций: ${liveDna.personalizationPercent}% • Жанров: ${selectedGenres.size} • Авторов: ${selectedArtists.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SlantCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_taste_sheet_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Закрыть",
                        tint = Color.White.copy(alpha = 0.75f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3 Step Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val tabs = listOf(
                    "1. Жанры (${selectedGenres.size})",
                    "2. Авторы (${selectedArtists.size})",
                    "3. Алгоритм и база"
                )
                tabs.forEachIndexed { index, title ->
                    val isSelected = activeTab == index
                    Surface(
                        onClick = { activeTab = index },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) SlantCyan.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.07f),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) SlantCyan else Color.White.copy(alpha = 0.12f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("taste_tab_$index")
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 9.dp, horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) SlantCyan else Color.White.copy(alpha = 0.8f),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Scrollable Tab Content
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (activeTab) {
                    // TAB 0: GENRES
                    0 -> {
                        item {
                            Text(
                                text = "Выберите любимые жанры и музыкальные направления — алгоритм перестроит «Мою волну», главную витрину и умные миксы под ваш стиль:",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.72f)
                            )
                        }

                        items(TasteGenreOption.entries, key = { it.id }) { genre ->
                            val isSelected = genre.id in selectedGenres
                            val isInferredFromCollection = genre in dnaSummary.topGenres && !isSelected

                            Surface(
                                onClick = {
                                    selectedGenres = if (isSelected) {
                                        selectedGenres - genre.id
                                    } else {
                                        selectedGenres + genre.id
                                    }
                                },
                                shape = RoundedCornerShape(18.dp),
                                color = if (isSelected) {
                                    SlantCyan.copy(alpha = 0.18f)
                                } else {
                                    Color.White.copy(alpha = 0.06f)
                                },
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = when {
                                        isSelected -> SlantCyan
                                        isInferredFromCollection -> SlantViolet.copy(alpha = 0.55f)
                                        else -> Color.White.copy(alpha = 0.1f)
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("taste_genre_card_${genre.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isSelected) SlantCyan.copy(alpha = 0.28f)
                                                else Color.White.copy(alpha = 0.08f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Default.Check else Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = if (isSelected) SlantCyan else Color.White.copy(alpha = 0.75f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = genre.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = if (isSelected) SlantCyan else Color.White,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (isInferredFromCollection) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "• В ваших лайках",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = SlantViolet,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                        Text(
                                            text = genre.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.65f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // TAB 1: ARTISTS WITH LIVE AUTOCOMPLETE + COVER ART
                    1 -> {
                        item {
                            Text(
                                text = "Начните вводить имя автора для выбора из живых подсказок с обложками или отметьте любимых исполнителей ниже:",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.72f)
                            )
                        }

                        // Custom artist input row + live autocomplete popup card
                        item {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = customArtistInput,
                                        onValueChange = { newText ->
                                            customArtistInput = newText
                                            onArtistQueryChange(newText)
                                        },
                                        placeholder = { Text("Поиск автора (например: Кино, Miyagi, Weeknd)…") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Search,
                                                contentDescription = null,
                                                tint = SlantCyan
                                            )
                                        },
                                        trailingIcon = {
                                            if (isArtistSuggestLoading) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(18.dp),
                                                    strokeWidth = 2.dp,
                                                    color = SlantCyan
                                                )
                                            } else if (customArtistInput.isNotEmpty()) {
                                                IconButton(
                                                    onClick = {
                                                        customArtistInput = ""
                                                        onClearArtistSuggestions()
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Очистить",
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("custom_artist_input")
                                    )

                                    Button(
                                        onClick = {
                                            val clean = customArtistInput.trim()
                                            if (clean.length >= 2) {
                                                val topMatch = artistSuggestions.firstOrNull {
                                                    it.name.equals(clean, ignoreCase = true)
                                                }
                                                val canonicalName = topMatch?.name ?: clean
                                                selectedArtists = selectedArtists + canonicalName
                                                if (topMatch != null && topMatch.artworkUrl.isNotBlank()) {
                                                    draftArtistArtworks = draftArtistArtworks +
                                                        (canonicalName.lowercase() to topMatch.artworkUrl)
                                                }
                                                val topGenreId = topMatch?.genreId
                                                if (!topGenreId.isNullOrBlank()) {
                                                    selectedGenres = selectedGenres + topGenreId
                                                }
                                                customArtistInput = ""
                                                onClearArtistSuggestions()
                                            }
                                        },
                                        enabled = customArtistInput.trim().length >= 2,
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = SlantCyan,
                                            contentColor = Color(0xFF0E1118)
                                        ),
                                        modifier = Modifier
                                            .height(52.dp)
                                            .testTag("add_custom_artist_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Добавить", fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Live Autocomplete Suggestions Dropdown with Artist Cover Art
                                AnimatedVisibility(
                                    visible = customArtistInput.trim().length >= 2 && artistSuggestions.isNotEmpty()
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(18.dp),
                                        color = Color(0xFF171D2E),
                                        border = BorderStroke(1.2.dp, SlantCyan.copy(alpha = 0.55f)),
                                        tonalElevation = 8.dp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp)
                                            .testTag("artist_autocomplete_dropdown")
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = "Подсказки исполнителей — нажмите, чтобы выбрать:",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SlantCyan,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                                            )

                                            artistSuggestions.take(7).forEachIndexed { idx, suggestion ->
                                                val alreadySelected = selectedArtists.any {
                                                    it.equals(suggestion.name, ignoreCase = true)
                                                }
                                                val avatarUrl = suggestion.artworkUrl.ifBlank {
                                                    resolveArtworkForArtist(suggestion.name)
                                                }

                                                Surface(
                                                    onClick = {
                                                        val existing = selectedArtists.firstOrNull {
                                                            it.equals(suggestion.name, ignoreCase = true)
                                                        }
                                                        selectedArtists = if (existing != null) {
                                                            selectedArtists - existing
                                                        } else {
                                                            selectedArtists + suggestion.name
                                                        }
                                                        if (avatarUrl.isNotBlank()) {
                                                            draftArtistArtworks = draftArtistArtworks +
                                                                (suggestion.name.lowercase() to avatarUrl)
                                                        }
                                                        val sugGenreId = suggestion.genreId
                                                        if (existing == null && !sugGenreId.isNullOrBlank()) {
                                                            selectedGenres = selectedGenres + sugGenreId
                                                        }
                                                        customArtistInput = ""
                                                        onClearArtistSuggestions()
                                                    },
                                                    color = if (alreadySelected) {
                                                        SlantCyan.copy(alpha = 0.14f)
                                                    } else {
                                                        Color.Transparent
                                                    },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .testTag("artist_suggestion_${suggestion.name}")
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        ArtistCoverBadge(
                                                            artistName = suggestion.name,
                                                            artworkUrl = avatarUrl,
                                                            isSelected = alreadySelected,
                                                            size = 44.dp
                                                        )

                                                        Spacer(modifier = Modifier.width(12.dp))

                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = suggestion.name,
                                                                style = MaterialTheme.typography.titleSmall,
                                                                color = Color.White,
                                                                fontWeight = FontWeight.Bold,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Text(
                                                                text = suggestion.subtitle.ifBlank { "Исполнитель" },
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = Color.White.copy(alpha = 0.68f),
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }

                                                        Surface(
                                                            shape = RoundedCornerShape(12.dp),
                                                            color = if (alreadySelected) {
                                                                SlantEmerald.copy(alpha = 0.22f)
                                                            } else {
                                                                SlantCyan.copy(alpha = 0.2f)
                                                            },
                                                            border = BorderStroke(
                                                                1.dp,
                                                                if (alreadySelected) SlantEmerald else SlantCyan
                                                            )
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.padding(
                                                                    horizontal = 10.dp,
                                                                    vertical = 5.dp
                                                                ),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Icon(
                                                                    imageVector = if (alreadySelected) {
                                                                        Icons.Default.Check
                                                                    } else {
                                                                        Icons.Default.Add
                                                                    },
                                                                    contentDescription = null,
                                                                    tint = if (alreadySelected) SlantEmerald else SlantCyan,
                                                                    modifier = Modifier.size(14.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                Text(
                                                                    text = if (alreadySelected) "Выбрано" else "Выбрать",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = if (alreadySelected) SlantEmerald else SlantCyan,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            }
                                                        }
                                                    }
                                                }

                                                if (idx < artistSuggestions.take(7).lastIndex) {
                                                    HorizontalDivider(
                                                        color = Color.White.copy(alpha = 0.07f),
                                                        modifier = Modifier.padding(horizontal = 14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Currently selected artists chips with cover art
                        if (selectedArtists.isNotEmpty()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = SlantCyan.copy(alpha = 0.1f),
                                    border = BorderStroke(1.dp, SlantCyan.copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "Выбранные авторы (${selectedArtists.size}):",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = SlantCyan,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            selectedArtists.forEach { artist ->
                                                val coverUrl = resolveArtworkForArtist(artist)
                                                Surface(
                                                    onClick = {
                                                        selectedArtists = selectedArtists - artist
                                                    },
                                                    shape = RoundedCornerShape(22.dp),
                                                    color = SlantCyan.copy(alpha = 0.22f),
                                                    border = BorderStroke(1.dp, SlantCyan.copy(alpha = 0.6f))
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(
                                                            start = 5.dp,
                                                            end = 10.dp,
                                                            top = 4.dp,
                                                            bottom = 4.dp
                                                        ),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        ArtistCoverBadge(
                                                            artistName = artist,
                                                            artworkUrl = coverUrl,
                                                            isSelected = true,
                                                            size = 26.dp
                                                        )
                                                        Spacer(modifier = Modifier.width(7.dp))
                                                        Text(
                                                            text = artist,
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Icon(
                                                            imageVector = Icons.Default.Close,
                                                            contentDescription = "Убрать",
                                                            tint = Color.White.copy(alpha = 0.8f),
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Artists from user's existing collection (Liked tracks, Playlists, History)
                        if (collectionArtists.isNotEmpty()) {
                            item {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "Авторы из ваших лайков, плейлистов и истории:",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = SlantMagenta,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        collectionArtists.forEach { artist ->
                                            val isSelected = selectedArtists.any {
                                                it.equals(artist, ignoreCase = true)
                                            }
                                            val coverUrl = resolveArtworkForArtist(artist)
                                            Surface(
                                                onClick = {
                                                    val match = selectedArtists.firstOrNull {
                                                        it.equals(artist, ignoreCase = true)
                                                    }
                                                    selectedArtists = if (match != null) {
                                                        selectedArtists - match
                                                    } else {
                                                        if (coverUrl.isNotBlank()) {
                                                            draftArtistArtworks = draftArtistArtworks +
                                                                (artist.lowercase() to coverUrl)
                                                        }
                                                        selectedArtists + artist
                                                    }
                                                },
                                                shape = RoundedCornerShape(22.dp),
                                                color = if (isSelected) {
                                                    SlantMagenta.copy(alpha = 0.25f)
                                                } else {
                                                    Color.White.copy(alpha = 0.07f)
                                                },
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isSelected) SlantMagenta else Color.White.copy(alpha = 0.14f)
                                                )
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(
                                                        start = 5.dp,
                                                        end = 11.dp,
                                                        top = 4.dp,
                                                        bottom = 4.dp
                                                    ),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    ArtistCoverBadge(
                                                        artistName = artist,
                                                        artworkUrl = coverUrl,
                                                        isSelected = isSelected,
                                                        size = 24.dp
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = artist,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = Color.White,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Genre filter for starter artists catalog
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = artistGenreFilter == null,
                                    onClick = { artistGenreFilter = null },
                                    label = { Text("Все стили") }
                                )
                                TasteGenreOption.entries.forEach { g ->
                                    FilterChip(
                                        selected = artistGenreFilter == g.id,
                                        onClick = {
                                            artistGenreFilter = if (artistGenreFilter == g.id) null else g.id
                                        },
                                        label = { Text(g.title.substringBefore(" &")) }
                                    )
                                }
                            }
                        }

                        val filteredSeeds = SlantWaveEngine.starterArtistsCatalog.filter { seed ->
                            artistGenreFilter == null || seed.genreId == artistGenreFilter
                        }

                        items(filteredSeeds, key = { it.name }) { seed ->
                            val isSelected = selectedArtists.any { it.equals(seed.name, ignoreCase = true) }
                            val coverUrl = resolveArtworkForArtist(seed.name).ifBlank { seed.avatarUrl }
                            Surface(
                                onClick = {
                                    val match = selectedArtists.firstOrNull {
                                        it.equals(seed.name, ignoreCase = true)
                                    }
                                    selectedArtists = if (match != null) {
                                        selectedArtists - match
                                    } else {
                                        if (coverUrl.isNotBlank()) {
                                            draftArtistArtworks = draftArtistArtworks +
                                                (seed.name.lowercase() to coverUrl)
                                        }
                                        selectedArtists + seed.name
                                    }
                                    // Also auto-enable the artist's genre if not yet selected
                                    if (match == null && seed.genreId !in selectedGenres) {
                                        selectedGenres = selectedGenres + seed.genreId
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) SlantViolet.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (isSelected) SlantViolet else Color.White.copy(alpha = 0.1f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("starter_artist_${seed.name}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ArtistCoverBadge(
                                        artistName = seed.name,
                                        artworkUrl = coverUrl,
                                        isSelected = isSelected,
                                        size = 44.dp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = seed.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = seed.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.65f)
                                        )
                                    }
                                    if (seed.isRussian) {
                                        Text(
                                            text = "RU",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SlantCyan,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) SlantViolet else Color.White.copy(alpha = 0.08f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Add,
                                            contentDescription = null,
                                            tint = if (isSelected) Color(0xFF0E1118) else Color.White.copy(alpha = 0.75f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // TAB 2: ALGORITHM & COLLECTION SYNC
                    else -> {
                        item {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = SlantEmerald.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, SlantEmerald.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Tune,
                                            contentDescription = null,
                                            tint = SlantEmerald,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Активная база вашей коллекции в алгоритме",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = SlantEmerald,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "• Лайкнутые треки: ${liveDna.likedTracksCount}\n" +
                                            "• Треки в ваших плейлистах: ${liveDna.playlistTracksCount}\n" +
                                            "• Выбранные и любимые авторы: ${liveDna.selectedArtistsCount}\n" +
                                            "• Любимые альбомы и релизы: ${liveDna.likedAlbumsCount}\n" +
                                            "• Ведущие авторы ДНК: ${liveDna.topArtists.take(4).joinToString(", ").ifBlank { "Формируются" }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.88f)
                                    )
                                }
                            }
                        }

                        // Discovery Balance selection
                        item {
                            Text(
                                text = "Ориентация алгоритма подборок",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        items(TasteDiscoveryBalance.entries, key = { it.id }) { mode ->
                            val isSelected = discoveryBalance == mode
                            Surface(
                                onClick = { discoveryBalance = mode },
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) SlantCyan.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) SlantCyan else Color.White.copy(alpha = 0.1f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("taste_balance_${mode.id}")
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = mode.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (isSelected) SlantCyan else Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = mode.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }

                        // Preferred Languages
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Языковые предпочтения (можно выбрать несколько)",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                WaveLanguage.entries.forEach { lang ->
                                    val isSelected = lang in preferredLanguages
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            preferredLanguages = if (isSelected) {
                                                preferredLanguages - lang
                                            } else {
                                                preferredLanguages + lang
                                            }
                                        },
                                        label = { Text(lang.title) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = SlantCyan.copy(alpha = 0.25f),
                                            selectedLabelColor = SlantCyan,
                                            containerColor = Color.White.copy(alpha = 0.08f),
                                            labelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }

                        // Preferred Eras
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Любимые эпохи релизов",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TasteEraOption.entries.forEach { era ->
                                    val isSelected = era in preferredEras
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            preferredEras = if (isSelected) {
                                                preferredEras - era
                                            } else {
                                                preferredEras + era
                                            }
                                        },
                                        label = { Text(era.title) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = SlantViolet.copy(alpha = 0.25f),
                                            selectedLabelColor = Color.White,
                                            containerColor = Color.White.copy(alpha = 0.08f),
                                            labelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }

                        // Toggles for Collection Auto-Adaptation & Full-Length priority
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White.copy(alpha = 0.06f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Учитывать лайки, плейлисты и альбомы",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = Color.White,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Подборки динамически подстраиваются под каждый ваш лайк и плейлист",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White.copy(alpha = 0.65f)
                                            )
                                        }
                                        Switch(
                                            checked = autoAdaptCollection,
                                            onCheckedChange = { autoAdaptCollection = it }
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Избегать 30-сек тизеров (только полные треки)",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = Color.White,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Автоматически заменять 30-секундные сниппеты на полные аудиопотоки и фильтровать тизеры",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White.copy(alpha = 0.65f)
                                            )
                                        }
                                        Switch(
                                            checked = preferFullLength,
                                            onCheckedChange = { preferFullLength = it }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = { onSaveProfile(liveDraftProfile, false) },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("save_taste_profile_btn")
                ) {
                    Text(
                        text = "Сохранить ДНК",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                Button(
                    onClick = { onSaveProfile(liveDraftProfile, true) },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFACC15),
                        contentColor = Color(0xFF0E1118)
                    ),
                    modifier = Modifier
                        .weight(1.25f)
                        .height(48.dp)
                        .testTag("save_and_launch_dna_wave_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Включить Мою волну",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Circular artist cover avatar with gradient monogram fallback and selection ring.
 */
@Composable
private fun ArtistCoverBadge(
    artistName: String,
    artworkUrl: String,
    isSelected: Boolean,
    size: Dp
) {
    val initials = remember(artistName) {
        artistName.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.take(1).uppercase() }
            .ifBlank { "♪" }
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    if (isSelected) {
                        listOf(SlantCyan, SlantViolet)
                    } else {
                        listOf(Color(0xFF283149), Color(0xFF1A2032))
                    }
                )
            )
            .then(
                if (isSelected) {
                    Modifier.border(1.5.dp, SlantCyan, CircleShape)
                } else {
                    Modifier.border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold
        )
        if (artworkUrl.isNotBlank()) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = artistName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        }
    }
}
