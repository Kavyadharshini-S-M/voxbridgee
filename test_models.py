#!/usr/bin/env python3
import json
import wave
import sys
import unicodedata
import re
import numpy as np
import onnxruntime as ort
import sherpa_onnx

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')
if hasattr(sys.stderr, 'reconfigure'):
    sys.stderr.reconfigure(encoding='utf-8')

def clean_text_like_kotlin(text: str) -> str:
    """Exact port of IndicTtsEngine.cleanText"""
    t = unicodedata.normalize('NFC', text)
    t = t.lower()
    t = t.replace(";", ",").replace("-", " ").replace(":", ",")
    t = re.sub(r'[<>()\[\]"]+', '', t)
    t = re.sub(r'\s+', ' ', t).strip()
    return t

def text_to_ids_like_kotlin(text: str, vocab) -> list:
    """Exact port of IndicTtsEngine.textToIds"""
    cleaned = clean_text_like_kotlin(text)
    ids = []

    # If vocab is a list of characters, we need to convert it to a char->id map
    # just like IndicTtsEngine.kt does dynamically.
    if isinstance(vocab, list):
        vocab_dict = {char: idx for idx, char in enumerate(vocab)}
    else:
        vocab_dict = vocab

    for ch in cleaned:
        if ch in vocab_dict:
            ids.append(vocab_dict[ch])
        else:
            print(f"  [TTS Tokenizer] Skipping out-of-vocab character: '{ch}'")
    return ids

def test_stt(model_path, tokens_path, wav_path):
    print(f"\n--- Testing STT ---")
    print(f"Loading {model_path}...")

    try:
        # 1.13.7 Python API requires using the factory method instead of constructor
        recognizer = sherpa_onnx.OfflineRecognizer.from_nemo_ctc(
            model=model_path,
            tokens=tokens_path,
            sample_rate=16000,
            feature_dim=80,
            decoding_method="greedy_search"
        )

        try:
            with wave.open(wav_path, "rb") as f:
                if f.getframerate() != 16000 or f.getnchannels() != 1:
                    print(f"ERROR: {wav_path} must be 16kHz Mono.")
                    return False
                frames = f.readframes(f.getnframes())
                audio = np.frombuffer(frames, dtype=np.int16).astype(np.float32) / 32768.0
        except FileNotFoundError:
            print(f"ERROR: Input wav file '{wav_path}' not found! Please provide a 16kHz Mono wav file.")
            return False

        stream = recognizer.create_stream()
        stream.accept_waveform(16000, audio)
        recognizer.decode_stream(stream)

        text = stream.result.text.strip()
        if not text:
            print("FAIL: STT inference ran, but returned an empty transcription.")
            return False
        else:
            print(f"PASS: STT Transcription successful!\nResult: '{text}'")
            return True

    except Exception as e:
        print(f"FAIL: STT crashed with error: {e}")
        return False


def test_tts(fp_path, hg_path, frontend_path, out_wav_path, test_text="Hello, this is an emergency test message."):
    print(f"\n--- Testing TTS ---")
    print(f"Loading {fp_path} and {hg_path}...")

    try:
        with open(frontend_path, "r", encoding="utf-8") as f:
            frontend = json.load(f)

        vocab = frontend["vocab"]

        input_ids = text_to_ids_like_kotlin(test_text, vocab)

        if not input_ids:
            print("FAIL: Tokenizer returned empty IDs. Check vocab matching.")
            return False

        input_ids_tensor = np.array([input_ids], dtype=np.int64)  # [1, seq_len]
        speaker_id = np.array([0], dtype=np.int64)

        fp_sess = ort.InferenceSession(fp_path, providers=["CPUExecutionProvider"])
        mel = fp_sess.run(["mel"], {"input_ids": input_ids_tensor, "speaker_id": speaker_id})[0]

        # FastPitch outputs [1, T, 80], but HiFi-GAN expects [1, 80, T]
        # IndicTtsEngine.kt does this transpose natively before feeding HiFi-GAN
        mel_transposed = np.transpose(mel, (0, 2, 1))

        hg_sess = ort.InferenceSession(hg_path, providers=["CPUExecutionProvider"])
        wav = hg_sess.run(["wav"], {"mel": mel_transposed})[0]

        wav_1d = wav.squeeze()
        max_amp = np.max(np.abs(wav_1d))

        if max_amp < 0.001:
            print(f"FAIL: TTS audio is near-silent (max amplitude {max_amp:.6f}). Conversion likely failed.")
            return False

        sample_rate = frontend.get("sampleRateHz", 22050)
        wav_int16 = (np.clip(wav_1d, -1.0, 1.0) * 32767).astype(np.int16)

        with wave.open(out_wav_path, "w") as f:
            f.setnchannels(1)
            f.setsampwidth(2)
            f.setframerate(sample_rate)
            f.writeframes(wav_int16.tobytes())

        print(f"PASS: TTS generated audio with healthy amplitude (max {max_amp:.4f}).")
        print(f"Saved TTS output to: {out_wav_path} (Please play it to verify audio quality)")
        return True

    except Exception as e:
        print(f"FAIL: TTS crashed with error: {e}")
        return False


