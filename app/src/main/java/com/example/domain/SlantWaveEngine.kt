package com.example.domain

/**
 * Multi-dimensional algorithmic recommendation, User Taste DNA analyzer, Artist Similarity Graph,
 * and filtering engine for "Моя волна" (SlantWave), Explore personalized feeds, Search ranking,
 * and Smart Mixes.
 *
 * Synthesizes signals from:
 * 1. Pre-Configuration (Selected Genres, Selected Artists, Preferred Languages, Preferred Eras, Discovery Ratio)
 * 2. Liked Tracks (favoriteTracks)
 * 3. Liked Artists (selectedArtists / likedArtistNames)
 * 4. Collaborative & Genre-Cluster Similar Artists (relatedArtists + relatedArtistsGraph)
 * 5. User Playlists (all tracks inside custom & smart playlists)
 * 6. Liked / Saved Albums (likedAlbums + albums of favorite/playlist tracks)
 * 7. Listening History (historyTracks)
 * 8. Disliked Tracks & Artists (negative feedback loop)
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

    /**
     * Curated Artist Similarity Graph so selecting or liking an artist immediately expands
     * recommendations to closely related artists in the same acoustic & cultural cluster.
     */
    private val relatedArtistsGraph: Map<String, List<String>> = mapOf(
        "кино" to listOf("Перемотка", "Molchat Doma", "Буерак", "Сплин", "Би-2", "Наутилус Помпилиус", "Агата Кристи"),
        "molchat doma" to listOf("Перемотка", "Буерак", "Дурной Вкус", "Ploho", "Кино", "Joy Division", "Depeche Mode"),
        "перемотка" to listOf("Буерак", "Molchat Doma", "Сироткин", "Дурной Вкус", "Кино"),
        "буерак" to listOf("Перемотка", "Molchat Doma", "Дурной Вкус", "Ploho", "Кино"),
        "depeche mode" to listOf("Joy Division", "New Order", "The Cure", "Molchat Doma", "Kavinsky"),
        "joy division" to listOf("New Order", "The Cure", "Molchat Doma", "Depeche Mode", "Interpol"),
        "kavinsky" to listOf("The Midnight", "Timecop1983", "Daft Punk", "M83", "Perturbator"),
        "the midnight" to listOf("Timecop1983", "FM-84", "Kavinsky", "Gunship", "M83"),
        "perturbator" to listOf("Carpenter Brut", "Kavinsky", "Lorn", "Kordhell"),
        "timecop1983" to listOf("The Midnight", "FM-84", "Kavinsky", "Tycho"),
        "m83" to listOf("The Midnight", "Tame Impala", "Kavinsky", "Tycho"),
        "kordhell" to listOf("DVRST", "Skeler", "INTERWORLD", "Hensonn", "LXST CXNTURY"),
        "dvrst" to listOf("Kordhell", "Skeler", "INTERWORLD", "Hensonn", "LXST CXNTURY"),
        "skeler" to listOf("Øfdream", "Lorn", "DVRST", "Kordhell", "Kavinsky"),
        "øfdream" to listOf("Skeler", "Lorn", "DVRST", "Perturbator"),
        "сироткин" to listOf("Перемотка", "Земфира", "Сплин", "Мумий Тролль", "Tame Impala"),
        "земфира" to listOf("Сплин", "Би-2", "Сироткин", "Мумий Тролль", "Кино"),
        "tame impala" to listOf("Arctic Monkeys", "M83", "MGMT", "Gorillaz", "Radiohead"),
        "arctic monkeys" to listOf("The Strokes", "Tame Impala", "Franz Ferdinand", "Radiohead"),
        "radiohead" to listOf("Massive Attack", "Portishead", "Tame Impala", "Depeche Mode"),
        "lana del rey" to listOf("The Weeknd", "Arctic Monkeys", "Tame Impala", "Cigarettes After Sex"),
        "би 2" to listOf("Сплин", "Земфира", "Кино", "Агата Кристи", "Наутилус Помпилиус"),
        "сплин" to listOf("Би-2", "Земфира", "Кино", "Наутилус Помпилиус", "Сироткин"),
        "король и шут" to listOf("Ария", "Кипелов", "Сектор Газа", "Би-2", "Кино"),
        "linkin park" to listOf("Bring Me The Horizon", "Muse", "Pendulum", "Three Days Grace"),
        "bring me the horizon" to listOf("Linkin Park", "Architects", "Bad Omens", "Pendulum"),
        "daft punk" to listOf("Justice", "Kavinsky", "Jamiroquai", "RÜFÜS DU SOL", "Modjo"),
        "bonobo" to listOf("Tycho", "Four Tet", "RÜFÜS DU SOL", "Nujabes"),
        "rüfüs du sol" to listOf("Bonobo", "ODESZA", "Daft Punk", "M83"),
        "pendulum" to listOf("The Prodigy", "Sub Focus", "Linkin Park", "Kavinsky"),
        "lorn" to listOf("Skeler", "Øfdream", "Perturbator", "Bonobo"),
        "miyagi эндшпиль" to listOf("Скриптонит", "Баста", "Масло Черного Тмина", "Santiz", "Эндшпиль"),
        "скриптонит" to listOf("Miyagi & Эндшпиль", "Масло Черного Тмина", "Баста", "The Weeknd"),
        "баста" to listOf("Miyagi & Эндшпиль", "Скриптонит", "Земфира"),
        "the weeknd" to listOf("Daft Punk", "Kavinsky", "Lana Del Rey", "Tame Impala"),
        "nujabes" to listOf("J Dilla", "Bonobo", "Tycho"),
        "tycho" to listOf("Bonobo", "Boards of Canada", "Brian Eno", "Timecop1983"),
        "brian eno" to listOf("Tycho", "Max Richter", "Ludovico Einaudi"),
        "ludovico einaudi" to listOf("Max Richter", "Hans Zimmer", "Ólafur Arnalds", "Yann Tiersen"),
        "hans zimmer" to listOf("Ludovico Einaudi", "Max Richter", "Ramin Djawadi"),
        "jamiroquai" to listOf("Daft Punk", "Dua Lipa", "Parcels"),
        "dua lipa" to listOf("The Weeknd", "Daft Punk", "Jamiroquai", "Kavinsky")
    )

    fun getRelatedArtistsFor(artistName: String): List<String> {
        val norm = TrackDeduplicationEngine.normalizeText(artistName)
        relatedArtistsGraph[norm]?.let { return it }

        // Check alias match
        for ((key, related) in relatedArtistsGraph) {
            if (TrackDeduplicationEngine.areArtistNamesEquivalent(key, artistName)) {
                return related
            }
        }

        // Fallback: same genre in starterArtistsCatalog
        val seed = starterArtistsCatalog.firstOrNull {
            TrackDeduplicationEngine.areArtistNamesEquivalent(it.name, artistName)
        }
        if (seed != null) {
            return starterArtistsCatalog
                .filter { it.genreId == seed.genreId && !it.name.equals(seed.name, ignoreCase = true) }
                .map { it.name }
        }
        return emptyList()
    }

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
            track.genre.contains("Русск", ignoreCase = true) ||
            starterArtistsCatalog.any {
                it.isRussian && TrackDeduplicationEngine.areArtistNamesEquivalent(it.name, track.artist)
            }
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

        // Also check if the artist is in starterArtistsCatalog or related to a starter artist
        starterArtistsCatalog.firstOrNull {
            TrackDeduplicationEngine.areArtistNamesEquivalent(it.name, track.artist)
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
            if (cleanAlbum.length < 2 ||
                cleanAlbum.endsWith("— Single") ||
                cleanAlbum == "YouTube Music" ||
                cleanAlbum == "Audius Network"
            ) return
            val key = "${artist.trim()} — $cleanAlbum"
            albumScores[key] = (albumScores[key] ?: 0) + weight
        }

        // 1. Explicit pre-configured genres (+150 weight)
        tasteProfile.selectedGenres.forEach { genre ->
            genreScores[genre] = (genreScores[genre] ?: 0) + 150
        }

        // 2. Explicit pre-configured & liked artists (+140 weight)
        (tasteProfile.selectedArtists + likedArtists).forEach { artist ->
            addArtistWeight(artist, 140)
            starterArtistsCatalog.firstOrNull {
                TrackDeduplicationEngine.areArtistNamesEquivalent(it.name, artist)
            }?.let { seed ->
                TasteGenreOption.fromId(seed.genreId)?.let { g ->
                    genreScores[g] = (genreScores[g] ?: 0) + 55
                }
            }
        }

        // 3. Explicit liked albums (+100 weight)
        tasteProfile.likedAlbums.forEach { albumKey ->
            val parts = albumKey.split("|")
            val art = parts.getOrNull(0).orEmpty()
            val alb = parts.getOrNull(1).orEmpty()
            if (art.isNotBlank()) addArtistWeight(art, 90)
            if (alb.isNotBlank()) addAlbumWeight(art, alb, 100)
        }

        if (tasteProfile.autoAdaptFromCollection) {
            // 4. Liked Tracks (favoriteTracks: +85 artist, +70 genre, +65 album)
            favoriteTracks.forEach { track ->
                addArtistWeight(track.artist, 85)
                if (track.album.isNotBlank()) {
                    addAlbumWeight(track.artist, track.album, 65)
                }
                inferTrackGenres(track).forEach { g ->
                    genreScores[g] = (genreScores[g] ?: 0) + 70
                }
            }

            // 5. User Playlists (playlistTracks: +65 artist, +55 genre, +55 album)
            playlistTracks.forEach { track ->
                addArtistWeight(track.artist, 65)
                if (track.album.isNotBlank()) {
                    addAlbumWeight(track.artist, track.album, 55)
                }
                inferTrackGenres(track).forEach { g ->
                    genreScores[g] = (genreScores[g] ?: 0) + 55
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

        val dominantLanguage = when {
            tasteProfile.preferredLanguages.size == 1 -> tasteProfile.preferredLanguages.first().title
            tasteProfile.preferredLanguages.size > 1 ->
                tasteProfile.preferredLanguages.joinToString("/") { it.title }
            else -> {
                val sample = favoriteTracks + playlistTracks + historyTracks.take(20)
                if (sample.isEmpty()) {
                    if (topArtists.isNotEmpty() && topArtists.count { containsCyrillic(it) } > topArtists.size / 2) {
                        "Преимущественно Русский"
                    } else {
                        "Мультиязычный"
                    }
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
            allSelectedArtistsCount * 3 +
            favoriteTracks.size * 2 +
            playlistTracks.distinctBy { it.id }.size +
            tasteProfile.likedAlbums.size * 2 +
            historyTracks.size.coerceAtMost(20)

        val personalizationPercent = (32 + (totalSignals * 3)).coerceIn(34, 99)

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

    data class PersonalizedExploreQueryPlan(
        val forYouQueries: List<String>,
        val favoriteArtistsQueries: List<String>,
        val genreRadarQueries: List<String>,
        val radioQuery: String
    )

    private fun cleanGenreSearchTerm(genre: TasteGenreOption, prefersRussian: Boolean): String {
        if (prefersRussian) {
            return when (genre) {
                TasteGenreOption.POST_PUNK -> "Русский пост-панк"
                TasteGenreOption.SYNTHWAVE -> "Retrowave Synthwave"
                TasteGenreOption.PHONK -> "Drift Phonk"
                TasteGenreOption.INDIE_ALT -> "Русский инди"
                TasteGenreOption.ROCK_METAL -> "Русский рок"
                TasteGenreOption.ELECTRONIC_CLUB -> "Melodic Techno"
                TasteGenreOption.HIP_HOP_RAP -> "Русский хип-хоп"
                TasteGenreOption.LOFI_CHILL -> "Lofi Chillhop"
                TasteGenreOption.POP_DISCO -> "Русский поп"
                TasteGenreOption.AMBIENT_SPACE -> "Space Ambient"
                TasteGenreOption.NEOCLASSICAL_OST -> "Neoclassical Piano"
                TasteGenreOption.JAZZ_SOUL -> "Smooth Jazz"
            }
        }
        return when (genre) {
            TasteGenreOption.POST_PUNK -> "Post-Punk Coldwave"
            TasteGenreOption.SYNTHWAVE -> "Synthwave Retrowave"
            TasteGenreOption.PHONK -> "Drift Phonk"
            TasteGenreOption.INDIE_ALT -> "Indie Rock Dream Pop"
            TasteGenreOption.ROCK_METAL -> "Alternative Rock"
            TasteGenreOption.ELECTRONIC_CLUB -> "Melodic Techno Electronic"
            TasteGenreOption.HIP_HOP_RAP -> "Hip-Hop Beats"
            TasteGenreOption.LOFI_CHILL -> "Lofi Chillhop"
            TasteGenreOption.POP_DISCO -> "Nu Disco Synth Pop"
            TasteGenreOption.AMBIENT_SPACE -> "Space Ambient"
            TasteGenreOption.NEOCLASSICAL_OST -> "Neoclassical Piano"
            TasteGenreOption.JAZZ_SOUL -> "Smooth Jazz Neo Soul"
        }
    }

    fun buildPersonalizedExploreQueries(
        tasteProfile: UserTasteProfile,
        dnaSummary: UserTasteDnaSummary,
        relatedArtists: List<String> = emptyList()
    ): PersonalizedExploreQueryPlan {
        val prefersRussian = (WaveLanguage.RUSSIAN in tasteProfile.preferredLanguages &&
            WaveLanguage.FOREIGN !in tasteProfile.preferredLanguages) ||
            dnaSummary.dominantLanguageLabel.contains("Преимущественно Русский", ignoreCase = true)

        val topArtists = dnaSummary.topArtists
        val topGenres = dnaSummary.topGenres

        val artistQueries = topArtists.take(4).map { it.trim() }.filter { it.isNotBlank() }
        val similarArtistQueries = (relatedArtists + topArtists.flatMap { getRelatedArtistsFor(it) })
            .distinctBy { it.lowercase() }
            .filterNot { rel -> artistQueries.any { it.equals(rel, ignoreCase = true) } }
            .take(4)

        val genreQueries = topGenres.take(4).map { genre ->
            cleanGenreSearchTerm(genre, prefersRussian)
        }

        val forYouQueries = buildList {
            artistQueries.take(2).forEach { add(it) }
            similarArtistQueries.take(2).forEach { add(it) }
            genreQueries.take(2).forEach { add(it) }
            if (isEmpty()) {
                add("Synthwave Retrowave")
                add("Post-Punk Coldwave")
            }
        }.distinctBy { it.lowercase() }.take(5)

        val finalArtistQueries = (artistQueries + similarArtistQueries).ifEmpty {
            listOf("Кино", "Daft Punk", "The Midnight")
        }.distinctBy { it.lowercase() }.take(4)

        val finalGenreQueries = (genreQueries + similarArtistQueries.take(2)).ifEmpty {
            listOf("Synthwave Retrowave", "Indie Rock Dream Pop")
        }.distinctBy { it.lowercase() }.take(4)

        val radioQuery = when {
            WaveLanguage.INSTRUMENTAL in tasteProfile.preferredLanguages -> "Ambient"
            topGenres.firstOrNull() == TasteGenreOption.SYNTHWAVE -> "Synthwave"
            topGenres.firstOrNull() == TasteGenreOption.POST_PUNK -> "Post-Punk"
            topGenres.firstOrNull() == TasteGenreOption.PHONK -> "Phonk"
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
     * Builds 5-6 focused, high-precision search queries for "Моя волна" combining WaveSettings,
     * UserTasteProfile, Collection DNA, and the Artist Similarity Graph.
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
        val relatedArtists = topUserArtists.flatMap { getRelatedArtistsFor(it) }
            .distinctBy { it.lowercase() }
            .filterNot { rel -> topUserArtists.any { it.equals(rel, ignoreCase = true) } }

        val effectiveLanguage = settings.language
            ?: tasteProfile.preferredLanguages.singleOrNull()

        fun filterByLang(artists: List<String>): List<String> = when (effectiveLanguage) {
            WaveLanguage.RUSSIAN -> artists.filter { containsCyrillic(it) }.ifEmpty { artists }
            WaveLanguage.FOREIGN -> artists.filterNot { containsCyrillic(it) }.ifEmpty { artists }
            else -> artists
        }

        // 1. Inject user's favorite artists & similar artists according to character/discovery balance
        when (settings.character) {
            WaveCharacter.FAVORITE -> {
                filterByLang(topUserArtists).take(4).forEach { queries.add(it) }
                filterByLang(relatedArtists).take(1).forEach { queries.add(it) }
            }
            WaveCharacter.UNFAMILIAR -> {
                filterByLang(relatedArtists).take(3).forEach { queries.add(it) }
            }
            else -> {
                when (tasteProfile.discoveryBalance) {
                    TasteDiscoveryBalance.FAVORITE_FOCUSED -> {
                        filterByLang(topUserArtists).take(3).forEach { queries.add(it) }
                        filterByLang(relatedArtists).take(1).forEach { queries.add(it) }
                    }
                    TasteDiscoveryBalance.BALANCED -> {
                        filterByLang(topUserArtists).take(2).forEach { queries.add(it) }
                        filterByLang(relatedArtists).take(2).forEach { queries.add(it) }
                    }
                    TasteDiscoveryBalance.DISCOVERY_HUNTER -> {
                        filterByLang(relatedArtists).take(3).forEach { queries.add(it) }
                        filterByLang(topUserArtists).take(1).forEach { queries.add(it) }
                    }
                }
            }
        }

        // 2. Inject user's top genres using clean search terms
        if (topUserGenres.isNotEmpty()) {
            val prefersRus = effectiveLanguage == WaveLanguage.RUSSIAN
            topUserGenres.take(2).forEach { genre ->
                queries.add(cleanGenreSearchTerm(genre, prefersRus))
            }
        }

        // 3. Language-aware Mood + Activity core queries
        when (effectiveLanguage) {
            WaveLanguage.RUSSIAN -> {
                when {
                    settings.activity == WaveActivity.WORKOUT || settings.mood == WaveMood.ENERGETIC ->
                        queries.add("DVRST Kordhell")
                    settings.activity == WaveActivity.ON_THE_ROAD ->
                        queries.add("Перемотка Молчат Дома")
                    settings.activity == WaveActivity.WORKING || settings.mood == WaveMood.CALM ->
                        queries.add("Сироткин акустика")
                    settings.activity == WaveActivity.FALLING_ASLEEP ->
                        queries.add("Спокойная музыка пианино")
                    settings.mood == WaveMood.SAD ->
                        queries.add("Русский пост-панк")
                    settings.mood == WaveMood.CHEERFUL || settings.activity == WaveActivity.WAKING_UP ->
                        queries.add("Русский инди поп")
                    else -> {
                        if (queries.isEmpty()) {
                            queries.add("Кино")
                            queries.add("Перемотка")
                        }
                    }
                }
            }

            WaveLanguage.INSTRUMENTAL -> {
                when {
                    settings.activity == WaveActivity.WORKOUT || settings.mood == WaveMood.ENERGETIC ->
                        queries.add("Darksynth Instrumental")
                    settings.activity == WaveActivity.ON_THE_ROAD ->
                        queries.add("Synthwave Instrumental")
                    settings.activity == WaveActivity.FALLING_ASLEEP || settings.mood == WaveMood.CALM ->
                        queries.add("Ambient Piano")
                    settings.mood == WaveMood.SAD ->
                        queries.add("Neoclassical Piano")
                    else ->
                        queries.add("Lofi Chillhop Instrumental")
                }
            }

            WaveLanguage.FOREIGN, null -> {
                when (settings.activity) {
                    WaveActivity.WAKING_UP -> queries.add("Indie Pop Upbeat")
                    WaveActivity.ON_THE_ROAD -> queries.add("Synthwave Retrowave")
                    WaveActivity.WORKING -> queries.add("Lofi Chillhop")
                    WaveActivity.WORKOUT -> queries.add("Drift Phonk")
                    WaveActivity.FALLING_ASLEEP -> queries.add("Space Ambient Piano")
                    null -> Unit
                }

                when (settings.mood) {
                    WaveMood.ENERGETIC -> queries.add("Electronic Dance Bass")
                    WaveMood.CHEERFUL -> queries.add("Nu Disco Indie Pop")
                    WaveMood.CALM -> queries.add("Chill Ambient")
                    WaveMood.SAD -> queries.add("Post-Punk Dream Pop")
                    null -> Unit
                }
            }
        }

        if (queries.isEmpty()) {
            queries.add("Synthwave Retrowave")
            queries.add("Post-Punk Coldwave")
        }

        return queries
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .take(6)
    }

    /**
     * Multi-factor scoring, strict teaser avoidance, DNA reason tagging, similarity graph boost,
     * and anti-clumping artist interleaving.
     */
    fun rankAndFilterWaveCandidates(
        rawCandidates: List<Track>,
        settings: WaveSettings,
        tasteProfile: UserTasteProfile = UserTasteProfile(),
        favoriteTracks: List<Track>,
        playlistTracks: List<Track> = emptyList(),
        historyTracks: List<Track>,
        likedArtists: Set<String>,
        dislikedTrackIds: Set<String>,
        relatedArtists: Set<String> = emptySet()
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

        val allLikedArtistNames = tasteProfile.selectedArtists + likedArtists + favoriteTracks.map { it.artist }
        val selectedAndLikedArtistsNorm = allLikedArtistNames
            .map { TrackDeduplicationEngine.normalizeText(TrackDeduplicationEngine.extractPrimaryArtistName(it)) }
            .filter { it.isNotBlank() }
            .toSet()

        val expandedRelatedArtists = (
            relatedArtists +
                allLikedArtistNames.flatMap { getRelatedArtistsFor(it) } +
                dna.topArtists.flatMap { getRelatedArtistsFor(it) }
            )
        val relatedArtistsNorm = expandedRelatedArtists
            .map { TrackDeduplicationEngine.normalizeText(TrackDeduplicationEngine.extractPrimaryArtistName(it)) }
            .filter { it.isNotBlank() && it !in selectedAndLikedArtistsNorm }
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

        // Step 1: Deduplicate, remove disliked/live items, and strictly filter out 30s teasers if preferFullLengthTracks is enabled
        val deduplicatedPool = TrackDeduplicationEngine.deduplicateAndRank(rawCandidates)
            .filter { track ->
                track.id !in dislikedTrackIds &&
                    !track.isLiveStream &&
                    track.streamUrl.isNotBlank() &&
                    !TrackDeduplicationEngine.isTeaserOrSnippetTitle(track.title)
            }

        val basePool = if (tasteProfile.preferFullLengthTracks) {
            val fullLengthPool = deduplicatedPool.filter { track ->
                val hasFullCandidate = track.allStreamCandidates().any { !it.isPreview }
                (!track.isPreviewOnly || hasFullCandidate) && (track.durationSec == 0 || track.durationSec >= 42)
            }
            if (fullLengthPool.size >= 4) fullLengthPool else deduplicatedPool
        } else {
            deduplicatedPool
        }

        if (basePool.isEmpty()) return emptyList()

        // Step 2: Apply Character filter
        val characterFiltered = when (settings.character) {
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
                        allLikedArtistNames.any { TrackDeduplicationEngine.isTrackByArtist(track, it) } ||
                        artistNorm in playlistArtistsNorm ||
                        (albumNorm.isNotBlank() && albumNorm in likedAlbumNamesNorm) ||
                        track.id in histIds
                }
                favAndLikedArtistPool.ifEmpty { basePool }
            }
            WaveCharacter.POPULAR, null -> basePool
        }

        // Step 3: Apply Language filter
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

        // Step 4: Score every track using multi-signal DNA + Artist Similarity Graph + Wave axes
        val familiarMult = tasteProfile.discoveryBalance.familiarWeightMultiplier
        val discoveryMult = tasteProfile.discoveryBalance.discoveryWeightMultiplier
        val hasPersonalSignals = tasteProfile.hasAnyExplicitPreferences ||
            favoriteTracks.isNotEmpty() ||
            playlistTracks.isNotEmpty()

        val scoredPairs = languageFiltered.map { track ->
            var score = 100.0
            val primaryArtist = TrackDeduplicationEngine.extractPrimaryArtistName(track.artist)
            val artistNorm = TrackDeduplicationEngine.normalizeText(primaryArtist)
            val albumNorm = TrackDeduplicationEngine.normalizeText(track.album)
            val combinedText = "${track.title} ${track.album} ${track.genre} ${track.artist}".lowercase()
            val matchedGenres = inferTrackGenres(track)

            var primaryReason = ""

            // 1. Full-length stream preference
            val hasDirectFullStream = !track.isPreviewOnly && track.durationSec >= 45
            if (hasDirectFullStream) {
                score += if (tasteProfile.preferFullLengthTracks) 190.0 else 110.0
            } else if (track.isPreviewOnly) {
                score -= if (tasteProfile.preferFullLengthTracks) 180.0 else 45.0
            }

            // 2. Direct User Taste DNA Affinity (Artists, Similar Artists, Liked Tracks, Playlists, Albums)
            val isFavoriteTrack = track.id in favIds || track.isFavorite
            val isInUserPlaylist = track.id in playlistIds
            val isSelectedOrLikedArtist = artistNorm in selectedAndLikedArtistsNorm ||
                allLikedArtistNames.any { TrackDeduplicationEngine.isTrackByArtist(track, it) }
            val isSimilarRelatedArtist = !isSelectedOrLikedArtist && (
                artistNorm in relatedArtistsNorm ||
                    expandedRelatedArtists.any { TrackDeduplicationEngine.isTrackByArtist(track, it) }
                )
            val isPlaylistArtist = artistNorm in playlistArtistsNorm
            val isLikedAlbum = albumNorm.length >= 3 && albumNorm in likedAlbumNamesNorm
            val matchingUserGenre = matchedGenres.firstOrNull { it in activeTargetGenres }

            if (isFavoriteTrack) {
                score += 240.0 * familiarMult
                primaryReason = "♥ В ваших любимых треках"
            }
            if (isInUserPlaylist) {
                score += 200.0 * familiarMult
                if (primaryReason.isBlank()) primaryReason = "♪ Из ваших плейлистов"
            }
            if (isSelectedOrLikedArtist) {
                score += 290.0 * familiarMult
                if (primaryReason.isBlank()) primaryReason = "★ Любимый автор: $primaryArtist"
            } else if (isSimilarRelatedArtist) {
                score += 225.0 * discoveryMult
                if (primaryReason.isBlank()) primaryReason = "✦ Похож на ваших любимых авторов"
            } else if (isPlaylistArtist) {
                score += 165.0 * familiarMult
                if (primaryReason.isBlank()) primaryReason = "♪ Автор из ваших плейлистов"
            }
            if (isLikedAlbum) {
                score += 185.0 * familiarMult
                if (primaryReason.isBlank()) primaryReason = "💿 Из любимого альбома «${track.album}»"
            }
            if (matchingUserGenre != null) {
                score += 210.0
                if (primaryReason.isBlank()) {
                    primaryReason = "✦ Ваш жанр: ${matchingUserGenre.title.substringBefore(" &")}"
                }
            }

            // Penalize completely off-profile tracks when the user has configured explicit preferences
            val matchesAnyPersonalSignal = isFavoriteTrack ||
                isInUserPlaylist ||
                isSelectedOrLikedArtist ||
                isSimilarRelatedArtist ||
                isPlaylistArtist ||
                isLikedAlbum ||
                matchingUserGenre != null

            if (hasPersonalSignals && !matchesAnyPersonalSignal) {
                score -= 260.0
            }

            // Discovery bonus for unfamiliar artists that match user's chosen genres or similarity graph
            val isCompletelyNewArtist = !isSelectedOrLikedArtist &&
                !isPlaylistArtist &&
                artistNorm !in histArtistNamesNorm

            if (isCompletelyNewArtist && (matchingUserGenre != null || isSimilarRelatedArtist)) {
                score += 170.0 * discoveryMult
                if (tasteProfile.discoveryBalance == TasteDiscoveryBalance.DISCOVERY_HUNTER ||
                    settings.character == WaveCharacter.UNFAMILIAR
                ) {
                    primaryReason = if (isSimilarRelatedArtist) {
                        "⚡ Новое имя • Похож на ваших авторов"
                    } else {
                        "⚡ Открытие в жанре ${matchingUserGenre?.title?.substringBefore(" &").orEmpty()}"
                    }
                }
            }

            // 3. Era match bonus
            if (tasteProfile.preferredEras.isNotEmpty() && track.releaseYear.length >= 4) {
                val eraMatched = tasteProfile.preferredEras.any { era ->
                    era.yearPrefixes.any { prefix -> track.releaseYear.startsWith(prefix) }
                }
                if (eraMatched) {
                    score += 100.0
                }
            }

            // 4. Character axis scoring
            when (settings.character) {
                WaveCharacter.FAVORITE -> {
                    if (isFavoriteTrack) score += 290.0
                    if (isSelectedOrLikedArtist) score += 250.0
                    if (isInUserPlaylist || isLikedAlbum) score += 190.0
                }
                WaveCharacter.UNFAMILIAR -> {
                    if (isCompletelyNewArtist) {
                        score += 270.0
                        if (primaryReason.isBlank()) primaryReason = "⚡ Новое имя для вашей коллекции"
                    } else {
                        score -= 160.0
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
                    if (isRussianTrack(track)) score += 260.0 else score -= 220.0
                }
                WaveLanguage.FOREIGN -> {
                    if (!isRussianTrack(track)) score += 190.0 else score -= 260.0
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
                        if (matchesAnyLang) score += 120.0 else score -= 90.0
                    }
                }
            }

            // Penalize artists of disliked tracks
            if (artistNorm in dislikedArtistsNorm) {
                score -= 260.0
            }

            val matchPct = ((score - 60.0) / 6.0).toInt().coerceIn(74, 99)
            val finalReason = if (primaryReason.isNotBlank()) {
                "$primaryReason • $matchPct% ДНК"
            } else {
                "✦ По вашему музыкальному ДНК • $matchPct%"
            }

            track.copy(recommendationReason = finalReason) to score
        }.sortedByDescending { it.second }

        // Drop low-scoring off-profile noise if we have a healthy pool of relevant tracks
        val highRelevance = scoredPairs.filter { it.second >= 180.0 }.map { it.first }
        val finalCandidates = if (highRelevance.size >= 8) {
            highRelevance
        } else {
            scoredPairs.map { it.first }
        }

        return interleaveByArtist(finalCandidates)
    }

    /**
     * Prevents tracks by the same artist from playing consecutively in a general wave queue
     * while preserving top relevance ranking.
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
                    !TrackDeduplicationEngine.areArtistNamesEquivalent(it.artist, lastArtist)
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
