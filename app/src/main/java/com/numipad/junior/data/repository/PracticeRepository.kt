package com.numipad.junior.data.repository

import androidx.room.withTransaction
import com.numipad.junior.data.local.AnswerRow
import com.numipad.junior.data.local.ArchivedCounterEntity
import com.numipad.junior.data.local.ArchivedDailyTotalEntity
import com.numipad.junior.data.local.BadgeEntity
import com.numipad.junior.data.local.NumiPadDatabase
import com.numipad.junior.data.local.PracticeQuestionEntity
import com.numipad.junior.data.local.PracticeSessionEntity
import com.numipad.junior.data.local.SessionStatus
import com.numipad.junior.data.local.options
import com.numipad.junior.domain.progress.AnswerFact
import com.numipad.junior.domain.progress.AnswerRules
import com.numipad.junior.domain.progress.AppClock
import com.numipad.junior.domain.progress.Badge
import com.numipad.junior.domain.progress.BadgeInputs
import com.numipad.junior.domain.progress.BadgeRules
import com.numipad.junior.domain.progress.ProgressCalculator
import com.numipad.junior.domain.progress.TopicDayTotal
import com.numipad.junior.domain.questions.Difficulty
import com.numipad.junior.domain.questions.Explanations
import com.numipad.junior.domain.questions.QUESTIONS_PER_SESSION
import com.numipad.junior.domain.questions.QuestionGenerator
import com.numipad.junior.domain.questions.QuestionKey
import com.numipad.junior.domain.questions.Topic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class SessionInfo(
    val id: Long,
    val topic: Topic,
    val difficulty: Difficulty,
    val startedAt: Long,
    val completedAt: Long?,
    val status: String,
) {
    val isActive: Boolean get() = status == SessionStatus.ACTIVE
    val isUnfinished: Boolean get() = status == SessionStatus.ENDED
}

data class QuestionItem(
    val id: Long,
    val position: Int,
    val topic: Topic,
    val first: Int,
    val second: Int,
    val correctAnswer: Int,
    val options: List<Int>,
    val selectedAnswer: Int?,
    val isCorrect: Boolean?,
) {
    val answered: Boolean get() = selectedAnswer != null
    val expression: String get() = "$first ${topic.symbol} $second"
    val spokenExpression: String get() = "$first ${topic.spokenSymbol} $second"
    val explanation: String get() = Explanations.explain(topic, first, second)
}

data class SessionDetail(val session: SessionInfo, val questions: List<QuestionItem>) {
    val answeredCount: Int get() = questions.count { it.answered }
    val correctCount: Int get() = questions.count { it.isCorrect == true }
    val incorrectCount: Int get() = questions.count { it.isCorrect == false }
    /** First unanswered question, or the last one when all are answered. */
    val currentIndex: Int get() = questions.indexOfFirst { !it.answered }.let { if (it < 0) questions.lastIndex else it }
    val allAnswered: Boolean get() = questions.isNotEmpty() && questions.all { it.answered }
}

data class UnlockedBadge(val badge: Badge, val unlockedAt: Long)

sealed interface StartResult {
    data class Started(val sessionId: Long) : StartResult
    data class ActiveExists(val sessionId: Long) : StartResult
}

