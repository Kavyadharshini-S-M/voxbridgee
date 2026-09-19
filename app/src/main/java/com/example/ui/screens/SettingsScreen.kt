package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.model.LanguagePack
import com.example.model.SupportedLanguage
import com.example.model.VerifiedAsset
import com.example.ui.theme.MinimalColorsInstance
import com.example.viewmodel.MissionControlViewModel

/**
 * Minimal Settings & On-Device Neural Model Hub (Linear / Notion / Things 3 aesthetic).
 * Single accent color #6C5CE7, flat 1dp cards, clean segmented theme controls.
 */
@Composable
fun SettingsScreen(
    viewModel: MissionControlViewModel,
    modifier: Modifier = Modifier
) {
    val colors = MinimalColorsInstance
    val uiState by viewModel.uiState.collectAsState()
    val languagePacks: Map<String, LanguagePack> by viewModel.languagePacks.collectAsState()
    val verifiedAssets: Map<String, VerifiedAsset> by viewModel.verifiedAssets.collectAsState()
    val isManifestLoaded: Boolean by viewModel.isManifestLoaded.collectAsState()
    val sttModelInfo by viewModel.sttModelInfo.collectAsState()
    val ttsModelInfo by viewModel.ttsModelInfo.collectAsState()

    val scrollState = rememberScrollState()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        val isCompact = maxWidth < 380.dp || maxHeight < 680.dp
        val horizontalPadding = if (isCompact) 14.dp else 20.dp
        val verticalPadding = if (isCompact) 12.dp else 20.dp
        val sectionSpacing = if (isCompact) 16.dp else 24.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalArrangement = Arrangement.spacedBy(sectionSpacing)
        ) {
            // Section 1: Screen Header
            Column {
                Text(
                    text = "Settings",
                    fontSize = if (isCompact) 24.sp else 28.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Sound preferences, emergency alerts & languages",
                    fontSize = if (isCompact) 12.sp else 13.sp,
                    color = colors.textSecondary
                )
            }

            // Section 2: Appearance & High-Contrast Field Mode
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Appearance & Display",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.outline, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Theme Mode Selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Theme Mode",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Choose system default, light, or dark UI theme",
                                    fontSize = 13.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.background)
                                .border(1.dp, colors.outline, RoundedCornerShape(12.dp))
                                .padding(4.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                val themes = listOf(
                                    Triple("system", "System", Icons.Default.SettingsBrightness),
                                    Triple("light", "Light", Icons.Default.LightMode),
                                    Triple("dark", "Dark", Icons.Default.DarkMode)
                                )

                                themes.forEach { (mode, label, icon) ->
                                    val isSelected = uiState.themeMode == mode && !uiState.isFieldModeEnabled
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) colors.accentContainer else Color.Transparent)
                                            .clickable { viewModel.setThemeMode(mode) }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = if (isSelected) colors.accent else colors.textSecondary,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Text(
                                                text = label,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                                color = if (isSelected) colors.accent else colors.textSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(thickness = 1.dp, color = colors.outline)

                        // Field Mode Switch (Ultra High Contrast Black & Yellow)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Ultra High-Contrast Field Mode",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Monochrome high-visibility black & yellow UI for direct sunlight & severe conditions",
                                    fontSize = 13.sp,
                                    color = colors.textSecondary
                                )
                            }

                            Switch(
                                checked = uiState.isFieldModeEnabled,
                                onCheckedChange = { viewModel.setFieldModeEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = colors.accent,
                                    uncheckedThumbColor = colors.textSecondary,
                                    uncheckedTrackColor = colors.outline
                                ),
                                modifier = Modifier.testTag("field_mode_switch")
                            )
                        }
                    }
                }
            }

            // Section: Language Preference
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Primary Language",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.outline, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Incoming voice messages are automatically translated and spoken in this language.",
                            fontSize = 13.sp,
                            color = colors.textSecondary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${uiState.selectedLanguage.nativeName} (${uiState.selectedLanguage.englishName})",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.accent
                                )
                                Text(
                                    text = "Active voice synthesis engine",
                                    fontSize = 12.sp,
                                    color = colors.textSecondary
                                )
                            }

                            Button(
                                onClick = { viewModel.testTtsAudio() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.accentContainer,
                                    contentColor = colors.accent
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Test Voice", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }

                        HorizontalDivider(thickness = 1.dp, color = colors.outline)

                        // Quick Select Language Grid
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Switch Language:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.textPrimary
                            )

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
                                        }
                                    }
                                    if (pair.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section: Permissions & Emergency DND Access Hub
            val context = LocalContext.current
            val lifecycleOwner = LocalLifecycleOwner.current
            val notificationManager = remember {
                context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            }
            var hasDndGranted by remember {
                mutableStateOf(
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        notificationManager.isNotificationPolicyAccessGranted
                    } else true
                )
            }

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                            hasDndGranted = notificationManager.isNotificationPolicyAccessGranted
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Permissions & Emergency Alerts",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.outline, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        // DND Policy Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Loud Emergency Alerts",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Required to sound priority emergency sirens when the phone is in Do Not Disturb mode.",
                                    fontSize = 13.sp,
                                    color = colors.textSecondary
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            if (hasDndGranted) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.accentContainer)
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text("Active", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                            try {
                                                val intent = android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                                context.startActivity(intent)
                                            } catch (e: Exception) {}
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Grant", fontSize = 12.sp, color = Color.White)
                                }
                            }
                        }

                        HorizontalDivider(thickness = 1.dp, color = colors.outline)

                        // Hardware Permissions Status Overview
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val hasMic = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            val hasLoc = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED

                            Column {
                                Text("Microphone", fontSize = 12.sp, color = colors.textSecondary)
                                Text(if (hasMic) "✓ Granted" else "✕ Missing", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (hasMic) colors.accent else colors.error)
                            }
                            Column {
                                Text("Location Mesh", fontSize = 12.sp, color = colors.textSecondary)
                                Text(if (hasLoc) "✓ Granted" else "✕ Missing", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (hasLoc) colors.accent else colors.error)
                            }
                            Column {
                                Text("DND Policy", fontSize = 12.sp, color = colors.textSecondary)
                                Text(if (hasDndGranted) "✓ Granted" else "✕ Missing", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (hasDndGranted) colors.accent else colors.error)
                            }
                        }
                    }
                }
            }

            // Section: Device Identity
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Device Identity",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.outline, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "This callsign name is broadcasted to mesh peers during discovery.",
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )

                    OutlinedTextField(
                        value = uiState.customDeviceName,
                        onValueChange = { viewModel.setCustomDeviceName(it) },
                        placeholder = { Text("Enter device name...", fontSize = 14.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.outline,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            cursorColor = colors.accent
                        )
                    )
                }
            }

            // Section 3: Audio & Transceiver Preferences
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Sound & Microphone",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.outline, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Switch 1: Max Volume Disaster Override
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Emergency Volume Override",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Plays disaster sirens at full volume even if phone is silenced",
                                    fontSize = 13.sp,
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
                                ),
                                modifier = Modifier.testTag("force_max_vol_switch")
                            )
                        }

                        HorizontalDivider(thickness = 1.dp, color = colors.outline)

                        // Switch 2: Physical Hardware Key Remap
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Physical Key PTT (Volume Down)",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Press and hold physical Volume Down button to speak when wearing gloves",
                                    fontSize = 13.sp,
                                    color = colors.textSecondary
                                )
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

                        // Switch 3: Tactical Audio Chirp (Roger Beep)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Beep when done talking",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Plays a short confirmation chirp when you release the talk button",
                                    fontSize = 13.sp,
                                    color = colors.textSecondary
                                )
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

                        // Switch 4: Low-Power Idle Listening
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Battery Saver",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Puts microphone buffers to sleep during silence to conserve battery",
                                    fontSize = 13.sp,
                                    color = colors.textSecondary
                                )
                            }

                            Switch(
                                checked = uiState.isLowPowerListeningEnabled,
                                onCheckedChange = { viewModel.setLowPowerListeningEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = colors.accent,
                                    uncheckedThumbColor = colors.textSecondary,
                                    uncheckedTrackColor = colors.outline
                                ),
                                modifier = Modifier.testTag("low_power_switch")
                            )
                        }
                    }
                }
            }

            // Section 4: Offline Voice Languages (Clean Layman UI)
            var isDiagnosticsExpanded by remember { mutableStateOf(false) }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    ) {
                        Text(
                            text = "Offline Voice Languages",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Speech recognition and voice playback without internet",
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .wrapContentWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.accentContainer)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isManifestLoaded) "Ready (Offline)" else "Loading...",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.accent,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // Clean Layman-Friendly Language Cards
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SupportedLanguage.entries.forEach { lang ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.surface)
                                .border(1.dp, colors.outline, RoundedCornerShape(16.dp))
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${lang.englishName} (${lang.nativeName})",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(colors.accentContainer)
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = "Ready for Talking",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = colors.accent
                                            )
                                        }
                                        Text(
                                            text = "· 100% Offline",
                                            fontSize = 11.sp,
                                            color = colors.textSecondary
                                        )
                                    }
                                }

                                OutlinedButton(
                                    onClick = { viewModel.testLanguageVoice(lang) },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = colors.textPrimary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Test Voice", fontSize = 12.sp, color = colors.textPrimary)
                                }
                            }
                        }
                    }
                }

                // ADVANCED / DEVELOPER DIAGNOSTICS COLLAPSIBLE SHEET
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.outline, RoundedCornerShape(16.dp))
                        .clickable { isDiagnosticsExpanded = !isDiagnosticsExpanded }
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Developer & Technical Diagnostics",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.textSecondary
                            )

                            Icon(
                                imageVector = if (isDiagnosticsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Expand Diagnostics",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        AnimatedVisibility(visible = isDiagnosticsExpanded) {
                            Column(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                HorizontalDivider(thickness = 1.dp, color = colors.outline)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Pipeline: v2.1.0 · RAM Budget: 400.0 MB (Resident: 1 ASR / 1 TTS)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.accent
                                )
                                Text(
                                    text = "Total Model Storage: 614.96 MB · 2 ASR / 7 TTS Physical",
                                    fontSize = 11.sp,
                                    color = colors.textSecondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Active Speech Model: ${sttModelInfo.name}",
                                    fontSize = 12.sp,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = "Speech Runtime: ${sttModelInfo.runtime} · Latency: ${sttModelInfo.inferenceLatencyMs}ms",
                                    fontSize = 11.sp,
                                    color = colors.textSecondary
                                )
                                Text(
                                    text = "Active Voice Model: ${ttsModelInfo.name}",
                                    fontSize = 12.sp,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = "Voice Runtime: ${ttsModelInfo.runtime} · Rate: ${ttsModelInfo.sampleRateHz}Hz",
                                    fontSize = 11.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Section 5: App Information Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.outline, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "VOXBRIDGE Mesh Transceiver",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Offline multilingual voice transceiver app for field rescue and disaster alert communications. Operates 100% on-device without cloud or cellular infrastructure.",
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
