package com.example.autoledger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [LedgerEntry::class, ReviewDraftEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun dao(): LedgerDao

    /** P1：复核草稿持久化。 */
    abstract fun reviewDraftDao(): ReviewDraftDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** v1→v2：新增 direction 列（0=支出 1=收入），旧数据默认支出。 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ledger_entries ADD COLUMN direction INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v2→v3：新增 review_drafts 表（复核草稿持久化，P1）。 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS review_drafts (
                        id TEXT NOT NULL PRIMARY KEY,
                        success INTEGER NOT NULL,
                        rawText TEXT NOT NULL,
                        confidence REAL NOT NULL,
                        imagePath TEXT,
                        candidateMoneyList TEXT NOT NULL,
                        suggestMoney REAL,
                        merchantCandidates TEXT NOT NULL,
                        suggestMerchant TEXT,
                        tradeTime TEXT,
                        tradeType TEXT NOT NULL,
                        platform TEXT NOT NULL,
                        category TEXT,
                        warningMsg TEXT,
                        source TEXT NOT NULL,
                        contextDate TEXT,
                        createdAt INTEGER NOT NULL
                    )
                    """,
                )
            }
        }

        fun build(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                val inst = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "autoledger.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = inst
                inst
            }
    }
}
