package ru.antispam.app.service

import android.content.ComponentName
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import ru.antispam.app.util.DeliveryModeManager

/**
 * DeliveryTileService:
 * Android Quick Settings Tile allowing 1-swipe and 1-tap toggling of
 * the "Waiting for Courier / Delivery" mode directly from the system notification shade.
 */
class DeliveryTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val currentlyActive = DeliveryModeManager.isDeliveryModeActive(this)
        val newActive = !currentlyActive

        DeliveryModeManager.setDeliveryMode(this, newActive)
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isActive = DeliveryModeManager.isDeliveryModeActive(this)

        tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Жду курьера"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val remaining = DeliveryModeManager.getRemainingMinutes(this)
            tile.subtitle = if (isActive) "Осталось ${remaining} мин" else "Выключен"
        }

        tile.updateTile()
    }

    companion object {
        fun requestTileUpdate(context: android.content.Context) {
            try {
                requestListeningState(context, ComponentName(context, DeliveryTileService::class.java))
            } catch (e: Exception) {
                // Safe ignore if QuickSettings is unavailable
            }
        }
    }
}
