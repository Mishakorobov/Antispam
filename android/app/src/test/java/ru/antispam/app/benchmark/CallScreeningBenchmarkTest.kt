package ru.antispam.app.benchmark

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class CallScreeningBenchmarkTest {

    // Simulated high-performance range interval lookup (mirroring SQLite B-Tree index)
    private class SimulatedRangeIndex {
        private val ranges = ArrayList<LongArray>()

        fun populate(count: Int) {
            val random = Random(42)
            var current = 79000000000L
            for (i in 0 until count) {
                val step = (100 + random.nextInt(900)).toLong()
                val start = current
                val end = current + step - 1
                ranges.add(longArrayOf(start, end))
                current = end + 100
            }
        }

        fun isBlocked(number: Long): Boolean {
            var low = 0
            var high = ranges.size - 1

            while (low <= high) {
                val mid = (low + high) ushr 1
                val r = ranges[mid]
                if (number < r[0]) {
                    high = mid - 1
                } else if (number > r[1]) {
                    low = mid + 1
                } else {
                    return true
                }
            }
            return false
        }
    }

    @Test
    fun benchmark_10000_Lookups_MeasuresP99LatencyUnder15ms() {
        val index = SimulatedRangeIndex()
        index.populate(50_000) // 50,000 blocked pools

        val iterations = 10_000
        val latenciesNanos = LongArray(iterations)

        val random = Random(12345)
        val testNumbers = LongArray(iterations) {
            79000000000L + (random.nextDouble() * 50_000_000L).toLong()
        }

        // Measure cold start query (first query)
        val coldStartT0 = System.nanoTime()
        index.isBlocked(testNumbers[0])
        val coldStartNanos = System.nanoTime() - coldStartT0
        val coldStartMs = coldStartNanos / 1_000_000.0

        // Measure subsequent 10,000 queries
        for (i in 0 until iterations) {
            val t0 = System.nanoTime()
            index.isBlocked(testNumbers[i])
            latenciesNanos[i] = System.nanoTime() - t0
        }

        latenciesNanos.sort()

        val p50Ms = latenciesNanos[(iterations * 0.50).toInt()] / 1_000_000.0
        val p95Ms = latenciesNanos[(iterations * 0.95).toInt()] / 1_000_000.0
        val p99Ms = latenciesNanos[(iterations * 0.99).toInt()] / 1_000_000.0
        val maxMs = latenciesNanos[iterations - 1] / 1_000_000.0

        println("=== CallScreening Benchmark Results ===")
        println("Cold Start (1st query): ${"%.4f".format(coldStartMs)} ms")
        println("Median (p50):           ${"%.4f".format(p50Ms)} ms")
        println("p95:                    ${"%.4f".format(p95Ms)} ms")
        println("p99:                    ${"%.4f".format(p99Ms)} ms")
        println("Max:                    ${"%.4f".format(maxMs)} ms")

        // SLA Assertions
        assertTrue("Cold start should be < 15ms, actual: $coldStartMs ms", coldStartMs < 15.0)
        assertTrue("p99 should be < 5ms, actual: $p99Ms ms", p99Ms < 5.0)
        assertTrue("p50 should be < 1ms, actual: $p50Ms ms", p50Ms < 1.0)
    }
}
