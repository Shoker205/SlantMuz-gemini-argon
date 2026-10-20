package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.MainActivity
import com.example.R
import com.example.player.PlayerController
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Enhanced Media3 MediaSessionService providing rich system media notification controls
 * with custom Favorite (Like) button in the notification shade & lock screen.
 * Immediately satisfies Android's foreground service contract to prevent
 * ForegroundServiceDidNotStartInTimeException.
 */
@OptIn(UnstableApi::class)
class MediaPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        promoteToForegroundImmediately()

        val controller = PlayerController.getInstance(applicationContext)
        val player = controller.getOrCreateExoPlayer()

        val sessionIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionIntent,
            pendingIntentFlags
        )

        val favoriteButton = buildFavoriteCommandButton(
            isFavorite = controller.playbackState.value.currentTrack?.isFavorite == true
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(SlantMediaSessionCallback(controller))
            .setCustomLayout(ImmutableList.of(favoriteButton))
            .build()

        val notificationProvider = DefaultMediaNotificationProvider.Builder(applicationContext)
            .setChannelId(CHANNEL_ID)
            .setChannelName(R.string.notification_channel_name)
            .setNotificationId(NOTIFICATION_ID)
            .build()
        notificationProvider.setSmallIcon(R.drawable.ic_launcher_foreground)
        setMediaNotificationProvider(notificationProvider)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureNotificationChannel()
        promoteToForegroundImmediately()
        return super.onStartCommand(intent, flags, startId)
    }

    private fun promoteToForegroundImmediately() {
        runCatching {
            val notification = buildInitialForegroundNotification()
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            }
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
        }
    }

    private fun buildInitialForegroundNotification(): Notification {
        val controller = PlayerController.getInstance(applicationContext)
        val currentTrack = controller.playbackState.value.currentTrack
        val sessionIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(currentTrack?.title ?: getString(R.string.app_name))
            .setContentText(currentTrack?.artist ?: "Аудиоплеер активен")
            .setContentIntent(pendingIntent)
            .setOngoing(controller.playbackState.value.isPlaying)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private inner class SlantMediaSessionCallback(
        private val controller: PlayerController
    ) : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_TOGGLE_FAVORITE) {
                val cur = controller.playbackState.value.currentTrack
                if (cur != null) {
                    val newFav = !cur.isFavorite
                    controller.updateCurrentTrackFavoriteState(newFav)
                    session.setCustomLayout(
                        ImmutableList.of(buildFavoriteCommandButton(newFav))
                    )
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controllerInfo, customCommand, args)
        }
    }

    private fun buildFavoriteCommandButton(isFavorite: Boolean): CommandButton {
        return CommandButton.Builder()
            .setDisplayName(if (isFavorite) "В избранном" else "В избранное")
            .setSessionCommand(SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY))
            .setIconResId(
                if (isFavorite) android.R.drawable.btn_star_big_on
                else android.R.drawable.btn_star_big_off
            )
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.notification_channel_desc)
                    setShowBadge(true)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "slantmuz_playback_channel"
        const val NOTIFICATION_ID = DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID
        const val ACTION_TOGGLE_FAVORITE = "com.example.service.ACTION_TOGGLE_FAVORITE"
    }
}
