package com.numipad.junior.domain.questions

import kotlin.random.Random

/** Injectable randomness so tests can be seeded. */
fun interface RandomProvider {
    fun random(): Random
}

class SeededRandomProvider(seed: Long) : RandomProvider {
    private val r = Random(seed)
    override fun random(): Random = r
}

object DefaultRandomProvider : RandomProvider {
    override fun random(): Random = Random.Default
}

/**
 * Generates practice sessions offline. Every topic/difficulty has an explicit,
 * finite pool of valid operand pairs; questions are sampled from the shuffled
 * pool, so generation always terminates.
 */
class QuestionGenerator(private val randomProvider: RandomProvider = DefaultRandomProvider) {

    private val poolCache = HashMap<Pair<Topic, Difficulty>, List<Pair<Int, Int>>>()

    /**
     * Canonical valid pairs (first, second):
     * - addition / multiplication: unordered pairs a ≤ b (order is randomised on display)
     * - subtraction: a ≥ b so the answer is ≥ 0
     * - division: dividend and divisor both in range, divisor > 0, no remainder
     */
    fun candidatePool(topic: Topic, difficulty: Difficulty): List<Pair<Int, Int>> =
        poolCache.getOrPut(topic to difficulty) {
            val r = difficulty.range
            buildList {
                when (topic) {
                    Topic.ADDITION, Topic.MULTIPLICATION ->
                        for (a in r) for (b in a..r.last) add(a to b)
                    Topic.SUBTRACTION ->
                        for (a in r) for (b in r.first..a) add(a to b)
                    Topic.DIVISION ->
                        for (dividend in r) for (divisor in r) {
                            if (divisor > 0 && dividend % divisor == 0) add(dividend to divisor)
                        }
                }
            }
        }

    /**
     * Builds a session of [count] questions with no duplicates (when the pool allows).
     * Questions whose keys are in [avoid] (the previous session) are used only if the
     * pool would otherwise run out. If a pool is smaller than [count], the pool is
     * reused from the start — never an endless retry loop.
     */
    fun generateSession(
        topic: Topic,
        difficulty: Difficulty,
        count: Int = QUESTIONS_PER_SESSION,
        avoid: Set<QuestionKey> = emptySet(),
    ): List<Question> {
        val random = randomProvider.random()
        val pool = candidatePool(topic, difficulty)
        check(pool.isNotEmpty()) { "Empty pool for $topic/$difficulty" }

        val shuffled = pool.shuffled(random)
        val fresh = shuffled.filter { QuestionKey.of(topic, it.first, it.second) !in avoid }
        val recent = shuffled.filter { QuestionKey.of(topic, it.first, it.second) in avoid }
        val ordered = fresh + recent

        val pairs = ArrayList<Pair<Int, Int>>(count)
        var i = 0
        while (pairs.size < count) {
            pairs += ordered[i % ordered.size] // wraps only for pools smaller than count
            i++
        }

        val questions = ArrayList<Question>(count)
        var lastIndex = -1
        var streak = 0
        for ((a0, b0) in pairs) {
            val (a, b) = when (topic) {
                Topic.ADDITION, Topic.MULTIPLICATION -> if (random.nextBoolean()) a0 to b0 else b0 to a0
                else -> a0 to b0
            }
            var q = buildQuestion(topic, a, b, random)
            // Avoid the correct answer sitting in the same slot three times running.
            val idx = q.options.indexOf(q.correctAnswer)
            if (idx == lastIndex && streak >= 1) {
                val others = q.options.filter { it != q.correctAnswer }.shuffled(random)
                val newIdx = (idx + 1 + random.nextInt(3)) % 4
                val opts = others.toMutableList().apply { add(newIdx, q.correctAnswer) }
                q = q.copy(options = opts)
            }
            val finalIdx = q.options.indexOf(q.correctAnswer)
            streak = if (finalIdx == lastIndex) streak + 1 else 0
            lastIndex = finalIdx
            questions += q
        }
        return questions
    }

    fun buildQuestion(topic: Topic, a: Int, b: Int, random: Random = randomProvider.random()): Question {
        val correct = answerFor(topic, a, b)
        val distractors = Distractors.pick(topic, a, b, correct, random)
        val options = (distractors + correct).shuffled(random)
        return Question(topic, a, b, correct, options)
    }
}

/** Operation-specific distractors with a bounded fallback. */
object Distractors {
    private const val NEEDED = 3

    fun minimumAllowed(topic: Topic): Int = when (topic) {
        Topic.ADDITION, Topic.SUBTRACTION -> 0
        Topic.MULTIPLICATION, Topic.DIVISION -> 1
    }

    /** Plausible mistakes for each operation, nearest first. */
    fun plausible(topic: Topic, a: Int, b: Int, correct: Int): List<Int> = when (topic) {
        Topic.ADDITION -> listOf(correct + 1, correct - 1, correct + 2, correct - 2, correct + 10, correct - 10, a * b)
        Topic.SUBTRACTION -> listOf(correct + 1, correct - 1, correct + 2, correct - 2, a + b, correct + 10, correct - 10)
        Topic.MULTIPLICATION -> listOf(correct + a, correct - a, correct + b, correct - b, a + b, correct + 1, correct - 1, correct + 10, correct - 10)
        Topic.DIVISION -> listOf(correct + 1, correct - 1, correct + 2, correct - 2, b, a - b, correct * 2)
    }

    fun pick(topic: Topic, a: Int, b: Int, correct: Int, random: Random): List<Int> {
        val min = minimumAllowed(topic)
        val valid = plausible(topic, a, b, correct)
            .filter { it >= min && it != correct }
            .distinct()
        // Prefer the close ones, but vary which are used.
        val chosen = LinkedHashSet<Int>()
        valid.take(5).shuffled(random).forEach { if (chosen.size < NEEDED) chosen += it }
        valid.drop(5).shuffled(random).forEach { if (chosen.size < NEEDED) chosen += it }

        // Bounded fallback: walk outward from the correct answer. Upward values are
        // always valid, so this terminates within NEEDED + chosen.size steps.
        var d = 1
        while (chosen.size < NEEDED && d <= 1000) {
            listOf(correct + d, correct - d).forEach {
                if (chosen.size < NEEDED && it >= min && it != correct) chosen += it
            }
            d++
        }
        check(chosen.size == NEEDED)
        return chosen.toList()
    }
}
