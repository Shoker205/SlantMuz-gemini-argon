package com.example.data.remote

import com.example.domain.RemoteStreamingConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages dynamic audio streaming parameters via Firebase Remote Config
 * with resilient local default fallback if Firebase project is not provisioned.
 */
class FirebaseRemoteConfigManager {

    private val _configFlow = MutableStateFlow(RemoteStreamingConfig())
    val configFlow: StateFlow<RemoteStreamingConfig> = _configFlow.asStateFlow()

    init {
        syncRemoteConfig()
    }

    fun syncRemoteConfig() {
        runCatching {
            val remoteConfig = FirebaseRemoteConfig.getInstance()
            val settings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(3600L)
                .build()
            remoteConfig.setConfigSettingsAsync(settings)

            val defaults = mapOf<String, Any>(
                "preferred_bitrate_kbps" to 320L,
                "piped_failover_timeout_ms" to 3500L,
                "max_concurrent_sources" to 7L,
                "auto_fallback_on_error" to true,
                "max_cache_size_mb" to 512L
            )
            remoteConfig.setDefaultsAsync(defaults)
            applyFromInstance(remoteConfig, "Firebase Remote Config (Defaults Synced)")

            remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
                val statusLabel = if (task.isSuccessful) {
                    "Firebase Remote Config (Cloud Live)"
                } else {
                    "Firebase Remote Config (Local Safe Profile)"
                }
                applyFromInstance(remoteConfig, statusLabel)
            }
        }.onFailure {
            _configFlow.value = RemoteStreamingConfig(
                configSource = "SlantTech Autonomous Config (Offline Safe)"
            )
        }
    }

    private fun applyFromInstance(rc: FirebaseRemoteConfig, sourceLabel: String) {
        runCatching {
            _configFlow.value = RemoteStreamingConfig(
                preferredBitrateKbps = rc.getLong("preferred_bitrate_kbps").toInt()
                    .takeIf { it > 0 } ?: 320,
                pipedFailoverTimeoutMs = rc.getLong("piped_failover_timeout_ms")
                    .takeIf { it > 500L } ?: 3500L,
                maxConcurrentSources = rc.getLong("max_concurrent_sources").toInt()
                    .takeIf { it > 0 } ?: 7,
                autoFallbackOnError = rc.getBoolean("auto_fallback_on_error"),
                maxCacheSizeMb = rc.getLong("max_cache_size_mb").toInt()
                    .takeIf { it >= 64 } ?: 512,
                configSource = sourceLabel
            )
        }
    }

    fun updateOverrideBitrate(bitrateKbps: Int) {
        _configFlow.value = _configFlow.value.copy(
            preferredBitrateKbps = bitrateKbps,
            configSource = "Developer Override (${bitrateKbps}kbps)"
        )
    }
}
