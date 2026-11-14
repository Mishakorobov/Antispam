package ru.antispam.app.security

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Dynamic feature flags and security rules delivered inside the Ed25519-signed
 * ASPB v2 header. Provides censorship-resistant, instant dynamic configuration
 * without requiring app store updates or re-moderation.
 */
@Serializable
data class DynamicConfig(
    @SerialName("new_spyware_packages")
    val newSpywarePackages: List<String> = emptyList(),

    @SerialName("dynamic_cdn_mirrors")
    val dynamicCdnMirrors: List<String> = emptyList(),

    @SerialName("antifraud_sms_keywords")
    val antifraudSmsKeywords: List<String> = emptyList(),

    @SerialName("foreign_spoofing_prefixes")
    val foreignSpoofingPrefixes: List<String> = emptyList()
)
