package com.mux.android.dm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/** Notification channels + the one-shot "done / failed" alerts. The live progress summary lives in DownloadService. */
object Notify {
    const val CHAN_PROGRESS = "dm_progress"
    const val CHAN_DONE = "dm_done"
    const val SUMMARY_ID = 9001

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHAN_PROGRESS) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHAN_PROGRESS, "Download progress", NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) }
            )
        }
        if (nm.getNotificationChannel(CHAN_DONE) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHAN_DONE, "Download finished", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    private fun openAppIntent(ctx: Context): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(ctx, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Called often while a segment is downloading; the live summary itself is refreshed by DownloadService's own ticker. */
    fun update(ctx: Context, item: DownloadItem) { }

    fun cancel(ctx: Context, item: DownloadItem) { }

    fun done(ctx: Context, item: DownloadItem) {
        ensureChannels(ctx)
        val n = NotificationCompat.Builder(ctx, CHAN_DONE)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download complete")
            .setContentText(item.fileName)
            .setContentIntent(openAppIntent(ctx))
            .setAutoCancel(true)
            .build()
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(item.id.hashCode(), n)
    }

    fun failed(ctx: Context, item: DownloadItem) {
        ensureChannels(ctx)
        val n = NotificationCompat.Builder(ctx, CHAN_DONE)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download failed")
            .setContentText(item.fileName + (item.error?.let { ": $it" } ?: ""))
            .setContentIntent(openAppIntent(ctx))
            .setAutoCancel(true)
            .build()
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(item.id.hashCode(), n)
    }

    fun summary(ctx: Context): android.app.Notification {
        ensureChannels(ctx)
        val active = Downloads.items.filter { it.status == Status.RUNNING || it.status == Status.CONNECTING }
        val totalSpeed = active.sumOf { it.speedBps }
        val title = if (active.isEmpty()) "Download Manager" else "Downloading ${active.size} file${if (active.size > 1) "s" else ""}"
        val text = if (active.isEmpty()) "Idle" else "${Fs.human(totalSpeed)}/s"
        return NotificationCompat.Builder(ctx, CHAN_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(active.isNotEmpty())
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent(ctx))
            .build()
    }
}
