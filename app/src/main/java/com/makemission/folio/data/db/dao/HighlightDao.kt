package com.makemission.folio.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.makemission.folio.data.db.entity.Highlight
import kotlinx.coroutines.flow.Flow

@Dao
interface HighlightDao {

    @Query("SELECT * FROM highlights WHERE bookId = :bookId ORDER BY createdAt ASC")
    fun observeForBook(bookId: String): Flow<List<Highlight>>

    @Query("SELECT * FROM highlights WHERE bookId = :bookId ORDER BY createdAt ASC")
    suspend fun getForBook(bookId: String): List<Highlight>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(highlight: Highlight): Long

    @Update
    suspend fun update(highlight: Highlight)

    @Delete
    suspend fun delete(highlight: Highlight)

    @Query("SELECT * FROM highlights ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Highlight>>

    @Query("SELECT * FROM highlights ORDER BY createdAt DESC")
    suspend fun getAll(): List<Highlight>

    @Query("SELECT COUNT(*) FROM highlights")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM highlights")
    suspend fun countAll(): Int

    @Query("DELETE FROM highlights WHERE bookId = :bookId")
    suspend fun clearForBook(bookId: String)
}
