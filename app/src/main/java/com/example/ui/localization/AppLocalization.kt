package com.example.ui.localization

import com.example.model.SupportedLanguage

/**
 * AppLocalization: Comprehensive multi-language strings with dual English labels.
 * Provides primary native translation with English sub-labels for non-literate and layman emergency usage.
 */
object AppLocalization {

    data class BilingualText(
        val nativeText: String,
        val englishLabel: String
    ) {
        val displayCombined: String get() = "$nativeText ($englishLabel)"
    }

    fun getTabWalkie(lang: SupportedLanguage): BilingualText = when (lang) {
        SupportedLanguage.HINDI -> BilingualText("वॉकी", "Walkie")
        SupportedLanguage.BENGALI -> BilingualText("ওয়াকি", "Walkie")
        SupportedLanguage.MARATHI -> BilingualText("वॉकी", "Walkie")
        SupportedLanguage.TELUGU -> BilingualText("వాకీ", "Walkie")
        SupportedLanguage.TAMIL -> BilingualText("வாக்கி", "Walkie")
        SupportedLanguage.GUJARATI -> BilingualText("વૉકી", "Walkie")
        SupportedLanguage.KANNADA -> BilingualText("ವಾಕಿ", "Walkie")
        SupportedLanguage.MALAYALAM -> BilingualText("വാക്കി", "Walkie")
        SupportedLanguage.ODIA -> BilingualText("ୱାକି", "Walkie")
        SupportedLanguage.ENGLISH -> BilingualText("Walkie", "Radio")
    }

    fun getTabSos(lang: SupportedLanguage): BilingualText = when (lang) {
        SupportedLanguage.HINDI -> BilingualText("एसओएस", "SOS")
        SupportedLanguage.BENGALI -> BilingualText("এসওএস", "SOS")
        SupportedLanguage.MARATHI -> BilingualText("एसओएस", "SOS")
        SupportedLanguage.TELUGU -> BilingualText("ఎస్ఓఎస్", "SOS")
        SupportedLanguage.TAMIL -> BilingualText("எஸ்ஓஎஸ்", "SOS")
        SupportedLanguage.GUJARATI -> BilingualText("એસઓએસ", "SOS")
        SupportedLanguage.KANNADA -> BilingualText("ಎಸ್ಒಎಸ್", "SOS")
        SupportedLanguage.MALAYALAM -> BilingualText("എസ്ഒഎസ്", "SOS")
        SupportedLanguage.ODIA -> BilingualText("ଏସ୍ଓଏସ୍", "SOS")
        SupportedLanguage.ENGLISH -> BilingualText("SOS", "Alert")
    }

    fun getTabNearby(lang: SupportedLanguage): BilingualText = when (lang) {
        SupportedLanguage.HINDI -> BilingualText("आस-पास", "Nearby")
        SupportedLanguage.BENGALI -> BilingualText("কাছাকাছি", "Nearby")
        SupportedLanguage.MARATHI -> BilingualText("जवळपास", "Nearby")
        SupportedLanguage.TELUGU -> BilingualText("సమీపంలో", "Nearby")
        SupportedLanguage.TAMIL -> BilingualText("அருகில்", "Nearby")
        SupportedLanguage.GUJARATI -> BilingualText("નજીકમાં", "Nearby")
        SupportedLanguage.KANNADA -> BilingualText("ಹತ್ತಿರದ", "Nearby")
        SupportedLanguage.MALAYALAM -> BilingualText("അടുത്ത്", "Nearby")
        SupportedLanguage.ODIA -> BilingualText("ନିକଟସ୍ଥ", "Nearby")
        SupportedLanguage.ENGLISH -> BilingualText("Nearby", "Peers")
    }

