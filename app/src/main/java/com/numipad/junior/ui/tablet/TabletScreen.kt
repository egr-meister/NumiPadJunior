package com.numipad.junior.ui.tablet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.selection.selectable
import androidx.compose.foundation.layout.selection.selectableGroup
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.numipad.junior.data.repository.SessionInfo
import com.numipad.junior.domain.calculator.CalcInput
import com.numipad.junior.domain.calculator.CalcOperator
import com.numipad.junior.domain.calculator.CalculatorState
import com.numipad.junior.domain.progress.DailyStats
import com.numipad.junior.domain.questions.Difficulty
import com.numipad.junior.domain.questions.QUESTIONS_PER_SESSION
import com.numipad.junior.domain.questions.Topic
import com.numipad.junior.ui.components.ConfirmDialog
import com.numipad.junior.ui.components.SectionTitle
import com.numipad.junior.ui.components.StatPill
import com.numipad.junior.ui.components.TabletPanel
import com.numipad.junior.ui.theme.NumiColors

@Composable
fun TabletScreen(
    vm: TabletViewModel,
    onOpenSession: (Long) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val calc by vm.calculator.collectAsStateWithLifecycle()
    val setup by vm.setup.collectAsStateWithLifecycle()
    val active by vm.activeSession.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()

    LaunchedEffect(vm) { vm.openSession.collect { onOpenSession(it) } }

    Column(
        Modifier
            .fillMaxSize()
            .background(NumiColors.Cream)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "NumiPad Junior",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            TextButton(onClick = onOpenSettings, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Filled.Settings, contentDescription = null)
                Spacer(Modifier.padding(start = 6.dp))
                Text("Settings")
            }
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 720.dp
            val calculatorPanel = @Composable { m: Modifier ->
                CalculatorPanel(calc, vm::onInput, onOpenHistory, wide, m)
            }
            val practicePanel = @Composable { m: Modifier ->
                PracticePanel(
                    setup = setup,
                    active = active,
                    today = today,
                    onTopic = vm::selectTopic,
                    onDifficulty = vm::selectDifficulty,
                    onStart = vm::startPractice,
                    onResume = { active?.let { onOpenSession(it.id) } },
                    onEnd = vm::endActiveSession,
                    onProgress = onOpenProgress,
                    modifier = m,
                )
            }
            if (wide) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(
                        Modifier
                            .weight(1.1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .semantics { isTraversalGroup = true; traversalIndex = 0f },
                    ) { calculatorPanel(Modifier.fillMaxWidth()) }
                    Column(
                        Modifier
                            .weight(0.9f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .semantics { isTraversalGroup = true; traversalIndex = 1f },
                    ) { practicePanel(Modifier.fillMaxWidth()) }
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    calculatorPanel(Modifier.fillMaxWidth())
                    practicePanel(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Calculator
// ---------------------------------------------------------------------------

@Composable
private fun CalculatorPanel(
    state: CalculatorState,
    onInput: (CalcInput) -> Unit,
    onHistory: () -> Unit,
    wide: Boolean,
    modifier: Modifier = Modifier,
) {
    TabletPanel(NumiColors.Teal, modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Calculator", Modifier.weight(1f))
            OutlinedButton(
                onClick = onHistory,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = NumiColors.Cream, contentColor = NumiColors.Navy),
            ) { Text("History") }
        }
        Spacer(Modifier.height(10.dp))
        CalcDisplay(state)
        Spacer(Modifier.height(12.dp))
        Keypad(onInput, keyHeight = if (wide) 60.dp else 64.dp)
    }
}

@Composable
private fun CalcDisplay(state: CalculatorState) {
    val value = state.displayValue
    val spokenValue = value.replace("-", "minus ")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NumiColors.Key)
            .border(2.dp, NumiColors.TealDark.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.End,
    ) {
        val expression = state.expressionText
        Text(
            expression.ifEmpty { " " },
            style = MaterialTheme.typography.bodyLarge,
            color = NumiColors.NavySoft,
            textAlign = TextAlign.End,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = if (expression.isEmpty()) "No expression" else "Expression: " + spokenExpression(state)
                },
        )
        val prefix = if (state.showsRounded) "≈ " else ""
        Text(
            prefix + value,
            style = if (value.length > 9) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displayMedium,
            textAlign = TextAlign.End,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = (if (state.completed != null) "Result " else "Value ") +
                        (if (state.showsRounded) "approximately " else "") + spokenValue
                },
        )
        if (state.showsRounded) {
            Text(
                "Rounded to 6 decimal places",
                style = MaterialTheme.typography.labelMedium,
                color = NumiColors.NavySoft,
            )
        }
        state.error?.let { err ->
            Text(
                "⚠ " + err.message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = NumiColors.ErrorText,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }
    }
}

