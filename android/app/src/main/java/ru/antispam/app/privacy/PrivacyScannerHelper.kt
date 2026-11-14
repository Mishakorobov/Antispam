package ru.antispam.app.privacy

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log

enum class PrivacyRiskLevel {
    CRITICAL, // Targeted surveillance superapp with READ_PHONE_STATE + RECORD_AUDIO or Notification/Accessibility spy
    HIGH,     // Legitimate messenger with line access OR Call Log / Location harvester
    MEDIUM,   // RECORD_AUDIO or QUERY_ALL_PACKAGES only
    SAFE      // System dialer or safe app
}

enum class PrivacyCategory {
    SUPERAPP_SURVEILLANCE, // 🔴 Повышенное внимание: супераппы и SDK со скрытой телеметрией (ru.vk.max, etc.)
    TRUSTED_MESSENGER,     // 🟡 Легитимные мессенджеры с доступом к линии (Telegram, WhatsApp)
    AUDIO_ACCESS_APP,      // 🎙️ Доступ к микрофону: утилиты, диктофоны, тюнеры, голосовой поиск
    SYSTEM_DIALER          // 🟢 Системные компоненты: стандартный номеронабиратель (Google/Samsung)
}

enum class AppThreatActionType {
    MANAGE_PERMISSIONS,           // Открыть системные настройки разрешений
    REVOKE_NOTIFICATION_LISTENER, // Открыть настройки доступа к уведомлениям
    MANAGE_ACCESSIBILITY,         // Открыть спецвозможности
    PUT_IN_DEEP_SLEEP,            // Открыть настройки глубокого сна (Samsung One UI)
    TRUST_APP                     // Добавить в доверенные
}

