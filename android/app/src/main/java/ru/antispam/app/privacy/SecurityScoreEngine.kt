package ru.antispam.app.privacy

import ru.antispam.app.privacy.model.PrivacyAuditInput
import ru.antispam.app.privacy.model.SecurityIssue
import ru.antispam.app.privacy.model.SecurityIssueSeverity
import ru.antispam.app.privacy.model.SecurityIssueType
import ru.antispam.app.privacy.model.SecurityScoreReport

/**
 * SecurityScoreEngine: Computes the unified device Privacy & Protection Index (0–100%).
 *
 * Weight Distribution (Base: 100 points):
 * 1. Spam Registry Freshness (synced within 48h): +30 points
 * 2. Real-time Call Microphone Inspection (MicGuard active): +20 points
 * 3. Notification Spy Protection (no rogue NotificationListeners): +20 points
 * 4. 2FA OTP Auto-Shredder (ClipboardGuard active): +15 points
 * 5. Network Integrity & MITM Protection (no rogue user Root CAs): +15 points
 *
 * Active Threat Deductions:
 * - Rogue Accessibility Services active: -10 points
 * - Surveillance Superapps cataloging installed VPNs: -10 points
 * - Stealth Non-GPS Geo-Triangulation active: -5 points
 */
object SecurityScoreEngine {

    const val WEIGHT_REGISTRY = 30
    const val WEIGHT_MIC_GUARD = 20
    const val WEIGHT_NOTIFICATION_GUARD = 20
    const val WEIGHT_CLIPBOARD_GUARD = 15
    const val WEIGHT_MITM_GUARD = 15

    const val PENALTY_ACCESSIBILITY = 10
    const val PENALTY_SUPERAPP_VPN = 10
    const val PENALTY_STEALTH_GEO = 5

