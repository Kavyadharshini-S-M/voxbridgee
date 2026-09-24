package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.VoiceMessageEntity
import com.example.model.SupportedLanguage
import com.example.ui.theme.MinimalColorsInstance
import com.example.viewmodel.MissionControlViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Deterministic vibrant avatar palette
private val AVATAR_PALETTE = listOf(
    Color(0xFF6C5CE7), // Indigo
    Color(0xFF00B894), // Teal / Emerald
    Color(0xFF0984E3), // Sky Blue
    Color(0xFFE17055), // Coral
    Color(0xFFFD79A8), // Pink
    Color(0xFF6C5CE7), // Violet
    Color(0xFF00CEC9), // Cyan
    Color(0xFFFDCB6E), // Warm Amber
    Color(0xFFE84393), // Magenta
    Color(0xFF2D3436)  // Slate
)

private fun getAvatarColor(name: String): Color {
    val hash = kotlin.math.abs(name.hashCode())
    return AVATAR_PALETTE[hash % AVATAR_PALETTE.size]
}

/**
 * Modern Chats Screen with Sender Avatars, Language Chips, and Pinned SOS Alerts.
 * Features:
 * - Sender initial avatar circle with color generated from senderName.
 * - Sender name at top of each bubble ("Kavya", "Node-506").
 * - Language chip ("Hindi", "Tamil", etc.).
 * - Red border for SOS alerts, pinned to the top of the chat list.
 */
