package ru.antispam.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import ru.antispam.app.MainActivity
import ru.antispam.app.util.DeliveryModeManager

/**
 * DeliveryNotificationManager:
 * Manages the quiet Ongoing notification with live countdown and a quick
 * "Курьер приехал (Выключить)" action button during active delivery mode.
 */
object DeliveryNotificationManager {

    private const val CHANNEL_ID = "antispam_delivery_channel"
    private const val NOTIFICATION_ID = 2001
    const val ACTION_STOP_DELIVERY = "ru.antispam.app.action.STOP_DELIVERY_MODE"

    fun showDeliveryNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Режим ожидания доставки",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Уведомления об активном режиме ожидания курьера / такси"
                enableVibration(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val remainingMinutes = DeliveryModeManager.getRemainingMinutes(context)
        val hours = remainingMinutes / 60
        val mins = remainingMinutes % 60
        val timeStr = if (hours > 0) "${hours} ч ${mins} мин" else "${mins} мин"

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, DeliveryActionReceiver::class.java).apply {
            action = ACTION_STOP_DELIVERY
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("📦 Режим доставки активен")
            .setContentText("Осталось ~$timeStr. Звонки из пулов переводятся в тихий режим.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "Осталось ~$timeStr. Звонки из пулов виртуальных АТС переводятся в беззвучный режим, чтобы курьер или таксист смогли дозвониться."
            ))
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Курьер приехал (Выключить)",
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun cancelDeliveryNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        notificationManager.cancel(NOTIFICATION_ID)
    }
}

/**
 * BroadcastReceiver triggered by the "Курьер приехал (Выключить)" action button in the notification.
 */
class DeliveryActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == DeliveryNotificationManager.ACTION_STOP_DELIVERY) {
            DeliveryModeManager.setDeliveryMode(context, false)
        }
    }
}
