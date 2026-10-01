package com.numipad.junior.domain.progress

import com.numipad.junior.domain.questions.Topic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ProgressTest {

    private class FakeClock(var instant: Instant, var zoneId: ZoneId) : AppClock {
        override fun now() = instant
        override fun zone() = zoneId
    }

    private val day1 = LocalDate.of(2026, 10, 1)
    private val day2 = LocalDate.of(2026, 10, 2)

    @Test fun accuracyFormulaAndZeroCase() {
        assertNull(accuracyPercent(0, 0))
        assertEquals(70, accuracyPercent(7, 10))
        assertEquals(67, accuracyPercent(2, 3))
        val stats = ProgressCalculator.daily(day1, emptyList())
        assertNull(stats.accuracyPercent)
        assertEquals(0, stats.answered)
    }

    @Test fun dailyStatsWithTopicBreakdown() {
        val facts = listOf(
            AnswerFact(day1, Topic.ADDITION, true),
            AnswerFact(day1, Topic.ADDITION, false),
            AnswerFact(day1, Topic.DIVISION, true),
            AnswerFact(day2, Topic.DIVISION, true),
        )
        val stats = ProgressCalculator.daily(day1, ProgressCalculator.combine(emptyList(), facts))
        assertEquals(3, stats.answered)
        assertEquals(2, stats.correct)
        assertEquals(1, stats.incorrect)
        assertEquals(67, stats.accuracyPercent)
        assertEquals(listOf(Topic.ADDITION, Topic.DIVISION), stats.byTopic.map { it.topic })
        assertEquals(2, stats.byTopic.first().answered)
    }

    @Test fun answersAcrossMidnightGoToTwoDates() {
        val zone = ZoneId.of("Europe/Kyiv")
        val clock = FakeClock(Instant.parse("2026-10-01T20:59:00Z"), zone) // 23:59 local
        val before = AnswerRules.stamp(5, null, 5, clock)!!
        clock.instant = Instant.parse("2026-10-01T21:01:00Z") // 00:01 next day
        val after = AnswerRules.stamp(5, null, 4, clock)!!
        assertEquals(day1, before.localDate)
        assertEquals(day2, after.localDate)
        val totals = ProgressCalculator.combine(
            emptyList(),
            listOf(AnswerFact(before.localDate, Topic.ADDITION, before.isCorrect), AnswerFact(after.localDate, Topic.ADDITION, after.isCorrect)),
        )
        assertEquals(1, ProgressCalculator.daily(day1, totals).answered)
        assertEquals(1, ProgressCalculator.daily(day2, totals).answered)
        assertEquals(0, ProgressCalculator.daily(day2, totals).correct)
    }

    @Test fun storedDateIgnoresLaterZoneChange() {
        val clock = FakeClock(Instant.parse("2026-10-01T22:30:00Z"), ZoneId.of("UTC"))
        val stamp = AnswerRules.stamp(1, null, 1, clock)!!
        assertEquals(day1, stamp.localDate)
        clock.zoneId = ZoneId.of("Asia/Tokyo") // would be Oct 2 there
        // the stored stamp is a value; aggregating uses the stored date
        val totals = ProgressCalculator.combine(emptyList(), listOf(AnswerFact(stamp.localDate, Topic.ADDITION, true)))
        assertEquals(1, ProgressCalculator.daily(day1, totals).answered)
        assertEquals(0, ProgressCalculator.daily(day2, totals).answered)
    }

    @Test fun duplicateAnswerIsRejected() {
        val clock = FakeClock(Instant.parse("2026-10-01T10:00:00Z"), ZoneId.of("UTC"))
        val first = AnswerRules.stamp(12, null, 12, clock)
        assertNotNull(first)
        assertTrue(first!!.isCorrect)
        assertNull(AnswerRules.stamp(12, first.selected, 11, clock))
    }

    @Test fun mostPracticedHandlesNoneSingleAndTies() {
        assertEquals(MostPracticed.None, ProgressCalculator.mostPracticed(emptyList()))
        val single = listOf(
            TopicDayTotal(day1, Topic.ADDITION, 5, 3),
            TopicDayTotal(day2, Topic.ADDITION, 2, 2),
            TopicDayTotal(day1, Topic.DIVISION, 6, 1),
        )
        assertEquals(MostPracticed.Single(Topic.ADDITION, 7), ProgressCalculator.mostPracticed(single))
        val tied = single + TopicDayTotal(day2, Topic.DIVISION, 1, 0)
        assertEquals(MostPracticed.Tied(listOf(Topic.ADDITION, Topic.DIVISION), 7), ProgressCalculator.mostPracticed(tied))
    }

    @Test fun pruningPreservesTotals() {
        val retained = listOf(AnswerFact(day2, Topic.SUBTRACTION, true))
        val pruned = listOf(
            AnswerFact(day1, Topic.ADDITION, true),
            AnswerFact(day1, Topic.ADDITION, false),
            AnswerFact(day1, Topic.MULTIPLICATION, true),
        )
        val existingArchive = listOf(TopicDayTotal(day1, Topic.ADDITION, 4, 4))
        val before = ProgressCalculator.combine(existingArchive, pruned + retained)
        val newArchive = ProgressCalculator.archive(existingArchive, pruned)
        val after = ProgressCalculator.combine(newArchive, retained)
        assertEquals(before.toSet(), after.toSet())
        assertEquals(ProgressCalculator.daily(day1, before), ProgressCalculator.daily(day1, after))
        assertEquals(ProgressCalculator.allTime(before), ProgressCalculator.allTime(after))
        assertEquals(TopicDayTotal(day1, Topic.ADDITION, 6, 5), newArchive.first { it.topic == Topic.ADDITION })
    }

    @Test fun badgeRules() {
        fun earned(total: Int, topics: Set<Topic> = setOf(Topic.ADDITION), sessions: Int = 0) =
            BadgeRules.earned(BadgeInputs(total, topics, sessions))
        assertTrue(earned(0, emptySet()).isEmpty())
        assertEquals(setOf(Badge.FIRST_STEPS), earned(1))
        assertEquals(setOf(Badge.FIRST_STEPS), earned(9))
        assertEquals(setOf(Badge.FIRST_STEPS, Badge.TEN_TRIED), earned(10))
        assertTrue(Badge.MATH_EXPLORER in earned(4, Topic.entries.toSet()))
        assertTrue(Badge.MATH_EXPLORER !in earned(40, setOf(Topic.ADDITION, Topic.SUBTRACTION, Topic.DIVISION)))
        assertTrue(Badge.FIFTY_PRACTICED in earned(50))
        assertTrue(Badge.HUNDRED_PRACTICED !in earned(99))
        assertTrue(Badge.HUNDRED_PRACTICED in earned(100))
        assertTrue(Badge.FULL_SESSION in earned(10, sessions = 1))
    }

    @Test fun badgesUnlockOnce() {
        val inputs = BadgeInputs(10, setOf(Topic.ADDITION), 0)
        val first = BadgeRules.newlyUnlocked(inputs, emptySet())
        assertEquals(setOf(Badge.FIRST_STEPS, Badge.TEN_TRIED), first)
        assertTrue(BadgeRules.newlyUnlocked(inputs, first).isEmpty())
        // participation only: zero correct answers still earns participation badges
        assertEquals(6, Badge.entries.size)
    }
}
