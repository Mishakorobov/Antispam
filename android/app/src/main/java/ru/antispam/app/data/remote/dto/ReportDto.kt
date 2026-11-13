package ru.antispam.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SingleReportRequest(
    @SerialName("phone_number") val phoneNumber: String,
    @SerialName("device_hash") val deviceHash: String,
    @SerialName("category") val category: String = "SPAM",
    @SerialName("call_duration_seconds") val callDurationSeconds: Int? = null
)

@Serializable
data class ReportResponseDto(
    @SerialName("status") val status: String,
    @SerialName("message") val message: String,
    @SerialName("blocked") val blocked: Boolean = false,
    @SerialName("block_type") val blockType: String? = null,
    @SerialName("range_start") val rangeStart: Long? = null,
    @SerialName("range_end") val rangeEnd: Long? = null,
    @SerialName("bucket_100") val bucket100: Long? = null
)

@Serializable
data class BatchReportItemDto(
    @SerialName("phone_number") val phoneNumber: String,
    @SerialName("category") val category: String = "SPAM",
    @SerialName("call_duration_seconds") val callDurationSeconds: Int? = null
)

@Serializable
data class BatchReportRequestDto(
    @SerialName("device_hash") val deviceHash: String,
    @SerialName("reports") val reports: List<BatchReportItemDto>
)

@Serializable
data class BatchReportResponseDto(
    @SerialName("status") val status: String,
    @SerialName("total_received") val totalReceived: Int,
    @SerialName("accepted") val accepted: Int,
    @SerialName("ignored") val ignored: Int,
    @SerialName("blocked_count") val blockedCount: Int
)

@Serializable
data class FalsePositiveRequestDto(
    @SerialName("phone_number") val phoneNumber: String,
    @SerialName("device_hash") val deviceHash: String,
    @SerialName("reason") val reason: String = "OUTGOING_CALL"
)

@Serializable
data class FalsePositiveResponseDto(
    @SerialName("status") val status: String,
    @SerialName("message") val message: String,
    @SerialName("unblocked") val unblocked: Boolean = false,
    @SerialName("block_type") val blockType: String? = null,
    @SerialName("range_start") val rangeStart: Long? = null,
    @SerialName("range_end") val rangeEnd: Long? = null
)
