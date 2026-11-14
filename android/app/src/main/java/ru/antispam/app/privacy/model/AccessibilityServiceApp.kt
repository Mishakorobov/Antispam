package ru.antispam.app.privacy.model

/**
 * Risk level of an application with active AccessibilityService access.
 */
enum class AccessibilityRiskLevel {
    /**
     * Non-system third-party app with full screen-reading, keystroke capture, and click emulation rights.
     * Can read chats in Telegram, Signal, WhatsApp, and private banking balances in real time!
     */
    CRITICAL,

    /**
     * Pre-installed vendor OEM accessibility tool (e.g. Samsung Universal Switch, Xiaomi Accessibility).
     */
    MEDIUM,

    /**
     * Official AOSP / Google Android Accessibility Suite (TalkBack, Switch Access, Voice Access).
     */
    SAFE
}

/**
 * Information about an app with active AccessibilityService access.
 */
data class AccessibilityServiceApp(
    val packageName: String,
    val appName: String,
    val serviceName: String,
    val isSystemApp: Boolean,
    val riskLevel: AccessibilityRiskLevel,
    val riskDescription: String
)
