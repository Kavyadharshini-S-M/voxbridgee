package com.example.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
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
import com.example.model.ConnectionStatus
import com.example.model.SupportedLanguage
import com.example.model.TransportProtocol
import com.example.ui.components.LanguageSelectionSheet
import com.example.ui.components.PttButtonWithRings
import com.example.ui.components.TacticalTransceiverHud
import com.example.ui.theme.MinimalColorsInstance
import com.example.viewmodel.MissionControlViewModel

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import com.example.model.AlertPriority

/**
 * Data structure representing pre-configured fast-dispatch tactical commands.
 */
data class TacticalQuickAction(
    val title: String,
    val isAlert: Boolean,
    val priority: AlertPriority,
    val icon: ImageVector,
    val accentColor: Color
)

/**
 * Minimal Walkie-Talkie Screen (Linear / Things 3 / Notion aesthetic).
 * - Single accent color #6C5CE7
 * - Screen horizontal margin 20dp, 8dp base unit grid, 24dp vertical section spacing
 * - One clear focal point (Hero PTT button)
 * - 28sp Medium title, 13sp caption, max 2 weights
 */
@Composable
fun MissionControlScreen(
    viewModel: MissionControlViewModel,
    onNavigateToAlerts: () -> Unit,
    onNavigateToPairing: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MinimalColorsInstance
    val uiState by viewModel.uiState.collectAsState()
    val audioLevel by viewModel.audioLevel.collectAsState()
    val vadStatus by viewModel.vadStatus.collectAsState()
    val speechProbability by viewModel.speechProbability.collectAsState()
    val activeProtocol by viewModel.activeProtocol.collectAsState()
    val connectedPeer by viewModel.connectedPeer.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val isTtsSpeaking by viewModel.isTtsSpeaking.collectAsState()

    var showLanguageSheet by remember { mutableStateOf(false) }
    var textMessageInput by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        val isCompactHeight = maxHeight < 680.dp
        val isCompactWidth = maxWidth < 360.dp
        val horizontalPadding = if (isCompactWidth) 14.dp else 18.dp
        val verticalPadding = if (isCompactHeight) 12.dp else 18.dp
        val sectionSpacing = if (isCompactHeight) 14.dp else 20.dp
        val pttDiameter = if (isCompactHeight) 116.dp else 144.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(sectionSpacing)
        ) {
            // Section 1: Screen Title & Connectivity Meta
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "VOXBRIDGE",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Walkie Talkie",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.accent
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (connectionStatus == ConnectionStatus.CONNECTED) colors.accent
                                else colors.outline
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (connectedPeer != null) {
                            "Mesh connected · ${connectedPeer?.name}"
                        } else {
                            "Mesh standby · ${if (activeProtocol == TransportProtocol.BLUETOOTH) "Bluetooth" else "Wi-Fi Direct"}"
                        },
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )

                    if (connectedPeer != null || connectionStatus == ConnectionStatus.CONNECTED) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "(Disconnect)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.error,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { viewModel.disconnectPeer() }
                        )
                    }
                }
            }

            // Language Selector Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.outline, RoundedCornerShape(12.dp))
                    .clickable { showLanguageSheet = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("lang_selector_pill"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = uiState.selectedLanguage.nativeName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Select language",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Section 2: Segmented Mode Selector (Walkie Talkie vs Phone Mode)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .border(1.dp, colors.outline, RoundedCornerShape(12.dp))
                .padding(4.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                val isPtt = uiState.isPttActive

                // Walkie Talkie Option
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isPtt) colors.accentContainer else Color.Transparent)
                        .clickable { viewModel.togglePttMode(true) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = if (isPtt) colors.accent else colors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Walkie Talkie",
                            fontSize = 13.sp,
                            fontWeight = if (isPtt) FontWeight.Medium else FontWeight.Normal,
                            color = if (isPtt) colors.accent else colors.textSecondary
                        )
                    }
                }

                // Phone Mode Option
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (!isPtt) colors.accentContainer else Color.Transparent)
                        .clickable { viewModel.togglePttMode(false) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = if (!isPtt) colors.accent else colors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Phone Mode",
                            fontSize = 13.sp,
                            fontWeight = if (!isPtt) FontWeight.Medium else FontWeight.Normal,
                            color = if (!isPtt) colors.accent else colors.textSecondary
                        )
                    }
                }
            }
        }

        // Section 3: The Hero Focal Point (Push-To-Talk Button)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = if (isCompactHeight) 8.dp else 16.dp),
            contentAlignment = Alignment.Center
        ) {
            PttButtonWithRings(
                channelState = uiState.channelState,
                isPttMode = uiState.isPttActive,
                onPressed = { viewModel.onPttPressed() },
                onReleased = { viewModel.onPttReleased() },
                buttonDiameter = pttDiameter
            )
        }

        // Section 4: Live Audio & Transcript Card
        TacticalTransceiverHud(
            channelState = uiState.channelState,
            connectedPeer = connectedPeer,
            selectedLanguage = uiState.selectedLanguage,
            currentTranscript = uiState.currentTranscript,
            incomingCaption = uiState.activeIncomingCaption,
            isIncomingAlert = uiState.activeIncomingIsAlert,
            isTtsSpeaking = isTtsSpeaking,
            audioLevel = audioLevel,
            vadStatus = vadStatus,
            speechProbability = speechProbability
        )

        // Section 5: Tactical Quick-Pad (Bypass STT & Instant Mesh Broadcast)
        val tacticalQuickActions = remember {
            listOf(
                TacticalQuickAction(
                    title = "SOS Medical",
                    isAlert = true,
                    priority = AlertPriority.CRITICAL_DISTRESS,
                    icon = Icons.Default.MedicalServices,
                    accentColor = Color(0xFFE53935)
                ),
                TacticalQuickAction(
                    title = "Route Blocked",
                    isAlert = true,
                    priority = AlertPriority.URGENT,
                    icon = Icons.Default.Block,
                    accentColor = Color(0xFFFB8C00)
                ),
                TacticalQuickAction(
                    title = "Need Water",
                    isAlert = false,
                    priority = AlertPriority.ROUTINE,
                    icon = Icons.Default.WaterDrop,
                    accentColor = Color(0xFF0288D1)
                ),
                TacticalQuickAction(
                    title = "Position Secure",
                    isAlert = false,
                    priority = AlertPriority.ROUTINE,
                    icon = Icons.Default.Shield,
                    accentColor = Color(0xFF43A047)
                )
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface)
                .border(1.dp, colors.outline, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TACTICAL QUICK-PAD",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textSecondary,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Direct UTF-8 Mesh Broadcast",
                        fontSize = 11.sp,
                        color = colors.accent
                    )
                }

                // 2x2 Tactical Action Matrix
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tacticalQuickActions.chunked(2).forEach { rowActions ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowActions.forEach { action ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(action.accentColor.copy(alpha = 0.08f))
                                        .border(1.dp, action.accentColor.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                                        .clickable {
                                            viewModel.sendTacticalQuickAction(
                                                actionTitle = action.title,
                                                isAlert = action.isAlert,
                                                priority = action.priority
                                            )
                                        }
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(action.accentColor.copy(alpha = 0.16f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = action.icon,
                                                contentDescription = action.title,
                                                tint = action.accentColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Text(
                                            text = action.title,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.textPrimary,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 6: Text Message Transmission
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface)
                .border(1.dp, colors.outline, RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Send Text Message",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = textMessageInput,
                        onValueChange = { textMessageInput = it },
                        placeholder = {
                            Text(
                                text = "Type message in ${uiState.selectedLanguage.englishName}...",
                                fontSize = 13.sp,
                                color = colors.textSecondary
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("walkie_text_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (textMessageInput.isNotBlank()) {
                                    viewModel.sendTextMessage(textMessageInput)
                                    textMessageInput = ""
                                }
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.outline,
                            focusedContainerColor = colors.background,
                            unfocusedContainerColor = colors.background,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            cursorColor = colors.accent
                        )
                    )

                    IconButton(
                        onClick = {
                            if (textMessageInput.isNotBlank()) {
                                viewModel.sendTextMessage(textMessageInput)
                                textMessageInput = ""
                            }
                        },
                        enabled = textMessageInput.isNotBlank(),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (textMessageInput.isNotBlank()) colors.accent else colors.outline.copy(alpha = 0.3f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .testTag("walkie_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send text message",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showLanguageSheet) {
        LanguageSelectionSheet(
            selectedLanguage = uiState.selectedLanguage,
            onLanguageSelected = { lang ->
                viewModel.selectOrDownloadLanguage(lang)
            },
            onDismiss = { showLanguageSheet = false },
            onPreviewAudio = { lang -> viewModel.testTtsAudio(lang.sampleAlertPhrase) },
            modelDownloader = viewModel.onDemandModelDownloader
        )
    }
}
