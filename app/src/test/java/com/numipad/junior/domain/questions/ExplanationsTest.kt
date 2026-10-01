package com.numipad.junior.domain.questions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplanationsTest {

    @Test fun specExamples() {
        assertEquals(
            "8 + 5 = 13. Add 2 to reach 10, then add the remaining 3.",
            Explanations.explain(Topic.ADDITION, 8, 5),
        )
        assertEquals(
            "12 − 4 = 8. Taking 4 away from 12 leaves 8. Check: 8 + 4 = 12.",
            Explanations.explain(Topic.SUBTRACTION, 12, 4),
        )
        assertEquals("6 × 4 = 24. 6 groups of 4 make 24.", Explanations.explain(Topic.MULTIPLICATION, 6, 4))
        assertEquals(
            "24 ÷ 6 = 4. Splitting 24 into 6 equal groups gives 4 in each group. Check: 6 × 4 = 24.",
            Explanations.explain(Topic.DIVISION, 24, 6),
        )
    }

    @Test fun zeroAndEqualOperands() {
        assertEquals(
            "7 − 7 = 0. Taking 7 away from 7 leaves nothing, so the answer is 0.",
            Explanations.explain(Topic.SUBTRACTION, 7, 7),
        )
        assertEquals("5 × 5 = 25. 5 groups of 5 make 25.", Explanations.explain(Topic.MULTIPLICATION, 5, 5))
        assertTrue(Explanations.explain(Topic.DIVISION, 9, 9).startsWith("9 ÷ 9 = 1."))
        assertEquals("1 × 7 = 7. 1 group of 7 makes 7.", Explanations.explain(Topic.MULTIPLICATION, 1, 7))
    }

    /**
     * Every "x op y = z" fragment inside every explanation, for every pair in every
     * pool, must be arithmetically true.
     */
    @Test fun everyStatedEquationIsTrue() {
        val eq = Regex("""(\d+) ([+−×÷]) (\d+) = (\d+)""")
        val gen = QuestionGenerator(SeededRandomProvider(0))
        var checked = 0
        for (topic in Topic.entries) for (d in Difficulty.entries) {
            for ((a0, b0) in gen.candidatePool(topic, d)) {
                val pairs = if (topic == Topic.ADDITION || topic == Topic.MULTIPLICATION) listOf(a0 to b0, b0 to a0) else listOf(a0 to b0)
                for ((a, b) in pairs) {
                    val text = Explanations.explain(topic, a, b)
                    assertTrue(text, text.startsWith("$a ${topic.symbol} $b = ${answerFor(topic, a, b)}."))
                    for (m in eq.findAll(text)) {
                        val (x, op, y, z) = m.destructured
                        val expected = when (op) {
                            "+" -> x.toInt() + y.toInt()
                            "−" -> x.toInt() - y.toInt()
                            "×" -> x.toInt() * y.toInt()
                            else -> x.toInt() / y.toInt().also { assertEquals(text, 0, x.toInt() % it) }
                        }
                        assertEquals(text, expected, z.toInt())
                        checked++
                    }
                    assertTrue(text, !text.contains("NaN") && !text.contains("-"))
                }
            }
        }
        assertTrue(checked > 10_000)
    }

    @Test fun makeTenNumbersAreConsistent() {
        for (a in 1..9) for (b in 1..9) if (a + b > 10) {
            val text = Explanations.explain(Topic.ADDITION, a, b)
            val toTen = 10 - a
            assertTrue(text, text.contains("Add $toTen to reach 10, then add the remaining ${a + b - 10}."))
        }
    }

    @Test fun countersOnlyForSmallValues() {
        assertTrue(Explanations.showsCounters(Topic.MULTIPLICATION, 3, 4))
        assertTrue(!Explanations.showsCounters(Topic.MULTIPLICATION, 100, 100))
        assertEquals("3 rows of 4 dots, 12 dots in total.", Explanations.countersDescription(Topic.MULTIPLICATION, 3, 4))
    }
}
