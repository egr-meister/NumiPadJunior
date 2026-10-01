package com.numipad.junior.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.numipad.junior.domain.questions.Topic
import com.numipad.junior.ui.theme.LocalReduceMotion
import com.numipad.junior.ui.theme.NumiColors
import kotlin.random.Random

/** Rounded panel with small, faint geometric decorations in the corners (decor only, no semantics). */
@Composable
fun TabletPanel(
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(color)
            .border(2.dp, NumiColors.Navy.copy(alpha = 0.12f), RoundedCornerShape(28.dp)),
    ) {
        GeometricDecor(Modifier.matchParentSize())
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun BoxScope.GeometricDecor(modifier: Modifier) {
    val reduce = LocalReduceMotion.current
    val angle = if (reduce) {
        0f
    } else {
        val t = rememberInfiniteTransition(label = "decor")
        t.animateFloat(
            initialValue = -6f,
            targetValue = 6f,
            animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse),
            label = "tilt",
        ).value
    }
    val tint = NumiColors.Navy.copy(alpha = 0.08f)
    Canvas(modifier.clearAndSetSemantics { }) {
        val s = 18.dp.toPx()
        // triangle top-right
        val tri = Path().apply {
            moveTo(size.width - 3 * s, s); lineTo(size.width - 2 * s, s); lineTo(size.width - 2.5f * s, 0.15f * s); close()
        }
        drawPath(tri, tint, style = Stroke(width = 2.dp.toPx()))
        // circle bottom-left
        drawCircle(tint, radius = s * 0.6f, center = Offset(1.4f * s, size.height - 1.4f * s), style = Stroke(2.dp.toPx()))
        // chalk dashes bottom-right
        drawLine(
            tint, Offset(size.width - 4 * s, size.height - 0.8f * s), Offset(size.width - s, size.height - 0.8f * s),
            strokeWidth = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
        )
    }
    Box(
        Modifier
            .align(Alignment.TopStart)
            .padding(6.dp)
            .size(14.dp)
            .rotate(angle)
            .clearAndSetSemantics { },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 2.dp.toPx()
            drawLine(tint, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), w)
            drawLine(tint, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), w)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier.semantics { heading() },
    )
}

/** Dot counters for small values with a text equivalent for accessibility. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CountersVisual(topic: Topic, a: Int, b: Int, description: String, modifier: Modifier = Modifier) {
    val dot: Dp = 14.dp
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = description }) {
        when (topic) {
            Topic.ADDITION -> FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(a) { Dot(dot, NumiColors.TealDark) }
                Spacer(Modifier.width(dot))
                repeat(b) { Dot(dot, NumiColors.Navy) }
            }
            Topic.SUBTRACTION -> FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(a) { i -> Dot(dot, NumiColors.TealDark, crossed = i >= a - b) }
            }
            Topic.MULTIPLICATION -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(a) { Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { repeat(b) { Dot(dot, NumiColors.TealDark) } } }
            }
            Topic.DIVISION -> FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(b) {
                    Row(
                        Modifier
                            .border(1.dp, NumiColors.NavySoft.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) { repeat(a / b) { Dot(dot, NumiColors.TealDark) } }
                }
            }
        }
    }
}

@Composable
private fun Dot(size: Dp, color: Color, crossed: Boolean = false) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (crossed) color.copy(alpha = 0.25f) else color),
        contentAlignment = Alignment.Center,
    ) {
        if (crossed) {
            Canvas(Modifier.size(size)) {
                drawLine(NumiColors.Incorrect, Offset(0f, 0f), Offset(this.size.width, this.size.height), 2.dp.toPx())
            }
        }
    }
}

/**
 * Grown-up confirmation for destructive actions. An accidental-entry safeguard, not
 * authentication: the adult types the answer to a two-digit sum (keyboard and
 * screen-reader friendly — no gesture-only interaction).
 */
@Composable
fun AdultConfirmDialog(
    title: String,
    explanation: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val x = rememberSaveable { Random.nextInt(12, 40) }
    val y = rememberSaveable { Random.nextInt(12, 40) }
    var input by rememberSaveable { mutableStateOf("") }
    val ok = input.trim() == (x + y).toString()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(explanation, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Grown-up check: type the answer to $x + $y to continue.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { v -> input = v.filter(Char::isDigit).take(3) },
                    label = { Text("Answer to $x plus $y") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = ok, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
        },
    )
}

/** Simple confirmation (non-destructive to stored data, e.g. ending a session). */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = { TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = 48.dp)) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") } },
    )
}

@Composable
fun StatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(NumiColors.Cream.copy(alpha = 0.85f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { },
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelMedium, color = NumiColors.NavySoft)
    }
}

