package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.RadioChannelState
import com.example.ui.theme.MinimalColorsInstance

/**
 * Giant Inclusive Push-to-Talk Button.
 * Designed for layman, non-literate, and emergency field use:
 * - Minimum touch target >= 120dp (occupies ~45-50% height on talk screen)
 * - Pulsing expanding concentric ripple animations on active press/transmission
 * - High-contrast visual icons and voice status indicators
 */
@Composable
fun PttButtonWithRings(
    channelState: RadioChannelState,
    isPttMode: Boolean,
    onPressed: () -> Unit,
    onReleased: () -> Unit,
    modifier: Modifier = Modifier,
    buttonDiameter: Dp = 190.dp
) {
    val colors = MinimalColorsInstance
    val haptic = LocalHapticFeedback.current
    var isPressedState by remember { mutableStateOf(false) }

    val isTransmitting = channelState == RadioChannelState.TRANSMITTING || (isPttMode && isPressedState)
    val isListening = channelState == RadioChannelState.LISTENING
    val isReceiving = channelState == RadioChannelState.RECEIVING

    val isActive = isTransmitting || isListening || isReceiving

    // Gentle press scale
    val buttonScale by animateFloatAsState(
        targetValue = if (isPressedState) 0.94f else 1.0f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "PttScale"
    )

    // Pulsing Ripple Animations
    val infiniteTransition = rememberInfiniteTransition(label = "PttRipples")
    val ripple1Scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Ripple1"
    )
    val ripple1Alpha by infiniteTransition.animateFloat(
        initialValue = if (isActive) 0.5f else 0.15f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Ripple1Alpha"
    )

    val ripple2Scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, delayMillis = 300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Ripple2"
    )
    val ripple2Alpha by infiniteTransition.animateFloat(
        initialValue = if (isActive) 0.35f else 0.08f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, delayMillis = 300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Ripple2Alpha"
    )

    // Smooth color state transitions
    val containerColor by animateColorAsState(
        targetValue = when {
            isTransmitting -> colors.accent
            isListening -> colors.accentContainer
            isReceiving -> colors.accentContainer
            else -> colors.surface
        },
        animationSpec = tween(200),
        label = "PttContainer"
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            isTransmitting -> Color.White
            isListening -> colors.accent
            isReceiving -> colors.accent
            else -> colors.textPrimary
        },
        animationSpec = tween(200),
        label = "PttContent"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isTransmitting -> colors.accent
            isActive -> colors.accent
            else -> colors.outline
        },
        animationSpec = tween(200),
        label = "PttBorder"
    )

    Box(
        modifier = modifier
            .size(buttonDiameter + 64.dp),
        contentAlignment = Alignment.Center
    ) {
        // Concentric Ripple 2
        if (isActive || isPressedState) {
            Canvas(modifier = Modifier.size(buttonDiameter * ripple2Scale)) {
                drawCircle(
                    color = colors.accent.copy(alpha = ripple2Alpha),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }

        // Concentric Ripple 1
        if (isActive || isPressedState) {
            Canvas(modifier = Modifier.size(buttonDiameter * ripple1Scale)) {
                drawCircle(
                    color = colors.accent.copy(alpha = ripple1Alpha),
                    style = Stroke(width = 4.dp.toPx())
                )
            }
        }

        // Main Tactile Button (>=120dp touch target)
        Box(
            modifier = Modifier
                .size(buttonDiameter)
                .scale(buttonScale)
                .clip(CircleShape)
                .background(containerColor)
                .border(
                    width = if (isActive) 2.5.dp else 1.5.dp,
                    color = borderColor,
                    shape = CircleShape
                )
                .pointerInput(isPttMode) {
                    if (isPttMode) {
                        detectTapGestures(
                            onPress = {
                                isPressedState = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onPressed()
                                val released = tryAwaitRelease()
                                isPressedState = false
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onReleased()
                            }
                        )
                    } else {
                        detectTapGestures(
                            onTap = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (isListening) onReleased() else onPressed()
                            }
                        )
                    }
                }
                .testTag("ptt_button_main"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = when {
                        isReceiving -> Icons.Default.VolumeUp
                        isListening -> Icons.Default.GraphicEq
                        else -> Icons.Default.Mic
                    },
                    contentDescription = "Push to talk",
                    tint = contentColor,
                    modifier = Modifier.size(buttonDiameter * 0.28f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = when {
                        isTransmitting -> "TRANSMITTING"
                        isReceiving -> "RECEIVING"
                        isListening -> "LISTENING..."
                        isPttMode -> "HOLD TO TALK"
                        else -> "TAP TO TALK"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (isTransmitting) "Voice Active" else "Ready to Talk",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = contentColor.copy(alpha = 0.85f)
                )
            }
        }
    }
}
