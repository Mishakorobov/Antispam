package ru.antispam.app.privacy.model

/**
 * Standard 3GPP / GSM call forwarding categories and USSD codes.
 */
enum class CallForwardingType(
    val ussdQueryCode: String,
    val ussdCancelCode: String,
    val title: String,
    val description: String
) {
    UNCONDITIONAL(
        ussdQueryCode = "*#21#",
        ussdCancelCode = "##21#",
        title = "Безусловная переадресация (*21*)",
        description = "Все входящие вызовы перенаправляются сразу. Телефон не звонит. Критический риск перехвата 2FA и звонков банков!"
    ),
    NO_REPLY(
        ussdQueryCode = "*#61#",
        ussdCancelCode = "##61#",
        title = "При отсутствии ответа (*61*)",
        description = "Вызовы перенаправляются, если вы не подняли трубку в течение 15–30 секунд."
    ),
    UNREACHABLE(
        ussdQueryCode = "*#62#",
        ussdCancelCode = "##62#",
        title = "При недоступности (*62*)",
        description = "Вызовы перенаправляются, когда телефон выключен, разряжен или находится вне зоны действия сети."
    ),
    BUSY(
        ussdQueryCode = "*#67#",
        ussdCancelCode = "##67#",
        title = "При занятости (*67*)",
        description = "Вызовы перенаправляются, когда линия уже занята другим входящим разговором."
    ),
    ALL_CONDITIONAL(
        ussdQueryCode = "*#004#",
        ussdCancelCode = "##004#",
        title = "Все условные переадресации (*#004#)",
        description = "Проверка и отмена всех условных переадресаций (недоступность, занятость, неотвеченный вызов)."
    ),
    ALL_FORWARDING(
        ussdQueryCode = "*#002#",
        ussdCancelCode = "##002#",
        title = "Все переадресации (Глобальный сброс ##002#)",
        description = "Полная очистка и аннулирование всех условных и безусловных переадресаций оператора связи."
    )
}

/**
 * Risk classification for call forwarding states.
 */
enum class ForwardingRiskLevel {
    SAFE,       // No forwarding active or user verified clean state
    WARNING,    // Conditional forwarding active (e.g. voicemail or carrier assistant)
    CRITICAL    // Unconditional forwarding active or rogue divert detected
}

/**
 * Represents a single call forwarding rule or query target.
 */
data class CallForwardingRule(
    val type: CallForwardingType,
    val targetNumber: String? = null,
    val isEnabled: Boolean = false,
    val riskLevel: ForwardingRiskLevel = ForwardingRiskLevel.SAFE
)

/**
 * Summary audit report of device telephony forwarding state.
 */
data class CallForwardingReport(
    val isCarrierIndicatorActive: Boolean = false,
    val hasActiveForwarding: Boolean = false,
    val riskLevel: ForwardingRiskLevel = ForwardingRiskLevel.SAFE,
    val activeRules: List<CallForwardingRule> = emptyList(),
    val lastAuditTimestamp: Long = 0L,
    val lastResetTimestamp: Long = 0L,
    val summary: String = ""
)
