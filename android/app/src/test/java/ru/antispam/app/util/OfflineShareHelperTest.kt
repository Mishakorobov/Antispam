package ru.antispam.app.util

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.antispam.app.data.local.dao.BlockedPoolDao
import ru.antispam.app.data.local.dao.SyncMetadataDao
import ru.antispam.app.data.local.entity.BlockedPoolEntity
import ru.antispam.app.data.local.entity.SyncMetadataEntity
import ru.antispam.app.data.repository.SyncRepository
import ru.antispam.app.security.AspbCryptoVerifier
import ru.antispam.app.security.DynamicConfig
import ru.antispam.app.security.DynamicConfigManager
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.security.SecureRandom

class OfflineShareHelperTest {

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var tempDir: File
    private lateinit var testContext: Context
    private lateinit var fakePoolDao: FakeBlockedPoolDao
    private lateinit var fakeMetaDao: FakeSyncMetadataDao

    @Before
    fun setUp() {
        DynamicConfigManager.reset()
        tempDir = Files.createTempDirectory("antispam_test_offline").toFile()
        testContext = object : ContextWrapper(null) {
            override fun getFilesDir(): File = tempDir
            override fun getPackageName(): String = "ru.antispam.app"
        }
        fakePoolDao = FakeBlockedPoolDao()
        fakeMetaDao = FakeSyncMetadataDao()
    }

    @After
    fun tearDown() {
        DynamicConfigManager.reset()
        tempDir.deleteRecursively()
    }

    private fun generateTestKeypair(): Pair<Ed25519PrivateKeyParameters, Ed25519PublicKeyParameters> {
        val gen = Ed25519KeyPairGenerator()
        gen.init(Ed25519KeyGenerationParameters(SecureRandom()))
        val pair = gen.generateKeyPair()
        return Pair(
            pair.private as Ed25519PrivateKeyParameters,
            pair.public as Ed25519PublicKeyParameters
        )
    }

    private fun buildSampleSignedAspb(
        currentVersion: Int,
        dynamicConfig: DynamicConfig?,
        addedRecords: List<Triple<Long, Long, Byte>>,
        privKey: Ed25519PrivateKeyParameters
    ): ByteArray {
        val configBytes = if (dynamicConfig != null) {
            json.encodeToString(DynamicConfig.serializer(), dynamicConfig).toByteArray(Charsets.UTF_8)
        } else {
            ByteArray(0)
        }
        val configLen = configBytes.size

        val bodyBuffer = ByteBuffer.allocate(configLen + 4 + addedRecords.size * 21 + 4).order(ByteOrder.BIG_ENDIAN)
        if (configLen > 0) {
            bodyBuffer.put(configBytes)
        }
        bodyBuffer.putInt(addedRecords.size)
        for (rec in addedRecords) {
            bodyBuffer.putLong(rec.first)
            bodyBuffer.putLong(rec.second)
            bodyBuffer.put(rec.third)
            bodyBuffer.putInt(currentVersion)
        }
        bodyBuffer.putInt(0) // 0 removed
        val bodyBytes = bodyBuffer.array()

        val signer = Ed25519Signer()
        signer.init(true, privKey)
        signer.update(bodyBytes, 0, bodyBytes.size)
        val signature = signer.generateSignature()

        val flags = (1) or (if (configLen > 0) 4 else 0)
        val headerBuffer = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
        headerBuffer.put("ASPB".toByteArray(Charsets.US_ASCII))
        headerBuffer.putShort(2.toShort()) // proto_ver = 2
        headerBuffer.putInt(currentVersion)
        headerBuffer.putShort(flags.toShort())
        headerBuffer.putInt(configLen)

        return headerBuffer.array() + signature + bodyBytes
    }

    @Test
    fun test_saveAndGetSnapshotFile() {
        val sample = byteArrayOf(1, 2, 3, 4, 5)
        val savedFile = OfflineShareHelper.saveVerifiedSnapshot(testContext, sample)

        assertTrue(savedFile.exists())
        assertEquals("pools.aspb", savedFile.name)
        val readBack = savedFile.readBytes()
        assertEquals(sample.size, readBack.size)
        assertEquals(1.toByte(), readBack[0])
    }

