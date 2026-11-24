package ru.antispam.app.ui.calllog

import android.app.Application
import android.app.role.RoleManager
import android.content.ContentResolver
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.antispam.app.data.local.AppDatabase
import ru.antispam.app.data.local.entity.BlockedCallRecordEntity
import ru.antispam.app.data.local.entity.BlockedPoolEntity
import ru.antispam.app.data.local.entity.LocalWhitelistEntity
import ru.antispam.app.data.repository.SyncRepository
import ru.antispam.app.util.DeliveryModeManager
import ru.antispam.app.util.PhoneNormalizer
import ru.antispam.app.worker.SyncPoolsWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class CallUiItem(
    val rawNumber: String,
    val normalizedNumber: Long?,
    val formattedNumber: String,
    val dateFormatted: String,
    val durationSeconds: Int,
    val callType: Int = CallLog.Calls.INCOMING_TYPE,
    val operatorName: String?,
    val isBlockedPool: Boolean,
    val isCancelledByApp: Boolean = false,
    val isCancelledByDatabase: Boolean = false,
    val cancelReasonCode: String? = null,
    val cancelCategory: String? = null,
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
    private val _uiState = MutableStateFlow(CallLogUiState())
    val uiState: StateFlow<CallLogUiState> = _uiState.asStateFlow()

    internal data class CallCancellationStatus(
        val isBlockedPool: Boolean,
        val isCancelledByApp: Boolean,
        val isCancelledByDatabase: Boolean,
        val cancelReasonCode: String?,
        val cancelCategory: String?,
        val operatorName: String?
    )

    companion object {
        private const val AUDIT_MATCH_WINDOW_MS = 60_000L
        private val NON_CANCELLING_REASONS = setOf("SILENCED_DELIVERY", "SILENCED_VIP_MODE")
        private val DATABASE_CANCELLING_REASONS = setOf("SPAM_POOL")

        internal fun resolveCallCancellationStatus(
            callType: Int,
            blockReasonColumn: Int,
            screeningComponent: String?,
            appPackageName: String,
            callTimestamp: Long,
            pool: BlockedPoolEntity?,
            auditRecordsForNumber: List<BlockedCallRecordEntity>
        ): CallCancellationStatus {
            val isOutgoing = callType == CallLog.Calls.OUTGOING_TYPE
            val matchingAudit = if (isOutgoing) {
                null
            } else {
                auditRecordsForNumber
                    .filter { abs(it.timestamp - callTimestamp) <= AUDIT_MATCH_WINDOW_MS }
                    .minByOrNull { abs(it.timestamp - callTimestamp) }
            }

            val blockedBySystemScreening = !isOutgoing &&
                callType == CallLog.Calls.BLOCKED_TYPE &&
                blockReasonColumn == CallLog.Calls.BLOCK_REASON_CALL_SCREENING_SERVICE &&
                screeningComponent?.contains(appPackageName, ignoreCase = true) == true

            val auditReason = matchingAudit?.blockReason
            val cancelledByAudit = matchingAudit != null && auditReason !in NON_CANCELLING_REASONS
            val isCancelledByApp = cancelledByAudit || blockedBySystemScreening

            val isCancelledByDatabase = isCancelledByApp && (
                (matchingAudit != null && auditReason in DATABASE_CANCELLING_REASONS) ||
                    (matchingAudit == null && pool != null)
                )

            val resolvedReason = auditReason ?: if (isCancelledByDatabase) "SPAM_POOL" else null
            val resolvedCategory = matchingAudit?.category ?: pool?.category
            val resolvedOperator = matchingAudit?.operatorName ?: pool?.operator

            return CallCancellationStatus(
                isBlockedPool = pool != null,
                isCancelledByApp = isCancelledByApp,
                isCancelledByDatabase = isCancelledByDatabase,
                cancelReasonCode = resolvedReason,
                cancelCategory = resolvedCategory,
                operatorName = resolvedOperator
            )
        }
    }

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
            val items = loadCallLogItems(auditRecordsByNumber = db.blockedCallRecordDao().getRecentBlockedCalls(limit = 200).groupBy { it.phoneNumber })

            _uiState.update {
                it.copy(
                    isRoleHeld = isRoleHeld,
                    totalPoolsCount = poolsCount,
                    blockedCount = if (auditBlockedCount > 0) auditBlockedCount else items.count { c -> c.isCancelledByApp },
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

    private fun loadCallLogItems(
        auditRecordsByNumber: Map<Long, List<BlockedCallRecordEntity>> = emptyMap()
    ): List<CallUiItem> {
        val result = mutableListOf<CallUiItem>()
        val context = getApplication<Application>()
        val contentResolver = context.contentResolver
        val timeFormat = SimpleDateFormat("HH:mm, d MMM", Locale.getDefault())
        val packageName = context.packageName

        try {
            val projection = arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.TYPE,
                CallLog.Calls.BLOCK_REASON,
                CallLog.Calls.CALL_SCREENING_COMPONENT_NAME
            )
            val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val queryArgs = Bundle().apply {
                    putInt(ContentResolver.QUERY_ARG_LIMIT, 50)
                    putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(CallLog.Calls.DATE))
                    putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                }
                contentResolver.query(CallLog.Calls.CONTENT_URI, projection, queryArgs, null)
            } else {
                contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${CallLog.Calls.DATE} DESC LIMIT 50"
                )
            }

            cursor?.use {
                val numIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                val durIdx = it.getColumnIndex(CallLog.Calls.DURATION)
                val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
                val blockReasonIdx = it.getColumnIndex(CallLog.Calls.BLOCK_REASON)
                val screeningCompIdx = it.getColumnIndex(CallLog.Calls.CALL_SCREENING_COMPONENT_NAME)

                while (it.moveToNext() && result.size < 50) {
                    val rawNum = if (numIdx >= 0) it.getString(numIdx) ?: continue else continue
                    val timestamp = if (dateIdx >= 0) it.getLong(dateIdx) else 0L
                    val duration = if (durIdx >= 0) it.getInt(durIdx) else 0
                    val callType = if (typeIdx >= 0) it.getInt(typeIdx) else CallLog.Calls.INCOMING_TYPE
                    val blockReasonCol = if (blockReasonIdx >= 0) it.getInt(blockReasonIdx) else 0
                    val screeningComp = if (screeningCompIdx >= 0) it.getString(screeningCompIdx) else null

                    val normalized = PhoneNormalizer.normalize(rawNum)
                    val pool = if (normalized != null) db.blockedPoolDao().getPoolForNumber(normalized) else null
                    val isWhitelisted = if (normalized != null) db.localWhitelistDao().isWhitelisted(normalized) else false
                    val auditList = if (normalized != null) auditRecordsByNumber[normalized].orEmpty() else emptyList()

                    val status = resolveCallCancellationStatus(
                        callType = callType,
                        blockReasonColumn = blockReasonCol,
                        screeningComponent = screeningComp,
                        appPackageName = packageName,
                        callTimestamp = timestamp,
                        pool = pool,
                        auditRecordsForNumber = auditList
                    )

                    // Suspicious if non-outgoing answered call lasted < 30s and not whitelisted/cancelled
                    val isSuspicious = callType != CallLog.Calls.OUTGOING_TYPE &&
                        duration in 1..29 &&
                        !isWhitelisted &&
                        !status.isBlockedPool &&
                        !status.isCancelledByApp

                    result.add(
                        CallUiItem(
                            rawNumber = rawNum,
                            normalizedNumber = normalized,
                            formattedNumber = formatNumberPretty(rawNum),
                            dateFormatted = timeFormat.format(Date(timestamp)),
                            durationSeconds = duration,
                            callType = callType,
                            operatorName = status.operatorName,
                            isBlockedPool = status.isBlockedPool,
                            isCancelledByApp = status.isCancelledByApp,
                            isCancelledByDatabase = status.isCancelledByDatabase,
                            cancelReasonCode = status.cancelReasonCode,
                            cancelCategory = status.cancelCategory,
                            isSuspiciousShort = isSuspicious,
                            isWhitelisted = isWhitelisted
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
            // READ_CALL_LOG permission not granted yet
        } catch (e: Exception) {
            Log.w("CallLogViewModel", "Failed reading call log: ${e.message}")
        }

        return result
    }

    fun submitReport(item: CallUiItem, category: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val norm = item.normalizedNumber ?: return@launch
            val app = getApplication<Application>()
            val devHash = SyncPoolsWorker.getOrCreateDeviceHash(app)
            val repository = SyncRepository(db = db, context = app)
            repository.submitOrQueueReport(
                phoneNumber = norm,
                deviceHash = devHash,
                category = category,
                callDurationSeconds = item.durationSeconds
            )

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
        if (raw.isBlank() || raw == "-1" || raw == "-2" || raw == "-3") {
            return "Скрытый номер"
        }
        val norm = PhoneNormalizer.normalize(raw) ?: PhoneNormalizer.normalizeShortCode(raw)
        if (norm != null) {
            return PhoneNormalizer.formatRussian(norm)
        }
        return raw
    }
}
