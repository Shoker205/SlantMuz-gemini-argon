package com.example.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Isolated intelligent deduplication, artist verification, alias resolution, and ranking engine
 * for multi-source tracks.
 * Cleans noise tags, filters out 30s teaser/snippet junk, computes Levenshtein & Jaccard similarity,
 * and provides both cross-source deduplication (for global search & recommendations) and strict
 * single-source catalog isolation (for Artist Profiles so tracks from different providers never mix).
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

    private val teaserNoiseRegex = Regex(
        pattern = """\b(teaser|snippet|preview|30\s*sec|short\s*version|тизер|отрывок|сниппет|превью)\b""",
        option = RegexOption.IGNORE_CASE
    )

    private val artistSplitRegex = Regex("""\s*(?:,|&|/|;|\bfeat\.?|\bft\.?|\bx\b|\bwith\b|\band\b|\bи\b)\s*""", RegexOption.IGNORE_CASE)

    private val nonAlphanumericRegex = Regex("""[^\p{L}\p{N}\s]""")
    private val multiSpaceRegex = Regex("""\s+""")

    /**
     * Bidirectional alias clusters so Russian/Latin transliterations and band/frontman credits
     * match seamlessly when loading an artist's full catalog.
     */
    private val artistAliasClusters: List<Set<String>> = listOf(
        setOf("кино", "kino", "виктор цой", "viktor tsoi", "группа кино"),
        setOf("molchat doma", "молчат дома"),
        setOf("перемотка", "peremotka"),
        setOf("буерак", "buerak"),
        setOf("сироткин", "sirotkin"),
        setOf("земфира", "zemfira"),
        setOf("би 2", "би2", "bi 2", "bi2"),
        setOf("сплин", "splean"),
        setOf("король и шут", "korol i shut", "киш"),
        setOf("miyagi эндшпиль", "miyagi", "эндшпиль", "miyagi andy panda", "andy panda", "мияги"),
        setOf("скриптонит", "scriptonite", "gruppa skryptonite"),
        setOf("баста", "basta", "ноггано"),
        setOf("dvrst", "дврст"),
        setOf("дурной вкус", "durnoy vkus"),
        setOf("наутилус помпилиус", "nautilus pompilius"),
        setOf("агата кристи", "agata kristi"),
        setOf("мумий тролль", "mumiy troll")
    )

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

    fun isTeaserOrSnippetTitle(title: String): Boolean {
        return teaserNoiseRegex.containsMatchIn(title)
    }

    fun extractPrimaryArtistName(rawArtist: String): String {
        val trimmed = rawArtist.trim()
        // Keep well-known duos intact
        if (trimmed.contains("Король и Шут", ignoreCase = true) ||
            trimmed.contains("Miyagi", ignoreCase = true)
        ) {
            return trimmed.split(Regex("""\s*(?:,|;|\bfeat\.?|\bft\.?)\s*""", RegexOption.IGNORE_CASE))
                .firstOrNull()?.trim().orEmpty().ifBlank { trimmed }
        }
        val firstPart = trimmed.split(artistSplitRegex).firstOrNull()?.trim().orEmpty()
        return firstPart.ifBlank { trimmed }
    }

    /**
     * If a video/track title is formatted like "Artist - Song Title" or "Artist — Song Title",
     * splits it into Pair(Artist, SongTitle) so artist catalogs don't miss tracks uploaded with prefix titles.
     */
    fun splitArtistAndTitleIfPrefixed(rawTitle: String, fallbackArtist: String): Pair<String, String> {
        val cleanTitle = rawTitle.trim()
        val separators = listOf(" — ", " – ", " - ")
        for (sep in separators) {
            val idx = cleanTitle.indexOf(sep)
            if (idx in 2..42) {
                val left = cleanTitle.substring(0, idx).trim()
                val right = cleanTitle.substring(idx + sep.length).trim()
                if (left.isNotBlank() && right.length >= 2) {
                    val genericUploader = fallbackArtist.contains("Topic", ignoreCase = true) ||
                        fallbackArtist.contains("Official", ignoreCase = true) ||
                        fallbackArtist.contains("Music", ignoreCase = true) ||
                        fallbackArtist.contains("Channel", ignoreCase = true) ||
                        fallbackArtist.contains("Records", ignoreCase = true) ||
                        fallbackArtist.contains("YouTube", ignoreCase = true) ||
                        fallbackArtist.contains("Audius", ignoreCase = true) ||
                        fallbackArtist.contains("Archive", ignoreCase = true)
                    if (genericUploader || areArtistNamesEquivalent(left, fallbackArtist)) {
                        return left to right
                    }
                }
            }
        }
        return fallbackArtist.removeSuffix(" - Topic").trim() to cleanTitle
    }

    fun areArtistNamesEquivalent(a: String, b: String): Boolean {
        val normA = normalizeText(a)
        val normB = normalizeText(b)
        if (normA.isBlank() || normB.isBlank()) return false
        if (normA == normB) return true
        if (levenshteinSimilarity(normA, normB) >= 0.85) return true

        val clusterA = artistAliasClusters.firstOrNull { cluster ->
            cluster.any { alias -> alias == normA || normA.contains(alias) }
        }
        if (clusterA != null && clusterA.any { alias -> alias == normB || normB.contains(alias) }) {
            return true
        }
        return false
    }

    /**
     * Strictly verifies whether a track is actually authored by [targetArtist]
     * (prevents tracks where the artist name merely appears in a tribute or radio station name,
     * while supporting legitimate aliases, collaborations, and "Artist - Title" uploads).
     */
    fun isTrackByArtist(track: Track, targetArtist: String): Boolean {
        if (track.isLiveStream) return false
        val normTarget = normalizeText(targetArtist)
        if (normTarget.isBlank()) return false

        if (areArtistNamesEquivalent(track.artist, targetArtist)) return true
        if (areArtistNamesEquivalent(extractPrimaryArtistName(track.artist), targetArtist)) return true

        // Check all credited artists on the track (e.g., "Daft Punk, Pharrell Williams" or "Miyagi & Эндшпиль")
        val creditedArtists = track.artist
            .split(artistSplitRegex)
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (creditedArtists.any { areArtistNamesEquivalent(it, targetArtist) }) {
            return true
        }

        // Also check if the title starts with "TargetArtist - "
        val (prefixedArtist, _) = splitArtistAndTitleIfPrefixed(track.title, track.artist)
        if (areArtistNamesEquivalent(prefixedArtist, targetArtist)) {
            return true
        }

        return false
    }

    /**
     * Deduplicates tracks strictly within a SINGLE SourceProvider without collapsing distinct songs.
     */
    fun deduplicateWithinSingleProvider(tracks: List<Track>): List<Track> {
        val unique = mutableListOf<Track>()
        for (track in tracks) {
            if (track.title.isBlank() || track.streamUrl.isBlank() || track.isLiveStream) continue
            if (isTeaserOrSnippetTitle(track.title)) continue
            val normTitle = normalizeText(track.title)
            if (normTitle.isBlank()) continue

            val existingIdx = unique.indexOfFirst { existing ->
                val existingTitle = normalizeText(existing.title)
                existingTitle == normTitle ||
                    (levenshteinSimilarity(existingTitle, normTitle) >= 0.93 &&
                        isDurationCompatible(existing.durationSec, track.durationSec, maxDeltaSec = 8))
            }

            if (existingIdx >= 0) {
                val current = unique[existingIdx]
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

        // Order providers by catalog richness (total tracks found) and full-track quality
        val sortedEntries = grouped.entries.sortedByDescending { (provider, list) ->
            val fullCount = list.count { !it.isPreviewOnly && it.durationSec > 35 }
            val providerWeight = when (provider) {
                SourceProvider.LOCAL -> 500
                SourceProvider.DEEZER -> 410
                SourceProvider.ITUNES -> 400
                SourceProvider.YOUTUBE_PIPED -> 420
                SourceProvider.JAMENDO -> 390
                SourceProvider.AUDIUS -> 380
                SourceProvider.INTERNET_ARCHIVE -> 280
                SourceProvider.RADIO_BROWSER -> 100
            }
            providerWeight + (list.size * 28) + (fullCount * 18)
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
        val lev = levenshteinSimilarity(a)
        val normA = normalizeText(a)
        val normB = normalizeText(b)
        if (normA == normB && normA.isNotBlank()) return 1.0
        val levScore = levenshteinSimilarity(a, b)
        val jac = jaccardSimilarity(a, b)
        return max(levScore, (levScore * 0.65 + jac * 0.35))
    }

    private fun levenshteinSimilarity(a: String): Double = if (a.isBlank()) 0.0 else 1.0

    fun isDurationCompatible(d1Sec: Int, d2Sec: Int, maxDeltaSec: Int = 5): Boolean {
        if (d1Sec <= 0 || d2Sec <= 0) return true
        val isOnePreview = (d1Sec in 25..33 && d2Sec > 45) || (d2Sec in 25..33 && d1Sec > 45)
        if (isOnePreview) return true
        return abs(d1Sec - d2Sec) <= maxDeltaSec
    }

    fun isSameTrack(t1: Track, t2: Track): Boolean {
        if (t1.isLiveStream != t2.isLiveStream) return false
        val titleSim = combinedSimilarity(t1.title, t2.title)
        val artistSim = if (areArtistNamesEquivalent(t1.artist, t2.artist)) {
            1.0
        } else {
            combinedSimilarity(t1.artist, t2.artist)
        }
        val durationOk = isDurationCompatible(t1.durationSec, t2.durationSec)
        return titleSim >= 0.80 && artistSim >= 0.75 && durationOk
    }

    private fun providerPriorityScore(track: Track): Int {
        val base = when (track.primaryProvider) {
            SourceProvider.LOCAL -> 120
            SourceProvider.JAMENDO -> 105
            SourceProvider.AUDIUS -> 102
            SourceProvider.YOUTUBE_PIPED -> 100
            SourceProvider.INTERNET_ARCHIVE -> 90
            SourceProvider.RADIO_BROWSER -> 80
            SourceProvider.DEEZER -> if (track.isPreviewOnly) 60 else 95
            SourceProvider.ITUNES -> if (track.isPreviewOnly) 55 else 90
        }
        val fullTrackBonus = if (!track.isPreviewOnly && track.durationSec >= 45) 45 else 0
        return base + fullTrackBonus
    }

    fun mergeTracks(existing: Track, incoming: Track): Track {
        val primary = if (providerPriorityScore(incoming) > providerPriorityScore(existing)) {
            incoming
        } else {
            existing
        }
        val secondary = if (primary === existing) incoming else existing

        // Prioritize non-preview full-length candidates FIRST, before any 30s preview URL
        val mergedCandidates = (primary.allStreamCandidates() + secondary.allStreamCandidates())
            .distinctBy { it.streamUrl }
            .sortedByDescending { candidate ->
                val fullStreamBonus = if (!candidate.isPreview) 2000 else 0
                val directHttpBonus = if (
                    candidate.streamUrl.startsWith("https://") ||
                    candidate.streamUrl.startsWith("http://") ||
                    candidate.streamUrl.startsWith("content://")
                ) 300 else 150
                fullStreamBonus + directHttpBonus + candidate.bitrateKbps
            }

        val bestArtwork = listOf(existing.artworkUrl, incoming.artworkUrl)
            .filter { it.isNotBlank() }
            .maxByOrNull { url ->
                when {
                    url.contains("1000x1000") || url.contains("cover_xl") -> 5
                    url.contains("600x600") || url.contains("500x500") || url.contains("cover_big") -> 4
                    url.contains("480x480") -> 3
                    url.startsWith("https://") -> 2
                    else -> 1
                }
            } ?: primary.artworkUrl

        val bestDuration = when {
            existing.durationSec > 45 && incoming.durationSec in 1..35 -> existing.durationSec
            incoming.durationSec > 45 && existing.durationSec in 1..35 -> incoming.durationSec
            else -> max(existing.durationSec, incoming.durationSec)
        }

        val bestAlbum = when {
            existing.album.isNotBlank() && existing.album != "YouTube Music" && existing.album != "Audius Network" -> existing.album
            incoming.album.isNotBlank() && incoming.album != "YouTube Music" && incoming.album != "Audius Network" -> incoming.album
            primary.album.isNotBlank() -> primary.album
            else -> secondary.album
        }
        val bestGenre = if (primary.genre.isNotBlank() && primary.genre != "Music" && primary.genre != "YouTube Stream") {
            primary.genre
        } else {
            secondary.genre
        }
        val bestStream = mergedCandidates.firstOrNull()

        return primary.copy(
            artworkUrl = bestArtwork,
            durationSec = bestDuration,
            album = bestAlbum,
            genre = bestGenre,
            primaryProvider = bestStream?.provider ?: primary.primaryProvider,
            streamUrl = bestStream?.streamUrl ?: primary.streamUrl,
            isPreviewOnly = bestStream?.isPreview ?: false,
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
            if (!candidate.isLiveStream && isTeaserOrSnippetTitle(candidate.title)) continue
            if (!candidate.isLiveStream && candidate.durationSec in 1..26) continue

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