class PracticeRepository(
    private val db: NumiPadDatabase,
    private val generator: QuestionGenerator,
    private val clock: AppClock,
) {
    private val practice = db.practiceDao()
    private val archive = db.archiveDao()
    private val badges = db.badgeDao()

    val activeSession: Flow<SessionInfo?> = practice.observeActiveSession().map { it?.toInfo() }

    /** Per-(date, topic) totals: archived (pruned) totals + live answer records. */
    val totals: Flow<List<TopicDayTotal>> =
        combine(archive.observeTotals(), practice.observeAnswers()) { archived, answers ->
            ProgressCalculator.combine(archived.mapNotNull { it.toTotal() }, answers.mapNotNull { it.toFact() })
        }

    val unlockedBadges: Flow<List<UnlockedBadge>> = badges.observeAll().map { rows ->
        rows.mapNotNull { row -> Badge.fromKey(row.badgeKey)?.let { UnlockedBadge(it, row.unlockedAt) } }
    }

    fun observeSession(sessionId: Long): Flow<SessionDetail?> =
        combine(practice.observeSession(sessionId), practice.observeQuestions(sessionId)) { s, qs ->
            s?.let { SessionDetail(it.toInfo(), qs.mapNotNull { q -> q.toItem() }) }
        }

    fun today(): LocalDate = clock.today()

    /**
     * Generates and persists all ten questions (with their option order) before
     * anything is shown, so rotation or process death never changes them.
     */
    suspend fun startSession(topic: Topic, difficulty: Difficulty): StartResult = db.withTransaction {
        practice.getActiveSession()?.let { return@withTransaction StartResult.ActiveExists(it.id) }
        val avoid: Set<QuestionKey> = practice.latestSession(topic.name, difficulty.name)?.let { prev ->
            practice.getQuestions(prev.id).map { QuestionKey.of(topic, it.firstOperand, it.secondOperand) }.toSet()
        } ?: emptySet()
        val questions = generator.generateSession(topic, difficulty, QUESTIONS_PER_SESSION, avoid)
        val sessionId = practice.insertSession(
            PracticeSessionEntity(
                topic = topic.name,
                difficulty = difficulty.name,
                startedAt = clock.now().toEpochMilli(),
            ),
        )
        practice.insertQuestions(
            questions.mapIndexed { index, q ->
                PracticeQuestionEntity(
                    sessionId = sessionId,
                    position = index,
                    firstOperand = q.first,
                    secondOperand = q.second,
                    operator = topic.name,
                    correctAnswer = q.correctAnswer,
                    answerOptions = q.options.joinToString(","),
                )
            },
        )
        StartResult.Started(sessionId)
    }

    /** Records the first answer only; returns false when the question was already answered. */
    suspend fun answer(questionId: Long, selected: Int): Boolean = db.withTransaction {
        val q = practice.getQuestion(questionId) ?: return@withTransaction false
        val session = practice.getSession(q.sessionId) ?: return@withTransaction false
        if (session.status != SessionStatus.ACTIVE) return@withTransaction false
        if (selected !in q.options()) return@withTransaction false
        val stamp = AnswerRules.stamp(q.correctAnswer, q.selectedAnswer, selected, clock)
            ?: return@withTransaction false
        val changed = practice.recordAnswer(
            id = questionId,
            selected = stamp.selected,
            answeredAt = stamp.answeredAt.toEpochMilli(),
            localDate = stamp.localDate.toString(),
            isCorrect = stamp.isCorrect,
        )
        if (changed == 1) unlockBadgesLocked()
        changed == 1
    }

    /** Marks a fully answered session complete. */
    suspend fun finishSession(sessionId: Long): Boolean = db.withTransaction {
        val qs = practice.getQuestions(sessionId)
        if (qs.isEmpty() || qs.any { it.selectedAnswer == null }) return@withTransaction false
        val changed = practice.closeSession(sessionId, SessionStatus.COMPLETED, clock.now().toEpochMilli())
        if (changed == 1) {
            unlockBadgesLocked()
            pruneLocked()
        }
        true
    }

    /** Ends early; answered questions remain in progress, unanswered ones are not errors. */
    suspend fun endSession(sessionId: Long) = db.withTransaction {
        val changed = practice.closeSession(sessionId, SessionStatus.ENDED, clock.now().toEpochMilli())
        if (changed == 1) pruneLocked()
        Unit
    }

    /** Must be called inside a transaction. */
    private suspend fun unlockBadgesLocked() {
        val archived = archive.getTotals().mapNotNull { it.toTotal() }
        val live = practice.getAnswers().mapNotNull { it.toFact() }
        val totals = ProgressCalculator.combine(archived, live)
        val completed = practice.completedSessionCount() + (archive.counter(COUNTER_COMPLETED) ?: 0L).toInt()
        val inputs = BadgeInputs(
            totalAnswered = totals.sumOf { it.answered },
            topicsAnswered = totals.filter { it.answered > 0 }.map { it.topic }.toSet(),
            completedSessions = completed,
        )
        val already = badges.getAll().mapNotNull { Badge.fromKey(it.badgeKey) }.toSet()
        val now = clock.now().toEpochMilli()
        val fresh = BadgeRules.newlyUnlocked(inputs, already)
        if (fresh.isNotEmpty()) badges.insert(fresh.map { BadgeEntity(it.name, now) })
    }

    /** Keeps the latest [MAX_SESSIONS] sessions, folding older answers into the archive first. */
    private suspend fun pruneLocked() {
        val ids = practice.sessionIdsBeyond(MAX_SESSIONS)
        if (ids.isEmpty()) return
        val prunedFacts = practice.answersForSessions(ids).mapNotNull { it.toFact() }
        val existing = archive.getTotals().mapNotNull { it.toTotal() }
        val merged = ProgressCalculator.archive(existing, prunedFacts)
        archive.upsertTotals(
            merged.map { ArchivedDailyTotalEntity(it.localDate.toString(), it.topic.name, it.answered, it.correct) },
        )
        val completedPruned = practice.completedAmong(ids)
        if (completedPruned > 0) {
            val current = archive.counter(COUNTER_COMPLETED) ?: 0L
            archive.upsertCounter(ArchivedCounterEntity(COUNTER_COMPLETED, current + completedPruned))
        }
        practice.deleteSessions(ids)
    }

    suspend fun clearProgress() = db.withTransaction {
        practice.clearSessions()
        archive.clearTotals()
        archive.clearCounters()
        badges.clear()
    }

    private fun PracticeSessionEntity.toInfo(): SessionInfo = SessionInfo(
        id = id,
        topic = Topic.fromName(topic) ?: Topic.ADDITION,
        difficulty = Difficulty.fromName(difficulty) ?: Difficulty.EASY,
        startedAt = startedAt,
        completedAt = completedAt,
        status = status,
    )

    private fun PracticeQuestionEntity.toItem(): QuestionItem? {
        val t = Topic.fromName(operator) ?: return null
        return QuestionItem(id, position, t, firstOperand, secondOperand, correctAnswer, options(), selectedAnswer, isCorrect)
    }

    private fun AnswerRow.toFact(): AnswerFact? {
        val t = Topic.fromName(operator) ?: return null
        val date = runCatching { LocalDate.parse(answeredLocalDate) }.getOrNull() ?: return null
        return AnswerFact(date, t, isCorrect)
    }

    private fun ArchivedDailyTotalEntity.toTotal(): TopicDayTotal? {
        val t = Topic.fromName(topic) ?: return null
        val date = runCatching { LocalDate.parse(localDate) }.getOrNull() ?: return null
        return TopicDayTotal(date, t, answered, correct)
    }

    companion object {
        const val MAX_SESSIONS = 100
        private const val COUNTER_COMPLETED = "completed_sessions"

        fun formatDate(epochMillis: Long, zone: ZoneId): LocalDate =
            Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
    }
}