private fun spokenExpression(s: CalculatorState): String {
    fun sp(t: String) = t.replace("-", "minus ")
    s.completed?.let { return "${sp(it.first)} ${it.operator.spokenName} ${sp(it.second)} equals" }
    return listOfNotNull(sp(s.first).ifEmpty { null }, s.operator?.spokenName, sp(s.second).ifEmpty { null })
        .joinToString(" ")
}

private enum class KeyKind { DIGIT, OPERATOR, EQUALS, UTILITY }

@Composable
private fun Keypad(onInput: (CalcInput) -> Unit, keyHeight: androidx.compose.ui.unit.Dp) {
    val gap = 8.dp
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            Key("C", "Clear", KeyKind.UTILITY, keyHeight, 2f) { onInput(CalcInput.Clear) }
            Key("⌫", "Backspace", KeyKind.UTILITY, keyHeight) { onInput(CalcInput.Backspace) }
            OpKey(CalcOperator.DIVIDE, keyHeight, onInput)
        }
        listOf(listOf(7, 8, 9) to CalcOperator.TIMES, listOf(4, 5, 6) to CalcOperator.MINUS, listOf(1, 2, 3) to CalcOperator.PLUS)
            .forEach { (digits, op) ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    digits.forEach { d -> Key("$d", "$d", KeyKind.DIGIT, keyHeight) { onInput(CalcInput.Digit(d)) } }
                    OpKey(op, keyHeight, onInput)
                }
            }
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            Key("0", "0", KeyKind.DIGIT, keyHeight, 2f) { onInput(CalcInput.Digit(0)) }
            Key(".", "Decimal point", KeyKind.DIGIT, keyHeight) { onInput(CalcInput.Decimal) }
            Key("=", "Equals", KeyKind.EQUALS, keyHeight) { onInput(CalcInput.Equals) }
        }
    }
}

@Composable
private fun RowScope.OpKey(op: CalcOperator, height: androidx.compose.ui.unit.Dp, onInput: (CalcInput) -> Unit) {
    val spoken = op.spokenName.replaceFirstChar { it.uppercase() }
    Key(op.symbol, spoken, KeyKind.OPERATOR, height) { onInput(CalcInput.Operator(op)) }
}

