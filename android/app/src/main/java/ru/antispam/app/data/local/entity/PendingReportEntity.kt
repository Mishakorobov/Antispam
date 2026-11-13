package ru.antispam.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_reports")
data class PendingReportEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Int = 0,
    @ColumnInfo(name = "phone_number") val phoneNumber: Long,
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "call_duration_seconds") val callDurationSeconds: Int?,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
