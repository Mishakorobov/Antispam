package ru.antispam.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import ru.antispam.app.data.local.entity.PendingReportEntity

@Dao
interface PendingReportDao {

    @Insert
    fun insert(report: PendingReportEntity): Long

    @Query("SELECT * FROM pending_reports ORDER BY created_at ASC LIMIT :limit")
    fun getPending(limit: Int = 50): List<PendingReportEntity>

    @Query("DELETE FROM pending_reports WHERE id IN (:ids)")
    fun deleteByIds(ids: List<Int>)

    @Query("SELECT COUNT(*) FROM pending_reports")
    fun getCount(): Int
}