    fun getTabChats(lang: SupportedLanguage): BilingualText = when (lang) {
        SupportedLanguage.HINDI -> BilingualText("चैट", "Chats")
        SupportedLanguage.BENGALI -> BilingualText("চ্যাট", "Chats")
        SupportedLanguage.MARATHI -> BilingualText("गप्पा", "Chats")
        SupportedLanguage.TELUGU -> BilingualText("చాట్స్", "Chats")
        SupportedLanguage.TAMIL -> BilingualText("அரட்டை", "Chats")
        SupportedLanguage.GUJARATI -> BilingualText("વાતચીત", "Chats")
        SupportedLanguage.KANNADA -> BilingualText("ಸಂಭಾಷಣೆ", "Chats")
        SupportedLanguage.MALAYALAM -> BilingualText("ചാറ്റ്", "Chats")
        SupportedLanguage.ODIA -> BilingualText("ଚାଟ୍", "Chats")
        SupportedLanguage.ENGLISH -> BilingualText("Chats", "Logs")
    }

    fun getTabSettings(lang: SupportedLanguage): BilingualText = when (lang) {
        SupportedLanguage.HINDI -> BilingualText("सेटिंग्स", "Settings")
        SupportedLanguage.BENGALI -> BilingualText("সেটিংস", "Settings")
        SupportedLanguage.MARATHI -> BilingualText("सेटिंग्ज", "Settings")
        SupportedLanguage.TELUGU -> BilingualText("సెట్టింగ్స్", "Settings")
        SupportedLanguage.TAMIL -> BilingualText("அமைப்புகள்", "Settings")
        SupportedLanguage.GUJARATI -> BilingualText("સેટિંગ્સ", "Settings")
        SupportedLanguage.KANNADA -> BilingualText("ಸೆಟ್ಟಿಂಗ್‌ಗಳು", "Settings")
        SupportedLanguage.MALAYALAM -> BilingualText("ക്രമീകരണങ്ങൾ", "Settings")
        SupportedLanguage.ODIA -> BilingualText("ସେଟିଙ୍ଗ୍ସ", "Settings")
        SupportedLanguage.ENGLISH -> BilingualText("Settings", "Config")
    }

    fun getMyRadio(lang: SupportedLanguage): BilingualText = when (lang) {
        SupportedLanguage.HINDI -> BilingualText("मेरा रेडियो", "My Radio")
        SupportedLanguage.BENGALI -> BilingualText("আমার রেডিও", "My Radio")
        SupportedLanguage.MARATHI -> BilingualText("माझे रेडिओ", "My Radio")
        SupportedLanguage.TELUGU -> BilingualText("నా రేడియో", "My Radio")
        SupportedLanguage.TAMIL -> BilingualText("என் வானொலி", "My Radio")
        SupportedLanguage.GUJARATI -> BilingualText("મારું રેડિયો", "My Radio")
        SupportedLanguage.KANNADA -> BilingualText("ನನ್ನ ರೇಡಿಯೋ", "My Radio")
        SupportedLanguage.MALAYALAM -> BilingualText("എന്റെ റേഡിയോ", "My Radio")
        SupportedLanguage.ODIA -> BilingualText("ମୋର ରେଡିଓ", "My Radio")
        SupportedLanguage.ENGLISH -> BilingualText("My Radio", "Local Node")
    }

    fun getPttWalkieTalkie(lang: SupportedLanguage): BilingualText = when (lang) {
        SupportedLanguage.HINDI -> BilingualText("वॉकी-टॉकी", "Walkie Talkie")
        SupportedLanguage.BENGALI -> BilingualText("ওয়াকি-টকি", "Walkie Talkie")
        SupportedLanguage.MARATHI -> BilingualText("वॉकी-टॉकी", "Walkie Talkie")
        SupportedLanguage.TELUGU -> BilingualText("వాకీ టాకీ", "Walkie Talkie")
        SupportedLanguage.TAMIL -> BilingualText("வாக்கி டாக்கி", "Walkie Talkie")
        SupportedLanguage.GUJARATI -> BilingualText("વૉકી ટૉકી", "Walkie Talkie")
        SupportedLanguage.KANNADA -> BilingualText("ವಾಕಿ ಟಾಕಿ", "Walkie Talkie")
        SupportedLanguage.MALAYALAM -> BilingualText("വാക്കി ടോക്കി", "Walkie Talkie")
        SupportedLanguage.ODIA -> BilingualText("ୱାକି ଟକି", "Walkie Talkie")
        SupportedLanguage.ENGLISH -> BilingualText("Walkie Talkie", "PTT Mode")
    }

