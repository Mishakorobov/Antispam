package ru.antispam.app.ui.calllog

import android.app.Application
import android.app.role.RoleManager
import android.content.Context
import android.provider.CallLog
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.antispam.app.data.local.AppDatabase
import ru.antispam.app.data.local.entity.LocalWhitelistEntity
import ru.antispam.app.data.local.entity.PendingReportEntity
import ru.antispam.app.data.remote.NetworkModule
import ru.antispam.app.data.remote.dto.SingleReportRequest
import ru.antispam.app.util.PhoneNormalizer
import ru.antispam.app.worker.SyncPoolsWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import ru.antispam.app.data.local.entity.BlockedCallRecordEntity
import ru.antispam.app.util.DeliveryModeManager

data class CallUiItem(
    val rawNumber: String,
    val normalizedNumber: Long?,
    val formattedNumber: String,
    val dateFormatted: String,
    val durationSeconds: Int,
    val operatorName: String?,
    val isBlockedPool: Boolean,
    val isSuspiciousShort: Boolean, // < 30 seconds
    val isWhitelisted: Boolean
)

data class CallLogUiState(
    val isRoleHeld: Boolean = false,
    val blockedCount: Int = 0,
    val totalPoolsCount: Int = 0,
    val lastSyncTime: String = "Не выполнялась",
    val calls: List<CallUiItem> = emptyList(),
    val blockedAuditRecords: List<BlockedCallRecordEntity> = emptyList(),
    val isDeliveryModeActive: Boolean = false,
    val deliveryRemainingMinutes: Int = 0,
    val isLoading: Boolean = false
)

class CallLogViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val api = NetworkModule.apiService
    private val _uiState = MutableStateFlow(CallLogUiState())
    val uiState: StateFlow<CallLogUiState> = _uiState.asStateFlow()

    init {
        refreshData()
    }

    fun toggleDeliveryMode() {
        val context = getApplication<Application>()
        val currentlyActive = DeliveryModeManager.isDeliveryModeActive(context)
        DeliveryModeManager.setDeliveryMode(context, !currentlyActive, durationHours = 2)
        refreshData()
    }

    fun refreshData() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }

            val context = getApplication<Application>()

            // 1. Check system Call Screening Role
            val roleManager = context.getSystemService(RoleManager::class.java)
            val isRoleHeld = roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true

            // 2. Query local stats and audit log from DB
            val poolsCount = db.blockedPoolDao().getCount()
            val lastVer = db.syncMetadataDao().getValueLong("last_delta_version") ?: 0L
            val auditRecords = db.blockedCallRecordDao().getRecentBlockedCalls(limit = 20)
            val auditBlockedCount = db.blockedCallRecordDao().getTotalBlockedCount()

            // 3. Check delivery mode status
            val isDelivery = DeliveryModeManager.isDeliveryModeActive(context)
            val remainingMin = DeliveryModeManager.getRemainingMinutes(context)

            // 4. Load call log and correlate with Room
            val items = loadCallLogItems()

            _uiState.update {
                it.copy(
                    isRoleHeld = isRoleHeld,
                    totalPoolsCount = poolsCount,
                    blockedCount = if (auditBlockedCount > 0) auditBlockedCount else items.count { c -> c.isBlockedPool },
                    lastSyncTime = if (lastVer > 0) "Версия $lastVer" else "Предзагружена",
                    calls = items,
                    blockedAuditRecords = auditRecords,
                    isDeliveryModeActive = isDelivery,
                    deliveryRemainingMinutes = remainingMin,
                    isLoading = false
                )
            }
        }
    }

    private fun loadCallLogItems(): List<CallUiItem> {
        val result = mutableListOf<CallUiItem>()
        val context = getApplication<Application>()
        val contentResolver = context.contentResolver
        val timeFormat = SimpleDateFormat("HH:mm, d MMM", Locale.getDefault())

        try {
            val cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.TYPE
                ),
                null,
                null,
                "${CallLog.Calls.DATE} DESC LIMIT 50"
            )

            cursor?.use {
                val numIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                val durIdx = it.getColumnIndex(CallLog.Calls.DURATION)

                while (it.moveToNext()) {
                    val rawNum = it.getString(numIdx) ?: continue
                    val timestamp = it.getLong(dateIdx)
                    val duration = it.getInt(durIdx)

                    val normalized = PhoneNormalizer.normalize(rawNum)
                    var operatorName: String? = null
                    var isBlocked = false
                    var isWhitelisted = false

                    if (normalized != null) {
                        val pool = db.blockedPoolDao().getPoolForNumber(normalized)
                        if (pool != null) {
                            isBlocked = true
                            operatorName = pool.operator
                        }
                        isWhitelisted = db.localWhitelistDao().isWhitelisted(normalized)
                    }

                    // Suspicious if duration < 30 seconds and not whitelisted
                    val isSuspicious = duration in 1..29 && !isWhitelisted && !isBlocked

                    result.add(
                        CallUiItem(
                            rawNumber = rawNum,
                            normalizedNumber = normalized,
                            formattedNumber = formatNumberPretty(rawNum),
                            dateFormatted = timeFormat.format(Date(timestamp)),
                            durationSeconds = duration,
                            operatorName = operatorName,
                            isBlockedPool = isBlocked,
                            isSuspiciousShort = isSuspicious,
                            isWhitelisted = isWhitelisted
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // If READ_CALL_LOG permission not granted yet, return empty list
        }

        return result
    }

    fun submitReport(item: CallUiItem, category: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val norm = item.normalizedNumber ?: return@launch
            val devHash = SyncPoolsWorker.getOrCreateDeviceHash(getApplication())

            try {
                // Try immediate API report
                api.submitReport(
                    SingleReportRequest(
                        phoneNumber = norm.toString(),
                        deviceHash = devHash,
                        category = category,
                        callDurationSeconds = item.durationSeconds
                    )
                )
            } catch (_: Exception) {
                // Queue in pending_reports for offline sync
                db.pendingReportDao().insert(
                    PendingReportEntity(
                        phoneNumber = norm,
                        category = category,
                        callDurationSeconds = item.durationSeconds,
                        createdAt = System.currentTimeMillis()
                    )
                )
            }

            refreshData()
        }
    }

    fun addToWhitelist(item: CallUiItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val norm = item.normalizedNumber ?: return@launch
            db.localWhitelistDao().add(
                LocalWhitelistEntity(
                    phoneNumber = norm,
                    label = "Добавлен из журнала",
                    addedAt = System.currentTimeMillis()
                )
            )
            refreshData()
        }
    }

    private fun formatNumberPretty(raw: String): String {
        val norm = PhoneNormalizer.normalize(raw)?.toString() ?: return raw
        if (norm.length == 11 && norm.startsWith("7")) {
            return "+7 (${norm.substring(1, 4)}) ${norm.substring(4, 7)}-${norm.substring(7, 9)}-${norm.substring(9)}"
        }
        return raw
    }
}