@Composable
private fun RowScope.Key(
    label: String,
    spoken: String,
    kind: KeyKind,
    height: androidx.compose.ui.unit.Dp,
    weight: Float = 1f,
    onClick: () -> Unit,
) {
    val (bg, fg) = when (kind) {
        KeyKind.DIGIT -> NumiColors.Key to NumiColors.Navy
        KeyKind.OPERATOR -> NumiColors.Yellow to NumiColors.Navy
        KeyKind.EQUALS -> NumiColors.Navy to NumiColors.Cream
        KeyKind.UTILITY -> NumiColors.TealLight to NumiColors.Navy
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = bg,
        contentColor = fg,
        shadowElevation = 1.dp,
        modifier = Modifier
            .weight(weight)
            .heightIn(min = height)
            .semantics { contentDescription = spoken },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.heightIn(min = height)) {
            Text(
                label,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Practice panel
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PracticePanel(
    setup: PracticeSetup,
    active: SessionInfo?,
    today: DailyStats,
    onTopic: (Topic) -> Unit,
    onDifficulty: (Difficulty) -> Unit,
    onStart: () -> Unit,
    onResume: () -> Unit,
    onEnd: () -> Unit,
    onProgress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmEnd by rememberSaveable { mutableStateOf(false) }

    TabletPanel(NumiColors.Lavender, modifier) {
        SectionTitle("Today Practice")
        Spacer(Modifier.height(10.dp))

        if (active != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(NumiColors.YellowSoft)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Session in progress: ${active.topic.label} · ${active.difficulty.label}",
                    style = MaterialTheme.typography.titleMedium,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onResume, modifier = Modifier.heightIn(min = 48.dp)) { Text("Resume practice") }
                    OutlinedButton(onClick = { confirmEnd = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("End session") }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Text("Topic", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        FlowRow(
            Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Topic.entries.forEach { t ->
                ChoiceTile(
                    selected = t == setup.topic,
                    title = t.symbol,
                    subtitle = t.label,
                    spoken = t.label,
                    onClick = { onTopic(t) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Difficulty", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        FlowRow(
            Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Difficulty.entries.forEach { d ->
                ChoiceTile(
                    selected = d == setup.difficulty,
                    title = d.label,
                    subtitle = "${d.range.first}–${d.range.last}",
                    spoken = "${d.label}, numbers ${d.range.first} to ${d.range.last}",
                    onClick = { onDifficulty(d) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(setup.difficulty.description(setup.topic), style = MaterialTheme.typography.bodyMedium, color = NumiColors.NavySoft)
        Spacer(Modifier.height(12.dp))
        Text(
            "${setup.topic.label} · ${setup.difficulty.label} · $QUESTIONS_PER_SESSION questions",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onStart,
            enabled = active == null,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NumiColors.Navy, contentColor = NumiColors.Cream),
        ) { Text("Start practice", style = MaterialTheme.typography.titleMedium, color = if (active == null) NumiColors.Cream else NumiColors.NavySoft) }
        if (active != null) {
            Text(
                "Resume or end the current session to start a new one.",
                style = MaterialTheme.typography.bodyMedium,
                color = NumiColors.NavySoft,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Spacer(Modifier.height(14.dp))
        Text("Today", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        if (today.answered == 0) {
            Text("No answers yet today. Practice is always optional.", style = MaterialTheme.typography.bodyMedium)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill("Solved", today.answered.toString())
                StatPill("Correct", today.correct.toString())
                StatPill("Accuracy", today.accuracyPercent?.let { "$it%" } ?: "No answers yet")
            }
        }
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onProgress, modifier = Modifier.heightIn(min = 48.dp)) { Text("View progress & badges") }
    }

    if (confirmEnd && active != null) {
        ConfirmDialog(
            title = "End this session?",
            text = "Answers you already gave still count in today’s progress. The session will be marked unfinished.",
            confirmLabel = "End session",
            onConfirm = { confirmEnd = false; onEnd() },
            onDismiss = { confirmEnd = false },
        )
    }
}

@Composable
private fun ChoiceTile(
    selected: Boolean,
    title: String,
    subtitle: String,
    spoken: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .widthIn(min = 76.dp)
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(if (selected) NumiColors.Navy else NumiColors.Cream)
            .border(2.dp, if (selected) NumiColors.Yellow else NumiColors.LavenderDeep, shape)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val fg = if (selected) NumiColors.Cream else NumiColors.Navy
        Text(
            (if (selected) "✓ " else "") + title,
            style = MaterialTheme.typography.titleLarge,
            color = fg,
            modifier = Modifier.clearAndSetSemantics { },
        )
        Text(subtitle, style = MaterialTheme.typography.labelMedium, color = fg, modifier = Modifier.clearAndSetSemantics { })
    }
}
