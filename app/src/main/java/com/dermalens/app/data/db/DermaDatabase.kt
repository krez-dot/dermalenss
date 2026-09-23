package com.dermalens.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.dermalens.app.data.model.ScanRecord
import com.dermalens.app.data.model.User

@Database(entities = [User::class, ScanRecord::class], version = 7, exportSchema = true)
abstract class DermaDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun scanRecordDao(): ScanRecordDao

    companion object {
        @Volatile
        private var INSTANCE: DermaDatabase? = null

        fun getDatabase(context: Context): DermaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DermaDatabase::class.java,
                    "dermalens_database"
                )
                    // Versions 1-6 were all pre-launch dev builds (no real user ever ran one, see
                    // HANDOFF.md's DB Version History) -- destructively wiping those specific
                    // upgrades is fine. But blanket fallbackToDestructiveMigration() would also
                    // silently wipe every real user's data on the *next* schema bump after launch
                    // (PRELAUNCH_AUDIT_2026-09-21.md #10). Scoping the fallback to only these
                    // known-dev versions means any future v7->v8+ bump without a real Migration
                    // throws IllegalStateException instead of quietly deleting everything -- a
                    // build-time-visible failure that forces a real migration to be written,
                    // rather than a silent data-loss bug nobody notices until support tickets show up.
                    .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5, 6)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}