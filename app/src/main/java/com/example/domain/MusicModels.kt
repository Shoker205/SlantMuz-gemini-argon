package com.example.domain

import kotlinx.serialization.Serializable

/**
 * Supported multi-source audio providers in SlantMuz.
 */
@Serializable
enum class SourceProvider(
    val displayName: String,
    val badgeLabel: String,
    val badgeColorHex: Long,
    val description: String,
    val defaultMirrors: List<String> = emptyList()
) {
    LOCAL(
        displayName = "На устройстве",
        badgeLabel = "LOCAL",
        badgeColorHex = 0xFF10B981,
        description = "Локальные аудиофайлы устройства (FLAC, WAV, MP3, M4A)"
    ),
    YOUTUBE_PIPED(
        displayName = "Full Audio Stream / YouTube",
        badgeLabel = "FULL STREAM",
        badgeColorHex = 0xFFEF4444,
        description = "Полноценные студийные аудиопотоки (320kbps MP4 / Opus / M4A)",
        defaultMirrors = listOf(
            "https://saavn.dev",
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.tokhmi.xyz",
            "https://inv.tux.pizza"
        )
    ),
    JAMENDO(
        displayName = "Jamendo Full HD",
        badgeLabel = "JAMENDO HD",
        badgeColorHex = 0xFF06B6D4,
        description = "Полные студийные треки (MP3 320kbps / FLAC)",
        defaultMirrors = listOf("https://api.jamendo.com")
    ),
    AUDIUS(
        displayName = "Audius Decentralized",
        badgeLabel = "AUDIUS 320",
        badgeColorHex = 0xFFA855F7,
        description = "Полные треки от независимых и электронных артистов (320kbps)",
        defaultMirrors = listOf(
            "https://discoveryprovider.audius.co",
            "https://discoveryprovider2.audius.co",
            "https://discoveryprovider3.audius.co"
        )
    ),
    DEEZER(
        displayName = "Deezer Catalog + Full Resolver",
        badgeLabel = "DEEZER HD",
        badgeColorHex = 0xFFEC4899,
        description = "Дискографии, обложки и разрешение в полные аудиопотоки",
        defaultMirrors = listOf("https://api.deezer.com")
    ),
    ITUNES(
        displayName = "Apple Music Catalog + Full Resolver",
        badgeLabel = "APPLE HD",
        badgeColorHex = 0xFF6366F1,
        description = "Каталог релизов, обложек и разрешение в полные треки",
        defaultMirrors = listOf("https://itunes.apple.com")
    ),
    RADIO_BROWSER(
        displayName = "Global Radio 30K+",
        badgeLabel = "RADIO LIVE",
        badgeColorHex = 0xFFF59E0B,
        description = "30,000+ мировых FM и интернет-радиостанций в реальном времени",
        defaultMirrors = listOf(
            "https://de1.api.radio-browser.info",
            "https://fr1.api.radio-browser.info",
            "https://at1.api.radio-browser.info"
        )
    ),
    INTERNET_ARCHIVE(
        displayName = "Internet Archive Audio",
        badgeLabel = "ARCHIVE FULL",
        badgeColorHex = 0xFF14B8A6,
        description = "Миллионы полных записей, альбомов, саундтреков и концертов",
        defaultMirrors = listOf("https://archive.org")
    );

    val shortBadge: String
        get() = badgeLabel

    val defaultBitrateKbps: Int
        get() = 320
}

/**
 * Checks whether a given URL points to a 30-second chorus preview/teaser (Deezer/iTunes/etc.)
 * rather than a full-length audio stream.
 */
fun isPreviewSnippetUrl(url: String): Boolean {
    if (url.isBlank()) return true
    val lower = url.lowercase()
    return lower.contains("dzcdn.net") ||
        lower.contains("cdns-preview") ||
        lower.contains("cdnt-preview") ||
        lower.contains("e-cdns-proxy") ||
        lower.contains("itunes.apple.com") ||
        lower.contains("mzstatic.com") ||
        lower.contains("audio-ssl.itunes") ||
        lower.contains("/preview/") ||
        lower.contains("preview_") ||
        lower.contains("_preview.")
}

@Serializable
data class StreamCandidate(
    val provider: SourceProvider,
    val streamUrl: String,
    val qualityLabel: String = "320 kbps",
    val bitrateKbps: Int = 320,
    val isDirectStream: Boolean = true,
    val isPreview: Boolean = false,
    val instanceHost: String = provider.defaultMirrors.firstOrNull()?.removePrefix("https://") ?: provider.name.lowercase(),
    val codec: String = "AAC/MP3"
) {
    val url: String
        get() = streamUrl
}

/**
 * Unified domain model for a playable audio track or live radio stream.
 */
@Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val artworkUrl: String = "",
    val durationSec: Int = 0,
    val primaryProvider: SourceProvider = SourceProvider.JAMENDO,
    val streamUrl: String = "",
    val fallbackStreams: List<StreamCandidate> = emptyList(),
    val isLiveStream: Boolean = false,
    val isPreviewOnly: Boolean = false,
    val genre: String = "Music",
    val releaseYear: String = "",
    val localUri: String? = null,
    val isFavorite: Boolean = false,
    val isCachedOffline: Boolean = false,
    val offlineFilePath: String? = null,
    val recommendationReason: String = "",
    val bitrateKbps: Int = 320
) {
    val source: SourceProvider
        get() = primaryProvider

    val durationMs: Long
        get() = durationSec.toLong().coerceAtLeast(0L) * 1000L

    /**
     * Returns true if this track currently only has a 30-second preview stream or snippet duration.
     * SlantMuz strictly filters out snippet streams so only full-length tracks are played.
     */
    fun isSnippetStream(): Boolean {
        if (isLiveStream) return false
        if (isCachedOffline && !offlineFilePath.isNullOrBlank()) return false
        if (!localUri.isNullOrBlank()) return false
        if (isPreviewOnly) return true
        if (isPreviewSnippetUrl(streamUrl)) return true
        if (durationSec in 1..48) return true
        return false
    }

    /**
     * Ordered list of all playable FULL-LENGTH candidate URLs for ExoPlayer failover.
     * Snippet preview URLs are excluded from playback candidates.
     */
    fun allStreamCandidates(): List<StreamCandidate> {
        val list = mutableListOf<StreamCandidate>()
        if (isCachedOffline && !offlineFilePath.isNullOrBlank()) {
            list.add(
                StreamCandidate(
                    provider = SourceProvider.LOCAL,
                    streamUrl = offlineFilePath,
                    qualityLabel = "OFFLINE FILE",
                    bitrateKbps = 320,
                    isDirectStream = true,
                    isPreview = false
                )
            )
        }
        if (!localUri.isNullOrBlank()) {
            list.add(
                StreamCandidate(
                    provider = SourceProvider.LOCAL,
                    streamUrl = localUri,
                    qualityLabel = "LOCAL STORAGE",
                    bitrateKbps = 320,
                    isDirectStream = true,
                    isPreview = false
                )
            )
        }
        if (streamUrl.isNotBlank() && !isPreviewSnippetUrl(streamUrl) && !isPreviewOnly) {
            list.add(
                StreamCandidate(
                    provider = primaryProvider,
                    streamUrl = streamUrl,
                    qualityLabel = formattedBitrate(),
                    bitrateKbps = bitrateKbps,
                    isDirectStream = !streamUrl.startsWith("ytwatch://"),
                    isPreview = false
                )
            )
        }
        fallbackStreams.forEach { candidate ->
            if (candidate.streamUrl.isNotBlank() &&
                !candidate.isPreview &&
                !isPreviewSnippetUrl(candidate.streamUrl) &&
                list.none { it.streamUrl == candidate.streamUrl }
            ) {
                list.add(candidate)
            }
        }
        return list
    }

    val formattedDuration: String
        get() {
            if (isLiveStream) return "LIVE"
            if (durationSec <= 0) return "3:30"
            val mins = durationSec / 60
            val secs = durationSec % 60
            return "%d:%02d".format(mins, secs)
        }

    fun formattedBitrate(): String {
        if (isLiveStream) return "LIVE STREAM"
        val bestBitrate = fallbackStreams.maxOfOrNull { it.bitrateKbps } ?: bitrateKbps
        return when {
            isCachedOffline -> "OFFLINE 320 kbps"
            localUri != null -> "LOSSLESS / LOCAL"
            bestBitrate >= 300 -> "FULL HD • $bestBitrate kbps"
            bestBitrate > 0 -> "FULL • $bestBitrate kbps"
            else -> "FULL STREAM • 320 kbps"
        }
    }
}