    fun getPhoneMode(lang: SupportedLanguage): BilingualText = when (lang) {
        SupportedLanguage.HINDI -> BilingualText("फ़ोन मोड", "Phone Mode")
        SupportedLanguage.BENGALI -> BilingualText("ফোন মোড", "Phone Mode")
        SupportedLanguage.MARATHI -> BilingualText("फोन मोड", "Phone Mode")
        SupportedLanguage.TELUGU -> BilingualText("ఫోన్ మోడ్", "Phone Mode")
        SupportedLanguage.TAMIL -> BilingualText("போன் பயன்முறை", "Phone Mode")
        SupportedLanguage.GUJARATI -> BilingualText("ફોન મોડ", "Phone Mode")
        SupportedLanguage.KANNADA -> BilingualText("ಫೋನ್ ಮೋಡ್", "Phone Mode")
        SupportedLanguage.MALAYALAM -> BilingualText("ഫോൺ മോഡ്", "Phone Mode")
        SupportedLanguage.ODIA -> BilingualText("ଫୋନ୍ ମୋଡ୍", "Phone Mode")
        SupportedLanguage.ENGLISH -> BilingualText("Phone Mode", "Hands-Free")
    }

    fun getActionDoctor(lang: SupportedLanguage): BilingualText = when (lang) {
        SupportedLanguage.HINDI -> BilingualText("डॉक्टर / सहायता", "Doctor / First Aid")
        SupportedLanguage.BENGALI -> BilingualText("ডাক্তার / চিকিৎসা", "Doctor / First Aid")
        SupportedLanguage.MARATHI -> BilingualText("डॉक्टर / प्रथमोपचार", "Doctor / First Aid")
        SupportedLanguage.TELUGU -> BilingualText("డాక్టర్ / వైద్యం", "Doctor / First Aid")
        SupportedLanguage.TAMIL -> BilingualText("மருத்துவர் / உதவி", "Doctor / First Aid")
        SupportedLanguage.GUJARATI -> BilingualText("ડૉક્ટર / સારવાર", "Doctor / First Aid")
        SupportedLanguage.KANNADA -> BilingualText("ವೈದ್ಯರು / ಚಿಕಿತ್ಸೆ", "Doctor / First Aid")
        SupportedLanguage.MALAYALAM -> BilingualText("ഡോക്ടർ / ചികിത്സ", "Doctor / First Aid")
        SupportedLanguage.ODIA -> BilingualText("ଡାକ୍ତର / ଚିକିତ୍ସା", "Doctor / First Aid")
        SupportedLanguage.ENGLISH -> BilingualText("Doctor", "First Aid")
    }

    fun getActionRoadBlocked(lang: SupportedLanguage): BilingualText = when (lang) {
        SupportedLanguage.HINDI -> BilingualText("रास्ता बंद", "Road Blocked")
        SupportedLanguage.BENGALI -> BilingualText("রাস্তা বন্ধ", "Road Blocked")
        SupportedLanguage.MARATHI -> BilingualText("रस्ता बंद", "Road Blocked")
        SupportedLanguage.TELUGU -> BilingualText("రహదారి మూసివేత", "Road Blocked")
        SupportedLanguage.TAMIL -> BilingualText("பாதை அடைப்பு", "Road Blocked")
        SupportedLanguage.GUJARATI -> BilingualText("રસ્તો બંધ", "Road Blocked")
        SupportedLanguage.KANNADA -> BilingualText("ರಸ್ತೆ ಬ್ಲಾಕ್", "Road Blocked")
        SupportedLanguage.MALAYALAM -> BilingualText("വഴി തടസ്സപ്പെട്ടു", "Road Blocked")
        SupportedLanguage.ODIA -> BilingualText("ରାସ୍ତା ବନ୍ଦ", "Road Blocked")
        SupportedLanguage.ENGLISH -> BilingualText("Road Blocked", "Hazard")
    }