    /**
     * Calculates the security score report based on the provided audit inputs.
     */
    fun calculateScore(input: PrivacyAuditInput): SecurityScoreReport {
        var score = 0
        val issues = mutableListOf<SecurityIssue>()
        val breakdown = mutableMapOf<String, Int>()

        // 1. Registry Freshness (+30 pts)
        if (input.isRegistryUpToDate) {
            score += WEIGHT_REGISTRY
            breakdown["Реестр Минцифры"] = WEIGHT_REGISTRY
        } else {
            breakdown["Реестр Минцифры"] = 0
            issues.add(
                SecurityIssue(
                    id = "registry_outdated",
                    type = SecurityIssueType.REGISTRY_OUTDATED,
                    title = "Спам-база устарела",
                    description = "Реестр номеров не обновлялся более 48 часов. Возможен пропуск звонков новых спам-пулов.",
                    severity = SecurityIssueSeverity.HIGH,
                    scoreDeduction = WEIGHT_REGISTRY,
                    actionLabel = "Обновить базу"
                )
            )
        }

        // 2. Microphone & Audio Guard (+20 pts)
        if (input.isMicGuardEnabled) {
            score += WEIGHT_MIC_GUARD
            breakdown["Защита микрофона"] = WEIGHT_MIC_GUARD
        } else {
            breakdown["Защита микрофона"] = 0
            issues.add(
                SecurityIssue(
                    id = "mic_guard_disabled",
                    type = SecurityIssueType.MIC_GUARD_DISABLED,
                    title = "Защита линии отключена",
                    description = "Фоновый инспектор MicGuard выключен. Разговоры уязвимы к записи сторонними супераппами.",
                    severity = SecurityIssueSeverity.HIGH,
                    scoreDeduction = WEIGHT_MIC_GUARD,
                    actionLabel = "Включить защиту"
                )
            )
        }

        // 3. Notification Spies (+20 pts)
        if (!input.hasNotificationSpies) {
            score += WEIGHT_NOTIFICATION_GUARD
            breakdown["Защита СМС и 2FA"] = WEIGHT_NOTIFICATION_GUARD
        } else {
            breakdown["Защита СМС и 2FA"] = 0
            issues.add(
                SecurityIssue(
                    id = "notification_spy_detected",
                    type = SecurityIssueType.NOTIFICATION_SPY_DETECTED,
                    title = "Шпион шторки уведомлений",
                    description = "Обнаружены сторонние приложения с доступом к чтению банковских СМС и 2FA-кодов.",
                    severity = SecurityIssueSeverity.CRITICAL,
                    scoreDeduction = WEIGHT_NOTIFICATION_GUARD,
                    actionLabel = "Отозвать доступ"
                )
            )
        }

        // 4. Clipboard 2FA Auto-Shredder (+15 pts)
        if (input.isClipboardGuardEnabled) {
            score += WEIGHT_CLIPBOARD_GUARD
            breakdown["Шредер буфера 2FA"] = WEIGHT_CLIPBOARD_GUARD
        } else {
            breakdown["Шредер буфера 2FA"] = 0
            issues.add(
                SecurityIssue(
                    id = "clipboard_guard_disabled",
                    type = SecurityIssueType.CLIPBOARD_GUARD_DISABLED,
                    title = "Шредер буфера выключен",
                    description = "Пароли и 2FA-коды остаются в буфере обмена и могут считываться открытыми супераппами.",
                    severity = SecurityIssueSeverity.MEDIUM,
                    scoreDeduction = WEIGHT_CLIPBOARD_GUARD,
                    actionLabel = "Включить авто-шредер"
                )
            )
        }

        // 5. Network MITM Root CA (+15 pts)
        if (!input.hasMitmRootCa) {
            score += WEIGHT_MITM_GUARD
            breakdown["Сертификаты (MITM)"] = WEIGHT_MITM_GUARD
        } else {
            breakdown["Сертификаты (MITM)"] = 0
            issues.add(
                SecurityIssue(
                    id = "mitm_root_ca_detected",
                    type = SecurityIssueType.MITM_ROOT_CA_DETECTED,
                    title = "Угроза перехвата трафика (MITM)",
                    description = "В хранилище найден пользовательский Root CA. Весь HTTPS-трафик может расшифровываться.",
                    severity = SecurityIssueSeverity.CRITICAL,
                    scoreDeduction = WEIGHT_MITM_GUARD,
                    actionLabel = "Удалить сертификат"
                )
            )
        }

        // Deductions for Active High-Risk Threats
        if (input.hasCriticalAccessibility) {
            score -= PENALTY_ACCESSIBILITY
            issues.add(
                SecurityIssue(
                    id = "critical_accessibility",
                    type = SecurityIssueType.CRITICAL_ACCESSIBILITY_DETECTED,
                    title = "Шпион экрана и клавиатуры",
                    description = "Стороннее приложение включено в Спецвозможности и может считывать переписку в Telegram и балансы.",
                    severity = SecurityIssueSeverity.CRITICAL,
                    scoreDeduction = PENALTY_ACCESSIBILITY,
                    actionLabel = "Отозвать спецвозможности"
                )
            )
        }

        if (input.hasVpnInventoryExposed) {
            score -= PENALTY_SUPERAPP_VPN
            issues.add(
                SecurityIssue(
                    id = "superapp_vpn_exposed",
                    type = SecurityIssueType.SUPERAPP_VPN_EXPOSED,
                    title = "Угроза сканирования VPN",
                    description = "Супераппы с QUERY_ALL_PACKAGES каталогизируют установленные средства обхода блокировок.",
                    severity = SecurityIssueSeverity.HIGH,
                    scoreDeduction = PENALTY_SUPERAPP_VPN,
                    actionLabel = "Ограничить суперапп"
                )
            )
        }

        if (input.hasStealthGeoTrackers) {
            score -= PENALTY_STEALTH_GEO
            issues.add(
                SecurityIssue(
                    id = "stealth_geo_detected",
                    type = SecurityIssueType.STEALTH_GEO_DETECTED,
                    title = "Скрытая триангуляция без GPS",
                    description = "Приложения определяют физический адрес до квартиры через сканирование Wi-Fi BSSID и вышек связи.",
                    severity = SecurityIssueSeverity.HIGH,
                    scoreDeduction = PENALTY_STEALTH_GEO,
                    actionLabel = "Настроить геолокацию"
                )
            )
        }

        val finalScore = score.coerceIn(0, 100)

        val (title, desc) = when {
            finalScore >= 80 -> Pair(
                "Надежно защищен",
                "Все ключевые рубежи обороны приватности активны. Устройство защищено от слежки и спама."
            )
            finalScore >= 50 -> Pair(
                "Требуется внимание",
                "Обнаружены уязвимости, снижающие уровень приватности. Рекомендуется активировать защиту."
            )
            else -> Pair(
                "Критический риск",
                "Обнаружены серьезные бреши в безопасности! Устройство уязвимо к скрытой слежке и перехвату данных."
            )
        }

        // Sort issues by severity: CRITICAL first, then HIGH, then MEDIUM
        val sortedIssues = issues.sortedBy {
            when (it.severity) {
                SecurityIssueSeverity.CRITICAL -> 0
                SecurityIssueSeverity.HIGH -> 1
                SecurityIssueSeverity.MEDIUM -> 2
            }
        }

        return SecurityScoreReport(
            score = finalScore,
            statusTitle = title,
            statusDescription = desc,
            issues = sortedIssues,
            categoryBreakdown = breakdown
        )
    }
}