    @Test
    fun test_importFromBytes_validSnapshot_succeeds() = runBlocking {
        val (privKey, pubKey) = generateTestKeypair()
        val pubHex = AspbCryptoVerifier.bytesToHex(pubKey.encoded)
        val signedAspb = buildSampleSignedAspb(
            currentVersion = 55,
            dynamicConfig = null,
            addedRecords = listOf(
                Triple(79001110000L, 79001110099L, 1.toByte()),
                Triple(79002220000L, 79002220099L, 3.toByte())
            ),
            privKey = privKey
        )

        val result = OfflineShareHelper.importFromBytes(
            testContext,
            signedAspb,
            fakePoolDao,
            fakeMetaDao,
            expectedPublicKeyHex = pubHex
        )

        assertTrue("Import should succeed", result is ImportResult.Success)
        val success = result as ImportResult.Success
        assertEquals(55L, success.version)
        assertEquals(2, success.addedCount)
        assertFalse(success.dynamicConfigApplied)

        // Verify database state
        assertEquals(2, fakePoolDao.getCount())
        assertEquals(55L, fakeMetaDao.getValueLong(SyncRepository.KEY_LAST_DELTA_VERSION))
        assertEquals("OFFLINE_P2P", fakeMetaDao.getValueStr(SyncRepository.KEY_LAST_SYNC_SOURCE))

        // Verify cached snapshot on disk
        val cachedSnapshot = OfflineShareHelper.getSnapshotFile(testContext)
        assertTrue(cachedSnapshot.exists())
        assertEquals(signedAspb.size.toLong(), cachedSnapshot.length())
    }

    @Test
    fun test_importFromBytes_withDynamicConfig_appliesConfig() = runBlocking {
        val (privKey, pubKey) = generateTestKeypair()
        val pubHex = AspbCryptoVerifier.bytesToHex(pubKey.encoded)
        val config = DynamicConfig(
            newSpywarePackages = listOf("ru.custom.p2p.spy"),
            dynamicCdnMirrors = listOf("https://p2p.cdn.org/pools.aspb"),
            antifraudSmsKeywords = listOf("код подтверждения p2p")
        )

        val signedAspb = buildSampleSignedAspb(
            currentVersion = 70,
            dynamicConfig = config,
            addedRecords = listOf(Triple(79003330000L, 79003330099L, 1.toByte())),
            privKey = privKey
        )

        val result = OfflineShareHelper.importFromBytes(
            testContext,
            signedAspb,
            fakePoolDao,
            fakeMetaDao,
            expectedPublicKeyHex = pubHex
        )

        assertTrue(result is ImportResult.Success)
        val success = result as ImportResult.Success
        assertTrue(success.dynamicConfigApplied)

        assertEquals(listOf("ru.custom.p2p.spy"), DynamicConfigManager.getDynamicSpywarePackages())
        assertEquals(listOf("https://p2p.cdn.org/pools.aspb"), DynamicConfigManager.getDynamicCdnMirrors())
    }

    @Test
    fun test_importFromBytes_alreadyUpToDate() = runBlocking {
        val (privKey, pubKey) = generateTestKeypair()
        val pubHex = AspbCryptoVerifier.bytesToHex(pubKey.encoded)
        fakeMetaDao.setValueLong(SyncRepository.KEY_LAST_DELTA_VERSION, 70L)
        fakePoolDao.pools.add(BlockedPoolEntity(79001110000L, 79001110099L, null, "SPAM", 0L))

        val signedAspb = buildSampleSignedAspb(
            currentVersion = 70,
            dynamicConfig = null,
            addedRecords = listOf(Triple(79001110000L, 79001110099L, 1.toByte())),
            privKey = privKey
        )

        val result = OfflineShareHelper.importFromBytes(
            testContext,
            signedAspb,
            fakePoolDao,
            fakeMetaDao,
            expectedPublicKeyHex = pubHex
        )
        assertTrue(result is ImportResult.AlreadyUpToDate)
        assertEquals(70L, (result as ImportResult.AlreadyUpToDate).version)
    }

