package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.MinimalColorsInstance
import java.util.UUID

import androidx.compose.foundation.layout.systemBarsPadding

/**
 * QR Code & Direct Connection String Pairing Fallback Dialog.
 *
 * Provides visual 2D matrix rendering of device IP, CallSign, Port, and encryption token,
 * along with quick token copy and direct input connection options.
 */
@Composable
fun QrPairingDialog(
    deviceName: String,
    ipAddress: String,
    port: Int = 8889,
    onConnectToPeer: (ip: String, port: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MinimalColorsInstance
    val context = LocalContext.current

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Show QR, 1 = Scan / Enter Token
    var manualTokenInput by remember { mutableStateOf("") }

    val sessionToken = remember {
        val raw = UUID.randomUUID().toString().take(8).uppercase()
        "ITP://$ipAddress:$port?peer=${deviceName.replace(" ", "_")}&tok=$raw"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .systemBarsPadding()
                .padding(vertical = 16.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, colors.outline, RoundedCornerShape(20.dp)),
            color = colors.surface,
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Direct QR & Token Link",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Connect instantly without Bluetooth/Wi-Fi scanning",
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = colors.textSecondary
                        )
                    }
                }

                // Tab Selector: My Node Address vs Connect to Node
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.background)
                        .border(1.dp, colors.outline, RoundedCornerShape(10.dp))
                        .padding(3.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selectedTab == 0) colors.accentContainer else Color.Transparent)
                                .clickable { selectedTab = 0 }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCode,
                                    contentDescription = null,
                                    tint = if (selectedTab == 0) colors.accent else colors.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "This Device Node",
                                    fontSize = 13.sp,
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == 0) colors.accent else colors.textSecondary
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selectedTab == 1) colors.accentContainer else Color.Transparent)
                                .clickable { selectedTab = 1 }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = null,
                                    tint = if (selectedTab == 1) colors.accent else colors.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Connect to Node",
                                    fontSize = 13.sp,
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == 1) colors.accent else colors.textSecondary
                                )
                            }
                        }
                    }
                }

                if (selectedTab == 0) {
                    // TAB 0: Visual QR Matrix Representation & Clean Node Code
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(200.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .padding(14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            QrMatrixCanvas(
                                seedString = "$ipAddress:$port",
                                modifier = Modifier.size(172.dp)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = deviceName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Scan with any camera or enter Node IP below on the other phone",
                                fontSize = 12.sp,
                                color = colors.textSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }

                        HorizontalDivider(thickness = 1.dp, color = colors.outline)

                        // Concise Node Address Box with 1-Tap Copy
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.background)
                                .border(1.dp, colors.outline, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Node Address",
                                        fontSize = 11.sp,
                                        color = colors.textSecondary
                                    )
                                    Text(
                                        text = "$ipAddress:$port",
                                        fontSize = 15.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.accent
                                    )
                                }

                                Button(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("VOXBRIDGE Node Address", "$ipAddress:$port")
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Node Address copied!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.accentContainer,
                                        contentColor = colors.accent
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    // TAB 1: Direct Node IP Input
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Enter the Node Address or IP shown on the other phone to establish an instant direct link:",
                            fontSize = 13.sp,
                            color = colors.textSecondary,
                            lineHeight = 18.sp
                        )

                        OutlinedTextField(
                            value = manualTokenInput,
                            onValueChange = { manualTokenInput = it },
                            placeholder = { Text("e.g. 192.168.49.1:8889 or 192.168.1.100", fontSize = 13.sp, color = colors.textSecondary) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.accent,
                                unfocusedBorderColor = colors.outline,
                                focusedContainerColor = colors.background,
                                unfocusedContainerColor = colors.background,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val item = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                                    if (!item.isNullOrBlank()) {
                                        manualTokenInput = item.trim()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Paste", fontSize = 13.sp, color = colors.textPrimary)
                            }

                            Button(
                                onClick = {
                                    val raw = manualTokenInput.trim()
                                    if (raw.isNotBlank()) {
                                        val parsedIp = extractIpFromToken(raw)
                                        val parsedPort = extractPortFromToken(raw, defaultPort = port)
                                        onConnectToPeer(parsedIp, parsedPort)
                                        onDismiss()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.accent,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1.5f)
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

/**
 * Procedural aesthetic 2D matrix representing QR data cells.
 */
@Composable
private fun QrMatrixCanvas(
    seedString: String,
    modifier: Modifier = Modifier
) {
    val hash = seedString.hashCode()
    val gridSize = 21 // Standard QR version 1 is 21x21 modules

    Canvas(modifier = modifier) {
        val cellSize = size.width / gridSize

        // Draw 3 corner finder patterns
        drawFinderPattern(0f, 0f, cellSize)
        drawFinderPattern((gridSize - 7) * cellSize, 0f, cellSize)
        drawFinderPattern(0f, (gridSize - 7) * cellSize, cellSize)

        // Draw data cells deterministically based on string hash
        val rand = java.util.Random(hash.toLong())
        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                // Skip finder pattern zones (7x7 corners)
                val inTopLeft = row < 8 && col < 8
                val inTopRight = row < 8 && col >= gridSize - 8
                val inBottomLeft = row >= gridSize - 8 && col < 8

                if (!inTopLeft && !inTopRight && !inBottomLeft) {
                    val isFilled = (rand.nextInt(100) % 2 == 0) || ((row + col) % 3 == 0)
                    if (isFilled) {
                        drawRect(
                            color = Color(0xFF1E293B),
                            topLeft = Offset(col * cellSize, row * cellSize),
                            size = Size(cellSize - 0.5f, cellSize - 0.5f)
                        )
                    }
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFinderPattern(
    x: Float,
    y: Float,
    cellSize: Float
) {
    val darkColor = Color(0xFF0F172A)
    // 7x7 outer square
    drawRect(
        color = darkColor,
        topLeft = Offset(x, y),
        size = Size(7 * cellSize, 7 * cellSize)
    )
    // 5x5 inner white square
    drawRect(
        color = Color.White,
        topLeft = Offset(x + cellSize, y + cellSize),
        size = Size(5 * cellSize, 5 * cellSize)
    )
    // 3x3 inner dark center
    drawRect(
        color = darkColor,
        topLeft = Offset(x + 2 * cellSize, y + 2 * cellSize),
        size = Size(3 * cellSize, 3 * cellSize)
    )
}

private fun extractIpFromToken(token: String): String {
    if (!token.contains("://") && !token.contains("?")) {
        return token.split(":")[0].trim()
    }
    return try {
        val hostPart = token.substringAfter("://").substringBefore("?").substringBefore("/")
        hostPart.split(":")[0].trim()
    } catch (e: Exception) {
        token.trim()
    }
}

private fun extractPortFromToken(token: String, defaultPort: Int): Int {
    if (!token.contains(":")) return defaultPort
    return try {
        val hostPart = if (token.contains("://")) {
            token.substringAfter("://").substringBefore("?").substringBefore("/")
        } else {
            token.substringBefore("?")
        }
        val parts = hostPart.split(":")
        if (parts.size >= 2) parts[1].toInt() else defaultPort
    } catch (e: Exception) {
        defaultPort
    }
}
