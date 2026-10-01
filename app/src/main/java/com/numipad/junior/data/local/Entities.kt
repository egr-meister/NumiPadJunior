package com.numipad.junior.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Decimal values are canonical strings (BigDecimal.toPlainString without trailing zeros). */
@Entity(tableName = "calculation_history", indices = [Index("createdAt")])
data class CalculationHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val firstOperand: String,
    /** CalcOperator.name */
    val operator: String,
    val secondOperand: String,
    val result: String,
    val rounded: Boolean,
    /** epoch millis */
    val createdAt: Long,
)

object SessionStatus {
    const val ACTIVE = "ACTIVE"
    const val COMPLETED = "COMPLETED"
    /** Ended early by the user: answered questions still count; labelled unfinished. */
    const val ENDED = "ENDED"
}

@Entity(tableName = "practice_session", indices = [Index("status"), Index("startedAt")])
data class PracticeSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Topic.name */
    val topic: String,
    /** Difficulty.name */
    val difficulty: String,
    val startedAt: Long,
    val completedAt: Long? = null,
    val status: String = SessionStatus.ACTIVE,
)

@Entity(
    tableName = "practice_question",
    foreignKeys = [
        ForeignKey(
            entity = PracticeSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["sessionId", "position"], unique = true), Index("answeredLocalDate")],
)
data class PracticeQuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    /** 0-based position within the session */
    val position: Int,
    val firstOperand: Int,
    val secondOperand: Int,
    /** Topic.name of the operation */
    val operator: String,
    val correctAnswer: Int,
    /** Four comma-separated integers in display order. */
    val answerOptions: String,
    val selectedAnswer: Int? = null,
    val answeredAt: Long? = null,
    /** ISO local date (yyyy-MM-dd) captured when answered. */
    val answeredLocalDate: String? = null,
    val isCorrect: Boolean? = null,
)

/** Decoded answer options in display order. */
fun PracticeQuestionEntity.options(): List<Int> = answerOptions.split(',').map { it.trim().toInt() }

@Entity(tableName = "badge")
data class BadgeEntity(
    @PrimaryKey val badgeKey: String,
    val unlockedAt: Long,
)

/** Daily totals of answers from sessions that were pruned (kept so progress never decreases). */
@Entity(tableName = "archived_daily_total", primaryKeys = ["localDate", "topic"])
data class ArchivedDailyTotalEntity(
    val localDate: String,
    val topic: String,
    val answered: Int,
    val correct: Int,
)

/** Single-row counters that outlive pruning. */
@Entity(tableName = "archived_counter")
data class ArchivedCounterEntity(
    @PrimaryKey val counterKey: String,
    @ColumnInfo(defaultValue = "0") val value: Long,
)

/** Projection used for statistics. */
data class AnswerRow(
    val answeredLocalDate: String,
    val operator: String,
    val isCorrect: Boolean,
)
