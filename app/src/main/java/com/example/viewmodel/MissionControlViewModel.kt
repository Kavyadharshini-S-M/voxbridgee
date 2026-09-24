package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AlertAudioManager
import com.example.data.ITantraDatabase
import com.example.data.PreferenceManager
import com.example.data.VoiceMessageEntity
import com.example.data.VoiceMessageRepository
import com.example.model.AlertPriority
import com.example.model.ConnectionStatus
import com.example.model.MissionTelemetry
import com.example.model.PeerDevice
import com.example.model.RadioChannelState
import com.example.model.SupportedLanguage
import com.example.model.TransportProtocol
import com.example.model.VadStatus
import com.example.stt.IndicSttEngine
import com.example.stt.SttEngine
import com.example.stt.SttModelInfo
import com.example.transport.MeshService
import com.example.transport.NetworkPacket
import com.example.transport.TacticalMeshTransport
import com.example.transport.TransportLayer
import com.example.translation.BundledOfflineTranslator
import com.example.tts.IndicTtsEngine
import com.example.tts.TtsEngine
import com.example.tts.TtsModelInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class MissionUiState(
    val selectedLanguage: SupportedLanguage = SupportedLanguage.HINDI,
    val isPttActive: Boolean = true, // true = Push-to-Talk (Walkie-Talkie); false = Phone Mode (Continuous VAD)
    val deviceRole: String = "TRANSCEIVER", // "TRANSCEIVER", "STT_ONLY", "TTS_ONLY"
    val channelState: RadioChannelState = RadioChannelState.STANDBY,
    val currentTranscript: String = "",
    val activeIncomingCaption: String? = null,
    val activeIncomingIsAlert: Boolean = false,
    val forceMaxVolumeAlerts: Boolean = true,
    val isLowPowerListeningEnabled: Boolean = true,
    val showArmDistressDialog: Boolean = false,
    val directIpInput: String = "",
    val themeMode: String = "system", // "system", "light", "dark"
    val customDeviceName: String = "",
    val isFieldModeEnabled: Boolean = false,
    val hardwareKeyRemap: String = "volume_down", // "none", "volume_down"
    val isAudioChirpEnabled: Boolean = true
)

class MissionControlViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    // Audio & Infrastructure Engines
    val alertAudioManager = AlertAudioManager(context)
    val tacticalAlertManager = com.example.audio.TacticalAlertManager(context)
    val acousticPairingManager = com.example.audio.AcousticPairingManager(context, viewModelScope)
    val ultrasonicTransceiver = acousticPairingManager // Alias for backwards compatibility
    private var lastSynthesizedSpeech: Pair<String, SupportedLanguage>? = null
    // Shared across both engines: each would otherwise construct its own
    // BundledModelManager and independently SHA-256-verify every bundled model on
    // startup — with 9 languages that's ~2GB hashed twice in parallel instead of once.
    private val sharedModelManager = com.example.model.BundledModelManager(context)
    val preferenceManager = PreferenceManager(context)
    val sttEngine: SttEngine = IndicSttEngine(context, viewModelScope, sharedModelManager)
    val ttsEngine: TtsEngine = IndicTtsEngine(context, alertAudioManager, viewModelScope, sharedModelManager)
    val neuralTranslator = com.example.translation.IndicTrans2Translator(context, sharedModelManager)
    
    // Transport is now managed by MeshService to survive in the background
    val transportLayer: TransportLayer = MeshService.transportInstance 
        ?: TacticalMeshTransport(context, viewModelScope) // Fallback for safety

    // Room Persistence
    private val database = ITantraDatabase.getInstance(context)
    val repository = VoiceMessageRepository(database.voiceMessageDao())

    val messageLogs: StateFlow<List<VoiceMessageEntity>> = repository.allMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val alertCount: StateFlow<Int> = repository.alertCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // UI State
    private val _uiState = MutableStateFlow(
        MissionUiState(
            selectedLanguage = SupportedLanguage.fromCode(preferenceManager.getDefaultLanguage())
        )
    )
    val uiState: StateFlow<MissionUiState> = _uiState.asStateFlow()

    // Engine State flows exposed to UI
    val isListening: StateFlow<Boolean> = sttEngine.isListening
    val vadStatus: StateFlow<VadStatus> = sttEngine.vadStatus
    val speechProbability: StateFlow<Float> = sttEngine.speechProbability
    val audioLevel: StateFlow<Float> = sttEngine.audioLevel
    val partialTranscript: StateFlow<String> = sttEngine.partialTranscript
    val sttModelInfo: StateFlow<SttModelInfo> = sttEngine.modelInfo

    val isTtsSpeaking: StateFlow<Boolean> = ttsEngine.isSpeaking
    val ttsPlayingText: StateFlow<String?> = ttsEngine.currentlyPlayingText
    val ttsPlayingCaption: StateFlow<String?> = ttsEngine.currentlyPlayingText
    val ttsModelInfo: StateFlow<TtsModelInfo> = ttsEngine.modelInfo

    // Acoustic Sound-Wave State flows
    val isAcousticEmitting: StateFlow<Boolean> = acousticPairingManager.isEmitting
    val isAcousticListening: StateFlow<Boolean> = acousticPairingManager.isListening
    val acousticDetectedPayload: StateFlow<String?> = acousticPairingManager.detectedPayload
    val acousticSignalEnergy: StateFlow<Float> = acousticPairingManager.acousticSignalEnergy

    val isUltrasonicEmitting: StateFlow<Boolean> = isAcousticEmitting
    val isUltrasonicListening: StateFlow<Boolean> = isAcousticListening
    val ultrasonicDetectedPayload: StateFlow<String?> = acousticDetectedPayload
    val ultrasonicSignalEnergy: StateFlow<Float> = acousticSignalEnergy

    val bundledModelManager = sharedModelManager
    val languagePacks: StateFlow<Map<String, com.example.model.LanguagePack>> = bundledModelManager.languagePacks
    val isManifestLoaded: StateFlow<Boolean> = bundledModelManager.isManifestLoaded
    val verifiedAssets: StateFlow<Map<String, com.example.model.VerifiedAsset>> = bundledModelManager.verifiedAssets

    /** Forces a real reload of a language's on-device models and plays a sample voice test. */
    fun runModelBenchmark(languageCode: String) {
        val lang = SupportedLanguage.fromCode(languageCode)
        viewModelScope.launch {
            (sttEngine as? IndicSttEngine)?.reloadAndBenchmark(lang)
            testLanguageVoice(lang)
        }
    }

    fun testLanguageVoice(lang: SupportedLanguage) {
        val speech = when (lang) {
            SupportedLanguage.HINDI -> "आई-तंत्र सिस्टम सक्रिय है। ऑडियो ट्रांसमिशन चालू है।"
            SupportedLanguage.ENGLISH -> "iTantra offline voice system is active and ready."
            SupportedLanguage.BENGALI -> "আই-তন্ত্র সিস্টেম সক্রিয় রয়েছে। অডিও ট্রান্সমিশন চলছে।"
            SupportedLanguage.TELUGU -> "ఐ-తంత్ర వ్యవస్థ చురుకుగా ఉంది। ఆడియో ప్రసారం పనిచేస్తోంది।"
            SupportedLanguage.TAMIL -> "ஐ-தந்திர அமைப்பு செயல்பாட்டில் உள்ளது. ஆடியோ இயங்குகிறது."
            SupportedLanguage.MARATHI -> "आय-तंत्र प्रणाली सक्रिय आहे. ऑडिओ प्रक्षेपण सुरू आहे."
            SupportedLanguage.GUJARATI -> "આઈ-તંત્ર સિસ્ટમ સક્રિય છે. ઑડિયો ટ્રાન્સમિશન ચાલુ છે."
            SupportedLanguage.KANNADA -> "ಐ-ತಂತ್ರ ವ್ಯವಸ್ಥೆಯು ಸಕ್ರಿಯವಾಗಿದೆ. ಆಡಿಯೊ ಪ್ರಸಾರ ಕಾರ್ಯನಿರ್ವಹಿಸುತ್ತಿದೆ."
            SupportedLanguage.MALAYALAM -> "ഐ-തന്ത്ര സിസ്റ്റം സജീവമാണ്. ഓഡിയോ സംപ്രേക്ഷണം പ്രവർത്തിക്കുന്നു."
            SupportedLanguage.ODIA -> "ଆଇ-ତନ୍ତ୍ର ସିଷ୍ଟମ୍ ସକ୍ରିୟ ଅଛି। ଅଡିଓ ପ୍ରସାରଣ ଚାଲୁଅଛି।"
        }
        ttsEngine.speak(
            text = speech,
            language = lang,
            isAlert = false
        )
    }

    val connectionStatus: StateFlow<ConnectionStatus> = transportLayer.connectionStatus
    val activeProtocol: StateFlow<TransportProtocol> = transportLayer.activeProtocol
    val discoveredPeers: StateFlow<List<PeerDevice>> = transportLayer.discoveredPeers
    val connectedPeer: StateFlow<PeerDevice?> = transportLayer.connectedPeer
    val connectedPeers: StateFlow<List<PeerDevice>> = transportLayer.connectedPeers
    val telemetry: StateFlow<MissionTelemetry> = transportLayer.telemetry

    init {
        // Collect partial live transcript from STT Engine in real-time
        viewModelScope.launch {
            sttEngine.partialTranscript.collect { partial ->
                if (partial.isNotBlank()) {
                    _uiState.value = _uiState.value.copy(currentTranscript = partial)
                }
            }
        }

        // Collect finalized voice utterances from STT Engine
        viewModelScope.launch {
            sttEngine.finalizedUtterances.collect { utterance ->
                if (utterance.text.isNotBlank()) {
                    transmitUtterance(
                        text = utterance.text,
                        language = utterance.language,
                        isAlert = false,
                        priority = AlertPriority.ROUTINE
                    )
                }
                // In continuous phone mode, resume listening automatically
                if (!_uiState.value.isPttActive && _uiState.value.deviceRole != "TTS_ONLY") {
                    sttEngine.startListening(_uiState.value.selectedLanguage)
                    _uiState.value = _uiState.value.copy(channelState = RadioChannelState.LISTENING)
                }
            }
        }

        // Collect incoming tactical mesh packets from remote device
        viewModelScope.launch {
            transportLayer.incomingPackets.collect { packet ->
                handleIncomingPacket(packet)
            }
        }

        // Warm the initially-selected language's TTS models in the background at
        // startup rather than waiting for the first speak() call — moves the cold
        // model-load cost off the interactive path (Test voice / first incoming
        // message) since it can overlap with the user just looking at the UI.
        ttsEngine.preload(_uiState.value.selectedLanguage)
        viewModelScope.launch { neuralTranslator.initialize() }

        // Load custom preferences
        val customName = preferenceManager.getCustomDeviceName() ?: ""
        _uiState.value = _uiState.value.copy(
            customDeviceName = customName,
            isFieldModeEnabled = preferenceManager.isFieldModeEnabled(),
            hardwareKeyRemap = preferenceManager.getHardwareKeyRemap(),
            isAudioChirpEnabled = preferenceManager.isAudioChirpEnabled()
        )
        if (customName.isNotBlank()) {
            (transportLayer as? TacticalMeshTransport)?.let {
                it.telemetry.value // Ensure telemetry is updated
            }
            updateTransportCallsign(customName)
        }
    }

    // ==========================================
    // Role & Mode Configuration
    // ==========================================

    fun setDeviceRole(role: String) {
        _uiState.value = _uiState.value.copy(deviceRole = role)
        if (role == "TTS_ONLY") {
            sttEngine.stopListening()
            _uiState.value = _uiState.value.copy(channelState = RadioChannelState.STANDBY)
        } else if (!_uiState.value.isPttActive) {
            // In Phone mode and not TTS only, start listening
            sttEngine.startListening(_uiState.value.selectedLanguage)
            _uiState.value = _uiState.value.copy(channelState = RadioChannelState.LISTENING)
        }
    }

    fun setDirectIpInput(ip: String) {
        _uiState.value = _uiState.value.copy(directIpInput = ip)
    }

    fun connectDirectIp(ip: String, port: Int = 8889) {
        if (ip.isNotBlank()) {
            transportLayer.connectDirectIp(ip.trim(), port)
        }
    }

    // ==========================================
    // Push-To-Talk & Voice Capture Management
    // ==========================================

    fun onPttPressed() {
        if (_uiState.value.deviceRole == "TTS_ONLY") return
        if (_uiState.value.channelState == RadioChannelState.RECEIVING) return

        _uiState.value = _uiState.value.copy(
            channelState = RadioChannelState.TRANSMITTING,
            currentTranscript = "Listening..."
        )
        if (_uiState.value.isAudioChirpEnabled) {
            viewModelScope.launch { alertAudioManager.playPttChirpOpen() }
        }
        sttEngine.startListening(_uiState.value.selectedLanguage)
    }

    fun onPttReleased() {
        if (_uiState.value.deviceRole == "TTS_ONLY") return
        if (_uiState.value.channelState == RadioChannelState.TRANSMITTING) {
            sttEngine.stopListening()
            if (_uiState.value.isAudioChirpEnabled) {
                viewModelScope.launch { alertAudioManager.playPttChirpClose() }
            }
            _uiState.value = _uiState.value.copy(
                channelState = RadioChannelState.STANDBY,
                currentTranscript = if (_uiState.value.currentTranscript == "Listening...") "" else _uiState.value.currentTranscript
            )
        }
    }

    fun togglePttMode(isPtt: Boolean) {
        _uiState.value = _uiState.value.copy(isPttActive = isPtt)
        if (!isPtt && _uiState.value.deviceRole != "TTS_ONLY") {
            // Continuous Phone Mode: VAD listens continuously and segments sentences automatically
            sttEngine.startListening(_uiState.value.selectedLanguage)
            _uiState.value = _uiState.value.copy(channelState = RadioChannelState.LISTENING)
        } else {
            // Push-to-Talk Mode
            sttEngine.stopListening()
            _uiState.value = _uiState.value.copy(channelState = RadioChannelState.STANDBY)
        }
    }

    fun setSelectedLanguage(lang: SupportedLanguage) {
        _uiState.value = _uiState.value.copy(selectedLanguage = lang)
        preferenceManager.setDefaultLanguage(lang.code)
        sttEngine.setLanguage(lang)
        ttsEngine.preload(lang)
    }

    fun setForceMaxVolumeAlerts(force: Boolean) {
        _uiState.value = _uiState.value.copy(forceMaxVolumeAlerts = force)
    }

    fun setLowPowerListeningEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isLowPowerListeningEnabled = enabled)
    }

    fun setFieldModeEnabled(enabled: Boolean) {
        preferenceManager.setFieldModeEnabled(enabled)
        _uiState.value = _uiState.value.copy(isFieldModeEnabled = enabled)
    }

    fun setHardwareKeyRemap(mode: String) {
        preferenceManager.setHardwareKeyRemap(mode)
        _uiState.value = _uiState.value.copy(hardwareKeyRemap = mode)
    }

    fun setAudioChirpEnabled(enabled: Boolean) {
        preferenceManager.setAudioChirpEnabled(enabled)
        _uiState.value = _uiState.value.copy(isAudioChirpEnabled = enabled)
    }

    fun setThemeMode(mode: String) {
        _uiState.value = _uiState.value.copy(themeMode = mode)
    }

    fun setCustomDeviceName(name: String) {
        preferenceManager.setCustomDeviceName(name)
        _uiState.value = _uiState.value.copy(customDeviceName = name)
        updateTransportCallsign(name)
    }

    private fun updateTransportCallsign(name: String) {
        (transportLayer as? TacticalMeshTransport)?.setCustomCallsign(if (name.isBlank()) null else name)
    }

    // ==========================================
    // Transmission & Reception
    // ==========================================

    private fun transmitUtterance(
        text: String,
        language: SupportedLanguage,
        isAlert: Boolean,
        priority: AlertPriority
    ) {
        viewModelScope.launch {
            val packet = NetworkPacket(
                packetId = "PKT_${UUID.randomUUID().toString().take(8)}",
                senderId = "MY_NODE_ALPHA",
                senderCallsign = telemetry.value.nodeCallsign,
                text = text,
                languageCode = language.code,
                isAlert = isAlert,
                alertPriority = priority,
                timestamp = System.currentTimeMillis(),
                channelFreq = telemetry.value.frequencyGhz
            )

            // Stream packet over real sockets (TCP / Bluetooth / UDP)
            transportLayer.sendPacket(packet)

            // Persist into Room mission log
            repository.logMessage(
                VoiceMessageEntity(
                    messageUid = packet.packetId,
                    text = packet.text,
                    senderCallsign = packet.senderCallsign,
                    isLocal = true,
                    languageCode = packet.languageCode,
                    timestamp = packet.timestamp,
                    isAlert = packet.isAlert,
                    alertPriority = packet.alertPriority.name,
                    audioDurationSec = 2.4f,
                    hasPlayed = true
                )
            )

            _uiState.value = _uiState.value.copy(
                currentTranscript = text,
                channelState = if (!_uiState.value.isPttActive) RadioChannelState.LISTENING else RadioChannelState.STANDBY
            )
        }
    }

    private fun handleIncomingPacket(packet: NetworkPacket) {
        viewModelScope.launch {
            val incomingLang = SupportedLanguage.fromCode(packet.languageCode)
            val receiverSelectedLang = _uiState.value.selectedLanguage

            // Translate into receiver device's active selected language using AI4Bharat IndicTrans2
            val translation = if (incomingLang == receiverSelectedLang) {
                com.example.translation.IndicTrans2Translator.TranslationResult(
                    translatedText = packet.text,
                    sourceLanguage = incomingLang,
                    targetLanguage = receiverSelectedLang,
                    isNeuralTranslation = false
                )
            } else {
                neuralTranslator.translate(
                    text = packet.text,
                    source = incomingLang,
                    target = receiverSelectedLang
                )
            }
            val speechText = translation.translatedText
            val speechLang = receiverSelectedLang

            val captionDisplay = if (incomingLang != receiverSelectedLang) {
                "${translation.translatedText} (${incomingLang.nativeName} ➔ ${receiverSelectedLang.nativeName})"
            } else {
                translation.translatedText
            }

            _uiState.value = _uiState.value.copy(
                channelState = RadioChannelState.RECEIVING,
                activeIncomingCaption = captionDisplay,
                activeIncomingIsAlert = packet.isAlert
            )

            // Save to Room DB: store translated text in receiver's language with original snippet
            val storedText = if (incomingLang != receiverSelectedLang) {
                "${translation.translatedText} [${incomingLang.nativeName}: ${packet.text}]"
            } else {
                packet.text
            }

            val rowId = repository.logMessage(
                VoiceMessageEntity(
                    messageUid = packet.packetId,
                    text = storedText,
                    senderCallsign = packet.senderCallsign,
                    isLocal = false,
                    languageCode = receiverSelectedLang.code,
                    timestamp = packet.timestamp,
                    isAlert = packet.isAlert,
                    alertPriority = packet.alertPriority.name,
                    audioDurationSec = 3.2f,
                    hasPlayed = false
                )
            )

            // Dispatch Multi-Sensory Alert (Flashlight Strobe + Haptic Vibration)
            tacticalAlertManager.dispatchIncomingAlert(isAlert = packet.isAlert, scope = viewModelScope)
            lastSynthesizedSpeech = Pair(speechText, speechLang)

            // If device is in STT_ONLY mode, do not play aloud over speaker
            if (_uiState.value.deviceRole == "STT_ONLY") {
                repository.markAsPlayed(rowId)
                _uiState.value = _uiState.value.copy(
                    channelState = if (!_uiState.value.isPttActive) RadioChannelState.LISTENING else RadioChannelState.STANDBY,
                    activeIncomingCaption = captionDisplay,
                    activeIncomingIsAlert = packet.isAlert
                )
                return@launch
            }

            // CRITICAL: Mute STT recording while TTS plays over speaker to prevent acoustic feedback loop
            sttEngine.stopListening()

            // Play voice note / alert speech through phone speaker in receiver's SELECTED language!
            ttsEngine.speak(
                text = speechText,
                language = speechLang,
                isAlert = packet.isAlert,
                onDone = {
                    viewModelScope.launch {
                        repository.markAsPlayed(rowId)
                        delay(600) // Allow speaker reverb to dissipate
                        _uiState.value = _uiState.value.copy(
                            channelState = if (!_uiState.value.isPttActive && _uiState.value.deviceRole != "TTS_ONLY") RadioChannelState.LISTENING else RadioChannelState.STANDBY,
                            activeIncomingCaption = null,
                            activeIncomingIsAlert = false
                        )
                        if (!_uiState.value.isPttActive && _uiState.value.deviceRole != "TTS_ONLY") {
                            sttEngine.startListening(_uiState.value.selectedLanguage)
                        }
                    }
                }
            )
        }
    }

    /**
     * Replays the last synthesized voice packet over the phone speaker.
     */
    fun replayLastMessage() {
        val last = lastSynthesizedSpeech ?: return
        viewModelScope.launch {
            sttEngine.stopListening()
            ttsEngine.speak(
                text = last.first,
                language = last.second,
                isAlert = false,
                onDone = {
                    if (!_uiState.value.isPttActive && _uiState.value.deviceRole != "TTS_ONLY") {
                        sttEngine.startListening(_uiState.value.selectedLanguage)
                    }
                }
            )
        }
    }

    /**
     * Directly bypasses STT and broadcasts hardcoded quick-action tactical commands over the mesh network.
     */
    fun sendTacticalQuickAction(
        actionTitle: String,
        isAlert: Boolean = false,
        priority: AlertPriority = AlertPriority.ROUTINE
    ) {
        val lang = _uiState.value.selectedLanguage
        transmitUtterance(
            text = actionTitle,
            language = lang,
            isAlert = isAlert,
            priority = priority
        )
    }

    /**
     * Transmits a manual typed text message over the tactical mesh network in the user's preferred language.
     */
    fun sendTextMessage(text: String, isAlert: Boolean = false) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        val lang = _uiState.value.selectedLanguage
        transmitUtterance(
            text = trimmed,
            language = lang,
            isAlert = isAlert,
            priority = if (isAlert) AlertPriority.URGENT else AlertPriority.ROUTINE
        )
    }

    // ==========================================
    // Priority Distress Alert Broadcast
    // ==========================================

    fun broadcastDistressAlert(
        customMessage: String?,
        priority: AlertPriority = AlertPriority.CRITICAL_DISTRESS
    ) {
        val lang = _uiState.value.selectedLanguage
        val defaultPhrase = when (priority) {
            AlertPriority.CRITICAL_DISTRESS -> lang.sampleAlertPhrase
            AlertPriority.URGENT -> when (lang) {
                SupportedLanguage.HINDI -> "चेतावनी: आगे मार्ग अवरुद्ध है। सावधानी बरतें।"
                SupportedLanguage.ENGLISH -> "URGENT ALERT: Hazard detected. Proceed with caution."
                SupportedLanguage.TAMIL -> "எச்சரிக்கை: அவசர உதவி தேவைப்படுகிறது. பாதுகாப்புடன் செல்லவும்."
                SupportedLanguage.TELUGU -> "హెచ్చరిక: అత్యవసర సహాయం అవసరం. జాగ్రత్తగా కొనసాగండి."
                SupportedLanguage.BENGALI -> "জরুরি সতর্কতা: সামনে বিপদ রয়েছে। সতর্ক থাকুন।"
                SupportedLanguage.MARATHI -> "तातडीचा इशारा: पुढे मार्ग बंद आहे. काळजी घ्या."
                SupportedLanguage.GUJARATI -> "તાકીદની ચેતવણી: આગળ રસ્તો બંધ છે. સાવચેતી રાખો."
                SupportedLanguage.KANNADA -> "ತುರ್ತು ಎಚ್ಚರಿಕೆ: ಮುಂದೆ ಅಪಾಯವಿದೆ. ಎಚ್ಚರಿಕೆ ವಹಿಸಿ."
                SupportedLanguage.MALAYALAM -> "അടിയന്തര മുന്നറിയിപ്പ്: അപകട സാധ്യതയുണ്ട്. ജാഗ്രത പാലിക്കുക."
                SupportedLanguage.ODIA -> "ଜରୁରୀ ସତର୍କତା: ଆଗରେ ବିପଦ ଅଛି। ସାବଧାନ ରୁହନ୍ତୁ।"
            }
            AlertPriority.ROUTINE -> when (lang) {
                SupportedLanguage.HINDI -> "स्थिति सामान्य: मेश ट्रांससीवर सक्रिय और सभी दल सुरक्षित हैं।"
                SupportedLanguage.ENGLISH -> "ROUTINE STATUS: All systems operational. Standing by."
                SupportedLanguage.TAMIL -> "நிலைமை சீரானது: நாங்கள் இங்கு பாதுகாப்பாக உள்ளோம்."
                SupportedLanguage.TELUGU -> "పరిస్థితి సాధారణం: మేము ఇక్కడ సురక్షితంగా ఉన్నాము."
                SupportedLanguage.BENGALI -> "পরিস্থিতি স্বাভাবিক: আমরা এখানে নিরাপদে আছি।"
                SupportedLanguage.MARATHI -> "स्थिती सामान्य: आम्ही येथे सुरक्षित आहोत."
                SupportedLanguage.GUJARATI -> "સ્થિતિ સામાન્ય: અમે અહીં સુરક્ષિત છીએ."
                SupportedLanguage.KANNADA -> "ಸ್ಥಿತಿ ಸಾಮಾನ್ಯ: ನಾವು ಇಲ್ಲೇ ಸುರಕ್ಷಿತವಾಗಿದ್ದೇವೆ."
                SupportedLanguage.MALAYALAM -> "സ്ഥിതി സാധാരണമാണ്: ഞങ്ങൾ ഇവിടെ സുരക്ഷിതരാണ്."
                SupportedLanguage.ODIA -> "ସ୍ଥିତି ସାଧାରଣ: ଆମେ ଏଠାରେ ସୁରକ୍ଷିତ ଅଛୁ।"
            }
        }
        val alertText = customMessage?.ifBlank { null } ?: defaultPhrase

        transmitUtterance(
            text = alertText,
            language = lang,
            isAlert = priority != AlertPriority.ROUTINE,
            priority = priority
        )

        // Haptic feedback & local notification
        alertAudioManager.triggerSosHaptics()
    }

    // ==========================================
    // Sound-Wave / Acoustic Pairing
    // ==========================================

    fun emitAcousticPairingSound(onComplete: (() -> Unit)? = null) {
        val localIp = telemetry.value.localIpAddress
        val callsign = telemetry.value.nodeCallsign
        acousticPairingManager.emitPairingSound(
            ip = localIp,
            port = 8889,
            deviceName = callsign,
            onComplete = onComplete
        )
    }

    fun startAcousticPairingListener(onAutoConnected: ((String) -> Unit)? = null) {
        acousticPairingManager.startListening { ip, port, peerName ->
            // Automatically connect to the discovered peer
            connectDirectIp(ip, port)
            onAutoConnected?.invoke(peerName)
        }
    }

    fun stopAcousticPairingListener() {
        acousticPairingManager.stopListening()
    }

    fun emitUltrasonicPairingChirp(onComplete: (() -> Unit)? = null) {
        emitAcousticPairingSound(onComplete)
    }

    fun startUltrasonicPairingListener(onAutoConnected: ((String) -> Unit)? = null) {
        startAcousticPairingListener(onAutoConnected)
    }

    fun stopUltrasonicPairingListener() {
        stopAcousticPairingListener()
    }

    // ==========================================
    // First-Time Setup & Onboarding
    // ==========================================

    fun isFirstLaunch(): Boolean {
        return !preferenceManager.isFirstLaunchCompleted()
    }

    fun completeFirstLaunch(language: SupportedLanguage, callsign: String) {
        preferenceManager.setDefaultLanguage(language.code)
        preferenceManager.setCustomDeviceName(callsign)
        preferenceManager.setFirstLaunchCompleted(true)
        setSelectedLanguage(language)
        setCustomDeviceName(callsign)
    }

    fun playVoiceMessage(message: VoiceMessageEntity) {
        viewModelScope.launch {
            val currentLang = _uiState.value.selectedLanguage
            val cleanText = message.text.substringBefore(" [")
            val messageLang = BundledOfflineTranslator.detectLanguage(cleanText)
                ?: SupportedLanguage.fromCode(message.languageCode)
            val translation = BundledOfflineTranslator.translate(
                text = cleanText,
                source = messageLang,
                target = currentLang
            )
            ttsEngine.speak(
                text = translation.translatedText,
                language = currentLang,
                isAlert = message.isAlert
            )
        }
    }

    fun testTtsAudio(customText: String? = null) {
        val lang = _uiState.value.selectedLanguage
        val speech = customText ?: when (lang) {
            SupportedLanguage.HINDI -> "आई-तंत्र सिस्टम सक्रिय है। ऑडियो ट्रांसमिशन चालू है।"
            SupportedLanguage.ENGLISH -> "iTantra system active. Speech synthesizer operational."
            SupportedLanguage.BENGALI -> "আই-তন্ত্র সিস্টেম সক্রিয় রয়েছে। অডিও ট্রান্সমিশন চলছে।"
            SupportedLanguage.TELUGU -> "ఐ-తంత్ర వ్యవస్థ చురుకుగా ఉంది. ఆడియో ప్రసారం పనిచేస్తోంది."
            SupportedLanguage.TAMIL -> "ஐ-தந்திர அமைப்பு செயல்பாட்டில் உள்ளது. ஆடியோ இயங்குகிறது."
            SupportedLanguage.MARATHI -> "आय-तंत्र प्रणाली सक्रिय आहे. ऑडिओ प्रक्षेपण सुरू आहे."
            SupportedLanguage.GUJARATI -> "આઈ-તંત્ર સિસ્ટમ સક્રિય છે. ઑડિયો ટ્રાન્સમિશન ચાલુ છે."
            SupportedLanguage.KANNADA -> "ಐ-ತಂತ್ರ ವ್ಯವಸ್ಥೆಯು ಸಕ್ರಿಯವಾಗಿದೆ. ಆಡಿಯೊ ಪ್ರಸಾರ ಕಾರ್ಯನಿರ್ವಹಿಸುತ್ತಿದೆ."
            SupportedLanguage.MALAYALAM -> "ഐ-തന്ത്ര സിസ്റ്റം സജീവമാണ്. ഓഡിയോ സംപ്രേക്ഷണം പ്രവർത്തിക്കുന്നു."
            SupportedLanguage.ODIA -> "ଆଇ-ତନ୍ତ୍ର ସିଷ୍ଟମ୍ ସକ୍ରିୟ ଅଛି। ଅଡିଓ ପ୍ରସାରଣ ଚାଲୁଅଛି।"
        }
        ttsEngine.speak(
            text = speech,
            language = lang,
            isAlert = false
        )
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearMissionLogs()
        }
    }

    // ==========================================
    // Pairing & Connection Controls
    // ==========================================

    fun scanForPeers(protocol: TransportProtocol) {
        transportLayer.startDiscovery(protocol)
    }

    fun connectToPeer(peer: PeerDevice) {
        transportLayer.connectToPeer(peer)
    }

    fun connectBluetooth(deviceAddress: String, deviceName: String = "Tactical BT Node") {
        val peer = PeerDevice(
            id = deviceAddress,
            name = deviceName,
            address = deviceAddress,
            protocol = TransportProtocol.BLUETOOTH,
            signalStrengthDbm = -55
        )
        transportLayer.connectToPeer(peer)
    }

    fun disconnectPeer() {
        viewModelScope.launch(Dispatchers.IO) {
            transportLayer.disconnect()
        }
    }

    fun switchProtocol(protocol: TransportProtocol) {
        transportLayer.setProtocol(protocol)
    }

    // Interactive Demo / Testing functions
    fun simulatePeerDistress() {
        transportLayer.triggerSimulatedPeerAlert(
            customText = "अलर्ट: इसरो मिशन नियंत्रण - तटीय क्षेत्रों में संचार टावर प्रभावित। उपग्रह बैकअप चालू किया गया।",
            priority = AlertPriority.CRITICAL_DISTRESS
        )
    }

    fun simulatePeerRoutineVoice() {
        transportLayer.triggerSimulatedPeerRoutineVoice(
            customText = "Ground station Alpha confirming telemetry signal 95% lock. All frequencies open."
        )
    }

    override fun onCleared() {
        super.onCleared()
        sttEngine.stopListening()
        ttsEngine.shutdown()
        transportLayer.disconnect()
        alertAudioManager.releaseAlertAudioFocus()
        alertAudioManager.stopHaptics()
    }
}
