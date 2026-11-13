package ru.antispam.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ru.antispam.app.data.local.entity.VaultCallRecordEntity

@Dao
interface PrivateCallLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRecord(record: VaultCallRecordEntity): Long

    @Query("SELECT * FROM vault_call_records ORDER BY timestamp DESC")
    fun getAllRecords(): List<VaultCallRecordEntity>

    @Query("SELECT * FROM vault_call_records ORDER BY timestamp DESC")
    fun getAllRecordsFlow(): Flow<List<VaultCallRecordEntity>>

    @Query("UPDATE vault_call_records SET is_scrubbed_from_system = :scrubbed WHERE id = :id")
    fun updateScrubbedStatus(id: Long, scrubbed: Boolean)

    @Query("DELETE FROM vault_call_records WHERE id = :id")
    fun deleteRecord(id: Long)

    @Query("DELETE FROM vault_call_records")
    fun clearAll()

    @Query("SELECT COUNT(*) FROM vault_call_records")
    fun getCount(): Int
}
