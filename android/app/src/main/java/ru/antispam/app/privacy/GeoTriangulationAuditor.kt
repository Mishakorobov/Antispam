package ru.antispam.app.privacy

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import ru.antispam.app.privacy.model.TriangulationAuditSummary
import ru.antispam.app.privacy.model.TriangulationCapabilities
import ru.antispam.app.privacy.model.TriangulationRiskApp
import ru.antispam.app.privacy.model.TriangulationRiskLevel

/**
 * GeoTriangulationAuditor: Detects stealth physical location tracking without GPS.
 *
 * Threat Model:
 * Users commonly turn off GPS satellites, assuming location tracking is completely blocked.
 * However, advertising SDKs, commercial trackers, and superapps exploit multi-vector
 * triangulation techniques:
 * 1. Wi-Fi BSSID Scanning: (ACCESS_WIFI_STATE / NEARBY_WIFI_DEVICES) scans nearby router MACs.
 *    By matching BSSIDs against geo-databases, location is resolved to 3-5 meters (down to the apartment!).
 * 2. Cellular Tower ID: (READ_PHONE_STATE + LOCATION) queries TelephonyManager for Cell ID & LAC.
 *    Pinpoints location to within 100-300m without turning on satellite hardware.
 * 3. Bluetooth Beacons: (BLUETOOTH_SCAN) tracks indoor beacons in transport, malls, and metro.
 *
 * If an app holds Location + Wi-Fi BSSID + Cellular Cell ID, it possesses full stealth
 * triangulation power regardless of whether GPS is toggled on or off!
 */
object GeoTriangulationAuditor {

    private const val TAG = "GeoTriangulationAuditor"

    const val PERM_FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
    const val PERM_COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION"
    const val PERM_ACCESS_WIFI_STATE = "android.permission.ACCESS_WIFI_STATE"
    const val PERM_CHANGE_WIFI_STATE = "android.permission.CHANGE_WIFI_STATE"
    const val PERM_NEARBY_WIFI = "android.permission.NEARBY_WIFI_DEVICES"
    const val PERM_READ_PHONE_STATE = "android.permission.READ_PHONE_STATE"
    const val PERM_BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN"
    const val PERM_BLUETOOTH_ADMIN = "android.permission.BLUETOOTH_ADMIN"

    /**
     * Recognized dedicated navigation apps where multi-sensor position tracking is expected.
     */
    val KNOWN_LEGITIMATE_NAVIGATORS = setOf(
        "ru.yandex.yandexnavi",
        "ru.yandex.yandexbus",
        "ru.dublgis.dgismobile",
        "com.google.android.apps.maps",
        "ru.navitel",
        "cz.seznam.mapy"
    )

    /**
     * Known core system telephony / OS packages.
     */
    val KNOWN_CORE_SYSTEM = setOf(
        "android",
        "com.android.systemui",
        "com.google.android.gms",
        "com.android.phone",
        "com.android.server.telecom"
    )

    /**
     * Known commercial surveillance superapps and telemetry SDKs.
     */
    val KNOWN_SURVEILLANCE_SUPERAPPS = setOf(
        "ru.vk.max",
        "com.vkontakte.android",
        "ru.vk.store",
        "com.my.mail",
        "ru.yandex.searchplugin",
        "ru.mail.cloud"
    )

    /**
     * Analyzes requested permissions for triangulation capabilities.
     */
    fun extractCapabilities(requestedPermissions: Set<String>): TriangulationCapabilities {
        val hasGps = PERM_FINE_LOCATION in requestedPermissions
        val hasCoarse = PERM_COARSE_LOCATION in requestedPermissions
        val hasWifi = (PERM_ACCESS_WIFI_STATE in requestedPermissions) ||
                (PERM_CHANGE_WIFI_STATE in requestedPermissions) ||
                (PERM_NEARBY_WIFI in requestedPermissions)
        val hasCell = PERM_READ_PHONE_STATE in requestedPermissions
        val hasBle = (PERM_BLUETOOTH_SCAN in requestedPermissions) ||
                (PERM_BLUETOOTH_ADMIN in requestedPermissions)

        return TriangulationCapabilities(
            hasGpsLocation = hasGps,
            hasCoarseLocation = hasCoarse,
            hasWifiBssidAccess = hasWifi,
            hasCellIdAccess = hasCell,
            hasBluetoothBeacon = hasBle
        )
    }

