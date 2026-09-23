#!/usr/bin/env python3
"""
Universal Speech-to-Text Model Downloader and Converter for VoxBridge.
Downloads AI4Bharat IndicConformer ASR models for any specified language from HuggingFace,
applies subsampling and CTC metadata, quantizes to mobile INT8 ONNX, and installs directly into
app/src/main/assets/models/<lang>/stt/.

Supported languages:
  - gu (Gujarati)
  - te (Telugu)
  - mr (Marathi)
  - bn (Bengali)
  - kn (Kannada)
  - ml (Malayalam)
  - or (Odia)
  - ta (Tamil)
  - hi (Hindi)

Usage:
  python download_and_convert_language.py <language_code>
  e.g.: python download_and_convert_language.py gu
"""

import json
import os
import sys
import urllib.request
from pathlib import Path

# Ensure UTF-8 output encoding on Windows terminals
if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except Exception:
        pass

try:
    import onnx
    from onnxruntime.quantization import QuantType, quantize_dynamic
except ImportError:
    print("Error: onnx and onnxruntime are required. Run in venv_conversion.")
    sys.exit(1)

REPO_ROOT = Path(__file__).resolve().parents[2]
ASSETS_MODELS_DIR = REPO_ROOT / "app" / "src" / "main" / "assets" / "models"
MANIFEST_PATH = ASSETS_MODELS_DIR / "model_manifest.json"
TEMP_BASE_DIR = REPO_ROOT / "downloads" / "stt"

LANGUAGE_NAMES = {
    "gu": ("Gujarati", "ગુજરાતી"),
    "te": ("Telugu", "తెలుగు"),
    "mr": ("Marathi", "मराठी"),
    "bn": ("Bengali", "বাংলা"),
    "kn": ("Kannada", "ಕನ್ನಡ"),
    "ml": ("Malayalam", "മലയാളം"),
    "or": ("Odia", "ଓଡ଼ିଆ"),
    "ta": ("Tamil", "தமிழ்"),
    "hi": ("Hindi", "हिन्दी"),
}

SUBSAMPLING_FACTOR = 4
MODEL_TYPE = "EncDecHybridRNNTCTCBPEModel"


def download_file(url: str, dest: Path) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    req = urllib.request.Request(
        url,
        headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"}
    )
    with urllib.request.urlopen(req) as resp:
        total_size = int(resp.headers.get("Content-Length", 0))
        if dest.exists() and total_size > 0 and dest.stat().st_size == total_size:
            print(f"[download] Already exists and verified ({dest.stat().st_size / 1e6:.2f} MB): {dest.name}")
            return
        elif dest.exists() and total_size > 0 and dest.stat().st_size != total_size:
            print(f"[download] File size mismatch ({dest.stat().st_size} != {total_size} bytes). Re-downloading...")
            dest.unlink(missing_ok=True)

        print(f"[download] Downloading {url} -> {dest} ...")
        with open(dest, "wb") as f:
            downloaded = 0
            chunk_size = 1024 * 1024  # 1MB chunk
            while True:
                chunk = resp.read(chunk_size)
                if not chunk:
                    break
                f.write(chunk)
                downloaded += len(chunk)
                if total_size > 0:
                    percent = (downloaded / total_size) * 100
                    print(f"\r[download] {downloaded / 1e6:.1f} MB / {total_size / 1e6:.1f} MB ({percent:.1f}%)", end="", flush=True)
                else:
                    print(f"\r[download] {downloaded / 1e6:.1f} MB", end="", flush=True)

    final_size = dest.stat().st_size
    if total_size > 0 and final_size != total_size:
        dest.unlink(missing_ok=True)
        raise IOError(f"Integrity check failed: Expected {total_size} bytes, got {final_size} bytes.")

    print(f"\n[download] Download complete: {dest.name} ({final_size / 1e6:.2f} MB)")


def build_tokens_txt(vocab_path: Path, tokens_out: Path) -> int:
    vocab = json.loads(vocab_path.read_text(encoding="utf-8"))
    if not isinstance(vocab, list):
        raise ValueError(f"Expected {vocab_path} to be a JSON list of tokens, got {type(vocab)}")

    with tokens_out.open("w", encoding="utf-8") as f:
        for i, token in enumerate(vocab):
            f.write(f"{token} {i}\n")
        blank_id = len(vocab)
        f.write(f"<blk> {blank_id}\n")

    return len(vocab)


