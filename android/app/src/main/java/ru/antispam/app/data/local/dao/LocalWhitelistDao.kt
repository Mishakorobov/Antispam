package ru.antispam.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.antispam.app.data.local.entity.LocalWhitelistEntity

@Dao
interface LocalWhitelistDao {

    @Query("SELECT EXISTS(SELECT 1 FROM local_whitelist WHERE phone_number = :phoneNumber)")
    fun isWhitelisted(phoneNumber: Long): Boolean

    @Query("SELECT * FROM local_whitelist ORDER BY added_at DESC")
    fun getAll(): List<LocalWhitelistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun add(entry: LocalWhitelistEntity)

    @Query("DELETE FROM local_whitelist WHERE phone_number = :phoneNumber")
    fun remove(phoneNumber: Long)
}
