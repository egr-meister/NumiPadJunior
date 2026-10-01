package com.numipad.junior.ui.practice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.numipad.junior.data.repository.QuestionItem
import com.numipad.junior.data.repository.SessionDetail
import com.numipad.junior.domain.progress.accuracyPercent
import com.numipad.junior.domain.questions.Explanations
import com.numipad.junior.ui.components.ConfirmDialog
import com.numipad.junior.ui.components.CountersVisual
import com.numipad.junior.ui.theme.LocalReduceMotion
import com.numipad.junior.ui.theme.NumiColors

// ---------------------------------------------------------------------------
// Question screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(
    vm: PracticeViewModel,
    onBack: () -> Unit,
    onResults: (Long) -> Unit,
    onEnded: () -> Unit,
) {
    val detail by vm.detail.collectAsStateWithLifecycle()
    val viewed by vm.viewedIndex.collectAsStateWithLifecycle()
    var confirmEnd by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is PracticeEvent.ShowResults -> onResults(e.sessionId)
                PracticeEvent.Ended -> onEnded()
                is PracticeEvent.OpenSession -> Unit
            }
        }
    }

    Scaffold(
        containerColor = NumiColors.Cream,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        detail?.let { "${it.session.topic.label} · ${it.session.difficulty.label}" } ?: "Practice",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to calculator, session is saved")
                    }
                },
                actions = {
                    TextButton(onClick = { confirmEnd = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("End session") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NumiColors.Cream),
            )
        },
    ) { padding ->
        val d = detail
        val index = viewed
        if (d == null || index == null || d.questions.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val q = d.questions[index.coerceIn(0, d.questions.lastIndex)]
        QuestionContent(
            detail = d,
            question = q,
            onAnswer = { vm.answer(q.id, it) },
            onNext = { vm.next(q.position) },
            isLast = q.position == d.questions.lastIndex,
            contentPadding = padding,
        )
    }

    if (confirmEnd) {
        ConfirmDialog(
            title = "End this session?",
            text = "Answers you already gave still count in today’s progress. Unanswered questions are not counted as mistakes. The session will be marked unfinished.",
            confirmLabel = "End session",
            onConfirm = { confirmEnd = false; vm.endSession() },
            onDismiss = { confirmEnd = false },
        )
    }
}

