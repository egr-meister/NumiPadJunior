package com.numipad.junior.domain.calculator

import java.math.BigDecimal
import java.math.RoundingMode

/** The four calculator operations. [symbol] is real text so screen readers can read it. */
enum class CalcOperator(val symbol: String, val spokenName: String) {
    PLUS("+", "plus"),
    MINUS("−", "minus"),
    TIMES("×", "times"),
    DIVIDE("÷", "divided by");

    companion object {
        fun fromName(name: String?): CalcOperator? = entries.firstOrNull { it.name == name }
    }
}

enum class CalcError(val message: String) {
    DIVIDE_BY_ZERO("You can’t divide by zero. Try another number."),
    TOO_LARGE("That number is too large for this calculator."),
}

/** A finished calculation, shown until the next input. */
data class CompletedCalculation(
    val first: String,
    val operator: CalcOperator,
    val second: String,
    val result: String,
    val rounded: Boolean,
)

/** Emitted once per successful evaluation; becomes a history entry. */
data class CalculationRecord(
    val first: String,
    val operator: CalcOperator,
    val second: String,
    val result: String,
    val rounded: Boolean,
)

/**
 * Complete, serialisable calculator state. Operands are kept as the text the
 * child typed (e.g. "0.", "-", "12.5") so editing is lossless.
 */
data class CalculatorState(
    val first: String = "",
    val operator: CalcOperator? = null,
    val second: String = "",
    val completed: CompletedCalculation? = null,
    val error: CalcError? = null,
) {
    /** Operand currently receiving digits. */
    val editingSecond: Boolean get() = completed == null && operator != null

    val expressionText: String
        get() = when {
            completed != null ->
                "${completed.first} ${completed.operator.symbol} ${completed.second} ="
            operator != null -> listOf(first, operator.symbol, second).filter { it.isNotEmpty() }.joinToString(" ")
            else -> first
        }

    val displayValue: String
        get() = when {
            completed != null -> completed.result
            operator != null -> second.ifEmpty { first }
            else -> first.ifEmpty { "0" }
        }

    val showsRounded: Boolean get() = completed?.rounded == true
}

sealed interface CalcInput {
    data class Digit(val digit: Int) : CalcInput {
        init { require(digit in 0..9) }
    }
    data object Decimal : CalcInput
    data class Operator(val operator: CalcOperator) : CalcInput
    data object Equals : CalcInput
    data object Clear : CalcInput
    data object Backspace : CalcInput
    /** "Use result" from history: the value becomes a new first operand. */
    data class UseValue(val value: String) : CalcInput
}

data class CalcOutcome(val state: CalculatorState, val record: CalculationRecord? = null)

sealed interface EvalResult {
    data class Ok(val value: String, val rounded: Boolean) : EvalResult
    data class Failed(val error: CalcError) : EvalResult
}

/**
 * Single-binary-operation calculator. Pure and deterministic; no expression parser,
 * no string evaluation. All arithmetic uses [BigDecimal].
 */
object Calculator {
    val MAX_ABS: BigDecimal = BigDecimal("1000000")
    const val MAX_FRACTION_DIGITS = 6

    fun reduce(state: CalculatorState, input: CalcInput): CalcOutcome {
        // Any new input dismisses a shown error but keeps the expression for correction.
        val s = state.copy(error = null)
        return when (input) {
            is CalcInput.Digit -> CalcOutcome(digit(s, input.digit))
            CalcInput.Decimal -> CalcOutcome(decimal(s))
            is CalcInput.Operator -> operator(s, input.operator)
            CalcInput.Equals -> equals(s)
            CalcInput.Clear -> CalcOutcome(CalculatorState())
            CalcInput.Backspace -> CalcOutcome(backspace(s))
            is CalcInput.UseValue -> CalcOutcome(useValue(input.value))
        }
    }

    // ---- input editing ---------------------------------------------------

    private fun digit(s: CalculatorState, d: Int): CalculatorState {
        if (s.completed != null) return CalculatorState(first = d.toString())
        return if (s.operator == null) {
            appendDigit(s.first, d)?.let { s.copy(first = it) } ?: s.copy(error = CalcError.TOO_LARGE)
        } else {
            appendDigit(s.second, d)?.let { s.copy(second = it) } ?: s.copy(error = CalcError.TOO_LARGE)
        }
    }

    /** Returns the new operand text, the unchanged text if the digit is not allowed, or null if too large. */
    internal fun appendDigit(text: String, d: Int): String? {
        val dot = text.indexOf('.')
        if (dot >= 0 && text.length - dot - 1 >= MAX_FRACTION_DIGITS) return text
        val candidate = when (text) {
            "0" -> d.toString()
            "-0" -> "-$d"
            else -> text + d
        }
        val value = parse(candidate) ?: return text
        return if (value.abs() > MAX_ABS) null else candidate
    }

    private fun decimal(s: CalculatorState): CalculatorState {
        if (s.completed != null) return CalculatorState(first = "0.")
        return if (s.operator == null) s.copy(first = appendDecimal(s.first))
        else s.copy(second = appendDecimal(s.second))
    }

    internal fun appendDecimal(text: String): String = when {
        text.contains('.') -> text
        text.isEmpty() -> "0."
        text == "-" -> "-0."
        else -> "$text."
    }

