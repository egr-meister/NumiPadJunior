package com.numipad.junior.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        CalculationHistoryEntity::class,
        PracticeSessionEntity::class,
        PracticeQuestionEntity::class,
        BadgeEntity::class,
        ArchivedDailyTotalEntity::class,
        ArchivedCounterEntity::class,
    ],
    version = NumiPadDatabase.VERSION,
    exportSchema = true,
)
abstract class NumiPadDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun practiceDao(): PracticeDao
    abstract fun archiveDao(): ArchiveDao
    abstract fun badgeDao(): BadgeDao

    companion object {
        const val VERSION = 1
        private const val NAME = "numipad.db"

        /**
         * Explicit migrations, one per schema bump (schemas are exported to app/schemas).
         * Version 1 is the first release, so the list is empty; add e.g. MIGRATION_1_2 here.
         * Destructive migration is intentionally NOT enabled.
         */
        val MIGRATIONS: Array<Migration> = emptyArray()

        fun create(context: Context): NumiPadDatabase =
            Room.databaseBuilder(context.applicationContext, NumiPadDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
