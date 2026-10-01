package com.numipad.junior.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM calculation_history ORDER BY createdAt DESC, id DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<CalculationHistoryEntity>>

    @Query("SELECT * FROM calculation_history WHERE id = :id")
    suspend fun get(id: Long): CalculationHistoryEntity?

    @Insert
    suspend fun insert(entity: CalculationHistoryEntity): Long

    /** Keep only the newest [keep] rows. */
    @Query(
        "DELETE FROM calculation_history WHERE id NOT IN " +
            "(SELECT id FROM calculation_history ORDER BY createdAt DESC, id DESC LIMIT :keep)",
    )
    suspend fun trim(keep: Int)

    @Query("DELETE FROM calculation_history")
    suspend fun clear()
}

@Dao
interface PracticeDao {
    @Insert
    suspend fun insertSession(session: PracticeSessionEntity): Long

    @Insert
    suspend fun insertQuestions(questions: List<PracticeQuestionEntity>)

    @Query("SELECT * FROM practice_session WHERE id = :id")
    suspend fun getSession(id: Long): PracticeSessionEntity?

    @Query("SELECT * FROM practice_session WHERE id = :id")
    fun observeSession(id: Long): Flow<PracticeSessionEntity?>

    @Query("SELECT * FROM practice_session WHERE status = 'ACTIVE' ORDER BY startedAt DESC LIMIT 1")
    fun observeActiveSession(): Flow<PracticeSessionEntity?>

    @Query("SELECT * FROM practice_session WHERE status = 'ACTIVE' ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActiveSession(): PracticeSessionEntity?

    @Query("SELECT * FROM practice_question WHERE sessionId = :sessionId ORDER BY position")
    fun observeQuestions(sessionId: Long): Flow<List<PracticeQuestionEntity>>

    @Query("SELECT * FROM practice_question WHERE sessionId = :sessionId ORDER BY position")
    suspend fun getQuestions(sessionId: Long): List<PracticeQuestionEntity>

    @Query("SELECT * FROM practice_question WHERE id = :id")
    suspend fun getQuestion(id: Long): PracticeQuestionEntity?

    /** Guarded write: only succeeds for an unanswered question (returns rows changed). */
    @Query(
        "UPDATE practice_question SET selectedAnswer = :selected, answeredAt = :answeredAt, " +
            "answeredLocalDate = :localDate, isCorrect = :isCorrect " +
            "WHERE id = :id AND selectedAnswer IS NULL",
    )
    suspend fun recordAnswer(id: Long, selected: Int, answeredAt: Long, localDate: String, isCorrect: Boolean): Int

    @Query("UPDATE practice_session SET status = :status, completedAt = :completedAt WHERE id = :id AND status = 'ACTIVE'")
    suspend fun closeSession(id: Long, status: String, completedAt: Long): Int

    /** Most recent finished-or-ended session of a topic/difficulty, for repeat avoidance. */
    @Query(
        "SELECT * FROM practice_session WHERE topic = :topic AND difficulty = :difficulty " +
            "ORDER BY startedAt DESC, id DESC LIMIT 1",
    )
    suspend fun latestSession(topic: String, difficulty: String): PracticeSessionEntity?

    @Query(
        "SELECT answeredLocalDate, operator, isCorrect FROM practice_question " +
            "WHERE selectedAnswer IS NOT NULL AND answeredLocalDate IS NOT NULL",
    )
    fun observeAnswers(): Flow<List<AnswerRow>>

    @Query(
        "SELECT answeredLocalDate, operator, isCorrect FROM practice_question " +
            "WHERE selectedAnswer IS NOT NULL AND answeredLocalDate IS NOT NULL",
    )
    suspend fun getAnswers(): List<AnswerRow>

    @Query("SELECT COUNT(*) FROM practice_session WHERE status = 'COMPLETED'")
    suspend fun completedSessionCount(): Int

    // ---- pruning -----------------------------------------------------------
    @Query(
        "SELECT id FROM practice_session WHERE status != 'ACTIVE' AND id NOT IN " +
            "(SELECT id FROM practice_session ORDER BY startedAt DESC, id DESC LIMIT :keep)",
    )
    suspend fun sessionIdsBeyond(keep: Int): List<Long>

    @Query(
        "SELECT answeredLocalDate, operator, isCorrect FROM practice_question " +
            "WHERE sessionId IN (:ids) AND selectedAnswer IS NOT NULL AND answeredLocalDate IS NOT NULL",
    )
    suspend fun answersForSessions(ids: List<Long>): List<AnswerRow>

    @Query("SELECT COUNT(*) FROM practice_session WHERE id IN (:ids) AND status = 'COMPLETED'")
    suspend fun completedAmong(ids: List<Long>): Int

    @Query("DELETE FROM practice_session WHERE id IN (:ids)")
    suspend fun deleteSessions(ids: List<Long>)

    @Query("DELETE FROM practice_session")
    suspend fun clearSessions()
}

@Dao
interface ArchiveDao {
    @Query("SELECT * FROM archived_daily_total")
    fun observeTotals(): Flow<List<ArchivedDailyTotalEntity>>

    @Query("SELECT * FROM archived_daily_total")
    suspend fun getTotals(): List<ArchivedDailyTotalEntity>

    @Upsert
    suspend fun upsertTotals(rows: List<ArchivedDailyTotalEntity>)

    @Query("SELECT value FROM archived_counter WHERE counterKey = :key")
    suspend fun counter(key: String): Long?

    @Upsert
    suspend fun upsertCounter(row: ArchivedCounterEntity)

    @Query("DELETE FROM archived_daily_total")
    suspend fun clearTotals()

    @Query("DELETE FROM archived_counter")
    suspend fun clearCounters()
}

@Dao
interface BadgeDao {
    @Query("SELECT * FROM badge")
    fun observeAll(): Flow<List<BadgeEntity>>

    @Query("SELECT * FROM badge")
    suspend fun getAll(): List<BadgeEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(badges: List<BadgeEntity>)

    @Query("DELETE FROM badge")
    suspend fun clear()
}