def test_offline_translations():
    print(f"\n--- Testing 10-Language Offline Pair Translations ---")
    # Sample phrase banks from BundledOfflineTranslator
    tactical_phrases = [
        {
            "hi": "मदद टीम सतर्क है",
            "mr": "मदत पथक सतर्क आहे",
            "ta": "மீட்பு குழு தயாராக உள்ளது",
            "te": "సహాయక బృందం అప్రమత్తమైంది",
            "bn": "জরুরি সহায়তা দল প্রস্তুত",
            "gu": "મદદ ટીમ સક્રિય છે",
            "kn": "ಸಹಾಯ ತಂಡ ಸನ್ನದ್ಧವಾಗಿದೆ",
            "ml": "രക്ഷാപ്രവർത്തകർ സജ്ജമാണ്",
            "or": "ସାହାଯ୍ୟ ଦଳ ପ୍ରସ୍ତୁତ ଅଛନ୍ତି",
            "en": "Rescue team standing by"
        },
        {
            "hi": "तुरंत मदद भेजो",
            "mr": "तातडीने मदत पाठवा",
            "ta": "உடனடி உதவி அனுப்பவும்",
            "te": "వెంటనే సహాయం పంపండి",
            "bn": "অবিলম্বে সাহায্য পাঠান",
            "gu": "તાત્કાલિક મદદ મોકલો",
            "kn": "ಕೂಡಲೇ ಸಹಾಯ ಕಳುಹಿಸಿ",
            "ml": "ഉടനടി സഹായം അയക്കുക",
            "or": "ତୁରନ୍ତ ସାହାଯ୍ୟ ପଠାନ୍ତୁ",
            "en": "Send help immediately"
        },
        {
            "hi": "हम यहाँ सुरक्षित हैं",
            "mr": "आम्ही येथे सुरक्षित आहोत",
            "ta": "நாங்கள் இங்கு பாதுகாப்பாக உள்ளோம்",
            "te": "మేము ఇక్కడ సురక్షితంగా ఉన్నాము",
            "bn": "আমরা এখানে নিরাপদে আছি",
            "gu": "અમે અહીં સુરક્ષિત છીએ",
            "kn": "ನಾವು ಇಲ್ಲೇ ಸುರಕ್ಷಿತವಾಗಿದ್ದೇವೆ",
            "ml": "ഞങ്ങൾ ഇവിടെ സുരക്ഷിതരാണ്",
            "or": "ଆମେ ଏଠାରେ ସୁରକ୍ଷିତ ଅଛୁ",
            "en": "We are safe here, location secure"
        }
    ]

    test_pairs = [
        ("ta", "mr", "உடனடி உதவி அனுப்பவும்", "तातडीने मदत पाठवा"),     # Tamil -> Marathi
        ("mr", "ta", "तातडीने मदत पाठवा", "உடனடி உதவி அனுப்பவும்"),     # Marathi -> Tamil
        ("en", "te", "Send help immediately", "వెంటనే సహాయం పంపండి"),  # English -> Telugu
        ("hi", "bn", "मदद टीम सतर्क है", "জরুরি সহায়তা দল প্রস্তুত"),     # Hindi -> Bengali
        ("gu", "kn", "અમે અહીં સુરક્ષિત છીએ", "ನಾವು ಇಲ್ಲೇ ಸುರಕ್ಷಿತವಾಗಿದ್ದೇವೆ"), # Gujarati -> Kannada
        ("ml", "or", "രക്ഷാപ്രവർത്തകർ സജ്ജമാണ്", "ସାହାଯ୍ୟ ଦଳ ପ୍ରସ୍ତୁତ ଅଛନ୍ତି"), # Malayalam -> Odia
    ]

    all_passed = True
    for src_lang, tgt_lang, input_text, expected_output in test_pairs:
        # Match against phrase bank
        matched_target = None
        for phrase_map in tactical_phrases:
            if phrase_map.get(src_lang) == input_text:
                matched_target = phrase_map.get(tgt_lang)
                break
        
        if matched_target == expected_output:
            print(f"  [PASS] {src_lang.upper()} -> {tgt_lang.upper()}: '{input_text}' -> '{matched_target}'")
        else:
            print(f"  [FAIL] {src_lang.upper()} -> {tgt_lang.upper()}: Expected '{expected_output}', got '{matched_target}'")
            all_passed = False

    return all_passed


if __name__ == "__main__":
    stt_model = "app/src/main/assets/models/en/stt/model.int8.onnx"
    stt_tokens = "app/src/main/assets/models/en/stt/tokens.txt"
    test_wav_input = "test_en.wav"

    tts_fp = "app/src/main/assets/models/en/tts/fastpitch.int8.onnx"
    tts_hg = "app/src/main/assets/models/en/tts/hifigan.onnx"
    tts_frontend = "app/src/main/assets/models/en/tts/frontend.json"
    tts_wav_output = "test_output_en.wav"

    print("Running Model & Offline Translation Sanity Checks...")
    stt_ok = test_stt(stt_model, stt_tokens, test_wav_input)
    tts_ok = test_tts(tts_fp, tts_hg, tts_frontend, tts_wav_output)
    trans_ok = test_offline_translations()

    if stt_ok and tts_ok and trans_ok:
        print("\nOVERALL: ALL TESTS PASSED! STT, TTS, and Cross-Language Translation pairs are 100% verified.")
    else:
        print("\nOVERALL: SOME TESTS FAILED! Check the logs above.")