def add_model_metadata(onnx_path: Path, vocab_size: int, lang_name: str) -> None:
    model = onnx.load(str(onnx_path))
    meta = {
        "vocab_size": str(vocab_size + 1),
        "normalize_type": "per_feature",
        "subsampling_factor": str(SUBSAMPLING_FACTOR),
        "model_type": MODEL_TYPE,
        "version": "1",
        "model_author": "ai4bharat",
        "comment": f"AI4Bharat IndicConformer {lang_name} (hybrid CTC/RNNT, CTC branch) - https://github.com/AI4Bharat/IndicConformerASR",
    }
    for key, value in meta.items():
        entry = model.metadata_props.add()
        entry.key = key
        entry.value = value
    onnx.save(model, str(onnx_path))
    print(f"[metadata] Added sherpa-onnx metadata to {onnx_path.name}")


def quantize(onnx_path: Path, out_path: Path) -> None:
    print(f"[quantize] Quantizing {onnx_path.name} -> {out_path.name} (INT8) ...")
    quantize_dynamic(
        model_input=str(onnx_path),
        model_output=str(out_path),
        per_channel=True,
        weight_type=QuantType.QUInt8,
    )
    fp32_mb = onnx_path.stat().st_size / 1e6
    int8_mb = out_path.stat().st_size / 1e6
    print(f"[quantize] Compressed: {fp32_mb:.1f} MB -> {int8_mb:.1f} MB")


def update_manifest(lang_code: str, lang_english: str, lang_native: str):
    if not MANIFEST_PATH.exists():
        print(f"Error: manifest not found at {MANIFEST_PATH}")
        return

    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    if "languages" not in manifest:
        manifest["languages"] = {}

    entry = manifest["languages"].get(lang_code, {
        "name": lang_english,
        "native_name": lang_native,
    })

    entry["name"] = lang_english
    entry["native_name"] = lang_native
    entry["asr"] = {
        "engine": "IndicSttEngine",
        "model": f"AI4Bharat IndicConformer {lang_english} (nemo_ctc)",
        "size_mb": 135.0,
        "license": "MIT / AI4Bharat",
        "offline": True,
        "model_file": f"{lang_code}/stt/model.int8.onnx",
        "tokens_file": f"{lang_code}/stt/tokens.txt",
        "sampleRateHz": 16000,
        "featureDim": 80
    }

    manifest["languages"][lang_code] = entry
    MANIFEST_PATH.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"[manifest] Updated {lang_english} ({lang_code}) in model_manifest.json")


def install_language(lang_code: str):
    lang_code = lang_code.strip().lower()
    if lang_code not in LANGUAGE_NAMES:
        print(f"Language '{lang_code}' not recognized. Supported: {list(LANGUAGE_NAMES.keys())}")
        sys.exit(1)

    lang_english, lang_native = LANGUAGE_NAMES[lang_code]
    print(f"\n=======================================================")
    print(f"  Installing Speech-to-Text Model: {lang_english} ({lang_native}) [{lang_code}]")
    print(f"=======================================================\n")

    temp_dir = TEMP_BASE_DIR / lang_code
    target_stt_dir = ASSETS_MODELS_DIR / lang_code / "stt"

    temp_dir.mkdir(parents=True, exist_ok=True)
    target_stt_dir.mkdir(parents=True, exist_ok=True)

    hf_base = f"https://huggingface.co/trysem/indicconformer-120m-onnx/resolve/main/{lang_code}"
    vocab_url = f"{hf_base}/vocab.json"
    model_url = f"{hf_base}/model.onnx"

    vocab_file = temp_dir / "vocab.json"
    model_file = temp_dir / "model.onnx"

    download_file(vocab_url, vocab_file)
    download_file(model_url, model_file)

    tokens_out = target_stt_dir / "tokens.txt"
    vocab_size = build_tokens_txt(vocab_file, tokens_out)
    print(f"[tokens] Created tokens.txt ({vocab_size} tokens) at {tokens_out}")

    add_model_metadata(model_file, vocab_size, lang_english)

    int8_out = target_stt_dir / "model.int8.onnx"
    quantize(model_file, int8_out)

    update_manifest(lang_code, lang_english, lang_native)
    print(f"\n[SUCCESS] {lang_english} speech model is installed and ready in assets/models/{lang_code}/stt/!\n")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python download_and_convert_language.py <lang_code> (e.g. gu, te, mr, bn, kn, ml, or)")
        sys.exit(1)
    install_language(sys.argv[1])
