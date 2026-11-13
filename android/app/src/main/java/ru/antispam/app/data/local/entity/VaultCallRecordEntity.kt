package ru.antispam.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vault_call_records",
    indices = [
        Index(value = ["phone_number"], name = "idx_vault_phone"),
        Index(value = ["timestamp"], name = "idx_vault_timestamp")
    ]
)
data class VaultCallRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "phone_number") val phoneNumber: Long,
    @ColumnInfo(name = "formatted_number") val formattedNumber: String,
    @ColumnInfo(name = "caller_name") val callerName: String?,
    @ColumnInfo(name = "call_type") val callType: Int, // 1 = INCOMING, 2 = OUTGOING, 3 = MISSED
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Int,
    @ColumnInfo(name = "is_scrubbed_from_system") val isScrubbedFromSystem: Boolean = true
)
