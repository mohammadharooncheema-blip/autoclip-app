package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.model.ProcessingUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps clipping alive in the background.
 * It does no rendering itself: [ProcessingEngine] does the work on an
 * application scope, and this service only holds a foreground
 * notification so Android does not kill the process while the user is
 * in another app, and so the user can see progress and tap back in.
 */
class ClipProcessingService : Service() {
    companion object {
        const val ACTION_START = "com.example.action.START_CLIPPING"
        const val ACTION_CANCEL = "com.example.action.CANCEL_CLIPPING"
        const val ACTION_OPEN = "com.example.action.OPEN_APP"
        private const val CHANNEL_ID = "autoclip_clipping"
        private const val NOTIFICATION_ID = 1001
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                ProcessingEngine.cancel()
                stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val notification = buildNotification("Creating clips...", "AutoClip is rendering in the background", 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                scope.launch {
                    ProcessingEngine.state.collect { state ->
                        when (state) {
                            is ProcessingUiState.InProgress -> {
                                val progress = (state.progress * 100).toInt()
                                val text = if (state.totalClips > 0) {
                                    "${state.statusMessage} (${state.currentClipIndex}/${state.totalClips})"
                                } else state.statusMessage
                                updateNotification("Creating clips... $progress%", text, progress)
                            }
                            is ProcessingUiState.Completed -> {
                                updateNotification(
                                    "Clips ready",
                                    "${state.clips.size} clips created, ${state.savedCount} saved to Gallery. Tap to open AutoClip.",
                                    100
                                )
                                // Keep the notification, stop the service.
                                stopForeground(false)
                                stopSelf()
                            }
                            is ProcessingUiState.Error -> {
                                updateNotification("Clipping failed", state.message, 0)
                                stopForeground(false)
                                stopSelf()
                            }
                            is ProcessingUiState.Idle -> {
                                // Engine was cancelled from the UI.
                                stopForeground(true)
                                stopSelf()
                            }
                        }
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AutoClip Clipping",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows clipping progress while AutoClip works in the background"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun openIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun cancelIntent(): PendingIntent {
        val intent = Intent(this, ClipProcessingService::class.java).apply {
            action = ACTION_CANCEL
        }
        return PendingIntent.getService(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildNotification(title: String, text: String, progress: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, progress == 0)
            .addAction(android.R.drawable.ic_delete, "Cancel", cancelIntent())
            .build()
    }

    private fun updateNotification(title: String, text: String, progress: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(title, text, progress))
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
