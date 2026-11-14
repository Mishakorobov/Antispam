package ru.antispam.app.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Supported OEM hardware vendors and custom Android firmware distributions.
 */
enum class OemVendor(
    val brandName: String,
    val uiSkinName: String,
    val instructions: List<String>,
    val isAggressiveKiller: Boolean
) {
    XIAOMI_HYPEROS(
        brandName = "Xiaomi / Redmi / POCO",
        uiSkinName = "HyperOS / MIUI",
        instructions = listOf(
            "Включите тумблер «Автозапуск» (Разрешить приложению запускаться автоматически)",
            "В разделе «Контроль активности» выберите «Нет ограничений», чтобы служба защиты звонков не выгружалась"
        ),
        isAggressiveKiller = true
    ),
    HUAWEI_EMUI(
        brandName = "Huawei / Honor",
        uiSkinName = "EMUI / MagicOS / HarmonyOS",
        instructions = listOf(
            "Откройте «Запуск приложений» в диспетчере телефона",
            "Отключите «Автоматическое управление» для AntiSpam",
            "Включите все 3 пункта: «Автозапуск», «Косвенный запуск» и «Работа в фоновом режиме»"
        ),
        isAggressiveKiller = true
    ),
    SAMSUNG_ONEUI(
        brandName = "Samsung",
        uiSkinName = "One UI",
        instructions = listOf(
            "Откройте «Обслуживание устройства» -> «Батарея»",
            "Перейдите в «Ограничения в фоновом режиме» -> «Неспящие приложения»",
            "Добавьте AntiSpam в список, чтобы система не отправляла фильтр звонков в глубокий сон"
        ),
        isAggressiveKiller = true
    ),
    VIVO_ORIGINOS(
        brandName = "Vivo / iQOO",
        uiSkinName = "OriginOS / Funtouch OS",
        instructions = listOf(
            "В диспетчере i Manager перейдите в «Управление автозапуском»",
            "Разрешите приложению AntiSpam запуск и работу в фоновом режиме с высоким энергопотреблением"
        ),
        isAggressiveKiller = true
    ),
    OPPO_COLOROS(
        brandName = "Oppo / Realme / OnePlus",
        uiSkinName = "ColorOS / Realme UI",
        instructions = listOf(
            "В диспетчере телефона откройте «Управление автозапуском»",
            "Разрешите приложению автоматический запуск и фоновую активность без ограничений"
        ),
        isAggressiveKiller = true
    ),
    GENERIC_AOSP(
        brandName = "Google Pixel / AOSP",
        uiSkinName = "Чистый Android",
        instructions = listOf(
            "В настройках батареи выберите «Без ограничений»",
            "Разрешите приложению фоновую работу без оптимизации энергопотребления"
        ),
        isAggressiveKiller = false
    )
}

/**
 * OemOnboardingHelper:
 * Manages 100% background service survivability across aggressive OEM battery-killing ROMs
 * (Xiaomi HyperOS, Huawei EMUI, Samsung OneUI, Vivo OriginOS, Oppo ColorOS).
 */
object OemOnboardingHelper {

    private const val TAG = "OemOnboardingHelper"

    /**
     * Detects OEM hardware vendor and Android UI skin.
     * Default parameters allow clean JVM unit testing without hardware reflection.
     */
    fun detectOem(
        manufacturer: String = Build.MANUFACTURER,
        brand: String = Build.BRAND
    ): OemVendor {
        val m = manufacturer.lowercase()
        val b = brand.lowercase()

        return when {
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") ||
            b.contains("xiaomi") || b.contains("redmi") || b.contains("poco") -> {
                OemVendor.XIAOMI_HYPEROS
            }
            m.contains("huawei") || m.contains("honor") ||
            b.contains("huawei") || b.contains("honor") -> {
                OemVendor.HUAWEI_EMUI
            }
            m.contains("samsung") || b.contains("samsung") -> {
                OemVendor.SAMSUNG_ONEUI
            }
            m.contains("vivo") || m.contains("iqoo") ||
            b.contains("vivo") || b.contains("iqoo") -> {
                OemVendor.VIVO_ORIGINOS
            }
            m.contains("oppo") || m.contains("realme") || m.contains("oneplus") ||
            b.contains("oppo") || b.contains("realme") || b.contains("oneplus") -> {
                OemVendor.OPPO_COLOROS
            }
            else -> {
                OemVendor.GENERIC_AOSP
            }
        }
    }

