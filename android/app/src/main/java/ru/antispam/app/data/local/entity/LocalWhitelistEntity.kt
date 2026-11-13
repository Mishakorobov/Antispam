package ru.antispam.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_whitelist")
data class LocalWhitelistEntity(
    @PrimaryKey
    @ColumnInfo(name = "phone_number") val phoneNumber: Long,
    @ColumnInfo(name = "label") val label: String?,
    @ColumnInfo(name = "added_at") val addedAt: Long
)
