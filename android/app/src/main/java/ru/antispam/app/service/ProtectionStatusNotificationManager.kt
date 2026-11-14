package ru.antispam.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.antispam.app.MainActivity
import ru.antispam.app.R
import ru.antispam.app.data.local.AppDatabase

/**
 * ProtectionStatusNotificationManager:
 * Manages the persistent, ongoing notification badge in the Android notification shade ("плашка сверху в уведомлениях")
 * showing that AntiSpam Shield is actively protecting incoming calls and inspecting background surveillance.
 */
object ProtectionStatusNotificationManager {

    private const val CHANNEL_ID = "antispam_protection_status_channel"
    private const val NOTIFICATION_ID = 1001
    private const val PREFS_NAME = "antispam_status_prefs"
    private const val KEY_SHOW_STATUS_NOTIFICATION = "show_status_notification"
    const val EXTRA_NAV_TAB = "extra_nav_tab"

    fun isNotificationEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SHOW_STATUS_NOTIFICATION, true)
    }

    fun setNotificationEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SHOW_STATUS_NOTIFICATION, enabled).apply()
        if (enabled) {
            updateNotification(context)
        } else {
            cancelNotification(context)
        }
    }

    fun updateNotification(context: Context) {
        if (!isNotificationEnabled(context)) {
            cancelNotification(context)
            return
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        // 1. Create or verify Notification Channel (IMPORTANCE_LOW for silent persistent card)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Статус фоновой защиты",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Показывает постоянную плашку в шторке о том, что AntiSpam Shield включен и работает"
                enableVibration(false)
                setSound(null, null)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // 2. Query statistics asynchronously on IO thread and post notification
        CoroutineScope(Dispatchers.IO).launch {
            val db = try { AppDatabase.getInstance(context) } catch (e: Exception) { null }
            val blockedCount = try { db?.blockedCallRecordDao()?.getTotalBlockedCount() ?: 0 } catch (e: Exception) { 0 }
            val poolsCount = try { db?.blockedPoolDao()?.getCount() ?: 9 } catch (e: Exception) { 9 }

            // Intent to open Main App (Journal)
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_NAV_TAB, 0)
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                101,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Intent to open Privacy Tab
            val privacyIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_NAV_TAB, 2)
            }
            val privacyPendingIntent = PendingIntent.getActivity(
                context,
                102,
                privacyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = "AntiSpam Shield: Защита активна"
            val text = if (blockedCount > 0) {
                "Отражено спам-вызовов: $blockedCount • Пулы: $poolsCount (диапазоны)"
            } else {
                "Блокировка спама и защита от прослушки работают в фоне"
            }

            val bigText = buildString {
                append("AntiSpam Shield активен в фоновом режиме:\n")
                append("• Спам-база: $poolsCount диапазонов номеров под защитой\n")
                if (blockedCount > 0) {
                    append("• Отражено нежелательных звонков: $blockedCount\n")
                }
                append("• Инспекция микрофона и предотвращение прослушки включены\n")
                append("• Иммунитет белых списков официальных служб активен")
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_shield_status_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setSubText("Защита активна")
                .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
                .setOngoing(true)
                .setSilent(true)
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(openAppPendingIntent)
                .addAction(
                    R.drawable.ic_shield_status_notification,
                    "Приватность",
                    privacyPendingIntent
                )
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    fun cancelNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
