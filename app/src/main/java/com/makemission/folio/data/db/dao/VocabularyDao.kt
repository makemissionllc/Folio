package com.makemission.folio.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.makemission.folio.data.db.entity.VocabularyCard
import kotlinx.coroutines.flow.Flow

@Dao
interface VocabularyDao {

    @Query("SELECT * FROM vocabulary ORDER BY dueAt ASC, word ASC")
    fun observeAll(): Flow<List<VocabularyCard>>

    @Query("SELECT * FROM vocabulary WHERE dueAt <= :now ORDER BY dueAt ASC")
    fun observeDue(now: Long = System.currentTimeMillis()): Flow<List<VocabularyCard>>

    @Query("SELECT * FROM vocabulary WHERE dueAt <= :now ORDER BY dueAt ASC")
    suspend fun getDue(now: Long = System.currentTimeMillis()): List<VocabularyCard>

    @Query("SELECT * FROM vocabulary WHERE word = :word LIMIT 1")
    suspend fun getByWord(word: String): VocabularyCard?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(card: VocabularyCard)

    @Query("DELETE FROM vocabulary WHERE word = :word")
    suspend fun delete(word: String)

    @Query("SELECT COUNT(*) FROM vocabulary WHERE dueAt <= :now")
    fun observeDueCount(now: Long = System.currentTimeMillis()): Flow<Int>

    @Query("SELECT COUNT(*) FROM vocabulary")
    fun observeTotalCount(): Flow<Int>
}
