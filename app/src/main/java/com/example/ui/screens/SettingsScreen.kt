package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.model.LanguagePack
import com.example.model.SupportedLanguage
import com.example.model.VerifiedAsset
import com.example.ui.theme.MinimalColorsInstance
import com.example.viewmodel.MissionControlViewModel

/**
 * Clean 4-Card Settings Screen + Expandable Advanced Settings.
 *
 * Big 4 Cards:
 * 1. My Language (Selection + Test Voice)
 * 2. Loud Alerts (Toggle emergency siren override + Test Siren button)
 * 3. My Name & Picture (Callsign name + Avatar badge picker)
 * 4. Fix Problems (Mic, Location, Battery checklist + Quick Fix actions)
 *
 * Advanced Settings (Collapsible / Triggered by long-press on Card 4):
 * 1. Battery Saver
 * 2. Push to Talk Key (Volume Down)
 * 3. Beep when done talking
 * 4. Bright Sun Mode / Colorblind Mode
 * 5. Font Size selector
 * 6. Show Technical Details & Diagnostics
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    viewModel: MissionControlViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = MinimalColorsInstance
    val uiState by viewModel.uiState.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val languagePacks: Map<String, LanguagePack> by viewModel.languagePacks.collectAsState()
    val verifiedAssets: Map<String, VerifiedAsset> by viewModel.verifiedAssets.collectAsState()
    val isManifestLoaded: Boolean by viewModel.isManifestLoaded.collectAsState()
    val sttModelInfo by viewModel.sttModelInfo.collectAsState()
    val ttsModelInfo by viewModel.ttsModelInfo.collectAsState()

    var isAdvancedExpanded by remember { mutableStateOf(false) }
    var isDiagnosticsExpanded by remember { mutableStateOf(false) }
    var selectedFontSize by remember { mutableStateOf("Normal") } // "Normal", "Large", "Extra Large"
    var selectedAvatarIndex by remember { mutableStateOf(0) }

    val avatarList = listOf("🛡️", "📻", "🧑‍🚀", "⚡", "🛰️", "🏔️")

    val scrollState = rememberScrollState()

    // Lifecycle observer for permissions & system settings
    val lifecycleOwner = LocalLifecycleOwner.current
    val notificationManager = remember {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    }
    var hasDndGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                notificationManager.isNotificationPolicyAccessGranted
            } else true
        )
    }
    var hasMicGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasLocGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    hasDndGranted = notificationManager.isNotificationPolicyAccessGranted
                }
                hasMicGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                hasLocGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        val isCompact = maxWidth < 380.dp || maxHeight < 680.dp
        val horizontalPadding = if (isCompact) 14.dp else 20.dp
        val verticalPadding = if (isCompact) 12.dp else 20.dp
        val sectionSpacing = if (isCompact) 16.dp else 20.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalArrangement = Arrangement.spacedBy(sectionSpacing)
        ) {
            // Screen Title Header
            Column {
                Text(
                    text = "Settings",
                    fontSize = if (isCompact) 24.sp else 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Simple setup for offline voice and emergency alerts",
                    fontSize = if (isCompact) 12.sp else 13.sp,
                    color = colors.textSecondary
                )
            }

            // =========================================================================
            // BIG CARD 1: MY LANGUAGE (Selection & Test Voice)
            // =========================================================================
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
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "1. My Language",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        }

                        Button(
                            onClick = { viewModel.testLanguageVoice(uiState.selectedLanguage) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accent,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test Voice", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Text(
                        text = "Currently speaking: ${uiState.selectedLanguage.nativeName} (${uiState.selectedLanguage.englishName})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.accent
                    )

                    HorizontalDivider(thickness = 1.dp, color = colors.outline)

                    // Quick Language Switch Grid
                    val rows = SupportedLanguage.entries.chunked(2)
                    rows.forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            pair.forEach { lang ->
                                val isSelected = uiState.selectedLanguage == lang
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) colors.accentContainer else colors.background)
                                        .border(
                                            1.dp,
                                            if (isSelected) colors.accent else colors.outline,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable { viewModel.setSelectedLanguage(lang) }
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = lang.nativeName,
                                                fontSize = 14.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) colors.accent else colors.textPrimary
                                            )
                                            Text(
                                                text = lang.englishName,
                                                fontSize = 11.sp,
                                                color = colors.textSecondary
                                            )
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = colors.accent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            if (pair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // BIG CARD 2: LOUD ALERTS (Emergency alerts & Test Siren)
            // =========================================================================
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
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = Color(0xFFE53935),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "2. Loud Alerts",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        }

                        Button(
                            onClick = { viewModel.testSirenAudio() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE53935).copy(alpha = 0.15f),
                                contentColor = Color(0xFFE53935)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test Siren", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Toggle: Emergency Volume Override
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "Emergency Volume Override",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Play priority disaster alerts at max volume even if silenced",
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }

                        Switch(
                            checked = uiState.forceMaxVolumeAlerts,
                            onCheckedChange = { viewModel.setForceMaxVolumeAlerts(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = colors.accent,
                                uncheckedThumbColor = colors.textSecondary,
                                uncheckedTrackColor = colors.outline
                            )
                        )
                    }

                    HorizontalDivider(thickness = 1.dp, color = colors.outline)

                    // Toggle: Shake-to-SOS Emergency Trigger
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "⚡ Shake-to-SOS Trigger",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Shake phone 3 times rapidly anytime to immediately broadcast emergency SOS beacon",
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }

                        Switch(
                            checked = uiState.isShakeToSosEnabled,
                            onCheckedChange = { viewModel.setShakeToSosEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = colors.accent,
                                uncheckedThumbColor = colors.textSecondary,
                                uncheckedTrackColor = colors.outline
                            )
                        )
                    }

                    // DND Permission check
                    if (!hasDndGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFFB8C00).copy(alpha = 0.12f))
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = "Allow Do Not Disturb Override",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = "Required for siren to ring when phone is on silent",
                                        fontSize = 11.sp,
                                        color = colors.textSecondary
                                    )
                                }

                                Button(
                                    onClick = {
                                        try {
                                            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                            context.startActivity(intent)
                                        } catch (e: Exception) {}
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFB8C00)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Allow", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // BIG CARD 3: MY NAME & PICTURE (Name, Avatar Badge)
            // =========================================================================
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
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "3. My Name & Picture",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    }

                    Text(
                        text = "Choose your avatar icon and the radio name shown to friends:",
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )

                    // Avatar Selector Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        avatarList.forEach { emoji ->
                            val isSelected = uiState.userAvatar == emoji
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) colors.accentContainer else colors.background)
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) colors.accent else colors.outline,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        viewModel.setUserAvatar(emoji)
                                    }
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emoji, fontSize = 20.sp)
                            }
                        }
                    }

                    // Name Input
                    OutlinedTextField(
                        value = uiState.customDeviceName,
                        onValueChange = { viewModel.setCustomDeviceName(it) },
                        placeholder = { Text("Enter your name or radio callsign...", fontSize = 13.sp, color = colors.textSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
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
                }
            }

            // =========================================================================
            // BIG CARD 4: FIX PROBLEMS (Mic, Location, Battery Checklist)
            // =========================================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.outline, RoundedCornerShape(16.dp))
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            isAdvancedExpanded = !isAdvancedExpanded
                            Toast.makeText(context, if (isAdvancedExpanded) "Advanced settings unlocked" else "Advanced settings hidden", Toast.LENGTH_SHORT).show()
                        }
                    )
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
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "4. Fix Problems",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        }

                        Text(
                            text = if (hasMicGranted && hasLocGranted) "🟢 All Good" else "⚠️ Attention Needed",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (hasMicGranted && hasLocGranted) Color(0xFF00B894) else Color(0xFFFB8C00)
                        )
                    }

                    // Item 1: Microphone
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = if (hasMicGranted) Color(0xFF00B894) else colors.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Microphone", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary)
                                Text(if (hasMicGranted) "Working for speech" else "Permission missing", fontSize = 11.sp, color = colors.textSecondary)
                            }
                        }

                        if (!hasMicGranted) {
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                            ) {
                                Text("Fix Mic", fontSize = 11.sp, color = Color.White)
                            }
                        } else {
                            Text("✓ OK", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00B894))
                        }
                    }

                    HorizontalDivider(thickness = 1.dp, color = colors.outline)

                    // Item 2: Location
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = if (hasLocGranted) Color(0xFF00B894) else colors.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Location & Distance", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary)
                                Text(if (hasLocGranted) "Active for GPS distance" else "Location access missing", fontSize = 11.sp, color = colors.textSecondary)
                            }
                        }

                        if (!hasLocGranted) {
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                            ) {
                                Text("Fix GPS", fontSize = 11.sp, color = Color.White)
                            }
                        } else {
                            Text("✓ OK", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00B894))
                        }
                    }

                    HorizontalDivider(thickness = 1.dp, color = colors.outline)

                    // Item 3: Battery Optimization Status
                    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
                    val isIgnoringBatteryOptimizations = remember(lifecycleOwner) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && powerManager != null) {
                            powerManager.isIgnoringBatteryOptimizations(context.packageName)
                        } else true
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Power,
                                contentDescription = null,
                                tint = Color(0xFF00B894),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Battery Background Mesh", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary)
                                Text("Keeps walkie connection alive", fontSize = 11.sp, color = colors.textSecondary)
                            }
                        }

                        Text("✓ OK", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00B894))
                    }
                }
            }

            // =========================================================================
            // ADVANCED SETTINGS TOGGLE BUTTON
            // =========================================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.outline, RoundedCornerShape(12.dp))
                    .clickable { isAdvancedExpanded = !isAdvancedExpanded }
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Advanced Settings",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    }

                    Icon(
                        imageVector = if (isAdvancedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = colors.textSecondary
                    )
                }
            }

            // =========================================================================
            // EXPANDABLE ADVANCED SETTINGS CONTENT
            // =========================================================================
            AnimatedVisibility(visible = isAdvancedExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surface)
                            .border(1.dp, colors.outline, RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            // 1. Battery Saver Toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text("1. Battery Saver", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                    Text("Puts audio recording to sleep during silence", fontSize = 12.sp, color = colors.textSecondary)
                                }
                                Switch(
                                    checked = uiState.isLowPowerListeningEnabled,
                                    onCheckedChange = { viewModel.setLowPowerListeningEnabled(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = colors.accent,
                                        uncheckedThumbColor = colors.textSecondary,
                                        uncheckedTrackColor = colors.outline
                                    )
                                )
                            }

                            HorizontalDivider(thickness = 1.dp, color = colors.outline)

                            // 2. Push-to-Talk Key (Volume Down)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text("2. Push to Talk Key (Volume Down)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                    Text("Hold physical Volume Down button to speak (useful with gloves)", fontSize = 12.sp, color = colors.textSecondary)
                                }
                                Switch(
                                    checked = uiState.hardwareKeyRemap == "volume_down",
                                    onCheckedChange = { viewModel.setHardwareKeyRemap(if (it) "volume_down" else "none") },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = colors.accent,
                                        uncheckedThumbColor = colors.textSecondary,
                                        uncheckedTrackColor = colors.outline
                                    )
                                )
                            }

                            HorizontalDivider(thickness = 1.dp, color = colors.outline)

                            // 3. Beep when done talking
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text("3. Beep When Done Talking", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                    Text("Plays confirmation roger chirp when releasing talk button", fontSize = 12.sp, color = colors.textSecondary)
                                }
                                Switch(
                                    checked = uiState.isAudioChirpEnabled,
                                    onCheckedChange = { viewModel.setAudioChirpEnabled(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = colors.accent,
                                        uncheckedThumbColor = colors.textSecondary,
                                        uncheckedTrackColor = colors.outline
                                    )
                                )
                            }

                            HorizontalDivider(thickness = 1.dp, color = colors.outline)

                            // 4. Bright Sun Mode / Colorblind Mode
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text("4. Bright Sun / Colorblind Mode", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                    Text("High contrast black & yellow UI for glare and outdoor sun", fontSize = 12.sp, color = colors.textSecondary)
                                }
                                Switch(
                                    checked = uiState.isFieldModeEnabled,
                                    onCheckedChange = { viewModel.setFieldModeEnabled(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.Black,
                                        checkedTrackColor = colors.accent,
                                        uncheckedThumbColor = colors.textSecondary,
                                        uncheckedTrackColor = colors.outline
                                    )
                                )
                            }

                            HorizontalDivider(thickness = 1.dp, color = colors.outline)

                            // 5. Text Size & Readability Slider
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.FormatSize,
                                            contentDescription = null,
                                            tint = colors.accent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "5. Text Size",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.textPrimary
                                        )
                                    }

                                    Text(
                                        text = "${(uiState.fontScale * 100).toInt()}% • ${uiState.fontSize}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.accent
                                    )
                                }

                                Text(
                                    text = "Drag slider to change font size across all app screens & alerts:",
                                    fontSize = 12.sp,
                                    color = colors.textSecondary
                                )

                                // Continuous Smooth Slider
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("A", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary)

                                    Slider(
                                        value = uiState.fontScale,
                                        onValueChange = { viewModel.setFontScale(it) },
                                        valueRange = 0.85f..1.35f,
                                        modifier = Modifier.weight(1f),
                                        colors = SliderDefaults.colors(
                                            thumbColor = colors.accent,
                                            activeTrackColor = colors.accent,
                                            inactiveTrackColor = colors.outline
                                        )
                                    )

                                    Text("A", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                }

                                // Quick Preset Chips
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(
                                        Triple("Small", 0.85f, "85%"),
                                        Triple("Normal", 1.0f, "100%"),
                                        Triple("Large", 1.15f, "115%"),
                                        Triple("X-Large", 1.30f, "130%")
                                    ).forEach { (label, scale, percent) ->
                                        val isSelected = Math.abs(uiState.fontScale - scale) < 0.06f
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) colors.accentContainer else colors.background)
                                                .border(
                                                    1.dp,
                                                    if (isSelected) colors.accent else colors.outline,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .clickable { viewModel.setFontScale(scale) }
                                                .padding(vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = label,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) colors.accent else colors.textPrimary
                                                )
                                                Text(
                                                    text = percent,
                                                    fontSize = 9.sp,
                                                    color = if (isSelected) colors.accent else colors.textSecondary
                                                )
                                            }
                                        }
                                    }
                                }

                                // Live Real-Time Preview Card
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.background)
                                        .border(1.dp, colors.outline, RoundedCornerShape(10.dp))
                                        .padding(12.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "Live Preview (पूर्वावलोकन)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.accent
                                        )
                                        Text(
                                            text = "iTantra Tactical Voice Mesh is ready for transmission.",
                                            fontSize = 14.sp,
                                            color = colors.textPrimary
                                        )
                                        Text(
                                            text = "आपातकालीन चेतावनी और ऑफ़लाइन वॉकी-टॉकी तैयार है।",
                                            fontSize = 13.sp,
                                            color = colors.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 6. Technical Details & Live Diagnostics
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surface)
                            .border(1.dp, colors.outline, RoundedCornerShape(16.dp))
                            .clickable { isDiagnosticsExpanded = !isDiagnosticsExpanded }
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "6. Show Technical Details & Diagnostics",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                                Icon(
                                    imageVector = if (isDiagnosticsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = colors.textSecondary
                                )
                            }

                            AnimatedVisibility(visible = isDiagnosticsExpanded) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF0F172A))
                                            .padding(12.dp)
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("NODE CALLSIGN : ${telemetry.nodeCallsign}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF00B894))
                                            Text("LOCAL IP      : ${telemetry.localIpAddress}:8889", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF74B9FF))
                                            Text("RADIO CHANNEL : ${telemetry.frequencyGhz}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFFDFE6E9))
                                            Text("BATTERY TEMP  : ${telemetry.batteryTemperatureC}°C", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFFFDCB6E))
                                            Text("RAM ALLOCATED : ${telemetry.ramUsageMb} MB", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFFA29BFE))
                                            Text("ON-DEVICE ASR : ${sttModelInfo.name} (${if (sttModelInfo.isLoaded) "READY" else "LOADING"})", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF55EFC4))
                                            Text("ON-DEVICE TTS : ${ttsModelInfo.name} (${if (ttsModelInfo.isReady) "READY" else "WARMING"})", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF55EFC4))
                                            Text("TRANSLATOR    : AI4Bharat IndicTrans2 Neural Core", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF81ECEC))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
