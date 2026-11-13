package ru.antispam.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BlockItemDto(
    @SerialName("range_start") val rangeStart: Long,
    @SerialName("range_end") val rangeEnd: Long,
    @SerialName("operator") val operator: String? = null,
    @SerialName("category") val category: String? = null,
    @SerialName("version") val version: Long
)

@Serializable
data class PoolsDeltaResponseDto(
    @SerialName("current_version") val currentVersion: Long,
    @SerialName("full_sync_required") val fullSyncRequired: Boolean,
    @SerialName("has_more") val hasMore: Boolean,
    @SerialName("added") val added: List<BlockItemDto>,
    @SerialName("removed") val removed: List<BlockItemDto>
)
