package ru.antispam.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WhitelistRangeDto(
    @SerialName("start") val start: Long,
    @SerialName("end") val end: Long,
    @SerialName("name") val name: String
)

@Serializable
data class WhitelistGroupDto(
    @SerialName("group_key") val groupKey: String,
    @SerialName("category") val category: String,
    @SerialName("is_enabled") val isEnabled: Boolean,
    @SerialName("range_count") val rangeCount: Int,
    @SerialName("description") val description: String,
    @SerialName("ranges") val ranges: List<WhitelistRangeDto>
)

@Serializable
data class WhitelistToggleRequestDto(
    @SerialName("enable") val enable: Boolean
)
