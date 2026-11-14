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
    AMNEZIA_VPN("AmneziaVPN", "org.amnezia.vpn"),
    V2RAYNG_NEKOBOX("v2rayNG / NekoBox", "com.v2ray.ang"),
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

    /**
     * Scans installed packages and returns the subset of Russian target services present on the device.
     */
    fun getInstalledTargetApps(context: Context): List<MaskingTargetApp> {
        val pm = context.packageManager
        val installedApps = mutableListOf<MaskingTargetApp>()

        for (target in RussianAppRegistry.TARGET_SERVICES) {
            try {
                val appInfo = pm.getApplicationInfo(target.packageName, 0)
                val label = pm.getApplicationLabel(appInfo).toString()
                installedApps.add(
                    MaskingTargetApp(
                        packageName = target.packageName,
                        appName = if (label.isNotBlank()) label else target.defaultName,
                        category = target.category,
                        isInstalled = true
                    )
                )
            } catch (_: PackageManager.NameNotFoundException) {
                // Not installed on this device
            } catch (e: Exception) {
                Log.w(TAG, "Error checking package ${target.packageName}: ${e.message}")
            }
        }

        return installedApps
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

        return packages.size
    }

    /**
     * Returns a human-friendly step-by-step setup guide for the given VPN client.
     */
    fun getSetupGuide(format: VpnClientFormat): List<String> {
        return when (format) {
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

        // Check Amnezia
        try {
            val appInfo = pm.getApplicationInfo("org.amnezia.vpn", 0)
            val name = pm.getApplicationLabel(appInfo).toString()
            return Pair(VpnClientFormat.AMNEZIA_VPN, name)
        } catch (_: PackageManager.NameNotFoundException) {}

        // Check v2rayNG
        try {
            val appInfo = pm.getApplicationInfo("com.v2ray.ang", 0)
            val name = pm.getApplicationLabel(appInfo).toString()
            return Pair(VpnClientFormat.V2RAYNG_NEKOBOX, name)
        } catch (_: PackageManager.NameNotFoundException) {}

        // Check NekoBox
        try {
            val appInfo = pm.getApplicationInfo("moe.nb4a", 0)
            val name = pm.getApplicationLabel(appInfo).toString()
            return Pair(VpnClientFormat.V2RAYNG_NEKOBOX, name)
        } catch (_: PackageManager.NameNotFoundException) {}

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
