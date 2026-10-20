package com.example

import com.example.domain.LrcParser
import com.example.domain.SourceProvider
import com.example.domain.Track
import com.example.domain.TrackDeduplicationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun lrcParser_parsesSyncedTimestampsAndFindsActiveLine() {
        val sampleLrc = """
            [ti:Nightcall]
            [ar:Kavinsky]
            [00:12.50] I'm giving you a night call to tell you how I feel
            [00:18.00] We'll go all, all, all night long
            [00:24.75] Tell you how I feel
        """.trimIndent()

        val parsed = LrcParser.parseLrc(
            rawLrc = sampleLrc,
            trackId = "test_1",
            title = "Nightcall",
            artist = "Kavinsky"
        )

        assertTrue(parsed.isSynced)
        assertFalse(parsed.isInstrumental)
        assertEquals(3, parsed.lines.size)
        assertEquals(12_500L, parsed.lines[0].timestampMs)
        assertEquals("I'm giving you a night call to tell you how I feel", parsed.lines[0].text)
        assertEquals(18_000L, parsed.lines[1].timestampMs)

        assertEquals(-1, LrcParser.findActiveLineIndex(parsed.lines, 5_000L))
        assertEquals(0, LrcParser.findActiveLineIndex(parsed.lines, 14_000L))
        assertEquals(1, LrcParser.findActiveLineIndex(parsed.lines, 18_100L))
        assertEquals(2, LrcParser.findActiveLineIndex(parsed.lines, 30_000L))
    }

    @Test
    fun deduplicationEngine_cleansNoiseAndMergesMultiSourceTracks() {
        val rawTitle = "Harder, Better, Faster, Stronger (Remastered 2021) [Official Video] feat. Pharrell"
        val cleaned = TrackDeduplicationEngine.normalizeText(rawTitle)
        assertEquals("harder better faster stronger", cleaned)

        val jamendoTrack = Track(
            id = "jam_101",
            title = "Midnight City (Official Audio)",
            artist = "M83",
            album = "Hurry Up, We're Dreaming",
            artworkUrl = "https://img.jamendo.com/300x300.jpg",
            durationSec = 243,
            primaryProvider = SourceProvider.JAMENDO,
            streamUrl = "https://mp3d.jamendo.com/download/track/101/mp32/",
            isPreviewOnly = false
        )

        val deezerDuplicate = Track(
            id = "dzr_202",
            title = "Midnight City [Remastered]",
            artist = "M83 feat. Guest",
            album = "Hurry Up, We're Dreaming",
            artworkUrl = "https://e-cdns-images.dzcdn.net/images/cover/1000x1000.jpg",
            durationSec = 241, // Delta = 2 sec (<= 3 sec)
            primaryProvider = SourceProvider.DEEZER,
            streamUrl = "https://cdns-preview.dzcdn.net/stream/preview.mp3",
            isPreviewOnly = true
        )

        val differentTrack = Track(
            id = "aud_303",
            title = "Solar Sailer",
            artist = "Daft Punk",
            durationSec = 162,
            primaryProvider = SourceProvider.AUDIUS,
            streamUrl = "https://discoveryprovider.audius.co/v1/tracks/303/stream"
        )

        assertTrue(TrackDeduplicationEngine.isSameTrack(jamendoTrack, deezerDuplicate))

        val ranked = TrackDeduplicationEngine.deduplicateAndRank(
            tracks = listOf(deezerDuplicate, jamendoTrack, differentTrack),
            query = "Midnight City M83"
        )

        assertEquals(2, ranked.size)
        val topMerged = ranked.first()
        assertEquals(SourceProvider.JAMENDO, topMerged.primaryProvider)
        assertTrue(topMerged.artworkUrl.contains("1000x1000"))
        assertEquals(2, topMerged.allStreamCandidates().size)
    }
}
