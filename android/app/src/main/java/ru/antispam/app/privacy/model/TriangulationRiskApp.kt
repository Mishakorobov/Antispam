package ru.antispam.app.privacy.model

/**
 * Risk level of stealth triangulation capability without GPS.
 */
enum class TriangulationRiskLevel {
    /**
     * App possesses all vectors: Location + Wi-Fi BSSID scanning + Cellular Cell ID (+ optionally Bluetooth beacons).
     * Capable of pinpointing apartment-level location 24/7 even when GPS satellites are turned off.
     */
    CRITICAL,

    /**
     * App possesses 2 vectors: Location + Wi-Fi BSSID, or Location + Cell ID.
     */
    HIGH,

    /**
     * App possesses standard location permission without multi-vector triangulation.
     */
    MEDIUM,

    /**
     * Core system component or legitimate app with no invasive telemetry capabilities.
     */
    SAFE
}

/**
 * Detailed breakdown of triangulation vectors requested by an application.
 */
data class TriangulationCapabilities(
    val hasGpsLocation: Boolean,       // ACCESS_FINE_LOCATION
    val hasCoarseLocation: Boolean,    // ACCESS_COARSE_LOCATION
    val hasWifiBssidAccess: Boolean,   // ACCESS_WIFI_STATE / CHANGE_WIFI_STATE / NEARBY_WIFI_DEVICES
    val hasCellIdAccess: Boolean,      // READ_PHONE_STATE
    val hasBluetoothBeacon: Boolean    // BLUETOOTH_SCAN / BLUETOOTH_ADMIN
)

/**
 * App profile evaluated for stealth geo-triangulation threats.
 */
data class TriangulationRiskApp(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val riskLevel: TriangulationRiskLevel,
    val capabilities: TriangulationCapabilities,
    val activeVectorsCount: Int,
    val riskDescription: String,
    val warningBadges: List<String>,
    val isDeepSleeping: Boolean = false
)

/**
 * High-level audit summary of geo-triangulation scanning.
 */
data class TriangulationAuditSummary(
    val apps: List<TriangulationRiskApp>,
    val criticalCount: Int,
    val highCount: Int,
    val hasStealthTrackersInstalled: Boolean
)