/**
 * Cloud Account & Synchronization State for Firebase Auth + Firestore.
 */
@Serializable
data class CloudAccountState(
    val isSignedIn: Boolean = false,
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val isSyncing: Boolean = false,
    val lastSyncedAtMs: Long = 0L,
    val syncedTracksCount: Int = 0,
    val syncedPlaylistsCount: Int = 0,
    val syncedArtistsCount: Int = 0,
    val statusMessage: String = "Войдите в аккаунт для облачного сохранения коллекции и ДНК",
    val errorMessage: String? = null
)

@Serializable
enum class LibrarySubCategory(
    val id: String,
    val title: String,
    val subtitle: String
) {
    PLAYLISTS("playlists", "Плейлисты", "Ваши пользовательские подборки и миксы"),
    ALBUMS("albums", "Альбомы", "Сохраненные релизы и альбомы из вашей медиатеки"),
    OFFLINE("offline", "Скачанное", "Полные треки, сохраненные в память для оффлайн-прослушивания"),
    LOCAL_FILES("local_files", "На устройстве", "Аудиофайлы из внутренней памяти смартфона"),
    ARTISTS("artists", "Исполнители", "Артисты из ваших лайков и истории прослушиваний"),
    HISTORY("history", "История", "Недавно прослушанные вами композиции");

    companion object {
        fun fromId(id: String?): LibrarySubCategory =
            entries.firstOrNull { it.id == id || it.name.equals(id, ignoreCase = true) } ?: PLAYLISTS
    }
}

@Serializable
enum class SettingsSubCategory(
    val id: String,
    val title: String,
    val subtitle: String
) {
    ACCOUNT_CLOUD(
        "account_cloud",
        "Аккаунт и облачная синхронизация",
        "Вход/регистрация, облачный бэкап лайков, плейлистов и Музыкального ДНК"
    ),
    TASTE_DNA(
        "taste_dna",
        "Музыкальный ДНК и вкусы",
        "Выбор любимых жанров, 48 узких поджанров, авторов и ориентации рекомендаций"
    ),
    AUDIO_DSP(
        "audio_dsp",
        "Звук и 5-полосный DSP Эквалайзер",
        "Настройка частот, Bass Boost, 3D Virtualizer и Loudness Enhancer"
    ),
    STREAMING_QUALITY(
        "streaming_quality",
        "Потоковое аудио и анти-превью фильтр",
        "Строгий фильтр только полных треков (без 30-сек сниппетов) и битрейт"
    ),
    PROVIDERS_HUB(
        "providers_hub",
        "Источники музыки и провайдеры",
        "Управление активными каталогами и проверка пинга узлов"
    ),
    STORAGE_CACHE(
        "storage_cache",
        "Память, кэш и оффлайн-файлы",
        "Лимит дискового кэша ExoPlayer и очистка скачанных данных"
    ),
    APPEARANCE_THEME(
        "appearance_theme",
        "Оформление и темы Liquid Glass",
        "Тёмное обсидиановое стекло, светлый режим и Material You"
    ),
    HOME_WIDGETS(
        "home_widgets",
        "Виджеты для рабочего стола",
        "Интерактивная галерея анимированных виджетов 2×1, 4×2 и 4×3"
    ),
    ABOUT_APP(
        "about_app",
        "О приложении SlantMuz",
        "Версия, архитектура аудиоядра Media3 и статус систем"
    );

    companion object {
        fun fromId(id: String?): SettingsSubCategory =
            entries.firstOrNull { it.id == id || it.name.equals(id, ignoreCase = true) } ?: TASTE_DNA
    }
}

/**
 * Primary genres for User Musical DNA ("Предварительная настройка вкусов").
 * Expanded to 20 distinct macro-genres while keeping all canonical IDs.
 */