data class AppPrivacyRisk(
    val appName: String,
    val packageName: String,
    val riskLevel: PrivacyRiskLevel,
    val category: PrivacyCategory,
    val hasReadPhoneState: Boolean = false,
    val hasRecordAudio: Boolean = false,
    val hasCallLog: Boolean = false,
    val hasAccessibility: Boolean = false,
    val hasNotificationListener: Boolean = false,
    val hasQueryAllPackages: Boolean = false,
    val hasStealthGeo: Boolean = false,
    val hasOverlay: Boolean = false,
    val threatBadges: List<String> = emptyList(),
    val primaryActionType: AppThreatActionType = AppThreatActionType.MANAGE_PERMISSIONS,
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
    private const val PERM_FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
    private const val PERM_COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION"
    private const val PERM_SYSTEM_ALERT_WINDOW = "android.permission.SYSTEM_ALERT_WINDOW"

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
     * across ALL vectors: Phone/Audio, Notification eavesdropping, Accessibility keylogging,
     * Location triangulation, SuperApp inventory, and Overlays.
     */
    fun scanInstalledApps(context: Context): List<AppPrivacyRisk> {
        val pm = context.packageManager
        val myPackage = context.packageName
        val results = mutableListOf<AppPrivacyRisk>()

        try {
            val installedPackages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            val enabledListeners = try {
                val raw = Settings.Secure.getString(context.contentResolver, NotificationListenerAuditor.SETTING_ENABLED_LISTENERS)
                NotificationListenerAuditor.parseEnabledListeners(raw).map { it.first.lowercase() }.toSet()
            } catch (_: Exception) {
                emptySet()
            }
            val enabledAccessibility = try {
                val raw = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                AccessibilityAuditor.parseEnabledServices(raw).map { it.first.lowercase() }.toSet()
            } catch (_: Exception) {
                emptySet()
            }

            for (pkg in installedPackages) {
                val pkgName = pkg.packageName
                if (pkgName == myPackage) continue
                val cleanPkg = pkgName.lowercase()

                val appInfo = pkg.applicationInfo ?: continue
                val isSystem = (appInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
                val isKnownSuperapp = cleanPkg in KNOWN_SUPERAPPS

                // USER REQUIREMENT: System & OEM apps are excluded completely from threat audit
                val isVendorOrSystem = isSystem ||
                        cleanPkg.startsWith("com.samsung.") ||
                        cleanPkg.startsWith("com.sec.") ||
                        cleanPkg.startsWith("com.google.android.") ||
                        cleanPkg.startsWith("com.android.") ||
                        cleanPkg == "com.microsoft.appmanager" ||
                        cleanPkg in KNOWN_SYSTEM_DIALERS

                if (isVendorOrSystem && !isKnownSuperapp) {
                    continue
                }

                // USER REQUIREMENT 4: Do not display apps that are currently in deep sleep
                val isDeepSleeping = AppDormancyHelper.isAppInDeepSleep(context, pkgName)
                if (isDeepSleeping) {
                    continue
                }

                // Check granted runtime permissions
                val reqPerms = pkg.requestedPermissions ?: emptyArray()
                val reqFlags = pkg.requestedPermissionsFlags ?: IntArray(0)
                val grantedPerms = mutableSetOf<String>()
                for (i in reqPerms.indices) {
                    if (i < reqFlags.size && (reqFlags[i] and android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                        grantedPerms.add(reqPerms[i])
                    }
                }

                val hasPhoneState = (PERM_READ_PHONE_STATE in grantedPerms) &&
                        (pm.checkPermission(PERM_READ_PHONE_STATE, pkgName) == PackageManager.PERMISSION_GRANTED)
                val hasRecordAudio = (PERM_RECORD_AUDIO in grantedPerms) &&
                        (pm.checkPermission(PERM_RECORD_AUDIO, pkgName) == PackageManager.PERMISSION_GRANTED)
                val hasCallLog = ((PERM_READ_CALL_LOG in grantedPerms) && (pm.checkPermission(PERM_READ_CALL_LOG, pkgName) == PackageManager.PERMISSION_GRANTED)) ||
                        ((PERM_PROCESS_OUTGOING in grantedPerms) && (pm.checkPermission(PERM_PROCESS_OUTGOING, pkgName) == PackageManager.PERMISSION_GRANTED))
                val hasCoarseLocation = ((PERM_COARSE_LOCATION in grantedPerms) || (PERM_FINE_LOCATION in grantedPerms)) &&
                        ((pm.checkPermission(PERM_COARSE_LOCATION, pkgName) == PackageManager.PERMISSION_GRANTED) ||
                         (pm.checkPermission(PERM_FINE_LOCATION, pkgName) == PackageManager.PERMISSION_GRANTED))

                // Special system permissions and listeners
                val hasNotificationListener = cleanPkg in enabledListeners
                val hasAccessibility = cleanPkg in enabledAccessibility
                val hasQueryAll = (SuperAppInspector.PERM_QUERY_ALL_PACKAGES in reqPerms) && !isSystem
                val hasOverlay = (PERM_SYSTEM_ALERT_WINDOW in grantedPerms) || (PERM_SYSTEM_ALERT_WINDOW in reqPerms)

                // Check if the app poses ANY active threat vector
                val hasAnyThreat = hasPhoneState || hasRecordAudio || hasCallLog || hasCoarseLocation ||
                        hasNotificationListener || hasAccessibility || hasQueryAll || hasOverlay

                // Exclude any app where user has not granted any threat permission (or revoked all of them)
                if (!hasAnyThreat) {
                    continue
                }

                val appName = try {
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    pkgName
                }

                val badges = mutableListOf<String>()
                if (hasNotificationListener) badges.add("Шпион СМС/2FA")
                if (hasAccessibility) badges.add("Спецвозможности (Экран)")
                if (hasQueryAll) badges.add("Инвентарь программ")
                if (hasPhoneState && hasRecordAudio) badges.add("Прослушивание линии")
                else if (hasRecordAudio) badges.add("Микрофон")
                if (hasCallLog) badges.add("Журнал звонков")
                if (hasCoarseLocation) badges.add("Геолокация")
                if (hasOverlay) badges.add("Оверлей окон")

                val primaryAction = when {
                    hasNotificationListener -> AppThreatActionType.REVOKE_NOTIFICATION_LISTENER
                    hasAccessibility -> AppThreatActionType.MANAGE_ACCESSIBILITY
                    hasQueryAll && !hasPhoneState && !hasRecordAudio && !hasCallLog && !hasCoarseLocation -> AppThreatActionType.PUT_IN_DEEP_SLEEP
                    else -> AppThreatActionType.MANAGE_PERMISSIONS
                }

                val (riskLevel, category, riskDesc) = classifyThreat(
                    appName = appName,
                    packageName = pkgName,
                    hasPhoneState = hasPhoneState,
                    hasRecordAudio = hasRecordAudio,
                    hasCallLog = hasCallLog,
                    hasAccessibility = hasAccessibility,
                    hasNotificationListener = hasNotificationListener,
                    hasQueryAll = hasQueryAll,
                    hasCoarseLocation = hasCoarseLocation,
                    hasOverlay = hasOverlay,
                    isSystem = isSystem
                )

                results.add(
                    AppPrivacyRisk(
                        appName = appName,
                        packageName = pkgName,
                        riskLevel = riskLevel,
                        category = category,
                        hasReadPhoneState = hasPhoneState,
                        hasRecordAudio = hasRecordAudio,
                        hasCallLog = hasCallLog,
                        hasAccessibility = hasAccessibility,
                        hasNotificationListener = hasNotificationListener,
                        hasQueryAllPackages = hasQueryAll,
                        hasStealthGeo = hasCoarseLocation,
                        hasOverlay = hasOverlay,
                        threatBadges = badges,
                        primaryActionType = primaryAction,
                        riskDescription = riskDesc,
                        isSystemApp = isSystem,
                        isDeepSleeping = false
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning installed packages: ${e.message}", e)
        }

        return results.sortedWith(
            compareBy<AppPrivacyRisk> { it.riskLevel.ordinal }
                .thenBy { it.category.ordinal }
                .thenBy { it.appName.lowercase() }
        )
    }

    fun classifyThreat(
        appName: String,
        packageName: String,
        hasPhoneState: Boolean,
        hasRecordAudio: Boolean,
        hasCallLog: Boolean,
        hasAccessibility: Boolean,
        hasNotificationListener: Boolean,
        hasQueryAll: Boolean,
        hasCoarseLocation: Boolean,
        hasOverlay: Boolean,
        isSystem: Boolean
    ): Triple<PrivacyRiskLevel, PrivacyCategory, String> {
        val cleanPkg = packageName.lowercase()

        // 1. Blacklisted surveillance superapps
        if (cleanPkg in KNOWN_SUPERAPPS) {
            return Triple(
                PrivacyRiskLevel.CRITICAL,
                PrivacyCategory.SUPERAPP_SURVEILLANCE,
                "КРИТИЧЕСКИЙ РИСК: Коммерческий суперапп со скрытой телеметрией. Ведет сбор данных о вызовах и окружении."
            )
        }

        // 2. Notification listener spy
        if (hasNotificationListener) {
            return Triple(
                PrivacyRiskLevel.CRITICAL,
                PrivacyCategory.SUPERAPP_SURVEILLANCE,
                "КРИТИЧЕСКИЙ РИСК: Приложение читает все входящие уведомления, включая банковские СМС-коды 2FA и переписку."
            )
        }

        // 3. Accessibility keylogger
        if (hasAccessibility) {
            return Triple(
                PrivacyRiskLevel.CRITICAL,
                PrivacyCategory.SUPERAPP_SURVEILLANCE,
                "КРИТИЧЕСКИЙ РИСК: Служба спецвозможностей может перехватывать нажатия клавиатуры и считывать экран."
            )
        }

        // 4. Phone line + Microphone
        if (hasPhoneState && hasRecordAudio) {
            return Triple(
                PrivacyRiskLevel.CRITICAL,
                PrivacyCategory.SUPERAPP_SURVEILLANCE,
                "Связка Телефон + Микрофон: стороннее приложение может инициировать запись звука точно во время звонка."
            )
        }

        // 5. Messengers
        if (cleanPkg in KNOWN_TRUSTED_MESSENGERS) {
            return Triple(
                PrivacyRiskLevel.HIGH,
                PrivacyCategory.TRUSTED_MESSENGER,
                "Мессенджер с доступом к микрофону/вызовам. Разрешения используются штатно для голосовой связи."
            )
        }

        // 6. Call Log harvester
        if (hasCallLog) {
            return Triple(
                PrivacyRiskLevel.HIGH,
                PrivacyCategory.SUPERAPP_SURVEILLANCE,
                "Доступ к журналу звонков: позволяет выгружать историю вызовов и граф социальных контактов."
            )
        }

        // 7. Query all packages (SuperApp catalog scanner)
        if (hasQueryAll) {
            return Triple(
                PrivacyRiskLevel.MEDIUM,
                PrivacyCategory.SUPERAPP_SURVEILLANCE,
                "Тотальный инвентарь ПО (QUERY_ALL_PACKAGES): сканирует установленные программы. Добавьте в глубокий сон для заморозки."
            )
        }

        // 8. Microphone only
        if (hasRecordAudio) {
            return Triple(
                PrivacyRiskLevel.MEDIUM,
                PrivacyCategory.AUDIO_ACCESS_APP,
                "Доступ к микрофону: запись аудио в активном режиме."
            )
        }

        // 9. Coarse Location / Overlay only
        return Triple(
            PrivacyRiskLevel.MEDIUM,
            PrivacyCategory.AUDIO_ACCESS_APP,
            "Доступ к чувствительным системным ресурсам (Геолокация / Оверлей)."
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
