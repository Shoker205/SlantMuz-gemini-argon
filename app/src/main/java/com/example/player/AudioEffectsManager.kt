package com.example.player

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import com.example.domain.DspState
import com.example.domain.EqualizerPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hardware-safe DSP Audio Effects Manager.
 * Connects to ExoPlayer's audioSessionId via onAudioSessionIdChanged and manages:
 * - 5-band Equalizer
 * - BassBoost
 * - Virtualizer
 * - LoudnessEnhancer
 * Wrapped in isolated runCatching blocks so emulators or unsupported audio HALs never crash.
 */
class AudioEffectsManager {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private var currentSessionId: Int = 0

    private val _dspState = MutableStateFlow(DspState())
    val dspState: StateFlow<DspState> = _dspState.asStateFlow()

    fun onAudioSessionIdChanged(audioSessionId: Int) {
        if (audioSessionId <= 0 || audioSessionId == currentSessionId) return
        releaseEffects()
        currentSessionId = audioSessionId

        var hwAttached = false
        val detectedFreqs = mutableListOf<Int>()

        runCatching {
            val eq = Equalizer(0, audioSessionId)
            val bandsCount = eq.numberOfBands.toInt()
            for (b in 0 until minOf(bandsCount, 5)) {
                val centerFreqMilliHz = eq.getCenterFreq(b.toShort())
                detectedFreqs.add((centerFreqMilliHz / 1000).coerceAtLeast(30))
            }
            equalizer = eq
            hwAttached = true
        }

        runCatching {
            bassBoost = BassBoost(0, audioSessionId)
        }

        runCatching {
            virtualizer = Virtualizer(0, audioSessionId)
        }

        runCatching {
            loudnessEnhancer = LoudnessEnhancer(audioSessionId)
        }

        val updatedFreqs = if (detectedFreqs.size == 5) {
            detectedFreqs
        } else {
            listOf(60, 230, 910, 3600, 14000)
        }

        _dspState.value = _dspState.value.copy(
            bandFrequenciesHz = updatedFreqs,
            hardwareAttached = hwAttached,
            activeAudioSessionId = audioSessionId
        )

        applyCurrentStateToHardware()
    }

    fun applyDspState(newState: DspState) {
        _dspState.value = newState.copy(
            bandFrequenciesHz = _dspState.value.bandFrequenciesHz,
            hardwareAttached = _dspState.value.hardwareAttached,
            activeAudioSessionId = currentSessionId
        )
        applyCurrentStateToHardware()
    }

    fun selectPreset(preset: EqualizerPreset) {
        val current = _dspState.value
        val updated = current.copy(
            preset = preset,
            bandGainsDb = preset.bandGainsDb,
            bassBoostPercent = preset.bassBoostPercent,
            virtualizerPercent = preset.virtualizerPercent,
            loudnessGainDb = preset.loudnessGainDb
        )
        applyDspState(updated)
    }

    fun setBandGain(bandIndex: Int, gainDb: Float) {
        if (bandIndex !in 0..4) return
        val current = _dspState.value
        val newBands = current.bandGainsDb.toMutableList().apply {
            this[bandIndex] = gainDb.coerceIn(-12f, 12f)
        }
        applyDspState(current.copy(bandGainsDb = newBands))
    }

    fun setBassBoost(percent: Int) {
        applyDspState(_dspState.value.copy(bassBoostPercent = percent.coerceIn(0, 100)))
    }

    fun setVirtualizer(percent: Int) {
        applyDspState(_dspState.value.copy(virtualizerPercent = percent.coerceIn(0, 100)))
    }

    fun setLoudnessGain(gainDb: Float) {
        applyDspState(_dspState.value.copy(loudnessGainDb = gainDb.coerceIn(0f, 10f)))
    }

    fun setEnabled(enabled: Boolean) {
        applyDspState(_dspState.value.copy(enabled = enabled))
    }

    private fun applyCurrentStateToHardware() {
        val state = _dspState.value

        runCatching {
            val eq = equalizer ?: return@runCatching
            eq.enabled = state.enabled
            if (state.enabled) {
                val range = eq.bandLevelRange
                val minMb = range.getOrElse(0) { -1500 }.toInt()
                val maxMb = range.getOrElse(1) { 1500 }.toInt()
                val numBands = minOf(eq.numberOfBands.toInt(), state.bandGainsDb.size)
                for (i in 0 until numBands) {
                    val targetMb = (state.bandGainsDb[i] * 100f).toInt().coerceIn(minMb, maxMb)
                    eq.setBandLevel(i.toShort(), targetMb.toShort())
                }
            }
        }

        runCatching {
            val bb = bassBoost ?: return@runCatching
            bb.enabled = state.enabled && state.bassBoostPercent > 0
            if (bb.strengthSupported && state.enabled) {
                val strength = (state.bassBoostPercent * 10).coerceIn(0, 1000).toShort()
                bb.setStrength(strength)
            }
        }

        runCatching {
            val virt = virtualizer ?: return@runCatching
            virt.enabled = state.enabled && state.virtualizerPercent > 0
            if (virt.strengthSupported && state.enabled) {
                val strength = (state.virtualizerPercent * 10).coerceIn(0, 1000).toShort()
                virt.setStrength(strength)
            }
        }

        runCatching {
            val loud = loudnessEnhancer ?: return@runCatching
            loud.enabled = state.enabled && state.loudnessGainDb > 0f
            if (state.enabled) {
                val targetGainMb = (state.loudnessGainDb * 100f).toInt().coerceIn(0, 1000)
                loud.setTargetGain(targetGainMb)
            }
        }
    }

    fun releaseEffects() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        runCatching { loudnessEnhancer?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudnessEnhancer = null
    }
}
