package com.anindra.messages.sms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.anindra.messages.MainActivity
import com.anindra.messages.R

/**
 * The "fetching this message" notice.
 *
 * The platform owns the transfer — `downloadMultimediaMessage` hands the job to
 * the carrier and reports back through [MmsDownloadReceiver] — so there are no
 * byte counts to show. The notice is therefore indeterminate on purpose: it says
 * a download is running and disappears when it finishes, which is the difference
 * between a slow picture and a lost one.
 */
internal object MmsDownloadNotice {

    private const val CHANNEL_ID = "mms_download"
    private const val CHANNEL_NAME = "MMS downloads"
    private const val BASE_ID = 7_100

    /** One notice per announced message, so two arriving at once both show. */
    fun idFor(rowId: Long): Int = BASE_ID + (rowId % 1_000).toInt()

    private fun channel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shown while a picture or video message is downloading"
                setShowBadge(false)
            }
        )
    }

    fun show(context: Context, rowId: Long) {
        channel(context)
        val open = PendingIntent.getActivity(
            context,
            idFor(rowId),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setContentTitle(context.getString(R.string.mms_download_title))
            .setContentText(context.getString(R.string.mms_download_text))
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(idFor(rowId), notification)
        } catch (_: SecurityException) {
        }
    }

    fun finish(context: Context, rowId: Long) {
        NotificationManagerCompat.from(context).cancel(idFor(rowId))
    }

    /** The row id at the end of `content://mms/<id>`, or -1. */
    fun rowIdFrom(contentUri: String?): Long =
        contentUri?.substringAfterLast('/', "")?.toLongOrNull() ?: -1L
}