    fun getActionNeedWater(lang: SupportedLanguage): BilingualText = when (lang) {
        SupportedLanguage.HINDI -> BilingualText("पानी चाहिए", "Need Water")
        SupportedLanguage.BENGALI -> BilingualText("পানি প্রয়োজন", "Need Water")
        SupportedLanguage.MARATHI -> BilingualText("पाणी हवे", "Need Water")
        SupportedLanguage.TELUGU -> BilingualText("నీరు కావాలి", "Need Water")
        SupportedLanguage.TAMIL -> BilingualText("தண்ணீர் தேவை", "Need Water")
        SupportedLanguage.GUJARATI -> BilingualText("પાણી જોઈએ", "Need Water")
        SupportedLanguage.KANNADA -> BilingualText("ನೀರು ಬೇಕು", "Need Water")
        SupportedLanguage.MALAYALAM -> BilingualText("വെള്ളം വേണം", "Need Water")
        SupportedLanguage.ODIA -> BilingualText("ପାଣି ଦରକାର", "Need Water")
        SupportedLanguage.ENGLISH -> BilingualText("Need Water", "Supplies")
    }

    fun getActionSafe(lang: SupportedLanguage): BilingualText = when (lang) {
        SupportedLanguage.HINDI -> BilingualText("मैं सुरक्षित हूँ", "I am Safe")
        SupportedLanguage.BENGALI -> BilingualText("আমি নিরাপদে আছি", "I am Safe")
        SupportedLanguage.MARATHI -> BilingualText("मी सुरक्षित आहे", "I am Safe")
        SupportedLanguage.TELUGU -> BilingualText("నేను సురక్షితంగా ఉన్నాను", "I am Safe")
        SupportedLanguage.TAMIL -> BilingualText("நான் பாதுகாப்பாக உள்ளேன்", "I am Safe")
        SupportedLanguage.GUJARATI -> BilingualText("હું સુરક્ષિત છું", "I am Safe")
        SupportedLanguage.KANNADA -> BilingualText("ನಾನು ಸುರಕ್ಷಿತವಾಗಿದ್ದೇನೆ", "I am Safe")
        SupportedLanguage.MALAYALAM -> BilingualText("ഞാൻ സുരക്ഷിതനാണ്", "I am Safe")
        SupportedLanguage.ODIA -> BilingualText("ମୁଁ ସୁରକ୍ଷିତ ଅଛି", "I am Safe")
        SupportedLanguage.ENGLISH -> BilingualText("I am Safe", "Status OK")
    }

    fun getEmergencyAlert(lang: SupportedLanguage): BilingualText = when (lang) {
        SupportedLanguage.HINDI -> BilingualText("आपातकालीन अलर्ट", "Emergency Alert")
        SupportedLanguage.BENGALI -> BilingualText("জরুরি সতর্কতা", "Emergency Alert")
        SupportedLanguage.MARATHI -> BilingualText("आपत्कालीन इशारा", "Emergency Alert")
        SupportedLanguage.TELUGU -> BilingualText("అత్యవసర హెచ్చరిక", "Emergency Alert")
        SupportedLanguage.TAMIL -> BilingualText("அவசர எச்சரிக்கை", "Emergency Alert")
        SupportedLanguage.GUJARATI -> BilingualText("કટોકટી ચેતવણી", "Emergency Alert")
        SupportedLanguage.KANNADA -> BilingualText("ತುರ್ತು ಎಚ್ಚರಿಕೆ", "Emergency Alert")
        SupportedLanguage.MALAYALAM -> BilingualText("അടിയന്തര മുന്നറിയിപ്പ്", "Emergency Alert")
        SupportedLanguage.ODIA -> BilingualText("ଜରୁରୀକାଳୀନ ସତର୍କତା", "Emergency Alert")
        SupportedLanguage.ENGLISH -> BilingualText("Emergency Alert", "Distress SOS")
    }

