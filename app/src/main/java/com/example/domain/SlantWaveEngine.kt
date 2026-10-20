package com.example.domain

import kotlin.math.abs

/**
 * Multi-dimensional algorithmic recommendation & filtering engine for "Моя волна" (SlantWave)
 * and smart personalized mixes across Explore, Search, and Collection.
 */
object SlantWaveEngine {

    private val cyrillicRegex = Regex("""[\p{IsCyrillic}]""")

    private val instrumentalPositiveKeywords = listOf(
        "instrumental", "ambient", "lofi", "lo-fi", "chillhop", "piano", "synthwave",
        "retrowave", "darksynth", "soundtrack", "ost", "score", "classical", "orchestra",
        "acoustic", "beats", "post-rock", "downtempo", "idm", "drone", "meditation",
        "sleep", "study", "focus", "jazz", "cinematic", "без слов", "инструментал"
    )

    private val vocalHeavyKeywords = listOf(
        "feat.", "ft.", "lyrics", "vocal", "rap", "hip-hop", "hip hop", "pop",
        "karaoke", "караоке", "рэп", "вокал"
    )

    private val energeticKeywords = listOf(
        "energetic", "upbeat", "dance", "electro", "electronic", "phonk", "workout",
        "bass", "drum", "dnb", "techno", "house", "trance", "rock", "metal", "punk",
        "synthwave", "hard", "rave", "club", "fast", "drive", "драйв", "бодр", "фонк", "рок"
    )

    private val cheerfulKeywords = listOf(
        "happy", "cheerful", "funk", "disco", "nu disco", "indie pop", "pop", "summer",
        "feel good", "groove", "bright", "dance", "party", "весел", "фанк", "диско", "поп"
    )

    private val calmKeywords = listOf(
        "calm", "chill", "chillout", "ambient", "acoustic", "soft", "relax", "sleep",
        "lofi", "lo-fi", "piano", "downtempo", "lounge", "meditation", "nature",
        "спокойн", "чилл", "эмбиент", "акустик", "сон"
    )

    private val sadKeywords = listOf(
        "sad", "melancholic", "melancholy", "slowed", "reverb", "post-punk", "doomer",
        "dream pop", "shoegaze", "slowcore", "ballad", "dark", "lonely", "noir",
        "грустн", "пост-панк", "меланхол", "лирика"
    )

    fun containsCyrillic(text: String): Boolean = cyrillicRegex.containsMatchIn(text)

    fun isRussianTrack(track: Track): Boolean {
        return containsCyrillic(track.title) ||
            containsCyrillic(track.artist) ||
            containsCyrillic(track.album) ||
            track.genre.contains("Russian", ignoreCase = true) ||
            track.genre.contains("Русск", ignoreCase = true)
    }

    fun isInstrumentalTrack(track: Track): Boolean {
        val combined = "${track.title} ${track.album} ${track.genre}".lowercase()
        val hasInstrumentalTag = instrumentalPositiveKeywords.any { combined.contains(it) }
        val hasVocalTag = vocalHeavyKeywords.any { combined.contains(it) }
        return hasInstrumentalTag && !hasVocalTag
    }

