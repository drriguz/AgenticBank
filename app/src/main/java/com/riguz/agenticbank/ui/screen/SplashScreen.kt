package com.riguz.agenticbank.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riguz.agenticbank.model.ModelManager

@Composable
fun SplashScreen(
    modelManager: ModelManager,
    onModelReady: () -> Unit,
    onGoHome: () -> Unit,
    onPickFile: () -> Unit,
) {
    val state by modelManager.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        modelManager.loadModel()
    }

    LaunchedEffect(state) {
        if (state is ModelManager.State.Ready) {
            onModelReady()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "AgenticBank",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "On-Device LLM Demo",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(48.dp))

        when (val currentState = state) {
            is ModelManager.State.Idle,
            is ModelManager.State.Loading -> {
                LoadingContent(currentState as? ModelManager.State.Loading)
            }

            is ModelManager.State.Ready -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Model ready",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is ModelManager.State.Error -> {
                ErrorContent(
                    message = currentState.message,
                    onRetry = { modelManager.retry() },
                    onGoHome = onGoHome,
                    onPickFile = onPickFile,
                )
            }
        }
    }
}

@Composable
private fun LoadingContent(loading: ModelManager.State.Loading?) {
    if (loading != null) {
        LinearProgressIndicator(
            progress = { loading.progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "${(loading.progress * 100).toInt()}%",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = loading.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    } else {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            strokeWidth = 4.dp,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Preparing...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onGoHome: () -> Unit,
    onPickFile: () -> Unit,
) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
    )

    Spacer(modifier = Modifier.height(24.dp))

    Button(onClick = onPickFile) {
        Text("Select Model File")
    }

    Spacer(modifier = Modifier.height(8.dp))

    Button(onClick = onRetry) {
        Text("Retry")
    }

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedButton(onClick = onGoHome) {
        Text("Back to Home")
    }
}
