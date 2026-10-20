package com.example.domain

import kotlinx.serialization.Serializable

@Serializable
enum class SourceProvider(
    val displayName: String,
    val shortBadge: String,
    val defaultBitrateKbps: Int,
    val description: String
) {
    LOCAL(
        displayName = "Локальные файлы",
        shortBadge = "LOCAL",
        defaultBitrateKbps = 1411,
        description = "MediaStore аудио на устройстве (MP3/FLAC/WAV)"
    ),
    YOUTUBE_PIPED(
        displayName = "YouTube Music (Piped/Invidious)",
        shortBadge = "YTM",
        defaultBitrateKbps = 256,
        description = "Пул зеркал Piped/Invidious с авто-фейловером по пингу"
    ),
    JAMENDO(
        displayName = "Jamendo CC HD",
        shortBadge = "JAM",
        defaultBitrateKbps = 320,
        description = "Открытый каталог Creative Commons MP3 320kbps"
    ),
    AUDIUS(
        displayName = "Audius Web3 Node",
        shortBadge = "AUD",
        defaultBitrateKbps = 320,
        description = "Децентрализованный стриминг электронной и инди-музыки"
    ),
    DEEZER(
        displayName = "Deezer Public API",
        shortBadge = "DZR",
        defaultBitrateKbps = 320,
        description = "HD-метаданные, обложки 1000x1000 и студийные превью"
    ),
    ITUNES(
        displayName = "Apple iTunes API",
        shortBadge = "ITN",
        defaultBitrateKbps = 256,
        description = "Глобальный каталог метаданных, жанров и AAC-стримов"
    ),
    RADIO_BROWSER(
        displayName = "Radio Browser Live",
        shortBadge = "RADIO",
        defaultBitrateKbps = 192,
        description = "Прямые потоки мировых онлайн-радиостанций"
    ),
    INTERNET_ARCHIVE(
        displayName = "Internet Archive",
        shortBadge = "ARCH",
        defaultBitrateKbps = 256,
        description = "Раритетные записи, живые концерты и открытые альбомы"
    )
}

@Serializable
enum class WaveActivity(val title: String, val searchQueryKeyword: String) {
    WAKING_UP("Просыпаюсь", "Morning Upbeat Chill Indie"),
    ON_THE_ROAD("В дороге", "Synthwave Drive Electronic"),
    WORKING("Работаю", "Lo-Fi Focus Ambient Instrumental"),
    WORKOUT("Тренируюсь", "Phonk Electro Bass Workout"),
    FALLING_ASLEEP("Засыпаю", "Ambient Calm Sleep Piano")
}

@Serializable
enum class WaveCharacter(val title: String, val queryHint: String) {
    FAVORITE("Любимое", "Best Hits"),
    UNFAMILIAR("Незнакомое", "Underground Indie Fresh"),
    POPULAR("Популярное", "Top Chart Trending")
}

@Serializable
enum class WaveMood(val title: String, val queryHint: String) {
    ENERGETIC("Бодрое", "Energetic Dance Upbeat"),
    CHEERFUL("Весёлое", "Happy Pop Funk"),
    CALM("Спокойное", "Chill Acoustic Soft"),
    SAD("Грустное", "Melancholic Slowed Sad")
}

@Serializable
enum class WaveLanguage(val title: String, val queryHint: String) {
    RUSSIAN("Русский", "Russian Post-Punk Hip-Hop"),
    FOREIGN("Иностранный", "Global Pop Rock Electronic"),
    INSTRUMENTAL("Без слов", "Instrumental Chillhop Synth")
}

