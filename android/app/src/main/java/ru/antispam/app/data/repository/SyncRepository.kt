package ru.antispam.app.data.repository

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.antispam.app.data.local.AppDatabase
import ru.antispam.app.data.local.entity.BlockedPoolEntity
import ru.antispam.app.data.remote.AntiSpamApiService
import ru.antispam.app.data.remote.NetworkModule
import ru.antispam.app.data.remote.dto.BatchReportItemDto
import ru.antispam.app.data.remote.dto.BatchReportRequestDto
import ru.antispam.app.security.AspbCryptoVerifier
import ru.antispam.app.security.DynamicConfig
import ru.antispam.app.security.DynamicConfigManager
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class SyncSource {
    PRIMARY_API,
    CDN_FALLBACK
}

sealed class SyncResult {
    data class Success(
        val source: SyncSource,
        val currentVersion: Long,
        val addedCount: Int,
        val removedCount: Int,
        val cdnMirrorUsed: String? = null
    ) : SyncResult()

    object NotModified : SyncResult()

    data class Failure(
        val message: String,
        val lastError: Throwable? = null
    ) : SyncResult()
}

data class ParsedAspb(
    val protoVersion: Int,
    val currentVersion: Long,
    val isFullSync: Boolean,
    val hasMore: Boolean,
    val added: List<BlockedPoolEntity>,
    val removed: List<Pair<Long, Long>>,
    val signatureHex: String? = null,
    val dynamicConfig: DynamicConfig? = null,
    val dynamicConfigRawJson: String? = null
)

