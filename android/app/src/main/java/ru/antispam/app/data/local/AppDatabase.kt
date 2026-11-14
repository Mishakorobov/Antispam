package ru.antispam.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import ru.antispam.app.data.local.dao.BlockedCallRecordDao
import ru.antispam.app.data.local.dao.BlockedPoolDao
import ru.antispam.app.data.local.dao.LocalWhitelistDao
import ru.antispam.app.data.local.dao.PendingReportDao
import ru.antispam.app.data.local.dao.PrivateCallLogDao
import ru.antispam.app.data.local.dao.SyncMetadataDao
import ru.antispam.app.data.local.entity.BlockedCallRecordEntity
import ru.antispam.app.data.local.entity.BlockedPoolEntity
import ru.antispam.app.data.local.entity.LocalWhitelistEntity
import ru.antispam.app.data.local.entity.PendingReportEntity
import ru.antispam.app.data.local.entity.SyncMetadataEntity
import ru.antispam.app.data.local.entity.VaultCallRecordEntity

@Database(
    entities = [
        BlockedPoolEntity::class,
        LocalWhitelistEntity::class,
        PendingReportEntity::class,
        SyncMetadataEntity::class,
        BlockedCallRecordEntity::class,
        VaultCallRecordEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun blockedPoolDao(): BlockedPoolDao
    abstract fun localWhitelistDao(): LocalWhitelistDao
    abstract fun pendingReportDao(): PendingReportDao
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun blockedCallRecordDao(): BlockedCallRecordDao
    abstract fun privateCallLogDao(): PrivateCallLogDao

    companion object {
        private const val DATABASE_NAME = "antispam_local.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            val builder = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )

            builder.addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    // High-performance SQLite tuning for SLA < 15ms cold & < 1ms warm
                    try {
                        db.execSQL("PRAGMA mmap_size = 67108864;") // 64MB memory-mapped I/O
                        db.execSQL("PRAGMA synchronous = NORMAL;")
                        db.execSQL("PRAGMA temp_store = MEMORY;")
                        db.execSQL("PRAGMA cache_size = -32000;")  // 32MB cache
                    } catch (_: Exception) {
                        // Safe fallback if device kernel limits mmap
                    }
                }
            })

            return builder
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