@Serializable
enum class TasteGenreOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val searchSeeds: List<String>,
    val matchKeywords: List<String>
) {
    POST_PUNK(
        id = "post_punk",
        title = "Пост-панк & Coldwave",
        subtitle = "Кино, Molchat Doma, Перемотка, Дурной Вкус, Joy Division, Doomer Wave",
        searchSeeds = listOf("post-punk coldwave", "molchat doma", "русский пост-панк", "new wave synth"),
        matchKeywords = listOf("post-punk", "post punk", "coldwave", "darkwave", "new wave", "doomer", "кино", "молчат дома", "перемотка", "буерак")
    ),
    SYNTHWAVE(
        id = "synthwave",
        title = "Synthwave & Retrowave",
        subtitle = "Kavinsky, The Midnight, Perturbator, Outrun, Darksynth, Cyberpunk",
        searchSeeds = listOf("synthwave retrowave", "outrun synth", "darksynth cyberpunk", "the midnight synthwave"),
        matchKeywords = listOf("synthwave", "retrowave", "outrun", "darksynth", "cyberpunk", "dreamwave", "synth", "kavinsky")
    ),
    PHONK(
        id = "phonk",
        title = "Phonk & Wave Bass",
        subtitle = "Kordhell, DVRST, Skeler, Drift Phonk, Brazilian Phonk, Hardwave",
        searchSeeds = listOf("drift phonk", "atmospheric wave phonk", "brazilian phonk", "hardwave skeler"),
        matchKeywords = listOf("phonk", "drift", "cowbell", "hardwave", "memphis", "montagem", "dvrst", "kordhell", "skeler")
    ),
    INDIE_ALT(
        id = "indie_alt",
        title = "Indie, Shoegaze & Dream Pop",
        subtitle = "Сироткин, Tame Impala, Arctic Monkeys, Shoegaze, Bedroom Pop",
        searchSeeds = listOf("indie rock dream pop", "shoegaze ethereal", "bedroom pop indie", "psychedelic indie"),
        matchKeywords = listOf("indie", "dream pop", "shoegaze", "bedroom pop", "alternative", "math rock", "сироткин", "tame impala")
    ),
    ROCK_METAL(
        id = "rock_metal",
        title = "Rock, Grunge & Alternative",
        subtitle = "Би-2, Сплин, Linkin Park, Grunge, Post-Grunge, Pop-Punk",
        searchSeeds = listOf("alternative rock", "classic rock hits", "grunge rock", "pop punk rock"),
        matchKeywords = listOf("rock", "grunge", "punk", "hard rock", "alternative rock", "guitar", "би-2", "сплин", "король и шут")
    ),
    METAL_HEAVY_CORE(
        id = "metal_core",
        title = "Metal, Nu-Metal & Metalcore",
        subtitle = "Bring Me The Horizon, Nu-Metal, Metalcore, Symphonic, Djent, Industrial",
        searchSeeds = listOf("metalcore heavy", "nu metal", "symphonic metal", "industrial metal"),
        matchKeywords = listOf("metal", "metalcore", "nu-metal", "nu metal", "heavy metal", "djent", "thrash", "symphonic metal", "industrial")
    ),
    ELECTRONIC_CLUB(
        id = "electronic_club",
        title = "Electronic, House & Techno",
        subtitle = "Daft Punk, Bonobo, RÜFÜS DU SOL, Deep House, Melodic & Hard Techno",
        searchSeeds = listOf("melodic techno house", "deep house electronic", "hard techno rave", "progressive trance"),
        matchKeywords = listOf("electronic", "house", "techno", "trance", "edm", "club", "deep house", "melodic techno", "rave", "daft punk")
    ),
    DNB_BREAKCORE(
        id = "dnb_breaks",
        title = "Drum & Bass, Jungle & Breakcore",
        subtitle = "Pendulum, Sub Focus, Liquid DnB, Neurofunk, Amen Breakcore, UK Garage",
        searchSeeds = listOf("liquid drum and bass", "neurofunk dnb", "breakcore jungle", "uk garage 2step"),
        matchKeywords = listOf("drum and bass", "dnb", "liquid", "neurofunk", "breakcore", "jungle", "uk garage", "dubstep", "pendulum")
    ),
    HIP_HOP_RAP(
        id = "hip_hop_rap",
        title = "Hip-Hop, Trap & Drill",
        subtitle = "Miyagi & Эндшпиль, Скриптонит, Boom Bap, Cloud Rap, Melodic Trap, Drill",
        searchSeeds = listOf("hip hop beats", "melodic trap", "90s boom bap", "cloud rap"),
        matchKeywords = listOf("hip-hop", "hip hop", "rap", "trap", "boom bap", "drill", "cloud rap", "rage", "grime", "miyagi", "скриптонит")
    ),
    HYPERPOP_WITCH(
        id = "hyperpop_witch",
        title = "Witch House, Hyperpop & Glitch",
        subtitle = "Witch House, Darkwave, Glitchcore, Hyperpop, Dariacore, Cyber-Goth",
        searchSeeds = listOf("witch house darkwave", "hyperpop glitchcore", "dark electronic witch house"),
        matchKeywords = listOf("witch house", "hyperpop", "glitchcore", "digicore", "darkwave", "glitch", "ic3peak", "crystal castles")
    ),
    LOFI_CHILL(
        id = "lofi_chill",
        title = "Lo-Fi, Chillhop & Vaporwave",
        subtitle = "Nujabes, Lo-Fi Study Beats, Chillhop, Downtempo, Vaporwave, Mallsoft",
        searchSeeds = listOf("lofi chillhop beats", "chillout downtempo", "vaporwave aesthetic", "study beats instrumental"),
        matchKeywords = listOf("lofi", "lo-fi", "chillhop", "chill", "downtempo", "vaporwave", "study", "nujabes")
    ),
    POP_DISCO(
        id = "pop_disco",
        title = "Pop, Synth-Pop & Eurodance",
        subtitle = "Dua Lipa, The Weeknd, Dark Pop, Nu-Disco, 90s/2000s Eurodance",
        searchSeeds = listOf("nu disco synth pop", "dark pop", "eurodance club", "electropop dance"),
        matchKeywords = listOf("pop", "disco", "nu-disco", "synth-pop", "electropop", "eurodance", "dance pop")
    ),
    RNB_CITY_POP(
        id = "rnb_citypop",
        title = "R&B, Neo-Soul & City Pop",
        subtitle = "Contemporary R&B, Neo-Soul, 80s Japanese City Pop, Funk Groove",
        searchSeeds = listOf("contemporary rnb neo soul", "japanese city pop 80s", "funk soul groove"),
        matchKeywords = listOf("r&b", "rnb", "neo-soul", "city pop", "funk", "soul", "groove")
    ),
    AMBIENT_SPACE(
        id = "ambient_space",
        title = "Ambient, Drone & Space",
        subtitle = "Tycho, Brian Eno, Космический эмбиент, Медитация, Глубокий фон",
        searchSeeds = listOf("space ambient", "atmospheric drone ambient", "meditation calm music"),
        matchKeywords = listOf("ambient", "space", "drone", "meditation", "new age", "atmospheric", "tycho", "brian eno")
    ),
    NEOCLASSICAL_OST(
        id = "neoclassical_ost",
        title = "Неоклассика & Саундтреки",
        subtitle = "Ludovico Einaudi, Hans Zimmer, Сольное пианино, Эпический оркестр, OST",
        searchSeeds = listOf("neoclassical piano", "cinematic soundtrack orchestral", "epic score ost"),
        matchKeywords = listOf("neoclassical", "classical", "piano", "soundtrack", "ost", "score", "orchestra", "einaudi", "zimmer")
    ),
    JAZZ_SOUL(
        id = "jazz_soul",
        title = "Jazz, Bossa Nova & Blues",
        subtitle = "Jamiroquai, Smooth Jazz, Acid Jazz, Bossa Nova, Blues Guitar, Lounge",
        searchSeeds = listOf("smooth jazz", "bossa nova lounge", "acid jazz funk", "blues guitar"),
        matchKeywords = listOf("jazz", "bossa nova", "blues", "swing", "acid jazz", "sax", "lounge", "jamiroquai")
    ),
    ANIME_KPOP_JPOP(
        id = "anime_asian",
        title = "Anime OST, J-Rock & K-Pop",
        subtitle = "Опенинги аниме, Japanese Rock, Vocaloid, K-Pop, Игровые саундтреки",
        searchSeeds = listOf("anime ost opening", "j-rock japanese", "k-pop dance", "touhou vocaloid"),
        matchKeywords = listOf("anime", "j-rock", "j-pop", "k-pop", "kpop", "vocaloid", "japanese", "korean")
    ),
    RUSSIAN_SCENE(
        id = "russian_scene",
        title = "Русская сцена (Рок, Поп, Инди, Рэп)",
        subtitle = "Любимые русскоязычные хиты, новая русская волна, рок и лирика",
        searchSeeds = listOf("русский рок", "русский инди", "русская музыка", "russian hip hop"),
        matchKeywords = listOf("русск", "russian", "кино", "земфира", "сплин", "би-2", "сироткин", "miyagi", "баста")
    ),
    ACOUSTIC_FOLK_BARD(
        id = "acoustic_folk",
        title = "Acoustic, Indie Folk & Country",
        subtitle = "Акустическая гитара, инди-фолк, нео-фолк, кантри и камерные баллады",
        searchSeeds = listOf("acoustic indie folk", "fingerstyle guitar acoustic", "neo folk ballad"),
        matchKeywords = listOf("acoustic", "folk", "country", "unplugged", "ballad", "singer-songwriter", "fingerstyle")
    ),
    REGGAE_AFRO_LATIN(
        id = "reggae_latin",
        title = "Reggae, Dub & Afro-Latin",
        subtitle = "Roots Reggae, Dub, Dancehall, Afrobeats, Reggaeton, Tropical Vibe",
        searchSeeds = listOf("reggae dub roots", "afrobeats chill", "latin groove"),
        matchKeywords = listOf("reggae", "dub", "dancehall", "afrobeats", "latin", "reggaeton", "ska")
    );

    val queryKeywords: List<String>
        get() = searchSeeds

    val primaryQuery: String
        get() = searchSeeds.firstOrNull() ?: title

    companion object {
        fun fromId(id: String): TasteGenreOption? = entries.firstOrNull { it.id == id }
    }
}

