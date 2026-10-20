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
    val maxCacheSizeMb: Int = 512
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

        AppUserSettings(
            darkThemeMode = darkTheme,
            useDynamicColor = dynamicColor,
            enabledProviders = enabledProviders,
            dspState = dspState,
            maxCacheSizeMb = prefs[Keys.MAX_CACHE_MB] ?: 512
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
}
