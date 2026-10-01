package com.numipad.junior.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.numipad.junior.AppContainer
import com.numipad.junior.data.repository.UnlockedBadge
import com.numipad.junior.domain.progress.Badge
import com.numipad.junior.domain.progress.DailyStats
import com.numipad.junior.domain.progress.MostPracticed
import com.numipad.junior.domain.progress.ProgressCalculator
import com.numipad.junior.domain.progress.TopicCount
import com.numipad.junior.ui.components.SectionTitle
import com.numipad.junior.ui.components.StatPill
import com.numipad.junior.ui.components.TabletPanel
import com.numipad.junior.ui.theme.NumiColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

data class ProgressUi(
    val today: DailyStats,
    val allTime: List<TopicCount>,
    val mostPracticed: MostPracticed,
    val badges: List<UnlockedBadge>,
)

class ProgressViewModel(private val container: AppContainer) : ViewModel() {
    val ui: StateFlow<ProgressUi?> =
        combine(container.practice.totals, container.practice.unlockedBadges) { totals, badges ->
            ProgressUi(
                today = ProgressCalculator.daily(container.clock.today(), totals),
                allTime = ProgressCalculator.allTime(totals),
                mostPracticed = ProgressCalculator.mostPracticed(totals),
                badges = badges,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val zone get() = container.clock.zone()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProgressScreen(vm: ProgressViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = NumiColors.Cream,
        topBar = {
            TopAppBar(
                title = { Text("Progress & badges") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NumiColors.Cream),
            )
        },
    ) { padding ->
        val u = ui ?: return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TabletPanel(NumiColors.Lavender, Modifier.fillMaxWidth()) {
                    SectionTitle("Today")
                    if (u.today.answered == 0) {
                        Text("No answers yet", style = MaterialTheme.typography.bodyLarge)
                    } else {
                        FlowRow(
                            Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            StatPill("Answered", u.today.answered.toString())
                            StatPill("Correct", u.today.correct.toString())
                            StatPill("Incorrect", u.today.incorrect.toString())
                            StatPill("Accuracy", u.today.accuracyPercent?.let { "$it%" } ?: "No answers yet")
                        }
                        Text("By topic", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                        u.today.byTopic.forEach { t ->
                            Text(
                                "${t.topic.symbol}  ${t.topic.label}: ${t.answered} answered, ${t.correct} correct",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }

                TabletPanel(NumiColors.TealLight, Modifier.fillMaxWidth()) {
                    SectionTitle("Most practiced")
                    Text(
                        when (val m = u.mostPracticed) {
                            MostPracticed.None -> "Try a topic to get started."
                            is MostPracticed.Single -> "${m.topic.label} (${m.answered} questions)"
                            is MostPracticed.Tied -> "Several topics: " + m.topics.joinToString(", ") { it.label } +
                                " (${m.answered} questions each)"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    val total = u.allTime.sumOf { it.answered }
                    if (total > 0) {
                        Text(
                            "All practice: $total questions answered",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NumiColors.NavySoft,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }

                TabletPanel(NumiColors.CreamDeep, Modifier.fillMaxWidth()) {
                    SectionTitle("Badges")
                    Text(
                        "Badges celebrate practicing — not speed or perfect scores.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NumiColors.NavySoft,
                    )
                    val unlocked = u.badges.associateBy { it.badge }
                    val fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Badge.entries.forEach { b ->
                            val got = unlocked[b]
                            BadgeRow(
                                badge = b,
                                unlockedText = got?.let {
                                    "Unlocked " + fmt.format(Instant.ofEpochMilli(it.unlockedAt).atZone(vm.zone).toLocalDate())
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeRow(badge: Badge, unlockedText: String?) {
    val unlocked = unlockedText != null
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (unlocked) NumiColors.YellowSoft else NumiColors.Cream)
            .border(2.dp, if (unlocked) NumiColors.Yellow else NumiColors.LavenderDeep, RoundedCornerShape(16.dp))
            .padding(12.dp)
            .semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (unlocked) "★" else "☆",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(end = 12.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(badge.title, style = MaterialTheme.typography.titleMedium)
            Text(badge.criteria, style = MaterialTheme.typography.bodyMedium)
            Text(
                unlockedText ?: "Locked",
                style = MaterialTheme.typography.labelMedium,
                color = NumiColors.NavySoft,
            )
        }
    }
}
