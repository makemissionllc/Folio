package com.makemission.folio.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.makemission.folio.data.db.entity.ReadingProgress
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingProgressDao {

    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId LIMIT 1")
    fun observe(bookId: String): Flow<ReadingProgress?>

    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId LIMIT 1")
    suspend fun get(bookId: String): ReadingProgress?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: ReadingProgress)

    @Query("DELETE FROM reading_progress WHERE bookId = :bookId")
    suspend fun clear(bookId: String)
}
