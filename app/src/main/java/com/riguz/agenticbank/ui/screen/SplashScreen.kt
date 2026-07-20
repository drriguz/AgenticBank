package com.riguz.agenticbank.ui.screen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riguz.agenticbank.model.ModelManager
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    modelManager: ModelManager,
    onModelReady: () -> Unit,
    onGoHome: () -> Unit,
    onPickFile: () -> Unit,
) {
    val state by modelManager.state.collectAsStateWithLifecycle()
    var elapsed by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        modelManager.loadModel()
    }

    LaunchedEffect(state) {
        if (state is ModelManager.State.Loading) {
            while (true) {
                delay(1000)
                elapsed++
            }
        }
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

        Spacer(modifier = Modifier.height(32.dp))

        AnimatedAiStar(
            modifier = Modifier.size(120.dp),
        )

        Spacer(modifier = Modifier.height(32.dp))

        when (val currentState = state) {
            is ModelManager.State.Idle,
            is ModelManager.State.Loading -> {
                LoadingContent(
                    loading = currentState as? ModelManager.State.Loading,
                    elapsed = elapsed,
                )
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
private fun LoadingContent(loading: ModelManager.State.Loading?, elapsed: Int) {
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
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Elapsed: ${elapsed}s",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
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

@Composable
fun AnimatedAiStar(
    modifier: Modifier = Modifier,
) {
    val colors = listOf(
        Color(0xFF4285F4),
        Color(0xFFEA4335),
        Color(0xFFFBBC04),
        Color(0xFF34A853),
    )

    val transition = rememberInfiniteTransition(label = "aiStars")

    val pulse0 = transition.animateFloat(
        initialValue = 0.3f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(1400, 0, easing = LinearEasing), RepeatMode.Reverse),
        label = "p0",
    ).value
    val pulse1 = transition.animateFloat(
        initialValue = 0.3f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(1700, 350, easing = LinearEasing), RepeatMode.Reverse),
        label = "p1",
    ).value
    val pulse2 = transition.animateFloat(
        initialValue = 0.3f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(1200, 700, easing = LinearEasing), RepeatMode.Reverse),
        label = "p2",
    ).value
    val pulse3 = transition.animateFloat(
        initialValue = 0.3f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(1500, 1000, easing = LinearEasing), RepeatMode.Reverse),
        label = "p3",
    ).value

    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val baseRadius = size.minDimension * 0.3f

            val sparkles = listOf(
                Pair(Offset(-0.20f, -0.40f), 0),
                Pair(Offset( 0.40f, -0.25f), 1),
                Pair(Offset(-0.35f,  0.20f), 2),
                Pair(Offset( 0.25f,  0.35f), 3),
            )

            val pulses = listOf(pulse0, pulse1, pulse2, pulse3)

            sparkles.forEach { (offset, idx) ->
                val sparkCenter = Offset(
                    center.x + offset.x * baseRadius * 2.5f,
                    center.y + offset.y * baseRadius * 2.5f,
                )
                val scale = pulses[idx]
                drawSparkle(sparkCenter, baseRadius * 0.7f * scale, colors[idx])
            }
        }
    }
}

private fun DrawScope.drawSparkle(center: Offset, size: Float, color: Color) {
    val innerRadius = size * 0.35f
    val outerRadius = size
    val points = 4

    val path = Path()
    for (i in 0 until points * 2) {
        val angle = (Math.PI / points * i - Math.PI / 2).toFloat()
        val r = if (i % 2 == 0) outerRadius else innerRadius
        val x = center.x + r * kotlin.math.cos(angle.toDouble()).toFloat()
        val y = center.y + r * kotlin.math.sin(angle.toDouble()).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()

    drawPath(path, color.copy(alpha = 0.25f))
    drawPath(path, color = color.copy(alpha = 0.7f), style = Stroke(width = 3f))
}
