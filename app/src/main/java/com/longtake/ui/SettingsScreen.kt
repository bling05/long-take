package com.longtake.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.longtake.Features
import com.longtake.data.ClaudeMode
import com.longtake.data.ClaudeModel
import com.longtake.data.Repository
import com.longtake.data.ThemeMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(repo: Repository, onOpenBlocked: () -> Unit) {
    val scope = rememberCoroutineScope()
    val s by repo.settings.flow.collectAsStateWithLifecycle(initialValue = null)
    val blocked by repo.db.channels().blocked().collectAsStateWithLifecycle(initialValue = emptyList())
    var ytKey by remember { mutableStateOf<String?>(null) }
    var anthKey by remember { mutableStateOf<String?>(null) }
    var importMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(s != null) {
        s?.let { if (ytKey == null) ytKey = it.youtubeApiKey; if (anthKey == null) anthKey = it.anthropicApiKey }
    }

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importMsg = "Importing…"
            importMsg = try { repo.importWatchHistory(uri) } catch (e: Exception) { "Import failed: ${e.message}" }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { pad ->
        val st = s ?: return@Scaffold
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {

            Section("Appearance")
            Row {
                ThemeMode.entries.forEach { m ->
                    FilterChip(
                        selected = st.theme == m,
                        onClick = { scope.launch { repo.settings.setTheme(m) } },
                        label = { Text(m.label) },
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
            }

            Section("YouTube access")
            Hint("Needed to load videos. Free from Google Cloud (see the setup guide that came with the app).")
            OutlinedTextField(
                value = ytKey.orEmpty(), onValueChange = { ytKey = it },
                label = { Text("YouTube API key") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { scope.launch { repo.settings.setYoutubeKey(ytKey.orEmpty()); repo.refreshInBackground() } },
                enabled = ytKey != st.youtubeApiKey,
                modifier = Modifier.padding(top = 6.dp),
            ) { Text("Save key") }

            Spacer(Modifier.size(12.dp))
            Text("Watch history", style = MaterialTheme.typography.titleSmall)
            Hint("YouTube doesn't share watch history with apps. To mark everything you've watched: go to takeout.google.com → YouTube → History, choose JSON format, then pick watch-history.json here.")
            OutlinedButton(onClick = { fileLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Import watch-history.json") }
            importMsg?.let { Hint(it) }

            Section("Video synopses")
            Hint("A one- or two-sentence summary under each video. Each video is summarised once and shared with everyone using the app.")
            val synopsisStatus by repo.synopses.status.collectAsStateWithLifecycle()
            val feedCount by repo.db.synopses().feedCount().collectAsStateWithLifecycle(initialValue = 0)
            val doneCount by repo.db.synopses().feedDoneCount().collectAsStateWithLifecycle(initialValue = 0)
            Text(
                buildString {
                    append("$doneCount of $feedCount videos have a synopsis")
                    if (synopsisStatus.writing) append(" · writing more…")
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp),
            )
            synopsisStatus.error?.let { Hint(it) }
            Hint("To write missing ones yourself, add a Claude API key from console.anthropic.com. With Claude Haiku it costs about $0.001 per video.")
            OutlinedTextField(
                value = anthKey.orEmpty(), onValueChange = { anthKey = it },
                label = { Text("Claude API key") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Button(
                onClick = {
                    scope.launch {
                        repo.settings.setAnthropicKey(anthKey.orEmpty())
                        repo.synopses.syncInBackground()
                    }
                },
                enabled = anthKey != st.anthropicApiKey,
                modifier = Modifier.padding(top = 6.dp),
            ) { Text("Save key") }

            if (Features.AI_CHECK) {
                Section("AI-script check")
                Hint("Every video gets a free check on your phone (AI phrases, how personal the narration is, how often the channel posts). Claude can double-check for a small cost.")
                Text("Ask Claude", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
                ClaudeMode.entries.forEach { m ->
                    Row(Modifier.fillMaxWidth().clickable { scope.launch { repo.settings.setClaudeMode(m) } }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = st.claudeMode == m, onClick = { scope.launch { repo.settings.setClaudeMode(m) } })
                        Column { Text(m.label); Hint(m.detail) }
                    }
                }
                Text("Model", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                ClaudeModel.entries.forEach { m ->
                    Row(Modifier.fillMaxWidth().clickable { scope.launch { repo.settings.setClaudeModel(m) } }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = st.claudeModel == m, onClick = { scope.launch { repo.settings.setClaudeModel(m) } })
                        Column { Text(m.label); Hint(m.costNote) }
                    }
                }
                if (st.claudeMode != ClaudeMode.OFF && st.anthropicApiKey.isBlank()) {
                    Hint("Uses the Claude key under Video synopses. Without one, only the free check runs.")
                }
            }

            Section("Filters")
            if (Features.AI_CHECK) {
                ToggleRow("Hide videos that are likely AI", st.hideAi) { v -> scope.launch { repo.settings.setHideAi(v) } }
                ToggleRow("Also hide \"unsure\" videos", st.hideUnsure) { v -> scope.launch { repo.settings.setHideUnsure(v) } }
            }
            ToggleRow("Hide videos I've watched", st.hideWatched) { v -> scope.launch { repo.settings.setHideWatched(v) } }
            Text("Minimum length", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            Row {
                listOf(10, 15, 20, 30, 45).forEach { m ->
                    FilterChip(
                        selected = st.minMinutes == m,
                        onClick = { scope.launch { repo.settings.setMinMinutes(m) } },
                        label = { Text("$m min") },
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
            }

            Section("Blocked channels")
            Hint(if (blocked.isEmpty()) "You haven't blocked any channels." else "${blocked.size} blocked. Their videos are hidden from Discover and Search.")
            OutlinedButton(onClick = onOpenBlocked, modifier = Modifier.padding(top = 6.dp)) {
                Text(if (blocked.isEmpty()) "See blocked channels" else "See blocked channels (${blocked.size})")
            }
            Spacer(Modifier.size(40.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(top = 16.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Switch(checked = value, onCheckedChange = onChange)
    }
}
