package ru.antispam.app.privacy

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import ru.antispam.app.privacy.model.MaskingTargetApp
import ru.antispam.app.privacy.model.RussianAppCategory
import ru.antispam.app.privacy.model.RussianAppRegistry

/**
 * Format of the split tunneling / bypass export for various VPN clients.
 */
enum class VpnClientFormat(val displayName: String, val appPackage: String?) {
    V2RAYTUN("v2RayTun", "com.v2raytun.android"),
    V2RAYNG_NEKOBOX("v2rayNG / NekoBox", "com.v2ray.ang"),
    AMNEZIA_VPN("AmneziaVPN", "org.amnezia.vpn"),
    RAW_PACKAGE_LIST("Список пакетов (Plain Text)", null)
}

/**
 * VpnMaskingHelper: Generates split-tunneling exclusion rules for Russian banks,
 * state portals, and superapps to mask active VPN usage.
 *
 * Technical Background:
 * When an app is added to a VPN client's "Bypass / Disallowed" list, Android's VpnService
 * routes that application's sockets directly through the physical cellular (rmnet) or
 * Wi-Fi (wlan) network interface instead of the virtual `tun0` interface.
 *
 * Result:
 * 1. Inside Sberbank, T-Bank, VTB, Gosuslugi: `NetworkCapabilities.hasTransport(TRANSPORT_VPN)`
 *    returns FALSE.
 * 2. Banking apps and state portals work seamlessly with fast local connections and no blocks.
 * 3. The VPN's foreign server IP is never exposed to Russian DPI monitors, and the rest of the
 *    device traffic remains fully encrypted.
 */
object VpnMaskingHelper {

    private const val TAG = "VpnMaskingHelper"
    const val PREFS_NAME = "antispam_privacy_prefs"
    const val KEY_VPN_ROUTING_CONFIGURED = "vpn_routing_configured"