    /**
     * Checks whether the application is already whitelisted from Android OS Doze battery optimizations.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return try {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } catch (e: Exception) {
            true
        }
    }

    /**
     * Constructs ordered cascade of candidate autostart and battery optimization intents
     * tailored to the detected OEM firmware.
     */
    fun getCandidateIntents(
        context: Context,
        vendor: OemVendor = detectOem()
    ): List<Intent> {
        val list = mutableListOf<Intent>()
        val pkg = context.packageName

        when (vendor) {
            OemVendor.XIAOMI_HYPEROS -> {
                val miuiAutostart = Intent()
                miuiAutostart.component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                list.add(miuiAutostart)

                val miuiPowerkeeper = Intent()
                miuiPowerkeeper.component = ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
                miuiPowerkeeper.putExtra("package_name", pkg)
                miuiPowerkeeper.putExtra("package_label", "AntiSpam")
                list.add(miuiPowerkeeper)
            }

            OemVendor.HUAWEI_EMUI -> {
                val huaweiStartup = Intent()
                huaweiStartup.component = ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
                list.add(huaweiStartup)

                val huaweiProtect = Intent()
                huaweiProtect.component = ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
                list.add(huaweiProtect)

                val huaweiControl = Intent()
                huaweiControl.component = ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")
                list.add(huaweiControl)
            }

            OemVendor.SAMSUNG_ONEUI -> {
                // On Samsung One UI (One UI 1 to 6), internal activities in com.samsung.android.sm / lool
                // are private and unexported (throwing SecurityException: READ_SEARCH_INDEXABLES).
                // The most direct deep-link is the system-level Battery Optimization exemption dialog,
                // followed by App Details Settings (from which Samsung provides direct access to "Battery -> Unrestricted").
                val reqIgnore = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$pkg")
                }
                list.add(reqIgnore)

                val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$pkg")
                }
                list.add(appDetails)
            }

            OemVendor.VIVO_ORIGINOS -> {
                val vivoAutostart = Intent()
                vivoAutostart.component = ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
                list.add(vivoAutostart)

                val iqooWhitelist = Intent()
                iqooWhitelist.component = ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")
                list.add(iqooWhitelist)
            }

            OemVendor.OPPO_COLOROS -> {
                val colorOsStartup = Intent()
                colorOsStartup.component = ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
                list.add(colorOsStartup)

                val colorOsGuard = Intent()
                colorOsGuard.component = ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")
                list.add(colorOsGuard)
            }

            OemVendor.GENERIC_AOSP -> {
                // Generic AOSP handled below in fallbacks
            }
        }

        // Standard Android Doze and Battery Optimization fallbacks
        try {
            val reqIgnore = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            reqIgnore.data = Uri.parse("package:$pkg")
            list.add(reqIgnore)
        } catch (_: Exception) {}

        try {
            val ignoreSettings = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            list.add(ignoreSettings)
        } catch (_: Exception) {}

        try {
            val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            appDetails.data = Uri.parse("package:$pkg")
            list.add(appDetails)
        } catch (_: Exception) {}

        return list
    }

    /**
     * Resolves the first valid autostart settings Intent for the current device.
     */
    fun getAutostartSettingsIntent(
        context: Context,
        vendor: OemVendor = detectOem()
    ): Intent? {
        val candidates = getCandidateIntents(context, vendor)
        val pm = context.packageManager

        for (intent in candidates) {
            try {
                if (intent.resolveActivity(pm) != null) {
                    val target = Intent(intent)
                    target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    return target
                }
            } catch (e: Exception) {
                Log.d(TAG, "Intent candidate resolution error: ${e.message}")
            }
        }

        // Default to first candidate with NEW_TASK flag
        return candidates.firstOrNull()?.let {
            val target = Intent(it)
            target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            target
        }
    }

    /**
     * Opens the vendor autostart / battery management screen.
     */
    fun openAutostartSettings(context: Context): Boolean {
        val candidates = getCandidateIntents(context)
        val pm = context.packageManager

        for (intent in candidates) {
            try {
                if (intent.resolveActivity(pm) != null) {
                    val target = Intent(intent).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(target)
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed candidate intent $intent: ${e.message}")
            }
        }

        // Direct fallback: Deep link into AntiSpam Shield Application Details Settings
        return try {
            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
            true
        } catch (e2: Exception) {
            Log.e(TAG, "Failed to open fallback app details: ${e2.message}")
            false
        }
    }
}