@Serializable
data class WaveSettings(
    val activity: WaveActivity? = null,
    val character: WaveCharacter? = null,
    val mood: WaveMood? = null,
    val language: WaveLanguage? = null,
    val autoSyncEqualizer: Boolean = true,
    val contextLabel: String = "Моя волна"
) {
    val isCustomized: Boolean
        get() = activity != null || character != null || mood != null || language != null

    val activeFilterCount: Int
        get() = listOfNotNull(activity, character, mood, language).size

    fun summarySubtitle(): String {
        val parts = listOfNotNull(
            activity?.title,
            character?.title,
            mood?.title,
            language?.title
        )
        return if (parts.isEmpty()) "Персональный поток SlantWave • Авто-подбор" else parts.joinToString(" • ")
    }

    fun buildSearchQuery(): String {
        val parts = listOfNotNull(
            activity?.searchQueryKeyword,
            character?.queryHint,
            mood?.queryHint,
            language?.queryHint
        )
        return if (parts.isEmpty()) "Electronic Indie Synth" else parts.joinToString(" ")
    }
}

@Serializable
enum class SearchSortMode(val title: String) {
    RELEVANCE("Релевантность"),
    FULL_TRACKS_FIRST("Полные треки"),
    QUALITY_HIGH("Битрейт HD"),
    DURATION_DESC("По длительности"),
    ALPHABETICAL("По алфавиту")
}

@Serializable
enum class CollectionSortMode(val title: String) {
    RECENT("Недавние"),
    TITLE_AZ("По названию (А–Я)"),
    ARTIST_AZ("По артисту"),
    DURATION("По длительности"),
    SOURCE("По источнику")
}

@Serializable
enum class SmartPlaylistPreset(
    val title: String,
    val description: String,
    val defaultQuery: String
) {
    MY_TOP_HITS(
        title = "Мои Топ-Хиты",
        description = "Любимые треки и лидеры истории прослушиваний",
        defaultQuery = "Top Hits Electronic Indie"
    ),
    NIGHT_FOCUS(
        title = "Ночной Фокус & Чилл",
        description = "Инструментальный Lo-Fi, эмбиент и спокойные треки без слов",
        defaultQuery = "Lofi Chillhop Ambient Instrumental"
    ),
    WORKOUT_ENERGY(
        title = "Драйв & Тренировка",
        description = "Бодрый электро-драйв, синтвейв, фонк и рок",
        defaultQuery = "Synthwave Phonk Electro Workout"
    ),
    DISCOVERY_RADAR(
        title = "Радар Открытий",
        description = "Свежие треки артистов, которых ещё нет в вашей истории",
        defaultQuery = "Underground Indie Fresh Electronic"
    )
}

@Serializable
data class StreamCandidate(
    val provider: SourceProvider,
    val streamUrl: String,
    val bitrateKbps: Int = provider.defaultBitrateKbps,
    val isPreview: Boolean = false,
    val instanceHost: String = provider.shortBadge
)

@Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val artworkUrl: String = "",
    val durationSec: Int = 0,
    val primaryProvider: SourceProvider = SourceProvider.ITUNES,
    val streamUrl: String = "",
    val fallbackStreams: List<StreamCandidate> = emptyList(),
    val isLiveStream: Boolean = false,
    val isPreviewOnly: Boolean = false,
    val genre: String = "Music",
    val releaseYear: String = "",
    val localUri: String? = null,
    val isFavorite: Boolean = false,
    val isCachedOffline: Boolean = false,
    val offlineFilePath: String? = null
) {
    val formattedDuration: String
        get() {
            if (isLiveStream) return "LIVE"
            if (durationSec <= 0) return "--:--"
            val mins = durationSec / 60
            val secs = durationSec % 60
            return "%d:%02d".format(mins, secs)
        }

    fun allStreamCandidates(): List<StreamCandidate> {
        val list = mutableListOf<StreamCandidate>()
        if (!offlineFilePath.isNullOrBlank()) {
            list.add(
                StreamCandidate(
                    provider = SourceProvider.LOCAL,
                    streamUrl = offlineFilePath,
                    bitrateKbps = 320,
                    isPreview = false,
                    instanceHost = "OFFLINE_CACHE"
                )
            )
        }
        if (streamUrl.isNotBlank()) {
            list.add(
                StreamCandidate(
                    provider = primaryProvider,
                    streamUrl = streamUrl,
                    bitrateKbps = primaryProvider.defaultBitrateKbps,
                    isPreview = isPreviewOnly,
                    instanceHost = primaryProvider.shortBadge
                )
            )
        }
        fallbackStreams.forEach { candidate ->
            if (candidate.streamUrl.isNotBlank() && list.none { it.streamUrl == candidate.streamUrl }) {
                list.add(candidate)
            }
        }
        return list
    }
}

