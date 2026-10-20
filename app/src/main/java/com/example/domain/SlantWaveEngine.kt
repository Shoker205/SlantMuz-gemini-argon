package com.example.domain

/**
 * Multi-dimensional algorithmic recommendation, User Taste DNA analyzer, and filtering engine
 * for "Моя волна" (SlantWave), Explore personalized feeds, Search ranking, and Smart Mixes.
 *
 * Synthesizes signals from:
 * 1. Pre-Configuration (Selected Genres, Selected Artists, Preferred Languages, Preferred Eras, Discovery Ratio)
 * 2. Liked Tracks (favoriteTracks)
 * 3. Liked Artists (selectedArtists / likedArtistNames)
 * 4. User Playlists (all tracks inside custom & smart playlists)
 * 5. Liked / Saved Albums (likedAlbums + albums of favorite/playlist tracks)
 * 6. Listening History (historyTracks)
 * 7. Disliked Tracks & Artists (negative feedback loop)
 */
object SlantWaveEngine {

    private val cyrillicRegex = Regex("""[\p{IsCyrillic}]""")

    val starterArtistsCatalog: List<StarterArtistSeed> = listOf(
        // Пост-панк & Новая волна
        StarterArtistSeed("Кино", TasteGenreOption.POST_PUNK.id, "Легенда русского пост-панка и рока", isRussian = true),
        StarterArtistSeed("Molchat Doma", TasteGenreOption.POST_PUNK.id, "Coldwave / Синт-пост-панк", isRussian = true),
        StarterArtistSeed("Перемотка", TasteGenreOption.POST_PUNK.id, "Дрим-поп / Русская новая волна", isRussian = true),
        StarterArtistSeed("Буерак", TasteGenreOption.POST_PUNK.id, "Сибирский инди пост-панк", isRussian = true),
        StarterArtistSeed("Depeche Mode", TasteGenreOption.POST_PUNK.id, "Synth-Pop / Dark Wave", isRussian = false),
        StarterArtistSeed("Joy Division", TasteGenreOption.POST_PUNK.id, "Классика мирового пост-панка", isRussian = false),

        // Синтвейв & Retrowave
        StarterArtistSeed("Kavinsky", TasteGenreOption.SYNTHWAVE.id, "Outrun / French Electro", isRussian = false),
        StarterArtistSeed("The Midnight", TasteGenreOption.SYNTHWAVE.id, "Retrowave / Саксофон и неон", isRussian = false),
        StarterArtistSeed("Perturbator", TasteGenreOption.SYNTHWAVE.id, "Darksynth / Cyberpunk", isRussian = false),
        StarterArtistSeed("Timecop1983", TasteGenreOption.SYNTHWAVE.id, "Dreamwave / Nostalgic Synth", isRussian = false),
        StarterArtistSeed("M83", TasteGenreOption.SYNTHWAVE.id, "Electronic Shoegaze / Synth", isRussian = false),

        // Фонк & Wave Bass
        StarterArtistSeed("Kordhell", TasteGenreOption.PHONK.id, "Drift Phonk / Aggressive Bass", isRussian = false),
        StarterArtistSeed("DVRST", TasteGenreOption.PHONK.id, "Atmospheric Phonk / Chill Phonk", isRussian = true),
        StarterArtistSeed("Skeler", TasteGenreOption.PHONK.id, "Hardwave / Night Drive Wave", isRussian = false),
        StarterArtistSeed("Øfdream", TasteGenreOption.PHONK.id, "Dark Wave / Atmospheric Bass", isRussian = false),

        // Инди & Альтернатива
        StarterArtistSeed("Сироткин", TasteGenreOption.INDIE_ALT.id, "Атмосферный инди-поп", isRussian = true),
        StarterArtistSeed("Земфира", TasteGenreOption.INDIE_ALT.id, "Русский инди-рок и альтернатива", isRussian = true),
        StarterArtistSeed("Tame Impala", TasteGenreOption.INDIE_ALT.id, "Psychedelic Indie / Synth", isRussian = false),
        StarterArtistSeed("Arctic Monkeys", TasteGenreOption.INDIE_ALT.id, "Indie Rock / Garage Revival", isRussian = false),
        StarterArtistSeed("Radiohead", TasteGenreOption.INDIE_ALT.id, "Art Rock / Experimental Indie", isRussian = false),
        StarterArtistSeed("Lana Del Rey", TasteGenreOption.INDIE_ALT.id, "Cinematic Dream Pop", isRussian = false),

        // Рок, Панк & Метал
        StarterArtistSeed("Би-2", TasteGenreOption.ROCK_METAL.id, "Альтернативный русский рок", isRussian = true),
        StarterArtistSeed("Сплин", TasteGenreOption.ROCK_METAL.id, "Русский рок и лирика", isRussian = true),
        StarterArtistSeed("Король и Шут", TasteGenreOption.ROCK_METAL.id, "Хоррор-панк / Фолк-рок", isRussian = true),
        StarterArtistSeed("Linkin Park", TasteGenreOption.ROCK_METAL.id, "Alternative Rock / Electronic Rock", isRussian = false),
        StarterArtistSeed("Bring Me The Horizon", TasteGenreOption.ROCK_METAL.id, "Modern Rock / Post-Hardcore", isRussian = false),

        // Электроника & Техно
        StarterArtistSeed("Daft Punk", TasteGenreOption.ELECTRONIC_CLUB.id, "French House / Electronic", isRussian = false),
        StarterArtistSeed("Bonobo", TasteGenreOption.ELECTRONIC_CLUB.id, "Downtempo / Organic Electronic", isRussian = false),
        StarterArtistSeed("RÜFÜS DU SOL", TasteGenreOption.ELECTRONIC_CLUB.id, "Melodic House / Live Electronic", isRussian = false),
        StarterArtistSeed("Pendulum", TasteGenreOption.ELECTRONIC_CLUB.id, "Drum & Bass / Electronic Rock", isRussian = false),
        StarterArtistSeed("Lorn", TasteGenreOption.ELECTRONIC_CLUB.id, "IDM / Dark Experimental Bass", isRussian = false),

        // Хип-хоп, Рэп & R&B
        StarterArtistSeed("Miyagi & Эндшпиль", TasteGenreOption.HIP_HOP_RAP.id, "Melodic Hip-Hop / Reggae Vibe", isRussian = true),
        StarterArtistSeed("Скриптонит", TasteGenreOption.HIP_HOP_RAP.id, "Альтернативный хип-хоп и грув", isRussian = true),
        StarterArtistSeed("Баста", TasteGenreOption.HIP_HOP_RAP.id, "Лирический хип-хоп", isRussian = true),
        StarterArtistSeed("The Weeknd", TasteGenreOption.HIP_HOP_RAP.id, "Dark R&B / Synthwave Pop", isRussian = false),

        // Lo-Fi, Эмбиент & Неоклассика
        StarterArtistSeed("Nujabes", TasteGenreOption.LOFI_CHILL.id, "Jazz Hop / Lo-Fi Beats", isRussian = false),
        StarterArtistSeed("Tycho", TasteGenreOption.AMBIENT_SPACE.id, "Ambient Chillwave / Instrumental", isRussian = false),
        StarterArtistSeed("Brian Eno", TasteGenreOption.AMBIENT_SPACE.id, "Space Ambient / Meditative", isRussian = false),
        StarterArtistSeed("Ludovico Einaudi", TasteGenreOption.NEOCLASSICAL_OST.id, "Neoclassical Piano", isRussian = false),
        StarterArtistSeed("Hans Zimmer", TasteGenreOption.NEOCLASSICAL_OST.id, "Epic Cinematic Soundtracks", isRussian = false),
        StarterArtistSeed("Jamiroquai", TasteGenreOption.JAZZ_SOUL.id, "Acid Jazz / Funk Groove", isRussian = false),
        StarterArtistSeed("Dua Lipa", TasteGenreOption.POP_DISCO.id, "Nu-Disco / Dance Pop", isRussian = false)
    )

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
     * Infers matching TasteGenreOption entries for a track based on its genre, title, album, and artist.
     */
    fun inferTrackGenres(track: Track): List<TasteGenreOption> {
        val text = "${track.genre} ${track.title} ${track.album} ${track.artist}".lowercase()
        val matched = TasteGenreOption.entries.filter { option ->
            option.matchKeywords.any { kw -> text.contains(kw) }
        }.toMutableList()

        // Also check if the artist is in starterArtistsCatalog
        val normArtist = TrackDeduplicationEngine.normalizeText(track.artist)
        starterArtistsCatalog.firstOrNull {
            TrackDeduplicationEngine.normalizeText(it.name) == normArtist
        }?.let { seed ->
            TasteGenreOption.fromId(seed.genreId)?.let { seedGenre ->
                if (seedGenre !in matched) matched.add(0, seedGenre)
            }
        }
        return matched
    }

