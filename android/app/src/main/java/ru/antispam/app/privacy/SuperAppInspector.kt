package ru.antispam.app.privacy

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import ru.antispam.app.privacy.model.PackagePrivacyProfile
import ru.antispam.app.privacy.model.SuperAppAuditSummary
import ru.antispam.app.privacy.model.SuperAppCategory
import ru.antispam.app.privacy.model.SuperAppRiskLevel

/**
 * SuperAppInspector: Audits applications that request `android.permission.QUERY_ALL_PACKAGES`.
 *
 * Threat model:
 * In Android 11+ (API 30), package visibility is restricted to protect user privacy.
 * However, intrusive commercial superapps, messengers (e.g. MAX, VK), and banking apps
 * declare `QUERY_ALL_PACKAGES` to catalog every app installed on the device.
 *
 * Purposes of intrusive cataloging:
 * 1. Surveillance and fingerprinting: building a behavioral profile of the user.
 * 2. VPN & Censorship-Circumvention detection: spying on whether Amnezia, v2rayNG,
 *    NekoBox, Tor, or Outline are installed.
 * 3. Detecting competitor or financial apps.
 */
object SuperAppInspector {

    private const val TAG = "SuperAppInspector"

    const val PERM_QUERY_ALL_PACKAGES = "android.permission.QUERY_ALL_PACKAGES"
    private const val PERM_INTERNET = "android.permission.INTERNET"
    private const val PERM_READ_PHONE_STATE = "android.permission.READ_PHONE_STATE"
    private const val PERM_FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
    private const val PERM_COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION"

    /**
     * Known commercial surveillance superapps and telemetry SDKs.
     */
    val KNOWN_SURVEILLANCE_SUPERAPPS = setOf(
        "ru.vk.max",
        "com.vkontakte.android",
        "ru.vk.store",
        "com.my.mail",
        "ru.yandex.searchplugin",
        "ru.sberbankmobile",
        "ru.tcsbank.mbank",
        "com.vk.im",
        "ru.mail.cloud"
    )

    /**
     * Recognized launchers and official app stores where package visibility is functional.
     */
    val KNOWN_UTILITY_LAUNCHERS_AND_STORES = setOf(
        "com.android.vending",
        "com.google.android.apps.nexuslauncher",
        "com.android.launcher3",
        "com.teslacoilsw.launcher",
        "bitpit.launcher",
        "com.huawei.appmarket",
        "com.xiaomi.mipicks",
        "com.sec.android.app.samsungapps"
    )

    /**
     * Core AOSP / system packages.
     */
    val KNOWN_CORE_SYSTEM = setOf(
        "android",
        "com.android.systemui",
        "com.google.android.gms",
        "com.google.android.packageinstaller"
    )

    /**
     * Registry of known VPN clients and circumvention tools that superapps target for cataloging.
     */
    val KNOWN_VPN_PACKAGES = mapOf(
        "com.v2raytun.android" to "v2RayTun",
        "org.amnezia.vpn" to "Amnezia VPN",
        "com.v2ray.ang" to "v2rayNG",
        "moe.nb4a" to "NekoBox",
        "io.github.hiddify.client" to "Hiddify",
        "org.outline.android.client" to "Outline VPN",
        "com.adguard.android" to "AdGuard",
        "ch.protonvpn.android" to "Proton VPN",
        "org.torproject.torbrowser" to "Tor Browser",
        "com.cloudflare.onedotonedotonedotone" to "1.1.1.1 WARP",
        "io.github.dovecoteescapee.byedpi" to "ByeDPI",
        "com.wireguard.android" to "WireGuard",
        "com.github.shadowsocks" to "Shadowsocks",
        "org.openvpn.openvpn" to "OpenVPN Connect",
        "de.blinkt.openvpn" to "OpenVPN for Android"
    )

