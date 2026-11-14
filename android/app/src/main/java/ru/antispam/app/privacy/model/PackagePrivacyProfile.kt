package ru.antispam.app.privacy.model

/**
 * Risk classification for applications requesting QUERY_ALL_PACKAGES.
 */
enum class SuperAppRiskLevel {
    /**
     * Commercial superapp, social network, messenger, or aggressive tracker
     * using QUERY_ALL_PACKAGES to inventory installed apps, profile the user,
     * or track VPN/circumvention tools.
     */
    CRITICAL,

    /**
     * Third-party non-system app requesting QUERY_ALL_PACKAGES without obvious functional necessity.
     */
    HIGH,

    /**
     * Functional utility that legitimately needs package visibility (e.g. Launcher, App Store, File Manager).
     */
    MEDIUM,

    /**
     * Core AOSP or system package, or app with no invasive capabilities.
     */
    SAFE
}

/**
 * Category of application requesting package visibility.
 */
enum class SuperAppCategory {
    SURVEILLANCE_SUPERAPP,
    COMMERCIAL_TRACKER,
    APP_STORE_OR_LAUNCHER,
    SECURITY_TOOL,
    SYSTEM_COMPONENT,
    REGULAR_APP
}

/**
 * Privacy profile of an application with full package visibility access.
 */
data class PackagePrivacyProfile(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val hasQueryAllPackages: Boolean,
    val riskLevel: SuperAppRiskLevel,
    val category: SuperAppCategory,
    val riskDescription: String,
    val detectedCapabilities: List<String> = emptyList()
)

/**
 * High-level audit summary of SuperApp scanning.
 */
data class SuperAppAuditSummary(
    val appsWithQueryAll: List<PackagePrivacyProfile>,
    val criticalCount: Int,
    val highCount: Int,
    val installedVpnClients: List<String>,
    val isVpnInventoryExposed: Boolean
)
