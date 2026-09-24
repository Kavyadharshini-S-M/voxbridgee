package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MinimalColorsInstance
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Inclusive 1.5-Second Hold Emergency Ring Button.
 * Replaces complex sliders with a giant tactile hold ring (>120dp touch target)
 * featuring progressive clockwise fill, pulsing alert rings, and continuous haptic feedback.
 */
@Composable
fun HoldToSosButton(
    onHoldComplete: () -> Unit,
    modifier: Modifier = Modifier,
    buttonDiameter: Dp = 160.dp,
    holdDurationMs: Long = 1500L
) {
    val colors = MinimalColorsInstance
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var isHolding by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var holdJob by remember { mutableStateOf<Job?>(null) }

    val infiniteTransition = rememberInfiniteTransition(label = "SosPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SosPulseScale"
    )

    Box(
        modifier = modifier
            .size(buttonDiameter + 32.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer Pulsing Danger Aura (when idle or holding)
        Canvas(modifier = Modifier.size(buttonDiameter + 24.dp)) {
            val auraColor = colors.error.copy(alpha = if (isHolding) 0.25f else 0.10f)
            drawCircle(
                color = auraColor,
                radius = (size.minDimension / 2f) * (if (isHolding) 1f else pulseScale)
            )
        }

        // Circular Progress Arc Canvas
        Canvas(modifier = Modifier.size(buttonDiameter + 8.dp)) {
            val strokeWidth = 6.dp.toPx()
            // Track background
            drawCircle(
                color = colors.error.copy(alpha = 0.2f),
                style = Stroke(width = strokeWidth)
            )

            // Dynamic progress fill (Clockwise from 12 o'clock / -90 deg)
            if (holdProgress > 0f) {
                drawArc(
                    color = colors.error,
                    startAngle = -90f,
                    sweepAngle = holdProgress * 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        // Tactile Center Button
        Box(
            modifier = Modifier
                .size(buttonDiameter)
                .scale(if (isHolding) 0.94f else 1f)
                .clip(CircleShape)
                .background(if (isHolding) colors.error else colors.surface)
                .border(
                    width = 2.dp,
                    color = colors.error,
                    shape = CircleShape
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isHolding = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            holdProgress = 0f

                            holdJob?.cancel()
                            holdJob = scope.launch {
                                val startTime = System.currentTimeMillis()
                                var lastHapticStep = 0
                                while (isHolding) {
                                    val elapsed = System.currentTimeMillis() - startTime
                                    val prog = (elapsed.toFloat() / holdDurationMs).coerceIn(0f, 1f)
                                    holdProgress = prog

                                    // Stepped haptic pulses every 25% progress
                                    val currentStep = (prog * 4).toInt()
                                    if (currentStep > lastHapticStep) {
                                        lastHapticStep = currentStep
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }

                                    if (prog >= 1.0f) {
                                        // Complete! Trigger SOS
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        delay(50)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onHoldComplete()
                                        isHolding = false
                                        holdProgress = 0f
                                        break
                                    }
                                    delay(25)
                                }
                            }

                            val released = tryAwaitRelease()
                            isHolding = false
                            holdJob?.cancel()
                            holdProgress = 0f
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    )
                }
                .testTag("sos_hold_button"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = "Hold 1.5s for SOS",
                    tint = if (isHolding) Color.White else colors.error,
                    modifier = Modifier.size(44.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isHolding) {
                        val remainingSec = ((1f - holdProgress) * 1.5f).coerceAtLeast(0.1f)
                        String.format(java.util.Locale.US, "HOLD %.1fs", remainingSec)
                    } else {
                        "HOLD 1.5s FOR SOS"
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isHolding) Color.White else colors.error,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = if (isHolding) "KEEP HOLDING" else "EMERGENCY BROADCAST",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isHolding) Color.White.copy(alpha = 0.85f) else colors.textSecondary
                )
            }
        }
    }
}