    /**
     * Synthesizes a complete UserTasteDnaSummary from explicit pre-configuration +
     * liked tracks + liked artists + user playlists + liked albums + listening history.
     */
    fun computeTasteDnaSummary(
        tasteProfile: UserTasteProfile,
        favoriteTracks: List<Track>,
        playlistTracks: List<Track>,
        historyTracks: List<Track>,
        likedArtists: Set<String>
    ): UserTasteDnaSummary {
        val artistScores = mutableMapOf<String, Int>()
        val artistDisplayNames = mutableMapOf<String, String>()
        val genreScores = mutableMapOf<TasteGenreOption, Int>()
        val albumScores = mutableMapOf<String, Int>()

        fun addArtistWeight(rawName: String, weight: Int) {
            val primary = TrackDeduplicationEngine.extractPrimaryArtistName(rawName).trim()
            if (primary.length < 2 || primary.equals("Unknown", ignoreCase = true)) return
            val norm = TrackDeduplicationEngine.normalizeText(primary)
            if (norm.isBlank()) return
            artistDisplayNames.putIfAbsent(norm, primary)
            artistScores[norm] = (artistScores[norm] ?: 0) + weight
        }

        fun addAlbumWeight(artist: String, album: String, weight: Int) {
            val cleanAlbum = album.trim()
            if (cleanAlbum.length < 2 || cleanAlbum.endsWith("— Single")) return
            val key = "${artist.trim()} — $cleanAlbum"
            albumScores[key] = (albumScores[key] ?: 0) + weight
        }

        // 1. Explicit pre-configured genres (+130 weight)
        tasteProfile.selectedGenres.forEach { genre ->
            genreScores[genre] = (genreScores[genre] ?: 0) + 130
        }

        // 2. Explicit pre-configured & liked artists (+120 weight)
        (tasteProfile.selectedArtists + likedArtists).forEach { artist ->
            addArtistWeight(artist, 120)
            starterArtistsCatalog.firstOrNull {
                it.name.equals(artist.trim(), ignoreCase = true)
            }?.let { seed ->
                TasteGenreOption.fromId(seed.genreId)?.let { g ->
                    genreScores[g] = (genreScores[g] ?: 0) + 45
                }
            }
        }

        // 3. Explicit liked albums (+95 weight)
        tasteProfile.likedAlbums.forEach { albumKey ->
            val parts = albumKey.split("|")
            val art = parts.getOrNull(0).orEmpty()
            val alb = parts.getOrNull(1).orEmpty()
            if (art.isNotBlank()) addArtistWeight(art, 85)
            if (alb.isNotBlank()) addAlbumWeight(art, alb, 95)
        }

        if (tasteProfile.autoAdaptFromCollection) {
            // 4. Liked Tracks (favoriteTracks: +75 artist, +65 genre, +60 album)
            favoriteTracks.forEach { track ->
                addArtistWeight(track.artist, 75)
                if (track.album.isNotBlank()) {
                    addAlbumWeight(track.artist, track.album, 60)
                }
                inferTrackGenres(track).forEach { g ->
                    genreScores[g] = (genreScores[g] ?: 0) + 65
                }
            }

            // 5. User Playlists (playlistTracks: +60 artist, +50 genre, +50 album)
            playlistTracks.forEach { track ->
                addArtistWeight(track.artist, 60)
                if (track.album.isNotBlank()) {
                    addAlbumWeight(track.artist, track.album, 50)
                }
                inferTrackGenres(track).forEach { g ->
                    genreScores[g] = (genreScores[g] ?: 0) + 50
                }
            }

            // 6. Listening History (historyTracks: +30 artist, +25 genre)
            historyTracks.take(40).forEachIndexed { index, track ->
                val recencyBonus = (20 - index / 2).coerceAtLeast(5)
                addArtistWeight(track.artist, 25 + recencyBonus)
                inferTrackGenres(track).forEach { g ->
                    genreScores[g] = (genreScores[g] ?: 0) + 25
                }
            }
        }

        val topArtists = artistScores.entries
            .sortedByDescending { it.value }
            .mapNotNull { artistDisplayNames[it.key] }
            .take(14)

        val topGenres = genreScores.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .ifEmpty {
                listOf(
                    TasteGenreOption.SYNTHWAVE,
                    TasteGenreOption.POST_PUNK,
                    TasteGenreOption.INDIE_ALT
                )
            }
            .take(6)

        val topAlbums = albumScores.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(10)

        // Language profile analysis
        val dominantLanguage = when {
            tasteProfile.preferredLanguages.size == 1 -> tasteProfile.preferredLanguages.first().title
            tasteProfile.preferredLanguages.size > 1 ->
                tasteProfile.preferredLanguages.joinToString("/") { it.title }
            else -> {
                val sample = favoriteTracks + playlistTracks + historyTracks.take(20)
                if (sample.isEmpty()) {
                    "Мультиязычный"
                } else {
                    val rusRatio = sample.count { isRussianTrack(it) }.toFloat() / sample.size.toFloat()
                    when {
                        rusRatio >= 0.65f -> "Преимущественно Русский"
                        rusRatio <= 0.25f -> "Преимущественно Иностранный"
                        else -> "Русский & Иностранный"
                    }
                }
            }
        }

        val allSelectedArtistsCount = (tasteProfile.selectedArtists + likedArtists)
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .distinct()
            .size

        val totalSignals = tasteProfile.selectedGenreIds.size * 3 +
            allSelectedArtistsCount * 2 +
            favoriteTracks.size * 2 +
            playlistTracks.distinctBy { it.id }.size +
            tasteProfile.likedAlbums.size * 2 +
            historyTracks.size.coerceAtMost(20)

        val personalizationPercent = (30 + (totalSignals * 3)).coerceIn(32, 99)

        return UserTasteDnaSummary(
            topArtists = topArtists,
            topGenres = topGenres,
            topAlbums = topAlbums,
            dominantLanguageLabel = dominantLanguage,
            personalizationPercent = personalizationPercent,
            totalSignalsCount = totalSignals,
            likedTracksCount = favoriteTracks.size,
            playlistTracksCount = playlistTracks.distinctBy { it.id }.size,
            likedAlbumsCount = (tasteProfile.likedAlbums.size + topAlbums.size).coerceAtLeast(tasteProfile.likedAlbums.size),
            selectedArtistsCount = allSelectedArtistsCount
        )
    }

