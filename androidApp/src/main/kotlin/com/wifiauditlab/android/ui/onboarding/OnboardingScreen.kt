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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wifiauditlab.android.R
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = koinViewModel(),
    replay: Boolean = false,
    onFinished: () -> Unit,
) {
    var sessionActive by remember { mutableStateOf(false) }
    LaunchedEffect(replay) {
        if (replay) {
            viewModel.reopenFromSettings()
        } else if (viewModel.state.value.completed) {
            onFinished()
        }
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.completed) {
        if (!state.completed) {
            sessionActive = true
        } else if (sessionActive) {
            onFinished()
        }
    }
    if (state.completed) return

    val page = state.page
    val progressCd = stringResource(R.string.onboarding_cd_progress)
    val skipCd = stringResource(R.string.onboarding_cd_skip)
    val continueCd = stringResource(R.string.onboarding_cd_continue)
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.onboarding_title)) }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(
                    R.string.onboarding_progress,
                    state.pageIndex + 1,
                    OnboardingPage.entries.size,
                ),
                modifier = Modifier.semantics { contentDescription = progressCd },
            )
            Text(stringResource(page.titleRes), fontWeight = FontWeight.Bold)
            Text(stringResource(page.bodyRes))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { viewModel.skip() },
                    modifier = Modifier.weight(1f).semantics { contentDescription = skipCd },
                ) { Text(stringResource(R.string.onboarding_skip)) }
                Button(
                    onClick = { viewModel.next() },
                    modifier = Modifier.weight(1f).semantics { contentDescription = continueCd },
                ) {
                    Text(
                        if (state.isLast) {
                            stringResource(R.string.onboarding_start)
                        } else {
                            stringResource(R.string.onboarding_continue)
                        },
                    )
                }
            }
        }
    }
}
