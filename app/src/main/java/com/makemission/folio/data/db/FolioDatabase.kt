package com.makemission.folio.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.makemission.folio.data.db.dao.BookDao
import com.makemission.folio.data.db.dao.HighlightDao
import com.makemission.folio.data.db.dao.ReadingProgressDao
import com.makemission.folio.data.db.entity.BookEntity
import com.makemission.folio.data.db.entity.Highlight
import com.makemission.folio.data.db.entity.ReadingProgress

@Database(
    entities = [ReadingProgress::class, Highlight::class, BookEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class FolioDatabase : RoomDatabase() {

    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun highlightDao(): HighlightDao
    abstract fun bookDao(): BookDao

    companion object {
        @Volatile
        private var INSTANCE: FolioDatabase? = null

        fun get(context: Context): FolioDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FolioDatabase::class.java,
                    "folio.db",
                )
                    .fallbackToDestructiveMigration(true)
                    .build().also { INSTANCE = it }
            }
    }
}