    /**
     * Builds 3-4 focused, high-precision search queries instead of a single 12-word string
     * so remote APIs return rich, relevant results for the exact combination of WaveSettings.
     */
    fun buildTargetedWaveQueries(
        settings: WaveSettings,
        favoriteTracks: List<Track>,
        historyTracks: List<Track>,
        likedArtists: Set<String>
    ): List<String> {
        val queries = mutableListOf<String>()

        val topUserArtists = (
            likedArtists.toList() +
                favoriteTracks.map { it.artist } +
                historyTracks.map { it.artist }
            )
            .map { it.trim() }
            .filter { it.length >= 2 && !it.equals("Unknown", ignoreCase = true) }
            .distinctBy { it.lowercase() }

        // 1. Language-aware Mood + Activity core queries
        when (settings.language) {
            WaveLanguage.RUSSIAN -> {
                when {
                    settings.activity == WaveActivity.WORKOUT || settings.mood == WaveMood.ENERGETIC -> {
                        queries.add("Русский фонк")
                        queries.add("Русский рок хиты")
                        queries.add("Русская электроника драйв")
                    }
                    settings.activity == WaveActivity.ON_THE_ROAD -> {
                        queries.add("Русский синтвейв пост-панк")
                        queries.add("В дорогу русские хиты")
                        queries.add("Русский инди рок")
                    }
                    settings.activity == WaveActivity.WORKING || settings.mood == WaveMood.CALM -> {
                        queries.add("Спокойная русская музыка")
                        queries.add("Русский инди акустика")
                        queries.add("Русский лоуфай чилл")
                    }
                    settings.activity == WaveActivity.FALLING_ASLEEP -> {
                        queries.add("Спокойная музыка для сна")
                        queries.add("Русская акустика вечер")
                    }
                    settings.mood == WaveMood.SAD -> {
                        queries.add("Русский пост-панк")
                        queries.add("Грустные русские песни")
                        queries.add("Русская меланхолия инди")
                    }
                    settings.mood == WaveMood.CHEERFUL || settings.activity == WaveActivity.WAKING_UP -> {
                        queries.add("Бодрые русские хиты")
                        queries.add("Русский поп фанк")
                        queries.add("Русский инди поп")
                    }
                    else -> {
                        queries.add("Русские хиты")
                        queries.add("Русский инди рок")
                        queries.add("Русский пост-панк")
                    }
                }
            }

            WaveLanguage.INSTRUMENTAL -> {
                when {
                    settings.activity == WaveActivity.WORKOUT || settings.mood == WaveMood.ENERGETIC -> {
                        queries.add("Darksynth Instrumental")
                        queries.add("Drift Phonk Instrumental")
                        queries.add("Synthwave Outrun")
                    }
                    settings.activity == WaveActivity.ON_THE_ROAD -> {
                        queries.add("Retrowave Nightdrive")
                        queries.add("Synthwave Instrumental")
                        queries.add("Instrumental Electronic Drive")
                    }
                    settings.activity == WaveActivity.FALLING_ASLEEP || settings.mood == WaveMood.CALM -> {
                        queries.add("Ambient Piano Calm")
                        queries.add("Deep Sleep Ambient")
                        queries.add("Acoustic Guitar Instrumental")
                    }
                    settings.mood == WaveMood.SAD -> {
                        queries.add("Melancholic Piano Instrumental")
                        queries.add("Cinematic Ambient Strings")
                        queries.add("Post-Rock Instrumental")
                    }
                    settings.mood == WaveMood.CHEERFUL || settings.activity == WaveActivity.WAKING_UP -> {
                        queries.add("Jazz Hop Instrumental")
                        queries.add("Upbeat Chillhop Beats")
                        queries.add("Nu Disco Instrumental")
                    }
                    else -> {
                        queries.add("Lofi Chillhop Instrumental")
                        queries.add("Ambient Electronic Study")
                        queries.add("Synthwave Instrumental")
                    }
                }
            }

            WaveLanguage.FOREIGN, null -> {
                when (settings.activity) {
                    WaveActivity.WAKING_UP -> {
                        queries.add("Morning Indie Pop")
                        queries.add("Upbeat Chill Electronic")
                    }
                    WaveActivity.ON_THE_ROAD -> {
                        queries.add("Synthwave Nightdrive")
                        queries.add("Indie Electronic Drive")
                    }
                    WaveActivity.WORKING -> {
                        queries.add("Lofi Chillhop Focus")
                        queries.add("Downtempo Electronic")
                    }
                    WaveActivity.WORKOUT -> {
                        queries.add("Phonk Workout Bass")
                        queries.add("Electro House Energy")
                    }
                    WaveActivity.FALLING_ASLEEP -> {
                        queries.add("Ambient Calm Piano")
                        queries.add("Chillout Atmospheric")
                    }
                    null -> Unit
                }

                when (settings.mood) {
                    WaveMood.ENERGETIC -> {
                        queries.add("Energetic Electronic Dance")
                        queries.add("Upbeat Synth Rock")
                    }
                    WaveMood.CHEERFUL -> {
                        queries.add("Happy Nu Disco Funk")
                        queries.add("Feel Good Indie Pop")
                    }
                    WaveMood.CALM -> {
                        queries.add("Chill Acoustic Ambient")
                        queries.add("Lo-Fi Relaxing")
                    }
                    WaveMood.SAD -> {
                        queries.add("Melancholic Dream Pop")
                        queries.add("Sad Indie Slowed")
                    }
                    null -> Unit
                }
            }
        }

        // 2. Character-specific query enrichment
        when (settings.character) {
            WaveCharacter.FAVORITE -> {
                val matchingArtists = when (settings.language) {
                    WaveLanguage.RUSSIAN -> topUserArtists.filter { containsCyrillic(it) }
                    WaveLanguage.FOREIGN -> topUserArtists.filterNot { containsCyrillic(it) }
                    else -> topUserArtists
                }
                matchingArtists.take(2).forEach { artist ->
                    queries.add(0, artist)
                }
            }
            WaveCharacter.UNFAMILIAR -> {
                when (settings.language) {
                    WaveLanguage.RUSSIAN -> queries.add("Новые русские инди фрешмены")
                    WaveLanguage.INSTRUMENTAL -> queries.add("Underground IDM Ambient")
                    else -> queries.add("Underground Indie Electronic")
                }
            }
            WaveCharacter.POPULAR -> {
                when (settings.language) {
                    WaveLanguage.RUSSIAN -> queries.add("Топ чарт русские хиты")
                    WaveLanguage.INSTRUMENTAL -> queries.add("Best Instrumental Hits")
                    else -> queries.add("Top Global Hits Electronic")
                }
            }
            null -> {
                if (queries.isEmpty()) {
                    if (topUserArtists.isNotEmpty()) {
                        queries.add(topUserArtists.first())
                    }
                    queries.add("Synthwave Electronic Indie")
                    queries.add("Chill Alternative Pop")
                }
            }
        }

        return queries
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .take(4)
    }