/**
 * 48 Pinpoint Subgenres & Micro-Styles ("Узкие поджанры и микро-стили для точечных рекомендаций").
 * Enables hyper-accurate, narrow musical targeting in "Моя волна" and Explore.
 */
@Serializable
enum class TasteSubgenreOption(
    val id: String,
    val parentGenreId: String,
    val title: String,
    val searchQuery: String,
    val matchKeywords: List<String>
) {
    // Пост-панк & Coldwave
    SUB_RU_DOOMER_POSTPUNK("sub_ru_postpunk", "post_punk", "Русский Дум-Пост-Панк", "russian post punk coldwave", listOf("post-punk", "coldwave", "doomer", "молчат дома", "перемотка", "буерак", "дурной вкус")),
    SUB_SOVIETWAVE("sub_sovietwave", "post_punk", "Sovietwave & Новая Волна 80-х", "sovietwave synth post-punk", listOf("sovietwave", "new wave", "кино", "альянс")),
    SUB_DARK_COLDWAVE("sub_coldwave", "post_punk", "80s Goth & Coldwave", "80s coldwave dark post-punk", listOf("coldwave", "goth", "joy division", "the cure")),

    // Synthwave & Retrowave
    SUB_OUTRUN_80S("sub_outrun", "synthwave", "Outrun 80s Highway", "outrun 80s synthwave", listOf("outrun", "retrowave", "kavinsky", "synthwave")),
    SUB_DARKSYNTH_CYBER("sub_darksynth", "synthwave", "Darksynth / Cyberpunk", "darksynth cyberpunk", listOf("darksynth", "cyberpunk", "perturbator", "carpenter brut")),
    SUB_DREAMWAVE_SAX("sub_dreamwave", "synthwave", "Dreamwave & Neon Sax", "dreamwave synthwave saxophone", listOf("dreamwave", "the midnight", "timecop1983", "chillwave")),
    SUB_SPACESYNTH("sub_spacesynth", "synthwave", "Spacesynth / Sci-Fi", "spacesynth cosmic electronic", listOf("spacesynth", "space synth", "sci-fi")),

    // Phonk & Wave Bass
    SUB_DRIFT_PHONK("sub_drift_phonk", "phonk", "Aggressive Drift Phonk", "aggressive drift phonk cowbell", listOf("drift phonk", "cowbell", "kordhell", "phonk")),
    SUB_BRAZILIAN_PHONK("sub_braz_phonk", "phonk", "Brazilian Phonk / Montagem", "brazilian phonk montagem", listOf("brazilian phonk", "montagem", "automotivo")),
    SUB_CHILL_WAVE_PHONK("sub_wave_phonk", "phonk", "Atmospheric / Chill Phonk", "atmospheric chill wave phonk", listOf("wave phonk", "chill phonk", "dvrst", "lxst cxntury")),
    SUB_HARDWAVE("sub_hardwave", "phonk", "Hardwave & Night Drive", "hardwave skeler wave bass", listOf("hardwave", "skeler", "øfdream", "wave")),

    // Indie, Shoegaze & Dream Pop
    SUB_SHOEGAZE("sub_shoegaze", "indie_alt", "Shoegaze & Wall of Sound", "shoegaze slowdive dream pop", listOf("shoegaze", "slowdive", "reverb", "my bloody valentine")),
    SUB_ETHEREAL_DREAMPOP("sub_dreampop", "indie_alt", "Ethereal Dream Pop", "ethereal dream pop indie", listOf("dream pop", "cigarettes after sex", "beach house")),
    SUB_BEDROOM_INDIE("sub_bedroom_pop", "indie_alt", "Bedroom Pop & Lo-Fi Indie", "bedroom pop lofi indie", listOf("bedroom pop", "indie pop", "mac demarco")),
    SUB_PSYCH_INDIE("sub_psych_indie", "indie_alt", "Psychedelic Indie & Synth", "psychedelic indie rock", listOf("psychedelic", "tame impala", "mgmt")),
    SUB_MATH_POSTROCK("sub_math_rock", "indie_alt", "Math Rock & Post-Rock", "math rock post rock instrumental", listOf("math rock", "post-rock", "midwest emo")),

    // Rock, Grunge & Alternative
    SUB_ALT_ROCK_2000S("sub_alt_2000s", "rock_metal", "Alt-Rock 2000-х", "2000s alternative rock", listOf("alternative rock", "linkin park", "muse", "three days grace")),
    SUB_SEATTLE_GRUNGE("sub_grunge", "rock_metal", "90s Seattle Grunge", "90s grunge rock", listOf("grunge", "nirvana", "alice in chains", "soundgarden")),
    SUB_POP_PUNK_EMO("sub_pop_punk", "rock_metal", "Pop-Punk & Emo Rock", "pop punk emo rock", listOf("pop-punk", "pop punk", "emo", "blink")),
    SUB_HORROR_FOLK_PUNK("sub_horror_punk", "rock_metal", "Панк-Рок & Хоррор-Панк", "король и шут панк рок", listOf("панк", "король и шут", "horror punk", "punk rock")),

    // Metal, Nu-Metal & Metalcore
    SUB_NU_METAL("sub_nu_metal", "metal_core", "Nu-Metal & Rapcore", "nu metal heavy", listOf("nu metal", "nu-metal", "korn", "deftones", "slipknot")),
    SUB_METALCORE_DJENT("sub_metalcore", "metal_core", "Modern Metalcore & Djent", "metalcore djent progressive", listOf("metalcore", "djent", "bring me the horizon", "architects")),
    SUB_SYMPHONIC_GOTH_METAL("sub_symph_metal", "metal_core", "Symphonic & Gothic Metal", "symphonic gothic metal", listOf("symphonic metal", "gothic metal", "nightwish", "power metal")),
    SUB_INDUSTRIAL_METAL("sub_ind_metal", "metal_core", "Industrial & Cyber Metal", "industrial metal heavy", listOf("industrial metal", "industrial", "rammstein")),

    // Electronic, House & Techno
    SUB_DEEP_MELODIC_HOUSE("sub_deep_house", "electronic_club", "Deep & Melodic House", "deep melodic house", listOf("deep house", "melodic house", "rufus du sol", "lane 8")),
    SUB_HARD_RAVE_TECHNO("sub_hard_techno", "electronic_club", "Hard & Industrial Techno", "hard industrial techno rave", listOf("hard techno", "industrial techno", "acid techno", "rave")),
    SUB_AFTERLIFE_TECHNO("sub_mel_techno", "electronic_club", "Afterlife Melodic Techno", "melodic techno progressive", listOf("melodic techno", "anyma", "tale of us", "progressive")),
    SUB_PSY_GOA_TRANCE("sub_psytrance", "electronic_club", "Psytrance & Uplifting Trance", "psytrance uplifting trance", listOf("psytrance", "trance", "goa", "astrix")),
    SUB_FRENCH_FILTER_HOUSE("sub_french_house", "electronic_club", "French Touch & Electro House", "french house daft punk justice", listOf("french house", "daft punk", "justice", "electro house")),

    // Drum & Bass, Jungle & Breakcore
    SUB_LIQUID_DNB("sub_liquid_dnb", "dnb_breaks", "Liquid Drum & Bass", "liquid drum and bass", listOf("liquid dnb", "liquid drum", "netsky", "hybrid minds")),
    SUB_NEUROFUNK_DARK("sub_neurofunk", "dnb_breaks", "Neurofunk & Jump-Up DnB", "neurofunk drum and bass", listOf("neurofunk", "noisia", "pendulum", "black sun empire")),
    SUB_AMEN_BREAKCORE("sub_breakcore", "dnb_breaks", "Atmospheric Breakcore & Jungle", "atmospheric breakcore jungle amen", listOf("breakcore", "jungle", "amen break", "sewerslvt")),
    SUB_UK_GARAGE_2STEP("sub_uk_garage", "dnb_breaks", "UK Garage / 2-Step / Bassline", "uk garage 2step electronic", listOf("uk garage", "2-step", "burial", "garage")),

    // Hip-Hop, Trap & Drill
    SUB_MELODIC_CLOUD_RAP("sub_cloud_rap", "hip_hop_rap", "Cloud Rap & Sad Trap", "cloud rap atmospheric", listOf("cloud rap", "bones", "sad trap", "atmospheric rap")),
    SUB_BOOM_BAP_90S("sub_boom_bap", "hip_hop_rap", "90s Boom Bap & Underground", "90s boom bap hip hop", listOf("boom bap", "old school", "90s hip hop", "underground")),
    SUB_RAGE_NEWJAZZ("sub_rage_trap", "hip_hop_rap", "Rage / Pluggnb / New Jazz", "rage synth trap beat", listOf("rage", "pluggnb", "new jazz", "yeat", "carti")),
    SUB_UK_NY_DRILL("sub_drill", "hip_hop_rap", "UK & NY Drill / Grime", "uk drill grime beat", listOf("drill", "uk drill", "grime")),

    // Witch House & Hyperpop
    SUB_WITCH_HOUSE("sub_witch_house", "hyperpop_witch", "Witch House & Occult Wave", "witch house darkwave", listOf("witch house", "ic3peak", "crim3s", "crystal castles", "sidewalks and skeletons")),
    SUB_HYPERPOP_GLITCH("sub_glitchcore", "hyperpop_witch", "Hyperpop & Glitchcore", "hyperpop glitchcore digicore", listOf("hyperpop", "glitchcore", "100 gecs", "digicore")),

    // Lo-Fi, Chillhop & Vaporwave
    SUB_LOFI_STUDY_RAIN("sub_lofi_rain", "lofi_chill", "Lo-Fi Study & Rain Beats", "lofi study rain beats", listOf("lofi", "lo-fi", "chillhop", "study", "nujabes")),
    SUB_VAPORWAVE_MALL("sub_vaporwave", "lofi_chill", "Vaporwave & Future Funk", "vaporwave future funk", listOf("vaporwave", "future funk", "mallsoft")),

    // Pop, R&B & City Pop
    SUB_DARK_ALT_POP("sub_dark_pop", "pop_disco", "Dark Pop & Alt-Pop", "dark pop alternative", listOf("dark pop", "alt-pop", "the weeknd", "lana del rey")),
    SUB_EURODANCE_2000S("sub_eurodance", "pop_disco", "90s/2000s Eurodance & Club", "eurodance 90s 2000s", listOf("eurodance", "90s dance", "2000s club")),
    SUB_JAPANESE_CITY_POP("sub_city_pop", "rnb_citypop", "80s Japanese City Pop", "japanese city pop 80s funk", listOf("city pop", "80s japanese", "funk")),

    // Ambient, Neoclassical & Jazz
    SUB_SPACE_DRONE("sub_space_drone", "ambient_space", "Deep Space & Dark Ambient", "deep space ambient drone", listOf("space ambient", "dark ambient", "drone", "brian eno")),
    SUB_SOLO_PIANO("sub_solo_piano", "neoclassical_ost", "Neoclassical Solo Piano", "neoclassical solo piano", listOf("neoclassical", "solo piano", "einaudi", "piano")),
    SUB_EPIC_GAME_OST("sub_game_ost", "neoclassical_ost", "Epic Movie & Video Game OST", "epic orchestral soundtrack ost", listOf("soundtrack", "game ost", "hans zimmer", "epic score")),
    SUB_ACID_BOSSA_JAZZ("sub_acid_jazz", "jazz_soul", "Acid Jazz & Bossa Nova", "acid jazz bossa nova groove", listOf("acid jazz", "bossa nova", "jamiroquai", "smooth jazz")),

    // Anime OST, J-Rock & K-Pop
    SUB_ANIME_OPENINGS("sub_anime_op", "anime_asian", "Anime Openings & OST", "anime opening ost full", listOf("anime", "opening", "ost", "japanese")),
    SUB_JROCK_VOCALOID("sub_jrock", "anime_asian", "J-Rock & Vocaloid", "j-rock vocaloid japanese rock", listOf("j-rock", "vocaloid", "ado", "yoasobi")),

    // Русская сцена
    SUB_RU_ROCK_LEGENDS("sub_ru_rock", "russian_scene", "Легенды Русского Рока", "русский рок кино би-2 сплин", listOf("русский рок", "кино", "би-2", "сплин", "земфира")),
    SUB_RU_NEW_INDIE("sub_ru_indie", "russian_scene", "Новая Русская Инди-Волна", "русский инди сироткин три дня дождя", listOf("сироткин", "три дня дождя", "русский инди", "перемотка")),
    SUB_RU_HIPHOP_VIBE("sub_ru_rap", "russian_scene", "Русский Хип-Хоп & Vibe", "miyagi скриптонит русский рэп", listOf("miyagi", "скриптонит", "баста", "русский рэп")),

    // Acoustic, Indie Folk & Country
    SUB_INDIE_FOLK_BALLADS("sub_indie_folk", "acoustic_folk", "Indie Folk & Acoustic Guitar", "indie folk acoustic guitar", listOf("indie folk", "acoustic", "fingerstyle", "folk")),
    SUB_DARK_NEOFOLK("sub_neofolk", "acoustic_folk", "Nordic & Dark Neofolk", "nordic neofolk dark acoustic", listOf("neofolk", "nordic", "dark folk", "country")),

    // Reggae, Dub & Afro-Latin
    SUB_ROOTS_DUB_REGGAE("sub_roots_dub", "reggae_latin", "Roots Reggae & Deep Dub", "roots reggae dub bass", listOf("reggae", "dub", "roots", "dancehall")),
    SUB_AFROBEATS_LATIN("sub_afrobeats", "reggae_latin", "Afrobeats & Latin Groove", "afrobeats latin groove chill", listOf("afrobeats", "latin", "reggaeton"));

    companion object {
        fun fromId(id: String): TasteSubgenreOption? = entries.firstOrNull { it.id == id }
        fun forGenre(genreId: String): List<TasteSubgenreOption> =
            entries.filter { it.parentGenreId == genreId }
    }
}

