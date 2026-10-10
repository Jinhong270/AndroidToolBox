package com.jh270.toolbox.ssh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.jh270.toolbox.MainActivity

class TerminalKeepAliveService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        when (intent?.action) {
            ACTION_WAKE -> {
                acquireWakeLock()
                openBatterySettings()
            }
            ACTION_RELEASE -> releaseWakeLock()
        }
        val notification = buildNotification(wakeLockHeld())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, "终端会话", NotificationManager.IMPORTANCE_LOW)
        channel.setShowBadge(false)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(wakeHeld: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
        val wake = PendingIntent.getService(
            this,
            2,
            Intent(this, TerminalKeepAliveService::class.java).setAction(ACTION_WAKE),
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
        val release = PendingIntent.getService(
            this,
            3,
            Intent(this, TerminalKeepAliveService::class.java).setAction(ACTION_RELEASE),
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("SSH 终端")
            .setContentText(if (wakeHeld) "Wake Lock 已开启，会话保持运行" else "会话仍在后台运行")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (wakeHeld) {
            builder.addAction(0, "Release Wake Lock", release)
        } else {
            builder.addAction(0, "Wake Lock", wake)
        }
        return builder.build()
    }

    private fun acquireWakeLock() {
        val existing = wakeLock
        if (existing != null && existing.isHeld) return
        val power = getSystemService(PowerManager::class.java)
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "toolbox:terminal").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun openBatterySettings() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun wakeLockHeld(): Boolean = wakeLock?.isHeld == true

    companion object {
        private const val CHANNEL_ID = "toolbox_terminal"
        private const val NOTIFICATION_ID = 2701
        private const val ACTION_WAKE = "com.jh270.toolbox.action.WAKE_LOCK"
        private const val ACTION_RELEASE = "com.jh270.toolbox.action.RELEASE_WAKE_LOCK"
        private var wakeLock: PowerManager.WakeLock? = null

        fun releaseWakeLock() {
            try {
                wakeLock?.let { if (it.isHeld) it.release() }
            } catch (_: Exception) {
            }
            wakeLock = null
        }

        private fun immutableFlag(): Int {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        }
    }
}
