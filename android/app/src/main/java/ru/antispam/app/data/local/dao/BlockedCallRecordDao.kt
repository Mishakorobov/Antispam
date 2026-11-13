package ru.antispam.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ru.antispam.app.data.local.entity.BlockedCallRecordEntity

@Dao
interface BlockedCallRecordDao {

    @Insert
    fun insert(record: BlockedCallRecordEntity)

    @Query("SELECT * FROM blocked_call_records ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentBlockedCalls(limit: Int = 50): List<BlockedCallRecordEntity>

    @Query("SELECT * FROM blocked_call_records ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecentBlockedCalls(limit: Int = 50): Flow<List<BlockedCallRecordEntity>>

    @Query("SELECT COUNT(*) FROM blocked_call_records")
    fun getTotalBlockedCount(): Int

    @Query("DELETE FROM blocked_call_records WHERE timestamp < :olderThanTimestamp")
    fun deleteOlderThan(olderThanTimestamp: Long)
}
