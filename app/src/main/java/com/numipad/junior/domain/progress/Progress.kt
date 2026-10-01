package com.numipad.junior.domain.progress

import com.numipad.junior.domain.questions.Topic
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Injectable clock so tests can control "now" and the time zone. */
interface AppClock {
    fun now(): Instant
    fun zone(): ZoneId
    fun today(): LocalDate = now().atZone(zone()).toLocalDate()
}

object SystemAppClock : AppClock {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

/** One answered question as far as statistics are concerned. */
data class AnswerFact(val localDate: LocalDate, val topic: Topic, val correct: Boolean)

/** Aggregated counts for one (date, topic). Used for archived (pruned) sessions. */
data class TopicDayTotal(val localDate: LocalDate, val topic: Topic, val answered: Int, val correct: Int)

data class TopicCount(val topic: Topic, val answered: Int, val correct: Int)

data class DailyStats(
    val date: LocalDate,
    val answered: Int,
    val correct: Int,
    val byTopic: List<TopicCount>,
) {
    val incorrect: Int get() = answered - correct

    /** null when nothing has been answered — show "No answers yet", never 0 %. */
    val accuracyPercent: Int? get() = accuracyPercent(correct, answered)
}

fun accuracyPercent(correct: Int, answered: Int): Int? =
    if (answered <= 0) null else Math.round(correct * 100.0 / answered).toInt()

sealed interface MostPracticed {
    data object None : MostPracticed
    data class Single(val topic: Topic, val answered: Int) : MostPracticed
    data class Tied(val topics: List<Topic>, val answered: Int) : MostPracticed
}

object ProgressCalculator {

    /** Merge archived totals with live answer facts into per-(date, topic) totals. */
    fun combine(archived: List<TopicDayTotal>, live: List<AnswerFact>): List<TopicDayTotal> {
        val map = LinkedHashMap<Pair<LocalDate, Topic>, IntArray>()
        archived.forEach { t ->
            val arr = map.getOrPut(t.localDate to t.topic) { IntArray(2) }
            arr[0] += t.answered; arr[1] += t.correct
        }
        live.forEach { f ->
            val arr = map.getOrPut(f.localDate to f.topic) { IntArray(2) }
            arr[0] += 1; if (f.correct) arr[1] += 1
        }
        return map.map { (k, v) -> TopicDayTotal(k.first, k.second, v[0], v[1]) }
    }

    fun daily(date: LocalDate, totals: List<TopicDayTotal>): DailyStats {
        val day = totals.filter { it.localDate == date }
        val byTopic = Topic.entries.mapNotNull { topic ->
            val rows = day.filter { it.topic == topic }
            val answered = rows.sumOf { it.answered }
            if (answered == 0) null else TopicCount(topic, answered, rows.sumOf { it.correct })
        }
        return DailyStats(date, day.sumOf { it.answered }, day.sumOf { it.correct }, byTopic)
    }

    fun allTime(totals: List<TopicDayTotal>): List<TopicCount> = Topic.entries.map { topic ->
        val rows = totals.filter { it.topic == topic }
        TopicCount(topic, rows.sumOf { it.answered }, rows.sumOf { it.correct })
    }

    fun mostPracticed(totals: List<TopicDayTotal>): MostPracticed {
        val counts = allTime(totals).filter { it.answered > 0 }
        if (counts.isEmpty()) return MostPracticed.None
        val max = counts.maxOf { it.answered }
        val top = counts.filter { it.answered == max }.map { it.topic }
        return if (top.size == 1) MostPracticed.Single(top.first(), max) else MostPracticed.Tied(top, max)
    }

    /**
     * Folds answers of sessions that are about to be pruned into the archive, so
     * displayed totals never decrease.
     */
    fun archive(existing: List<TopicDayTotal>, pruned: List<AnswerFact>): List<TopicDayTotal> =
        combine(existing, pruned)
}

/** Fixed local badge set. Participation only — no speed, accuracy or money. */
enum class Badge(val title: String, val criteria: String) {
    FIRST_STEPS("First Steps", "Answer your first practice question."),
    TEN_TRIED("Ten Tried", "Answer 10 practice questions."),
    MATH_EXPLORER("Math Explorer", "Answer at least one question in all four topics."),
    FIFTY_PRACTICED("Fifty Practiced", "Answer 50 practice questions."),
    HUNDRED_PRACTICED("Hundred Practiced", "Answer 100 practice questions."),
    FULL_SESSION("Full Session", "Finish a 10-question session.");

    companion object {
        fun fromKey(key: String): Badge? = entries.firstOrNull { it.name == key }
    }
}

data class BadgeInputs(
    val totalAnswered: Int,
    val topicsAnswered: Set<Topic>,
    val completedSessions: Int,
)

object BadgeRules {
    fun earned(inputs: BadgeInputs): Set<Badge> = buildSet {
        if (inputs.totalAnswered >= 1) add(Badge.FIRST_STEPS)
        if (inputs.totalAnswered >= 10) add(Badge.TEN_TRIED)
        if (inputs.topicsAnswered.containsAll(Topic.entries)) add(Badge.MATH_EXPLORER)
        if (inputs.totalAnswered >= 50) add(Badge.FIFTY_PRACTICED)
        if (inputs.totalAnswered >= 100) add(Badge.HUNDRED_PRACTICED)
        if (inputs.completedSessions >= 1) add(Badge.FULL_SESSION)
    }

    /** Badges to unlock now: earned but not yet stored. Unlocks are never revoked. */
    fun newlyUnlocked(inputs: BadgeInputs, alreadyUnlocked: Set<Badge>): Set<Badge> =
        earned(inputs) - alreadyUnlocked
}

/** What gets persisted when a question is answered. */
data class AnswerStamp(
    val selected: Int,
    val isCorrect: Boolean,
    val answeredAt: Instant,
    /** Local date at the moment of answering; stored so later zone changes never move it. */
    val localDate: LocalDate,
)

object AnswerRules {
    /** Returns null when the question already has an answer: each question counts once. */
    fun stamp(correctAnswer: Int, previousSelection: Int?, selected: Int, clock: AppClock): AnswerStamp? {
        if (previousSelection != null) return null
        val now = clock.now()
        return AnswerStamp(selected, selected == correctAnswer, now, now.atZone(clock.zone()).toLocalDate())
    }
}
