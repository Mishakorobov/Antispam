package ru.antispam.app.privacy.model

/**
 * Risk level of an application with active NotificationListener access.
 */
enum class ListenerRiskLevel {
    /**
     * Non-system third-party app with full notification interception access.
     * Can read all 2FA codes, bank OTPs, and private messenger messages in real time.
     */
    HIGH,

    /**
     * Verified wearable/companion app (e.g. Galaxy Wearable, Huawei Health, Mi Fitness).
     * Legitimately mirrors notifications to smart watches and fitness bands.
     */
    MEDIUM,

    /**
     * Official AOSP / Android OS core system service (e.g. SystemUI, Android System).
     */
    SAFE
}

/**
 * Information about an app with NotificationListenerService access.
 */
data class NotificationListenerApp(
    val packageName: String,
    val appName: String,
    val serviceName: String,
    val isSystemApp: Boolean,
    val riskLevel: ListenerRiskLevel,
    val riskDescription: String,
    val installedDate: Long = 0L,
    val isTrusted: Boolean = false,
    val isDeepSleeping: Boolean = false
)