    // Hazard Types
    fun getHazardMedical(lang: SupportedLanguage): BilingualText = when (lang) {
        SupportedLanguage.HINDI -> BilingualText("चिकित्सा", "Medical")
        SupportedLanguage.BENGALI -> BilingualText("চিকিৎসা", "Medical")
        SupportedLanguage.MARATHI -> BilingualText("वैद्यकीय", "Medical")
        SupportedLanguage.TELUGU -> BilingualText("వైద్యం", "Medical")
        SupportedLanguage.TAMIL -> BilingualText("மருத்துவம்", "Medical")
        SupportedLanguage.GUJARATI -> BilingualText("તબીબી", "Medical")
        SupportedLanguage.KANNADA -> BilingualText("ವೈದ್ಯಕೀಯ", "Medical")
        SupportedLanguage.MALAYALAM -> BilingualText("വൈദ്യം", "Medical")
        SupportedLanguage.ODIA -> BilingualText("ଡାକ୍ତରୀ", "Medical")
        SupportedLanguage.ENGLISH -> BilingualText("Medical", "First Aid")
    }

    fun getHazardFire(lang: SupportedLanguage): BilingualText = when (lang) {
        SupportedLanguage.HINDI -> BilingualText("आग", "Fire")
        SupportedLanguage.BENGALI -> BilingualText("আগুন", "Fire")
        SupportedLanguage.MARATHI -> BilingualText("आग", "Fire")
        SupportedLanguage.TELUGU -> BilingualText("అగ్ని", "Fire")
        SupportedLanguage.TAMIL -> BilingualText("தீ", "Fire")
        SupportedLanguage.GUJARATI -> BilingualText("આગ", "Fire")
        SupportedLanguage.KANNADA -> BilingualText("ಬೆಂಕಿ", "Fire")
        SupportedLanguage.MALAYALAM -> BilingualText("തീ", "Fire")
        SupportedLanguage.ODIA -> BilingualText("ନିଆଁ", "Fire")
        SupportedLanguage.ENGLISH -> BilingualText("Fire", "Rescue")
    }

    fun getHazardFlood(lang: SupportedLanguage): BilingualText = when (lang) {
        SupportedLanguage.HINDI -> BilingualText("बाढ़", "Flood")
        SupportedLanguage.BENGALI -> BilingualText("বন্যা", "Flood")
        SupportedLanguage.MARATHI -> BilingualText("पूर", "Flood")
        SupportedLanguage.TELUGU -> BilingualText("వరద", "Flood")
        SupportedLanguage.TAMIL -> BilingualText("வெள்ளம்", "Flood")
        SupportedLanguage.GUJARATI -> BilingualText("પૂર", "Flood")
        SupportedLanguage.KANNADA -> BilingualText("ಪ್ರವಾಹ", "Flood")
        SupportedLanguage.MALAYALAM -> BilingualText("പ്രളയം", "Flood")
        SupportedLanguage.ODIA -> BilingualText("ବନ୍ୟା", "Flood")
        SupportedLanguage.ENGLISH -> BilingualText("Flood", "Water")
    }

