package ru.antispam.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.antispam.app.data.local.entity.SyncMetadataEntity

@Dao
interface SyncMetadataDao {

    @Query("SELECT value_long FROM sync_metadata WHERE `key` = :key LIMIT 1")
    fun getValueLong(key: String): Long?

    @Query("SELECT value_str FROM sync_metadata WHERE `key` = :key LIMIT 1")
    fun getValueStr(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun set(entity: SyncMetadataEntity)

    fun setValueLong(key: String, value: Long) {
        set(SyncMetadataEntity(key = key, valueLong = value))
    }

    fun setValueStr(key: String, value: String) {
        set(SyncMetadataEntity(key = key, valueStr = value))
    }
}
