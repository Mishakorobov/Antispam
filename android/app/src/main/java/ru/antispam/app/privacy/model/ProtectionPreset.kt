package ru.antispam.app.privacy.model

/**
 * Deterministic configuration snapshot applied by a protection preset.
 */
data class PresetConfiguration(
    val blockSurveys: Boolean,
    val blockFinancial: Boolean,
    val blockSilentCalls: Boolean,
    val postCallEnabled: Boolean,
    val micGuardEnabled: Boolean,
    val jammerAutoEnabled: Boolean,
    val clipboardGuardEnabled: Boolean,
    val callVaultEnabled: Boolean,
    val deliveryModeActive: Boolean,
    val blockForeignPlus7Active: Boolean
)

/**
 * 1-Click Protection Presets (Уровни паранойи и комплексной защиты).
 *
 * BASIC_COMFORT:
 * - Только блокировка спам-пулов и опросов ВЦИОМ + режим «Жду курьера» (+2 часа).
 * - Фоновые датчики приватности отключены для максимальной экономии энергии и свободы использования.
 *
 * ANTI_EAVESDROPPING:
 * - Все фильтры спама включены, режим обхода курьерами отключен.
 * - Защита от прослушки (MicGuardService) и авто-подавление (MicJammer).
 * - Автоматический шредер одноразовых СМС-паролей 2FA (ClipboardGuard).
 * - Сейф звонков отключен.
 *
 * MAX_BUNKER:
 * - Максимальный уровень паранойи и изоляции.
 * - Все фильтры спама и блокировка зарубежных +7 номеров (+7 6xx/7xx Казахстан).
 * - Все датчики приватности включены (MicGuard, MicJammer, ClipboardGuard).
 * - Сейф звонков (PrivateCallLogVaultManager) активен: звонки стираются из системы в зашифрованный сейф.
 * - Скрытие уведомлений: всплывающие Post-Call уведомления отключены для полной скрытности.
 */
enum class ProtectionPreset(
    val title: String,
    val subtitle: String,
    val description: String
) {
    BASIC_COMFORT(
        title = "Базовый комфорт",
        subtitle = "Блокировка спама, опросов ВЦИОМ и режим «Жду курьера»",
        description = "Идеально для повседневного использования без паранойи. Блокирует массовый спам, соцопросы и автопрозвоны, не мешает курьерам и таксистам."
    ),
    ANTI_EAVESDROPPING(
        title = "Анти-Прослушка",
        subtitle = "Защита микрофона, динамика и буфера обмена 2FA",
        description = "Защищает переговоры от фонового перехвата супераппами (VK, MAX) и рекламными трекерами. Автоматически стирает SMS-пароли."
    ),
    MAX_BUNKER(
        title = "Максимальный бункер",
        subtitle = "Полная изоляция: Сейф журнала звонков и скрытие уведомлений",
        description = "Режим максимальной скрытности. Все защитные экраны активны, звонки шифруются в приватном Сейфе и стираются из системы, уведомления скрыты."
    );

    fun getConfiguration(): PresetConfiguration = when (this) {
        BASIC_COMFORT -> PresetConfiguration(
            blockSurveys = true,
            blockFinancial = true,
            blockSilentCalls = true,
            postCallEnabled = true,
            micGuardEnabled = false,
            jammerAutoEnabled = false,
            clipboardGuardEnabled = false,
            callVaultEnabled = false,
            deliveryModeActive = true,
            blockForeignPlus7Active = false
        )
        ANTI_EAVESDROPPING -> PresetConfiguration(
            blockSurveys = true,
            blockFinancial = true,
            blockSilentCalls = true,
            postCallEnabled = true,
            micGuardEnabled = true,
            jammerAutoEnabled = true,
            clipboardGuardEnabled = true,
            callVaultEnabled = false,
            deliveryModeActive = false,
            blockForeignPlus7Active = false
        )
        MAX_BUNKER -> PresetConfiguration(
            blockSurveys = true,
            blockFinancial = true,
            blockSilentCalls = true,
            postCallEnabled = false, // Stealth: hide post-call popups
            micGuardEnabled = true,
            jammerAutoEnabled = true,
            clipboardGuardEnabled = true,
            callVaultEnabled = true,  // Encrypted call log vault
            deliveryModeActive = false,
            blockForeignPlus7Active = true // Block foreign +7 spoofing
        )
    }

    companion object {
        const val PREFS_KEY_ACTIVE_PRESET = "active_protection_preset"

        fun fromString(name: String?): ProtectionPreset {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: ANTI_EAVESDROPPING
        }
    }
}