    fun getHazardCollapse(lang: SupportedLanguage): BilingualText = when (lang) {
        SupportedLanguage.HINDI -> BilingualText("भवन ढहना", "Collapse")
        SupportedLanguage.BENGALI -> BilingualText("ভবন ধস", "Collapse")
        SupportedLanguage.MARATHI -> BilingualText("इमारत कोसळणे", "Collapse")
        SupportedLanguage.TELUGU -> BilingualText("కూలిపోవడం", "Collapse")
        SupportedLanguage.TAMIL -> BilingualText("கட்டட இடிவு", "Collapse")
        SupportedLanguage.GUJARATI -> BilingualText("મકાન ધરાશાયી", "Collapse")
        SupportedLanguage.KANNADA -> BilingualText("ಕಟ್ಟಡ ಕುಸಿತ", "Collapse")
        SupportedLanguage.MALAYALAM -> BilingualText("തകർച്ച", "Collapse")
        SupportedLanguage.ODIA -> BilingualText("ଭବନ ଭୁଶୁଡ଼ିବା", "Collapse")
        SupportedLanguage.ENGLISH -> BilingualText("Collapse", "Debris")
    }

    fun getHazardCyclone(lang: SupportedLanguage): BilingualText = when (lang) {
        SupportedLanguage.HINDI -> BilingualText("चक्रवात", "Cyclone")
        SupportedLanguage.BENGALI -> BilingualText("ঘূর্ণিঝড়", "Cyclone")
        SupportedLanguage.MARATHI -> BilingualText("चक्रीवादळ", "Cyclone")
        SupportedLanguage.TELUGU -> BilingualText("తుఫాను", "Cyclone")
        SupportedLanguage.TAMIL -> BilingualText("புயல்", "Cyclone")
        SupportedLanguage.GUJARATI -> BilingualText("વાવાઝોડું", "Cyclone")
        SupportedLanguage.KANNADA -> BilingualText("ಚಂಡಮಾರುತ", "Cyclone")
        SupportedLanguage.MALAYALAM -> BilingualText("ചുഴലിക്കാറ്റ്", "Cyclone")
        SupportedLanguage.ODIA -> BilingualText("ବାତ୍ୟା", "Cyclone")
        SupportedLanguage.ENGLISH -> BilingualText("Cyclone", "Storm")
    }

    fun getHazardDanger(lang: SupportedLanguage): BilingualText = when (lang) {
        SupportedLanguage.HINDI -> BilingualText("खतरा", "Danger")
        SupportedLanguage.BENGALI -> BilingualText("বিপদ", "Danger")
        SupportedLanguage.MARATHI -> BilingualText("धोका", "Danger")
        SupportedLanguage.TELUGU -> BilingualText("ప్రమాదం", "Danger")
        SupportedLanguage.TAMIL -> BilingualText("ஆபத்து", "Danger")
        SupportedLanguage.GUJARATI -> BilingualText("ખતરો", "Danger")
        SupportedLanguage.KANNADA -> BilingualText("ಅಪಾಯ", "Danger")
        SupportedLanguage.MALAYALAM -> BilingualText("അപകടം", "Danger")
        SupportedLanguage.ODIA -> BilingualText("ବିପଦ", "Danger")
        SupportedLanguage.ENGLISH -> BilingualText("Danger", "Warning")
    }

    // SOS Screen Controls
    fun getInstantSos(lang: SupportedLanguage): BilingualText = when (lang) {
        SupportedLanguage.HINDI -> BilingualText("तत्काल एसओएस भेजें", "1-Tap Instant SOS")
        SupportedLanguage.BENGALI -> BilingualText("তাত্ক্ষণিক এসওএস পাঠান", "1-Tap Instant SOS")
        SupportedLanguage.MARATHI -> BilingualText("त्वरित एसओएस पाठवा", "1-Tap Instant SOS")
        SupportedLanguage.TELUGU -> BilingualText("వెంటనే ఎస్ఓఎస్ పంపండి", "1-Tap Instant SOS")
        SupportedLanguage.TAMIL -> BilingualText("உடனடி அவசர உதவி", "1-Tap Instant SOS")
        SupportedLanguage.GUJARATI -> BilingualText("તાત્કાલિક એસઓએસ મોકલો", "1-Tap Instant SOS")
        SupportedLanguage.KANNADA -> BilingualText("ತಕ್ಷಣ ಎಸ್ಒಎಸ್ ಕಳುಹಿಸಿ", "1-Tap Instant SOS")
        SupportedLanguage.MALAYALAM -> BilingualText("ഉടൻ എസ്ഒഎസ് അയക്കുക", "1-Tap Instant SOS")
        SupportedLanguage.ODIA -> BilingualText("ତୁରନ୍ତ ଏସ୍ଓଏସ୍ ପଠାନ୍ତୁ", "1-Tap Instant SOS")
        SupportedLanguage.ENGLISH -> BilingualText("Instant SOS", "1-Tap Broadcast")
    }

