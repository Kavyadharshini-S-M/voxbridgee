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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Tsunami
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AlertPriority
import com.example.model.SupportedLanguage
import com.example.ui.components.HoldToSosButton
import com.example.ui.theme.MinimalColorsInstance
import com.example.viewmodel.MissionControlViewModel

/**
 * Visual Hazard Tile representation for non-literate and layman emergency usage.
 */
data class VisualHazardTile(
    val id: String,
    val title: String,
    val iconEmoji: String,
    val iconVector: ImageVector,
    val accentColor: Color,
    val priority: AlertPriority,
    val getMessage: (SupportedLanguage) -> String
)

/**
 * Inclusive Emergency SOS Screen.
 * Design principles:
 * - 3-second tactile hold ring button with haptic feedback (replaces slide-to-SOS).
 * - 6 Visual Hazard Tiles: Medical ➕, Fire 🔥, Flood 🌊, Structural Collapse 🏗️, Cyclone 🌪️, Danger ⚠️.
 * - Auto-attaches live GPS coordinates (`FusedLocationProviderClient`).
 * - Touch targets >= 64dp for hazard tiles, >= 120dp for SOS hold ring.
 * - Jargon-free terminology ("Emergency Alert").
 */
@Composable
fun AlertDistressScreen(
    viewModel: MissionControlViewModel,
    modifier: Modifier = Modifier
) {
    val colors = MinimalColorsInstance
    val uiState by viewModel.uiState.collectAsState()
    val gpsLocation by viewModel.gpsCoordinates.collectAsState()
    
    var selectedPriority by remember { mutableStateOf(AlertPriority.CRITICAL_DISTRESS) }
    var customMessageText by remember { mutableStateOf("") }
    var selectedHazardId by remember { mutableStateOf("medical") }
    var alertSentConfirmation by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // 6 Visual Hazard Tiles with comprehensive translations across Indic languages
    val hazardTiles = remember {
        listOf(
            VisualHazardTile(
                id = "medical",
                title = "Medical",
                iconEmoji = "➕",
                iconVector = Icons.Default.MedicalServices,
                accentColor = Color(0xFFD32F2F), // Red
                priority = AlertPriority.CRITICAL_DISTRESS,
                getMessage = { lang ->
                    when (lang) {
                        SupportedLanguage.HINDI -> "चिकित्सा आपातकाल: तुरंत डॉक्टर और एम्बुलेंस की आवश्यकता है।"
                        SupportedLanguage.ENGLISH -> "Medical Emergency: Doctor and medical aid required immediately."
                        SupportedLanguage.TAMIL -> "மருத்துவ அவசரநிலை: உடனே மருத்துவர் தேவை."
                        SupportedLanguage.TELUGU -> "వైద్య అత్యవసర పరిస్థితి: వెంటనే డాక్టర్ అవసరం."
                        SupportedLanguage.BENGALI -> "চিকিৎসা জরুরি: অবিলম্বে ডাক্তার এবং চিকিৎসা প্রয়োজন।"
                        SupportedLanguage.MARATHI -> "वैद्यकीय आणीबाणी: त्वरित डॉक्टर आणि औषधांची गरज आहे."
                        SupportedLanguage.GUJARATI -> "તબીબી કટોકટી: તાત્કાલિક ડૉક્ટર અને દવાઓની જરૂર છે."
                        SupportedLanguage.KANNADA -> "ವೈದ್ಯಕೀಯ ತುರ್ತುಸ್ಥಿತಿ: ತಕ್ಷಣ ವೈದ್ಯರ ನೆರವು ಬೇಕಾಗಿದೆ."
                        SupportedLanguage.MALAYALAM -> "വൈദ്യസഹായം അടിയന്തിരമായി ആവശ്യമുണ്ട്."
                        SupportedLanguage.ODIA -> "ଡାକ୍ତରୀ ଜରୁରୀକାଳୀନ: ତୁରନ୍ତ ଡାକ୍ତର ଆବଶ୍ୟକ।"
                    }
                }
            ),
            VisualHazardTile(
                id = "fire",
                title = "Fire",
                iconEmoji = "🔥",
                iconVector = Icons.Default.LocalFireDepartment,
                accentColor = Color(0xFFE64A19), // Deep Orange
                priority = AlertPriority.CRITICAL_DISTRESS,
                getMessage = { lang ->
                    when (lang) {
                        SupportedLanguage.HINDI -> "आग का खतरा: भीषण आग लगी है। तुरंत दमकल भेजें।"
                        SupportedLanguage.ENGLISH -> "Fire Hazard: Severe fire breakout. Send fire rescue immediately."
                        SupportedLanguage.TAMIL -> "தீ விபத்து: கடும் தீ பரவுகிறது. தீயணைப்பு படை தேவை."
                        SupportedLanguage.TELUGU -> "అగ్ని ప్రమాదం: తీవ్రమైన మంటలు. ఫైర్ ఇంజిన్ పంపండి."
                        SupportedLanguage.BENGALI -> "অগ্নিকাণ্ড: মারাত্মক আগুন লেগেছে। দমকল পাঠান।"
                        SupportedLanguage.MARATHI -> "आगीचा धोका: मोठी आग लागली आहे. अग्निशामक दल पाठवा."
                        SupportedLanguage.GUJARATI -> "આગ લાગી છે: તાત્કાલિક ફાયર બ્રિગેડ મોકલો."
                        SupportedLanguage.KANNADA -> "ಬೆಂಕಿ ಅವಘಡ: ತಕ್ಷಣ ಅಗ್ನಿಶಾಮಕ ದಳ ಕಳುಹಿಸಿ."
                        SupportedLanguage.MALAYALAM -> "തീപിടുത്തം: അടിയന്തരമായി ഫയർഫോഴ്സിനെ അയക്കുക."
                        SupportedLanguage.ODIA -> "ନିଆଁ ଲାଗିଛି: ତୁରନ୍ତ ଦମକଳ ବାହିନୀ ପଠାନ୍ତୁ।"
                    }
                }
            ),
            VisualHazardTile(
                id = "flood",
                title = "Flood",
                iconEmoji = "🌊",
                iconVector = Icons.Default.Tsunami,
                accentColor = Color(0xFF1976D2), // Blue
                priority = AlertPriority.CRITICAL_DISTRESS,
                getMessage = { lang ->
                    when (lang) {
                        SupportedLanguage.HINDI -> "बाढ़ की चेतावनी: जलस्तर तेजी से बढ़ रहा है। उच्च स्थान पर जाएं।"
                        SupportedLanguage.ENGLISH -> "Flood Alert: Water level rising rapidly. Move to higher ground."
                        SupportedLanguage.TAMIL -> "வெள்ளப்பெருக்கு: நீர்மட்டம் உயர்கிறது. மேடான பகுதிக்கு செல்லவும்."
                        SupportedLanguage.TELUGU -> "వరద ప్రమాదం: నీటి మట్టం పెరుగుతోంది. ఎత్తైన ప్రాంతానికి వెళ్ళండి."
                        SupportedLanguage.BENGALI -> "বন্যা সতর্কতা: জলস্তর বৃদ্ধি পাচ্ছে। উঁচু স্থানে যান।"
                        SupportedLanguage.MARATHI -> "पूर इशारा: पाण्याची पातळी वाढत आहे. सुरक्षित ठिकाणी जा."
                        SupportedLanguage.GUJARATI -> "પૂરની ચેતવણી: પાણી વધી રહ્યું છે. ઊંચા સ્થળે જાઓ."
                        SupportedLanguage.KANNADA -> "ಪ್ರವಾಹ ಎಚ್ಚರಿಕೆ: ನೀರಿನ ಮಟ್ಟ ಹೆಚ್ಚುತ್ತಿದೆ. ಎತ್ತರದ ಸ್ಥಳಕ್ಕೆ ತೆರಳಿ."
                        SupportedLanguage.MALAYALAM -> "പ്രളയ മുന്നറിയിപ്പ്: വെള്ളം ഉയരുന്നു. സുരക്ഷിത സ്ഥാനത്തേക്ക് മാറുക."
                        SupportedLanguage.ODIA -> "ବନ୍ୟା ସତର୍କତା: ଜଳସ୍ତର ବୃଦ୍ଧି ପାଉଛି। ଉଚ୍ଚ ସ୍ଥାନକୁ ଯାଆନ୍ତୁ।"
                    }
                }
            ),
            VisualHazardTile(
                id = "collapse",
                title = "Collapse",
                iconEmoji = "🏗️",
                iconVector = Icons.Default.Domain,
                accentColor = Color(0xFF795548), // Brown
                priority = AlertPriority.CRITICAL_DISTRESS,
                getMessage = { lang ->
                    when (lang) {
                        SupportedLanguage.HINDI -> "भवन ढहना: मलबे में लोग फंसे हैं। बचाव दल भेजें।"
                        SupportedLanguage.ENGLISH -> "Structural Collapse: People trapped under debris. Dispatch rescue team."
                        SupportedLanguage.TAMIL -> "கட்டட இடிவு: இடிபாடுகளில் மக்கள் சிக்கியுள்ளனர்."
                        SupportedLanguage.TELUGU -> "భవనం కూలిపోయింది: శిథిలాల కింద జనం చిక్కుకున్నారు."
                        SupportedLanguage.BENGALI -> "ভবন ধস: ধ্বংসস্তূপে মানুষ আটকে আছে। উদ্ধারকারী দল পাঠান।"
                        SupportedLanguage.MARATHI -> "इमारत कोसळली: मलब्याखाली लोक अडकले आहेत."
                        SupportedLanguage.GUJARATI -> "મકાન ધરાશાયી: કાટમાળમાં લોકો ફસાયા છે."
                        SupportedLanguage.KANNADA -> "ಕಟ್ಟಡ ಕುಸಿತ: ಅವಶೇಷಗಳ ಅಡಿಯಲ್ಲಿ ಜನರು ಸಿಲುಕಿದ್ದಾರೆ."
                        SupportedLanguage.MALAYALAM -> "കെട്ടിടം തകർന്നു: ആളുകൾ കുടുങ്ങിയിരിക്കുന്നു."
                        SupportedLanguage.ODIA -> "ଭବନ ଭୁଶୁଡ଼ିବା: ଭଗ୍ନାବଶେଷ ତଳେ ଲୋକେ ଫସିଛନ୍ତି।"
                    }
                }
            ),
            VisualHazardTile(
                id = "cyclone",
                title = "Cyclone",
                iconEmoji = "🌪️",
                iconVector = Icons.Default.Warning,
                accentColor = Color(0xFF00897B), // Teal
                priority = AlertPriority.CRITICAL_DISTRESS,
                getMessage = { lang ->
                    when (lang) {
                        SupportedLanguage.HINDI -> "चक्रवात आंधी: तेज हवाएं और तूफान। आश्रय में रहें।"
                        SupportedLanguage.ENGLISH -> "Cyclone / Storm: Extreme wind and rain. Take shelter immediately."
                        SupportedLanguage.TAMIL -> "புயல் காற்று: பலத்த காற்று வீசுகிறது. பாதுகாப்பான இடத்தில் இருங்கள்."
                        SupportedLanguage.TELUGU -> "తీవ్ర తుఫాను: భారీ గాలులు. సురక్షిత ఆశ్రయంలో ఉండండి."
                        SupportedLanguage.BENGALI -> "ঘূর্ণিঝড়: প্রবল বাতাস ও ঝড়। নিরাপদ আশ্রয়ে থাকুন।"
                        SupportedLanguage.MARATHI -> "चक्रीवादळ: जोरदार वारे आणि पाऊस. सुरक्षित राहा."
                        SupportedLanguage.GUJARATI -> "વાવાઝોડું: ભારે પવન અને વરસાદ. આશ્રય લો."
                        SupportedLanguage.KANNADA -> "ಚಂಡಮಾರುತ: ಬಿರುಗಾಳಿ ಮತ್ತು ಮಳೆ. ಸುರಕ್ಷಿತವಾಗಿರಿ."
                        SupportedLanguage.MALAYALAM -> "ചുഴലിക്കാറ്റ്: ശക്തമായ കാറ്റും മഴയും. സുരക്ഷിതരായിരിക്കുക."
                        SupportedLanguage.ODIA -> "ବାତ୍ୟା: ପ୍ରବଳ ପବନ ଏବଂ ବର୍ଷା। ନିରାପଦରେ ରୁହନ୍ତୁ।"
                    }
                }
            ),
            VisualHazardTile(
                id = "danger",
                title = "Danger",
                iconEmoji = "⚠️",
                iconVector = Icons.Default.WarningAmber,
                accentColor = Color(0xFFF57C00), // Amber
                priority = AlertPriority.CRITICAL_DISTRESS,
                getMessage = { lang ->
                    when (lang) {
                        SupportedLanguage.HINDI -> "अत्यधिक खतरा: तत्काल सहायता की आवश्यकता है।"
                        SupportedLanguage.ENGLISH -> "Critical Danger: Emergency assistance needed immediately."
                        SupportedLanguage.TAMIL -> "ஆபத்து: உடனடி உதவி தேவை."
                        SupportedLanguage.TELUGU -> "తీవ్ర ప్రమాదం: వెంటనే సహాయం కావాలి."
                        SupportedLanguage.BENGALI -> "চরম বিপদ: জরুরি সাহায্য প্রয়োজন।"
                        SupportedLanguage.MARATHI -> "गंभीर धोका: त्वरित मदतीची गरज आहे."
                        SupportedLanguage.GUJARATI -> "મોટો ખતરો: તાત્કાલિક મદદની જરૂર છે."
                        SupportedLanguage.KANNADA -> "ಅಪಾಯ: ತಕ್ಷಣ ಸಹಾಯ ಬೇಕಾಗಿದೆ."
                        SupportedLanguage.MALAYALAM -> "അപകടാവസ്ഥ: ഉടൻ സഹായം ആവശ്യമാണ്."
                        SupportedLanguage.ODIA -> "ବିପଦ: ତୁରନ୍ତ ସାହାଯ୍ୟ ଆବଶ୍ୟକ।"
                    }
                }
            )
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        val isCompact = maxWidth < 380.dp || maxHeight < 680.dp
        val horizontalPadding = if (isCompact) 14.dp else 20.dp
        val verticalPadding = if (isCompact) 12.dp else 18.dp
        val sectionSpacing = if (isCompact) 14.dp else 20.dp

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Scrollable Content Container
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                verticalArrangement = Arrangement.spacedBy(sectionSpacing)
            ) {
                // Section 1: Screen Header
                Column {
                    Text(
                        text = "Emergency Alert",
                        fontSize = if (isCompact) 24.sp else 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Broadcast instant multi-sensory emergency alert over mesh",
                        fontSize = if (isCompact) 12.sp else 13.sp,
                        color = colors.textSecondary
                    )
                }

                // Section 2: Live Attached GPS Location Badge
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.outline, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.accent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "GPS Location",
                                tint = colors.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Auto-Attached GPS Location",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textSecondary
                            )
                            Text(
                                text = if (!gpsLocation.isNullOrBlank()) {
                                    gpsLocation ?: "Fix acquired"
                                } else {
                                    "Acquiring satellite fix..."
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (!gpsLocation.isNullOrBlank()) colors.accent else colors.textSecondary
                            )
                        }
                    }
                }

                // Section 3: Confirmation Banner if alert dispatched
                if (alertSentConfirmation) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surface)
                            .border(1.5.dp, colors.accent, RoundedCornerShape(16.dp))
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Emergency alert broadcasted!",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Transmitted to all radios with siren & flashlight strobe override.",
                                    fontSize = 12.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }

                // Section 4: 6 Visual Hazard Tiles (Grid Layout, >=64dp touch target)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SELECT HAZARD TYPE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textSecondary,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Tap to select",
                            fontSize = 11.sp,
                            color = colors.accent
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        hazardTiles.chunked(2).forEach { rowTiles ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowTiles.forEach { tile ->
                                    val isSelected = selectedHazardId == tile.id
                                    
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 72.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(
                                                if (isSelected) tile.accentColor.copy(alpha = 0.18f)
                                                else colors.surface
                                            )
                                            .border(
                                                width = if (isSelected) 2.5.dp else 1.dp,
                                                color = if (isSelected) tile.accentColor else colors.outline,
                                                shape = RoundedCornerShape(14.dp)
                                            )
                                            .clickable {
                                                selectedHazardId = tile.id
                                                customMessageText = tile.getMessage(uiState.selectedLanguage)
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
                                                    .size(42.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(tile.accentColor.copy(alpha = 0.25f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = tile.iconVector,
                                                    contentDescription = tile.title,
                                                    tint = tile.accentColor,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }

                                            Column {
                                                Text(
                                                    text = tile.title,
                                                    fontSize = 14.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                    color = if (isSelected) tile.accentColor else colors.textPrimary
                                                )
                                                Text(
                                                    text = tile.iconEmoji,
                                                    fontSize = 14.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 5: Broadcast Message Preview / Custom Input
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Message to Broadcast",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )

                    val activeTile = hazardTiles.find { it.id == selectedHazardId }
                    val currentDisplayMessage = customMessageText.ifBlank {
                        activeTile?.getMessage?.invoke(uiState.selectedLanguage) ?: "Emergency assistance needed immediately."
                    }

                    OutlinedTextField(
                        value = customMessageText.ifBlank { currentDisplayMessage },
                        onValueChange = { customMessageText = it },
                        placeholder = { Text("Emergency alert text...", color = colors.textSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.outline,
                            focusedContainerColor = colors.surface,
                            unfocusedContainerColor = colors.surface,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(84.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            // Section 6: Dedicated Bottom 3-Second Hold Ring SOS Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .padding(horizontal = horizontalPadding, vertical = 12.dp)
                    .navigationBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                val activeTile = hazardTiles.find { it.id == selectedHazardId }
                val messageToBroadcast = customMessageText.ifBlank {
                    activeTile?.getMessage?.invoke(uiState.selectedLanguage) ?: "आपातकालीन चेतावनी: तत्काल सहायता आवश्यक है।"
                }

                HoldToSosButton(
                    onHoldComplete = {
                        viewModel.broadcastDistressAlert(
                            customMessage = messageToBroadcast,
                            priority = AlertPriority.CRITICAL_DISTRESS
                        )
                        alertSentConfirmation = true
                    },
                    buttonDiameter = if (isCompact) 140.dp else 160.dp
                )
            }
        }
    }
}
