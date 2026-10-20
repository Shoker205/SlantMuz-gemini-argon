package com.example.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.domain.SourceProvider
import com.example.domain.StreamCandidate
import com.example.domain.Track
import com.example.service.MediaPlaybackService
import com.example.widget.SlantMuzWidgetUpdater
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class RepeatModeState {
    OFF, ALL, ONE
}

data class PlaybackUiState(
    val currentTrack: Track? = null,
    val queue: List<Track> = emptyList(),
    val currentQueueIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatModeState = RepeatModeState.OFF,
    val activeStreamCandidate: StreamCandidate? = null,
    val failoverNotice: String? = null,
    val sleepTimerRemainingSec: Int = 0
)

/**
 * Central ExoPlayer & MediaSession coordinator with automatic multi-provider stream failover,
 * real-time 100ms progress updates, Home Screen AppWidget synchronization, Sleep Timer, and DSP binding.
 */
@OptIn(UnstableApi::class)
class PlayerController private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val audioEffectsManager = AudioEffectsManager()

    private var exoPlayer: ExoPlayer? = null
    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null

    private var streamResolver: (suspend (SourceProvider, String, Track) -> String)? = null
    private var onTrackPlayedCallback: (suspend (Track) -> Unit)? = null

    private var candidateIndexForCurrentTrack: Int = 0

    private val _playbackState = MutableStateFlow(PlaybackUiState())
    val playbackState: StateFlow<PlaybackUiState> = _playbackState.asStateFlow()

    fun attachResolvers(
        resolver: suspend (SourceProvider, String, Track) -> String,
        onTrackPlayed: suspend (Track) -> Unit
    ) {
        this.streamResolver = resolver
        this.onTrackPlayedCallback = onTrackPlayed
    }

    fun getOrCreateExoPlayer(): ExoPlayer {
        exoPlayer?.let { return it }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val cacheDataSourceFactory = ExoPlayerCacheProvider.buildCacheDataSourceFactory(context)
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(cacheDataSourceFactory)

        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
                SlantMuzWidgetUpdater.updateAllWidgets(context, _playbackState.value)
                if (isPlaying) startProgressPolling() else stopProgressPolling()
            }

            override fun onPlaybackStateChanged(state: Int) {
                val buffering = state == Player.STATE_BUFFERING
                val duration = player.duration.takeIf { it > 0 } ?: 0L
                _playbackState.value = _playbackState.value.copy(
                    isBuffering = buffering,
                    durationMs = if (duration > 0L) duration else _playbackState.value.durationMs
                )
                SlantMuzWidgetUpdater.updateAllWidgets(context, _playbackState.value)
                if (state == Player.STATE_ENDED) {
                    handleTrackEnded()
                }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                audioEffectsManager.onAudioSessionIdChanged(audioSessionId)
            }

            override fun onPlayerError(error: PlaybackException) {
                attemptStreamFailover(error.message ?: "Playback error")
            }
        })

        runCatching {
            if (player.audioSessionId > 0) {
                audioEffectsManager.onAudioSessionIdChanged(player.audioSessionId)
            }
        }

        exoPlayer = player
        return player
    }

    fun playTrack(track: Track, newQueue: List<Track> = listOf(track)) {
        val queueToUse = if (newQueue.isNotEmpty()) newQueue else listOf(track)
        val index = queueToUse.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        candidateIndexForCurrentTrack = 0

        _playbackState.value = _playbackState.value.copy(
            currentTrack = track,
            queue = queueToUse,
            currentQueueIndex = index,
            positionMs = 0L,
            durationMs = track.durationSec * 1000L,
            isBuffering = true,
            failoverNotice = null
        )

        val candidates = track.allStreamCandidates()
        val chosen = candidates.firstOrNull() ?: StreamCandidate(
            provider = track.primaryProvider,
            streamUrl = track.streamUrl
        )
        playCandidateStream(track, chosen)
        SlantMuzWidgetUpdater.updateAllWidgets(context, _playbackState.value)

        scope.launch(Dispatchers.IO) {
            onTrackPlayedCallback?.invoke(track)
        }
    }

    fun switchStreamCandidate(candidate: StreamCandidate) {
        val track = _playbackState.value.currentTrack ?: return
        val idx = track.allStreamCandidates().indexOfFirst { it.streamUrl == candidate.streamUrl }
        if (idx >= 0) {
            candidateIndexForCurrentTrack = idx
        }
        playCandidateStream(track, candidate)
    }

    private fun playCandidateStream(track: Track, candidate: StreamCandidate) {
        scope.launch {
            _playbackState.value = _playbackState.value.copy(
                activeStreamCandidate = candidate,
                isBuffering = true
            )

            val resolvedUrl = runCatching {
                if (candidate.streamUrl.startsWith("/")) {
                    Uri.fromFile(File(candidate.streamUrl)).toString()
                } else {
                    streamResolver?.invoke(candidate.provider, candidate.streamUrl, track)
                        ?: candidate.streamUrl
                }
            }.getOrDefault("")

            if (!isPlayableUriScheme(resolvedUrl)) {
                attemptStreamFailover("Протокол потока недоступен, поиск альтернативного зеркала…")
                return@launch
            }

            val player = getOrCreateExoPlayer()
            val albumDisplay = buildString {
                if (track.album.isNotBlank()) append(track.album)
                else append(candidate.provider.displayName)
                append(" • ${candidate.bitrateKbps}kbps")
            }

            val metadata = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(albumDisplay)
                .setSubtitle("${candidate.provider.shortBadge} • ${track.artist}")
                .setArtworkUri(
                    if (track.artworkUrl.isNotBlank()) Uri.parse(track.artworkUrl) else null
                )
                .build()

            val mediaItem = MediaItem.Builder()
                .setMediaId(track.id)
                .setUri(resolvedUrl)
                .setMediaMetadata(metadata)
                .build()

            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()

            ensureForegroundServiceStarted()
            SlantMuzWidgetUpdater.updateAllWidgets(context, _playbackState.value)
        }
    }

    private fun isPlayableUriScheme(url: String): Boolean {
        val clean = url.trim()
        return clean.startsWith("https://") ||
            clean.startsWith("http://") ||
            clean.startsWith("content://") ||
            clean.startsWith("file://") ||
            clean.startsWith("android.resource://")
    }

    private fun attemptStreamFailover(reason: String) {
        val track = _playbackState.value.currentTrack ?: return
        val candidates = track.allStreamCandidates()
        val nextIndex = candidateIndexForCurrentTrack + 1
        if (nextIndex < candidates.size) {
            candidateIndexForCurrentTrack = nextIndex
            val fallback = candidates[nextIndex]
            _playbackState.value = _playbackState.value.copy(
                failoverNotice = "Авто-переключение потока → ${fallback.provider.shortBadge} (${fallback.instanceHost})"
            )
            playCandidateStream(track, fallback)
        } else {
            _playbackState.value = _playbackState.value.copy(
                isBuffering = false,
                isPlaying = false,
                failoverNotice = "Поток недоступен ($reason)"
            )
        }
    }

    fun togglePlayPause() {
        val player = getOrCreateExoPlayer()
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                val track = _playbackState.value.currentTrack
                if (track != null) {
                    playTrack(track, _playbackState.value.queue)
                    return
                }
            }
            player.play()
        }
        SlantMuzWidgetUpdater.updateAllWidgets(context, _playbackState.value)
    }

    fun seekTo(positionMs: Long) {
        val player = getOrCreateExoPlayer()
        val safePos = positionMs.coerceAtLeast(0L)
        player.seekTo(safePos)
        _playbackState.value = _playbackState.value.copy(positionMs = safePos)
        SlantMuzWidgetUpdater.updateAllWidgets(context, _playbackState.value)
    }

    fun skipNext() {
        val state = _playbackState.value
        val queue = state.queue
        if (queue.isEmpty()) return

        val nextIndex = when {
            state.shuffleEnabled && queue.size > 1 -> {
                val indices = queue.indices.filter { it != state.currentQueueIndex }
                indices.random()
            }
            state.currentQueueIndex + 1 < queue.size -> state.currentQueueIndex + 1
            else -> 0
        }
        playTrack(queue[nextIndex], queue)
    }

    fun skipPrevious() {
        val player = getOrCreateExoPlayer()
        if (player.currentPosition > 4_000L) {
            seekTo(0L)
            return
        }
        val state = _playbackState.value
        val queue = state.queue
        if (queue.isEmpty()) return
        val prevIndex = if (state.currentQueueIndex - 1 >= 0) {
            state.currentQueueIndex - 1
        } else {
            queue.lastIndex
        }
        playTrack(queue[prevIndex], queue)
    }

    fun toggleShuffle() {
        val newShuffle = !_playbackState.value.shuffleEnabled
        _playbackState.value = _playbackState.value.copy(shuffleEnabled = newShuffle)
    }

    fun cycleRepeatMode() {
        val next = when (_playbackState.value.repeatMode) {
            RepeatModeState.OFF -> RepeatModeState.ALL
            RepeatModeState.ALL -> RepeatModeState.ONE
            RepeatModeState.ONE -> RepeatModeState.OFF
        }
        _playbackState.value = _playbackState.value.copy(repeatMode = next)
    }

    fun updateCurrentTrackFavoriteState(isFav: Boolean) {
        val cur = _playbackState.value.currentTrack ?: return
        val updated = cur.copy(isFavorite = isFav)
        val updatedQueue = _playbackState.value.queue.map {
            if (it.id == cur.id) updated else it
        }
        _playbackState.value = _playbackState.value.copy(
            currentTrack = updated,
            queue = updatedQueue
        )
        SlantMuzWidgetUpdater.updateAllWidgets(context, _playbackState.value)
    }

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _playbackState.value = _playbackState.value.copy(sleepTimerRemainingSec = 0)
            return
        }
        var remaining = minutes * 60
        _playbackState.value = _playbackState.value.copy(sleepTimerRemainingSec = remaining)
        sleepTimerJob = scope.launch {
            while (remaining > 0 && isActive) {
                delay(1000L)
                remaining--
                _playbackState.value = _playbackState.value.copy(sleepTimerRemainingSec = remaining)
            }
            if (remaining <= 0) {
                exoPlayer?.pause()
                _playbackState.value = _playbackState.value.copy(
                    sleepTimerRemainingSec = 0,
                    failoverNotice = "Таймер сна: воспроизведение остановлено"
                )
            }
        }
    }

    private fun handleTrackEnded() {
        val state = _playbackState.value
        when (state.repeatMode) {
            RepeatModeState.ONE -> {
                seekTo(0L)
                exoPlayer?.play()
            }
            RepeatModeState.ALL -> skipNext()
            RepeatModeState.OFF -> {
                if (state.currentQueueIndex + 1 < state.queue.size || state.shuffleEnabled) {
                    skipNext()
                } else {
                    _playbackState.value = state.copy(isPlaying = false, positionMs = 0L)
                }
            }
        }
    }

    private fun startProgressPolling() {
        progressJob?.cancel()
        var tickCount = 0
        progressJob = scope.launch {
            while (isActive) {
                val player = exoPlayer
                if (player != null && player.isPlaying) {
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    val dur = player.duration.takeIf { it > 0L }
                        ?: _playbackState.value.durationMs
                    _playbackState.value = _playbackState.value.copy(
                        positionMs = pos,
                        durationMs = dur
                    )
                    tickCount++
                    if (tickCount % 20 == 0) {
                        SlantMuzWidgetUpdater.updateAllWidgets(context, _playbackState.value)
                    }
                }
                delay(100L)
            }
        }
    }

    private fun stopProgressPolling() {
        progressJob?.cancel()
    }

    private fun ensureForegroundServiceStarted() {
        runCatching {
            val intent = Intent(context, MediaPlaybackService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: PlayerController? = null

        fun getInstance(context: Context): PlayerController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PlayerController(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