@Composable
fun VoiceLogScreen(
    viewModel: MissionControlViewModel,
    modifier: Modifier = Modifier
) {
    val colors = MinimalColorsInstance
    val messageLogs by viewModel.messageLogs.collectAsState()
    val isTtsSpeaking by viewModel.isTtsSpeaking.collectAsState()
    val playingCaption by viewModel.ttsPlayingCaption.collectAsState()

    var filterMode by remember { mutableStateOf("ALL") } // "ALL", "SENT", "RECEIVED"

    // Sort with SOS alerts pinned to the top, then newest-first timestamps
    val filteredLogs = remember(messageLogs, filterMode) {
        val base = when (filterMode) {
            "SENT" -> messageLogs.filter { it.isLocal }
            "RECEIVED" -> messageLogs.filter { !it.isLocal }
            else -> messageLogs
        }
        base.sortedWith(
            compareByDescending<VoiceMessageEntity> { it.isAlert }
                .thenByDescending { it.timestamp }
        )
    }

    val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        val isCompact = maxWidth < 380.dp || maxHeight < 680.dp
        val horizontalPadding = if (isCompact) 14.dp else 20.dp
        val verticalPadding = if (isCompact) 12.dp else 20.dp
        val spacing = if (isCompact) 12.dp else 16.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            // Section 1: Screen Header (Chats)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Chats",
                        fontSize = if (isCompact) 24.sp else 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${messageLogs.size} conversations & voice logs",
                        fontSize = if (isCompact) 12.sp else 13.sp,
                        color = colors.textSecondary
                    )
                }

                if (messageLogs.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { viewModel.clearLogs() },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("clear_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear all",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear", fontSize = 13.sp, color = colors.textSecondary)
                    }
                }
            }

            // Filter Segmented Buttons (All, Sent, Received)
            if (messageLogs.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.outline, RoundedCornerShape(12.dp))
                        .padding(4.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        val tabs = listOf(
                            "ALL" to "All (${messageLogs.size})",
                            "SENT" to "Sent (${messageLogs.count { it.isLocal }})",
                            "RECEIVED" to "Received (${messageLogs.count { !it.isLocal }})"
                        )
                        tabs.forEach { (mode, label) ->
                            val isSelected = filterMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) colors.accentContainer else Color.Transparent)
                                    .clickable { filterMode = mode }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) colors.accent else colors.textSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Section 2: Active Audio Playback Banner (if speaking)
            AnimatedVisibility(visible = isTtsSpeaking && playingCaption != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.accentContainer)
                        .border(1.dp, colors.accent, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Playing voice transmission",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.accent
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = playingCaption ?: "",
                                fontSize = 14.sp,
                                color = colors.textPrimary
                            )
                        }
                    }
                }
            }

            // Section 3: Chats List
            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No chats found",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Transmitted & received voice transcripts will appear here",
                            fontSize = 13.sp,
                            color = colors.textSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredLogs, key = { it.id }) { message ->
                        val isPlayingThis = isTtsSpeaking && playingCaption == message.text
                        val avatarColor = getAvatarColor(message.senderCallsign)
                        val initial = message.senderCallsign.trim().take(1).uppercase().ifBlank { "N" }
                        val langNative = SupportedLanguage.fromCode(message.languageCode).nativeName

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (message.isAlert) colors.error.copy(alpha = 0.08f)
                                    else colors.surface
                                )
                                .border(
                                    width = if (message.isAlert) 2.dp else if (isPlayingThis) 1.5.dp else 1.dp,
                                    color = if (message.isAlert) colors.error
                                    else if (isPlayingThis) colors.accent
                                    else colors.outline,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                // Sender Initial Avatar Circle
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(if (message.isAlert) colors.error else avatarColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (message.isAlert) {
                                        Icon(
                                            imageVector = Icons.Default.WarningAmber,
                                            contentDescription = "SOS",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Text(
                                            text = initial,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }

                                // Message Body
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Top Row: Sender Name, Badges & Timestamp
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = if (message.isLocal) "You (${message.senderCallsign})" else message.senderCallsign,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (message.isAlert) colors.error else colors.textPrimary
                                            )

                                            // Sent / Received Indicator
                                            if (message.isLocal) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(colors.accent.copy(alpha = 0.12f))
                                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            imageVector = Icons.Default.ArrowUpward,
                                                            contentDescription = "Sent",
                                                            tint = colors.accent,
                                                            modifier = Modifier.size(10.dp)
                                                        )
                                                        Text(
                                                            text = "SENT",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = colors.accent
                                                        )
                                                    }
                                                }
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color(0xFF00B894).copy(alpha = 0.12f))
                                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            imageVector = Icons.Default.ArrowDownward,
                                                            contentDescription = "Received",
                                                            tint = Color(0xFF00B894),
                                                            modifier = Modifier.size(10.dp)
                                                        )
                                                        Text(
                                                            text = "RECV",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF00B894)
                                                        )
                                                    }
                                                }
                                            }

                                            if (message.isAlert) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(colors.error)
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "PINNED SOS",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                }
                                            }
                                        }

                                        Text(
                                            text = timeFormatter.format(Date(message.timestamp)),
                                            fontSize = 11.sp,
                                            color = colors.textSecondary
                                        )
                                    }

                                    // Language Chip
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(colors.outline.copy(alpha = 0.3f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "🌐 $langNative",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = colors.textSecondary
                                            )
                                        }
                                    }

                                    // Message Text Content
                                    Text(
                                        text = message.text,
                                        fontSize = 14.sp,
                                        color = colors.textPrimary,
                                        lineHeight = 20.sp
                                    )

                                    // Audio Play Action Button
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isPlayingThis) colors.accentContainer else colors.background)
                                                .border(
                                                    1.dp,
                                                    if (isPlayingThis) colors.accent else colors.outline,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .clickable {
                                                    viewModel.playVoiceMessage(message)
                                                }
                                                .padding(horizontal = 10.dp, vertical = 5.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isPlayingThis) Icons.Default.VolumeUp else Icons.Default.PlayArrow,
                                                    contentDescription = "Play voice",
                                                    tint = if (isPlayingThis) colors.accent else colors.textPrimary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = if (isPlayingThis) "Playing" else "Play Voice",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = if (isPlayingThis) colors.accent else colors.textPrimary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
