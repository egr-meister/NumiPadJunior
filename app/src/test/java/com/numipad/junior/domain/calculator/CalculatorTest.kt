package com.numipad.junior.domain.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorTest {

    private val records = mutableListOf<CalculationRecord>()

    private fun press(state: CalculatorState, keys: String): CalculatorState {
        var s = state
        for (k in keys) {
            val input = when (k) {
                in '0'..'9' -> CalcInput.Digit(k - '0')
                '.' -> CalcInput.Decimal
                '+' -> CalcInput.Operator(CalcOperator.PLUS)
                '-' -> CalcInput.Operator(CalcOperator.MINUS)
                '*' -> CalcInput.Operator(CalcOperator.TIMES)
                '/' -> CalcInput.Operator(CalcOperator.DIVIDE)
                '=' -> CalcInput.Equals
                'C' -> CalcInput.Clear
                '<' -> CalcInput.Backspace
                ' ' -> continue
                else -> error("unknown key $k")
            }
            val out = Calculator.reduce(s, input)
            out.record?.let(records::add)
            s = out.state
        }
        return s
    }

    private fun run(keys: String) = press(CalculatorState(), keys)

    @Test fun simpleAddition() {
        val s = run("12+8=")
        assertEquals("20", s.displayValue)
        assertEquals("12 + 8 =", s.expressionText)
        assertEquals(1, records.size)
    }

    @Test fun decimalArithmeticIsExact() {
        assertEquals("0.3", run("0.1+0.2=").displayValue)
        assertEquals("1.5", run("0.5*3=").displayValue)
        assertEquals("2", run("1.50+0.50=").displayValue) // trailing zeros removed
    }

    @Test fun divisionRoundsHalfUpToSixPlaces() {
        val s = run("2/3=")
        assertEquals("0.666667", s.displayValue)
        assertTrue(s.showsRounded)
        assertTrue(records.single().rounded)
        val t = run("1/8=")
        assertEquals("0.125", t.displayValue)
        assertFalse(t.showsRounded)
    }

    @Test fun multiplicationBeyondSixDecimalsIsRounded() {
        val s = run("0.000005*0.5=") // 0.0000025 -> 0.000003
        assertEquals("0.000003", s.displayValue)
        assertTrue(s.showsRounded)
    }

    @Test fun divisionByZeroShowsErrorWithoutHistory() {
        val s = run("5/0=")
        assertEquals(CalcError.DIVIDE_BY_ZERO, s.error)
        assertEquals("You can’t divide by zero. Try another number.", s.error!!.message)
        assertTrue(records.isEmpty())
        // expression kept for correction
        assertEquals("5", s.first)
        assertEquals(CalcOperator.DIVIDE, s.operator)
        assertEquals("0", s.second)
        val fixed = press(s, "<2=")
        assertEquals("2.5", fixed.displayValue)
        assertNull(fixed.error)
    }

    @Test fun resultOutsideRangeIsRejected() {
        val s = run("1000000+1=")
        assertEquals(CalcError.TOO_LARGE, s.error)
        assertEquals("That number is too large for this calculator.", s.error!!.message)
        assertTrue(records.isEmpty())
        assertEquals("1000000", run("999999+1=").displayValue)
        assertEquals(CalcError.TOO_LARGE, run("1000*1001=").error)
    }

    @Test fun operandEntryIsLimited() {
        val s = run("10000000")
        assertEquals("1000000", s.first)
        assertEquals(CalcError.TOO_LARGE, s.error)
        val f = run("1.12345678")
        assertEquals("1.123456", f.first) // max six fractional digits
    }

    @Test fun negativeResultsAndNegativeOperands() {
        assertEquals("-5", run("3-8=").displayValue)
        assertEquals("-15", run("-3*5=").displayValue)
        assertEquals("-15", run("5*-3=").displayValue)
        assertEquals("8", run("5--3=").displayValue)
        assertEquals("0", run("-0+0=").displayValue)
    }

    @Test fun inputRules() {
        assertEquals("1.5", run("1..5").first)
        assertEquals("7", run("007").first)
        assertEquals("0.", run(".").first)
        assertEquals("0.25", run("0.25").first)
    }

    @Test fun operatorReplacementBeforeSecondOperand() {
        val s = run("6+*")
        assertEquals(CalcOperator.TIMES, s.operator)
        assertEquals("42", press(s, "7=").displayValue)
        // plus then minus replaces (not a negative sign)
        assertEquals(CalcOperator.MINUS, run("6+-").operator)
        assertEquals("", run("6+-").second)
    }

    @Test fun clearAndBackspace() {
        assertEquals(CalculatorState(), run("12+3C"))
        assertEquals("12", run("123<").first)
        val s = run("12+<")
        assertNull(s.operator)
        assertEquals("1", run("12+<<").first)
        assertEquals("1", run("<1").first) // backspace on empty is harmless
    }

    @Test fun backspaceAfterResultMakesItEditable() {
        val s = run("12+8=<")
        assertNull(s.completed)
        assertEquals("20", s.first)
        assertEquals("2", press(s, "<").first)
    }

    @Test fun repeatedEqualsDoesNotRepeatOrDuplicate() {
        val s = run("2+3===")
        assertEquals("5", s.displayValue)
        assertEquals(1, records.size)
    }

    @Test fun equalsNeedsTwoOperands() {
        val s = run("5+=")
        assertNull(s.completed)
        assertTrue(records.isEmpty())
        assertNull(run("=").completed)
    }

    @Test fun operatorAfterResultReusesIt() {
        val s = run("12+8=*2=")
        assertEquals("40", s.displayValue)
        assertEquals("20 × 2 =", s.expressionText)
    }

    @Test fun digitAfterResultStartsNew() {
        val s = run("12+8=4")
        assertNull(s.completed)
        assertEquals("4", s.first)
        assertNull(s.operator)
    }

    @Test fun chainedOperatorEvaluatesFirst() {
        val s = run("2+3*4=")
        assertEquals("20", s.displayValue)
        assertEquals(2, records.size)
        assertEquals("5", records[0].result)
    }

    @Test fun chainedErrorKeepsExpression() {
        val s = run("4/0+")
        assertEquals(CalcError.DIVIDE_BY_ZERO, s.error)
        assertEquals(CalcOperator.DIVIDE, s.operator)
        assertTrue(records.isEmpty())
    }

    @Test fun useValueStartsNewFirstOperand() {
        val s = Calculator.reduce(run("1+1="), CalcInput.UseValue("0.666667")).state
        assertEquals("0.666667", s.first)
        assertNull(s.completed)
        assertNotNull(Calculator.reduce(s, CalcInput.Operator(CalcOperator.PLUS)).state.operator)
    }

    @Test fun canonicalForm() {
        assertEquals("100", Calculator.canonical(java.math.BigDecimal("1E+2")))
        assertEquals("0", Calculator.canonical(java.math.BigDecimal("-0.000")))
        assertEquals("1.5", Calculator.canonical(java.math.BigDecimal("1.500000")))
    }
}
