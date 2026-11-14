package ru.antispam.app.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.antispam.app.data.repository.SyncRepository
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SyncRepositoryTest {

    @Test
    fun test_parseAspbPayload_roundtrip() {
        // Construct a synthetic ASPB binary payload
        val currentVer = 42L
        val isFullSync = true
        val hasMore = false

        val addedRanges = listOf(
            Triple(74951110000L, 74951119999L, 1.toByte()), // SPAM
            Triple(78002000000L, 78002009999L, 2.toByte()), // SURVEY
            Triple(77011230000L, 77011239999L, 7.toByte())  // FOREIGN_PLUS7
        )

        val removedRanges = listOf(
            Pair(74959990000L, 74959999999L)
        )

        // 16 bytes header + 4 + 3 * 21 + 4 + 1 * 20
        val totalSize = 16 + 4 + (addedRanges.size * 21) + 4 + (removedRanges.size * 20)
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)

        // Header
        buffer.put('A'.code.toByte())
        buffer.put('S'.code.toByte())
        buffer.put('P'.code.toByte())
        buffer.put('B'.code.toByte())
        buffer.putShort(1.toShort()) // proto_ver
        buffer.putInt(currentVer.toInt())
        val flags = (if (isFullSync) 1 else 0) or (if (hasMore) 2 else 0)
        buffer.putShort(flags.toShort())
        buffer.putInt(0) // reserved

        // Added chunk
        buffer.putInt(addedRanges.size)
        for ((start, end, cat) in addedRanges) {
            buffer.putLong(start)
            buffer.putLong(end)
            buffer.put(cat)
            buffer.putInt(42) // ver
        }

        // Removed chunk
        buffer.putInt(removedRanges.size)
        for ((start, end) in removedRanges) {
            buffer.putLong(start)
            buffer.putLong(end)
            buffer.putInt(40) // ver
        }

        val rawBytes = buffer.array()

        // Parse using repository parser
        val parsed = SyncRepository.parseAspbPayload(rawBytes)

        assertEquals(1, parsed.protoVersion)
        assertEquals(42L, parsed.currentVersion)
        assertTrue(parsed.isFullSync)
        assertFalse(parsed.hasMore)

        assertEquals(3, parsed.added.size)
        assertEquals(74951110000L, parsed.added[0].rangeStart)
        assertEquals(74951119999L, parsed.added[0].rangeEnd)
        assertEquals("SPAM", parsed.added[0].category)

        assertEquals("SURVEY", parsed.added[1].category)
        assertEquals("FOREIGN_PLUS7", parsed.added[2].category)

        assertEquals(1, parsed.removed.size)
        assertEquals(74959990000L, parsed.removed[0].first)
        assertEquals(74959999999L, parsed.removed[0].second)
    }

    @Test(expected = IllegalArgumentException::class)
    fun test_parseAspbPayload_invalidMagic() {
        val buffer = ByteBuffer.allocate(32).order(ByteOrder.BIG_ENDIAN)
        buffer.put("FAIL".toByteArray())
        SyncRepository.parseAspbPayload(buffer.array())
    }

    @Test(expected = IllegalArgumentException::class)
    fun test_parseAspbPayload_truncatedHeader() {
        val buffer = ByteArray(10)
        SyncRepository.parseAspbPayload(buffer)
    }

    @Test
    fun test_categoryByteToString_mappings() {
        assertEquals("SPAM", SyncRepository.categoryByteToString(1))
        assertEquals("SURVEY", SyncRepository.categoryByteToString(2))
        assertEquals("FINANCIAL", SyncRepository.categoryByteToString(3))
        assertEquals("SPOOFING", SyncRepository.categoryByteToString(4))
        assertEquals("SILENT", SyncRepository.categoryByteToString(5))
        assertEquals("VIP_MODE", SyncRepository.categoryByteToString(6))
        assertEquals("FOREIGN_PLUS7", SyncRepository.categoryByteToString(7))
        assertEquals("SPAM", SyncRepository.categoryByteToString(99)) // fallback default
    }

    @Test
    fun test_defaultCdnUrls_multiVendorResilience() {
        val mirrors = SyncRepository.DEFAULT_CDN_URLS
        assertTrue("Must have at least 4 independent CDN mirrors", mirrors.size >= 4)

        // Verify multi-vendor distinct ASNs and jurisdictions
        assertTrue("Must include GitHub Releases mirror", mirrors.any { it.contains("github.com") && it.contains("releases") })
        assertTrue("Must include jsDelivr Global Edge CDN mirror", mirrors.any { it.contains("cdn.jsdelivr.net") })
        assertTrue("Must include GitHub Raw mirror", mirrors.any { it.contains("raw.githubusercontent.com") })
        assertTrue("Must include GitFlic Russian sovereign hosting mirror", mirrors.any { it.contains("gitflic.ru") })
    }

    @Test
    fun test_prioritizeMirrors_elevatesPreferredMirrorToFirstPosition() {
        val defaultList = listOf(
            "https://mirror1.com/pools.aspb",
            "https://mirror2.com/pools.aspb",
            "https://mirror3.com/pools.aspb"
        )

        // When preferred is null or blank, keep original order
        assertEquals(defaultList, SyncRepository.prioritizeMirrors(null, defaultList))
        assertEquals(defaultList, SyncRepository.prioritizeMirrors("", defaultList))

        // When preferred is in the list, elevate it to first
        val prioritized = SyncRepository.prioritizeMirrors("https://mirror3.com/pools.aspb", defaultList)
        assertEquals("https://mirror3.com/pools.aspb", prioritized[0])
        assertEquals("https://mirror1.com/pools.aspb", prioritized[1])
        assertEquals("https://mirror2.com/pools.aspb", prioritized[2])
        assertEquals(3, prioritized.size)

        // When preferred is unknown/not in the list, fallback to default order
        val unknown = SyncRepository.prioritizeMirrors("https://unknown.com/pools.aspb", defaultList)
        assertEquals(defaultList, unknown)
    }
}
