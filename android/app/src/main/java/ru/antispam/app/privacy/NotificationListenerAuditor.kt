package ru.antispam.app.privacy

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import ru.antispam.app.privacy.model.ListenerRiskLevel
import ru.antispam.app.privacy.model.NotificationListenerApp

/**
 * NotificationListenerAuditor: Detects unauthorized applications eavesdropping on system notifications.
 *
 * Background:
 * Android apps that acquire `NotificationListenerService` can intercept ALL incoming notifications,
 * including 2FA SMS OTP codes, banking alerts (Sber, Tinkoff, VTB), Gosuslugi verification codes,
 * and messenger chats without ever needing the dangerous `RECEIVE_SMS` or `READ_SMS` permissions.
 */
object NotificationListenerAuditor {

    private const val TAG = "NotificationListenerAuditor"
    const val SETTING_ENABLED_LISTENERS = "enabled_notification_listeners"

    /**
     * Known official system/OS packages inherently trusted to process notifications.
     */
    val TRUSTED_SYSTEM_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.google.android.gms",
        "com.google.android.wearable.app",
        "com.android.server.telecom",
        "com.microsoft.appmanager",
        "com.google.android.projection.gearhead",
        "com.sec.android.app.launcher",
        "com.samsung.android.app.routines",
        "com.samsung.android.smartmirroring"
    )

    /**
     * Known companion apps for smart watches and fitness trackers that legitimately
     * mirror notifications to external wearable hardware.
     */
    val KNOWN_WEARABLE_COMPANIONS = setOf(
        "com.samsung.android.app.watchmanager",
        "com.samsung.android.geargplugin",
        "com.samsung.android.gearrplugin",
        "com.samsung.wearable.fit3plugin",
        "com.samsung.android.wearable.fit3plugin",
        "com.huawei.health",
        "com.mi.health",
        "com.huami.midong",
        "com.xiaomi.wearable",
        "com.garmin.android.apps.connectmobile",
        "com.fitbit.FitbitMobile",
        "com.polar.polarflow",
        "com.oppo.wearable",
        "com.heytap.wearable.health"
    )

    /**
     * Parses the colon-separated `enabled_notification_listeners` string into a list of (packageName, serviceName).
     * Example input: "com.pkg.a/com.pkg.a.ServiceA:com.pkg.b/com.pkg.b.ServiceB"
     */
    fun parseEnabledListeners(rawString: String?): List<Pair<String, String>> {
        if (rawString.isNullOrBlank()) return emptyList()

        val results = mutableListOf<Pair<String, String>>()
        val entries = rawString.split(":")

        for (entry in entries) {
            val trimmed = entry.trim()
            if (trimmed.isEmpty()) continue

            val cn = ComponentName.unflattenFromString(trimmed)
            if (cn != null) {
                results.add(Pair(cn.packageName, cn.className))
            } else if (trimmed.contains("/")) {
                val parts = trimmed.split("/", limit = 2)
                results.add(Pair(parts[0], parts.getOrNull(1) ?: ""))
            } else {
                results.add(Pair(trimmed, ""))
            }
        }

        return results
    }

    /**
     * Classifies the security risk of a listener based on its package name and system application status.
     */
    fun classifyRisk(packageName: String, isSystemApp: Boolean): Pair<ListenerRiskLevel, String> {
        val lowerPkg = packageName.lowercase()

        // 1. Core Android system service / OEM trusted system service
        if (TRUSTED_SYSTEM_PACKAGES.contains(lowerPkg) ||
            lowerPkg.startsWith("com.android.") ||
            lowerPkg.startsWith("com.sec.android.app.launcher") ||
            lowerPkg.startsWith("com.samsung.android.app.routines") ||
            lowerPkg.startsWith("com.samsung.android.smartmirroring") ||
            lowerPkg == "com.microsoft.appmanager" ||
            lowerPkg == "com.google.android.projection.gearhead"
        ) {
            return Pair(
                ListenerRiskLevel.SAFE,
                "Официальный системный компонент (Samsung / Google / Microsoft). Доступ к шторке безопасен."
            )
        }

        // 2. Verified smartwatch / fitness companion app
        if (KNOWN_WEARABLE_COMPANIONS.contains(lowerPkg) ||
            lowerPkg.contains("wearable") ||
            lowerPkg.contains("watch") ||
            (lowerPkg.contains("gear") && lowerPkg.contains("plugin")) ||
            lowerPkg.contains("fit3") ||
            lowerPkg.contains("health")
        ) {
            return Pair(
                ListenerRiskLevel.MEDIUM,
                "Приложение для носимых устройств (умные часы/браслет). Пересылает уведомления на аксессуар."
            )
        }

        // 3. System app from vendor
        if (isSystemApp) {
            if (lowerPkg.startsWith("com.google.android.") ||
                lowerPkg.startsWith("com.samsung.") ||
                lowerPkg.startsWith("com.sec.")
            ) {
                return Pair(
                    ListenerRiskLevel.SAFE,
                    "Системный компонент платформы. Доступ к шторке безопасен."
                )
            }
            return Pair(
                ListenerRiskLevel.MEDIUM,
                "Предустановленное системное приложение производителя."
            )
        }

        // 4. Third-party non-system app -> HIGH RISK
        return Pair(
            ListenerRiskLevel.HIGH,
            "ВНИМАНИЕ: Стороннее приложение имеет полный доступ к шторке уведомлений и может перехватывать СМС-коды банков и 2FA!"
        )
    }

    /**
     * Queries active NotificationListener applications from Android Settings.
     */
    fun getEnabledNotificationListeners(
        context: Context,
        trustedPackages: Set<String> = emptySet()
    ): List<NotificationListenerApp> {
        val raw = try {
            Settings.Secure.getString(context.contentResolver, SETTING_ENABLED_LISTENERS)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read $SETTING_ENABLED_LISTENERS: ${e.message}")
            null
        }

        val parsedComponents = parseEnabledListeners(raw)
        if (parsedComponents.isEmpty()) return emptyList()

        val pm = context.packageManager
        val apps = mutableListOf<NotificationListenerApp>()

        for ((pkgName, serviceName) in parsedComponents) {
            val isTrusted = trustedPackages.contains(pkgName.lowercase().trim())
            try {
                val appInfo = pm.getApplicationInfo(pkgName, 0)
                val isSystem = (appInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
                val appName = pm.getApplicationLabel(appInfo).toString()
                val (riskLevel, riskDesc) = classifyRisk(pkgName, isSystem)

                apps.add(
                    NotificationListenerApp(
                        packageName = pkgName,
                        appName = appName,
                        serviceName = serviceName,
                        isSystemApp = isSystem,
                        riskLevel = riskLevel,
                        riskDescription = riskDesc,
                        isTrusted = isTrusted
                    )
                )
            } catch (e: PackageManager.NameNotFoundException) {
                // App uninstalled but still in settings string
                val (riskLevel, riskDesc) = classifyRisk(pkgName, isSystemApp = false)
                apps.add(
                    NotificationListenerApp(
                        packageName = pkgName,
                        appName = pkgName,
                        serviceName = serviceName,
                        isSystemApp = false,
                        riskLevel = riskLevel,
                        riskDescription = "$riskDesc (Пакет удален или недоступен)",
                        isTrusted = isTrusted
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Error resolving listener package $pkgName: ${e.message}")
            }
        }

        return apps.sortedBy {
            when (it.riskLevel) {
                ListenerRiskLevel.HIGH -> 0
                ListenerRiskLevel.MEDIUM -> 1
                ListenerRiskLevel.SAFE -> 2
            }
        }
    }

    /**
     * Opens system Notification Access settings so user can revoke listener permission in 1 click.
     */
    fun openNotificationListenerSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open notification listener settings: ${e.message}")
        }
    }
}
