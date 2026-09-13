package com.wifiauditlab.android.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = koinViewModel(),
    replay: Boolean = false,
    onFinished: () -> Unit,
) {
    // Only finish after the user actually completes a session that was shown incomplete.
    // Replay starts with prefs.completed=true; reopen clears UI completed without finishing.
    var sawIncomplete by remember { mutableStateOf(false) }
    LaunchedEffect(replay) {
        if (replay) viewModel.reopenFromSettings()
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.completed) {
        if (!state.completed) {
            sawIncomplete = true
        } else if (sawIncomplete) {
            onFinished()
        }
    }
    if (state.completed) return

    val page = state.page
    Scaffold(topBar = { TopAppBar(title = { Text("Bienvenida") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Paso ${state.pageIndex + 1} de ${OnboardingPage.entries.size}",
                modifier = Modifier.semantics { contentDescription = "Progreso de onboarding" },
            )
            Text(page.title, fontWeight = FontWeight.Bold)
            Text(page.body)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { viewModel.skip() },
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Omitir onboarding" },
                ) { Text("Omitir") }
                Button(
                    onClick = { viewModel.next() },
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Continuar onboarding" },
                ) { Text(if (state.isLast) "Empezar" else "Continuar") }
            }
        }
    }
}