    /**
     * Classifies risk and categorization for an application with given permissions.
     */
    fun classifyRisk(
        packageName: String,
        requestedPermissions: Set<String>,
        isSystemApp: Boolean
    ): Triple<SuperAppRiskLevel, SuperAppCategory, String> {
        val lowerPkg = packageName.lowercase()
        val hasQueryAll = PERM_QUERY_ALL_PACKAGES in requestedPermissions

        if (!hasQueryAll) {
            return Triple(
                SuperAppRiskLevel.SAFE,
                if (isSystemApp) SuperAppCategory.SYSTEM_COMPONENT else SuperAppCategory.REGULAR_APP,
                "Нет доступа к общему реестру установленных программ."
            )
        }

        // 1. Known intrusive surveillance superapps / social networks / telemetry collectors (static + dynamic Ed25519)
        val dynamicSpyware = ru.antispam.app.security.DynamicConfigManager.getDynamicSpywarePackages()
        if (KNOWN_SURVEILLANCE_SUPERAPPS.contains(lowerPkg) || dynamicSpyware.any { it.equals(lowerPkg, ignoreCase = true) }) {
            return Triple(
                SuperAppRiskLevel.CRITICAL,
                SuperAppCategory.SURVEILLANCE_SUPERAPP,
                "КРИТИЧЕСКАЯ УГРОЗА: Коммерческий суперапп каталогизирует все приложения на смартфоне для профилирования и поиска VPN/утилит обхода!"
            )
        }

        // 2. Official App Stores or Launchers (need package visibility for home screen / updates)
        if (KNOWN_UTILITY_LAUNCHERS_AND_STORES.contains(lowerPkg) || lowerPkg.contains("launcher")) {
            return Triple(
                SuperAppRiskLevel.MEDIUM,
                SuperAppCategory.APP_STORE_OR_LAUNCHER,
                "Лаунчер рабочего стола или магазин приложений (доступ к каталогу программ необходим функционально)."
            )
        }

        // 3. Core OS components
        if (KNOWN_CORE_SYSTEM.contains(lowerPkg) || (isSystemApp && lowerPkg.startsWith("com.android."))) {
            return Triple(
                SuperAppRiskLevel.SAFE,
                SuperAppCategory.SYSTEM_COMPONENT,
                "Системный компонент Android OS."
            )
        }

        // 4. Pre-installed OEM system apps
        if (isSystemApp) {
            return Triple(
                SuperAppRiskLevel.MEDIUM,
                SuperAppCategory.SYSTEM_COMPONENT,
                "Системное приложение производителя устройства."
            )
        }

        // 5. Generic third-party app with QUERY_ALL_PACKAGES
        return Triple(
            SuperAppRiskLevel.HIGH,
            SuperAppCategory.COMMERCIAL_TRACKER,
            "ВЫСОКИЙ РИСК: Стороннее приложение сканирует весь список установленных на устройстве программ."
        )
    }

    /**
     * Builds list of readable capability indicators based on requested permissions.
     */
    fun buildCapabilities(
        requestedPermissions: Set<String>,
        hasInstalledVpn: Boolean
    ): List<String> {
        val caps = mutableListOf<String>()
        if (PERM_QUERY_ALL_PACKAGES in requestedPermissions) {
            caps.add("Тотальный инвентарь (QUERY_ALL_PACKAGES)")
        }
        if (hasInstalledVpn) {
            caps.add("Угроза деанонимизации VPN-клиентов")
        }
        if (PERM_INTERNET in requestedPermissions) {
            caps.add("Сетевая выгрузка (INTERNET)")
        }
        if (PERM_READ_PHONE_STATE in requestedPermissions) {
            caps.add("Привязка к SIM/устройству (PHONE_STATE)")
        }
        if (PERM_FINE_LOCATION in requestedPermissions || PERM_COARSE_LOCATION in requestedPermissions) {
            caps.add("Привязка к координатам (LOCATION)")
        }
        return caps
    }

