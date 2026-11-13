package ru.antispam.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.antispam.app.data.local.entity.BlockedPoolEntity

@Dao
interface BlockedPoolDao {

    /**
     * Ultra-fast (< 0.1ms) O(log N) indexed lookup for screening incoming calls.
     * Uses ORDER BY range_start DESC LIMIT 1 to directly navigate to the single candidate leaf node.
     */
    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM (
                SELECT range_end FROM blocked_pools 
                WHERE range_start <= :phoneNumber 
                ORDER BY range_start DESC 
                LIMIT 1
            ) WHERE range_end >= :phoneNumber
        )
    """)
    fun isNumberBlocked(phoneNumber: Long): Boolean

    @Query("""
        SELECT * FROM (
            SELECT * FROM blocked_pools 
            WHERE range_start <= :phoneNumber 
            ORDER BY range_start DESC 
            LIMIT 1
        ) WHERE range_end >= :phoneNumber
    """)
    fun getPoolForNumber(phoneNumber: Long): BlockedPoolEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(pools: List<BlockedPoolEntity>)

    /**
     * Edge Case 1: Guarantee disjoint intervals by removing any nested/contained sub-ranges
     * when a wider pool (MICRO_100 or SUBPOOL_1000) is inserted.
     */
    @Query("""
        DELETE FROM blocked_pools 
        WHERE range_start >= :start AND range_end <= :end 
          AND NOT (range_start = :start AND range_end = :end)
    """)
    fun deleteContainedSubranges(start: Long, end: Long)

    @androidx.room.Transaction
    fun insertWithAbsorption(pools: List<BlockedPoolEntity>) {
        for (pool in pools) {
            deleteContainedSubranges(pool.rangeStart, pool.rangeEnd)
        }
        insertAll(pools)
    }

    @Query("DELETE FROM blocked_pools WHERE range_start = :start AND range_end = :end")
    fun deleteRange(start: Long, end: Long)

    @Query("DELETE FROM blocked_pools")
    fun clearAll()

    @Query("SELECT COUNT(*) FROM blocked_pools")
    fun getCount(): Int
}
