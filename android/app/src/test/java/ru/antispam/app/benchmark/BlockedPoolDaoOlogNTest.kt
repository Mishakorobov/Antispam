package ru.antispam.app.benchmark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TreeMap

/**
 * Validates correctness and boundary invariants of the O(log N) indexed search:
 * SELECT * FROM (
 *     SELECT * FROM blocked_pools 
 *     WHERE range_start <= :phoneNumber 
 *     ORDER BY range_start DESC 
 *     LIMIT 1
 * ) WHERE range_end >= :phoneNumber
 */
class BlockedPoolDaoOlogNTest {

    data class MockPool(val rangeStart: Long, val rangeEnd: Long, val operator: String, val category: String)

    private class MockOlogNIndex {
        // Red-Black Tree simulating SQLite B-Tree indexed by range_start
        private val tree = TreeMap<Long, MockPool>()

        fun insert(pool: MockPool) {
            tree[pool.rangeStart] = pool
        }

        fun lookup(phoneNumber: Long): MockPool? {
            // Equivalent to WHERE range_start <= :phoneNumber ORDER BY range_start DESC LIMIT 1
            val entry = tree.floorEntry(phoneNumber) ?: return null
            val candidate = entry.value
            // Equivalent to outer WHERE range_end >= :phoneNumber
            return if (candidate.rangeEnd >= phoneNumber) candidate else null
        }

        fun isBlocked(phoneNumber: Long): Boolean = lookup(phoneNumber) != null
    }

    @Test
    fun testBoundaryConditionsAndGaps() {
        val index = MockOlogNIndex()
        // Pool 1: [79031000000..79031000099]
        index.insert(MockPool(79031000000L, 79031000099L, "Operator A", "SPAM"))
        // Pool 2: [79031000200..79031000299] (Notice gap 100..199)
        index.insert(MockPool(79031000200L, 79031000299L, "Operator B", "SURVEY"))

        // Exact boundaries of Pool 1
        assertTrue("Exact range_start should be blocked", index.isBlocked(79031000000L))
        assertTrue("Exact range_end should be blocked", index.isBlocked(79031000099L))
        assertTrue("Middle of pool should be blocked", index.isBlocked(79031000050L))
        assertEquals("Operator A", index.lookup(79031000050L)?.operator)

        // Points immediately outside Pool 1
        assertFalse("Number before range_start must not be blocked", index.isBlocked(79030999999L))
        assertNull(index.lookup(79030999999L))

        // In the gap between Pool 1 and Pool 2 [100..199]
        assertFalse("1 unit after range_end must not be blocked", index.isBlocked(79031000100L))
        assertFalse("Inside gap must not be blocked", index.isBlocked(79031000150L))
        assertFalse("1 unit before pool 2 must not be blocked", index.isBlocked(79031000199L))

        // Exact boundaries of Pool 2
        assertTrue("Exact range_start of pool 2", index.isBlocked(79031000200L))
        assertTrue("Exact range_end of pool 2", index.isBlocked(79031000299L))
        assertEquals("Operator B", index.lookup(79031000250L)?.operator)

        // Far beyond all pools
        assertFalse("Far after all pools", index.isBlocked(79999999999L))
    }
}