    /**
     * Detects installed VPN clients and circumvention tools from the known registry.
     */
    fun detectInstalledVpnClients(context: Context): List<String> {
        val pm = context.packageManager
        val found = mutableListOf<String>()

        for ((pkg, friendlyName) in KNOWN_VPN_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0)
                found.add(friendlyName)
            } catch (_: PackageManager.NameNotFoundException) {
                // Not installed
            } catch (e: Exception) {
                Log.w(TAG, "Error checking VPN package $pkg: ${e.message}")
            }
        }
        return found
    }

    /**
     * Scans all installed packages that possess `android.permission.QUERY_ALL_PACKAGES`.
     */
    fun scanPackagesWithQueryAll(context: Context): List<PackagePrivacyProfile> {
        val pm = context.packageManager
        val myPackage = context.packageName
        val results = mutableListOf<PackagePrivacyProfile>()

        val vpnClients = detectInstalledVpnClients(context)
        val hasVpnInstalled = vpnClients.isNotEmpty()

        try {
            val installedPackages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            for (pkg in installedPackages) {
                val pkgName = pkg.packageName
                if (pkgName == myPackage) continue

                val requestedPerms = pkg.requestedPermissions?.toSet() ?: emptySet()
                if (PERM_QUERY_ALL_PACKAGES !in requestedPerms) continue

                val appInfo = pkg.applicationInfo
                val isSystem = if (appInfo != null) {
                    (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                } else false

                val appName = if (appInfo != null) {
                    try {
                        pm.getApplicationLabel(appInfo).toString()
                    } catch (_: Exception) {
                        pkgName
                    }
                } else pkgName

                val (riskLevel, category, riskDesc) = classifyRisk(pkgName, requestedPerms, isSystem)
                val isDeepSleeping = AppDormancyHelper.isAppInDeepSleep(context, pkgName)
                val finalRiskLevel = if (isDeepSleeping) SuperAppRiskLevel.SAFE else riskLevel
                val finalCategory = if (isDeepSleeping) SuperAppCategory.REGULAR_APP else category
                val finalDesc = if (isDeepSleeping) {
                    "Приложение находится в глубоком сне (заморожено системой). Фоновое сканирование установленных приложений заблокировано."
                } else {
                    riskDesc
                }
                val capabilities = buildCapabilities(requestedPerms, hasVpnInstalled)

                results.add(
                    PackagePrivacyProfile(
                        packageName = pkgName,
                        appName = appName,
                        isSystemApp = isSystem,
                        hasQueryAllPackages = true,
                        riskLevel = finalRiskLevel,
                        category = finalCategory,
                        riskDescription = finalDesc,
                        detectedCapabilities = capabilities,
                        isDeepSleeping = isDeepSleeping
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to scan packages with QUERY_ALL_PACKAGES: ${e.message}")
        }

        return results.sortedBy {
            when (it.riskLevel) {
                SuperAppRiskLevel.CRITICAL -> 0
                SuperAppRiskLevel.HIGH -> 1
                SuperAppRiskLevel.MEDIUM -> 2
                SuperAppRiskLevel.SAFE -> 3
            }
        }
    }

    /**
     * Performs a full SuperApp privacy audit, analyzing total package scanners
     * in conjunction with installed VPN clients.
     */
    fun auditSuperApps(context: Context): SuperAppAuditSummary {
        val apps = scanPackagesWithQueryAll(context)
        val vpns = detectInstalledVpnClients(context)
        val critical = apps.count { it.riskLevel == SuperAppRiskLevel.CRITICAL && !it.isDeepSleeping }
        val high = apps.count { it.riskLevel == SuperAppRiskLevel.HIGH && !it.isDeepSleeping }

        val isExposed = vpns.isNotEmpty() && (critical + high > 0)

        return SuperAppAuditSummary(
            appsWithQueryAll = apps,
            criticalCount = critical,
            highCount = high,
            installedVpnClients = vpns,
            isVpnInventoryExposed = isExposed
        )
    }

    /**
     * Opens system Application Details settings to allow the user to restrict or uninstall the app.
     */
    fun openAppDetailsSettings(context: Context, packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open application details for $packageName: ${e.message}")
        }
    }
}
