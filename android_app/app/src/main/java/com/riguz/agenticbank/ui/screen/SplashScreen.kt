package com.riguz.agenticbank.ui.screen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
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
private val AtmGold = Color(0xFFFFD700)
private val AtmTextPrimary = Color(0xFFE8F0FE)
private val AtmTextSecondary = Color(0xFF8899AA)
private val AtmCardBg = Brush.linearGradient(
    colors = listOf(Color(0xFF1A2744), Color(0xFF0F1A2E)),
)

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
            Spacer(modifier = Modifier.height(48.dp))

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
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Thin,
                letterSpacing = 12.sp,
                color = AtmTextPrimary,
            )

            Spacer(modifier = Modifier.height(32.dp))

            AtmCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )

            Spacer(modifier = Modifier.height(40.dp))

            AnimatedAtmLogo(modifier = Modifier.size(80.dp))

            Spacer(modifier = Modifier.height(32.dp))

            when (val currentState = state) {
                is ModelManager.State.Idle,
                is ModelManager.State.Loading -> {
                    AtmLoadingContent(
                        loading = currentState as? ModelManager.State.Loading,
                        elapsed = elapsed,
                    )
                }

                is ModelManager.State.Ready -> {
                    AtmStatusText("SYSTEM READY", AtmTeal)
                }

                is ModelManager.State.Error -> {
                    AtmErrorContent(
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
private fun AtmCard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(AtmCardBg)
            .padding(20.dp),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp, 28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(AtmGold, Color(0xFFB8860B)),
                            )
                        ),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "CONTACTLESS",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 2.sp,
                    color = AtmTextSecondary.copy(alpha = 0.5f),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "4242  ****  ****  4242",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp,
                color = AtmTextPrimary,
                fontFamily = FontFamily.Monospace,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "CARDHOLDER",
                        style = MaterialTheme.typography.labelSmall,
                        color = AtmTextSecondary.copy(alpha = 0.5f),
                    )
                    Text(
                        text = "JOHN SMITH",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = AtmTextPrimary,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "VALID THRU",
                        style = MaterialTheme.typography.labelSmall,
                        color = AtmTextSecondary.copy(alpha = 0.5f),
                    )
                    Text(
                        text = "12/26",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = AtmTextPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun AtmLoadingContent(loading: ModelManager.State.Loading?, elapsed: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(
            modifier = Modifier.size(36.dp),
            strokeWidth = 2.dp,
            color = AtmTeal,
            trackColor = AtmDarkSurface,
        )
        Spacer(modifier = Modifier.height(16.dp))
        AtmStatusText("INITIALIZING SYSTEM", AtmTealDim)
        if (loading != null && loading.progress > 0f) {
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { loading.progress },
                modifier = Modifier
                    .fillMaxWidth(0.6f)
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
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun AtmStatusText(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        letterSpacing = 2.sp,
        color = color,
    )
}

@Composable
private fun AtmErrorContent(
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
            colors = ButtonDefaults.buttonColors(
                containerColor = AtmTeal,
                contentColor = AtmDark,
            ),
            shape = RoundedCornerShape(4.dp),
        ) {
            Text("SELECT MODEL FILE", letterSpacing = 1.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = AtmTealDim,
                contentColor = AtmDark,
            ),
            shape = RoundedCornerShape(4.dp),
        ) {
            Text("RETRY", letterSpacing = 1.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onGoHome,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = AtmTextSecondary,
            ),
            shape = RoundedCornerShape(4.dp),
        ) {
            Text("CONTINUE ANYWAY", letterSpacing = 1.sp)
        }
    }
}

@Composable
fun AnimatedAtmLogo(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "atmLogo")
    val pulse = transition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    ).value

    val shimmer = transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer",
    ).value

    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension * 0.38f

            drawCircle(
                color = AtmTeal.copy(alpha = 0.08f * pulse),
                radius = radius * 1.4f,
                center = center,
            )
            drawCircle(
                color = AtmTeal.copy(alpha = 0.15f * pulse),
                radius = radius * 1.1f,
                center = center,
            )
            drawCircle(
                color = AtmTeal.copy(alpha = 0.3f * pulse),
                radius = radius,
                center = center,
                style = Stroke(width = 2f),
            )

            drawCircle(
                color = AtmTeal,
                radius = radius * 0.15f,
                center = center,
            )

            val cardW = radius * 1.6f
            val cardH = cardW * 0.63f
            val cardLeft = center.x - cardW / 2
            val cardTop = center.y - cardH / 2

            drawRoundRect(
                color = AtmTextPrimary.copy(alpha = 0.9f),
                topLeft = Offset(cardLeft, cardTop),
                size = Size(cardW, cardH),
                cornerRadius = CornerRadius(8f, 8f),
            )

            drawRoundRect(
                color = AtmGold,
                topLeft = Offset(cardLeft + cardW * 0.08f, cardTop + cardH * 0.2f),
                size = Size(cardW * 0.2f, cardH * 0.35f),
                cornerRadius = CornerRadius(4f, 4f),
            )

            val lineY = cardTop + cardH * 0.72f
            drawLine(
                color = AtmTextSecondary.copy(alpha = 0.3f),
                start = Offset(cardLeft + cardW * 0.08f, lineY),
                end = Offset(cardLeft + cardW * 0.6f, lineY),
                strokeWidth = 2f,
            )

            val shimmerX = cardLeft + cardW * shimmer
            drawLine(
                color = Color.White.copy(alpha = 0.15f),
                start = Offset(shimmerX, cardTop),
                end = Offset(shimmerX, cardTop + cardH),
                strokeWidth = cardW * 0.15f,
            )
        }
    }
}
