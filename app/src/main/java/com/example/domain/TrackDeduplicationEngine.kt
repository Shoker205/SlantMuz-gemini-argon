package com.example.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Isolated intelligent deduplication, artist verification, and ranking engine for multi-source tracks.
 * Cleans noise tags, computes Levenshtein & Jaccard similarity, checks duration delta (<= 3s),
 * and provides both cross-source deduplication (for global search) and strict single-source
 * catalog isolation (for Artist Profiles so tracks from different providers never mix).
 */
object TrackDeduplicationEngine {

    private val parentheticalNoiseRegex = Regex(
        pattern = """[\(\[\{][^\)\]\}]*\b(remaster(?:ed)?|official\s*(?:music\s*)?(?:video|audio|lyric\s*video)?|lyrics?|hd|hq|4k|explicit|clean|version|edit|radio\s*edit|live|visualizer|prod\.?\s*by[^\)\]\}]*|feat\.?[^\)\]\}]*|ft\.?[^\)\]\}]*)[^\)\]\}]*[\)\]\}]""",
        option = RegexOption.IGNORE_CASE
    )

    private val inlineFeatRegex = Regex(
        pattern = """\b(feat\.?|ft\.?|featuring|prod\.?\s*by)\s+.*$""",
        option = RegexOption.IGNORE_CASE
    )

    private val inlineSuffixNoiseRegex = Regex(
        pattern = """\s*[-–—]\s*(remaster(?:ed)?(\s+\d{4})?|official\s*(video|audio)|lyrics?|hd|hq|explicit).*$""",
        option = RegexOption.IGNORE_CASE
    )

    private val artistSplitRegex = Regex("""\s*(?:,|&|/|;|\bfeat\.?|\bft\.?|\bx\b|\bwith\b)\s*""", RegexOption.IGNORE_CASE)

    private val nonAlphanumericRegex = Regex("""[^\p{L}\p{N}\s]""")
    private val multiSpaceRegex = Regex("""\s+""")

    fun normalizeText(raw: String): String {
        if (raw.isBlank()) return ""
        return raw
            .replace(parentheticalNoiseRegex, " ")
            .replace(inlineFeatRegex, " ")
            .replace(inlineSuffixNoiseRegex, " ")
            .lowercase()
            .replace(nonAlphanumericRegex, " ")
            .replace(multiSpaceRegex, " ")
            .trim()
    }

    fun extractPrimaryArtistName(rawArtist: String): String {
        val firstPart = rawArtist.split(artistSplitRegex).firstOrNull()?.trim().orEmpty()
        return firstPart.ifBlank { rawArtist.trim() }
    }

    /**
     * Strictly verifies whether a track is actually authored by [targetArtist]
     * (prevents tracks where the artist name merely appears in the song title or radio station name).
     */
    fun isTrackByArtist(track: Track, targetArtist: String): Boolean {
        if (track.isLiveStream) return false
        val normTarget = normalizeText(targetArtist)
        if (normTarget.isBlank()) return false

        val normTrackArtistFull = normalizeText(track.artist)
        if (normTrackArtistFull.isBlank()) return false
        if (normTrackArtistFull == normTarget) return true

        val primaryArtistNorm = normalizeText(extractPrimaryArtistName(track.artist))
        if (primaryArtistNorm == normTarget) return true

        // Check all credited artists on the track (e.g., "Daft Punk, Pharrell Williams")
        val creditedArtists = track.artist
            .split(artistSplitRegex)
            .map { normalizeText(it) }
            .filter { it.isNotBlank() }

        if (creditedArtists.any { it == normTarget || levenshteinSimilarity(it, normTarget) >= 0.86 }) {
            return true
        }

        return levenshteinSimilarity(primaryArtistNorm, normTarget) >= 0.85
    }

    /**
     * Deduplicates tracks strictly within a SINGLE SourceProvider without mixing or merging across providers.
     */
    fun deduplicateWithinSingleProvider(tracks: List<Track>): List<Track> {
        val unique = mutableListOf<Track>()
        for (track in tracks) {
            if (track.title.isBlank() || track.streamUrl.isBlank() || track.isLiveStream) continue
            val normTitle = normalizeText(track.title)
            if (normTitle.isBlank()) continue

            val existingIdx = unique.indexOfFirst { existing ->
                val existingTitle = normalizeText(existing.title)
                existingTitle == normTitle || levenshteinSimilarity(existingTitle, normTitle) >= 0.86
            }

            if (existingIdx >= 0) {
                val current = unique[existingIdx]
                // Prefer full-length track or track with better artwork/album info within the SAME provider
                val incomingBetter = (!track.isPreviewOnly && current.isPreviewOnly) ||
                    (track.durationSec > current.durationSec + 15) ||
                    (current.artworkUrl.isBlank() && track.artworkUrl.isNotBlank())
                if (incomingBetter) {
                    unique[existingIdx] = track.copy(
                        isFavorite = current.isFavorite || track.isFavorite,
                        isCachedOffline = current.isCachedOffline || track.isCachedOffline,
                        offlineFilePath = current.offlineFilePath ?: track.offlineFilePath
                    )
                }
            } else {
                unique.add(track)
            }
        }
        return unique
    }

