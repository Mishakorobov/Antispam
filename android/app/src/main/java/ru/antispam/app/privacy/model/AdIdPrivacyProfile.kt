package ru.antispam.app.privacy.model

/**
 * Ecosystem platform providing advertising tracking identifiers.
 */
enum class AdIdPlatformType(val displayName: String) {
    GOOGLE_PLAY_SERVICES("Google Play Services (GAID)"),
    HUAWEI_HMS("Huawei Mobile Services (OAID)"),
    AOSP_GENERIC("Системный Android (AOSP)")
}

/**
 * Privacy profile describing cross-app advertising tracking status.
 */
data class AdIdPrivacyProfile(
    val platformType: AdIdPlatformType,
    val trackingPlatformName: String,
    val threatSummary: String,
    val recommendedAction: String,
    val resetSteps: List<String>
)
