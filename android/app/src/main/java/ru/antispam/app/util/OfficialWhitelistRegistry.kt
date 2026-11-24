package ru.antispam.app.util

import android.content.Context
import ru.antispam.app.data.remote.dto.WhitelistGroupDto
import ru.antispam.app.data.remote.dto.WhitelistRangeDto

/**
 * Single local source of truth for official Russian emergency, government, and bank whitelist groups.
 * Synchronizes UI toggles in WhitelistViewModel with runtime protection in AntiSpamCallScreeningService
 * and PostCall heuristics without requiring primary server connectivity in serverless mode.
 */
object OfficialWhitelistRegistry {

    private const val PREFS_NAME = "antispam_whitelist_prefs"
    private const val KEY_PREFIX_GROUP_ENABLED = "group_enabled_"

    private val defaultGroups: List<WhitelistGroupDto> = listOf(
        WhitelistGroupDto(
            groupKey = "EMERGENCY",
            category = "EMERGENCY",
            isEnabled = true,
            rangeCount = 2,
            description = "Единая служба спасения 112, 101, 102, 103, 104",
            ranges = listOf(
                WhitelistRangeDto(70000000112L, 70000000112L, "Единая служба спасения (112)"),
                WhitelistRangeDto(70000000101L, 70000000104L, "Службы 101, 102, 103, 104")
            )
        ),
        WhitelistGroupDto(
            groupKey = "GOV",
            category = "GOV",
            isEnabled = true,
            rangeCount = 3,
            description = "Госуслуги (0919, 8-800-100-70-10), Социальный фонд",
            ranges = listOf(
                WhitelistRangeDto(70000000919L, 70000000920L, "Госуслуги (Короткие номера 0919, 0920)"),
                WhitelistRangeDto(78001007010L, 78001007010L, "Госуслуги (Горячая линия 8-800-100-70-10)"),
                WhitelistRangeDto(78001000001L, 78001000001L, "Социальный фонд России (8-800-100-00-01)")
            )
        ),
        WhitelistGroupDto(
            groupKey = "SBERBANK",
            category = "BANK",
            isEnabled = true,
            rangeCount = 3,
            description = "Сбербанк (900, 8-800-555-55-50)",
            ranges = listOf(
                WhitelistRangeDto(70000000900L, 70000000900L, "Сбербанк (Сервисный номер 900)"),
                WhitelistRangeDto(78005555550L, 78005555550L, "Сбербанк (Горячая линия 8-800-555-55-50)"),
                WhitelistRangeDto(74955005550L, 74955005550L, "Сбербанк Москва (+7-495-500-55-50)")
            )
        ),
        WhitelistGroupDto(
            groupKey = "TBANK",
            category = "BANK",
            isEnabled = true,
            rangeCount = 4,
            description = "Т-Банк (Линии 8-800, Московский офис)",
            ranges = listOf(
                WhitelistRangeDto(78005557778L, 78005557778L, "Т-Банк (Поддержка 8-800-555-77-78)"),
                WhitelistRangeDto(78007557778L, 78007557778L, "Т-Банк (Линия 8-800-755-77-78)"),
                WhitelistRangeDto(78003333333L, 78003333333L, "Т-Банк (Линия 8-800-333-33-33)"),
                WhitelistRangeDto(74956481111L, 74956481111L, "Т-Банк Москва (+7-495-648-11-11)")
            )
        ),
        WhitelistGroupDto(
            groupKey = "VTB",
            category = "BANK",
            isEnabled = true,
            rangeCount = 2,
            description = "ВТБ (1000, 8-800-100-24-24)",
            ranges = listOf(
                WhitelistRangeDto(70000001000L, 70000001000L, "ВТБ (Короткий номер 1000)"),
                WhitelistRangeDto(78001002424L, 78001002424L, "ВТБ (Горячая линия 8-800-100-24-24)")
            )
        ),
        WhitelistGroupDto(
            groupKey = "ALFABANK",
            category = "BANK",
            isEnabled = true,
            rangeCount = 2,
            description = "Альфа-Банк (8-800-200-00-00)",
            ranges = listOf(
                WhitelistRangeDto(78002000000L, 78002000000L, "Альфа-Банк (Горячая линия 8-800-200-00-00)"),
                WhitelistRangeDto(74957888878L, 74957888878L, "Альфа-Банк Москва (+7-495-788-88-78)")
            )
        )
    )

    fun isGroupEnabled(context: Context, groupKey: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_PREFIX_GROUP_ENABLED + groupKey.uppercase(), true)
    }

    fun setGroupEnabled(context: Context, groupKey: String, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_PREFIX_GROUP_ENABLED + groupKey.uppercase(), enabled)
            .apply()
    }

    fun getGroupsWithLocalPrefs(context: Context): List<WhitelistGroupDto> {
        return defaultGroups.map { group ->
            group.copy(isEnabled = isGroupEnabled(context, group.groupKey))
        }
    }

    fun isWhitelistedByEnabledGroup(context: Context, normalizedNumber: Long): Boolean {
        return defaultGroups.any { group ->
            isGroupEnabled(context, group.groupKey) &&
                group.ranges.any { range -> normalizedNumber in range.start..range.end }
        }
    }

    /**
     * Pure helper for unit testing against explicit enabled group keys.
     */
    fun isWhitelistedInGroups(normalizedNumber: Long, enabledGroupKeys: Set<String>): Boolean {
        return defaultGroups.any { group ->
            group.groupKey in enabledGroupKeys &&
                group.ranges.any { range -> normalizedNumber in range.start..range.end }
        }
    }
}