    /**
     * Multi-factor scoring, strict filtering, and anti-clumping artist interleaving.
     */
    fun rankAndFilterWaveCandidates(
        rawCandidates: List<Track>,
        settings: WaveSettings,
        favoriteTracks: List<Track>,
        historyTracks: List<Track>,
        likedArtists: Set<String>,
        dislikedTrackIds: Set<String>
    ): List<Track> {
        val favIds = favoriteTracks.map { it.id }.toSet()
        val histIds = historyTracks.map { it.id }.toSet()
        val knownSignatures = (favoriteTracks + historyTracks)
            .map { "${TrackDeduplicationEngine.normalizeText(it.artist)}|${TrackDeduplicationEngine.normalizeText(it.title)}" }
            .toSet()

        val favArtistNamesNorm = (likedArtists + favoriteTracks.map { it.artist })
            .map { TrackDeduplicationEngine.normalizeText(it) }
            .filter { it.isNotBlank() }
            .toSet()

        val histArtistNamesNorm = historyTracks
            .map { TrackDeduplicationEngine.normalizeText(it.artist) }
            .filter { it.isNotBlank() }
            .toSet()

        val dislikedArtistsNorm = rawCandidates
            .filter { it.id in dislikedTrackIds }
            .map { TrackDeduplicationEngine.normalizeText(it.artist) }
            .toSet()

        // Step 1: Deduplicate and remove disliked or live-radio items
        val basePool = TrackDeduplicationEngine.deduplicateAndRank(rawCandidates)
            .filter { track ->
                track.id !in dislikedTrackIds &&
                    !track.isLiveStream &&
                    track.streamUrl.isNotBlank()
            }

        if (basePool.isEmpty()) return emptyList()

        // Step 2: Apply strict Character filter (e.g. UNFAMILIAR removes known tracks)
        val characterFiltered = when (settings.character) {
            WaveCharacter.UNFAMILIAR -> {
                val strictlyUnfamiliar = basePool.filter { track ->
                    val sig = "${TrackDeduplicationEngine.normalizeText(track.artist)}|${TrackDeduplicationEngine.normalizeText(track.title)}"
                    track.id !in favIds &&
                        track.id !in histIds &&
                        !track.isFavorite &&
                        sig !in knownSignatures
                }
                strictlyUnfamiliar.ifEmpty { basePool }
            }
            WaveCharacter.FAVORITE -> {
                val favAndLikedArtistPool = basePool.filter { track ->
                    val artistNorm = TrackDeduplicationEngine.normalizeText(track.artist)
                    track.id in favIds ||
                        track.isFavorite ||
                        artistNorm in favArtistNamesNorm ||
                        track.id in histIds
                }
                favAndLikedArtistPool.ifEmpty { basePool }
            }
            WaveCharacter.POPULAR, null -> basePool
        }

        // Step 3: Apply strict Language filter
        val languageFiltered = when (settings.language) {
            WaveLanguage.RUSSIAN -> {
                val russianOnly = characterFiltered.filter { isRussianTrack(it) }
                russianOnly.ifEmpty { characterFiltered }
            }
            WaveLanguage.FOREIGN -> {
                val foreignOnly = characterFiltered.filterNot { isRussianTrack(it) }
                foreignOnly.ifEmpty { characterFiltered }
            }
            WaveLanguage.INSTRUMENTAL -> {
                val instrumentalOnly = characterFiltered.filter { isInstrumentalTrack(it) }
                instrumentalOnly.ifEmpty {
                    characterFiltered.filterNot { track ->
                        val text = "${track.title} ${track.genre}".lowercase()
                        vocalHeavyKeywords.any { text.contains(it) }
                    }.ifEmpty { characterFiltered }
                }
            }
            null -> characterFiltered
        }

        // Step 4: Score every track according to all 4 Wave axes + personal taste
        val scored = languageFiltered.map { track ->
            var score = 100.0
            val artistNorm = TrackDeduplicationEngine.normalizeText(track.artist)
            val combinedText = "${track.title} ${track.album} ${track.genre} ${track.artist}".lowercase()

            // Full-length stream preference over 30s previews
            if (!track.isPreviewOnly && track.durationSec >= 45) {
                score += 140.0
            } else if (track.isPreviewOnly) {
                score -= 40.0
            }

            // Character scoring
            when (settings.character) {
                WaveCharacter.FAVORITE -> {
                    if (track.id in favIds || track.isFavorite) score += 320.0
                    if (artistNorm in favArtistNamesNorm) score += 220.0
                    if (track.id in histIds) score += 140.0
                }
                WaveCharacter.UNFAMILIAR -> {
                    if (artistNorm !in favArtistNamesNorm && artistNorm !in histArtistNamesNorm) {
                        score += 240.0
                    } else {
                        score -= 120.0
                    }
                }
                WaveCharacter.POPULAR -> {
                    score += (track.fallbackStreams.size.coerceAtMost(5)) * 45.0
                    if (track.primaryProvider == SourceProvider.DEEZER ||
                        track.primaryProvider == SourceProvider.ITUNES ||
                        track.primaryProvider == SourceProvider.YOUTUBE_PIPED
                    ) {
                        score += 80.0
                    }
                }
                null -> {
                    // Balanced personal wave: mix favorites (40%) + discovery (60%)
                    if (track.id in favIds || track.isFavorite) score += 95.0
                    if (artistNorm in favArtistNamesNorm) score += 85.0
                }
            }

            // Mood scoring
            when (settings.mood) {
                WaveMood.ENERGETIC -> {
                    if (energeticKeywords.any { combinedText.contains(it) }) score += 180.0
                    if (calmKeywords.any { combinedText.contains(it) }) score -= 90.0
                    if (track.durationSec in 110..240) score += 35.0
                }
                WaveMood.CHEERFUL -> {
                    if (cheerfulKeywords.any { combinedText.contains(it) }) score += 180.0
                    if (sadKeywords.any { combinedText.contains(it) }) score -= 110.0
                }
                WaveMood.CALM -> {
                    if (calmKeywords.any { combinedText.contains(it) }) score += 190.0
                    if (energeticKeywords.any { combinedText.contains(it) }) score -= 100.0
                }
                WaveMood.SAD -> {
                    if (sadKeywords.any { combinedText.contains(it) }) score += 190.0
                    if (cheerfulKeywords.any { combinedText.contains(it) }) score -= 110.0
                }
                null -> Unit
            }

            // Activity scoring
            when (settings.activity) {
                WaveActivity.WAKING_UP -> {
                    if (cheerfulKeywords.any { combinedText.contains(it) } ||
                        energeticKeywords.any { combinedText.contains(it) }
                    ) score += 130.0
                }
                WaveActivity.ON_THE_ROAD -> {
                    if (combinedText.contains("synth") ||
                        combinedText.contains("drive") ||
                        combinedText.contains("electronic") ||
                        combinedText.contains("rock") ||
                        combinedText.contains("phonk")
                    ) score += 150.0
                }
                WaveActivity.WORKING -> {
                    if (isInstrumentalTrack(track) || calmKeywords.any { combinedText.contains(it) }) {
                        score += 170.0
                    }
                }
                WaveActivity.WORKOUT -> {
                    if (energeticKeywords.any { combinedText.contains(it) }) score += 200.0
                    if (calmKeywords.any { combinedText.contains(it) }) score -= 120.0
                }
                WaveActivity.FALLING_ASLEEP -> {
                    if (calmKeywords.any { combinedText.contains(it) } || isInstrumentalTrack(track)) {
                        score += 210.0
                    }
                    if (energeticKeywords.any { combinedText.contains(it) }) score -= 150.0
                }
                null -> Unit
            }

            // Language scoring
            when (settings.language) {
                WaveLanguage.RUSSIAN -> {
                    if (isRussianTrack(track)) score += 250.0 else score -= 200.0
                }
                WaveLanguage.FOREIGN -> {
                    if (!isRussianTrack(track)) score += 180.0 else score -= 250.0
                }
                WaveLanguage.INSTRUMENTAL -> {
                    if (isInstrumentalTrack(track)) score += 240.0 else score -= 120.0
                }
                null -> Unit
            }

            // Penalize artists of disliked tracks
            if (artistNorm in dislikedArtistsNorm) {
                score -= 180.0
            }

            track to score
        }.sortedByDescending { it.second }.map { it.first }

        return interleaveByArtist(scored)
    }

