package com.makemission.folio.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.makemission.folio.data.db.dao.BookDao
import com.makemission.folio.data.db.dao.BookmarkDao
import com.makemission.folio.data.db.dao.HighlightDao
import com.makemission.folio.data.db.dao.ReadingProgressDao
import com.makemission.folio.data.db.dao.VocabularyDao
import com.makemission.folio.data.db.entity.BookEntity
import com.makemission.folio.data.db.entity.Bookmark
import com.makemission.folio.data.db.entity.Highlight
import com.makemission.folio.data.db.entity.ReadingProgress
import com.makemission.folio.data.db.entity.VocabularyCard

private val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE highlights ADD COLUMN paragraphIndex INTEGER NOT NULL DEFAULT -1")
        db.execSQL("ALTER TABLE highlights ADD COLUMN startOffset INTEGER NOT NULL DEFAULT -1")
        db.execSQL("ALTER TABLE highlights ADD COLUMN endOffset INTEGER NOT NULL DEFAULT -1")
    }
}

@Database(
    entities = [ReadingProgress::class, Highlight::class, BookEntity::class, VocabularyCard::class, Bookmark::class],
    version = 11,
    exportSchema = false,
)
abstract class FolioDatabase : RoomDatabase() {

    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun highlightDao(): HighlightDao
    abstract fun bookDao(): BookDao
    abstract fun vocabularyDao(): VocabularyDao
    abstract fun bookmarkDao(): BookmarkDao

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
                    .addMigrations(MIGRATION_10_11)
                    .fallbackToDestructiveMigration(true)
                    .build().also { INSTANCE = it }
            }
    }
}