@Serializable
data class AlbumSummary(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String,
    val releaseYear: String = "",
    val tracks: List<Track> = emptyList()
)

@Serializable
data class ArtistProfile(
    val name: String,
    val avatarUrl: String,
    val isLiked: Boolean = false,
    val isLoading: Boolean = false,
    val biography: String = "",
    val activeProvider: SourceProvider = SourceProvider.ITUNES,
    val availableProviders: List<SourceProvider> = emptyList(),
    val tracksByProvider: Map<SourceProvider, List<Track>> = emptyMap(),
    val latestRelease: Track? = null,
    val popularTracks: List<Track> = emptyList(),
    val albums: List<AlbumSummary> = emptyList()
)

@Serializable
enum class LibrarySubCategory(val id: String, val title: String, val subtitle: String) {
    PLAYLISTS("playlists", "Плейлисты", "Ваши подборки и умные авто-плейлисты"),
    OFFLINE("offline", "Скачанные треки", "Сохранённые в оффлайн-кэш композиции"),
    LOCAL_FILES("local_files", "Файлы на устройстве", "Локальная аудиотека смартфона (MediaStore)"),
    ALBUMS("albums", "Альбомы и релизы", "Альбомы и синглы из вашей коллекции"),
    ARTISTS("artists", "Исполнители", "Артисты из любимых треков и истории"),
    HISTORY("history", "История прослушиваний", "Хронология недавно воспроизведённых треков");

    companion object {
        fun fromId(id: String?): LibrarySubCategory =
            entries.firstOrNull { it.id == id } ?: PLAYLISTS
    }
}

@Serializable
enum class SettingsSubCategory(val id: String, val title: String, val subtitle: String) {
    AUDIO_DSP("audio_dsp", "Звук и DSP Эквалайзер", "5-полосный эквалайзер, Bass Boost, 3D Virtualizer и Loudness"),
    STREAMING_QUALITY("streaming_quality", "Качество потока и сеть", "Выбор битрейта, фейловер зеркал и сетевой буфер"),
    PROVIDERS_HUB("providers_hub", "Источники музыки (8 нод)", "Управление провайдерами, пинг зеркал и статус API"),
    STORAGE_CACHE("storage_cache", "Память и оффлайн-кэш", "Лимит кэша ExoPlayer, Room Offline и очистка места"),
    APPEARANCE_THEME("appearance_theme", "Оформление и темы", "Тёмное/светлое стекло, пастельные градиенты и Material You"),
    HOME_WIDGETS("home_widgets", "Виджеты рабочего стола", "Интерактивные виджеты 2×1, 4×2 и 4×3 для лаунчера"),
    ABOUT_APP("about_app", "О приложении SlantMuz", "Сведения о версии, аудиоядре Media3 и архитектуре");

    companion object {
        fun fromId(id: String?): SettingsSubCategory =
            entries.firstOrNull { it.id == id } ?: HOME_WIDGETS
    }
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
    val configSource: String = "Активный профиль сети"
)

@Serializable
enum class SourceFilter(val title: String, val provider: SourceProvider?) {
    ALL("Все", null),
    LOCAL("На устройстве", SourceProvider.LOCAL),
    YOUTUBE("YouTube", SourceProvider.YOUTUBE_PIPED),
    JAMENDO_AUDIUS("Full HD (Jamendo/Audius)", SourceProvider.JAMENDO),
    DEEZER_ITUNES("Deezer & iTunes", SourceProvider.DEEZER),
    RADIO("Радио LIVE", SourceProvider.RADIO_BROWSER),
    ARCHIVE("Live & Архив", SourceProvider.INTERNET_ARCHIVE)
}