class SyncRepository(
    private val db: AppDatabase,
    private val api: AntiSpamApiService = NetworkModule.apiService,
    private val httpClient: OkHttpClient = NetworkModule.cdnHttpClient,
    private val fallbackCdnUrls: List<String> = DEFAULT_CDN_URLS,
    private val context: Context? = null
) {

    companion object {
        private const val TAG = "SyncRepository"
        const val KEY_LAST_DELTA_VERSION = "last_delta_version"
        const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
        const val KEY_LAST_SYNC_SOURCE = "last_sync_source"
        const val KEY_PREFERRED_CDN_MIRROR = "preferred_cdn_mirror"
        const val KEY_LAST_USED_CDN_MIRROR = "last_used_cdn_mirror"

        private val dynamicConfigJsonParser = Json { ignoreUnknownKeys = true }

        /**
         * Multi-Vendor CDN Mirrors across distinct autonomous systems (ASNs) and jurisdictions.
         * Resilient against regional ISP throttling and TSPU DPI blocks in Russia.
         */
        val DEFAULT_CDN_URLS = listOf(
            // Mirror 1: Official GitHub Releases binary asset
            "https://github.com/Mishakorobov/Antispam/releases/download/latest-data/pools.aspb",
            // Mirror 2: jsDelivr Global Edge CDN (Cloudflare/Fastly backbone, bypasses Russian TSPU GitHub filtering)
            "https://cdn.jsdelivr.net/gh/Mishakorobov/Antispam@main/data/pools.aspb",
            // Mirror 3: GitHub Raw CDN
            "https://raw.githubusercontent.com/Mishakorobov/Antispam/main/data/pools.aspb",
            // Mirror 4: Sovereign Russian Git hosting mirror (GitFlic)
            "https://gitflic.ru/project/mishakorobov/antispam/blob/raw?file=data/pools.aspb"
        )

        fun prioritizeMirrors(preferred: String?, mirrors: List<String>): List<String> {
            return if (!preferred.isNullOrBlank() && mirrors.contains(preferred)) {
                listOf(preferred) + mirrors.filter { it != preferred }
            } else {
                mirrors
            }
        }

        /**
         * Pure parser for ASPB binary protocol payload.
         * Header (16 bytes): 'ASPB', proto_ver(2), current_ver(4), flags(2), reserved(4)=0
         * Added: count(4) + list of (range_start:8, range_end:8, cat:1, ver:4) [21 bytes each]
         * Removed: count(4) + list of (range_start:8, range_end:8, ver:4) [20 bytes each]
         */
        fun parseAspbPayload(bytes: ByteArray, timestamp: Long = System.currentTimeMillis()): ParsedAspb {
            if (bytes.size < 16) {
                throw IllegalArgumentException("Payload too short for ASPB header: ${bytes.size} bytes")
            }

            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)

            // 1. Verify Magic
            val magic = ByteArray(4)
            buffer.get(magic)
            if (magic[0] != 'A'.code.toByte() || magic[1] != 'S'.code.toByte() ||
                magic[2] != 'P'.code.toByte() || magic[3] != 'B'.code.toByte()
            ) {
                throw IllegalArgumentException("Invalid ASPB magic signature: ${String(magic)}")
            }

            val protoVer = buffer.short.toInt() and 0xFFFF
            val currentVer = buffer.int.toLong() and 0xFFFFFFFFL
            val flags = buffer.short.toInt() and 0xFFFF
            val isFullSync = (flags and 1) != 0
            val hasMore = (flags and 2) != 0
            val dynamicConfigLen = buffer.int.toLong() and 0xFFFFFFFFL

            // For ASPB v2, read 64-byte Ed25519 signature
            val signatureHex = if (protoVer == 2) {
                if (bytes.size < 80) {
                    throw IllegalArgumentException("Payload too short for signed ASPB v2 header: ${bytes.size} bytes")
                }
                val sigBytes = ByteArray(64)
                buffer.get(sigBytes)
                AspbCryptoVerifier.bytesToHex(sigBytes)
            } else {
                null
            }

            // Dynamic Config extraction if dynamicConfigLen > 0
            var dynamicConfig: DynamicConfig? = null
            var dynamicConfigRawJson: String? = null
            if (dynamicConfigLen > 0L) {
                if (buffer.remaining() < dynamicConfigLen.toInt()) {
                    throw IllegalArgumentException("Payload too short for dynamic config JSON: ${buffer.remaining()} < $dynamicConfigLen")
                }
                val jsonBytes = ByteArray(dynamicConfigLen.toInt())
                buffer.get(jsonBytes)
                dynamicConfigRawJson = String(jsonBytes, Charsets.UTF_8)
                try {
                    dynamicConfig = dynamicConfigJsonParser.decodeFromString<DynamicConfig>(dynamicConfigRawJson)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse DynamicConfig JSON: ${e.message}")
                }
            }

            // 2. Added chunk
            val addedCount = buffer.int.toLong() and 0xFFFFFFFFL
            val addedList = ArrayList<BlockedPoolEntity>(addedCount.toInt().coerceAtMost(100_000))
            for (i in 0 until addedCount) {
                val rStart = buffer.long
                val rEnd = buffer.long
                val catByte = buffer.get()
                val ver = buffer.int
                addedList.add(
                    BlockedPoolEntity(
                        rangeStart = rStart,
                        rangeEnd = rEnd,
                        operator = null,
                        category = categoryByteToString(catByte),
                        updatedAt = timestamp
                    )
                )
            }

            // 3. Removed chunk
            val removedCount = buffer.int.toLong() and 0xFFFFFFFFL
            val removedList = ArrayList<Pair<Long, Long>>(removedCount.toInt().coerceAtMost(100_000))
            for (i in 0 until removedCount) {
                val rStart = buffer.long
                val rEnd = buffer.long
                val ver = buffer.int
                removedList.add(Pair(rStart, rEnd))
            }

            return ParsedAspb(
                protoVersion = protoVer,
                currentVersion = currentVer,
                isFullSync = isFullSync,
                hasMore = hasMore,
                added = addedList,
                removed = removedList,
                signatureHex = signatureHex,
                dynamicConfig = dynamicConfig,
                dynamicConfigRawJson = dynamicConfigRawJson
            )
        }

        fun categoryByteToString(catByte: Byte): String {
            return when (catByte.toInt()) {
                1 -> "SPAM"
                2 -> "SURVEY"
                3 -> "FINANCIAL"
                4 -> "SPOOFING"
                5 -> "SILENT"
                6 -> "VIP_MODE"
                7 -> "FOREIGN_PLUS7"
                else -> "SPAM"
            }
        }
    }

    /**
     * Executes Dual-Circuit synchronization:
     * Circuit 1 (Primary): Fast incremental delta sync via REST API (when server is deployed).
     * Circuit 2 (Autonomous / Fallback): High-availability binary ASPB download via GitHub Releases Global CDN.
     */
    suspend fun synchronize(deviceHash: String? = null): SyncResult = withContext(Dispatchers.IO) {
        // Step 0: Flush offline reports (via Cloudflare Worker bridge in Serverless mode, or primary API)
        if (deviceHash != null) {
            try {
                flushPendingReports(deviceHash)
            } catch (e: Exception) {
                Log.w(TAG, "Non-critical: Failed to flush offline reports before sync: ${e.message}")
            }
        }

        // Autonomous CDN Mode: Immediately fetch binary ASPB snapshot from GitHub Releases CDN
        // without waiting for primary server connection timeouts
        if (NetworkModule.isServerlessMode) {
            Log.i(TAG, "Autonomous CDN Mode active: Synchronizing directly via GitHub Releases Global CDN...")
            return@withContext executeCdnFallback()
        }

        // Circuit 1: Primary API
        try {
            val primaryResult = executePrimarySync()
            if (primaryResult != null) {
                return@withContext primaryResult
            }
        } catch (e: Exception) {
            Log.w(TAG, "Circuit 1 (Primary API) failed: ${e.message}. Failing over to Circuit 2 (CDN Fallback)...")
        }

        // Circuit 2: Secondary CDN Fallback
        Log.i(TAG, "Engaging Circuit 2 (CDN Fallback) across ${fallbackCdnUrls.size} mirrors...")
        executeCdnFallback()
    }

    private suspend fun executePrimarySync(): SyncResult? {
        val lastVersion = db.syncMetadataDao().getValueLong(KEY_LAST_DELTA_VERSION) ?: 0L
        Log.d(TAG, "Primary Sync: Requesting deltas since version $lastVersion")

        val response = api.getPoolsDelta(since = lastVersion)

        // HTTP 304: Client is already up to date
        if (response.code() == 304) {
            Log.i(TAG, "Primary Sync HTTP 304 Not Modified (v$lastVersion). 0 bytes transferred.")
            return SyncResult.NotModified
        }

        if (!response.isSuccessful || response.body() == null) {
            throw IOException("Primary API returned HTTP ${response.code()}: ${response.message()}")
        }

        val delta = response.body()!!
        val now = System.currentTimeMillis()

        db.withTransaction {
            if (delta.fullSyncRequired) {
                Log.i(TAG, "Primary Sync: Full sync requested. Resetting pools.")
                db.blockedPoolDao().clearAll()
            } else {
                for (item in delta.removed) {
                    db.blockedPoolDao().deleteRange(item.rangeStart, item.rangeEnd)
                }
            }

            val entities = delta.added.map {
                BlockedPoolEntity(
                    rangeStart = it.rangeStart,
                    rangeEnd = it.rangeEnd,
                    operator = it.operator,
                    category = it.category,
                    updatedAt = now
                )
            }
            db.blockedPoolDao().insertAll(entities)

            db.syncMetadataDao().setValueLong(KEY_LAST_DELTA_VERSION, delta.currentVersion)
            db.syncMetadataDao().setValueLong(KEY_LAST_SYNC_TIMESTAMP, now)
            db.syncMetadataDao().setValueStr(KEY_LAST_SYNC_SOURCE, SyncSource.PRIMARY_API.name)
        }

        Log.i(TAG, "Primary Sync successful: version ${delta.currentVersion}, +${delta.added.size}, -${delta.removed.size}")
        return SyncResult.Success(
            source = SyncSource.PRIMARY_API,
            currentVersion = delta.currentVersion,
            addedCount = delta.added.size,
            removedCount = delta.removed.size
        )
    }

    /**
     * Prioritizes the last successfully connected CDN mirror to provide instant, zero-penalty sync,
     * while retaining full multi-vendor fallback resilience.
     */
    fun getOrderedCdnMirrors(): List<String> {
        val preferred = try {
            db.syncMetadataDao().getValueStr(KEY_PREFERRED_CDN_MIRROR)
        } catch (e: Exception) {
            null
        }
        val dynamicMirrors = DynamicConfigManager.getDynamicCdnMirrors()
        val allMirrors = (dynamicMirrors + fallbackCdnUrls).distinct()
        return prioritizeMirrors(preferred, allMirrors)
    }

    private suspend fun executeCdnFallback(): SyncResult {
        var lastException: Throwable? = null
        val mirrors = getOrderedCdnMirrors()

        for (url in mirrors) {
            try {
                Log.d(TAG, "Attempting CDN fallback fetch from: $url")
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "AntiSpam-Android-Client")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("CDN mirror $url returned HTTP ${response.code}")
                    }

                    val bodyBytes = response.body?.bytes()
                        ?: throw IOException("Empty body received from CDN mirror $url")

                    val lastVersion = db.syncMetadataDao().getValueLong(KEY_LAST_DELTA_VERSION) ?: 0L

                    // Phase 7.2 & 13.1: Cryptographic Ed25519 verification with anti-rollback protection
                    val verification = AspbCryptoVerifier.verifyAspbSnapshot(bodyBytes, localStoredVersion = lastVersion)
                    if (!verification.isAccepted) {
                        val breachMsg = "SECURITY_INTEGRITY_BREACH: Snapshot from $url failed cryptographic verification: $verification"
                        Log.e(TAG, breachMsg)
                        throw SecurityException(breachMsg)
                    }

                    val parsed = parseAspbPayload(bodyBytes)
                    val now = System.currentTimeMillis()

                    if (parsed.currentVersion <= lastVersion && lastVersion > 0L && db.blockedPoolDao().getCount() > 0) {
                        Log.i(TAG, "CDN snapshot v${parsed.currentVersion} already applied (local is v$lastVersion). Up-to-date.")
                        return SyncResult.NotModified
                    }

                    // Apply newly verified dynamic feature flags & rules
                    parsed.dynamicConfig?.let { dynConfig ->
                        DynamicConfigManager.applyConfig(dynConfig, db)
                    }

                    db.withTransaction {
                        db.blockedPoolDao().clearAll()
                        db.blockedPoolDao().insertAll(parsed.added)

                        db.syncMetadataDao().setValueLong(KEY_LAST_DELTA_VERSION, parsed.currentVersion)
                        db.syncMetadataDao().setValueLong(KEY_LAST_SYNC_TIMESTAMP, now)
                        db.syncMetadataDao().setValueStr(KEY_LAST_SYNC_SOURCE, SyncSource.CDN_FALLBACK.name)
                        db.syncMetadataDao().setValueStr(KEY_PREFERRED_CDN_MIRROR, url)
                        db.syncMetadataDao().setValueStr(KEY_LAST_USED_CDN_MIRROR, url)
                    }

                    // Phase 13.3: Cache verified snapshot for offline P2P sharing
                    context?.let { ctx ->
                        try {
                            ru.antispam.app.util.OfflineShareHelper.saveVerifiedSnapshot(ctx, bodyBytes)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to cache snapshot for P2P sharing: ${e.message}")
                        }
                    }

                    Log.i(TAG, "CDN Fallback successful via $url! Loaded ${parsed.added.size} pools (v${parsed.currentVersion})")
                    return SyncResult.Success(
                        source = SyncSource.CDN_FALLBACK,
                        currentVersion = parsed.currentVersion,
                        addedCount = parsed.added.size,
                        removedCount = parsed.removed.size,
                        cdnMirrorUsed = url
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "CDN mirror $url failed: ${e.message}. Failing over to next mirror...")
                lastException = e
            }
        }

        Log.e(TAG, "All primary and CDN fallback channels exhausted.")
        return SyncResult.Failure(
            message = "All sync channels failed. Primary API and CDN fallback mirrors unreachable.",
            lastError = lastException
        )
    }

    suspend fun flushPendingReports(deviceHash: String) {
        val pending = db.pendingReportDao().getPending(limit = 50)
        if (pending.isEmpty()) return

        Log.d(TAG, "Flushing ${pending.size} pending reports...")
        val firstPhone = pending.firstOrNull()?.phoneNumber?.toString() ?: ""
        val powSolution = ru.antispam.app.data.remote.PowGenerator.solve(firstPhone, deviceHash)

        val batchPayload = BatchReportRequestDto(
            deviceHash = deviceHash,
            reports = pending.map {
                BatchReportItemDto(
                    phoneNumber = it.phoneNumber.toString(),
                    category = it.category,
                    callDurationSeconds = it.callDurationSeconds
                )
            },
            powNonce = powSolution.nonce,
            powTimestamp = powSolution.timestamp
        )

        val targetApi = if (NetworkModule.isServerlessMode) {
            NetworkModule.serverlessReportApiService
        } else {
            api
        }

        targetApi.submitBatchReports(batchPayload)
        db.pendingReportDao().deleteByIds(pending.map { it.id })
        Log.i(TAG, "Successfully flushed ${pending.size} reports to ${if (NetworkModule.isServerlessMode) "Cloudflare Worker Bridge" else "Primary Server"}")
    }
}
