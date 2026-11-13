package ru.antispam.app.util

import android.content.Context
import ru.antispam.app.service.DeliveryNotificationManager
import ru.antispam.app.service.DeliveryTileService

/**
 * Manages the "Waiting for Courier / Delivery" mode.
 * Temporarily silences incoming calls from spam pools instead of hard-rejecting them
 * for 2 hours, ensuring delivery couriers (Yandex, Ozon, CDEK) and taxi drivers on
 * virtual PBX numbers can successfully reach the user.
 */
object DeliveryModeManager {

    private const val PREFS_NAME = "antispam_delivery_prefs"
    private const val KEY_PAUSE_UNTIL = "delivery_pause_until"

    fun isDeliveryModeActive(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val until = prefs.getLong(KEY_PAUSE_UNTIL, 0L)
        return System.currentTimeMillis() < until
    }

    fun getRemainingMinutes(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val until = prefs.getLong(KEY_PAUSE_UNTIL, 0L)
        val diff = until - System.currentTimeMillis()
        return if (diff > 0) (diff / (60 * 1000)).toInt() else 0
    }

    fun setDeliveryMode(context: Context, enabled: Boolean, durationHours: Int = 2) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (enabled) {
            val until = System.currentTimeMillis() + (durationHours * 3600 * 1000L)
            prefs.edit().putLong(KEY_PAUSE_UNTIL, until).apply()
            DeliveryNotificationManager.showDeliveryNotification(context)
        } else {
            prefs.edit().remove(KEY_PAUSE_UNTIL).apply()
            DeliveryNotificationManager.cancelDeliveryNotification(context)
        }
        DeliveryTileService.requestTileUpdate(context)
    }
}
