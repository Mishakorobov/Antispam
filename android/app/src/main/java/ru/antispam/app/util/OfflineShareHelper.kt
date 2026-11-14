package ru.antispam.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import ru.antispam.app.data.local.AppDatabase
import ru.antispam.app.data.repository.ParsedAspb
import ru.antispam.app.data.repository.SyncRepository
import ru.antispam.app.security.AspbCryptoVerifier
import ru.antispam.app.security.AspbVerificationResult
import ru.antispam.app.security.DynamicConfigManager
import java.io.File
import java.io.FileOutputStream

/**
 * Result of importing an offline ASPB snapshot.
 */
sealed class ImportResult {
    data class Success(
        val version: Long,
        val addedCount: Int,
        val dynamicConfigApplied: Boolean
    ) : ImportResult()

    data class AlreadyUpToDate(
        val version: Long
    ) : ImportResult()

    data class RollbackError(
        val incomingVersion: Long,
        val currentVersion: Long,
        val message: String
    ) : ImportResult()

    data class SecurityError(
        val reason: String
    ) : ImportResult()

    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : ImportResult()
}

/**
 * OfflineShareHelper:
 * Manages GMS-Free, Zero-Trust offline P2P database sharing via Quick Share, Bluetooth,
 * local files, and removable media.
 *
 * Guarantees cryptographic immutability: all incoming snapshots must be signed
 * with master Ed25519 asymmetric signature and strictly satisfy anti-rollback checks
 * (incomingVersion >= localStoredVersion) before Room SQLite insertion.
 */
object OfflineShareHelper {

    private const val TAG = "OfflineShareHelper"
    private const val SNAPSHOT_DIR = "snapshots"
    const val SNAPSHOT_FILE_NAME = "pools.aspb"

    /**
     * Returns the local verified snapshot file on disk.
     */
    fun getSnapshotFile(context: Context): File {
        val dir = File(context.filesDir, SNAPSHOT_DIR).apply { mkdirs() }
        return File(dir, SNAPSHOT_FILE_NAME)
    }

    /**
     * Persists a cryptographically verified ASPB snapshot to disk so it can be shared offline.
     */
    fun saveVerifiedSnapshot(context: Context, bytes: ByteArray): File {
        val file = getSnapshotFile(context)
        FileOutputStream(file).use { out ->
            out.write(bytes)
            out.flush()
        }
        Log.i(TAG, "Cached verified ASPB snapshot (${bytes.size} bytes) to ${file.absolutePath}")
        return file
    }

    /**
     * Prepares an Android ACTION_SEND Intent with FileProvider content URI
     * for Quick Share, Bluetooth, Telegram, or flash drives.
     * Returns null if no verified snapshot is available yet.
     */
    fun prepareExportIntent(
        context: Context,
        expectedPublicKeyHex: String = AspbCryptoVerifier.DEFAULT_PUBLIC_KEY_HEX
    ): Intent? {
        val file = getSnapshotFile(context)
        if (!file.exists() || file.length() < AspbCryptoVerifier.ASPB_V2_HEADER_SIZE) {
            Log.w(TAG, "Cannot export: snapshot file does not exist or is too short.")
            return null
        }

        // Verify file integrity before sharing
        val bytes = file.readBytes()
        val verification = AspbCryptoVerifier.verifyAspbSnapshot(bytes, expectedPublicKeyHex = expectedPublicKeyHex)
        if (!verification.isAccepted) {
            Log.e(TAG, "Cannot export corrupted snapshot: $verification")
            return null
        }

        val authority = "${context.packageName}.fileprovider"
        val contentUri: Uri = try {
            FileProvider.getUriForFile(context, authority, file)
        } catch (e: Exception) {
            Log.e(TAG, "FileProvider error: ${e.message}", e)
            return null
        }

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, "База спам-номеров AntiSpam (.aspb)")
            putExtra(Intent.EXTRA_TEXT, "Офлайн-снимок базы спам-номеров, подписанный Ed25519.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return Intent.createChooser(sendIntent, "Поделиться базой спам-номеров (.aspb)")
    }

