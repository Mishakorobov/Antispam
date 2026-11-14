package ru.antispam.app.privacy

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import ru.antispam.app.privacy.model.AccessibilityRiskLevel
import ru.antispam.app.privacy.model.AccessibilityServiceApp

/**
 * AccessibilityAuditor: Audits active Android Accessibility Services.
 *
 * Threat:
 * Android Accessibility Services (`AccessibilityService`) possess total control over the UI tree:
 * 1. Window text extraction: can read private chats inside Telegram, Signal, WhatsApp, and banking apps.
 * 2. Keystroke logging: can capture passwords, PINs, and card data.
 * 3. Remote touch injection: can click buttons without user consent.
 *
 * While legitimate for disabled users (TalkBack), intrusive superapps and banking trojans
 * aggressively demand Accessibility rights to bypass all Android sandbox security boundaries.
 */
object AccessibilityAuditor {

    private const val TAG = "AccessibilityAuditor"
    const val SETTING_ENABLED_ACCESSIBILITY_SERVICES = Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES

    /**
     * Known official Android OS / Google Accessibility Suite packages inherently designed for accessibility.
     */
    val TRUSTED_ACCESSIBILITY_PACKAGES = setOf(
        "com.google.android.marvin.talkback",
        "com.google.android.accessibility.suite",
        "com.google.android.apps.accessibility.voiceaccess",
        "com.android.switchaccess",
        "android",
        "com.android.systemui",
        "com.google.android.gms"
    )

    /**
     * Known official vendor OEM accessibility tools.
     */
    val KNOWN_OEM_ACCESSIBILITY = setOf(
        "com.samsung.accessibility",
        "com.samsung.android.honeyboard",
        "com.sec.android.app.launcher",
        "com.samsung.android.app.modes",
        "com.huawei.accessibility",
        "com.miui.accessibility",
        "com.oppo.accessibility"
    )

    /**
     * Parses the colon-separated `enabled_accessibility_services` setting string into (packageName, serviceName).
     * Example input: "com.google.android.marvin.talkback/.TalkBackService:com.other.app/.SpyService"
     */
    fun parseEnabledServices(rawString: String?): List<Pair<String, String>> {
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
     * Classifies the security risk of an active accessibility service.
     */
    fun classifyRisk(packageName: String, isSystemApp: Boolean): Pair<AccessibilityRiskLevel, String> {
        val lowerPkg = packageName.lowercase()

        // 1. Official Google / AOSP Accessibility Suite
        if (TRUSTED_ACCESSIBILITY_PACKAGES.contains(lowerPkg) || (isSystemApp && lowerPkg.startsWith("com.google.android.accessibility"))) {
            return Pair(
                AccessibilityRiskLevel.SAFE,
                "Официальный системный сервис доступности Android (TalkBack / Switch Access)."
            )
        }

        // 2. Vendor OEM pre-installed service
        if (isSystemApp || KNOWN_OEM_ACCESSIBILITY.contains(lowerPkg)) {
            return Pair(
                AccessibilityRiskLevel.MEDIUM,
                "Системный сервис спецвозможностей производителя устройства."
            )
        }

        // 3. Known password manager or autofill tool
        if (TrustedAppManager.KNOWN_PASSWORD_MANAGERS.contains(lowerPkg)) {
            return Pair(
                AccessibilityRiskLevel.CRITICAL,
                "Менеджер паролей (автозаполнение). Рекомендуется проверить и добавить в доверенные, чтобы снять штраф."
            )
        }

        // 4. Third-party non-system app -> CRITICAL RISK
        return Pair(
            AccessibilityRiskLevel.CRITICAL,
            "КРИТИЧЕСКАЯ УГРОЗА: Стороннее приложение имеет доступ к экрану, может читать переписку в защищенных мессенджерах (Telegram, Signal) и перехватывать нажатия!"
        )
    }

    /**
     * Queries active Accessibility Services from Android Settings.
     */
    fun getActiveAccessibilityServices(
        context: Context,
        trustedServices: Set<String> = emptySet()
    ): List<AccessibilityServiceApp> {
        val raw = try {
            Settings.Secure.getString(context.contentResolver, SETTING_ENABLED_ACCESSIBILITY_SERVICES)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read $SETTING_ENABLED_ACCESSIBILITY_SERVICES: ${e.message}")
            null
        }

        val parsedComponents = parseEnabledServices(raw)
        if (parsedComponents.isEmpty()) return emptyList()

        val pm = context.packageManager
        val apps = mutableListOf<AccessibilityServiceApp>()

        for ((pkgName, serviceName) in parsedComponents) {
            val isTrusted = trustedServices.contains(pkgName.lowercase().trim())
            try {
                val appInfo = pm.getApplicationInfo(pkgName, 0)
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val appName = pm.getApplicationLabel(appInfo).toString()
                val (riskLevel, riskDesc) = classifyRisk(pkgName, isSystem)
                val isDeepSleeping = AppDormancyHelper.isAppInDeepSleep(context, pkgName)

                val finalRiskLevel = if (isDeepSleeping) AccessibilityRiskLevel.SAFE else riskLevel
                val finalRiskDesc = if (isDeepSleeping) {
                    "Приложение находится в глубоком сне (заморожено системой). Спецвозможности неактивны в фоновом режиме."
                } else {
                    riskDesc
                }

                apps.add(
                    AccessibilityServiceApp(
                        packageName = pkgName,
                        appName = appName,
                        serviceName = serviceName,
                        isSystemApp = isSystem,
                        riskLevel = finalRiskLevel,
                        riskDescription = finalRiskDesc,
                        isTrusted = isTrusted,
                        isDeepSleeping = isDeepSleeping
                    )
                )
            } catch (e: PackageManager.NameNotFoundException) {
                apps.add(
                    AccessibilityServiceApp(
                        packageName = pkgName,
                        appName = pkgName,
                        serviceName = serviceName,
                        isSystemApp = false,
                        riskLevel = AccessibilityRiskLevel.SAFE,
                        riskDescription = "Пакет удален или недоступен. Угроза отсутствует.",
                        isTrusted = isTrusted,
                        isDeepSleeping = true
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Error resolving accessibility package $pkgName: ${e.message}")
            }
        }

        return apps.sortedBy {
            when (it.riskLevel) {
                AccessibilityRiskLevel.CRITICAL -> 0
                AccessibilityRiskLevel.MEDIUM -> 1
                AccessibilityRiskLevel.SAFE -> 2
            }
        }
    }

    /**
     * Opens system Accessibility Settings so user can revoke permissions in 1 tap.
     */
    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open accessibility settings: ${e.message}")
        }
    }
}
