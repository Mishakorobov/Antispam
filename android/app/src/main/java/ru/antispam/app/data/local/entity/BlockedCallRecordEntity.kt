package ru.antispam.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "blocked_call_records",
    indices = [
        Index(value = ["timestamp"], name = "idx_blocked_calls_time"),
        Index(value = ["phone_number"], name = "idx_blocked_calls_phone")
    ]
)
data class BlockedCallRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "phone_number") val phoneNumber: Long,
    @ColumnInfo(name = "formatted_number") val formattedNumber: String,
    @ColumnInfo(name = "operator_name") val operatorName: String?,
    @ColumnInfo(name = "category") val category: String?,
    @ColumnInfo(name = "block_reason") val blockReason: String, // 'SPAM_POOL', 'SPOOFED_CALLER_ID', 'SILENCED_DELIVERY'
    @ColumnInfo(name = "timestamp") val timestamp: Long
)
