package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.domain.SearchSortMode
import com.example.domain.SourceFilter
import com.example.domain.SourceProvider
import com.example.domain.Track
import com.example.domain.TrackDeduplicationEngine
import com.example.ui.components.MarqueeText
import com.example.ui.components.TrackItemCard
import com.example.ui.theme.SlantCyan

private data class MatchedArtistCardModel(
    val name: String,
    val avatarUrl: String,
    val preferredProvider: SourceProvider,
    val trackCount: Int
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    query: String,
    selectedFilter: SourceFilter,
    selectedSortMode: SearchSortMode = SearchSortMode.RELEVANCE,
    searchResults: List<Track>,
    searchHistory: List<String>,
    isSearching: Boolean,
    currentTrack: Track?,
    isPlaying: Boolean,
    downloadingIds: Set<String>,
    resetTrigger: Int = 0,
    onQueryChange: (String) -> Unit,
    onSubmitSearch: (String) -> Unit,
    onFilterChange: (SourceFilter) -> Unit,
    onSortModeChange: (SearchSortMode) -> Unit = {},
    onOpenArtistProfile: (String, SourceProvider?) -> Unit = { _, _ -> },
    onDeleteHistoryQuery: (String) -> Unit,
    onClearHistory: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onDownloadOffline: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onOpenTrackMenu: (Track) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val resultsListState = rememberLazyListState()
    val homeListState = rememberLazyListState()
    val accentColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(resetTrigger) {
        if (resetTrigger > 0) {
            onQueryChange("")
            onFilterChange(SourceFilter.ALL)
            homeListState.scrollToItem(0)
        }
    }

    val matchedArtists = remember(searchResults) {
        searchResults
            .filter { !it.isLiveStream && it.artist.isNotBlank() }
            .groupBy { TrackDeduplicationEngine.extractPrimaryArtistName(it.artist).lowercase() }
            .values
            .filter { it.isNotEmpty() }
            .map { tracks ->
                val cleanName = TrackDeduplicationEngine.extractPrimaryArtistName(tracks.first().artist)
                // Group by provider so we pick the single strongest provider for this artist
                val topProviderEntry = tracks.groupBy { it.primaryProvider }
                    .maxByOrNull { (_, list) -> list.size }
                val bestProvider = topProviderEntry?.key ?: tracks.first().primaryProvider
                val countInBestProvider = topProviderEntry?.value?.size ?: tracks.size
                MatchedArtistCardModel(
                    name = cleanName,
                    avatarUrl = tracks.firstOrNull { it.artworkUrl.isNotBlank() }?.artworkUrl.orEmpty(),
                    preferredProvider = bestProvider,
                    trackCount = countInBestProvider
                )
            }
            .sortedByDescending { it.trackCount }
            .take(6)
    }

    val quickSuggestions = listOf(
        "Daft Punk",
        "The Weeknd",
        "Kavinsky Nightcall",
        "Linkin Park",
        "Hans Zimmer",
        "Lofi Hip Hop Radio",
        "Depeche Mode",
        "Prodigy"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("search_screen")
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = {
                Text(
                    text = "Треки, артисты, радио, концерты…",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = accentColor
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.testTag("clear_search_input_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Очистить запрос"
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accentColor,
                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    onSubmitSearch(query)
                    focusManager.clearFocus()
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_query_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(SourceFilter.entries) { filter ->
                val selected = filter == selectedFilter
                FilterChip(
                    selected = selected,
                    onClick = { onFilterChange(filter) },
                    label = {
                        Text(
                            text = filter.title,
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.testTag("source_filter_chip_${filter.name}")
                )
            }
        }

        if (searchResults.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(SearchSortMode.entries) { sort ->
                    val selected = sort == selectedSortMode
                    Surface(
                        onClick = { onSortModeChange(sort) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) SlantCyan.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ) {
                        Text(
                            text = sort.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) SlantCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when {
            isSearching -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = accentColor)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Параллельный опрос источников & дедупликация…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            query.isBlank() -> {
                LazyColumn(
                    state = homeListState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 128.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (searchHistory.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "История поиска",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = onClearHistory,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("clear_search_history_btn")
                                ) {
                                    Text(
                                        text = "Очистить",
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        items(searchHistory) { histQuery ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSubmitSearch(histQuery) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    MarqueeText(
                                        text = histQuery,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteHistoryQuery(histQuery) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Удалить из истории",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Быстрый поиск по жанрам и исполнителям",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            quickSuggestions.forEach { suggestion ->
                                AssistChip(
                                    onClick = { onSubmitSearch(suggestion) },
                                    label = {
                                        Text(
                                            text = suggestion,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                        labelColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier.testTag("search_suggestion_$suggestion")
                                )
                            }
                        }
                    }
                }
            }

            searchResults.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Ничего не найдено по запросу «$query»",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Попробуйте переключить фильтр провайдера на «Все»",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            else -> {
                Text(
                    text = "Найдено ${searchResults.size} треков • Сортировка: ${selectedSortMode.title}",
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                LazyColumn(
                    state = resultsListState,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("search_results_list"),
                    contentPadding = PaddingValues(bottom = 128.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (matchedArtists.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Исполнители (профиль по единому источнику)",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(matchedArtists, key = { "${it.name}_${it.preferredProvider.name}" }) { artistCard ->
                                        Surface(
                                            onClick = {
                                                onOpenArtistProfile(artistCard.name, artistCard.preferredProvider)
                                            },
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                            modifier = Modifier.width(175.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(42.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.surface),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (artistCard.avatarUrl.isNotBlank()) {
                                                        AsyncImage(
                                                            model = artistCard.avatarUrl,
                                                            contentDescription = artistCard.name,
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    } else {
                                                        Icon(
                                                            imageVector = Icons.Default.Person,
                                                            contentDescription = null,
                                                            tint = SlantCyan
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = artistCard.name,
                                                        style = MaterialTheme.typography.labelLarge,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = "${artistCard.preferredProvider.shortBadge} • ${artistCard.trackCount} тр.",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = SlantCyan,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    items(searchResults, key = { it.id }) { track ->
                        TrackItemCard(
                            track = track,
                            isCurrentTrack = currentTrack?.id == track.id,
                            isPlaying = isPlaying,
                            isDownloading = track.id in downloadingIds,
                            onTrackClick = { onPlayTrack(track, searchResults) },
                            onFavoriteClick = { onToggleFavorite(track) },
                            onDownloadClick = { onDownloadOffline(track) },
                            onAddToPlaylistClick = { onAddToPlaylist(track) },
                            onMoreClick = { onOpenTrackMenu(track) }
                        )
                    }
                }
            }
        }
    }
}
