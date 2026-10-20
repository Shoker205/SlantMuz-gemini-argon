package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.DspState
import com.example.domain.EqualizerPreset
import com.example.domain.SourceProvider
import com.example.domain.TasteDiscoveryBalance
import com.example.domain.TasteEraOption
import com.example.domain.UserTasteProfile
import com.example.domain.WaveLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.slantDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "slantmuz_preferences"
)

data class AppUserSettings(
    val darkThemeMode: Boolean = true,
    val useDynamicColor: Boolean = false,
    val enabledProviders: Set<SourceProvider> = SourceProvider.entries.toSet(),
    val dspState: DspState = DspState(),
    val maxCacheSizeMb: Int = 512,
    val tasteProfile: UserTasteProfile = UserTasteProfile(),
    val dislikedTrackIds: Set<String> = emptySet()
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val ENABLED_PROVIDERS = stringSetPreferencesKey("enabled_providers")
        val DSP_ENABLED = booleanPreferencesKey("dsp_enabled")
        val DSP_PRESET = stringPreferencesKey("dsp_preset")
        val DSP_BANDS = stringPreferencesKey("dsp_bands")
        val DSP_BASS = intPreferencesKey("dsp_bass")
        val DSP_VIRTUALIZER = intPreferencesKey("dsp_virtualizer")
        val DSP_LOUDNESS = floatPreferencesKey("dsp_loudness")
        val MAX_CACHE_MB = intPreferencesKey("max_cache_mb")

        // User Taste DNA & Pre-Configuration Keys
        val TASTE_ONBOARDING_DONE = booleanPreferencesKey("taste_onboarding_done")
        val TASTE_GENRES = stringSetPreferencesKey("taste_genres")
        val TASTE_ARTISTS = stringSetPreferencesKey("taste_artists")
        val TASTE_LIKED_ALBUMS = stringSetPreferencesKey("taste_liked_albums")
        val TASTE_LANGUAGES = stringSetPreferencesKey("taste_languages")
        val TASTE_ERAS = stringSetPreferencesKey("taste_eras")
        val TASTE_DISCOVERY_BALANCE = stringPreferencesKey("taste_discovery_balance")
        val TASTE_PREFER_FULL_TRACKS = booleanPreferencesKey("taste_prefer_full_tracks")
        val TASTE_AUTO_ADAPT_COLLECTION = booleanPreferencesKey("taste_auto_adapt_collection")
        val DISLIKED_TRACK_IDS = stringSetPreferencesKey("disliked_track_ids")
    }

    val settingsFlow: Flow<AppUserSettings> = context.slantDataStore.data.map { prefs ->
        val darkTheme = prefs[Keys.DARK_THEME] ?: true
        val dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false
        val enabledRaw = prefs[Keys.ENABLED_PROVIDERS]
        val enabledProviders = if (enabledRaw == null) {
            SourceProvider.entries.toSet()
        } else {
            enabledRaw.mapNotNull { name ->
                runCatching { SourceProvider.valueOf(name) }.getOrNull()
            }.toSet().ifEmpty { setOf(SourceProvider.JAMENDO, SourceProvider.DEEZER) }
        }

        val presetName = prefs[Keys.DSP_PRESET] ?: EqualizerPreset.SLANT_PURE_SIGNATURE.name
        val preset = runCatching { EqualizerPreset.valueOf(presetName) }
            .getOrDefault(EqualizerPreset.SLANT_PURE_SIGNATURE)

        val bandsString = prefs[Keys.DSP_BANDS]
        val bands = bandsString
            ?.split(",")
            ?.mapNotNull { it.toFloatOrNull() }
            ?.takeIf { it.size == 5 }
            ?: preset.bandGainsDb

        val dspState = DspState(
            enabled = prefs[Keys.DSP_ENABLED] ?: true,
            preset = preset,
            bandGainsDb = bands,
            bassBoostPercent = prefs[Keys.DSP_BASS] ?: preset.bassBoostPercent,
            virtualizerPercent = prefs[Keys.DSP_VIRTUALIZER] ?: preset.virtualizerPercent,
            loudnessGainDb = prefs[Keys.DSP_LOUDNESS] ?: preset.loudnessGainDb
        )

        val preferredLanguages = prefs[Keys.TASTE_LANGUAGES].orEmpty().mapNotNull { name ->
            runCatching { WaveLanguage.valueOf(name) }.getOrNull()
        }.toSet()

        val preferredEras = prefs[Keys.TASTE_ERAS].orEmpty().mapNotNull { id ->
            TasteEraOption.fromId(id)
        }.toSet()

        val discoveryBalance = TasteDiscoveryBalance.fromId(prefs[Keys.TASTE_DISCOVERY_BALANCE])

        val tasteProfile = UserTasteProfile(
            isOnboardingCompleted = prefs[Keys.TASTE_ONBOARDING_DONE] ?: false,
            selectedGenreIds = prefs[Keys.TASTE_GENRES].orEmpty(),
            selectedArtists = prefs[Keys.TASTE_ARTISTS].orEmpty(),
            likedAlbums = prefs[Keys.TASTE_LIKED_ALBUMS].orEmpty(),
            preferredLanguages = preferredLanguages,
            preferredEras = preferredEras,
            discoveryBalance = discoveryBalance,
            preferFullLengthTracks = prefs[Keys.TASTE_PREFER_FULL_TRACKS] ?: true,
            autoAdaptFromCollection = prefs[Keys.TASTE_AUTO_ADAPT_COLLECTION] ?: true
        )

        AppUserSettings(
            darkThemeMode = darkTheme,
            useDynamicColor = dynamicColor,
            enabledProviders = enabledProviders,
            dspState = dspState,
            maxCacheSizeMb = prefs[Keys.MAX_CACHE_MB] ?: 512,
            tasteProfile = tasteProfile,
            dislikedTrackIds = prefs[Keys.DISLIKED_TRACK_IDS].orEmpty()
        )
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.slantDataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.slantDataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun toggleProvider(provider: SourceProvider, enabled: Boolean) {
        context.slantDataStore.edit { prefs ->
            val current = prefs[Keys.ENABLED_PROVIDERS]
                ?: SourceProvider.entries.map { it.name }.toSet()
            val updated = if (enabled) {
                current + provider.name
            } else {
                (current - provider.name).ifEmpty { setOf(provider.name) }
            }
            prefs[Keys.ENABLED_PROVIDERS] = updated
        }
    }

    suspend fun updateDspState(dsp: DspState) {
        context.slantDataStore.edit { prefs ->
            prefs[Keys.DSP_ENABLED] = dsp.enabled
            prefs[Keys.DSP_PRESET] = dsp.preset.name
            prefs[Keys.DSP_BANDS] = dsp.bandGainsDb.joinToString(",")
            prefs[Keys.DSP_BASS] = dsp.bassBoostPercent
            prefs[Keys.DSP_VIRTUALIZER] = dsp.virtualizerPercent
            prefs[Keys.DSP_LOUDNESS] = dsp.loudnessGainDb
        }
    }

    suspend fun setMaxCacheSizeMb(sizeMb: Int) {
        context.slantDataStore.edit { prefs ->
            prefs[Keys.MAX_CACHE_MB] = sizeMb.coerceIn(64, 4096)
        }
    }

    suspend fun saveTasteProfile(profile: UserTasteProfile) {
        context.slantDataStore.edit { prefs ->
            prefs[Keys.TASTE_ONBOARDING_DONE] = profile.isOnboardingCompleted
            prefs[Keys.TASTE_GENRES] = profile.selectedGenreIds
            prefs[Keys.TASTE_ARTISTS] = profile.selectedArtists
            prefs[Keys.TASTE_LIKED_ALBUMS] = profile.likedAlbums
            prefs[Keys.TASTE_LANGUAGES] = profile.preferredLanguages.map { it.name }.toSet()
            prefs[Keys.TASTE_ERAS] = profile.preferredEras.map { it.id }.toSet()
            prefs[Keys.TASTE_DISCOVERY_BALANCE] = profile.discoveryBalance.id
            prefs[Keys.TASTE_PREFER_FULL_TRACKS] = profile.preferFullLengthTracks
            prefs[Keys.TASTE_AUTO_ADAPT_COLLECTION] = profile.autoAdaptFromCollection
        }
    }

    suspend fun toggleLikedArtist(artistName: String): Set<String> {
        var resultSet = emptySet<String>()
        context.slantDataStore.edit { prefs ->
            val clean = artistName.trim()
            val current = prefs[Keys.TASTE_ARTISTS].orEmpty()
            val existingMatch = current.firstOrNull { it.equals(clean, ignoreCase = true) }
            val updated = if (existingMatch != null) {
                current - existingMatch
            } else {
                current + clean
            }
            prefs[Keys.TASTE_ARTISTS] = updated
            resultSet = updated
        }
        return resultSet
    }

    suspend fun toggleLikedAlbum(albumKey: String): Set<String> {
        var resultSet = emptySet<String>()
        context.slantDataStore.edit { prefs ->
            val clean = albumKey.trim()
            val current = prefs[Keys.TASTE_LIKED_ALBUMS].orEmpty()
            val existingMatch = current.firstOrNull { it.equals(clean, ignoreCase = true) }
            val updated = if (existingMatch != null) {
                current - existingMatch
            } else {
                current + clean
            }
            prefs[Keys.TASTE_LIKED_ALBUMS] = updated
            resultSet = updated
        }
        return resultSet
    }

    suspend fun addDislikedTrackId(trackId: String) {
        context.slantDataStore.edit { prefs ->
            val current = prefs[Keys.DISLIKED_TRACK_IDS].orEmpty()
            prefs[Keys.DISLIKED_TRACK_IDS] = current + trackId
        }
    }
}
