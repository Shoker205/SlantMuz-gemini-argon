package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.player.PlaybackUiState
import com.example.player.PlayerController

enum class WidgetSizePreset(
    val title: String,
    val gridSpec: String,
    val subtitle: String
) {
    SMALL(
        title = "Компактная капсула",
        gridSpec = "2×1",
        subtitle = "Минималистичный пилл с Play/Pause и переключением трека"
    ),
    MEDIUM(
        title = "Пастельный Liquid Glass плеер",
        gridSpec = "4×2",
        subtitle = "Обложка-бейдж, прогресс-бар и быстрые кнопки Prev / Play / Next"
    ),
    LARGE(
        title = "Студийная дека + Караоке",
        gridSpec = "4×3",
        subtitle = "Синхронизированная строчка текста LRCLIB, избранное и очередь треков"
    )
}

object SlantMuzWidgetUpdater {
    const val ACTION_PLAY_PAUSE = "com.example.widget.ACTION_PLAY_PAUSE"
    const val ACTION_NEXT = "com.example.widget.ACTION_NEXT"
    const val ACTION_PREV = "com.example.widget.ACTION_PREV"
    const val ACTION_FAVORITE = "com.example.widget.ACTION_FAVORITE"

    fun handleWidgetAction(context: Context, action: String?) {
        if (action == null) return
        val controller = PlayerController.getInstance(context.applicationContext)
        when (action) {
            ACTION_PLAY_PAUSE -> controller.togglePlayPause()
            ACTION_NEXT -> controller.skipNext()
            ACTION_PREV -> controller.skipPrevious()
            ACTION_FAVORITE -> {
                val cur = controller.playbackState.value.currentTrack
                if (cur != null) {
                    controller.updateCurrentTrackFavoriteState(!cur.isFavorite)
                }
            }
        }
        updateAllWidgets(context.applicationContext, controller.playbackState.value)
    }

    fun updateAllWidgets(
        context: Context,
        state: PlaybackUiState,
        activeLyricLine: String? = null
    ) {
        runCatching {
            val manager = AppWidgetManager.getInstance(context)

            val smallIds = manager.getAppWidgetIds(
                ComponentName(context, SlantMuzSmallWidgetProvider::class.java)
            )
            if (smallIds.isNotEmpty()) {
                val views = buildSmallRemoteViews(context, state)
                manager.updateAppWidget(smallIds, views)
            }

            val mediumIds = manager.getAppWidgetIds(
                ComponentName(context, SlantMuzMediumWidgetProvider::class.java)
            )
            if (mediumIds.isNotEmpty()) {
                val views = buildMediumRemoteViews(context, state)
                manager.updateAppWidget(mediumIds, views)
            }

            val largeIds = manager.getAppWidgetIds(
                ComponentName(context, SlantMuzLargeWidgetProvider::class.java)
            )
            if (largeIds.isNotEmpty()) {
                val views = buildLargeRemoteViews(context, state, activeLyricLine)
                manager.updateAppWidget(largeIds, views)
            }
        }
    }

