package ru.antispam.app.privacy

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log

enum class PrivacyRiskLevel {
    CRITICAL, // Targeted surveillance superapp with READ_PHONE_STATE + RECORD_AUDIO
    HIGH,     // Legitimate messenger with line access OR Call Log / Accessibility harvester
    MEDIUM,   // RECORD_AUDIO only
    SAFE      // System dialer or safe app
}

enum class PrivacyCategory {
    SUPERAPP_SURVEILLANCE, // 🔴 Повышенное внимание: супераппы и SDK со скрытой телеметрией (ru.vk.max, etc.)
    TRUSTED_MESSENGER,     // 🟡 Легитимные мессенджеры с доступом к линии (Telegram, WhatsApp)
    AUDIO_ACCESS_APP,      // 🎙️ Доступ к микрофону: утилиты, диктофоны, тюнеры, голосовой поиск
    SYSTEM_DIALER          // 🟢 Системные компоненты: стандартный номеронабиратель (Google/Samsung)
}

data class AppPrivacyRisk(
    val appName: String,
    val packageName: String,
    val riskLevel: PrivacyRiskLevel,
    val category: PrivacyCategory,
    val hasReadPhoneState: Boolean,
    val hasRecordAudio: Boolean,
    val hasCallLog: Boolean,
    val hasAccessibility: Boolean,
    val riskDescription: String,
    val isSystemApp: Boolean = false,
    val isDeepSleeping: Boolean = false
)

object PrivacyScannerHelper {

    private const val TAG = "PrivacyScannerHelper"

    private const val PERM_READ_PHONE_STATE = "android.permission.READ_PHONE_STATE"
    private const val PERM_RECORD_AUDIO = "android.permission.RECORD_AUDIO"
    private const val PERM_READ_CALL_LOG = "android.permission.READ_CALL_LOG"
    private const val PERM_PROCESS_OUTGOING = "android.permission.PROCESS_OUTGOING_CALLS"
    private const val PERM_ACCESSIBILITY = "android.permission.BIND_ACCESSIBILITY_SERVICE"

    // Known surveillance superapps and telemetry SDKs that harvest call events
    val KNOWN_SUPERAPPS = setOf(
        "ru.vk.max",
        "ru.vk.store",
        "com.vkontakte.android"
    )

    // Known standard telephony packages (Presumption of Innocence: excluded from threat alert)
    val KNOWN_SYSTEM_DIALERS = setOf(
        "com.google.android.dialer",
        "com.samsung.android.incallui",
        "com.samsung.android.dialer",
        "com.android.dialer",
        "com.android.phone",
        "com.android.server.telecom",
        "com.miui.telephony",
        "com.coloros.telephony"
    )

    // Known legitimate messengers where user chooses whether to revoke line access
    val KNOWN_TRUSTED_MESSENGERS = setOf(
        "org.telegram.messenger",
        "com.whatsapp",
        "com.viber.voip",
        "org.thoughtcrime.securesms"
    )