    @Test
    fun test_importFromBytes_rollbackAttack_rejected() = runBlocking {
        val (privKey, pubKey) = generateTestKeypair()
        val pubHex = AspbCryptoVerifier.bytesToHex(pubKey.encoded)
        // Local is version 100
        fakeMetaDao.setValueLong(SyncRepository.KEY_LAST_DELTA_VERSION, 100L)

        // Incoming is older version 45
        val signedAspb = buildSampleSignedAspb(
            currentVersion = 45,
            dynamicConfig = null,
            addedRecords = listOf(Triple(79001110000L, 79001110099L, 1.toByte())),
            privKey = privKey
        )

        val result = OfflineShareHelper.importFromBytes(
            testContext,
            signedAspb,
            fakePoolDao,
            fakeMetaDao,
            expectedPublicKeyHex = pubHex
        )
        assertTrue("Rollback attack must be rejected", result is ImportResult.RollbackError)
        val rollback = result as ImportResult.RollbackError
        assertEquals(45L, rollback.incomingVersion)
        assertEquals(100L, rollback.currentVersion)
    }

    @Test
    fun test_importFromBytes_tamperedSignature_rejected() = runBlocking {
        val (privKey, pubKey) = generateTestKeypair()
        val pubHex = AspbCryptoVerifier.bytesToHex(pubKey.encoded)
        val signedAspb = buildSampleSignedAspb(
            currentVersion = 80,
            dynamicConfig = null,
            addedRecords = listOf(Triple(79001110000L, 79001110099L, 1.toByte())),
            privKey = privKey
        )

        // Mutate payload
        signedAspb[signedAspb.size - 1] = (signedAspb[signedAspb.size - 1].toInt() xor 0xFF).toByte()

        val result = OfflineShareHelper.importFromBytes(
            testContext,
            signedAspb,
            fakePoolDao,
            fakeMetaDao,
            expectedPublicKeyHex = pubHex
        )
        assertTrue(result is ImportResult.SecurityError)
    }

    @Test
    fun test_prepareExportIntent_whenFileMissing_returnsNull() {
        val intent = OfflineShareHelper.prepareExportIntent(testContext)
        assertNull("Should return null when no snapshot exists yet", intent)
    }

    @Test
    fun test_prepareExportIntent_whenFileCorrupted_returnsNull() {
        val corruptedFile = OfflineShareHelper.getSnapshotFile(testContext)
        corruptedFile.writeBytes(ByteArray(100) { 0x42 })

        val intent = OfflineShareHelper.prepareExportIntent(testContext)
        assertNull("Should return null when snapshot is corrupted", intent)
    }

    // --- Fakes for in-memory unit testing ---

    class FakeBlockedPoolDao : BlockedPoolDao {
        val pools = mutableListOf<BlockedPoolEntity>()
        override fun isNumberBlocked(phoneNumber: Long): Boolean = false
        override fun getPoolForNumber(phoneNumber: Long): BlockedPoolEntity? = null
        override fun insertAll(pools: List<BlockedPoolEntity>) { this.pools.addAll(pools) }
        override fun deleteContainedSubranges(start: Long, end: Long) {}
        override fun deleteRange(start: Long, end: Long) {}
        override fun clearAll() { pools.clear() }
        override fun getCount(): Int = pools.size
    }

    class FakeSyncMetadataDao : SyncMetadataDao {
        val dataLong = mutableMapOf<String, Long>()
        val dataStr = mutableMapOf<String, String>()
        override fun getValueLong(key: String): Long? = dataLong[key]
        override fun getValueStr(key: String): String? = dataStr[key]
        override fun set(entity: SyncMetadataEntity) {}
        override fun setValueLong(key: String, value: Long) { dataLong[key] = value }
        override fun setValueStr(key: String, value: String) { dataStr[key] = value }
    }
}