    private fun operator(s: CalculatorState, op: CalcOperator): CalcOutcome {
        // Continue from a finished result.
        s.completed?.let { done ->
            return CalcOutcome(CalculatorState(first = done.result, operator = op))
        }
        // No operator yet: either start a negative first operand or attach the operator.
        if (s.operator == null) {
            return when {
                s.first.isEmpty() && op == CalcOperator.MINUS -> CalcOutcome(s.copy(first = "-"))
                parse(s.first) == null -> CalcOutcome(s) // nothing usable yet
                else -> CalcOutcome(s.copy(operator = op))
            }
        }
        // Operator present, second operand not started.
        if (s.second.isEmpty()) {
            return when {
                op == CalcOperator.MINUS && s.operator != CalcOperator.PLUS ->
                    CalcOutcome(s.copy(second = "-")) // e.g. 5 × −3
                else -> CalcOutcome(s.copy(operator = op)) // replace operator
            }
        }
        // Second operand is only a sign: replace the operator and drop the sign.
        if (parse(s.second) == null) return CalcOutcome(s.copy(operator = op, second = ""))
        // Both operands present: evaluate first, then chain.
        return when (val r = evaluate(s.first, s.operator, s.second)) {
            is EvalResult.Failed -> CalcOutcome(s.copy(error = r.error))
            is EvalResult.Ok -> CalcOutcome(
                CalculatorState(first = r.value, operator = op),
                CalculationRecord(normalize(s.first), s.operator, normalize(s.second), r.value, r.rounded),
            )
        }
    }

    private fun equals(s: CalculatorState): CalcOutcome {
        if (s.completed != null) return CalcOutcome(s) // repeated "=" never repeats the operation
        val op = s.operator ?: return CalcOutcome(s)
        if (parse(s.first) == null || parse(s.second) == null) return CalcOutcome(s)
        return when (val r = evaluate(s.first, op, s.second)) {
            is EvalResult.Failed -> CalcOutcome(s.copy(error = r.error))
            is EvalResult.Ok -> {
                val first = normalize(s.first)
                val second = normalize(s.second)
                CalcOutcome(
                    CalculatorState(completed = CompletedCalculation(first, op, second, r.value, r.rounded)),
                    CalculationRecord(first, op, second, r.value, r.rounded),
                )
            }
        }
    }

    private fun backspace(s: CalculatorState): CalculatorState {
        s.completed?.let { return CalculatorState(first = it.result) }
        return when {
            s.operator != null && s.second.isNotEmpty() -> s.copy(second = s.second.dropLast(1))
            s.operator != null -> s.copy(operator = null)
            else -> s.copy(first = s.first.dropLast(1))
        }
    }

    private fun useValue(value: String): CalculatorState {
        val v = parse(value)
        return if (v == null || v.abs() > MAX_ABS) CalculatorState() else CalculatorState(first = canonical(v))
    }

    // ---- arithmetic --------------------------------------------------------

    fun evaluate(firstText: String, op: CalcOperator, secondText: String): EvalResult {
        val a = parse(firstText) ?: return EvalResult.Failed(CalcError.TOO_LARGE)
        val b = parse(secondText) ?: return EvalResult.Failed(CalcError.TOO_LARGE)
        if (a.abs() > MAX_ABS || b.abs() > MAX_ABS) return EvalResult.Failed(CalcError.TOO_LARGE)

        val exact: BigDecimal? = when (op) {
            CalcOperator.PLUS -> a.add(b)
            CalcOperator.MINUS -> a.subtract(b)
            CalcOperator.TIMES -> a.multiply(b)
            CalcOperator.DIVIDE -> {
                if (b.signum() == 0) return EvalResult.Failed(CalcError.DIVIDE_BY_ZERO)
                try {
                    a.divide(b) // exact when the decimal expansion terminates
                } catch (_: ArithmeticException) {
                    null // non-terminating
                }
            }
        }
        val (value, rounded) = if (exact != null && exact.stripTrailingZeros().scale() <= MAX_FRACTION_DIGITS) {
            exact to false
        } else if (exact != null) {
            exact.setScale(MAX_FRACTION_DIGITS, RoundingMode.HALF_UP) to true
        } else {
            a.divide(b, MAX_FRACTION_DIGITS, RoundingMode.HALF_UP) to true
        }
        if (value.abs() > MAX_ABS) return EvalResult.Failed(CalcError.TOO_LARGE)
        return EvalResult.Ok(canonical(value), rounded)
    }

    /** Parses operand text; returns null for empty or sign-only input. */
    fun parse(text: String): BigDecimal? {
        if (text.isEmpty() || text == "-" ) return null
        return try {
            BigDecimal(if (text.endsWith('.')) text.dropLast(1) else text)
        } catch (_: NumberFormatException) {
            null
        }
    }

    /** Canonical string: no trailing zeros, plain notation, no "-0". */
    fun canonical(v: BigDecimal): String {
        if (v.signum() == 0) return "0"
        val stripped = v.stripTrailingZeros()
        return (if (stripped.scale() < 0) stripped.setScale(0) else stripped).toPlainString()
    }

    fun normalize(text: String): String = parse(text)?.let(::canonical) ?: text
}