    /**
     * Prevents tracks by the same artist from playing consecutively in a general wave queue.
     */
    fun interleaveByArtist(tracks: List<Track>): List<Track> {
        if (tracks.size <= 2) return tracks
        val remaining = tracks.toMutableList()
        val result = mutableListOf<Track>()

        while (remaining.isNotEmpty()) {
            val lastArtist = result.lastOrNull()?.let { TrackDeduplicationEngine.normalizeText(it.artist) }
            val nextIndex = if (lastArtist.isNullOrBlank()) {
                0
            } else {
                val differentArtistIdx = remaining.indexOfFirst {
                    TrackDeduplicationEngine.normalizeText(it.artist) != lastArtist
                }
                if (differentArtistIdx >= 0) differentArtistIdx else 0
            }
            result.add(remaining.removeAt(nextIndex))
        }
        return result
    }

    /**
     * Recommends an Equalizer DSP preset matching the active Wave mood & activity.
     */
    fun recommendedEqualizerPreset(settings: WaveSettings): EqualizerPreset {
        return when {
            settings.activity == WaveActivity.WORKOUT || settings.mood == WaveMood.ENERGETIC ->
                EqualizerPreset.BASS_BOOST
            settings.activity == WaveActivity.ON_THE_ROAD || settings.mood == WaveMood.CHEERFUL ->
                EqualizerPreset.ELECTRO
            settings.activity == WaveActivity.FALLING_ASLEEP || settings.mood == WaveMood.CALM ->
                EqualizerPreset.ACOUSTIC
            settings.activity == WaveActivity.WORKING && settings.language == WaveLanguage.INSTRUMENTAL ->
                EqualizerPreset.FLAT
            settings.mood == WaveMood.SAD ->
                EqualizerPreset.VOCAL
            else -> EqualizerPreset.SLANT_PURE_SIGNATURE
        }
    }
}
