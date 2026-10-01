package com.numipad.junior.domain.questions

/**
 * Deterministic, template-based explanations. Every sentence states only
 * arithmetic that is true for the given operands (verified in unit tests).
 */
object Explanations {

    fun explain(topic: Topic, a: Int, b: Int): String {
        val answer = answerFor(topic, a, b)
        val head = "$a ${topic.symbol} $b = $answer."
        return "$head ${body(topic, a, b, answer)}"
    }

    private fun body(topic: Topic, a: Int, b: Int, answer: Int): String = when (topic) {
        Topic.ADDITION -> addition(a, b, answer)
        Topic.SUBTRACTION -> subtraction(a, b, answer)
        Topic.MULTIPLICATION -> multiplication(a, b, answer)
        Topic.DIVISION -> division(a, b, answer)
    }

    private fun addition(a: Int, b: Int, sum: Int): String = when {
        // Make-ten strategy: 8 + 5 → add 2 to reach 10, then 3 more.
        a < 10 && b < 10 && sum > 10 -> {
            val toTen = 10 - a
            val rest = b - toTen
            "Add $toTen to reach 10, then add the remaining $rest."
        }
        a >= 10 && b >= 10 -> {
            val tensA = a / 10 * 10
            val tensB = b / 10 * 10
            val onesA = a % 10
            val onesB = b % 10
            if (onesA == 0 && onesB == 0) {
                "Add the tens: $tensA + $tensB = $sum."
            } else {
                "Add the tens: $tensA + $tensB = ${tensA + tensB}. " +
                    "Add the ones: $onesA + $onesB = ${onesA + onesB}. " +
                    "Then ${tensA + tensB} + ${onesA + onesB} = $sum."
            }
        }
        else -> {
            val big = maxOf(a, b)
            val small = minOf(a, b)
            "Start at $big and count on $small more to reach $sum."
        }
    }

    private fun subtraction(a: Int, b: Int, diff: Int): String = when (diff) {
        0 -> "Taking $b away from $a leaves nothing, so the answer is 0."
        else -> "Taking $b away from $a leaves $diff. Check: $diff + $b = $a."
    }

    private fun multiplication(a: Int, b: Int, product: Int): String {
        val groups = if (a == 1) "1 group of $b makes $product." else "$a groups of $b make $product."
        // Split a two-digit second factor into tens and ones: 6 × 14 = 6 × 10 + 6 × 4.
        return if (b in 11..99 && b % 10 != 0) {
            val tens = b / 10 * 10
            val ones = b % 10
            "$groups Split $b into $tens and $ones: $a × $tens = ${a * tens} and " +
                "$a × $ones = ${a * ones}, so ${a * tens} + ${a * ones} = $product."
        } else {
            groups
        }
    }

    private fun division(a: Int, b: Int, quotient: Int): String {
        val split = if (b == 1) {
            "Splitting $a into 1 group gives $quotient in that group."
        } else {
            "Splitting $a into $b equal groups gives $quotient in each group."
        }
        return "$split Check: $b × $quotient = $a."
    }

    /** Small counter visuals are offered only when they stay readable. */
    fun showsCounters(topic: Topic, a: Int, b: Int): Boolean = when (topic) {
        Topic.ADDITION -> a + b <= 20
        Topic.SUBTRACTION -> a <= 20
        Topic.MULTIPLICATION -> a * b <= 30 && a <= 6
        Topic.DIVISION -> a <= 30 && b <= 6
    }

    /** Text equivalent for the counters visual. */
    fun countersDescription(topic: Topic, a: Int, b: Int): String = when (topic) {
        Topic.ADDITION -> "$a dots and $b dots, ${a + b} dots in total."
        Topic.SUBTRACTION -> "$a dots with $b crossed out, ${a - b} dots left."
        Topic.MULTIPLICATION -> "$a rows of $b dots, ${a * b} dots in total."
        Topic.DIVISION -> "$a dots shared into $b groups of ${a / b}."
    }
}
