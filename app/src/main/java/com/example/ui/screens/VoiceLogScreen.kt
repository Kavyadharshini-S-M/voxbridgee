package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

/**
 * Minimal Messages Screen (Linear / Things 3 aesthetic).
 * Single accent color #6C5CE7, flat 1dp outlined cards, 20dp horizontal margins.
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

    val filteredLogs = remember(messageLogs, filterMode) {
        when (filterMode) {
            "SENT" -> messageLogs.filter { it.isLocal }
            "RECEIVED" -> messageLogs.filter { !it.isLocal }
            else -> messageLogs
        }
    }

    val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())

    androidx.compose.foundation.layout.BoxWithConstraints(
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
            // Section 1: Screen Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Messages",
                        fontSize = if (isCompact) 24.sp else 28.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${messageLogs.size} archived voice transcripts",
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
                    val tabs = listOf("ALL" to "All (${messageLogs.size})", "SENT" to "Sent (${messageLogs.count { it.isLocal }})", "RECEIVED" to "Received (${messageLogs.count { !it.isLocal }})")
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
                    .padding(16.dp)
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
                            text = "Playing message",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.accent
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = playingCaption ?: "",
                            fontSize = 15.sp,
                            color = colors.textPrimary
                        )
                    }
                }
            }
        }

        // Section 3: Messages List
        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No messages found",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Voice messages recorded on Walkie will appear here",
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredLogs) { message ->
                    val isPlayingThis = isTtsSpeaking && playingCaption == message.text

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surface)
                            .border(
                                width = 1.dp,
                                color = if (isPlayingThis) colors.accent
                                else if (message.isAlert) colors.error
                                else colors.outline,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Top Row: Sent / Received Badge, Sender, Language & Timestamp
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Sent vs Received Badge
                                    if (message.isLocal) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(colors.accent.copy(alpha = 0.15f))
                                                .border(1.dp, colors.accent.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ArrowUpward,
                                                    contentDescription = "Sent",
                                                    tint = colors.accent,
                                                    modifier = Modifier.size(11.dp)
                                                )
                                                Text(
                                                    text = "SENT",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.accent
                                                )
                                            }
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFF00B894).copy(alpha = 0.15f))
                                                .border(1.dp, Color(0xFF00B894).copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ArrowDownward,
                                                    contentDescription = "Received",
                                                    tint = Color(0xFF00B894),
                                                    modifier = Modifier.size(11.dp)
                                                )
                                                Text(
                                                    text = "RECEIVED",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF00B894)
                                                )
                                            }
                                        }
                                    }

                                    if (message.isAlert) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(colors.error.copy(alpha = 0.15f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = message.alertPriority,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.error
                                            )
                                        }
                                    }

                                    Text(
                                        text = message.senderCallsign,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (message.isAlert) colors.error else colors.textPrimary
                                    )

                                    Text(
                                        text = "· ${SupportedLanguage.fromCode(message.languageCode).nativeName}",
                                        fontSize = 12.sp,
                                        color = colors.textSecondary
                                    )
                                }

                                Text(
                                    text = timeFormatter.format(Date(message.timestamp)),
                                    fontSize = 12.sp,
                                    color = colors.textSecondary
                                )
                            }

                            // Message Content
                            Text(
                                text = message.text,
                                fontSize = 15.sp,
                                color = colors.textPrimary
                            )

                            // Action: Audio Playback
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isPlayingThis) colors.accentContainer else colors.background)
                                        .border(1.dp, if (isPlayingThis) colors.accent else colors.outline, RoundedCornerShape(8.dp))
                                        .clickable {
                                            viewModel.playVoiceMessage(message)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
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
                                            text = if (isPlayingThis) "Playing" else "Play",
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
