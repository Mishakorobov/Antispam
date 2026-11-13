package ru.antispam.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey
    @ColumnInfo(name = "key") val key: String,
    @ColumnInfo(name = "value_long") val valueLong: Long? = null,
    @ColumnInfo(name = "value_str") val valueStr: String? = null
)