    /**
     * Generates targeted search queries for personalized Explore sections:
     * 1. Personal For You Mix
     * 2. Favorite Artists & Albums Feed
     * 3. Genre Radar Feed
     * 4. Personalized Live Radio Query
     */
    data class PersonalizedExploreQueryPlan(
        val forYouQueries: List<String>,
        val favoriteArtistsQueries: List<String>,
        val genreRadarQueries: List<String>,
        val radioQuery: String
    )

    fun buildPersonalizedExploreQueries(
        tasteProfile: UserTasteProfile,
        dnaSummary: UserTasteDnaSummary
    ): PersonalizedExploreQueryPlan {
        val prefersRussian = WaveLanguage.RUSSIAN in tasteProfile.preferredLanguages ||
            dnaSummary.dominantLanguageLabel.contains("Русский", ignoreCase = true)

        val topArtists = dnaSummary.topArtists
        val topGenres = dnaSummary.topGenres

        val artistQueries = topArtists.take(4).map { it.trim() }.filter { it.isNotBlank() }

        val genreQueries = topGenres.take(4).map { genre ->
            if (prefersRussian && WaveLanguage.FOREIGN !in tasteProfile.preferredLanguages) {
                genre.russianQuery
            } else {
                genre.primaryQuery
            }
        }

        val eraHint = tasteProfile.preferredEras.firstOrNull()?.queryKeyword.orEmpty()

        val forYouQueries = buildList {
            if (artistQueries.isNotEmpty()) add(artistQueries.first())
            if (genreQueries.isNotEmpty()) {
                val firstGenre = genreQueries.first()
                add(if (eraHint.isNotBlank()) "$firstGenre $eraHint" else firstGenre)
            }
            if (artistQueries.size >= 2) add(artistQueries[1])
            if (genreQueries.size >= 2) add(genreQueries[1])
            if (isEmpty()) {
                add("Synthwave Electronic Indie")
                add("Post-Punk New Wave")
            }
        }.distinct().take(4)

        val finalArtistQueries = artistQueries.ifEmpty {
            listOf("Кино", "Daft Punk", "The Midnight")
        }.take(3)

        val finalGenreQueries = genreQueries.ifEmpty {
            listOf("Synthwave Retrowave", "Indie Rock Dream Pop")
        }.take(3)

        val radioQuery = when {
            WaveLanguage.INSTRUMENTAL in tasteProfile.preferredLanguages -> "Ambient Chillout"
            topGenres.firstOrNull() == TasteGenreOption.SYNTHWAVE -> "Synthwave"
            topGenres.firstOrNull() == TasteGenreOption.LOFI_CHILL -> "Lofi"
            topGenres.firstOrNull() == TasteGenreOption.JAZZ_SOUL -> "Jazz"
            topGenres.firstOrNull() == TasteGenreOption.ROCK_METAL -> "Rock"
            topGenres.firstOrNull() == TasteGenreOption.ELECTRONIC_CLUB -> "Techno"
            else -> "Chill"
        }

        return PersonalizedExploreQueryPlan(
            forYouQueries = forYouQueries,
            favoriteArtistsQueries = finalArtistQueries,
            genreRadarQueries = finalGenreQueries,
            radioQuery = radioQuery
        )
    }

