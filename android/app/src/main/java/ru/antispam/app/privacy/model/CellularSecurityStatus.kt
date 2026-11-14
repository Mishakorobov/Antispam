package ru.antispam.app.privacy.model

/**
 * Mobile cellular generation categories and risk assessment.
 */
enum class CellularGeneration(
    val code: String,
    val title: String,
    val isSecure: Boolean,
    val riskDescription: String
) {
    GEN_2G(
        code = "2G",
        title = "2G (GSM / GPRS / EDGE)",
        isSecure = false,
        riskDescription = "Критический риск: устаревший стандарт без взаимной аутентификации вышек. Шифрование A5/1 взламывается в реальном времени, либо трафик передается в открытом виде (A5/0). Высокая уязвимость к ложным базовым станциям (IMSI-Catcher)."
    ),
    GEN_3G(
        code = "3G",
        title = "3G (UMTS / HSPA / CDMA)",
        isSecure = true,
        riskDescription = "Умеренная защита: взаимная аутентификация вышки и абонента поддерживается, но протокол постепенно выводится операторами из эксплуатации."
    ),
    GEN_4G_LTE(
        code = "4G",
        title = "4G (LTE / LTE-A)",
        isSecure = true,
        riskDescription = "Надежная защита: криптографическое шифрование трафика (AES / SNOW 3G), защита от подмены базовых станций в штатном режиме."
    ),
    GEN_5G(
        code = "5G",
        title = "5G (NR SA / NSA)",
        isSecure = true,
        riskDescription = "Максимальная защита: стойкое шифрование абонентских идентификаторов (SUPI/SUCI), защита от радиоперехвата и атак понижения."
    ),
    UNKNOWN(
        code = "UNKNOWN",
        title = "Нет активного сотового подключения",
        isSecure = true,
        riskDescription = "Сотовая сеть неактивна или соединение работает по Wi-Fi."
    )
}

/**
 * Risk level of the current cellular environment.
 */
enum class CellularRiskLevel {
    SAFE,       // Connected to 4G/5G, or cellular inactive
    WARNING,    // 3G network active or legacy protocol
    CRITICAL    // Currently active connection on 2G (potential IMSI-Catcher downgrade attack!)
}

/**
 * Comprehensive security audit report for cellular connection.
 */
data class CellularSecurityReport(
    val generation: CellularGeneration = CellularGeneration.UNKNOWN,
    val networkTypeName: String = "Неизвестно",
    val is2GActive: Boolean = false,
    val isNullCipherAlertSupported: Boolean = false,
    val isNullCipherAlertEnabled: Boolean? = null,
    val riskLevel: CellularRiskLevel = CellularRiskLevel.SAFE,
    val summary: String = "",
    val recommendations: List<String> = emptyList(),
    val lastAuditTimestamp: Long = 0L
)