    fun requestPinWidget(context: Context, size: WidgetSizePreset): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = AppWidgetManager.getInstance(context)
                if (!manager.isRequestPinAppWidgetSupported) return@runCatching false
                val providerClass = when (size) {
                    WidgetSizePreset.SMALL -> SlantMuzSmallWidgetProvider::class.java
                    WidgetSizePreset.MEDIUM -> SlantMuzMediumWidgetProvider::class.java
                    WidgetSizePreset.LARGE -> SlantMuzLargeWidgetProvider::class.java
                }
                val provider = ComponentName(context, providerClass)
                manager.requestPinAppWidget(provider, null, null)
            } else {
                false
            }
        }.getOrDefault(false)
    }

    internal fun buildSmallRemoteViews(context: Context, state: PlaybackUiState): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_slantmuz_small)
        val track = state.currentTrack
        val badge = track?.primaryProvider?.shortBadge ?: "PASTEL"
        views.setTextViewText(R.id.widget_small_badge, "SLANTMUZ • $badge")
        views.setTextViewText(R.id.widget_small_title, track?.title ?: "Поток дня")
        views.setTextViewText(R.id.widget_small_artist, track?.artist ?: "SlantMuz Audio")
        views.setImageViewResource(
            R.id.widget_small_play_btn,
            if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )

        views.setOnClickPendingIntent(R.id.widget_root_small, launchAppPendingIntent(context))
        views.setOnClickPendingIntent(
            R.id.widget_small_play_btn,
            actionPendingIntent(context, ACTION_PLAY_PAUSE, 101)
        )
        views.setOnClickPendingIntent(
            R.id.widget_small_next_btn,
            actionPendingIntent(context, ACTION_NEXT, 102)
        )
        return views
    }

    internal fun buildMediumRemoteViews(context: Context, state: PlaybackUiState): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_slantmuz_medium)
        val track = state.currentTrack
        val badge = track?.primaryProvider?.displayName ?: "Liquid Glass 4×2"
        val dur = state.durationMs.coerceAtLeast((track?.durationSec ?: 1) * 1000L).coerceAtLeast(1L)
        val progress = ((state.positionMs * 100L) / dur).toInt().coerceIn(0, 100)

        views.setTextViewText(R.id.widget_medium_provider, "SLANTMUZ • ${badge.uppercase()}")
        views.setTextViewText(R.id.widget_medium_title, track?.title ?: "Выберите трек в SlantMuz")
        views.setTextViewText(
            R.id.widget_medium_artist,
            track?.let { "${it.artist} • ${it.formattedDuration}" } ?: "Мульти-источниковый аудиоплеер"
        )
        views.setProgressBar(R.id.widget_medium_progress, 100, progress, false)
        views.setImageViewResource(
            R.id.widget_medium_play_btn,
            if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )

        views.setOnClickPendingIntent(R.id.widget_root_medium, launchAppPendingIntent(context))
        views.setOnClickPendingIntent(
            R.id.widget_medium_prev_btn,
            actionPendingIntent(context, ACTION_PREV, 201)
        )
        views.setOnClickPendingIntent(
            R.id.widget_medium_play_btn,
            actionPendingIntent(context, ACTION_PLAY_PAUSE, 202)
        )
        views.setOnClickPendingIntent(
            R.id.widget_medium_next_btn,
            actionPendingIntent(context, ACTION_NEXT, 203)
        )
        return views
    }

    internal fun buildLargeRemoteViews(
        context: Context,
        state: PlaybackUiState,
        activeLyricLine: String?
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_slantmuz_large)
        val track = state.currentTrack
        val dur = state.durationMs.coerceAtLeast((track?.durationSec ?: 1) * 1000L).coerceAtLeast(1L)
        val progress = ((state.positionMs * 100L) / dur).toInt().coerceIn(0, 100)

        val providerText = track?.primaryProvider?.displayName ?: "Studio 4×3"
        views.setTextViewText(R.id.widget_large_provider, "SLANTMUZ • ${providerText.uppercase()}")
        views.setTextViewText(
            R.id.widget_large_fav_badge,
            if (track?.isFavorite == true) "♥ В избранном" else "♡ В избранное"
        )
        views.setTextViewText(R.id.widget_large_title, track?.title ?: "SlantMuz Pastel Studio")
        views.setTextViewText(
            R.id.widget_large_artist,
            track?.let { "${it.artist} • ${it.album.ifBlank { it.genre }}" }
                ?: "Выберите трек для синхронизации караоке и очереди"
        )
        views.setTextViewText(
            R.id.widget_large_lyric,
            activeLyricLine?.let { "♪ $it" }
                ?: "♪ Синхронный текст песни LRCLIB активен в плеере"
        )
        views.setProgressBar(R.id.widget_large_progress, 100, progress, false)
        views.setImageViewResource(
            R.id.widget_large_play_btn,
            if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )

        val upcoming = state.queue
            .drop((state.currentQueueIndex + 1).coerceAtLeast(0))
            .take(2)
            .joinToString(" • ") { "${it.title} (${it.artist})" }
            .ifBlank { "Jamendo 320kbps • Audius • Deezer HD" }
        views.setTextViewText(R.id.widget_large_queue, "Далее: $upcoming")

        views.setOnClickPendingIntent(R.id.widget_root_large, launchAppPendingIntent(context))
        views.setOnClickPendingIntent(
            R.id.widget_large_fav_badge,
            actionPendingIntent(context, ACTION_FAVORITE, 300)
        )
        views.setOnClickPendingIntent(
            R.id.widget_large_prev_btn,
            actionPendingIntent(context, ACTION_PREV, 301)
        )
        views.setOnClickPendingIntent(
            R.id.widget_large_play_btn,
            actionPendingIntent(context, ACTION_PLAY_PAUSE, 302)
        )
        views.setOnClickPendingIntent(
            R.id.widget_large_next_btn,
            actionPendingIntent(context, ACTION_NEXT, 303)
        )
        return views
    }

    private fun launchAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun actionPendingIntent(context: Context, action: String, reqCode: Int): PendingIntent {
        val intent = Intent(context, SlantMuzSmallWidgetProvider::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

class SlantMuzSmallWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val state = PlayerController.getInstance(context.applicationContext).playbackState.value
        val views = SlantMuzWidgetUpdater.buildSmallRemoteViews(context, state)
        appWidgetManager.updateAppWidget(appWidgetIds, views)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action?.startsWith("com.example.widget.ACTION_") == true) {
            SlantMuzWidgetUpdater.handleWidgetAction(context, intent.action)
        }
    }
}

class SlantMuzMediumWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val state = PlayerController.getInstance(context.applicationContext).playbackState.value
        val views = SlantMuzWidgetUpdater.buildMediumRemoteViews(context, state)
        appWidgetManager.updateAppWidget(appWidgetIds, views)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action?.startsWith("com.example.widget.ACTION_") == true) {
            SlantMuzWidgetUpdater.handleWidgetAction(context, intent.action)
        }
    }
}

class SlantMuzLargeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val state = PlayerController.getInstance(context.applicationContext).playbackState.value
        val views = SlantMuzWidgetUpdater.buildLargeRemoteViews(context, state, null)
        appWidgetManager.updateAppWidget(appWidgetIds, views)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action?.startsWith("com.example.widget.ACTION_") == true) {
            SlantMuzWidgetUpdater.handleWidgetAction(context, intent.action)
        }
    }
}