    /**
     * Builds 4-5 focused, high-precision search queries for "Моя волна" combining WaveSettings
     * and the full UserTasteProfile + Collection DNA.
     */
    fun buildTargetedWaveQueries(
        settings: WaveSettings,
        tasteProfile: UserTasteProfile,
        favoriteTracks: List<Track>,
        playlistTracks: List<Track>,
        historyTracks: List<Track>,
        likedArtists: Set<String>
    ): List<String> {
        val dna = computeTasteDnaSummary(
            tasteProfile = tasteProfile,
            favoriteTracks = favoriteTracks,
            playlistTracks = playlistTracks,
            historyTracks = historyTracks,
            likedArtists = likedArtists
        )

        val queries = mutableListOf<String>()
        val topUserArtists = dna.topArtists
        val topUserGenres = dna.topGenres

        val effectiveLanguage = settings.language
            ?: tasteProfile.preferredLanguages.firstOrNull()

        // 1. If user has Favorite character OR Favorite-focused discovery balance, inject top DNA artists first
        if (settings.character == WaveCharacter.FAVORITE ||
            tasteProfile.discoveryBalance == TasteDiscoveryBalance.FAVORITE_FOCUSED
        ) {
            val langFilteredArtists = when (effectiveLanguage) {
                WaveLanguage.RUSSIAN -> topUserArtists.filter { containsCyrillic(it) }
                WaveLanguage.FOREIGN -> topUserArtists.filterNot { containsCyrillic(it) }
                else -> topUserArtists
            }.ifEmpty { topUserArtists }

            langFilteredArtists.take(2).forEach { queries.add(it) }
        }

        // 2. Combine user's chosen genres with active Wave mood/activity/language
        if (topUserGenres.isNotEmpty() && settings.character != WaveCharacter.UNFAMILIAR) {
            topUserGenres.take(2).forEach { genre ->
                val baseGenreQuery = if (effectiveLanguage == WaveLanguage.RUSSIAN) {
                    genre.russianQuery
                } else {
                    genre.primaryQuery
                }
                queries.add(baseGenreQuery)
            }
        }

        // 3. Language-aware Mood + Activity core queries
        when (effectiveLanguage) {
            WaveLanguage.RUSSIAN -> {
                when {
                    settings.activity == WaveActivity.WORKOUT || settings.mood == WaveMood.ENERGETIC -> {
                        queries.add("Русский фонк драйв")
                        queries.add("Русский рок хиты")
                    }
                    settings.activity == WaveActivity.ON_THE_ROAD -> {
                        queries.add("Русский синтвейв пост-панк")
                        queries.add("В дорогу русские хиты")
                    }
                    settings.activity == WaveActivity.WORKING || settings.mood == WaveMood.CALM -> {
                        queries.add("Спокойная русская музыка")
                        queries.add("Русский инди акустика")
                    }
                    settings.activity == WaveActivity.FALLING_ASLEEP -> {
                        queries.add("Спокойная музыка для сна")
                        queries.add("Русская акустика вечер")
                    }
                    settings.mood == WaveMood.SAD -> {
                        queries.add("Русский пост-панк")
                        queries.add("Русская меланхолия инди")
                    }
                    settings.mood == WaveMood.CHEERFUL || settings.activity == WaveActivity.WAKING_UP -> {
                        queries.add("Бодрые русские хиты")
                        queries.add("Русский инди поп")
                    }
                    else -> {
                        if (queries.isEmpty()) {
                            queries.add("Русский инди пост-панк")
                            queries.add("Русский рок лучшие")
                        }
                    }
                }
            }

            WaveLanguage.INSTRUMENTAL -> {
                when {
                    settings.activity == WaveActivity.WORKOUT || settings.mood == WaveMood.ENERGETIC -> {
                        queries.add("Darksynth Instrumental")
                        queries.add("Drift Phonk Instrumental")
                    }
                    settings.activity == WaveActivity.ON_THE_ROAD -> {
                        queries.add("Retrowave Nightdrive Instrumental")
                        queries.add("Synthwave Instrumental")
                    }
                    settings.activity == WaveActivity.FALLING_ASLEEP || settings.mood == WaveMood.CALM -> {
                        queries.add("Ambient Piano Calm")
                        queries.add("Deep Sleep Ambient")
                    }
                    settings.mood == WaveMood.SAD -> {
                        queries.add("Melancholic Piano Instrumental")
                        queries.add("Post-Rock Instrumental")
                    }
                    settings.mood == WaveMood.CHEERFUL || settings.activity == WaveActivity.WAKING_UP -> {
                        queries.add("Jazz Hop Instrumental")
                        queries.add("Upbeat Chillhop Beats")
                    }
                    else -> {
                        queries.add("Lofi Chillhop Instrumental")
                        queries.add("Synthwave Instrumental")
                    }
                }
            }

            WaveLanguage.FOREIGN, null -> {
                when (settings.activity) {
                    WaveActivity.WAKING_UP -> queries.add("Morning Indie Pop Upbeat")
                    WaveActivity.ON_THE_ROAD -> queries.add("Synthwave Nightdrive Electronic")
                    WaveActivity.WORKING -> queries.add("Lofi Chillhop Focus Study")
                    WaveActivity.WORKOUT -> queries.add("Phonk Workout Electro Bass")
                    WaveActivity.FALLING_ASLEEP -> queries.add("Ambient Calm Piano Sleep")
                    null -> Unit
                }

                when (settings.mood) {
                    WaveMood.ENERGETIC -> queries.add("Energetic Electronic Dance Rock")
                    WaveMood.CHEERFUL -> queries.add("Happy Nu Disco Indie Pop")
                    WaveMood.CALM -> queries.add("Chill Acoustic Ambient")
                    WaveMood.SAD -> queries.add("Melancholic Post-Punk Dream Pop")
                    null -> Unit
                }
            }
        }

        // 4. Inject top artist or discovery query so personal taste is always represented
        when (settings.character) {
            WaveCharacter.UNFAMILIAR -> {
                val genreKeyword = topUserGenres.firstOrNull()?.primaryQuery ?: "Indie Electronic"
                queries.add(0, "Underground Fresh $genreKeyword")
            }
            WaveCharacter.POPULAR -> {
                val genreKeyword = topUserGenres.firstOrNull()?.primaryQuery ?: "Electronic Pop"
                queries.add(0, "Top Hits $genreKeyword")
            }
            else -> {
                if (topUserArtists.isNotEmpty() && queries.none { q ->
                        topUserArtists.any { a -> q.equals(a, ignoreCase = true) }
                    }
                ) {
                    queries.add(0, topUserArtists.first())
                }
            }
        }

        return queries
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .take(5)
    }