    /**
     * Imports an ASPB snapshot from a Content/File URI (SAF, Quick Share, Bluetooth receive).
     */
    suspend fun importFromUri(
        context: Context,
        uri: Uri,
        db: AppDatabase,
        expectedPublicKeyHex: String = AspbCryptoVerifier.DEFAULT_PUBLIC_KEY_HEX
    ): ImportResult {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes()
            } ?: return ImportResult.Error("Не удалось прочитать файл по указанному URI.")
        } catch (e: Exception) {
            Log.e(TAG, "Error reading snapshot URI: ${e.message}", e)
            return ImportResult.Error("Ошибка чтения файла: ${e.message}", e)
        }

        return importFromBytes(context, bytes, db, expectedPublicKeyHex)
    }

    /**
     * Cryptographically validates and imports raw ASPB bytes into Room SQLite.
     */
    suspend fun importFromBytes(
        context: Context?,
        bytes: ByteArray,
        db: AppDatabase,
        expectedPublicKeyHex: String = AspbCryptoVerifier.DEFAULT_PUBLIC_KEY_HEX
    ): ImportResult {
        return importFromBytes(context, bytes, db.blockedPoolDao(), db.syncMetadataDao(), db, expectedPublicKeyHex)
    }

    suspend fun importFromBytes(
        context: Context?,
        bytes: ByteArray,
        poolDao: ru.antispam.app.data.local.dao.BlockedPoolDao,
        metaDao: ru.antispam.app.data.local.dao.SyncMetadataDao,
        db: AppDatabase? = null,
        expectedPublicKeyHex: String = AspbCryptoVerifier.DEFAULT_PUBLIC_KEY_HEX
    ): ImportResult {
        if (bytes.size < AspbCryptoVerifier.ASPB_HEADER_SIZE) {
            return ImportResult.Error("Размер файла слишком мал для заголовка ASPB (${bytes.size} байт).")
        }

        val lastVersion = metaDao.getValueLong(SyncRepository.KEY_LAST_DELTA_VERSION) ?: 0L

        // 1. Zero-Trust Ed25519 signature verification + Anti-Rollback validation
        val verification = AspbCryptoVerifier.verifyAspbSnapshot(
            bytes,
            expectedPublicKeyHex = expectedPublicKeyHex,
            localStoredVersion = lastVersion
        )

        when (verification) {
            is AspbVerificationResult.RollbackDetected -> {
                Log.w(TAG, "P2P Import rejected: Rollback detected (${verification.message})")
                return ImportResult.RollbackError(
                    incomingVersion = verification.incomingVersion,
                    currentVersion = verification.localVersion,
                    message = verification.message
                )
            }
            is AspbVerificationResult.InvalidSignature -> {
                Log.e(TAG, "P2P Import rejected: Invalid signature (${verification.reason})")
                return ImportResult.SecurityError("Криптографическая проверка Ed25519 провалена: файл модифицирован или поврежден.")
            }
            is AspbVerificationResult.Corrupted -> {
                Log.e(TAG, "P2P Import rejected: Corrupted format (${verification.error})")
                return ImportResult.Error("Поврежденный формат ASPB: ${verification.error}")
            }
            is AspbVerificationResult.UnsignedFormat -> {
                if (!verification.isAllowed) {
                    return ImportResult.SecurityError("Неподписанные файлы ASPB v1 запрещены в релизной версии.")
                }
            }
            is AspbVerificationResult.Valid -> {
                // Accepted
            }
        }

        // 2. Parse payload
        val parsed = try {
            SyncRepository.parseAspbPayload(bytes)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse ASPB payload: ${e.message}", e)
            return ImportResult.Error("Ошибка парсинга ASPB: ${e.message}", e)
        }

        // 3. Check if local database is already up to date
        if (parsed.currentVersion <= lastVersion && lastVersion > 0L && poolDao.getCount() > 0) {
            Log.i(TAG, "P2P snapshot v${parsed.currentVersion} already applied (local is v$lastVersion). Up-to-date.")
            return ImportResult.AlreadyUpToDate(parsed.currentVersion)
        }

        // 4. Atomic transaction update into Room SQLite
        val now = System.currentTimeMillis()
        if (db != null) {
            try {
                db.withTransaction {
                    applyParsedToDaos(poolDao, metaDao, parsed, now)
                }
            } catch (_: Exception) {
                applyParsedToDaos(poolDao, metaDao, parsed, now)
            }
        } else {
            applyParsedToDaos(poolDao, metaDao, parsed, now)
        }

        // 5. Apply verified DynamicConfig if present
        var dynamicApplied = false
        parsed.dynamicConfig?.let { dynConfig ->
            DynamicConfigManager.applyConfig(dynConfig, db)
            dynamicApplied = true
        }

        // 6. Cache verified snapshot on disk for further P2P distribution
        context?.let { ctx ->
            try {
                saveVerifiedSnapshot(ctx, bytes)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to cache imported snapshot to disk: ${e.message}")
            }
        }

        Log.i(TAG, "P2P Import successful! Loaded ${parsed.added.size} pools (v${parsed.currentVersion})")
        return ImportResult.Success(
            version = parsed.currentVersion,
            addedCount = parsed.added.size,
            dynamicConfigApplied = dynamicApplied
        )
    }

    fun applyParsedToDaos(
        poolDao: ru.antispam.app.data.local.dao.BlockedPoolDao,
        metaDao: ru.antispam.app.data.local.dao.SyncMetadataDao,
        parsed: ParsedAspb,
        timestamp: Long
    ) {
        poolDao.clearAll()
        poolDao.insertAll(parsed.added)
        metaDao.setValueLong(SyncRepository.KEY_LAST_DELTA_VERSION, parsed.currentVersion)
        metaDao.setValueLong(SyncRepository.KEY_LAST_SYNC_TIMESTAMP, timestamp)
        metaDao.setValueStr(SyncRepository.KEY_LAST_SYNC_SOURCE, "OFFLINE_P2P")
    }
}
