package ru.antispam.app.privacy

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import ru.antispam.app.privacy.model.CellularGeneration
import ru.antispam.app.privacy.model.CellularRiskLevel
import ru.antispam.app.privacy.model.CellularSecurityReport

/**
 * CellularSecurityAuditor:
 * Detects cellular downgrade attacks (IMSI-Catchers forcing 2G GSM downgrade)
 * and verifies encryption indicators to shield voice calls and SMS from over-the-air radio interception.
 */
object CellularSecurityAuditor {

    @Volatile
    var mockNetworkTypeProvider: (() -> Int)? = null

    @Volatile
    var mockNullCipherNotificationsProvider: (() -> Boolean?)? = null

    /**
     * Maps TelephonyManager network type constants to cellular generation classes.
     */
    fun mapNetworkTypeToGeneration(networkType: Int): CellularGeneration {
        return when (networkType) {
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_1xRTT,
            TelephonyManager.NETWORK_TYPE_IDEN,
            TelephonyManager.NETWORK_TYPE_GSM -> CellularGeneration.GEN_2G

            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_EVDO_B,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_EHRPD,
            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_TD_SCDMA -> CellularGeneration.GEN_3G

            TelephonyManager.NETWORK_TYPE_LTE,
            TelephonyManager.NETWORK_TYPE_IWLAN -> CellularGeneration.GEN_4G_LTE

            TelephonyManager.NETWORK_TYPE_NR -> CellularGeneration.GEN_5G

            else -> CellularGeneration.UNKNOWN
        }
    }

    /**
     * Returns human-readable label for a given telephony network type.
     */
    fun getNetworkTypeName(networkType: Int): String {
        return when (networkType) {
            TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS (2G)"
            TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE (2G)"
            TelephonyManager.NETWORK_TYPE_GSM -> "GSM (2G)"
            TelephonyManager.NETWORK_TYPE_CDMA -> "CDMA (2G)"
            TelephonyManager.NETWORK_TYPE_1xRTT -> "1xRTT (2G)"
            TelephonyManager.NETWORK_TYPE_IDEN -> "iDen (2G)"
            TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS (3G)"
            TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA (3G)"
            TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA (3G)"
            TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA (3G)"
            TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPA+ (3G)"
            TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "TD-SCDMA (3G)"
            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_EVDO_B -> "EV-DO (3G)"
            TelephonyManager.NETWORK_TYPE_LTE -> "LTE (4G)"
            TelephonyManager.NETWORK_TYPE_NR -> "5G NR"
            TelephonyManager.NETWORK_TYPE_IWLAN -> "VoWiFi / IWLAN"
            else -> "Неизвестный тип"
        }
    }

