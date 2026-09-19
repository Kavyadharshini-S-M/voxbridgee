package com.example.ui.components

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.model.SupportedLanguage
import com.example.ui.theme.MinimalColorsInstance

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.systemBarsPadding

@Composable
fun OnboardingDialog(
    initialLanguage: SupportedLanguage,
    initialCallsign: String,
    onComplete: (SupportedLanguage, String) -> Unit
) {
    val context = LocalContext.current
    val colors = MinimalColorsInstance
    var currentStep by remember { mutableIntStateOf(0) }
    var selectedLanguage by remember { mutableStateOf(initialLanguage) }
    var callsign by remember { mutableStateOf(if (initialCallsign.isNotBlank()) initialCallsign else "Node-${(100..999).random()}") }

    // Required System Permissions
    val requiredPermissions = remember {
        val list = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(Manifest.permission.BLUETOOTH_CONNECT)
            list.add(Manifest.permission.BLUETOOTH_SCAN)
            list.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        list.toTypedArray()
    }

    var hasAllPermissions by remember {
        mutableStateOf(
            requiredPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val notificationManager = remember {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    var hasDndAccess by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                notificationManager.isNotificationPolicyAccessGranted
            } else true
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasAllPermissions = requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    // Refresh DND status periodically / when dialog shows
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            hasDndAccess = notificationManager.isNotificationPolicyAccessGranted
        }
    }

    Dialog(
        onDismissRequest = { /* Modal: require completion */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .systemBarsPadding(),
            color = colors.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header Step Indicator
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(colors.accent),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Radio,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "iTantra Setup",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        }

                        Text(
                            text = "Step ${currentStep + 1} of 3",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.accent
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Progress bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (i in 0..2) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        if (i <= currentStep) colors.accent else colors.outline
                                    )
                            )
                        }
                    }
                }

                // Step Content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 12.dp)
                ) {
                    when (currentStep) {
                        0 -> {
                            // Step 1: Welcome & Overview (Scrollable for compact screens)
                            val step0ScrollState = rememberScrollState()
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(step0ScrollState),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(colors.accentContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = colors.accent,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                Text(
                                    text = "Welcome to iTantra",
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Offline multilingual tactical voice mesh for emergency response and field communication.",
                                    fontSize = 15.sp,
                                    color = colors.textSecondary,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 22.sp
                                )

                                Spacer(modifier = Modifier.height(28.dp))

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    FeatureHighlightRow(
                                        icon = Icons.Default.Mic,
                                        title = "100% Offline Neural Speech",
                                        desc = "On-device STT & TTS across 10 Indic languages"
                                    )
                                    FeatureHighlightRow(
                                        icon = Icons.Default.Radio,
                                        title = "Zero-Infrastructure Mesh",
                                        desc = "Direct Wi-Fi, Bluetooth, BLE & Ultrasonic pairing"
                                    )
                                    FeatureHighlightRow(
                                        icon = Icons.Default.DoNotDisturbOn,
                                        title = "Emergency SOS Override",
                                        desc = "Priority life-safety disaster sirens override silence"
                                    )
                                }
                            }
                        }

                        1 -> {
                            // Step 2: Permissions (Mic, Location, Nearby, DND Override)
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                item {
                                    Column {
                                        Text(
                                            text = "App Permissions",
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.textPrimary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "iTantra operates 100% offline. These permissions are strictly required for local hardware transceivers.",
                                            fontSize = 13.sp,
                                            color = colors.textSecondary
                                        )
                                    }
                                }

                                item {
                                    PermissionCard(
                                        icon = Icons.Default.Mic,
                                        title = "Microphone Access",
                                        desc = "Used for live voice walkie-talkie & ultrasonic pairing",
                                        isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
                                        onRequest = { permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) }
                                    )
                                }

                                item {
                                    PermissionCard(
                                        icon = Icons.Default.LocationOn,
                                        title = "Location & Mesh Discovery",
                                        desc = "Required by Android OS for Wi-Fi Direct and BLE beacon scanning",
                                        isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED,
                                        onRequest = {
                                            permissionLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                                )
                                            )
                                        }
                                    )
                                }

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    item {
                                        PermissionCard(
                                            icon = Icons.Default.Bluetooth,
                                            title = "Nearby Devices & Bluetooth",
                                            desc = "Required for un-paired Bluetooth mesh and BLE beacon discovery",
                                            isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                                                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED,
                                            onRequest = {
                                                val perms = mutableListOf(
                                                    Manifest.permission.BLUETOOTH_SCAN,
                                                    Manifest.permission.BLUETOOTH_CONNECT
                                                )
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                    perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
                                                }
                                                permissionLauncher.launch(perms.toTypedArray())
                                            }
                                        )
                                    }
                                }

                                item {
                                    PermissionCard(
                                        icon = Icons.Default.DoNotDisturbOn,
                                        title = "Do Not Disturb (DND) Override",
                                        desc = "Allows high-priority life-safety SOS sirens to sound even if phone is silenced",
                                        isGranted = hasDndAccess,
                                        onRequest = {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                try {
                                                    val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {}
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        2 -> {
                            // Step 3: Callsign & Language Preference
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                item {
                                    Column {
                                        Text(
                                            text = "Identity & Language",
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.textPrimary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Set your tactical callsign and choose your primary spoken language.",
                                            fontSize = 13.sp,
                                            color = colors.textSecondary
                                        )
                                    }
                                }

                                item {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "Device Tactical Callsign",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = colors.textPrimary
                                        )
                                        OutlinedTextField(
                                            value = callsign,
                                            onValueChange = { callsign = it },
                                            placeholder = { Text("e.g. Rescue Alpha 1") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = colors.accent,
                                                unfocusedBorderColor = colors.outline,
                                                focusedContainerColor = colors.surface,
                                                unfocusedContainerColor = colors.surface,
                                                focusedTextColor = colors.textPrimary,
                                                unfocusedTextColor = colors.textPrimary
                                            )
                                        )
                                    }
                                }

                                item {
                                    Text(
                                        text = "Primary Language for this Device",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.textPrimary
                                    )
                                }

                                items(SupportedLanguage.entries) { lang ->
                                    val isSelected = selectedLanguage == lang
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSelected) colors.accentContainer else colors.surface)
                                            .border(
                                                1.dp,
                                                if (isSelected) colors.accent else colors.outline,
                                                RoundedCornerShape(12.dp)
                                            )
                                            .clickable { selectedLanguage = lang }
                                            .padding(14.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = lang.nativeName,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) colors.accent else colors.textPrimary
                                                )
                                                Text(
                                                    text = lang.englishName,
                                                    fontSize = 13.sp,
                                                    color = colors.textSecondary
                                                )
                                            }

                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Selected",
                                                    tint = colors.accent,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Footer Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStep > 0) {
                        OutlinedButton(
                            onClick = { currentStep-- },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Back", color = colors.textPrimary, fontSize = 14.sp)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Button(
                        onClick = {
                            if (currentStep < 2) {
                                if (currentStep == 0) {
                                    // Trigger bulk permissions request if moving past step 0
                                    permissionLauncher.launch(requiredPermissions)
                                }
                                currentStep++
                            } else {
                                onComplete(selectedLanguage, callsign.ifBlank { "Rescue-Node" })
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(
                            text = if (currentStep == 2) "Launch Mission Control ➔" else "Continue ➔",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureHighlightRow(
    icon: ImageVector,
    title: String,
    desc: String
) {
    val colors = MinimalColorsInstance
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, colors.outline, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(colors.accentContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Text(
                text = desc,
                fontSize = 12.sp,
                color = colors.textSecondary
            )
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    desc: String,
    isGranted: Boolean,
    onRequest: () -> Unit
) {
    val colors = MinimalColorsInstance
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(
                1.dp,
                if (isGranted) colors.accent.copy(alpha = 0.4f) else colors.outline,
                RoundedCornerShape(14.dp)
            )
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) colors.accent else colors.textSecondary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = desc,
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isGranted) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.accentContainer)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("Granted", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                }
            } else {
                Button(
                    onClick = onRequest,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Grant", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
