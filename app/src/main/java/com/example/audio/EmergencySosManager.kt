package com.example.audio

import android.content.Context
import com.example.model.AlertPriority
import com.example.model.SupportedLanguage

/**
 * EmergencySosManager: Manages tactical SOS triggers, hold duration, and localized TTS speech dispatches.
 *
 * Requirements:
 * - 1500ms (1.5s) hold-to-trigger duration.
 * - TTS Audio Output: Does NOT speak raw coordinates out loud. Speaks "Emergency alert sent! Location attached."
 * - Silently attaches GPS coordinates into packet payload.
 */
class EmergencySosManager(private val context: Context) {

    companion object {
        const val HOLD_TRIGGER_DURATION_MS = 1500L

        /**
         * Returns localized emergency confirmation speech string without raw GPS coordinates.
         */
        fun getLocalizedAlertSpeech(lang: SupportedLanguage): String {
            return when (lang) {
                SupportedLanguage.HINDI -> "आपातकालीन चेतावनी भेजी गई! स्थान संलग्न है।"
                SupportedLanguage.ENGLISH -> "Emergency alert sent! Location attached."
                SupportedLanguage.TAMIL -> "அவசர எச்சரிக்கை அனுப்பப்பட்டது! இருப்பிடம் இணைக்கப்பட்டுள்ளது."
                SupportedLanguage.TELUGU -> "అత్యవసర హెచ్చరిక పంపబడింది! స్థానం జతచేయబడింది."
                SupportedLanguage.BENGALI -> "জরুরি সতর্কতা পাঠানো হয়েছে! অবস্থান সংযুক্ত করা হয়েছে।"
                SupportedLanguage.MARATHI -> "आणीबाणीचा इशारा पाठवला! स्थान जोडले आहे."
                SupportedLanguage.GUJARATI -> "કટોકટી ચેતવણી મોકલાઈ! સ્થાન જોડાયેલ છે."
                SupportedLanguage.KANNADA -> "ತುರ್ತು ಎಚ್ಚರಿಕೆ ಕಳುಹಿಸಲಾಗಿದೆ! ಸ್ಥಳ ಲಗತ್ತಿಸಲಾಗಿದೆ."
                SupportedLanguage.MALAYALAM -> "അടിയന്തര മുന്നറിയിപ്പ് അയച്ചു! ലൊക്കേഷൻ ചേർത്തു."
                SupportedLanguage.ODIA -> "ଜରୁରୀକାଳୀନ ସତର୍କତା ପଠାଗଲା! ସ୍ଥାନ ସଂଲଗ୍ନ ହୋଇଛି।"
            }
        }

        /**
         * Cleans any embedded GPS tag from text to ensure TTS never speaks raw numbers out loud.
         */
        fun stripGpsCoordinates(rawText: String): String {
            return rawText.replace(Regex("""\[GPS:\s*[^\]]+\]"""), "").trim()
        }
    }
}
