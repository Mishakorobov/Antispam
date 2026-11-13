package ru.antispam.app.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import ru.antispam.app.data.remote.dto.BatchReportRequestDto
import ru.antispam.app.data.remote.dto.BatchReportResponseDto
import ru.antispam.app.data.remote.dto.PoolsDeltaResponseDto
import ru.antispam.app.data.remote.dto.ReportResponseDto
import ru.antispam.app.data.remote.dto.SingleReportRequest
import ru.antispam.app.data.remote.dto.WhitelistGroupDto
import ru.antispam.app.data.remote.dto.WhitelistToggleRequestDto

interface AntiSpamApiService {

    @POST("api/v1/report")
    suspend fun submitReport(
        @Body request: SingleReportRequest
    ): ReportResponseDto

    @POST("api/v1/report/false-positive")
    suspend fun submitFalsePositive(
        @Body request: ru.antispam.app.data.remote.dto.FalsePositiveRequestDto
    ): ru.antispam.app.data.remote.dto.FalsePositiveResponseDto

    @POST("api/v1/reports/batch")
    suspend fun submitBatchReports(
        @Body request: BatchReportRequestDto
    ): BatchReportResponseDto

    @GET("api/v1/pools")
    suspend fun getPoolsDelta(
        @Query("since") since: Long = 0,
        @Query("limit") limit: Int = 5000
    ): retrofit2.Response<PoolsDeltaResponseDto>

    @GET("api/v1/whitelist/groups")
    suspend fun getWhitelistGroups(): List<WhitelistGroupDto>

    @POST("api/v1/whitelist/groups/{group_key}/toggle")
    suspend fun toggleWhitelistGroup(
        @Path("group_key") groupKey: String,
        @Body request: WhitelistToggleRequestDto
    ): Map<String, String>
}
