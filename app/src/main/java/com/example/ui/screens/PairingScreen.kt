package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PreferenceManager
import com.example.model.ConnectionStatus
import com.example.model.TransportProtocol
import com.example.ui.components.QrPairingDialog
import com.example.ui.theme.MinimalColorsInstance
import com.example.viewmodel.MissionControlViewModel

/**
 * Minimal Mesh Link Screen (Linear / Things 3 / Notion aesthetic).
 * Single accent color #6C5CE7, 20dp horizontal margins, 8dp base unit grid, flat 1dp cards.
 */
@Composable
fun PairingScreen(
    viewModel: MissionControlViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = MinimalColorsInstance
    val activeProtocol by viewModel.activeProtocol.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val discoveredPeers by viewModel.discoveredPeers.collectAsState()
    val connectedPeer by viewModel.connectedPeer.collectAsState()
    val connectedPeers by viewModel.connectedPeers.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()

    val isUltrasonicEmitting by viewModel.isUltrasonicEmitting.collectAsState()
    val isUltrasonicListening by viewModel.isUltrasonicListening.collectAsState()
    val ultrasonicDetectedPayload by viewModel.ultrasonicDetectedPayload.collectAsState()
    val ultrasonicSignalEnergy by viewModel.ultrasonicSignalEnergy.collectAsState()

    var manualIpInput by remember { mutableStateOf("") }
    var isManualIpExpanded by remember { mutableStateOf(false) }
    var detectedPeerNotice by remember { mutableStateOf<String?>(null) }
    var showQrDialog by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val waveScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waveScale"
    )

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        onDispose {
            viewModel.stopAcousticPairingListener()
        }
    }

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        val isCompactWidth = maxWidth < 360.dp
        val horizontalPadding = if (isCompactWidth) 14.dp else 18.dp
        val verticalPadding = if (maxHeight < 680.dp) 14.dp else 20.dp
        val itemSpacing = if (maxHeight < 680.dp) 16.dp else 20.dp

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(itemSpacing)
        ) {
            // Section 1: Screen Header
            item {
                Column {
                    Text(
                        text = "Nearby Connections",
                        fontSize = if (isCompactWidth) 24.sp else 28.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary
                    )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Talk without internet or mobile network",
                    fontSize = 13.sp,
                    color = colors.textSecondary
                )
            }
        }

        // Section 2: My Device Status Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.outline, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(colors.accent)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = telemetry.nodeCallsign,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.textPrimary
                            )
                        }

                        Text(
                            text = "This Device",
                            fontSize = 13.sp,
                            color = colors.textSecondary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "IP: ${telemetry.localIpAddress.substringBefore(":")}",
                            fontSize = 13.sp,
                            color = colors.textSecondary
                        )
                        Text(
                            text = "🔋 ${telemetry.batteryPercent}%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textSecondary
                        )
                    }
                }
            }
        }

        // Section 3: Protocol Segmented Selector
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Connection Type",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.outline, RoundedCornerShape(12.dp))
                        .padding(4.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        data class ProtocolTab(val proto: TransportProtocol, val title: String, val subtitle: String, val icon: ImageVector)
                        val protocols = listOf(
                            ProtocolTab(TransportProtocol.BLUETOOTH, "Bluetooth", "Short Range", Icons.Default.Bluetooth),
                            ProtocolTab(TransportProtocol.WIFI_DIRECT, "Wi-Fi", "Fast & Far", Icons.Default.Wifi),
                            ProtocolTab(TransportProtocol.BLE, "Beacon", "Battery Saver", Icons.Default.CellTower)
                        )

                        protocols.forEach { (proto, title, subtitle, icon) ->
                            val isSelected = activeProtocol == proto
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) colors.accentContainer else Color.Transparent)
                                    .clickable { viewModel.switchProtocol(proto) }
                                    .padding(vertical = 8.dp, horizontal = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = if (isSelected) colors.accent else colors.textSecondary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = title,
                                            fontSize = if (isCompactWidth) 12.sp else 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) colors.accent else colors.textPrimary,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = subtitle,
                                        fontSize = if (isCompactWidth) 10.sp else 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                        color = if (isSelected) colors.accent else colors.textSecondary,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 4: Active Connected Peer / Mesh Network
        val activePeersList = if (connectedPeers.isNotEmpty()) connectedPeers else listOfNotNull(connectedPeer)
        val hasActiveConnections = activePeersList.isNotEmpty() || connectionStatus == ConnectionStatus.CONNECTED
        if (hasActiveConnections) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.accent, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (activePeersList.size > 1) "Connected Radios (${activePeersList.size})" else (activePeersList.firstOrNull()?.name ?: "Connected Friend"),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                            }

                            Button(
                                onClick = { viewModel.disconnectPeer() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.error.copy(alpha = 0.12f),
                                    contentColor = colors.error
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Tap to Disconnect", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        activePeersList.forEach { peer ->
                            val distEst = com.example.location.GpsDistanceUtils.estimateRssiDistance(peer.signalStrengthDbm)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.accentContainer.copy(alpha = 0.35f))
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = peer.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.textPrimary
                                        )
                                        Text(
                                            text = "${peer.protocol.name} · $distEst away",
                                            fontSize = 12.sp,
                                            color = colors.textSecondary
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(colors.surface)
                                            .border(1.dp, colors.outline, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "🔋 ${peer.batteryPercent}%",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (peer.batteryPercent < 20) colors.error else colors.accent
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bluetooth System Pairing Prompt Banner
        if (activeProtocol == TransportProtocol.BLUETOOTH) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.accentContainer.copy(alpha = 0.5f))
                        .border(1.dp, colors.accent.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Bluetooth,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Paired Bluetooth Devices",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        }
                        Text(
                            text = "Only paired devices appear below. To connect to a new device, pair it in Android Bluetooth Settings first.",
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {}
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, colors.accent)
                        ) {
                            Text("Open System Bluetooth Settings", fontSize = 12.sp, color = colors.accent)
                        }
                    }
                }
            }
        }

        // Section 5: Scan & Discovered Peers Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (activeProtocol == TransportProtocol.BLUETOOTH) "Paired Devices (${discoveredPeers.size})" else "People Around You (${discoveredPeers.size})",
                    fontSize = if (isCompactWidth) 16.sp else 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    maxLines = 1
                )

                // The single primary CTA button for this screen
                Button(
                    onClick = { viewModel.scanForPeers(activeProtocol) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .wrapContentWidth()
                        .height(38.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Find Devices",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // Section 6: Discovered Peers List
        if (discoveredPeers.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.outline, RoundedCornerShape(16.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No one nearby yet",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap 'Look for Devices' to find nearby phones",
                            fontSize = 13.sp,
                            color = colors.textSecondary
                        )
                    }
                }
            }
        } else {
            items(discoveredPeers) { peer ->
                val distEst = com.example.location.GpsDistanceUtils.estimateRssiDistance(peer.signalStrengthDbm)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.outline, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = peer.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${peer.protocol.name} · ~$distEst away",
                                fontSize = 13.sp,
                                color = colors.textSecondary
                            )
                        }

                        Button(
                            onClick = { viewModel.connectToPeer(peer) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accent,
                                contentColor = Color.White
                            )
                        ) {
                            Text("Connect Now", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

        // Section 7: Sound Wave Tap-to-Connect
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.outline, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sound Wave Tap-to-Connect",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        }

                        if (isUltrasonicEmitting) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(colors.accent)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text("Emitting sound...", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Text(
                        text = "Hold two phones close together. One taps 'Play Sound' and the other taps 'Listen to Connect'. Connects instantly using acoustic sound waves without internet or passwords.",
                        fontSize = 13.sp,
                        color = colors.textSecondary,
                        lineHeight = 18.sp
                    )

                    // Acoustic Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Button 1: Play Sound
                        Button(
                            onClick = {
                                viewModel.emitAcousticPairingSound {
                                    detectedPeerNotice = "Connection sound emitted successfully"
                                }
                            },
                            enabled = !isUltrasonicEmitting,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accent,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(16.dp)
                                    .then(if (isUltrasonicEmitting) Modifier.scale(waveScale) else Modifier)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isUltrasonicEmitting) "Emitting..." else "Play Sound",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Button 2: Listen to Connect
                        OutlinedButton(
                            onClick = {
                                if (isUltrasonicListening) {
                                    viewModel.stopAcousticPairingListener()
                                } else {
                                    viewModel.startAcousticPairingListener { peerName ->
                                        detectedPeerNotice = "Connected to $peerName via sound wave!"
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isUltrasonicListening) colors.accentContainer else Color.Transparent
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Hearing,
                                contentDescription = null,
                                tint = if (isUltrasonicListening) colors.accent else colors.textPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isUltrasonicListening) "Listening..." else "Listen to Connect",
                                fontSize = 13.sp,
                                color = if (isUltrasonicListening) colors.accent else colors.textPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Live Audio Energy Level when listening
                    if (isUltrasonicListening) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Acoustic Detection Energy", fontSize = 11.sp, color = colors.textSecondary)
                                Text("${(ultrasonicSignalEnergy * 100).toInt()}%", fontSize = 11.sp, color = colors.accent)
                            }
                            LinearProgressIndicator(
                                progress = { ultrasonicSignalEnergy },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = colors.accent,
                                trackColor = colors.outline
                            )
                        }
                    }

                    // Notice if detected
                    if (detectedPeerNotice != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.accentContainer)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = detectedPeerNotice ?: "",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.accent
                            )
                        }
                    }

                    HorizontalDivider(thickness = 1.dp, color = colors.outline)

                    // Zero-Friction QR Code Pairing Fallback Action
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showQrDialog = true }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Having trouble with sound? Scan QR Code instead",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.accent
                        )
                    }
                }
            }
        }

        // Section 8: Manual IP Connection (Collapsible Fallback)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.outline, RoundedCornerShape(16.dp))
                    .clickable { isManualIpExpanded = !isManualIpExpanded }
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Connect to Friend's IP",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Enter friend's IP address directly",
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }

                        Icon(
                            imageVector = if (isManualIpExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = colors.textSecondary
                        )
                    }

                    AnimatedVisibility(visible = isManualIpExpanded) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = manualIpInput,
                                onValueChange = { manualIpInput = it },
                                placeholder = { Text("e.g. 192.168.49.1", color = colors.textSecondary) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.accent,
                                    unfocusedBorderColor = colors.outline,
                                    focusedContainerColor = colors.surface,
                                    unfocusedContainerColor = colors.surface,
                                    focusedTextColor = colors.textPrimary,
                                    unfocusedTextColor = colors.textPrimary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )

                            Button(
                                onClick = {
                                    if (manualIpInput.isNotBlank()) {
                                        viewModel.connectDirectIp(manualIpInput.trim(), 8889)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.accent,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.height(56.dp)
                            ) {
                                Text("Connect Now", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
    }

    if (showQrDialog) {
        QrPairingDialog(
            deviceId = PreferenceManager(context).getInstallUuid(),
            callsign = telemetry.nodeCallsign,
            realIpAddress = telemetry.localIpAddress,
            port = 8889,
            onConnectToPeer = { ip, port ->
                viewModel.connectDirectIp(ip, port)
            },
            onDismiss = { showQrDialog = false }
        )
    }
}
}
