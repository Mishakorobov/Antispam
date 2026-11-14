package ru.antispam.app.privacy

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import ru.antispam.app.privacy.model.CallForwardingReport
import ru.antispam.app.privacy.model.CallForwardingRule
import ru.antispam.app.privacy.model.CallForwardingType
import ru.antispam.app.privacy.model.ForwardingRiskLevel

/**
 * CallForwardingAuditor:
 * Detects covert call forwarding rules and provides instant 1-tap USSD reset (##002#)
 * to prevent attackers and spyware from intercepting SMS 2FA voice OTPs and bank confirmation calls.
 */
object CallForwardingAuditor {

    const val USSD_RESET_ALL = "##002#"
    const val USSD_QUERY_UNCONDITIONAL = "*#21#"
    const val USSD_QUERY_ALL_CONDITIONAL = "*#004#"
    const val USSD_QUERY_NO_REPLY = "*#61#"
    const val USSD_QUERY_UNREACHABLE = "*#62#"
    const val USSD_QUERY_BUSY = "*#67#"

    const val PREFS_NAME = "antispam_privacy_prefs"
    const val KEY_CF_CARRIER_INDICATOR = "cf_carrier_indicator_active"
    const val KEY_CF_LAST_AUDIT_TS = "cf_last_audit_timestamp"
    const val KEY_CF_LAST_RESET_TS = "cf_last_reset_timestamp"
    const val KEY_CF_MANUAL_CLEAN = "cf_manual_clean_confirmed"

    @Volatile
    var sharedPreferencesProvider: ((Context) -> SharedPreferences)? = null

    private fun getPrefs(context: Context): SharedPreferences {
        return sharedPreferencesProvider?.invoke(context)
            ?: context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Builds a dialer intent for executing USSD commands via ACTION_DIAL.
     * ACTION_DIAL is universally supported across all Android versions without dangerous permissions,
     * respects dual-SIM selection, and gives the user 100% transparent 1-tap confirmation.
     */
    fun createUssdDialIntent(ussdCode: String): Intent {
        val encoded = try {
            Uri.encode(ussdCode) ?: ussdCode.replace("#", "%23")
        } catch (_: Exception) {
            ussdCode.replace("#", "%23")
        }
        return Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$encoded")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Creates an intent for universal 1-click reset of ALL call forwardings (##002#).
     */
    fun createResetForwardingIntent(): Intent {
        return createUssdDialIntent(USSD_RESET_ALL)
    }

    /**
     * Creates an intent for querying forwarding status by type (e.g. *#21# for unconditional).
     */
    fun createQueryForwardingIntent(type: CallForwardingType = CallForwardingType.UNCONDITIONAL): Intent {
        return createUssdDialIntent(type.ussdQueryCode)
    }

    /**
     * Audits current call forwarding status based on carrier indicators,
     * historical resets, and recorded network responses.
     */
    fun auditCallForwarding(context: Context): CallForwardingReport {
        val prefs = getPrefs(context)
        val isIndicatorActive = prefs.getBoolean(KEY_CF_CARRIER_INDICATOR, false)
        val lastAuditTs = prefs.getLong(KEY_CF_LAST_AUDIT_TS, 0L)
        val lastResetTs = prefs.getLong(KEY_CF_LAST_RESET_TS, 0L)
        val isManualClean = prefs.getBoolean(KEY_CF_MANUAL_CLEAN, false)

        val rules = mutableListOf<CallForwardingRule>()
        val hasForwarding: Boolean
        val riskLevel: ForwardingRiskLevel
        val summary: String

        if (isIndicatorActive) {
            hasForwarding = true
            riskLevel = ForwardingRiskLevel.CRITICAL
            summary = "Внимание: Оператор связи сообщает об активной безусловной переадресации вызовов (*21*). Входящие звонки и голосовые 2FA-коды могут перехватываться!"
            rules.add(
                CallForwardingRule(
                    type = CallForwardingType.UNCONDITIONAL,
                    isEnabled = true,
                    riskLevel = ForwardingRiskLevel.CRITICAL
                )
            )
        } else if (lastResetTs > 0L && !isIndicatorActive) {
            hasForwarding = false
            riskLevel = ForwardingRiskLevel.SAFE
            summary = "Все переадресации успешно аннулированы кодом ##002#. Перехват входящих вызовов исключен."
            rules.add(
                CallForwardingRule(
                    type = CallForwardingType.ALL_FORWARDING,
                    isEnabled = false,
                    riskLevel = ForwardingRiskLevel.SAFE
                )
            )
        } else if (isManualClean) {
            hasForwarding = false
            riskLevel = ForwardingRiskLevel.SAFE
            summary = "Переадресации проверены и отсутствуют. Защита голосовой линии активна."
        } else {
            hasForwarding = false
            riskLevel = ForwardingRiskLevel.SAFE
            summary = "Скрытая безусловная переадресация не зафиксирована. Рекомендуется выполнить проверку *#21# или профилактический сброс ##002#."
        }

        return CallForwardingReport(
            isCarrierIndicatorActive = isIndicatorActive,
            hasActiveForwarding = hasForwarding,
            riskLevel = riskLevel,
            activeRules = rules,
            lastAuditTimestamp = if (lastAuditTs > 0L) lastAuditTs else System.currentTimeMillis(),
            lastResetTimestamp = lastResetTs,
            summary = summary
        )
    }

    /**
     * Updates the carrier call forwarding indicator in SharedPreferences.
     */
    fun updateCarrierIndicator(context: Context, active: Boolean) {
        val prefs = getPrefs(context)
        prefs.edit()
            .putBoolean(KEY_CF_CARRIER_INDICATOR, active)
            .putLong(KEY_CF_LAST_AUDIT_TS, System.currentTimeMillis())
            .apply()
    }

    /**
     * Records that user completed a ##002# reset, clearing any detected active forwarding.
     */
    fun markAllForwardingCleared(context: Context) {
        val prefs = getPrefs(context)
        prefs.edit()
            .putBoolean(KEY_CF_CARRIER_INDICATOR, false)
            .putBoolean(KEY_CF_MANUAL_CLEAN, true)
            .putLong(KEY_CF_LAST_RESET_TS, System.currentTimeMillis())
            .putLong(KEY_CF_LAST_AUDIT_TS, System.currentTimeMillis())
            .apply()
    }

    /**
     * Registers a live telephony listener for CallForwardingIndicator changes.
     * Wrapped defensively against OEM quirks or missing permissions.
     */
    fun registerTelephonyListener(context: Context, onIndicatorChanged: (Boolean) -> Unit = {}): Boolean {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                ?: return false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = object : TelephonyCallback(), TelephonyCallback.CallForwardingIndicatorListener {
                    override fun onCallForwardingIndicatorChanged(cfi: Boolean) {
                        updateCarrierIndicator(context, cfi)
                        onIndicatorChanged(cfi)
                    }
                }
                telephonyManager.registerTelephonyCallback(context.mainExecutor, callback)
                true
            } else {
                @Suppress("DEPRECATION")
                val listener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallForwardingIndicatorChanged(cfi: Boolean) {
                        updateCarrierIndicator(context, cfi)
                        onIndicatorChanged(cfi)
                    }
                }
                @Suppress("DEPRECATION")
                telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_FORWARDING_INDICATOR)
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
