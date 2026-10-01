package com.numipad.junior.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.numipad.junior.AppContainer
import com.numipad.junior.data.repository.HistoryItem
import com.numipad.junior.ui.theme.NumiColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.text.DateFormat
import java.util.Date

class HistoryViewModel(container: AppContainer) : ViewModel() {
    val items: StateFlow<List<HistoryItem>?> = container.history.latest
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: HistoryViewModel, onBack: () -> Unit, onUseResult: (String) -> Unit) {
    val items by vm.items.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        containerColor = NumiColors.Cream,
        topBar = {
            TopAppBar(
                title = { Text("Calculation history") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NumiColors.Cream),
            )
        },
    ) { padding ->
        val list = items ?: return@Scaffold
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No calculations yet. Finished calculations will appear here.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(list, key = { it.id }) { item ->
                Row(
                    Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(NumiColors.TealLight)
                        .clickable(role = Role.Button, onClickLabel = "Open details") { selectedId = item.id }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .semantics(mergeDescendants = true) { contentDescription = item.spoken },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(item.expression, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        "= " + (if (item.rounded) "≈ " else "") + item.result,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
        }
        val selected = list.firstOrNull { it.id == selectedId }
        if (selected != null) {
            AlertDialog(
                onDismissRequest = { selectedId = null },
                title = { Text("Calculation") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${selected.expression} =", style = MaterialTheme.typography.titleLarge)
                        Text(
                            (if (selected.rounded) "≈ " else "") + selected.result,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        if (selected.rounded) Text("Rounded to 6 decimal places", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(selected.createdAt)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = NumiColors.NavySoft,
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { selectedId = null; onUseResult(selected.result) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Use result") }
                },
                dismissButton = {
                    TextButton(onClick = { selectedId = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Close") }
                },
            )
        }
    }
}
