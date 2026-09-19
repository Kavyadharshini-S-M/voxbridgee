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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.WarningAmber
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.mutableFloatStateOf
import kotlin.math.roundToInt
import com.example.model.AlertPriority
import com.example.model.SupportedLanguage
import com.example.ui.theme.MinimalColorsInstance
import com.example.viewmodel.MissionControlViewModel

/**
 * Minimal Emergency SOS Screen (Linear / Things 3 aesthetic).
 * Restrained, high-clarity emergency broadcast interface.
 */
@Composable
fun AlertDistressScreen(
    viewModel: MissionControlViewModel,
    modifier: Modifier = Modifier
) {
    val colors = MinimalColorsInstance
    val uiState by viewModel.uiState.collectAsState()
    val connectedPeer by viewModel.connectedPeer.collectAsState()
    var selectedPriority by remember { mutableStateOf(AlertPriority.CRITICAL_DISTRESS) }
    var customMessageText by remember { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var alertSentConfirmation by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    val activePresets = remember(uiState.selectedLanguage, selectedPriority) {
        val lang = uiState.selectedLanguage
        when (selectedPriority) {
            AlertPriority.CRITICAL_DISTRESS -> when (lang) {
                SupportedLanguage.HINDI -> listOf(
                    Pair("Cyclone Alert", "चक्रवात चेतावनी: तटीय क्षेत्र तुरंत खाली करें। सुरक्षित आश्रय में जाएं।"),
                    Pair("Flash Flood", "बाढ़ चेतावनी: जलस्तर तेजी से बढ़ रहा है। उच्च स्थान की ओर प्रस्थान करें।"),
                    Pair("Life-Threat Evacuation", "गंभीर आपातकाल: तत्काल बचाव दल और एयर-इवैक्यूएशन की आवश्यकता है।"),
                    Pair("Structural Collapse", "भूकंप / भवन पतन: मलबे में लोग फंसे हैं। भारी बचाव उपकरण भेजें।")
                )
                SupportedLanguage.ENGLISH -> listOf(
                    Pair("Cyclone Alert", "Cyclone Warning: Evacuate coastal zones immediately. Move to storm shelter."),
                    Pair("Flash Flood", "Flash Flood Alert: Water levels rising rapidly. Move to higher ground."),
                    Pair("Life-Threat Evacuation", "Critical Emergency: Immediate rescue team and air evacuation requested."),
                    Pair("Structural Collapse", "Structural Collapse: Personnel trapped under debris. Dispatch heavy rescue gear.")
                )
                SupportedLanguage.TAMIL -> listOf(
                    Pair("Cyclone Alert", "புயல் எச்சரிக்கை: கடலோர பகுதிகளை உடனே காலி செய்யவும். பாதுகாப்பான இடத்திற்கு செல்லவும்."),
                    Pair("Flash Flood", "வெள்ள அபாய எச்சரிக்கை: நீர்மட்டம் உயர்கிறது. மேடான பகுதிக்கு செல்லவும்."),
                    Pair("Life-Threat Evacuation", "உயிராபத்து அவசரநிலை: உடனடி மீட்பு குழுவை அனுப்பவும்."),
                    Pair("Structural Collapse", "கட்டட இடிவு: இடிபாடுகளில் மக்கள் சிக்கியுள்ளனர். மீட்பு கருவிகளை அனுப்பவும்.")
                )
                SupportedLanguage.TELUGU -> listOf(
                    Pair("Cyclone Alert", "తుఫాను హెచ్చరిక: తీర ప్రాంతాలను వెంటనే ఖాళీ చేయండి. సురక్షిత ప్రాంతానికి వెళ్ళండి."),
                    Pair("Flash Flood", "వరద హెచ్చరిక: నీటి మట్టం వేగంగా పెరుగుతోంది. ఎత్తైన ప్రదేశాలకు వెళ్ళండి."),
                    Pair("Life-Threat Evacuation", "తీవ్ర అత్యవసర పరిస్థితి: వెంటనే రెస్క్యూ బృందాన్ని పంపండి."),
                    Pair("Structural Collapse", "భవన కూలిపోవడం: శిథిలాల క్రింద జనం చిక్కుకున్నారు. సహాయక పరికరాలు పంపండి.")
                )
                SupportedLanguage.BENGALI -> listOf(
                    Pair("Cyclone Alert", "ঘূর্ণিঝড় সতর্কতা: উপকূলীয় এলাকা অবিলম্বে খালি করুন। নিরাপদ আশ্রয়ে যান।"),
                    Pair("Flash Flood", "বন্যা সতর্কতা: জলস্তর দ্রুত বৃদ্ধি পাচ্ছে। উঁচু স্থানে সরে যান।"),
                    Pair("Life-Threat Evacuation", "জরুরি উদ্ধার প্রয়োজন: অবিলম্বে উদ্ধারকারী দল এবং ত্রাণ পাঠান।"),
                    Pair("Structural Collapse", "ভবন ধস: ধ্বংসস্তূপে মানুষ আটকে আছে। ভারী উদ্ধার সরঞ্জাম পাঠান।")
                )
                SupportedLanguage.MARATHI -> listOf(
                    Pair("Cyclone Alert", "चक्रवात इशारा: किनारी भाग त्वरित रिकामे करा. सुरक्षित निवाऱ्यात जा."),
                    Pair("Flash Flood", "पूर इशारा: पाण्याची पातळी वेगाने वाढत आहे. उंच ठिकाणी स्थलांतर करा."),
                    Pair("Life-Threat Evacuation", "गंभीर आपत्कालीन स्थिती: त्वरित बचाव पथक आणि मदत पाठवा."),
                    Pair("Structural Collapse", "इमारत पडझड: ढिगाऱ्याखाली लोक अडकले आहेत. बचाव उपकरणे पाठवा.")
                )
                SupportedLanguage.GUJARATI -> listOf(
                    Pair("Cyclone Alert", "વાવાઝોડાની ચેતવણી: દરિયાકાંઠાના વિસ્તારો તાત્કાલિક ખાલી કરો. સલામત સ્થળે જાઓ."),
                    Pair("Flash Flood", "પૂર ચેતવણી: પાણીની સપાટી ઝડપથી વધી રહી છે. ઊંચા સ્થળે પ્રયાણ કરો."),
                    Pair("Life-Threat Evacuation", "કટોકટીની સ્થિતિ: તાત્કાલિક બચાવ ટીમ અને સહાય મોકલો."),
                    Pair("Structural Collapse", "મકાન ધરાશાયી: કાટમાળમાં લોકો ફસાયેલા છે. બચાવ સાધનો મોકલો.")
                )
                SupportedLanguage.KANNADA -> listOf(
                    Pair("Cyclone Alert", "ಚಂಡಮಾರುತ ಎಚ್ಚರಿಕೆ: ಕರಾವಳಿ ಪ್ರದೇಶಗಳನ್ನು ಕೂಡಲೇ ತೆರವುಗೊಳಿಸಿ. ಸುರಕ್ಷಿತ ಸ್ಥಳಕ್ಕೆ ತೆರಳಿ."),
                    Pair("Flash Flood", "ಪ್ರವಾಹ ಎಚ್ಚರಿಕೆ: ನೀರಿನ ಮಟ್ಟ ವೇಗವಾಗಿ ಏರುತ್ತಿದೆ. ಎತ್ತರದ ಪ್ರದೇಶಕ್ಕೆ ತೆರಳಿ."),
                    Pair("Life-Threat Evacuation", "ತುರ್ತು ಪರಿಸ್ಥಿತಿ: ತಕ್ಷಣ ರಕ್ಷಣಾ ತಂಡ ಮತ್ತು ನೆರವು ಕಳುಹಿಸಿ."),
                    Pair("Structural Collapse", "ಕಟ್ಟಡ ಕುಸಿತ: ಅವಶೇಷಗಳ ಅಡಿಯಲ್ಲಿ ಜನರು ಸಿಲುಕಿದ್ದಾರೆ. ರಕ್ಷಣಾ ಉಪಕರಣಗಳನ್ನು ಕಳುಹಿಸಿ.")
                )
                SupportedLanguage.MALAYALAM -> listOf(
                    Pair("Cyclone Alert", "ചുഴലിക്കാറ്റ് മുന്നറിയിപ്പ്: തീരദേശ മേഖലകൾ ഉടൻ ഒഴിയുക. സുരക്ഷിത കേന്ദ്രങ്ങളിലേക്ക് മാറുക."),
                    Pair("Flash Flood", "പ്രളയ മുന്നറിയിപ്പ്: ജലനിരപ്പ് വേഗത്തിൽ ഉയരുന്നു. ഉയർന്ന സ്ഥലങ്ങളിലേക്ക് മാറുക."),
                    Pair("Life-Threat Evacuation", "അടിയന്തര രക്ഷാപ്രവർത്തനം ആവശ്യമാണ്: രക്ഷാപ്രവർത്തകരെ അയക്കുക."),
                    Pair("Structural Collapse", "കെട്ടിട തകർച്ച: അവശിഷ്ടങ്ങൾക്കിടയിൽ ആളുകൾ കുടുങ്ങിയിരിക്കുന്നു.")
                )
                SupportedLanguage.ODIA -> listOf(
                    Pair("Cyclone Alert", "ବାତ୍ୟା ସତର୍କତା: ଉପକୂଳବର୍ତ୍ତୀ ଅଞ୍ଚଳ ତୁରନ୍ତ ଖାଲି କରନ୍ତୁ। ନିରାପଦ ଆଶ୍ରୟସ୍ଥଳକୁ ଯାଆନ୍ତୁ।"),
                    Pair("Flash Flood", "ବନ୍ୟା ସତର୍କତା: ଜଳସ୍ତର ଦ୍ରୁତ ଗତିରେ ବୃଦ୍ଧି ପାଉଛି। ଉଚ୍ଚ ସ୍ଥାନକୁ ପ୍ରସ୍ଥାନ କରନ୍ତୁ।"),
                    Pair("Life-Threat Evacuation", "ଜରୁରୀକାଳୀନ ପରିସ୍ଥିତି: ତୁରନ୍ତ ଉଦ୍ଧାରକାରୀ ଦଳ ପଠାନ୍ତୁ।"),
                    Pair("Structural Collapse", "ଭବନ ଭୁଶୁଡ଼ିବା: ଭଗ୍ନାବଶେଷ ତଳେ ଲୋକେ ଫସି ରହିଛନ୍ତି।")
                )
            }
            AlertPriority.URGENT -> when (lang) {
                SupportedLanguage.HINDI -> listOf(
                    Pair("Hazard Warning", "चेतावनी: आगे मार्ग अवरुद्ध है और उच्च-वोल्टेज तार टूटे हैं। सावधानी बरतें।"),
                    Pair("Medical Attention", "चिकित्सा सहायता: घायल सदस्य को प्राथमिक उपचार और स्ट्रेचर की आवश्यकता है।"),
                    Pair("Comms Blackout", "आपदा संचार सूचना: प्राथमिक टॉवर बंद। सामरिक आपातकालीन मेश चालू है।"),
                    Pair("Severe Weather", "मौसम चेतावनी: भारी आंधी और वज्रपात की संभावना। उपकरणों को सुरक्षित करें।")
                )
                SupportedLanguage.ENGLISH -> listOf(
                    Pair("Hazard Warning", "Hazard Warning: Route ahead blocked. Power lines down. Proceed with caution."),
                    Pair("Medical Attention", "Medical Attention: Injured person requires immediate first aid and stretcher."),
                    Pair("Comms Blackout", "Comms Notice: Primary cellular towers down. Tactical emergency mesh active."),
                    Pair("Severe Weather", "Severe Weather: High winds and lightning strike risk. Secure field gear.")
                )
                SupportedLanguage.TAMIL -> listOf(
                    Pair("Hazard Warning", "ஆபத்து எச்சரிக்கை: பாதை தடைப்பட்டுள்ளது. மின்சார கம்பிகள் அறுந்து விழுந்துள்ளன."),
                    Pair("Medical Attention", "மருத்துவ உதவி: காயமடைந்தவருக்கு முதலுதவி மற்றும் ஸ்ட்ரெச்சர் தேவை."),
                    Pair("Comms Blackout", "தொடர்பு தகவல்: செல்லுலார் கோபுரங்கள் செயலிழந்தன. அவசர வயர்லெஸ் நெட்வொர்க் இயங்குகிறது."),
                    Pair("Severe Weather", "வானிலை எச்சரிக்கை: கடுமையான காற்று மற்றும் இடி மின்னல் அபாயம்.")
                )
                SupportedLanguage.TELUGU -> listOf(
                    Pair("Hazard Warning", "ప్రమాద హెచ్చరిక: ముందు మార్గం మూసివేయబడింది. విద్యుత్ తీగలు తెగిపడ్డాయి."),
                    Pair("Medical Attention", "వైద్య సహాయం: గాయపడిన వారికి ప్రాథమిక చికిత్స మరియు స్ట్రెచర్ అవసరం."),
                    Pair("Comms Blackout", "కమ్యూనికేషన్ సమాచారం: టవర్లు పనిచేయడం లేదు. ఎమర్జెన్సీ మెష్ ఆన్‌లో ఉంది."),
                    Pair("Severe Weather", "వాతావరణ హెచ్చరిక: బలమైన గాలులు మరియు పిడుగులు పడే అవకాశం.")
                )
                SupportedLanguage.BENGALI -> listOf(
                    Pair("Hazard Warning", "বিপদ সতর্কতা: সামনের রাস্তা বন্ধ। বিদ্যুৎ তার ছিঁড়ে গেছে। সাবধানে থাকুন।"),
                    Pair("Medical Attention", "চিকিৎসা সহায়তা: আহত ব্যক্তির প্রাথমিক চিকিৎসা এবং স্ট্রেচার প্রয়োজন।"),
                    Pair("Comms Blackout", "যোগাযোগ তথ্য: সেলুলার টাওয়ার বন্ধ। জরুরি রেডিও নেটওয়ার্ক সক্রিয়।"),
                    Pair("Severe Weather", "আবহাওয়া সতর্কতা: প্রবল ঝড় ও বজ্রপাতের আশঙ্কা। সতর্ক থাকুন।")
                )
                SupportedLanguage.MARATHI -> listOf(
                    Pair("Hazard Warning", "धोका इशारा: पुढे रस्ता बंद आहे. विजेच्या तारा तुटल्या आहेत. काळजी घ्या."),
                    Pair("Medical Attention", "वैद्यकीय मदत: जखमी व्यक्तीला प्रथमोपचार आणि स्ट्रेचरची गरज आहे."),
                    Pair("Comms Blackout", "संपर्क माहिती: मुख्य टॉवर बंद. आपत्कालीन वायरलेस मेश चालू आहे."),
                    Pair("Severe Weather", "हवामान इशारा: जोरदार वारे आणि वीज पडण्याची शक्यता. उपकरणे सुरक्षित ठेवा.")
                )
                SupportedLanguage.GUJARATI -> listOf(
                    Pair("Hazard Warning", "જોખમ ચેતવણી: આગળ રસ્તો બંધ છે અને વીજ વાયર તૂટેલા છે. સાવચેતી રાખો."),
                    Pair("Medical Attention", "તબીબી સહાય: ઘાયલ વ્યક્તિને પ્રાથમિક સારવાર અને સ્ટ્રેચરની જરૂર છે."),
                    Pair("Comms Blackout", "સંચાર માહિતી: ટાવર બંધ છે. કટોકટી વાયરલેસ મેશ સક્રિય છે."),
                    Pair("Severe Weather", "હવામાન ચેતવણી: ભારે પવન અને વીજળી પડવાની શક્યતા છે.")
                )
                SupportedLanguage.KANNADA -> listOf(
                    Pair("Hazard Warning", "ಅಪಾಯದ ಎಚ್ಚರಿಕೆ: ಮುಂದೆ ರಸ್ತೆ ಬಂದ್ ಆಗಿದೆ. ವಿದ್ಯುತ್ ತಂತಿಗಳು ಬಿದ್ದಿವೆ."),
                    Pair("Medical Attention", "ವೈದ್ಯಕೀಯ ನೆರವು: ಗಾಯಗೊಂಡ ವ್ಯಕ್ತಿಗೆ ಪ್ರಥಮ ಚಿಕಿತ್ಸೆ ಮತ್ತು ಸ್ಟ್ರೆಚರ್ ಬೇಕಾಗಿದೆ."),
                    Pair("Comms Blackout", "ಸಂಪರ್ಕ ಮಾಹಿತಿ: ಟವರ್‌ಗಳು ಸ್ಥಗಿತಗೊಂಡಿವೆ. ತುರ್ತು ವೈರ್‌ಲೆಸ್ ಮೆಶ್ ಚಾಲನೆಯಲ್ಲಿದೆ."),
                    Pair("Severe Weather", "ಹವಾಮಾನ ಎಚ್ಚರಿಕೆ: ಭಾರೀ ಗಾಳಿ ಮತ್ತು ಸಿಡಿಲು ಬೀಳುವ ಸಾಧ್ಯತೆ.")
                )
                SupportedLanguage.MALAYALAM -> listOf(
                    Pair("Hazard Warning", "അപകട മുന്നറിയിപ്പ്: വഴി തടസ്സപ്പെട്ടു. വൈദ്യുതി ലൈനുകൾ തകർന്നു വീണു."),
                    Pair("Medical Attention", "വൈദ്യസഹായം: പരിക്കേറ്റയാൾക്ക് പ്രഥമശുശ്രൂഷയും സ്ട്രെച്ചറും ആവശ്യമാണ്."),
                    Pair("Comms Blackout", "ആശയവിനിമയ വിവരം: ടവറുകൾ പ്രവർത്തനരഹിതമാണ്. അടിയന്തര മെഷ് സജീവമാണ്."),
                    Pair("Severe Weather", "കാലാവസ്ഥാ മുന്നറിയിപ്പ്: ശക്തമായ കാറ്റും മിന്നലും ഉണ്ടാകാൻ സാധ്യത.")
                )
                SupportedLanguage.ODIA -> listOf(
                    Pair("Hazard Warning", "ବିପଦ ସତର୍କତା: ଆଗରେ ରାସ୍ତା ବନ୍ଦ ଅଛି। ବିଦ୍ୟୁତ୍ ତାର ଛିଣ୍ଡି ପଡ଼ିଛି। ସାବଧାନ ରୁହନ୍ତୁ।"),
                    Pair("Medical Attention", "ଡାକ୍ତରୀ ସାହାଯ୍ୟ: ଆହତ ବ୍ୟକ୍ତିଙ୍କୁ ପ୍ରାଥମିକ ଚିକିତ୍ସା ଏବଂ ଷ୍ଟ୍ରେଚର୍ ଆବଶ୍ୟକ।"),
                    Pair("Comms Blackout", "ଯୋଗାଯୋଗ ସୂଚନା: ମୋବାଇଲ୍ ଟାୱାର୍ ବନ୍ଦ। ଜରୁରୀକାଳୀନ ବେତାର ମେସ୍ ସକ୍ରିୟ।"),
                    Pair("Severe Weather", "ପାଣିପାଗ ସତର୍କତା: ପ୍ରବଳ ପବନ ଏବଂ ବଜ୍ରପାତର ସମ୍ଭାବନା।")
                )
            }
            AlertPriority.ROUTINE -> when (lang) {
                SupportedLanguage.HINDI -> listOf(
                    Pair("Status Check", "स्थिति सामान्य: मेश ट्रांससीवर सक्रिय और सभी दल सुरक्षित हैं।"),
                    Pair("Supply Inventory", "सामग्री जांच: भोजन, पेयजल और आपातकालीन चिकित्सा किट पर्याप्त मात्रा में हैं।"),
                    Pair("Patrol Check-in", "गश्ती रिपोर्ट: सेक्टर का निरीक्षण पूर्ण। कोई असामान्य गतिविधि नहीं।"),
                    Pair("Base Secured", "बेस कैंप सुरक्षित: सामरिक वायरलेस चैनल चालू। आगामी संदेशों की प्रतीक्षा है।")
                )
                SupportedLanguage.ENGLISH -> listOf(
                    Pair("Status Check", "Status Normal: Tactical mesh transceiver active and all teams safe."),
                    Pair("Supply Inventory", "Supply Check: Food, water and emergency medical kits sufficient."),
                    Pair("Patrol Check-in", "Patrol Report: Sector perimeter secure. No anomalies detected."),
                    Pair("Base Secured", "Base Secured: Tactical wireless channel standing by for transmission.")
                )
                SupportedLanguage.TAMIL -> listOf(
                    Pair("Status Check", "நிலைமை சீரானது: நாங்கள் இங்கு பாதுகாப்பாக உள்ளோம்."),
                    Pair("Supply Inventory", "பொருட்கள் இருப்பு: உணவு, குடிநீர் மற்றும் முதலுதவி பெட்டிகள் தயார்."),
                    Pair("Patrol Check-in", "ரோந்து அறிக்கை: பகுதி முழுவதும் பாதுகாப்பாக உள்ளது."),
                    Pair("Base Secured", "முகாம் பாதுகாப்பானது: வயர்லெஸ் நெட்வொர்க் தயார் நிலையில் உள்ளது.")
                )
                SupportedLanguage.TELUGU -> listOf(
                    Pair("Status Check", "పరిస్థితి సాధారణం: మేము ఇక్కడ సురక్షితంగా ఉన్నాము."),
                    Pair("Supply Inventory", "సామాగ్రి వివరాలు: ఆహారం, తాగునీరు మరియు ప్రథమ చికిత్స కిట్లు సిద్ధంగా ఉన్నాయి."),
                    Pair("Patrol Check-in", "గస్తీ నివేదిక: ప్రాంతం సురಕ್ಷితంగా ఉంది."),
                    Pair("Base Secured", "బేస్ క్యాంప్ సురక్షితం: వైర్‌లెస్ ఛానల్ సిద్ధంగా ఉంది.")
                )
                SupportedLanguage.BENGALI -> listOf(
                    Pair("Status Check", "পরিস্থিতি স্বাভাবিক: আমরা এখানে নিরাপদে আছি।"),
                    Pair("Supply Inventory", "ত্রাণ মজুদ: খাবার, পানীয় জল ও ওষুধ পর্যাপ্ত রয়েছে।"),
                    Pair("Patrol Check-in", "টহল রিপোর্ট: এলাকা সম্পূর্ণ নিরাপদ।"),
                    Pair("Base Secured", "বেস ক্যাম্প নিরাপদ: রেডিও চ্যানেল সক্রিয় রয়েছে।")
                )
                SupportedLanguage.MARATHI -> listOf(
                    Pair("Status Check", "स्थिती सामान्य: आम्ही येथे सुरक्षित आहोत."),
                    Pair("Supply Inventory", "साहित्य तपासणी: अन्न, पाणी व वैद्यकीय किट्स पुरेशा प्रमाणात आहेत."),
                    Pair("Patrol Check-in", "गस्त अहवाल: परिसर सुरक्षित आहे."),
                    Pair("Base Secured", "बेस कॅम्प सुरक्षित: आपत्कालीन वायरलेस चॅनेल सुरू आहे.")
                )
                SupportedLanguage.GUJARATI -> listOf(
                    Pair("Status Check", "સ્થિતિ સામાન્ય: અમે અહીં સુરક્ષિત છીએ."),
                    Pair("Supply Inventory", "સામગ્રી તપાસ: ખોરાક, પાણી અને દવાઓ પર્યાપ્ત છે."),
                    Pair("Patrol Check-in", "પેટ્રોલિંગ અહેવાલ: વિસ્તાર સુરક્ષિત છે."),
                    Pair("Base Secured", "બેઝ કેમ્પ સુરક્ષિત: વાયરલેસ ચેનલ ચાલુ છે.")
                )
                SupportedLanguage.KANNADA -> listOf(
                    Pair("Status Check", "ಸ್ಥಿತಿ ಸಾಮಾನ್ಯ: ನಾವು ಇಲ್ಲೇ ಸುರಕ್ಷಿತವಾಗಿದ್ದೇವೆ."),
                    Pair("Supply Inventory", "ದಾಸ್ತಾನು ಪರಿಶೀಲನೆ: ಆಹಾರ, ನೀರು ಮತ್ತು ಔಷಧಗಳು ಸಾಕಷ್ಟು ಇವೆ."),
                    Pair("Patrol Check-in", "ಗಸ್ತು ವರದಿ: ವಲಯ ಸುರಕ್ಷಿತವಾಗಿದೆ."),
                    Pair("Base Secured", "ಬೇಸ್ ಕ್ಯಾಂಪ್ ಸುರಕ್ಷಿತ: ವೈರ್‌ಲೆಸ್ ಚಾನಲ್ ಸಿದ್ಧವಾಗಿದೆ.")
                )
                SupportedLanguage.MALAYALAM -> listOf(
                    Pair("Status Check", "സ്ഥിതി സാധാരണമാണ്: ഞങ്ങൾ ഇവിടെ സുരക്ഷിതരാണ്."),
                    Pair("Supply Inventory", "സാമഗ്രികളുടെ ലഭ്യത: ഭക്ഷണവും വെള്ളവും മരുന്നുകളും ആവശ്യത്തിനുണ്ട്."),
                    Pair("Patrol Check-in", "പട്രോളിംഗ് റിപ്പോർട്ട്: മേഖല സുരക്ഷിതമാണ്."),
                    Pair("Base Secured", "ബേസ് ക്യാമ്പ് സുരക്ഷിതം: വയർലെസ് ചാനൽ സജ്ജമാണ്.")
                )
                SupportedLanguage.ODIA -> listOf(
                    Pair("Status Check", "ସ୍ଥିତି ସାଧାରଣ: ଆମେ ଏଠାରେ ସୁରକ୍ଷିତ ଅଛୁ।"),
                    Pair("Supply Inventory", "ସାମଗ୍ରୀ ଯାଞ୍ଚ: ଖାଦ୍ୟ, ପାଣି ଏବଂ ଔଷଧ ଯଥେଷ୍ଟ ଅଛି।"),
                    Pair("Patrol Check-in", "ପାଟ୍ରୋଲିଂ ରିପୋର୍ଟ: ଅଞ୍ଚଳ ନିରାପଦ ଅଛି।"),
                    Pair("Base Secured", "ବେସ୍ କ୍ୟାମ୍ପ୍ ସୁରକ୍ଷିତ: ବେତାର ଚ୍ୟାନେଲ୍ ପ୍ରସ୍ତୁତ ଅଛି।")
                )
            }
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
        val sectionSpacing = if (isCompact) 16.dp else 24.dp

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Scrollable Content Column
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
                        text = "Emergency SOS",
                        fontSize = if (isCompact) 24.sp else 28.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Broadcast priority tactical alerts over offline mesh",
                        fontSize = if (isCompact) 12.sp else 13.sp,
                        color = colors.textSecondary
                    )
                }

                // Section 2: Confirmation Notice if broadcast
                if (alertSentConfirmation) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surface)
                            .border(1.dp, colors.accent, RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Distress signal broadcasted",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Transmitted to all connected mesh nodes with priority volume override",
                                    fontSize = 13.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }

                // Section 3: Priority Selector (Restrained Segmented Style)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Alert priority category",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary
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
                            val priorities = listOf(
                                Pair(AlertPriority.CRITICAL_DISTRESS, "Critical SOS"),
                                Pair(AlertPriority.URGENT, "Urgent"),
                                Pair(AlertPriority.ROUTINE, "Routine")
                            )

                            priorities.forEach { (priority, label) ->
                                val isSelected = selectedPriority == priority
                                val btnColor = when (priority) {
                                    AlertPriority.CRITICAL_DISTRESS -> colors.error
                                    AlertPriority.URGENT -> colors.accent
                                    AlertPriority.ROUTINE -> colors.textSecondary
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) btnColor.copy(alpha = 0.2f) else Color.Transparent)
                                        .clickable {
                                            selectedPriority = priority
                                            customMessageText = ""
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) btnColor else colors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // Section 4: Quick Emergency Presets for selected category
                val categoryTitle = when (selectedPriority) {
                    AlertPriority.CRITICAL_DISTRESS -> "Critical SOS"
                    AlertPriority.URGENT -> "Urgent"
                    AlertPriority.ROUTINE -> "Routine"
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Preset $categoryTitle messages",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        activePresets.forEach { (label, text) ->
                            val isSelected = customMessageText == text

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) colors.accentContainer else colors.surface)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) colors.accent else colors.outline,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { customMessageText = text }
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text(
                                        text = label,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isSelected) colors.accent else colors.textPrimary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = text,
                                        fontSize = 13.sp,
                                        color = colors.textSecondary,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                }

                // Section 5: Custom Message Input
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Custom broadcast message",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary
                    )

                    OutlinedTextField(
                        value = customMessageText,
                        onValueChange = { customMessageText = it },
                        placeholder = { Text("Enter emergency broadcast text...", color = colors.textSecondary) },
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
                            .height(96.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Dedicated Bottom Container for SOS Slider with Navigation Bars Padding
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .padding(horizontal = horizontalPadding, vertical = 8.dp)
                    .navigationBarsPadding()
            ) {
                SlideToBroadcastSos(
                    onSlideComplete = {
                        viewModel.broadcastDistressAlert(customMessageText, selectedPriority)
                        alertSentConfirmation = true
                    }
                )
            }
        }
    }

    // Confirmation Dialog
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Text(
                    text = "Confirm Emergency Broadcast",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary
                )
            },
            text = {
                Text(
                    text = "This will trigger a high-volume acoustic alert siren on all connected mesh devices.",
                    fontSize = 15.sp,
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val messageToBroadcast = customMessageText.ifBlank {
                            "आपातकालीन चेतावनी: तत्काल सहायता आवश्यक है।"
                        }
                        viewModel.broadcastDistressAlert(
                            customMessage = messageToBroadcast,
                            priority = selectedPriority
                        )
                        showConfirmDialog = false
                        alertSentConfirmation = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.error),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Confirm Broadcast", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showConfirmDialog = false },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cancel", color = colors.textPrimary)
                }
            },
            containerColor = colors.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun SlideToBroadcastSos(
    onSlideComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MinimalColorsInstance
    var offsetX by remember { mutableFloatStateOf(0f) }
    var isConfirmed by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.5.dp, colors.error, RoundedCornerShape(16.dp))
            .padding(4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val thumbSizeDp = 48.dp
        val thumbSizePx = with(density) { thumbSizeDp.toPx() }
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val paddingPx = with(density) { 8.dp.toPx() }
        val maxDragPx = (containerWidthPx - thumbSizePx - paddingPx).coerceAtLeast(0f)

        val animatedOffsetX by animateFloatAsState(
            targetValue = if (isConfirmed) maxDragPx else offsetX,
            label = "SosDrag"
        )

        Text(
            text = "Slide to Broadcast Emergency SOS ➔",
            fontSize = if (maxWidth < 360.dp) 12.sp else 14.sp,
            fontWeight = FontWeight.Bold,
            color = colors.error,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = thumbSizeDp),
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false
        )

        Box(
            modifier = Modifier
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .size(thumbSizeDp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.error)
                .pointerInput(maxDragPx) {
                    if (maxDragPx > 0f) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (offsetX >= maxDragPx * 0.70f) {
                                    isConfirmed = true
                                    onSlideComplete()
                                } else {
                                    offsetX = 0f
                                }
                            },
                            onDragCancel = { offsetX = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                offsetX = (offsetX + dragAmount).coerceIn(0f, maxDragPx)
                            }
                        )
                    }
                }
                .testTag("slide_sos_handle"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Campaign,
                contentDescription = "Slide SOS",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
