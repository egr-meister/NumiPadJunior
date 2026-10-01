package com.numipad.junior.domain.questions

import java.util.Locale

/** Practice topics. [symbol] is display text, [spokenSymbol] is for screen readers. */
enum class Topic(val label: String, val symbol: String, val spokenSymbol: String) {
    ADDITION("Addition", "+", "plus"),
    SUBTRACTION("Subtraction", "−", "minus"),
    MULTIPLICATION("Multiplication", "×", "times"),
    DIVISION("Division", "÷", "divided by");

    companion object {
        fun fromName(name: String?): Topic? = entries.firstOrNull { it.name == name }
    }
}

enum class Difficulty(val label: String, val range: IntRange) {
    EASY("Easy", 1..10),
    MEDIUM("Medium", 1..50),
    HARD("Hard", 1..100);

    val max: Int get() = range.last

    fun description(topic: Topic): String = when (topic) {
        Topic.ADDITION -> "Numbers ${range.first}–${range.last}. Answers up to ${max * 2}."
        Topic.SUBTRACTION -> "Numbers ${range.first}–${range.last}. Answers are never negative."
        Topic.MULTIPLICATION -> "Numbers ${range.first}–${range.last}. Answers up to ${String.format(Locale.US, "%,d", max * max)}."
        Topic.DIVISION -> "Numbers ${range.first}–${range.last}. Whole-number answers, no remainders."
    }

    companion object {
        fun fromName(name: String?): Difficulty? = entries.firstOrNull { it.name == name }
    }
}

/** A generated practice question. [options] holds exactly four distinct values. */
data class Question(
    val topic: Topic,
    val first: Int,
    val second: Int,
    val correctAnswer: Int,
    val options: List<Int>,
) {
    val expression: String get() = "$first ${topic.symbol} $second"
    val spokenExpression: String get() = "$first ${topic.spokenSymbol} $second"

    /** Session-duplicate key: reversed operands count as the same question for + and ×. */
    val key: QuestionKey get() = QuestionKey.of(topic, first, second)
}

data class QuestionKey(val topic: Topic, val a: Int, val b: Int) {
    companion object {
        fun of(topic: Topic, first: Int, second: Int): QuestionKey = when (topic) {
            Topic.ADDITION, Topic.MULTIPLICATION -> QuestionKey(topic, minOf(first, second), maxOf(first, second))
            else -> QuestionKey(topic, first, second)
        }
    }
}

fun answerFor(topic: Topic, a: Int, b: Int): Int = when (topic) {
    Topic.ADDITION -> a + b
    Topic.SUBTRACTION -> a - b
    Topic.MULTIPLICATION -> a * b
    Topic.DIVISION -> a / b
}

const val QUESTIONS_PER_SESSION = 10
