package ru.antispam.app.postcall

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import ru.antispam.app.R

object PostCallNotificationHelper {

    private const val CHANNEL_ID = "post_call_channel"
    private const val CHANNEL_NAME = "Быстрый репорт звонков"
    const val NOTIFICATION_ID_BASE = 2000

    fun showPostCallNotification(context: Context, phoneNumber: Long, durationSeconds: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create high priority notification channel for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Интерактивные уведомления после коротких разговоров (< 30 сек)"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notificationId = (NOTIFICATION_ID_BASE + (phoneNumber % 1000)).toInt()

        // Action 1: Survey
        val surveyIntent = Intent(context, PostCallActionReceiver::class.java).apply {
            action = PostCallActionReceiver.ACTION_REPORT
            putExtra(PostCallActionReceiver.EXTRA_PHONE, phoneNumber)
            putExtra(PostCallActionReceiver.EXTRA_CATEGORY, "SURVEY")
            putExtra(PostCallActionReceiver.EXTRA_DURATION, durationSeconds)
            putExtra(PostCallActionReceiver.EXTRA_NOTIF_ID, notificationId)
        }
        val surveyPending = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 1,
            surveyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 2: Spam
        val spamIntent = Intent(context, PostCallActionReceiver::class.java).apply {
            action = PostCallActionReceiver.ACTION_REPORT
            putExtra(PostCallActionReceiver.EXTRA_PHONE, phoneNumber)
            putExtra(PostCallActionReceiver.EXTRA_CATEGORY, "SPAM")
            putExtra(PostCallActionReceiver.EXTRA_DURATION, durationSeconds)
            putExtra(PostCallActionReceiver.EXTRA_NOTIF_ID, notificationId)
        }
        val spamPending = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 2,
            spamIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 3: Whitelist
        val whitelistIntent = Intent(context, PostCallActionReceiver::class.java).apply {
            action = PostCallActionReceiver.ACTION_WHITELIST
            putExtra(PostCallActionReceiver.EXTRA_PHONE, phoneNumber)
            putExtra(PostCallActionReceiver.EXTRA_NOTIF_ID, notificationId)
        }
        val whitelistPending = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 3,
            whitelistIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedPhone = formatPhone(phoneNumber)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_call_mute)
            .setContentTitle("Незнакомый номер $formattedPhone")
            .setContentText("Разговор длился $durationSeconds сек. Это был опрос или спам?")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Разговор длился $durationSeconds сек с незнакомого номера. Помогите заблокировать спам-пул в 1 касание:")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setTimeoutAfter(60_000) // Auto-dismiss after 60 seconds to prevent notification clutter
            .addAction(0, "📊 Опрос / ВЦИОМ", surveyPending)
            .addAction(0, "📢 Спам", spamPending)
            .addAction(0, "⭐️ В доверенные", whitelistPending)

        notificationManager.notify(notificationId, builder.build())
    }

    private fun formatPhone(num: Long): String {
        val s = num.toString()
        if (s.length == 11 && s.startsWith("7")) {
            return "+7 (${s.substring(1, 4)}) ${s.substring(4, 7)}-${s.substring(7, 9)}-${s.substring(9)}"
        }
        return "+$num"
    }
}
