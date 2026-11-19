package com.longtake.ui

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.longtake.data.Repository
import com.longtake.social.AccountState
import com.longtake.social.VideoRef
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouScreen(
    repo: Repository,
    onOpenVideo: (VideoRef) -> Unit,
    onOpenSources: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by repo.social.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("You") },
                actions = {
                    IconButton(onClick = onOpenSources) { Icon(Icons.Filled.Subscriptions, "Sources") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Settings") }
                },
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (val s = state) {
                AccountState.Disabled -> EmptyMessage(
                    "Profiles aren't switched on yet",
                    "Everything else works without an account. Profiles need the Firebase setup step in SETUP.md.",
                )
                AccountState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                AccountState.SignedOut -> SignInForm(repo)
                is AccountState.NeedsUsername -> ChooseUsername(repo, s.suggestedName)
                is AccountState.Ready -> ProfileContent(repo, s.profile.uid, isMe = true, onOpenVideo = onOpenVideo, onOpenProfile = {})
            }
        }
    }
}

@Composable
private fun SignInForm(repo: Repository) {
    val scope = rememberCoroutineScope()
    val activity = LocalContext.current as? Activity
    var mode by remember { mutableIntStateOf(0) } // 0 sign in, 1 create account
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    fun run(block: suspend () -> Unit) {
        busy = true; msg = null
        scope.launch {
            try { block() } catch (e: Exception) { msg = (e.message ?: "Something went wrong") to true }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Rate essays, share reviews and follow people.", style = MaterialTheme.typography.titleMedium)
        Text(
            "Your ratings, saved videos and notes are also backed up. Notes stay private unless you share them.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        TabRow(selectedTabIndex = mode) {
            Tab(mode == 0, { mode = 0; msg = null }, text = { Text("Sign in") })
            Tab(mode == 1, { mode = 1; msg = null }, text = { Text("Create account") })
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            email, { email = it }, label = { Text("Email") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            password, { password = it }, label = { Text(if (mode == 1) "Password (8+ characters)" else "Password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Button(
            onClick = {
                run { if (mode == 0) repo.social.signInWithEmail(email, password) else repo.social.signUpWithEmail(email, password) }
            },
            enabled = !busy && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) { Text(if (mode == 0) "Sign in" else "Create account") }
        if (mode == 0) {
            TextButton(onClick = {
                if (email.isBlank()) msg = "Type your email above first." to true
                else run { repo.social.sendPasswordReset(email); msg = "Check your email for a reset link." to false }
            }) { Text("Forgot password?") }
        }
        if (repo.social.googleSignInAvailable && activity != null) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(Modifier.weight(1f)); Text("  or  ", style = MaterialTheme.typography.labelMedium); HorizontalDivider(Modifier.weight(1f))
            }
            OutlinedButton(onClick = { run { repo.social.signInWithGoogle(activity) } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Continue with Google")
            }
        }
        if (busy) CircularProgressIndicator(Modifier.padding(top = 16.dp).align(Alignment.CenterHorizontally))
        msg?.let { (m, err) ->
            Text(m, color = if (err) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun ChooseUsername(repo: Repository, suggested: String) {
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf(suggested) }
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Pick a username", style = MaterialTheme.typography.titleLarge)
        Text("This is how people find and follow you.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            username, { username = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' }.take(20) },
            label = { Text("Username") }, prefix = { Text("@") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )
        OutlinedTextField(name, { name = it.take(40) }, label = { Text("Display name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        Button(
            onClick = {
                busy = true; err = null
                scope.launch {
                    try { repo.social.claimUsername(username, name) } catch (e: Exception) { err = e.message }
                    busy = false
                }
            },
            enabled = !busy && username.length >= 3,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) { Text("Continue") }
        TextButton(onClick = { repo.social.signOut() }) { Text("Use a different account") }
        err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