    /**
     * Scans all installed packages on the device and assesses their potential risk
     * of eavesdropping on phone calls or harvesting call metadata, applying
     * Presumption of Innocence to system dialers.
     */
    fun scanInstalledApps(context: Context): List<AppPrivacyRisk> {
        val pm = context.packageManager
        val myPackage = context.packageName
        val results = mutableListOf<AppPrivacyRisk>()

        try {
            val installedPackages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            val enabledAccessibilityPackages = try {
                val raw = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                AccessibilityAuditor.parseEnabledServices(raw).map { it.first }.toSet()
            } catch (_: Exception) {
                emptySet()
            }

            for (pkg in installedPackages) {
                val pkgName = pkg.packageName
                if (pkgName == myPackage) continue

                val appInfo = pkg.applicationInfo ?: continue
                val isSystem = (appInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
                val isKnownSuperapp = pkgName.lowercase() in KNOWN_SUPERAPPS

                // USER REQUIREMENT: "Системные не показывай вовсе."
                // Pre-installed system apps are excluded from audit unless they are blacklisted superapps
                if (isSystem && !isKnownSuperapp) {
                    continue
                }

                // Phase 13.5 & antispam_fix.md 7.4: Audit ONLY actively GRANTED runtime permissions!
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

                val hasPhoneState = (PERM_READ_PHONE_STATE in grantedPerms) &&
                        (pm.checkPermission(PERM_READ_PHONE_STATE, pkgName) == PackageManager.PERMISSION_GRANTED)
                val hasRecordAudio = (PERM_RECORD_AUDIO in grantedPerms) &&
                        (pm.checkPermission(PERM_RECORD_AUDIO, pkgName) == PackageManager.PERMISSION_GRANTED)
                val hasCallLog = ((PERM_READ_CALL_LOG in grantedPerms) && (pm.checkPermission(PERM_READ_CALL_LOG, pkgName) == PackageManager.PERMISSION_GRANTED)) ||
                        ((PERM_PROCESS_OUTGOING in grantedPerms) && (pm.checkPermission(PERM_PROCESS_OUTGOING, pkgName) == PackageManager.PERMISSION_GRANTED))
                val hasAccessibility = pkgName in enabledAccessibilityPackages

                // Exclude any app where user has not granted any of these permissions (or revoked all of them)
                if (!hasPhoneState && !hasRecordAudio && !hasCallLog && !hasAccessibility) {
                    continue
                }

                val appName = try {
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    pkgName
                }

                val risk = classifyRisk(
                    appName = appName,
                    packageName = pkgName,
                    hasPhoneState = hasPhoneState,
                    hasRecordAudio = hasRecordAudio,
                    hasCallLog = hasCallLog,
                    hasAccessibility = hasAccessibility,
                    isSystem = isSystem
                )

                val isDeepSleeping = AppDormancyHelper.isAppInDeepSleep(context, pkgName)
                val finalRisk = if (isDeepSleeping) {
                    risk.copy(
                        riskLevel = PrivacyRiskLevel.SAFE,
                        riskDescription = "Приложение находится в глубоком сне (Samsung One UI). Фоновая активность заморожена.",
                        isDeepSleeping = true
                    )
                } else {
                    risk
                }

                // Do not show safe system apps in audit list
                if (finalRisk.category != PrivacyCategory.SYSTEM_DIALER) {
                    results.add(finalRisk)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning installed packages: ${e.message}", e)
        }

        // Sort: CRITICAL (Superapps) first, then HIGH (Trusted messengers), then SAFE (System)
        return results.sortedWith(
            compareBy<AppPrivacyRisk> { it.riskLevel.ordinal }
                .thenBy { it.category.ordinal }
                .thenBy { it.appName.lowercase() }
        )
    }

    fun classifyRisk(
        appName: String,
        packageName: String,
        hasPhoneState: Boolean,
        hasRecordAudio: Boolean,
        hasCallLog: Boolean,
        hasAccessibility: Boolean,
        isSystem: Boolean
    ): AppPrivacyRisk {
        val cleanPkg = packageName.lowercase()

        // 1. Presumption of Innocence: System dialer / InCallUI is necessary for calling and safe
        val isDialer = cleanPkg in KNOWN_SYSTEM_DIALERS ||
                ((isSystem || cleanPkg.startsWith("com.android.")) &&
                        (cleanPkg.contains("dialer") || cleanPkg.contains("incallui") || cleanPkg.contains("telecom") || cleanPkg.contains("telephony")))

        if (isDialer) {
            return AppPrivacyRisk(
                appName = appName,
                packageName = packageName,
                riskLevel = PrivacyRiskLevel.SAFE,
                category = PrivacyCategory.SYSTEM_DIALER,
                hasReadPhoneState = hasPhoneState,
                hasRecordAudio = hasRecordAudio,
                hasCallLog = hasCallLog,
                hasAccessibility = hasAccessibility,
                riskDescription = "Системный компонент телефонии: необходим для совершения стандартных вызовов. Исключен из угроз.",
                isSystemApp = isSystem
            )
        }

        // 2. Targeted Surveillance Superapps (ru.vk.max, etc.)
        if (cleanPkg in KNOWN_SUPERAPPS) {
            return AppPrivacyRisk(
                appName = appName,
                packageName = packageName,
                riskLevel = PrivacyRiskLevel.CRITICAL,
                category = PrivacyCategory.SUPERAPP_SURVEILLANCE,
                hasReadPhoneState = hasPhoneState,
                hasRecordAudio = hasRecordAudio,
                hasCallLog = hasCallLog,
                hasAccessibility = hasAccessibility,
                riskDescription = "Суперапп со связкой Телефон + Микрофон: высокий риск скрытой фиксации вызовов и записи разговора. Рекомендуется отозвать доступ к Телефону.",
                isSystemApp = isSystem
            )
        }

        // 3. System & OEM apps (Google, Samsung, OEM, AOSP, Android Auto, Google Photos) - Presumption of Innocence
        // Standard pre-installed OS components must NEVER be classified as SUPERAPP_SURVEILLANCE
        val isOemOrSystemApp = isSystem ||
                cleanPkg.startsWith("com.android.") ||
                cleanPkg.startsWith("com.google.android.") ||
                cleanPkg.startsWith("com.samsung.") ||
                cleanPkg.startsWith("com.sec.") ||
                cleanPkg.startsWith("com.microsoft.appmanager")

        if (isOemOrSystemApp) {
            return AppPrivacyRisk(
                appName = appName,
                packageName = packageName,
                riskLevel = PrivacyRiskLevel.SAFE,
                category = PrivacyCategory.SYSTEM_DIALER,
                hasReadPhoneState = hasPhoneState,
                hasRecordAudio = hasRecordAudio,
                hasCallLog = hasCallLog,
                hasAccessibility = hasAccessibility,
                riskDescription = if (isDialer) {
                    "Системный компонент телефонии: необходим для совершения стандартных вызовов. Исключен из угроз."
                } else {
                    "Предустановленное системное приложение Android/Samsung. Доступ к разрешениям является штатным."
                },
                isSystemApp = isSystem
            )
        }

        // 4. Trusted Messengers (Telegram, WhatsApp)
        val isTrustedMessenger = cleanPkg in KNOWN_TRUSTED_MESSENGERS

        val (level, category, desc) = when {
            isTrustedMessenger && hasPhoneState && hasRecordAudio -> {
                Triple(
                    PrivacyRiskLevel.HIGH,
                    PrivacyCategory.TRUSTED_MESSENGER,
                    "Мессенджер с доступом к линии: разрешение Телефон используется для паузы VoIP при вызове. Вы можете отозвать его, если хотите полную изоляцию."
                )
            }
            hasAccessibility && hasRecordAudio -> {
                Triple(
                    PrivacyRiskLevel.CRITICAL,
                    PrivacyCategory.SUPERAPP_SURVEILLANCE,
                    "Критический вектор: Спецвозможности (Accessibility) позволяют перехватывать аудиопоток в обход системных ограничений."
                )
            }
            hasPhoneState && hasRecordAudio -> {
                Triple(
                    PrivacyRiskLevel.CRITICAL,
                    PrivacyCategory.SUPERAPP_SURVEILLANCE,
                    "Связка Телефон + Микрофон: стороннее приложение может инициировать запись звука точно в момент сотового вызова."
                )
            }
            hasCallLog -> {
                Triple(
                    PrivacyRiskLevel.HIGH,
                    PrivacyCategory.TRUSTED_MESSENGER,
                    "Сбор метаданных: доступ к журналу звонков позволяет выгружать историю и социальный граф контактов."
                )
            }
            isTrustedMessenger && hasRecordAudio -> {
                Triple(
                    PrivacyRiskLevel.MEDIUM,
                    PrivacyCategory.TRUSTED_MESSENGER,
                    "Легитимный мессенджер с доступом к микрофону."
                )
            }
            hasRecordAudio -> {
                Triple(
                    PrivacyRiskLevel.MEDIUM,
                    PrivacyCategory.AUDIO_ACCESS_APP,
                    "Доступ к микрофону: запись аудио в активном режиме (распознавание речи, тюнер, аудиозапись)."
                )
            }
            else -> {
                Triple(
                    PrivacyRiskLevel.SAFE,
                    PrivacyCategory.SYSTEM_DIALER,
                    "Безопасное приложение."
                )
            }
        }

        return AppPrivacyRisk(
            appName = appName,
            packageName = packageName,
            riskLevel = level,
            category = category,
            hasReadPhoneState = hasPhoneState,
            hasRecordAudio = hasRecordAudio,
            hasCallLog = hasCallLog,
            hasAccessibility = hasAccessibility,
            riskDescription = desc,
            isSystemApp = isSystem
        )
    }

    /**
     * Checks if a permission is currently granted to the specified package.
     */
    fun isPermissionGranted(context: Context, packageName: String, permission: String): Boolean {
        return try {
            val pm = context.packageManager
            pm.checkPermission(permission, packageName) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Opens system Application Details Settings for the target package.
     */
    fun openAppSettings(context: Context, packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app settings for $packageName: ${e.message}")
        }
    }
}