@Serializable
data class StarterArtistSeed(
    val name: String,
    val genreId: String,
    val subtitle: String,
    val isRussian: Boolean = false,
    val avatarUrl: String = ""
)

@Serializable
enum class TasteEraOption(
    val id: String,
    val title: String,
    val yearPrefixes: List<String>
) {
    ERA_2020S("2020s", "2020–2026 (Свежие релизы)", listOf("2020", "2021", "2022", "2023", "2024", "2025", "2026")),
    ERA_2010S("2010s", "2010–2019", listOf("2010", "2011", "2012", "2013", "2014", "2015", "2016", "2017", "2018", "2019")),
    ERA_2000S("2000s", "2000–2009", listOf("2000", "2001", "2002", "2003", "2004", "2005", "2006", "2007", "2008", "2009")),
    ERA_RETRO("retro", "80-е и 90-е (Классика)", listOf("197", "198", "199"));

    companion object {
        fun fromId(id: String): TasteEraOption? = entries.firstOrNull { it.id == id }
    }
}

@Serializable
enum class TasteDiscoveryBalance(
    val id: String,
    val title: String,
    val subtitle: String,
    val familiarWeightMultiplier: Double,
    val discoveryWeightMultiplier: Double
) {
    FAVORITE_FOCUSED(
        id = "favorite_focused",
        title = "Максимум моих вкусов (85% свои / 15% открытия)",
        subtitle = "Строгая ориентация на выбранных авторов, поджанры, лайки и плейлисты",
        familiarWeightMultiplier = 1.45,
        discoveryWeightMultiplier = 0.65
    ),
    BALANCED(
        id = "balanced",
        title = "Умный баланс ДНК (65% свои / 35% похожие)",
        subtitle = "Любимые исполнители и треки из коллекции + точное попадание в ваши поджанры",
        familiarWeightMultiplier = 1.15,
        discoveryWeightMultiplier = 1.05
    ),
    DISCOVERY_HUNTER(
        id = "discovery_hunter",
        title = "Радар открытий (40% свои / 60% новые имена)",
        subtitle = "Ищет малоизвестные полноценные треки строго внутри ваших любимых поджанров",
        familiarWeightMultiplier = 0.85,
        discoveryWeightMultiplier = 1.50
    );

    companion object {
        fun fromId(id: String?): TasteDiscoveryBalance =
            entries.firstOrNull { it.id == id } ?: BALANCED
    }
}