@Composable
private fun QuestionContent(
    detail: SessionDetail,
    question: QuestionItem,
    onAnswer: (Int) -> Unit,
    onNext: () -> Unit,
    isLast: Boolean,
    contentPadding: PaddingValues,
) {
    val total = detail.questions.size
    val number = question.position + 1
    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Question $number of $total",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                Text(
                    "✓ ${detail.correctCount}   ✗ ${detail.incorrectCount}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics {
                        contentDescription = "${detail.correctCount} correct, ${detail.incorrectCount} incorrect"
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            val progress = detail.answeredCount.toFloat() / total
            LinearProgressIndicator(
                progress = { progress },
                color = NumiColors.TealDark,
                trackColor = NumiColors.Lavender,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .semantics {
                        progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                        stateDescription = "${detail.answeredCount} of $total answered"
                    },
            )
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(NumiColors.Lavender)
                    .padding(vertical = 28.dp, horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${question.expression} = ?",
                    style = MaterialTheme.typography.displayLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { contentDescription = "What is ${question.spokenExpression}?" },
                )
            }
            Spacer(Modifier.height(20.dp))

            // Four answers in a 2 × 2 grid.
            val opts = question.options
            for (row in 0..1) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (col in 0..1) {
                        val value = opts[row * 2 + col]
                        AnswerButton(
                            value = value,
                            question = question,
                            onClick = { onAnswer(value) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (question.answered) {
                Feedback(question)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onNext,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NumiColors.Navy, contentColor = NumiColors.Cream),
                ) { Text(if (isLast) "See results" else "Next question", style = MaterialTheme.typography.titleMedium) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AnswerButton(value: Int, question: QuestionItem, onClick: () -> Unit, modifier: Modifier) {
    val answered = question.answered
    val isSelected = question.selectedAnswer == value
    val isCorrect = question.correctAnswer == value
    val (bg, borderColor, mark, state) = when {
        !answered -> Quad(NumiColors.Key, NumiColors.LavenderDeep, "", "")
        isCorrect && isSelected -> Quad(NumiColors.CorrectBg, NumiColors.Correct, "✓ ", "your answer, correct")
        isCorrect -> Quad(NumiColors.CorrectBg, NumiColors.Correct, "✓ ", "correct answer")
        isSelected -> Quad(NumiColors.IncorrectBg, NumiColors.Incorrect, "✗ ", "your answer, not correct")
        else -> Quad(NumiColors.Key.copy(alpha = 0.6f), NumiColors.LavenderDeep, "", "")
    }
    Surface(
        onClick = onClick,
        enabled = !answered,
        shape = RoundedCornerShape(20.dp),
        color = bg,
        contentColor = NumiColors.Navy,
        modifier = modifier
            .heightIn(min = 72.dp)
            .border(if (isSelected || (answered && isCorrect)) 3.dp else 2.dp, borderColor, RoundedCornerShape(20.dp))
            .semantics {
                contentDescription = "Answer $value"
                if (state.isNotEmpty()) stateDescription = state
            },
    ) {
        Box(Modifier.heightIn(min = 72.dp), contentAlignment = Alignment.Center) {
            Text(
                mark + value,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

private data class Quad(val a: androidx.compose.ui.graphics.Color, val b: androidx.compose.ui.graphics.Color, val c: String, val d: String)

@Composable
private fun Feedback(q: QuestionItem) {
    val reduce = LocalReduceMotion.current
    val correct = q.isCorrect == true
    val content = @Composable {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(if (correct) NumiColors.CorrectBg else NumiColors.YellowSoft)
                .padding(16.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (correct) "✓ That’s right!" else "➜ Let’s look at it together.",
                style = MaterialTheme.typography.headlineSmall,
            )
            if (!correct) {
                Text("The answer is ${q.correctAnswer}.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(q.explanation, style = MaterialTheme.typography.bodyLarge)
            if (Explanations.showsCounters(q.topic, q.first, q.second)) {
                CountersVisual(q.topic, q.first, q.second, Explanations.countersDescription(q.topic, q.first, q.second))
            }
        }
    }
    if (reduce) {
        content()
    } else {
        key(q.id) {
            val visible = remember { MutableTransitionState(false).apply { targetState = true } }
            AnimatedVisibility(visibleState = visible, enter = fadeIn()) { content() }
        }
    }
}

// ---------------------------------------------------------------------------
// Results
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    vm: PracticeViewModel,
    onReview: () -> Unit,
    onPracticeAgain: (Long) -> Unit,
    onBackToCalculator: () -> Unit,
) {
    val detail by vm.detail.collectAsStateWithLifecycle()
    LaunchedEffect(vm) {
        vm.events.collect { e -> if (e is PracticeEvent.OpenSession) onPracticeAgain(e.sessionId) }
    }
    Scaffold(
        containerColor = NumiColors.Cream,
        topBar = {
            TopAppBar(
                title = { Text("Session results") },
                navigationIcon = {
                    IconButton(onClick = onBackToCalculator) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to calculator")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NumiColors.Cream),
            )
        },
    ) { padding ->
        val d = detail ?: run {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${d.session.topic.label} · ${d.session.difficulty.label}",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                if (d.session.isUnfinished) Text("Unfinished session", style = MaterialTheme.typography.titleMedium)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(NumiColors.Lavender)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${d.correctCount} out of ${d.questions.size}",
                            style = MaterialTheme.typography.displayMedium,
                        )
                        Text("correct answers", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        val acc = accuracyPercent(d.correctCount, d.answeredCount)
                        Text(
                            "Accuracy: " + (acc?.let { "$it%" } ?: "No answers yet"),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
                Text(
                    "Thanks for practicing! Every question you try helps you get more comfortable with numbers.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(onClick = onReview, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Review answers") }
                OutlinedButton(onClick = { vm.practiceAgain() }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text("Practice again")
                }
                OutlinedButton(onClick = onBackToCalculator, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text("Back to calculator")
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Review
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(vm: PracticeViewModel, onBack: () -> Unit) {
    val detail by vm.detail.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = NumiColors.Cream,
        topBar = {
            TopAppBar(
                title = { Text("Review answers") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NumiColors.Cream),
            )
        },
    ) { padding ->
        val d = detail ?: return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(d.questions, key = { _, q -> q.id }) { i, q ->
                ReviewItem(i + 1, q, Modifier.widthIn(max = 640.dp).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ReviewItem(number: Int, q: QuestionItem, modifier: Modifier) {
    val status = when (q.isCorrect) {
        true -> "✓ Correct"
        false -> "✗ Not quite"
        null -> "Not answered"
    }
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                when (q.isCorrect) {
                    true -> NumiColors.CorrectBg
                    false -> NumiColors.IncorrectBg
                    null -> NumiColors.CreamDeep
                },
            )
            .padding(14.dp)
            .semantics(mergeDescendants = true) { },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row {
            Text("$number. ${q.expression}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text(status, style = MaterialTheme.typography.titleMedium)
        }
        Text(
            "Your answer: ${q.selectedAnswer?.toString() ?: "—"}   ·   Correct answer: ${q.correctAnswer}",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(q.explanation, style = MaterialTheme.typography.bodyMedium)
    }
}
