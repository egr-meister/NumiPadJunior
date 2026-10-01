package com.numipad.junior.domain.questions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionGeneratorTest {

    private val seeds = 0L until 60L

    private fun forAll(block: (Topic, Difficulty, Long) -> Unit) {
        for (topic in Topic.entries) for (difficulty in Difficulty.entries) for (seed in seeds) {
            block(topic, difficulty, seed)
        }
    }

    @Test fun everyQuestionRespectsTopicConstraints() = forAll { topic, difficulty, seed ->
        val qs = QuestionGenerator(SeededRandomProvider(seed)).generateSession(topic, difficulty)
        assertEquals(QUESTIONS_PER_SESSION, qs.size)
        val range = difficulty.range
        for (q in qs) {
            assertEquals(topic, q.topic)
            assertTrue("$q operands in range", q.first in range && q.second in range)
            assertEquals(answerFor(topic, q.first, q.second), q.correctAnswer)
            when (topic) {
                Topic.ADDITION -> assertEquals(q.first + q.second, q.correctAnswer)
                Topic.SUBTRACTION -> assertTrue("$q non-negative", q.correctAnswer >= 0)
                Topic.MULTIPLICATION -> assertTrue(q.correctAnswer <= difficulty.max * difficulty.max)
                Topic.DIVISION -> {
                    assertTrue(q.second > 0)
                    assertEquals("$q no remainder", 0, q.first % q.second)
                    assertTrue(q.correctAnswer > 0)
                    assertEquals(q.first, q.correctAnswer * q.second)
                }
            }
        }
    }

    @Test fun fourDistinctOptionsWithExactlyOneCorrect() = forAll { topic, difficulty, seed ->
        val qs = QuestionGenerator(SeededRandomProvider(seed)).generateSession(topic, difficulty)
        for (q in qs) {
            assertEquals(4, q.options.size)
            assertEquals("$q distinct", 4, q.options.toSet().size)
            assertEquals(1, q.options.count { it == q.correctAnswer })
            val min = if (topic == Topic.ADDITION || topic == Topic.SUBTRACTION) 0 else 1
            assertTrue("$q options >= $min", q.options.all { it >= min })
        }
    }

    @Test fun noDuplicatesWithinSessionIncludingReversedPairs() = forAll { topic, difficulty, seed ->
        val qs = QuestionGenerator(SeededRandomProvider(seed)).generateSession(topic, difficulty)
        assertEquals(qs.size, qs.map { it.key }.toSet().size)
    }

    @Test fun reversedPairsShareKey() {
        assertEquals(QuestionKey.of(Topic.ADDITION, 3, 8), QuestionKey.of(Topic.ADDITION, 8, 3))
        assertEquals(QuestionKey.of(Topic.MULTIPLICATION, 3, 8), QuestionKey.of(Topic.MULTIPLICATION, 8, 3))
        assertTrue(QuestionKey.of(Topic.SUBTRACTION, 8, 3) != QuestionKey.of(Topic.SUBTRACTION, 3, 8))
    }

    @Test fun avoidsPreviousSessionWhenPoolAllows() = forAll { topic, difficulty, seed ->
        val gen = QuestionGenerator(SeededRandomProvider(seed))
        val first = gen.generateSession(topic, difficulty)
        val second = gen.generateSession(topic, difficulty, avoid = first.map { it.key }.toSet())
        val overlap = second.map { it.key }.toSet() intersect first.map { it.key }.toSet()
        // Smallest pool (Easy division) has 27 pairs, so 10 fresh questions always exist.
        assertTrue("$topic $difficulty overlap $overlap", overlap.isEmpty())
    }

    @Test fun smallPoolsTerminateAndWrap() {
        val gen = QuestionGenerator(SeededRandomProvider(1))
        val pool = gen.candidatePool(Topic.DIVISION, Difficulty.EASY)
        val qs = gen.generateSession(Topic.DIVISION, Difficulty.EASY, count = pool.size + 5)
        assertEquals(pool.size + 5, qs.size)
        assertEquals(pool.size, qs.map { it.key }.toSet().size)
    }

    @Test fun divisionPoolIsIntegerOnly() {
        val gen = QuestionGenerator(SeededRandomProvider(2))
        for (d in Difficulty.entries) {
            val pool = gen.candidatePool(Topic.DIVISION, d)
            assertTrue(pool.isNotEmpty())
            assertTrue(pool.all { (a, b) -> b > 0 && a % b == 0 && a / b > 0 })
        }
        assertEquals(27, gen.candidatePool(Topic.DIVISION, Difficulty.EASY).size)
    }

    @Test fun correctPositionVaries() {
        val positions = IntArray(4)
        val gen = QuestionGenerator(SeededRandomProvider(42))
        repeat(40) {
            gen.generateSession(Topic.ADDITION, Difficulty.MEDIUM).forEach { q ->
                positions[q.options.indexOf(q.correctAnswer)]++
            }
        }
        assertTrue(positions.joinToString(), positions.all { it > 40 })
    }

    @Test fun noThreeInARowInSameSlot() = forAll { topic, difficulty, seed ->
        val qs = QuestionGenerator(SeededRandomProvider(seed)).generateSession(topic, difficulty)
        val idx = qs.map { it.options.indexOf(it.correctAnswer) }
        for (i in 2 until idx.size) {
            assertTrue("$idx", !(idx[i] == idx[i - 1] && idx[i] == idx[i - 2]))
        }
    }

    @Test fun distractorFallbackIsBounded() {
        val r = kotlin.random.Random(0)
        // Smallest possible answers: 1 − 1 = 0 and 1 ÷ 1 = 1.
        val sub = Distractors.pick(Topic.SUBTRACTION, 1, 1, 0, r)
        assertEquals(3, sub.toSet().size)
        assertTrue(sub.all { it > 0 })
        val div = Distractors.pick(Topic.DIVISION, 1, 1, 1, r)
        assertEquals(3, div.toSet().size)
        assertTrue(div.all { it >= 1 && it != 1 })
    }

    @Test fun seededGenerationIsDeterministic() {
        val a = QuestionGenerator(SeededRandomProvider(7)).generateSession(Topic.MULTIPLICATION, Difficulty.HARD)
        val b = QuestionGenerator(SeededRandomProvider(7)).generateSession(Topic.MULTIPLICATION, Difficulty.HARD)
        assertEquals(a, b)
    }
}