    /**
     * Multi-factor scoring, strict filtering, DNA reason tagging, and anti-clumping artist interleaving.
     */
    fun rankAndFilterWaveCandidates(
        rawCandidates: List<Track>,
        settings: WaveSettings,
        tasteProfile: UserTasteProfile = UserTasteProfile(),
        favoriteTracks: List<Track>,
        playlistTracks: List<Track> = emptyList(),
        historyTracks: List<Track>,
        likedArtists: Set<String>,
        dislikedTrackIds: Set<String>
    ): List<Track> {
        val dna = computeTasteDnaSummary(
            tasteProfile = tasteProfile,
            favoriteTracks = favoriteTracks,
            playlistTracks = playlistTracks,
            historyTracks = historyTracks,
            likedArtists = likedArtists
        )

        val favIds = favoriteTracks.map { it.id }.toSet()
        val playlistIds = playlistTracks.map { it.id }.toSet()
        val histIds = historyTracks.map { it.id }.toSet()

        val knownSignatures = (favoriteTracks + playlistTracks + historyTracks)
            .map { "${TrackDeduplicationEngine.normalizeText(it.artist)}|${TrackDeduplicationEngine.normalizeText(it.title)}" }
            .toSet()

        val selectedAndLikedArtistsNorm = (
            tasteProfile.selectedArtists +
                likedArtists +
                favoriteTracks.map { it.artist }
            )
            .map { TrackDeduplicationEngine.normalizeText(TrackDeduplicationEngine.extractPrimaryArtistName(it)) }
            .filter { it.isNotBlank() }
            .toSet()

        val playlistArtistsNorm = playlistTracks
            .map { TrackDeduplicationEngine.normalizeText(TrackDeduplicationEngine.extractPrimaryArtistName(it.artist)) }
            .filter { it.isNotBlank() }
            .toSet()

        val histArtistNamesNorm = historyTracks
            .map { TrackDeduplicationEngine.normalizeText(TrackDeduplicationEngine.extractPrimaryArtistName(it.artist)) }
            .filter { it.isNotBlank() }
            .toSet()

        val likedAlbumNamesNorm = (
            tasteProfile.likedAlbums.map { it.substringAfter("|") } +
                favoriteTracks.map { it.album } +
                playlistTracks.map { it.album }
            )
            .map { TrackDeduplicationEngine.normalizeText(it) }
            .filter { it.length >= 3 }
            .toSet()

        val activeTargetGenres = (tasteProfile.selectedGenres + dna.topGenres).distinct()

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

        // Step 2: Apply strict Character filter (or Discovery Balance filter)
        val effectiveCharacter = settings.character ?: when (tasteProfile.discoveryBalance) {
            TasteDiscoveryBalance.DISCOVERY_HUNTER -> null
            else -> null
        }

        val characterFiltered = when (effectiveCharacter) {
            WaveCharacter.UNFAMILIAR -> {
                val strictlyUnfamiliar = basePool.filter { track ->
                    val sig = "${TrackDeduplicationEngine.normalizeText(track.artist)}|${TrackDeduplicationEngine.normalizeText(track.title)}"
                    track.id !in favIds &&
                        track.id !in playlistIds &&
                        track.id !in histIds &&
                        !track.isFavorite &&
                        sig !in knownSignatures
                }
                strictlyUnfamiliar.ifEmpty { basePool }
            }
            WaveCharacter.FAVORITE -> {
                val favAndLikedArtistPool = basePool.filter { track ->
                    val artistNorm = TrackDeduplicationEngine.normalizeText(
                        TrackDeduplicationEngine.extractPrimaryArtistName(track.artist)
                    )
                    val albumNorm = TrackDeduplicationEngine.normalizeText(track.album)
                    track.id in favIds ||
                        track.id in playlistIds ||
                        track.isFavorite ||
                        artistNorm in selectedAndLikedArtistsNorm ||
                        artistNorm in playlistArtistsNorm ||
                        (albumNorm.isNotBlank() && albumNorm in likedAlbumNamesNorm) ||
                        track.id in histIds
                }
                favAndLikedArtistPool.ifEmpty { basePool }
            }
            WaveCharacter.POPULAR, null -> basePool
        }

        // Step 3: Apply strict Language filter (explicit Wave setting or single preferred language in TasteProfile)
        val effectiveLanguage = settings.language
            ?: tasteProfile.preferredLanguages.singleOrNull()

        val languageFiltered = when (effectiveLanguage) {
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

        // Step 4: Score every track using multi-signal DNA + Wave axes and attach recommendationReason
        val familiarMult = tasteProfile.discoveryBalance.familiarWeightMultiplier
        val discoveryMult = tasteProfile.discoveryBalance.discoveryWeightMultiplier

        val scored = languageFiltered.map { track ->
            var score = 100.0
            val primaryArtist = TrackDeduplicationEngine.extractPrimaryArtistName(track.artist)
            val artistNorm = TrackDeduplicationEngine.normalizeText(primaryArtist)
            val albumNorm = TrackDeduplicationEngine.normalizeText(track.album)
            val combinedText = "${track.title} ${track.album} ${track.genre} ${track.artist}".lowercase()
            val matchedGenres = inferTrackGenres(track)

            var primaryReason = ""

            // 1. Full-length stream preference
            if (!track.isPreviewOnly && track.durationSec >= 45) {
                score += if (tasteProfile.preferFullLengthTracks) 175.0 else 110.0
            } else if (track.isPreviewOnly) {
                score -= if (tasteProfile.preferFullLengthTracks) 110.0 else 35.0
            }

            // 2. Direct User Taste DNA Affinity (Artists, Liked Tracks, Playlists, Albums)
            val isFavoriteTrack = track.id in favIds || track.isFavorite
            val isInUserPlaylist = track.id in playlistIds
            val isSelectedOrLikedArtist = artistNorm in selectedAndLikedArtistsNorm
            val isPlaylistArtist = artistNorm in playlistArtistsNorm
            val isLikedAlbum = albumNorm.length >= 3 && albumNorm in likedAlbumNamesNorm
            val matchingUserGenre = matchedGenres.firstOrNull { it in activeTargetGenres }

            if (isFavoriteTrack) {
                score += 210.0 * familiarMult
                primaryReason = "♥ В ваших любимых треках"
            }
            if (isInUserPlaylist) {
                score += 185.0 * familiarMult
                if (primaryReason.isBlank()) primaryReason = "♪ Из ваших плейлистов"
            }
            if (isSelectedOrLikedArtist) {
                score += 240.0 * familiarMult
                if (primaryReason.isBlank()) primaryReason = "★ Любимый автор: $primaryArtist"
            } else if (isPlaylistArtist) {
                score += 150.0 * familiarMult
                if (primaryReason.isBlank()) primaryReason = "♪ Автор из ваших плейлистов"
            }
            if (isLikedAlbum) {
                score += 165.0 * familiarMult
                if (primaryReason.isBlank()) primaryReason = "💿 Из любимого альбома «${track.album}»"
            }
            if (matchingUserGenre != null) {
                score += 195.0
                if (primaryReason.isBlank()) {
                    primaryReason = "✦ Ваш жанр: ${matchingUserGenre.title.substringBefore(" &")}"
                }
            }

            // Discovery bonus for unfamiliar artists that match user's chosen genres
            val isCompletelyNewArtist = artistNorm !in selectedAndLikedArtistsNorm &&
                artistNorm !in playlistArtistsNorm &&
                artistNorm !in histArtistNamesNorm

            if (isCompletelyNewArtist && matchingUserGenre != null) {
                score += 155.0 * discoveryMult
                if (tasteProfile.discoveryBalance == TasteDiscoveryBalance.DISCOVERY_HUNTER ||
                    settings.character == WaveCharacter.UNFAMILIAR
                ) {
                    primaryReason = "⚡ Открытие в жанре ${matchingUserGenre.title.substringBefore(" &")}"
                }
            }

            // 3. Era match bonus
            if (tasteProfile.preferredEras.isNotEmpty() && track.releaseYear.length >= 4) {
                val eraMatched = tasteProfile.preferredEras.any { era ->
                    era.yearPrefixes.any { prefix -> track.releaseYear.startsWith(prefix) }
                }
                if (eraMatched) {
                    score += 95.0
                }
            }

            // 4. Character axis scoring
            when (settings.character) {
                WaveCharacter.FAVORITE -> {
                    if (isFavoriteTrack) score += 280.0
                    if (isSelectedOrLikedArtist) score += 220.0
                    if (isInUserPlaylist || isLikedAlbum) score += 180.0
                }
                WaveCharacter.UNFAMILIAR -> {
                    if (isCompletelyNewArtist) {
                        score += 260.0
                        if (primaryReason.isBlank()) primaryReason = "⚡ Новое имя для вашей коллекции"
                    } else {
                        score -= 140.0
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
                null -> Unit
            }

            // 5. Mood axis scoring
            when (settings.mood) {
                WaveMood.ENERGETIC -> {
                    if (energeticKeywords.any { combinedText.contains(it) }) score += 180.0
                    if (calmKeywords.any { combinedText.contains(it) }) score -= 90.0
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

            // 6. Activity axis scoring
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

            // 7. Language axis scoring
            when (effectiveLanguage) {
                WaveLanguage.RUSSIAN -> {
                    if (isRussianTrack(track)) score += 250.0 else score -= 200.0
                }
                WaveLanguage.FOREIGN -> {
                    if (!isRussianTrack(track)) score += 180.0 else score -= 250.0
                }
                WaveLanguage.INSTRUMENTAL -> {
                    if (isInstrumentalTrack(track)) score += 240.0 else score -= 120.0
                }
                null -> {
                    if (tasteProfile.preferredLanguages.isNotEmpty()) {
                        val matchesAnyLang = tasteProfile.preferredLanguages.any { lang ->
                            when (lang) {
                                WaveLanguage.RUSSIAN -> isRussianTrack(track)
                                WaveLanguage.FOREIGN -> !isRussianTrack(track)
                                WaveLanguage.INSTRUMENTAL -> isInstrumentalTrack(track)
                            }
                        }
                        if (matchesAnyLang) score += 110.0 else score -= 75.0
                    }
                }
            }

            // Penalize artists of disliked tracks
            if (artistNorm in dislikedArtistsNorm) {
                score -= 220.0
            }

            val matchPct = ((score - 60.0) / 5.5).toInt().coerceIn(72, 99)
            val finalReason = if (primaryReason.isNotBlank()) {
                "$primaryReason • $matchPct% ДНК"
            } else {
                "✦ По вашему музыкальному ДНК • $matchPct%"
            }

            track.copy(recommendationReason = finalReason) to score
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
     * Recommends an Equalizer DSP preset matching the active Wave mood, activity, or top user genre.
     */
    fun recommendedEqualizerPreset(
        settings: WaveSettings,
        tasteProfile: UserTasteProfile = UserTasteProfile()
    ): EqualizerPreset {
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
            TasteGenreOption.PHONK.id in tasteProfile.selectedGenreIds ->
                EqualizerPreset.BASS_BOOST
            TasteGenreOption.SYNTHWAVE.id in tasteProfile.selectedGenreIds ||
                TasteGenreOption.ELECTRONIC_CLUB.id in tasteProfile.selectedGenreIds ->
                EqualizerPreset.ELECTRO
            TasteGenreOption.NEOCLASSICAL_OST.id in tasteProfile.selectedGenreIds ||
                TasteGenreOption.JAZZ_SOUL.id in tasteProfile.selectedGenreIds ->
                EqualizerPreset.ACOUSTIC
            else -> EqualizerPreset.SLANT_PURE_SIGNATURE
        }
    }
}
