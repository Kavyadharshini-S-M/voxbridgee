package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AlertPriority
import com.example.model.ConnectionStatus
import com.example.model.SupportedLanguage
import com.example.ui.components.LanguageSelectionSheet
import com.example.ui.components.PttButtonWithRings
import com.example.ui.components.TacticalTransceiverHud
import com.example.ui.theme.MinimalColorsInstance
import com.example.viewmodel.MissionControlViewModel

/**
 * Visual Action Card Model prioritizing high-contrast color, prominent icon, and 1-tap instant dispatch.
 */
data class VisualActionCard(
    val title: String,
    val subTitle: String,
    val isAlert: Boolean,
    val priority: AlertPriority,
    val icon: ImageVector,
    val accentColor: Color
)

/**
 * Inclusive Mission Control / Walkie Talkie Screen.
 * - Minimum touch target size: 120dp for primary actions (Hero PTT), 64dp for secondary buttons.
 * - Removed technical jargon: "Ready to Talk", "My Radio", "Visual Action Cards".
 * - Giant Push-To-Talk occupying ~50% screen height with pulsing ripple animations.
 * - 4 High-contrast Visual Action Cards: Doctor (Red), Road Blocked (Amber), Need Water (Blue), Position Safe (Green).
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
    val connectedPeer by viewModel.connectedPeer.collectAsState()
    val connectedPeers by viewModel.connectedPeers.collectAsState()
    val discoveredPeers by viewModel.discoveredPeers.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val isTtsSpeaking by viewModel.isTtsSpeaking.collectAsState()

    var showLanguageSheet by remember { mutableStateOf(false) }
    var textMessageInput by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    val allConnected = remember(connectedPeer, connectedPeers) {
        if (connectedPeers.isNotEmpty()) connectedPeers else listOfNotNull(connectedPeer)
    }
    val isConnected = connectionStatus == ConnectionStatus.CONNECTED && allConnected.isNotEmpty()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        val isCompactHeight = maxHeight < 680.dp
        val isCompactWidth = maxWidth < 360.dp
        val horizontalPadding = if (isCompactWidth) 14.dp else 18.dp
        val verticalPadding = if (isCompactHeight) 12.dp else 16.dp
        val sectionSpacing = if (isCompactHeight) 14.dp else 18.dp
        
        // Giant circular PTT button sizing (~45-50% screen height on typical viewports, >=120dp touch target)
        val pttDiameter = if (isCompactHeight) {
            150.dp
        } else {
            (maxHeight * 0.32f).coerceIn(180.dp, 240.dp)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(sectionSpacing)
        ) {
            // Section 1: Header (VOXBRIDGE · My Radio · Ready to Talk)
            val myRadio = com.example.ui.localization.AppLocalization.getMyRadio(uiState.selectedLanguage)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "VOXBRIDGE",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = if (uiState.selectedLanguage == SupportedLanguage.ENGLISH) "My Radio" else "${myRadio.nativeText} (${myRadio.englishLabel})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.accent
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    val statusText = when {
                        isConnected && allConnected.size == 1 -> "🟢 Connected to ${allConnected[0].name} (🔋${allConnected[0].batteryPercent}%)"
                        isConnected && allConnected.size > 1 -> "🟢 ${allConnected.size} Connected (${allConnected.joinToString(", ") { "${it.name} 🔋${it.batteryPercent}%" }})"
                        discoveredPeers.isNotEmpty() -> "🟡 ${discoveredPeers.size} available (Tap to connect)"
                        else -> "🟡 Ready to connect (Tap to pair)"
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isConnected) Color(0xFF00B894).copy(alpha = 0.15f)
                                    else Color(0xFFFDCB6E).copy(alpha = 0.20f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isConnected) Color(0xFF00B894).copy(alpha = 0.5f)
                                    else Color(0xFFFDCB6E).copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onNavigateToPairing() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isConnected) Color(0xFF00B894) else Color(0xFFE17055))
                                )
                                Text(
                                    text = statusText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isConnected) Color(0xFF00B894) else colors.textPrimary,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (isConnected) {
                            IconButton(
                                onClick = { viewModel.disconnectPeer() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LinkOff,
                                    contentDescription = "Disconnect peer",
                                    tint = colors.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Language Selector Pill (>=64dp width, 40dp height)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surface)
                        .border(1.5.dp, colors.outline, RoundedCornerShape(12.dp))
                        .clickable { showLanguageSheet = true }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .testTag("lang_selector_pill"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = uiState.selectedLanguage.nativeName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Select language",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Section 2: Mode Selector (Walkie Talkie vs Hands-Free Phone Mode)
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

                    val pttLabel = com.example.ui.localization.AppLocalization.getPttWalkieTalkie(uiState.selectedLanguage)
                    val phoneLabel = com.example.ui.localization.AppLocalization.getPhoneMode(uiState.selectedLanguage)

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
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (uiState.selectedLanguage == SupportedLanguage.ENGLISH) "Walkie Talkie" else "${pttLabel.nativeText} (${pttLabel.englishLabel})",
                                fontSize = 12.sp,
                                fontWeight = if (isPtt) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isPtt) colors.accent else colors.textSecondary,
                                maxLines = 1
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
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (uiState.selectedLanguage == SupportedLanguage.ENGLISH) "Phone Mode" else "${phoneLabel.nativeText} (${phoneLabel.englishLabel})",
                                fontSize = 12.sp,
                                fontWeight = if (!isPtt) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (!isPtt) colors.accent else colors.textSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Section 3: Giant Hero Push-To-Talk Button (~50% focal height, >=120dp target)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = if (isCompactHeight) 6.dp else 12.dp),
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

            // Section 5: Visual Action Cards (4 High-Contrast Instant Dispatch Cards)
            val currentLang = uiState.selectedLanguage
            val doc = com.example.ui.localization.AppLocalization.getActionDoctor(currentLang)
            val road = com.example.ui.localization.AppLocalization.getActionRoadBlocked(currentLang)
            val water = com.example.ui.localization.AppLocalization.getActionNeedWater(currentLang)
            val safe = com.example.ui.localization.AppLocalization.getActionSafe(currentLang)

            val visualActionCards = remember(currentLang) {
                listOf(
                    VisualActionCard(
                        title = doc.nativeText,
                        subTitle = doc.englishLabel,
                        isAlert = true,
                        priority = AlertPriority.CRITICAL_DISTRESS,
                        icon = Icons.Default.MedicalServices,
                        accentColor = Color(0xFFE53935) // Red
                    ),
                    VisualActionCard(
                        title = road.nativeText,
                        subTitle = road.englishLabel,
                        isAlert = true,
                        priority = AlertPriority.URGENT,
                        icon = Icons.Default.Block,
                        accentColor = Color(0xFFFB8C00) // Amber
                    ),
                    VisualActionCard(
                        title = water.nativeText,
                        subTitle = water.englishLabel,
                        isAlert = false,
                        priority = AlertPriority.ROUTINE,
                        icon = Icons.Default.WaterDrop,
                        accentColor = Color(0xFF0288D1) // Blue
                    ),
                    VisualActionCard(
                        title = safe.nativeText,
                        subTitle = safe.englishLabel,
                        isAlert = false,
                        priority = AlertPriority.ROUTINE,
                        icon = Icons.Default.Check,
                        accentColor = Color(0xFF43A047) // Green
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
                            text = "VISUAL ACTION CARDS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textSecondary,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Single tap to send",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.accent
                        )
                    }

                    // 2x2 High-Contrast Action Matrix (>=64dp touch target per card)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        visualActionCards.chunked(2).forEach { rowActions ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowActions.forEach { action ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 68.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(action.accentColor.copy(alpha = 0.12f))
                                            .border(
                                                width = 2.dp,
                                                color = action.accentColor.copy(alpha = 0.70f),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                viewModel.sendTacticalQuickAction(
                                                    actionTitle = "${action.title} (${action.subTitle})",
                                                    isAlert = action.isAlert,
                                                    priority = action.priority
                                                )
                                            }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(action.accentColor.copy(alpha = 0.22f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = action.icon,
                                                    contentDescription = action.title,
                                                    tint = action.accentColor,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                            Column {
                                                Text(
                                                    text = action.title,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.textPrimary,
                                                    maxLines = 1
                                                )
                                                if (currentLang != SupportedLanguage.ENGLISH) {
                                                    Text(
                                                        text = action.subTitle,
                                                        fontSize = 11.sp,
                                                        color = colors.textSecondary,
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
                }
            }

            // Section 6: Text Message Input
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
                                .size(48.dp)
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
                viewModel.setSelectedLanguage(lang)
                showLanguageSheet = false
            },
            onDismiss = { showLanguageSheet = false },
            onPreviewAudio = { lang -> viewModel.testLanguageVoice(lang) }
        )
    }
}