    /**
     * Classifies stealth geo-triangulation risk for a given package and permission set.
     */
    fun classifyRisk(
        packageName: String,
        appName: String,
        requestedPermissions: Set<String>,
        isSystemApp: Boolean
    ): TriangulationRiskApp {
        val lowerPkg = packageName.lowercase()
        val caps = extractCapabilities(requestedPermissions)
        val hasAnyLocation = caps.hasGpsLocation || caps.hasCoarseLocation

        // Build warning badges
        val badges = mutableListOf<String>()
        if (caps.hasGpsLocation) badges.add("GPS координаты")
        else if (caps.hasCoarseLocation) badges.add("Приблизительное гео")

        if (caps.hasWifiBssidAccess) badges.add("Wi-Fi BSSID (до квартиры)")
        if (caps.hasCellIdAccess) badges.add("Cell ID вышек связи")
        if (caps.hasBluetoothBeacon) badges.add("BLE радиомаяки")

        var vectorCount = 0
        if (hasAnyLocation) vectorCount++
        if (caps.hasWifiBssidAccess) vectorCount++
        if (caps.hasCellIdAccess) vectorCount++
        if (caps.hasBluetoothBeacon) vectorCount++

        // 1. Without location permissions, Android blocks BSSID and Cell ID location resolution
        if (!hasAnyLocation) {
            return TriangulationRiskApp(
                packageName = packageName,
                appName = appName,
                isSystemApp = isSystemApp,
                riskLevel = TriangulationRiskLevel.SAFE,
                capabilities = caps,
                activeVectorsCount = vectorCount,
                riskDescription = "Нет разрешений геолокации. Триангуляция невозможна.",
                warningBadges = badges
            )
        }

        // 2. Core OS system components and trusted OEM packages
        val isOemOrSystem = isSystemApp ||
                KNOWN_CORE_SYSTEM.contains(lowerPkg) ||
                lowerPkg.startsWith("com.android.") ||
                lowerPkg.startsWith("com.google.android.") ||
                lowerPkg.startsWith("com.samsung.") ||
                lowerPkg.startsWith("com.sec.") ||
                lowerPkg == "com.microsoft.appmanager"

        if (isOemOrSystem && !KNOWN_SURVEILLANCE_SUPERAPPS.contains(lowerPkg)) {
            return TriangulationRiskApp(
                packageName = packageName,
                appName = appName,
                isSystemApp = isSystemApp,
                riskLevel = TriangulationRiskLevel.SAFE,
                capabilities = caps,
                activeVectorsCount = vectorCount,
                riskDescription = "Системный сервис Android OS / производителя.",
                warningBadges = badges
            )
        }

        // 3. Legitimate map and navigation apps
        if (KNOWN_LEGITIMATE_NAVIGATORS.contains(lowerPkg) || lowerPkg.contains("navigation") || lowerPkg.contains("navigator")) {
            return TriangulationRiskApp(
                packageName = packageName,
                appName = appName,
                isSystemApp = isSystemApp,
                riskLevel = TriangulationRiskLevel.MEDIUM,
                capabilities = caps,
                activeVectorsCount = vectorCount,
                riskDescription = "Официальный навигатор / карты. Доступ к датчикам позиционирования функционально необходим.",
                warningBadges = badges
            )
        }

        // 4. Multi-vector Triangulation: Location + Wi-Fi BSSID + Cell ID
        val hasWifiAndCell = caps.hasWifiBssidAccess && caps.hasCellIdAccess
        val isSurveillanceSuperapp = KNOWN_SURVEILLANCE_SUPERAPPS.contains(lowerPkg)

        if (hasWifiAndCell || (isSurveillanceSuperapp && (caps.hasWifiBssidAccess || caps.hasCellIdAccess))) {
            val desc = if (isSurveillanceSuperapp) {
                "🚨 КРИТИЧЕСКАЯ УГРОЗА: Коммерческий суперапп способен вычислять ваш точный домашний адрес (с точностью до квартиры) без включения GPS через Wi-Fi BSSID и сотовые вышки!"
            } else {
                "КРИТИЧЕСКАЯ УГРОЗА: Приложение способно вычислять ваше местоположение с точностью до квартиры без включения GPS через триангуляцию по BSSID роутеров и Cell ID вышек!"
            }

            return TriangulationRiskApp(
                packageName = packageName,
                appName = appName,
                isSystemApp = isSystemApp,
                riskLevel = TriangulationRiskLevel.CRITICAL,
                capabilities = caps,
                activeVectorsCount = vectorCount,
                riskDescription = desc,
                warningBadges = badges
            )
        }

        // 5. High Risk: Location + (Wi-Fi BSSID OR Cell ID)
        if (caps.hasWifiBssidAccess || caps.hasCellIdAccess) {
            val vectorName = if (caps.hasWifiBssidAccess) "сканирование соседних Wi-Fi роутеров (BSSID)" else "Cell ID сотовой вышки"
            return TriangulationRiskApp(
                packageName = packageName,
                appName = appName,
                isSystemApp = isSystemApp,
                riskLevel = TriangulationRiskLevel.HIGH,
                capabilities = caps,
                activeVectorsCount = vectorCount,
                riskDescription = "ВЫСОКИЙ РИСК: Скрытая триангуляция через $vectorName при отключенном GPS.",
                warningBadges = badges
            )
        }

        // 6. Generic location only
        return TriangulationRiskApp(
            packageName = packageName,
            appName = appName,
            isSystemApp = isSystemApp,
            riskLevel = if (isSystemApp) TriangulationRiskLevel.SAFE else TriangulationRiskLevel.MEDIUM,
            capabilities = caps,
            activeVectorsCount = vectorCount,
            riskDescription = "Стандартный запрос геолокации без скрытой BSSID/Cell ID триангуляции.",
            warningBadges = badges
        )
    }