    fun isVpnRoutingConfigured(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_VPN_ROUTING_CONFIGURED, false)
    }

    fun setVpnRoutingConfigured(context: Context, configured: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_VPN_ROUTING_CONFIGURED, configured)
            .apply()
    }

    /**
     * Scans installed packages and returns the subset of Russian target services and
     * VPN-catalog-querying applications present on the device.
     */
    fun getInstalledTargetApps(context: Context): List<MaskingTargetApp> {
        val pm = context.packageManager
        val installedApps = mutableMapOf<String, MaskingTargetApp>()

        // 1. Check all officially cataloged entries in RussianAppRegistry
        for (target in RussianAppRegistry.TARGET_SERVICES) {
            try {
                val appInfo = pm.getApplicationInfo(target.packageName, 0)
                val label = pm.getApplicationLabel(appInfo).toString()
                installedApps[target.packageName] = MaskingTargetApp(
                    packageName = target.packageName,
                    appName = if (label.isNotBlank()) label else target.defaultName,
                    category = target.category,
                    isInstalled = true
                )
            } catch (_: PackageManager.NameNotFoundException) {
                // Not installed
            } catch (e: Exception) {
                Log.w(TAG, "Error checking package ${target.packageName}: ${e.message}")
            }
        }

        // 2. Dynamically scan all installed packages for additional Russian services & VPN catalog trackers
        try {
            val allPackages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            for (pkg in allPackages) {
                val pkgName = pkg.packageName.lowercase()
                if (installedApps.containsKey(pkg.packageName)) continue
                if (pkgName.startsWith("ru.antispam.")) continue

                val appInfo = pkg.applicationInfo ?: continue
                val isSystem = (appInfo.flags and (android.content.pm.ApplicationInfo.FLAG_SYSTEM or android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
                val requestedPerms = pkg.requestedPermissions?.toSet() ?: emptySet()
                val hasQueryAll = SuperAppInspector.PERM_QUERY_ALL_PACKAGES in requestedPerms

                val isRussianService = pkgName.startsWith("ru.") ||
                        pkgName.startsWith("su.") ||
                        pkgName.contains("gosuslugi") ||
                        pkgName.contains("goskey") ||
                        pkgName.contains(".yandex.") ||
                        pkgName.contains("yandex") ||
                        pkgName.contains("sber") ||
                        pkgName.contains("tcsbank") ||
                        pkgName.contains("tinkoff") ||
                        pkgName.contains("wildberries") ||
                        pkgName.contains("alfabank") ||
                        pkgName.contains("vtb") ||
                        pkgName.contains("gazprombank") ||
                        pkgName.contains("ozon") ||
                        pkgName.contains("avito") ||
                        pkgName.contains("mirpay") ||
                        pkgName.contains("nspk") ||
                        pkgName.contains("beeline") ||
                        pkgName.contains("megafon") ||
                        pkgName.contains("mts.") ||
                        pkgName.contains("tele2") ||
                        pkgName.contains("rostelecom") ||
                        pkgName.contains("yota") ||
                        pkgName.contains("drom") ||
                        pkgName.contains("auto.ru") ||
                        pkgName.contains("cian") ||
                        pkgName.contains("hh.ru") ||
                        pkgName.contains("headhunter") ||
                        pkgName.contains("rutube") ||
                        pkgName.contains("vkontakte") ||
                        pkgName.contains("vk.store") ||
                        pkgName.contains("vk.max") ||
                        pkgName.contains("my.mail") ||
                        pkgName.contains("sovcombank") ||
                        pkgName.contains("openbank") ||
                        pkgName.contains("raiffeisen") ||
                        pkgName.contains("psbank") ||
                        pkgName.contains("pochtabank")

                // If Russian service OR non-system package with QUERY_ALL_PACKAGES (potential VPN tracker)
                if (isRussianService || (!isSystem && hasQueryAll)) {
                    val label = try { pm.getApplicationLabel(appInfo).toString() } catch (_: Exception) { pkg.packageName }
                    val category = when {
                        !isSystem && hasQueryAll -> RussianAppCategory.SUPERAPP_AND_TELECOM
                        pkgName.contains("bank") || pkgName.contains("sber") || pkgName.contains("tcs") || pkgName.contains("vtb") || pkgName.contains("pay") -> RussianAppCategory.BANKING
                        pkgName.contains("gos") -> RussianAppCategory.GOVERNMENT
                        pkgName.contains("beeline") || pkgName.contains("megafon") || pkgName.contains("mts") || pkgName.contains("tele2") || pkgName.contains("yota") -> RussianAppCategory.SUPERAPP_AND_TELECOM
                        else -> RussianAppCategory.MARKETPLACE_AND_MEDIA
                    }
                    installedApps[pkg.packageName] = MaskingTargetApp(
                        packageName = pkg.packageName,
                        appName = if (label.isNotBlank()) label else pkg.packageName,
                        category = category,
                        isInstalled = true
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error dynamically scanning Russian apps: ${e.message}")
        }

        return installedApps.values.sortedBy { it.appName.lowercase() }
    }

    /**
     * Generates a split-tunneling / direct-routing config for the specified VPN format.
     */
    fun generateSplitTunnelConfig(
        targetPackageNames: List<String>,
        format: VpnClientFormat
    ): String {
        if (targetPackageNames.isEmpty()) {
            return ""
        }

        val sortedPackages = targetPackageNames.distinct().sorted()

        return when (format) {
            VpnClientFormat.V2RAYTUN -> {
                buildString {
                    appendLine("# ========================================================")
                    appendLine("# Правила прямого обхода (Bypass / Direct) для v2RayTun")
                    appendLine("# ========================================================")
                    appendLine("# Инструкция для v2RayTun:")
                    appendLine("# 1. Откройте v2RayTun -> Настройки (внизу справа) -> Маршрутизация.")
                    appendLine("# 2. Нажмите «Маршрутизация выбранных приложений».")
                    appendLine("# 3. Включите режим «Обход выбранных приложений» (Bypass).")
                    appendLine("# 4. Отметьте следующие сервисы (банки, Госуслуги, супераппы):")
                    sortedPackages.forEach { pkg ->
                        appendLine(pkg)
                    }
                }
            }

            VpnClientFormat.AMNEZIA_VPN -> {
                buildString {
                    appendLine("# ========================================================")
                    appendLine("# Правила раздельного туннелирования для AmneziaVPN")
                    appendLine("# Все эти приложения пускаются напрямую в обход VPN:")
                    appendLine("# bank/gov apps will see hasTransport(TRANSPORT_VPN) == FALSE")
                    appendLine("# ========================================================")
                    sortedPackages.forEach { pkg ->
                        appendLine(pkg)
                    }
                }
            }

            VpnClientFormat.V2RAYNG_NEKOBOX -> {
                val formattedApps = sortedPackages.joinToString(separator = ",\n        ") { "\"$it\"" }
                """
{
  "routing": {
    "domainStrategy": "IPIfNonMatch",
    "rules": [
      {
        "type": "field",
        "outboundTag": "direct",
        "app": [
        $formattedApps
        ]
      }
    ]
  }
}
                """.trimIndent()
            }

            VpnClientFormat.RAW_PACKAGE_LIST -> {
                sortedPackages.joinToString("\n")
            }
        }
    }

    /**
     * Copies the split tunnel config of installed target apps to the clipboard.
     * Returns the count of installed target apps exported.
     */
    fun copyConfigToClipboard(context: Context, format: VpnClientFormat): Int {
        val installed = getInstalledTargetApps(context)
        val packages = if (installed.isNotEmpty()) {
            installed.map { it.packageName }
        } else {
            // Fallback to complete registry if no target apps were detected
            RussianAppRegistry.ALL_PACKAGE_NAMES.toList()
        }

        val config = generateSplitTunnelConfig(packages, format)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("VPN Split Tunnel Rules", config)
        clipboard?.setPrimaryClip(clip)
        setVpnRoutingConfigured(context, true)

        return packages.size
    }

    /**
     * Returns a human-friendly step-by-step setup guide for the given VPN client.
     */
    fun getSetupGuide(format: VpnClientFormat): List<String> {
        return when (format) {
            VpnClientFormat.V2RAYTUN -> listOf(
                "1. Откройте v2RayTun и нажмите кнопку «Настройки» в правом нижнем углу экрана.",
                "2. Перейдите в раздел «Маршрутизация» -> нажмите «Маршрутизация выбранных приложений».",
                "3. Вверху переключите режим на «Обход выбранных приложений» (Bypass / Исключить).",
                "4. В списке приложений отметьте галочками российские сервисы (банки, Госуслуги, Госключ, маркетплейсы).",
                "5. Переподключите VPN-соединение. Теперь все отмеченные российские сервисы будут работать НАПРЯМУЮ в обход VPN, а остальной трафик пойдет через VPN!"
            )

            VpnClientFormat.AMNEZIA_VPN -> listOf(
                "1. Откройте приложение AmneziaVPN и перейдите в «Настройки» (иконка шестеренки).",
                "2. Выберите пункт «Раздельное туннелирование» (Split tunneling).",
                "3. Установите режим: «Исключить выбранные приложения» (Только выбранные приложения идут в обход VPN).",
                "4. Отметьте скопированные банки и госуслуги из списка (Сбер, Т-Банк, ВТБ, Госуслуги).",
                "5. Готово! Теперь банковские приложения работают напрямую на максимальной скорости и не видят VPN."
            )

            VpnClientFormat.V2RAYNG_NEKOBOX -> listOf(
                "1. Откройте v2rayNG или NekoBox и перейдите в боковое меню -> «Настройки».",
                "2. Включите опцию «Раздельное проксирование» (Per-app proxy).",
                "3. Выберите режим: «Исключить выбранные» (Bypass selected apps).",
                "4. Выберите или импортируйте добавленные приложения из буфера обмена.",
                "5. Переподключите VPN-профиль. Банки перестанут выдавать ошибки входа!"
            )

            VpnClientFormat.RAW_PACKAGE_LIST -> listOf(
                "1. Скопируйте список пакетов в буфер обмена.",
                "2. Откройте настройки раздельного туннелирования в вашем VPN-клиенте (Clash, Sing-box, WireGuard, Shadowrocket).",
                "3. Вставьте список пакетов в поле Direct / Bypass / Disallowed Apps.",
                "4. Сохраните конфигурацию и перезапустите туннель."
            )
        }
    }

    /**
     * Detects if the user has a known VPN client installed on their phone.
     */
    fun detectInstalledVpnClient(context: Context): Pair<VpnClientFormat, String>? {
        val pm = context.packageManager

        // 1. Check v2RayTun
        try {
            val appInfo = pm.getApplicationInfo("com.v2raytun.android", 0)
            val name = pm.getApplicationLabel(appInfo).toString()
            return Pair(VpnClientFormat.V2RAYTUN, if (name.isNotBlank()) name else "v2RayTun")
        } catch (_: PackageManager.NameNotFoundException) {}

        // 2. Check Amnezia
        try {
            val appInfo = pm.getApplicationInfo("org.amnezia.vpn", 0)
            val name = pm.getApplicationLabel(appInfo).toString()
            return Pair(VpnClientFormat.AMNEZIA_VPN, name)
        } catch (_: PackageManager.NameNotFoundException) {}

        // 3. Check v2rayNG
        try {
            val appInfo = pm.getApplicationInfo("com.v2ray.ang", 0)
            val name = pm.getApplicationLabel(appInfo).toString()
            return Pair(VpnClientFormat.V2RAYNG_NEKOBOX, name)
        } catch (_: PackageManager.NameNotFoundException) {}

        // 4. Check NekoBox
        try {
            val appInfo = pm.getApplicationInfo("moe.nb4a", 0)
            val name = pm.getApplicationLabel(appInfo).toString()
            return Pair(VpnClientFormat.V2RAYNG_NEKOBOX, name)
        } catch (_: PackageManager.NameNotFoundException) {}

        // 5. Check Hiddify
        try {
            val appInfo = pm.getApplicationInfo("io.github.hiddify.client", 0)
            val name = pm.getApplicationLabel(appInfo).toString()
            return Pair(VpnClientFormat.V2RAYNG_NEKOBOX, name)
        } catch (_: PackageManager.NameNotFoundException) {}

        // 6. Dynamic universal detection via android.net.VpnService intent filter
        try {
            val vpnServices = pm.queryIntentServices(
                Intent("android.net.VpnService"),
                PackageManager.GET_META_DATA
            )
            for (resolveInfo in vpnServices) {
                val serviceInfo = resolveInfo.serviceInfo ?: continue
                val pkgName = serviceInfo.packageName.lowercase()
                val label = serviceInfo.loadLabel(pm)?.toString() ?: pkgName
                when {
                    pkgName.contains("v2raytun") -> return Pair(VpnClientFormat.V2RAYTUN, "v2RayTun")
                    pkgName.contains("amnezia") -> return Pair(VpnClientFormat.AMNEZIA_VPN, "AmneziaVPN")
                    pkgName.contains("v2rayng") || pkgName.contains("nekobox") || pkgName.contains("hiddify") ||
                            pkgName.contains("singbox") || pkgName.contains("happ") ->
                        return Pair(VpnClientFormat.V2RAYNG_NEKOBOX, label)
                    else -> return Pair(VpnClientFormat.RAW_PACKAGE_LIST, label)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Dynamic VPN service discovery error: ${e.message}")
        }

        return null
    }

    /**
     * Opens system VPN settings.
     */
    fun openVpnSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_VPN_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open VPN settings: ${e.message}")
        }
    }

    /**
     * Launches the installed VPN client directly if present.
     */
    fun launchVpnClient(context: Context, format: VpnClientFormat): Boolean {
        val pkg = format.appPackage ?: return false
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(pkg) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(launchIntent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch $pkg: ${e.message}")
            false
        }
    }
}