    /**
     * Groups verified tracks of a specific artist strictly by their SourceProvider so tracks
     * from different sources NEVER mix in the artist profile.
     */
    fun groupArtistTracksByProvider(
        rawTracks: List<Track>,
        artistName: String
    ): Map<SourceProvider, List<Track>> {
        val verified = rawTracks.filter { isTrackByArtist(it, artistName) }
        val grouped = verified
            .groupBy { it.primaryProvider }
            .mapValues { (_, providerTracks) ->
                deduplicateWithinSingleProvider(providerTracks)
            }
            .filterValues { it.isNotEmpty() }

        // Order providers by catalog richness and full-track quality
        val sortedEntries = grouped.entries.sortedByDescending { (provider, list) ->
            val fullCount = list.count { !it.isPreviewOnly && it.durationSec > 35 }
            val providerWeight = when (provider) {
                SourceProvider.LOCAL -> 500
                SourceProvider.JAMENDO -> 420
                SourceProvider.AUDIUS -> 410
                SourceProvider.YOUTUBE_PIPED -> 400
                SourceProvider.DEEZER -> 320
                SourceProvider.ITUNES -> 310
                SourceProvider.INTERNET_ARCHIVE -> 280
                SourceProvider.RADIO_BROWSER -> 100
            }
            providerWeight + (fullCount * 35) + (list.size * 15)
        }

        val orderedMap = LinkedHashMap<SourceProvider, List<Track>>()
        for (entry in sortedEntries) {
            orderedMap[entry.key] = entry.value
        }
        return orderedMap
    }

    fun levenshteinDistance(lhs: CharSequence, rhs: CharSequence): Int {
        val lhsLen = lhs.length
        val rhsLen = rhs.length
        if (lhsLen == 0) return rhsLen
        if (rhsLen == 0) return lhsLen

        var prevCost = IntArray(rhsLen + 1) { it }
        var currCost = IntArray(rhsLen + 1)

        for (i in 1..lhsLen) {
            currCost[0] = i
            for (j in 1..rhsLen) {
                val match = if (lhs[i - 1] == rhs[j - 1]) 0 else 1
                val insert = currCost[j - 1] + 1
                val delete = prevCost[j] + 1
                val replace = prevCost[j - 1] + match
                currCost[j] = min(min(insert, delete), replace)
            }
            val swap = prevCost
            prevCost = currCost
            currCost = swap
        }
        return prevCost[rhsLen]
    }

    fun levenshteinSimilarity(a: String, b: String): Double {
        val normA = normalizeText(a)
        val normB = normalizeText(b)
        if (normA.isEmpty() && normB.isEmpty()) return 1.0
        if (normA.isEmpty() || normB.isEmpty()) return 0.0
        if (normA == normB) return 1.0
        val maxLen = max(normA.length, normB.length)
        val dist = levenshteinDistance(normA, normB)
        return (1.0 - dist.toDouble() / maxLen.toDouble()).coerceIn(0.0, 1.0)
    }

    fun jaccardSimilarity(a: String, b: String): Double {
        val tokensA = normalizeText(a).split(" ").filter { it.isNotBlank() }.toSet()
        val tokensB = normalizeText(b).split(" ").filter { it.isNotBlank() }.toSet()
        if (tokensA.isEmpty() && tokensB.isEmpty()) return 1.0
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0
        val intersection = tokensA.intersect(tokensB).size
        val union = tokensA.union(tokensB).size
        return if (union == 0) 0.0 else intersection.toDouble() / union.toDouble()
    }

    fun combinedSimilarity(a: String, b: String): Double {
        val lev = levenshteinSimilarity(a, b)
        val jac = jaccardSimilarity(a, b)
        return max(lev, (lev * 0.65 + jac * 0.35))
    }

    fun isDurationCompatible(d1Sec: Int, d2Sec: Int, maxDeltaSec: Int = 3): Boolean {
        if (d1Sec <= 0 || d2Sec <= 0) return true
        val isOnePreview = (d1Sec in 28..31 && d2Sec > 45) || (d2Sec in 28..31 && d1Sec > 45)
        if (isOnePreview) return true
        return abs(d1Sec - d2Sec) <= maxDeltaSec
    }