/**
 * Persistent User Musical DNA & Pre-Configuration Profile.
 */
@Serializable
data class UserTasteProfile(
    val isOnboardingCompleted: Boolean = false,
    val selectedGenreIds: Set<String> = emptySet(),
    val selectedSubgenreIds: Set<String> = emptySet(),
    val selectedArtists: Set<String> = emptySet(),
    val likedAlbums: Set<String> = emptySet(),
    val preferredLanguages: Set<WaveLanguage> = emptySet(),
    val preferredEras: Set<TasteEraOption> = emptySet(),
    val discoveryBalance: TasteDiscoveryBalance = TasteDiscoveryBalance.BALANCED,
    val preferFullLengthTracks: Boolean = true,
    val autoAdaptFromCollection: Boolean = true,
    val artistArtworks: Map<String, String> = emptyMap()
) {
    val selectedGenres: List<TasteGenreOption>
        get() = selectedGenreIds.mapNotNull { TasteGenreOption.fromId(it) }

    val selectedSubgenres: List<TasteSubgenreOption>
        get() = selectedSubgenreIds.mapNotNull { TasteSubgenreOption.fromId(it) }

    val hasAnyExplicitPreferences: Boolean
        get() = selectedGenreIds.isNotEmpty() ||
            selectedSubgenreIds.isNotEmpty() ||
            selectedArtists.isNotEmpty() ||
            likedAlbums.isNotEmpty() ||
            preferredLanguages.isNotEmpty() ||
            preferredEras.isNotEmpty()

    val hasCustomSetup: Boolean
        get() = isOnboardingCompleted || hasAnyExplicitPreferences
}