    /**
     * Checks if Android 14+ null cipher notification feature is supported and enabled.
     */
    fun checkNullCipherNotifications(context: Context): Pair<Boolean, Boolean?> {
        mockNullCipherNotificationsProvider?.let { provider ->
            val result = provider()
            return Pair(true, result)
        }

        if (Build.VERSION.SDK_INT < 34) {
            return Pair(false, null)
        }

        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                ?: return Pair(false, null)
            val method = tm.javaClass.getMethod("isNullCipherNotificationsEnabled")
            val isEnabled = method.invoke(tm) as? Boolean
            Pair(true, isEnabled)
        } catch (_: Throwable) {
            Pair(false, null)
        }
    }

    /**
     * Creates an Intent to launch the system Cellular Network Operator settings.
     */
    fun createCellularSettingsIntent(): Intent {
        return Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Safely opens network operator settings across all OEM variants with fallback.
     */
    fun openCellularSettings(context: Context): Boolean {
        val intents = listOf(
            Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS),
            Intent("android.settings.NETWORK_OPERATOR_SETTINGS"),
            Intent(Settings.ACTION_DATA_ROAMING_SETTINGS),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (_: Exception) {}
        }
        return false
    }

    /**
     * Audits current cellular connection security, detecting potential 2G downgrade attacks.
     */
    fun auditCellularSecurity(context: Context): CellularSecurityReport {
        val networkType = mockNetworkTypeProvider?.invoke() ?: getActiveNetworkType(context)
        val generation = mapNetworkTypeToGeneration(networkType)
        val networkTypeName = getNetworkTypeName(networkType)

        val (nullCipherSupported, nullCipherEnabled) = checkNullCipherNotifications(context)

        val is2G = generation == CellularGeneration.GEN_2G
        val riskLevel = when {
            is2G -> CellularRiskLevel.CRITICAL
            generation == CellularGeneration.GEN_3G -> CellularRiskLevel.WARNING
            else -> CellularRiskLevel.SAFE
        }

        val summary = when {
            is2G -> "Внимание: Подключение работает через незащищенную сеть 2G (GSM/EDGE)! Возможна атака с использованием ложной базовой станции (IMSI-Catcher) с перехватом звонков и СМС из радиоэфира."
            generation == CellularGeneration.GEN_3G -> "Соединение 3G: поддерживается базовая взаимная аутентификация, но стандарт устаревает. Рекомендуется отключить 2G для блокировки атак принудительного понижения."
            generation == CellularGeneration.GEN_4G_LTE || generation == CellularGeneration.GEN_5G -> "Соединение 4G/5G защищено современным шифрованием (AES / SNOW 3G). Для максимальной защиты отключите 2G в настройках SIM."
            else -> "Активное сотовое подключение для передачи данных не зафиксировано или используется Wi-Fi."
        }

        val recommendations = mutableListOf<String>()
        if (is2G) {
            recommendations.add("Немедленно перейдите в настройки сотовой связи и отключите пункт «Разрешить 2G» (Allow 2G).")
            recommendations.add("До возврата в зону надежного 4G/LTE используйте для связи защищенные мессенджеры со сквозным шифрованием.")
            recommendations.add("Не принимайте конфиденциальные СМС с кодами подтверждения в текущей сотовой ячейке.")
        } else {
            recommendations.add("Перейдите в настройки сотовой сети и отключите параметр «Разрешить 2G» (Allow 2G) для превентивной защиты.")
            if (nullCipherSupported && nullCipherEnabled == false) {
                recommendations.add("Включите системное уведомление об отсутствии шифрования (Null Cipher Alerts) в Android 14+.")
            }
        }

        return CellularSecurityReport(
            generation = generation,
            networkTypeName = networkTypeName,
            is2GActive = is2G,
            isNullCipherAlertSupported = nullCipherSupported,
            isNullCipherAlertEnabled = nullCipherEnabled,
            riskLevel = riskLevel,
            summary = summary,
            recommendations = recommendations,
            lastAuditTimestamp = System.currentTimeMillis()
        )
    }

    /**
     * Reads current active data network type from TelephonyManager.
     */
    private fun getActiveNetworkType(context: Context): Int {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                ?: return TelephonyManager.NETWORK_TYPE_UNKNOWN
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                tm.dataNetworkType
            } else {
                @Suppress("DEPRECATION")
                tm.networkType
            }
        } catch (_: SecurityException) {
            TelephonyManager.NETWORK_TYPE_UNKNOWN
        } catch (_: Exception) {
            TelephonyManager.NETWORK_TYPE_UNKNOWN
        }
    }

    /**
     * Step-by-step guidance for disabling 2G in Android settings.
     */
    fun getInstructionSteps(): List<String> {
        return listOf(
            "1. Нажмите кнопку «Настройки сотовой связи» ниже.",
            "2. Выберите вашу активную SIM-карту (например, SIM 1 или Т-Банк / МТС).",
            "3. Найдите переключатель «Разрешить 2G» (Allow 2G).",
            "4. Переведите тумблер в положение ВЫКЛ. Это полностью заблокирует возможность радиоперехватчиков сбросить ваш телефон в уязвимый стандарт 2G."
        )
    }
}
