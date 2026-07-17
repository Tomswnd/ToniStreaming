package com.toni.streaming.download

import android.app.Notification
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.NotificationUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import com.toni.streaming.R

/**
 * Foreground service that keeps episode downloads running while the app is
 * backgrounded, showing a persistent notification with overall progress.
 */
@OptIn(UnstableApi::class)
class EpisodeDownloadService : DownloadService(
    DownloadCenter.FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    DownloadCenter.NOTIFICATION_CHANNEL_ID,
    R.string.download_notification_channel_name,
    0
) {

    companion object {
        private const val JOB_ID = 2001
    }

    override fun getDownloadManager(): DownloadManager {
        return DownloadCenter.getDownloadManager(this)
    }

    override fun getScheduler(): Scheduler {
        return PlatformScheduler(this, JOB_ID)
    }

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {
        return DownloadCenter.getNotificationHelper(this).buildProgressNotification(
            this,
            android.R.drawable.stat_sys_download,
            null,
            getString(R.string.download_notification_message),
            downloads,
            notMetRequirements
        )
    }
}

/** Ensures the notification channel exists before the first download starts. */
@OptIn(UnstableApi::class)
fun ensureDownloadNotificationChannel(context: Context) {
    NotificationUtil.createNotificationChannel(
        context,
        DownloadCenter.NOTIFICATION_CHANNEL_ID,
        R.string.download_notification_channel_name,
        0,
        NotificationUtil.IMPORTANCE_LOW
    )
}
