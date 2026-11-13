package ru.antispam.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "blocked_pools",
    primaryKeys = ["range_start", "range_end"],
    indices = [Index(value = ["range_start", "range_end"], name = "idx_pools_lookup")]
)
data class BlockedPoolEntity(
    @ColumnInfo(name = "range_start") val rangeStart: Long,
    @ColumnInfo(name = "range_end") val rangeEnd: Long,
    @ColumnInfo(name = "operator") val operator: String?,
    @ColumnInfo(name = "category") val category: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