    fun getHoldToSos(lang: SupportedLanguage): BilingualText = when (lang) {
        SupportedLanguage.HINDI -> BilingualText("दबाकर रखें (३ सेकंड)", "Hold to SOS (3s)")
        SupportedLanguage.BENGALI -> BilingualText("চেপে ধরে রাখুন (৩ সে)", "Hold to SOS (3s)")
        SupportedLanguage.MARATHI -> BilingualText("दाबून ठेवा (३ सेकंद)", "Hold to SOS (3s)")
        SupportedLanguage.TELUGU -> BilingualText("నొక్కి పట్టుకోండి (3సె)", "Hold to SOS (3s)")
        SupportedLanguage.TAMIL -> BilingualText("அழுத்திப் பிடிக்கவும் (3வி)", "Hold to SOS (3s)")
        SupportedLanguage.GUJARATI -> BilingualText("દબાવી રાખો (૩ સેકન્ડ)", "Hold to SOS (3s)")
        SupportedLanguage.KANNADA -> BilingualText("ಒತ್ತಿ ಹಿಡಿಯಿರಿ (3ಸೆ)", "Hold to SOS (3s)")
        SupportedLanguage.MALAYALAM -> BilingualText("അമർത്തിപ്പിടിക്കുക (3സെ)", "Hold to SOS (3s)")
        SupportedLanguage.ODIA -> BilingualText("ଚାପି ଧରନ୍ତୁ (୩ ସେକେଣ୍ଡ)", "Hold to SOS (3s)")
        SupportedLanguage.ENGLISH -> BilingualText("Hold to SOS", "3 Seconds")
    }

    fun getShakeToSosPrompt(lang: SupportedLanguage): BilingualText = when (lang) {
        SupportedLanguage.HINDI -> BilingualText("हिलाकर एसओएस भेजें (सक्रिय)", "Shake to SOS (Active)")
        SupportedLanguage.BENGALI -> BilingualText("নাড়িয়ে এসওএস পাঠান", "Shake to SOS (Active)")
        SupportedLanguage.MARATHI -> BilingualText("फोन हलवून एसओएस पाठवा", "Shake to SOS (Active)")
        SupportedLanguage.TELUGU -> BilingualText("ఫోన్ ఊపి ఎస్ఓఎస్ పంపండి", "Shake to SOS (Active)")
        SupportedLanguage.TAMIL -> BilingualText("போனை குலுக்கி உதவி பெறுங்கள்", "Shake to SOS (Active)")
        SupportedLanguage.GUJARATI -> BilingualText("ફોન હલાવીને એસઓએસ મોકલો", "Shake to SOS (Active)")
        SupportedLanguage.KANNADA -> BilingualText("ಫೋನ್ ಅಲ್ಲಾಡಿಸಿ ಎಸ್ಒಎಸ್ ಕಳುಹಿಸಿ", "Shake to SOS (Active)")
        SupportedLanguage.MALAYALAM -> BilingualText("ഫോൺ കുലുക്കി എസ്ഒഎസ് അയക്കുക", "Shake to SOS (Active)")
        SupportedLanguage.ODIA -> BilingualText("ଫୋନ୍ ହଲାଇ ଏସ୍ଓଏସ୍ ପଠାନ୍ତୁ", "Shake to SOS (Active)")
        SupportedLanguage.ENGLISH -> BilingualText("Shake-to-SOS Active", "Shake 3x")
    }
}
