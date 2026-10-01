package com.numipad.junior.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.numipad.junior.AppContainer
import com.numipad.junior.data.repository.AppSettings
import com.numipad.junior.domain.questions.Difficulty
import com.numipad.junior.domain.questions.Topic
import com.numipad.junior.ui.components.AdultConfirmDialog
import com.numipad.junior.ui.components.SectionTitle
import com.numipad.junior.ui.theme.NumiColors
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ResetKind(val title: String, val explanation: String, val confirm: String, val done: String) {
    HISTORY(
        "Clear calculation history?",
        "This removes all saved calculations from the History list. Practice progress and badges stay.",
        "Clear history",
        "Calculation history cleared.",
    ),
    PROGRESS(
        "Clear practice progress and badges?",
        "This removes every practice session, all answer records, daily totals and unlocked badges. Calculator history stays.",
        "Clear progress",
        "Practice progress and badges cleared.",
    ),
    ALL(
        "Clear all local data?",
        "This removes calculator history, practice sessions, progress, badges and settings, and restores the app to its first-launch state.",
        "Clear everything",
        "All local data cleared.",
    ),
}

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /** Emitted after a reset so the main screen can reload its state. */
    private val _resetDone = Channel<Unit>(Channel.CONFLATED)
    val resetDone = _resetDone.receiveAsFlow()

    fun setTopic(t: Topic) = viewModelScope.launch { container.settings.setDefaultTopic(t) }
    fun setDifficulty(d: Difficulty) = viewModelScope.launch { container.settings.setDefaultDifficulty(d) }
    fun setReduceMotion(v: Boolean) = viewModelScope.launch { container.settings.setReduceMotion(v) }

    fun reset(kind: ResetKind) {
        viewModelScope.launch {
            when (kind) {
                ResetKind.HISTORY -> container.history.clear()
                ResetKind.PROGRESS -> container.practice.clearProgress()
                ResetKind.ALL -> container.clearAllData()
            }
            _messages.send(kind.done)
            _resetDone.send(Unit)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    onBack: () -> Unit,
    onPrivacy: () -> Unit,
    onDataReset: () -> Unit,
) {
    val s by vm.settings.collectAsStateWithLifecycle()
    var pending by rememberSaveable { mutableStateOf<ResetKind?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm) { launch { vm.messages.collect { snackbar.showSnackbar(it) } }; vm.resetDone.collect { onDataReset() } }

    Scaffold(
        containerColor = NumiColors.Cream,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NumiColors.Cream),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Practice defaults")
                Text("Default topic", style = MaterialTheme.typography.titleMedium)
                FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Topic.entries.forEach { t ->
                        FilterChip(
                            selected = s.defaultTopic == t,
                            onClick = { vm.setTopic(t) },
                            label = { Text("${t.symbol}  ${t.label}") },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
                Text("Default difficulty", style = MaterialTheme.typography.titleMedium)
                FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Difficulty.entries.forEach { d ->
                        FilterChip(
                            selected = s.defaultDifficulty == d,
                            onClick = { vm.setDifficulty(d) },
                            label = { Text("${d.label} (${d.range.first}–${d.range.last})") },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }

                HorizontalDivider()
                SectionTitle("Display")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .toggleable(value = s.reduceMotion, role = Role.Switch, onValueChange = { vm.setReduceMotion(it) }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Reduce decorative animation", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Stops the gently moving shapes and fade effects.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NumiColors.NavySoft,
                        )
                    }
                    Switch(checked = s.reduceMotion, onCheckedChange = null)
                }

                HorizontalDivider()
                SectionTitle("Grown-ups: stored data")
                Text(
                    "These actions ask for a quick grown-up check so they are not tapped by accident.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NumiColors.NavySoft,
                )
                ResetKind.entries.forEach { kind ->
                    OutlinedButton(
                        onClick = { pending = kind },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) { Text(labelFor(kind)) }
                }

                HorizontalDivider()
                SectionTitle("Privacy")
                OutlinedButton(onClick = onPrivacy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text("Privacy information")
                }
                Text("NumiPad Junior 1.0.0", style = MaterialTheme.typography.labelMedium, color = NumiColors.NavySoft)
            }
        }
    }

    pending?.let { kind ->
        AdultConfirmDialog(
            title = kind.title,
            explanation = kind.explanation,
            confirmLabel = kind.confirm,
            onConfirm = { pending = null; vm.reset(kind) },
            onDismiss = { pending = null },
        )
    }
}

private fun labelFor(kind: ResetKind): String = when (kind) {
    ResetKind.HISTORY -> "Clear calculation history"
    ResetKind.PROGRESS -> "Clear practice progress and badges"
    ResetKind.ALL -> "Clear all local data"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = NumiColors.Cream,
        topBar = {
            TopAppBar(
                title = { Text("Privacy") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NumiColors.Cream),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PRIVACY_SECTIONS.forEach { (title, body) ->
                    SectionTitle(title)
                    Text(body, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

private val PRIVACY_SECTIONS = listOf(
    "Everything stays on this device" to
        "Calculations, calculation history, practice answers, progress and badges are stored only in the app’s private storage on this device.",
    "No account, no personal details" to
        "NumiPad Junior never asks for a name, age, email address or any other personal information.",
    "No internet" to
        "The app has no internet permission. It contains no advertising, analytics, tracking, payments, cloud services or external links, and works fully in airplane mode.",
    "No backup or transfer" to
        "App data is excluded from cloud backup and from device-to-device transfer, so it never leaves this device.",
    "No other permissions" to
        "The app does not use the camera, microphone, location, contacts or notifications.",
    "Removing data" to
        "A grown-up can clear calculation history, practice progress and badges, or all data at any time in Settings. Uninstalling the app also removes everything.",
)