    /**
     * Audits all installed applications on the device for hidden triangulation capabilities.
     */
    fun auditLocationAndWifiPermissions(context: Context): List<TriangulationRiskApp> {
        val pm = context.packageManager
        val myPackage = context.packageName
        val results = mutableListOf<TriangulationRiskApp>()

        // 1. Check if system-wide Location is enabled on the device
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
        val isLocationGloballyEnabled = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                locationManager?.isLocationEnabled ?: false
            } else {
                Settings.Secure.getInt(context.contentResolver, Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF) != Settings.Secure.LOCATION_MODE_OFF
            }
        } catch (_: Exception) {
            true
        }

        // If GPS / Location is completely switched off in Android system settings,
        // no apps can receive satellite fixes, BSSID scans, or Cell ID triangulation!
        if (!isLocationGloballyEnabled) {
            Log.d(TAG, "Location is globally disabled on device. Triangulation risks are inactive.")
            return emptyList()
        }

        try {
            val installedPackages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            for (pkg in installedPackages) {
                val pkgName = pkg.packageName
                if (pkgName == myPackage) continue

                // Phase 13.5: Audit ONLY actively GRANTED runtime permissions!
                val reqPerms = pkg.requestedPermissions
                val reqFlags = pkg.requestedPermissionsFlags
                val grantedPerms = mutableSetOf<String>()
                if (reqPerms != null && reqFlags != null) {
                    for (i in reqPerms.indices) {
                        if ((reqFlags[i] and android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                            grantedPerms.add(reqPerms[i])
                        }
                    }
                }

                val caps = extractCapabilities(grantedPerms)
                val hasAnyLocation = caps.hasGpsLocation || caps.hasCoarseLocation

                // Skip apps that don't hold active granted location permissions
                if (!hasAnyLocation) continue

                val appInfo = pkg.applicationInfo
                val isSystem = if (appInfo != null) {
                    (appInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
                } else false

                val appName = if (appInfo != null) {
                    try {
                        pm.getApplicationLabel(appInfo).toString()
                    } catch (_: Exception) {
                        pkgName
                    }
                } else pkgName

                val appRisk = classifyRisk(pkgName, appName, grantedPerms, isSystem)
                val isDeepSleeping = AppDormancyHelper.isAppInDeepSleep(context, pkgName)

                val finalRisk = if (isDeepSleeping) {
                    appRisk.copy(
                        riskLevel = TriangulationRiskLevel.SAFE,
                        riskDescription = "Приложение находится в глубоком сне (заморожено системой). Фоновый сбор геопозиции и Wi-Fi BSSID заблокирован.",
                        warningBadges = listOf("Глубокий сон"),
                        isDeepSleeping = true
                    )
                } else {
                    appRisk
                }

                // Only include apps with actionable risks or deep sleep info if previously risky
                if (finalRisk.riskLevel != TriangulationRiskLevel.SAFE || isDeepSleeping) {
                    results.add(finalRisk)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to audit location permissions: ${e.message}")
        }

        return results.sortedBy {
            when (it.riskLevel) {
                TriangulationRiskLevel.CRITICAL -> 0
                TriangulationRiskLevel.HIGH -> 1
                TriangulationRiskLevel.MEDIUM -> 2
                TriangulationRiskLevel.SAFE -> 3
            }
        }
    }

    /**
     * Performs complete triangulation audit summary.
     */
    fun auditSummary(context: Context): TriangulationAuditSummary {
        val apps = auditLocationAndWifiPermissions(context)
        val critical = apps.count { it.riskLevel == TriangulationRiskLevel.CRITICAL && !it.isDeepSleeping }
        val high = apps.count { it.riskLevel == TriangulationRiskLevel.HIGH && !it.isDeepSleeping }

        return TriangulationAuditSummary(
            apps = apps,
            criticalCount = critical,
            highCount = high,
            hasStealthTrackersInstalled = (critical + high) > 0
        )
    }

    /**
     * Opens system Location Settings.
     */
    fun openLocationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open location settings: ${e.message}")
        }
    }

    /**
     * Opens system Application Details settings to revoke permissions.
     */
    fun openAppDetailsSettings(context: Context, packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app details for $packageName: ${e.message}")
        }
    }
}