/**
 * Computed real-time summary of the user's Musical DNA (explicit preferences + collection signals).
 */
@Serializable
data class UserTasteDnaSummary(
    val topArtists: List<String> = emptyList(),
    val topGenres: List<TasteGenreOption> = emptyList(),
    val topSubgenres: List<TasteSubgenreOption> = emptyList(),
    val topAlbums: List<String> = emptyList(),
    val dominantLanguageLabel: String = "Мультиязычный",
    val personalizationPercent: Int = 65,
    val totalSignalsCount: Int = 0,
    val likedTracksCount: Int = 0,
    val playlistTracksCount: Int = 0,
    val likedAlbumsCount: Int = 0,
    val selectedArtistsCount: Int = 0
) {
    fun buildCompactStatusText(): String {
        val parts = mutableListOf<String>()
        if (topSubgenres.isNotEmpty()) {
            parts.add(topSubgenres.take(2).joinToString(", ") { it.title })
        } else if (topGenres.isNotEmpty()) {
            parts.add(topGenres.take(2).joinToString(", ") { it.title.substringBefore(" &") })
        }
        if (topArtists.isNotEmpty()) {
            parts.add(topArtists.take(2).joinToString(", "))
        }
        return if (parts.isEmpty()) {
            "Настройте ДНК вкусов для 100% точности"
        } else {
            parts.joinToString(" • ")
        }
    }
}

@Serializable
enum class WaveActivity(val title: String, val queryHint: String, val targetBpmRange: IntRange) {
    WAKING_UP("Просыпаюсь", "morning acoustic upbeat indie", 95..122),
    ON_THE_ROAD("В дороге", "driving synthwave phonk highway", 112..145),
    WORKING("Работаю", "focus lofi ambient instrumental", 70..105),
    WORKOUT("Тренируюсь", "workout bass phonk electronic power", 128..175),
    FALLING_ASLEEP("Засыпаю", "sleep ambient piano soft calm", 50..78)
}

@Serializable
enum class WaveCharacter(val title: String, val queryHint: String) {
    FAVORITE("Любимое", "best hits favorites"),
    UNFAMILIAR("Незнакомое", "underground indie discovery fresh"),
    POPULAR("Популярное", "top chart hits trending")
}

@Serializable
enum class WaveMood(val title: String, val queryHint: String) {
    ENERGETIC("Бодрое", "energetic upbeat dance"),
    CHEERFUL("Весёлое", "happy feel good summer"),
    CALM("Спокойное", "chill relax downtempo"),
    SAD("Грустное", "melancholic sad post-punk acoustic")
}

@Serializable
enum class WaveLanguage(val title: String, val queryHint: String) {
    RUSSIAN("Русский", "русская музыка"),
    FOREIGN("Иностранный", "international hits"),
    INSTRUMENTAL("Без слов", "instrumental no vocals")
}

@Serializable
data class WaveSettings(
    val activity: WaveActivity? = null,
    val character: WaveCharacter? = null,
    val mood: WaveMood? = null,
    val language: WaveLanguage? = null,
    val contextLabel: String = "",
    val customSeedGenreId: String? = null,
    val customSeedSubgenreId: String? = null,
    val customSeedArtist: String? = null,
    val autoSyncEqualizer: Boolean = true
) {
    val isCustomized: Boolean
        get() = activity != null ||
            character != null ||
            mood != null ||
            language != null ||
            contextLabel.isNotBlank() ||
            customSeedGenreId != null ||
            customSeedSubgenreId != null ||
            customSeedArtist != null

    val activeFilterCount: Int
        get() = listOfNotNull(
            activity,
            character,
            mood,
            language,
            customSeedGenreId,
            customSeedSubgenreId,
            customSeedArtist
        ).size

    fun summarySubtitle(): String {
        val parts = mutableListOf<String>()
        if (contextLabel.isNotBlank()) parts.add(contextLabel)
        activity?.let { parts.add(it.title) }
        character?.let { parts.add(it.title) }
        mood?.let { parts.add(it.title) }
        language?.let { parts.add(it.title) }
        customSeedSubgenreId?.let { id ->
            TasteSubgenreOption.fromId(id)?.let { parts.add(it.title) }
        } ?: customSeedGenreId?.let { id ->
            TasteGenreOption.fromId(id)?.let { parts.add(it.title) }
        }
        customSeedArtist?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        return if (parts.isEmpty()) "Поток подстраивается под ваш ДНК" else parts.joinToString(" • ")
    }
}

@Serializable
data class ArtistSuggestion(
    val name: String,
    val artworkUrl: String = "",
    val subtitle: String = "",
    val genreId: String? = null,
    val isRussian: Boolean = false
)

@Serializable
data class AlbumSummary(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String = "",
    val releaseYear: String = "",
    val tracks: List<Track> = emptyList(),
    val isLiked: Boolean = false
) {
    fun storageKey(): String = "${artist.trim()}|${title.trim()}"
}

/**
 * Dedicated Artist Profile data with strict canonical artist filtering and single-provider isolation.
 */
@Serializable
data class ArtistProfile(
    val name: String,
    val avatarUrl: String = "",
    val isLiked: Boolean = false,
    val isLoading: Boolean = false,
    val biography: String = "",
    val activeProvider: SourceProvider = SourceProvider.ITUNES,
    val availableProviders: List<SourceProvider> = emptyList(),
    val tracksByProvider: Map<SourceProvider, List<Track>> = emptyMap(),
    val latestRelease: Track? = null,
    val popularTracks: List<Track> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
    val similarArtists: List<String> = emptyList()
)

typealias ArtistProfileData = ArtistProfile

@Serializable
enum class SmartPlaylistPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val defaultQuery: String,
    val gradientStartHex: Long,
    val gradientEndHex: Long
) {
    PERSONAL_DNA_MIX(
        id = "personal_dna",
        title = "Мой ДНК-Микс",
        subtitle = "Персональная выжимка из ваших жанров, поджанров, лайков и авторов",
        badge = "100% ДЛЯ ВАС",
        defaultQuery = "post-punk synthwave indie electronic full track",
        gradientStartHex = 0xFF06B6D4,
        gradientEndHex = 0xFF3B82F6
    ),
    MY_TOP_HITS(
        id = "my_top_hits",
        title = "Любимые & Топ Авторы",
        subtitle = "Лучшие полноценные треки ваших любимых исполнителей и коллекции",
        badge = "ФАВОРИТЫ",
        defaultQuery = "top alternative electronic hits full audio",
        gradientStartHex = 0xFFEC4899,
        gradientEndHex = 0xFF8B5CF6
    ),
    NIGHT_FOCUS(
        id = "night_focus",
        title = "Ночной Фокус (Без слов)",
        subtitle = "Lo-Fi, Ambient, Neoclassical и инструментальный поток для работы",
        badge = "FOCUS HD",
        defaultQuery = "lofi chillhop ambient piano instrumental",
        gradientStartHex = 0xFF10B981,
        gradientEndHex = 0xFF047857
    ),
    WORKOUT_ENERGY(
        id = "workout_energy",
        title = "Драйв, Phonk & Неон",
        subtitle = "Drift Phonk, Darksynth, Drum & Bass и мощный бас для тренировки",
        badge = "BPM 130+",
        defaultQuery = "drift phonk darksynth drum and bass",
        gradientStartHex = 0xFFF59E0B,
        gradientEndHex = 0xFFEF4444
    ),
    DISCOVERY_RADAR(
        id = "discovery_radar",
        title = "Радар Открытий",
        subtitle = "Новые исполнители и скрытые хиты строго в рамках вашего ДНК",
        badge = "НОВЫЕ ИМЕНА",
        defaultQuery = "indie electronic coldwave synth discovery",
        gradientStartHex = 0xFF8B5CF6,
        gradientEndHex = 0xFF06B6D4
    );

    val description: String
        get() = subtitle
}

