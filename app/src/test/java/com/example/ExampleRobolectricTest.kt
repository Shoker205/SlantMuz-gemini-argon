package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.EqualizerPreset
import com.example.domain.SourceProvider
import com.example.domain.Track
import com.example.player.AudioEffectsManager
import com.example.player.PlaybackUiState
import com.example.player.PlayerController
import com.example.widget.SlantMuzWidgetUpdater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun appName_isSlantMuz() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("SlantMuz", appName)
    }

    @Test
    fun audioEffectsManager_handlesPresetsSafelyOnVirtualDevice() {
        val manager = AudioEffectsManager()
        manager.onAudioSessionIdChanged(1024)
        manager.selectPreset(EqualizerPreset.BASS_BOOST)
        assertEquals(EqualizerPreset.BASS_BOOST, manager.dspState.value.preset)
        assertEquals(65, manager.dspState.value.bassBoostPercent)

        manager.setBandGain(0, 7.5f)
        assertEquals(7.5f, manager.dspState.value.bandGainsDb[0], 0.01f)
        manager.releaseEffects()
    }

    @Test
    fun playerController_managesQueueAndSleepTimer() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val controller = PlayerController.getInstance(context)
        val sampleTrack = Track(
            id = "jam_test",
            title = "Pastel Horizon",
            artist = "SlantTech Synth",
            durationSec = 195,
            primaryProvider = SourceProvider.JAMENDO,
            streamUrl = "https://mp3d.jamendo.com/download/track/1/mp32/"
        )

        controller.startSleepTimer(15)
        assertEquals(900, controller.playbackState.value.sleepTimerRemainingSec)
        controller.startSleepTimer(0)
        assertEquals(0, controller.playbackState.value.sleepTimerRemainingSec)
        assertNotNull(sampleTrack.allStreamCandidates().firstOrNull())
    }

    @Test
    fun homeScreenWidgets_buildSmallMediumAndLargeRemoteViews() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sampleTrack = Track(
            id = "jam_99",
            title = "Liquid Glass Dreams",
            artist = "SlantTech Pastel",
            durationSec = 200,
            primaryProvider = SourceProvider.JAMENDO,
            streamUrl = "https://mp3d.jamendo.com/download/track/99/mp32/"
        )
        val state = PlaybackUiState(
            currentTrack = sampleTrack,
            queue = listOf(sampleTrack),
            currentQueueIndex = 0,
            isPlaying = true,
            positionMs = 50_000L,
            durationMs = 200_000L
        )

        val smallViews = SlantMuzWidgetUpdater.buildSmallRemoteViews(context, state)
        val mediumViews = SlantMuzWidgetUpdater.buildMediumRemoteViews(context, state)
        val largeViews = SlantMuzWidgetUpdater.buildLargeRemoteViews(
            context,
            state,
            "Floating through pastel skies"
        )

        assertNotNull(smallViews)
        assertNotNull(mediumViews)
        assertNotNull(largeViews)
    }

    @Test
    fun tasteDnaEngine_synthesizesPreConfigurationAndCollectionSignals() {
        val profile = com.example.domain.UserTasteProfile(
            isOnboardingCompleted = true,
            selectedGenreIds = setOf("PHONK", "SYNTHWAVE"),
            selectedArtists = setOf("Kordhell", "The Weeknd"),
            likedAlbums = setOf("The Weeknd|After Hours")
        )
        val likedTrack = Track(
            id = "fav_1",
            title = "Murder In My Mind",
            artist = "Kordhell",
            album = "Phonk Anthology",
            genre = "Drift Phonk",
            durationSec = 145,
            primaryProvider = SourceProvider.JAMENDO,
            streamUrl = "https://mp3d.jamendo.com/download/track/10/mp32/"
        )
        val dna = com.example.domain.SlantWaveEngine.computeTasteDnaSummary(
            tasteProfile = profile,
            favoriteTracks = listOf(likedTrack),
            playlistTracks = emptyList(),
            historyTracks = emptyList(),
            likedArtists = setOf("Kordhell")
        )
        assertEquals(true, dna.personalizationPercent >= 60)
        assertEquals(true, dna.topArtists.any { it.equals("Kordhell", ignoreCase = true) })

        val candidateMatch = Track(
            id = "cand_1",
            title = "Blinding Lights",
            artist = "The Weeknd",
            album = "After Hours",
            genre = "Synthwave",
            durationSec = 200,
            primaryProvider = SourceProvider.JAMENDO,
            streamUrl = "https://mp3d.jamendo.com/download/track/11/mp32/"
        )
        val candidateOther = Track(
            id = "cand_2",
            title = "Random Acoustic Song",
            artist = "Unknown Folk",
            album = "Random",
            genre = "Country",
            durationSec = 180,
            primaryProvider = SourceProvider.ITUNES,
            streamUrl = "https://example.com/preview.m4a",
            isPreviewOnly = true
        )

        val ranked = com.example.domain.SlantWaveEngine.rankAndFilterWaveCandidates(
            rawCandidates = listOf(candidateOther, candidateMatch),
            settings = com.example.domain.WaveSettings(),
            tasteProfile = profile,
            favoriteTracks = listOf(likedTrack),
            playlistTracks = emptyList(),
            historyTracks = emptyList(),
            likedArtists = setOf("Kordhell"),
            dislikedTrackIds = emptySet()
        )
        assertEquals("cand_1", ranked.first().id)
        assertEquals(true, ranked.first().recommendationReason.isNotBlank())
    }
}