    fun isSameTrack(t1: Track, t2: Track): Boolean {
        if (t1.isLiveStream != t2.isLiveStream) return false
        val titleSim = combinedSimilarity(t1.title, t2.title)
        val artistSim = combinedSimilarity(t1.artist, t2.artist)
        val durationOk = isDurationCompatible(t1.durationSec, t2.durationSec)
        return titleSim >= 0.78 && artistSim >= 0.72 && durationOk
    }

    private fun providerPriorityScore(track: Track): Int {
        val base = when (track.primaryProvider) {
            SourceProvider.LOCAL -> 100
            SourceProvider.JAMENDO -> 92
            SourceProvider.AUDIUS -> 90
            SourceProvider.YOUTUBE_PIPED -> 88
            SourceProvider.INTERNET_ARCHIVE -> 82
            SourceProvider.RADIO_BROWSER -> 80
            SourceProvider.DEEZER -> if (track.isPreviewOnly) 65 else 85
            SourceProvider.ITUNES -> if (track.isPreviewOnly) 60 else 80
        }
        val fullTrackBonus = if (!track.isPreviewOnly && track.durationSec > 35) 20 else 0
        return base + fullTrackBonus
    }

    fun mergeTracks(existing: Track, incoming: Track): Track {
        val primary = if (providerPriorityScore(incoming) > providerPriorityScore(existing)) {
            incoming
        } else {
            existing
        }
        val secondary = if (primary === existing) incoming else existing

        val mergedCandidates = (primary.allStreamCandidates() + secondary.allStreamCandidates())
            .distinctBy { it.streamUrl }
            .sortedByDescending { candidate ->
                val directHttpBonus = if (
                    candidate.streamUrl.startsWith("https://") ||
                    candidate.streamUrl.startsWith("http://") ||
                    candidate.streamUrl.startsWith("content://")
                ) 600 else 0
                val previewPenalty = if (candidate.isPreview) 0 else 400
                directHttpBonus + previewPenalty + candidate.bitrateKbps
            }

        val bestArtwork = listOf(existing.artworkUrl, incoming.artworkUrl)
            .filter { it.isNotBlank() }
            .maxByOrNull { url ->
                when {
                    url.contains("1000x1000") || url.contains("cover_xl") -> 4
                    url.contains("600x600") || url.contains("500x500") -> 3
                    url.startsWith("https://") -> 2
                    else -> 1
                }
            } ?: primary.artworkUrl

        val bestDuration = when {
            existing.durationSec > 35 && incoming.durationSec in 1..32 -> existing.durationSec
            incoming.durationSec > 35 && existing.durationSec in 1..32 -> incoming.durationSec
            else -> max(existing.durationSec, incoming.durationSec)
        }

        val bestAlbum = if (primary.album.isNotBlank()) primary.album else secondary.album
        val bestGenre = if (primary.genre.isNotBlank() && primary.genre != "Music") primary.genre else secondary.genre
        val bestStream = mergedCandidates.firstOrNull()

        return primary.copy(
            artworkUrl = bestArtwork,
            durationSec = bestDuration,
            album = bestAlbum,
            genre = bestGenre,
            primaryProvider = bestStream?.provider ?: primary.primaryProvider,
            streamUrl = bestStream?.streamUrl ?: primary.streamUrl,
            isPreviewOnly = bestStream?.isPreview ?: primary.isPreviewOnly,
            fallbackStreams = mergedCandidates,
            isFavorite = existing.isFavorite || incoming.isFavorite,
            isCachedOffline = existing.isCachedOffline || incoming.isCachedOffline,
            offlineFilePath = existing.offlineFilePath ?: incoming.offlineFilePath
        )
    }

    fun deduplicateAndRank(tracks: List<Track>, query: String = ""): List<Track> {
        val mergedList = mutableListOf<Track>()
        for (candidate in tracks) {
            if (candidate.title.isBlank() || candidate.streamUrl.isBlank()) continue
            val matchIndex = mergedList.indexOfFirst { isSameTrack(it, candidate) }
            if (matchIndex >= 0) {
                mergedList[matchIndex] = mergeTracks(mergedList[matchIndex], candidate)
            } else {
                val withSelfCandidate = if (candidate.fallbackStreams.isEmpty()) {
                    candidate.copy(fallbackStreams = candidate.allStreamCandidates())
                } else {
                    candidate
                }
                mergedList.add(withSelfCandidate)
            }
        }

        val normQuery = normalizeText(query)
        return mergedList.sortedWith(
            compareByDescending<Track> { track ->
                val relevance = if (normQuery.isBlank()) {
                    0.5
                } else {
                    max(
                        combinedSimilarity(track.title, normQuery),
                        combinedSimilarity("${track.artist} ${track.title}", normQuery)
                    )
                }
                val streamBonus = providerPriorityScore(track) / 100.0
                val multiSourceBonus = (track.fallbackStreams.size.coerceAtMost(5)) * 0.06
                relevance * 2.0 + streamBonus + multiSourceBonus
            }
        )
    }
}