@Serializable
enum class SearchSortMode(val title: String) {
    RELEVANCE("По точности"),
    FULL_TRACKS_FIRST("Полные версии"),
    QUALITY_HIGH("Высокий битрейт"),
    DURATION_DESC("По длительности"),
    ALPHABETICAL("По алфавиту")
}

@Serializable
enum class CollectionSortMode(val title: String) {
    RECENT("Недавние"),
    TITLE_AZ("По названию"),
    ARTIST_AZ("По автору"),
    DURATION("По длительности"),
    SOURCE("По источнику")
}

@Serializable
enum class RadioCategory(val label: String, val tagQuery: String) {
    ALL("Топ мира", ""),
    SYNTHWAVE("Synthwave", "synthwave"),
    LOFI("Lo-Fi Chill", "lofi"),
    ROCK("Rock & Alt", "rock"),
    ELECTRONIC("Electronic & Club", "electronic"),
    JAZZ("Jazz & Lounge", "jazz"),
    CLASSICAL("Classical", "classical"),
    AMBIENT("Ambient & Sleep", "ambient")
}

@Serializable
data class LyricsLine(
    val timestampMs: Long,
    val text: String
)

@Serializable
data class SyncedLyrics(
    val trackId: String,
    val title: String,
    val artist: String,
    val isInstrumental: Boolean = false,
    val isSynced: Boolean = false,
    val lines: List<LyricsLine> = emptyList(),
    val plainLyrics: String = ""
)

@Serializable
data class ArtistBioInfo(
    val artistName: String,
    val biography: String,
    val bannerUrl: String = "",
    val country: String = "",
    val style: String = "",
    val formedYear: String = ""
)

@Serializable
data class ProviderHealthStatus(
    val provider: SourceProvider,
    val enabled: Boolean = true,
    val isOnline: Boolean = true,
    val latencyMs: Long = 0L,
    val activeMirror: String = "",
    val lastCheckedMs: Long = 0L,
    val tracksReturnedLastQuery: Int = 0,
    val errorMessage: String? = null
)

@Serializable
enum class EqualizerPreset(
    val displayName: String,
    val bandGainsDb: List<Float>, // 5 bands: 60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz
    val bassBoostPercent: Int,
    val virtualizerPercent: Int,
    val loudnessGainDb: Float
) {
    FLAT(
        displayName = "Flat Studio",
        bandGainsDb = listOf(0f, 0f, 0f, 0f, 0f),
        bassBoostPercent = 0,
        virtualizerPercent = 0,
        loudnessGainDb = 0f
    ),
    BASS_BOOST(
        displayName = "Bass Boost",
        bandGainsDb = listOf(6.5f, 4.5f, 0f, 1.0f, 2.0f),
        bassBoostPercent = 65,
        virtualizerPercent = 25,
        loudnessGainDb = 2.0f
    ),
    VOCAL(
        displayName = "Vocal Clarity",
        bandGainsDb = listOf(-2.0f, 1.5f, 5.0f, 4.0f, 1.5f),
        bassBoostPercent = 10,
        virtualizerPercent = 30,
        loudnessGainDb = 1.5f
    ),
    ACOUSTIC(
        displayName = "Warm Acoustic",
        bandGainsDb = listOf(3.0f, 2.5f, 1.0f, 3.5f, 4.0f),
        bassBoostPercent = 25,
        virtualizerPercent = 40,
        loudnessGainDb = 1.0f
    ),
    ELECTRO(
        displayName = "Cyber Electro",
        bandGainsDb = listOf(5.5f, 3.0f, -1.5f, 3.5f, 6.0f),
        bassBoostPercent = 55,
        virtualizerPercent = 60,
        loudnessGainDb = 2.5f
    ),
    SLANT_PURE_SIGNATURE(
        displayName = "Slant Pure Signature",
        bandGainsDb = listOf(4.2f, 2.0f, 0.8f, 3.8f, 5.2f),
        bassBoostPercent = 45,
        virtualizerPercent = 50,
        loudnessGainDb = 2.0f
    )
}

@Serializable
data class DspState(
    val enabled: Boolean = true,
    val preset: EqualizerPreset = EqualizerPreset.SLANT_PURE_SIGNATURE,
    val bandGainsDb: List<Float> = EqualizerPreset.SLANT_PURE_SIGNATURE.bandGainsDb,
    val bandFrequenciesHz: List<Int> = listOf(60, 230, 910, 3600, 14000),
    val bassBoostPercent: Int = 45,
    val virtualizerPercent: Int = 50,
    val loudnessGainDb: Float = 2.0f,
    val hardwareAttached: Boolean = false,
    val activeAudioSessionId: Int = 0
)

@Serializable
data class RemoteStreamingConfig(
    val preferredBitrateKbps: Int = 320,
    val pipedFailoverTimeoutMs: Long = 3500L,
    val maxConcurrentSources: Int = 7,
    val autoFallbackOnError: Boolean = true,
    val maxCacheSizeMb: Int = 512,
    val configSource: String = "Только полные треки (Без 30-сек превью)"
)

@Serializable
enum class SourceFilter(val title: String, val provider: SourceProvider?) {
    ALL("Все полные", null),
    LOCAL("На устройстве", SourceProvider.LOCAL),
    YOUTUBE("Full Stream / YT", SourceProvider.YOUTUBE_PIPED),
    JAMENDO_AUDIUS("Jamendo & Audius HD", SourceProvider.JAMENDO),
    DEEZER_ITUNES("Каталог (Полные версии)", SourceProvider.DEEZER),
    RADIO("Радио LIVE", SourceProvider.RADIO_BROWSER),
    ARCHIVE("Live & Архив", SourceProvider.INTERNET_ARCHIVE)
}
