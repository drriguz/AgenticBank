package com.riguz.agenticbank.ui.screen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riguz.agenticbank.model.ModelManager
import kotlinx.coroutines.delay

private val AtmDark = Color(0xFF0A1628)
private val AtmDarkSurface = Color(0xFF121E33)
private val AtmTeal = Color(0xFF00D4AA)
private val AtmTealDim = Color(0xFF00A88A)
private val AtmTextPrimary = Color(0xFFE8F0FE)
private val AtmTextSecondary = Color(0xFF8899AA)

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AtmDark),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "STANDARD CHARTERED",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Light,
                letterSpacing = 4.sp,
                color = AtmTeal,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "A T M",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Thin,
                letterSpacing = 12.sp,
                color = AtmTextPrimary,
            )

            Spacer(modifier = Modifier.height(48.dp))

            AtmPulse(modifier = Modifier.size(64.dp))

            Spacer(modifier = Modifier.height(32.dp))

            when (val currentState = state) {
                is ModelManager.State.Idle,
                is ModelManager.State.Loading -> {
                    AtmLoading(
                        loading = currentState as? ModelManager.State.Loading,
                        elapsed = elapsed,
                    )
                }

                is ModelManager.State.Ready -> {
                    Text(
                        text = "SYSTEM READY",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 2.sp,
                        color = AtmTeal,
                    )
                }

                is ModelManager.State.Error -> {
                    AtmError(
                        message = currentState.message,
                        onRetry = { modelManager.retry() },
                        onGoHome = onGoHome,
                        onPickFile = onPickFile,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Powered by Gemma 4 on-device",
                style = MaterialTheme.typography.labelSmall,
                color = AtmTextSecondary.copy(alpha = 0.4f),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AtmLoading(loading: ModelManager.State.Loading?, elapsed: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            strokeWidth = 2.dp,
            color = AtmTeal,
            trackColor = AtmDarkSurface,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "INITIALIZING",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp,
            color = AtmTealDim,
        )
        if (loading != null && loading.progress > 0f) {
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { loading.progress },
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp)),
                color = AtmTeal,
                trackColor = AtmDarkSurface,
            )
        }
        if (elapsed > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${elapsed}s",
                style = MaterialTheme.typography.labelSmall,
                color = AtmTextSecondary.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun AtmError(
    message: String,
    onRetry: () -> Unit,
    onGoHome: () -> Unit,
    onPickFile: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "SYSTEM ERROR",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp,
            color = Color(0xFFFF6B6B),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = AtmTextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onPickFile,
            colors = ButtonDefaults.buttonColors(containerColor = AtmTeal, contentColor = AtmDark),
            shape = RoundedCornerShape(4.dp),
        ) { Text("SELECT MODEL", letterSpacing = 1.sp) }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = AtmTealDim, contentColor = AtmDark),
            shape = RoundedCornerShape(4.dp),
        ) { Text("RETRY", letterSpacing = 1.sp) }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onGoHome,
            shape = RoundedCornerShape(4.dp),
        ) { Text("CONTINUE", letterSpacing = 1.sp, color = AtmTextSecondary) }
    }
}

@Composable
private fun AtmPulse(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha = transition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Reverse),
        label = "alpha",
    ).value

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension * 0.35f
        drawCircle(AtmTeal.copy(alpha = 0.1f * alpha), r * 1.4f, center)
        drawCircle(AtmTeal.copy(alpha = 0.2f * alpha), r * 1.1f, center)
        drawCircle(AtmTeal.copy(alpha = 0.5f * alpha), r, center)
        drawCircle(AtmTeal, r * 0.15f, center)
    }
}
