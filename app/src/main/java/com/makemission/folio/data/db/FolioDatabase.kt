package com.makemission.folio.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.makemission.folio.data.db.dao.ReadingProgressDao
import com.makemission.folio.data.db.entity.ReadingProgress

@Database(
    entities = [ReadingProgress::class],
    version = 1,
    exportSchema = false,
)
abstract class FolioDatabase : RoomDatabase() {

    abstract fun readingProgressDao(): ReadingProgressDao

    companion object {
        @Volatile
        private var INSTANCE: FolioDatabase? = null

        fun get(context: Context): FolioDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FolioDatabase::class.java,
                    "folio.db",
                ).build().also { INSTANCE = it }
            }
    }
}
