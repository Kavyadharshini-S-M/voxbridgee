#!/usr/bin/env python3
"""
Downloads and converts AI4Bharat IndicConformer ASR model for Tamil (ta)
from AI4Bharat IndicConformer ASR exports (trysem/indicconformer-120m-onnx).

Produces:
  - app/src/main/assets/models/ta/stt/tokens.txt
  - app/src/main/assets/models/ta/stt/model.int8.onnx
Updates model_manifest.json for Tamil without touching Hindi.
"""

import json
import os
import sys
import urllib.request
from pathlib import Path

try:
    import onnx
    from onnxruntime.quantization import QuantType, quantize_dynamic
except ImportError:
    print("Please ensure onnx and onnxruntime are installed in your python environment.")

REPO_ROOT = Path(__file__).resolve().parents[2]
TA_STT_DIR = REPO_ROOT / "app" / "src" / "main" / "assets" / "models" / "ta" / "stt"
MANIFEST_PATH = REPO_ROOT / "app" / "src" / "main" / "assets" / "models" / "model_manifest.json"
TEMP_DIR = REPO_ROOT / "downloads" / "stt" / "ta"

HF_BASE = "https://huggingface.co/trysem/indicconformer-120m-onnx/resolve/main/ta"
VOCAB_URL = f"{HF_BASE}/vocab.json"
MODEL_URL = f"{HF_BASE}/model.onnx"

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
            print(f"[download] Already exists and verified ({dest.stat().st_size / 1e6:.2f} MB): {dest}")
            return
        elif dest.exists() and total_size > 0 and dest.stat().st_size != total_size:
            print(f"[download] Existing file size mismatch ({dest.stat().st_size} != {total_size} bytes). Re-downloading: {dest}")
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
        raise IOError(f"Integrity check failed for {dest}: Expected {total_size} bytes, got {final_size} bytes.")

    print(f"\n[download] Verified complete: {dest} ({final_size / 1e6:.2f} MB)")



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


def add_model_metadata(onnx_path: Path, vocab_size: int) -> None:
    model = onnx.load(str(onnx_path))
    meta = {
        "vocab_size": str(vocab_size + 1),
        "normalize_type": "per_feature",
        "subsampling_factor": str(SUBSAMPLING_FACTOR),
        "model_type": MODEL_TYPE,
        "version": "1",
        "model_author": "ai4bharat",
        "comment": "AI4Bharat IndicConformer Tamil (hybrid CTC/RNNT, CTC branch) - https://github.com/AI4Bharat/IndicConformerASR",
    }
    for key, value in meta.items():
        entry = model.metadata_props.add()
        entry.key = key
        entry.value = value
    onnx.save(model, str(onnx_path))
    print(f"[metadata] wrote {meta} into {onnx_path}")


def quantize(onnx_path: Path, out_path: Path) -> None:
    print(f"[quantize] Quantizing {onnx_path} -> {out_path} ...")
    quantize_dynamic(
        model_input=str(onnx_path),
        model_output=str(out_path),
        per_channel=True,
        weight_type=QuantType.QUInt8,
    )
    fp32_mb = onnx_path.stat().st_size / 1e6
    int8_mb = out_path.stat().st_size / 1e6
    print(f"[quantize] {onnx_path.name}: {fp32_mb:.1f}MB -> {out_path.name}: {int8_mb:.1f}MB")


def update_manifest():
    if not MANIFEST_PATH.exists():
        print(f"Error: manifest not found at {MANIFEST_PATH}")
        return

    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    if "languages" not in manifest or "ta" not in manifest["languages"]:
        print("Error: Tamil entry not found in manifest")
        return

    ta_entry = manifest["languages"]["ta"]
    # Update Tamil STT configuration to use dedicated Tamil IndicConformer model
    ta_entry["asr"] = {
        "engine": "IndicSttEngine",
        "model": "AI4Bharat IndicConformer (nemo_ctc)",
        "size_mb": 135.0,
        "license": "MIT / AI4Bharat",
        "offline": True,
        "model_file": "ta/stt/model.int8.onnx",
        "tokens_file": "ta/stt/tokens.txt",
        "sampleRateHz": 16000,
        "featureDim": 80
    }

    # Update manifest root counts if needed
    manifest["languages"]["ta"] = ta_entry
    MANIFEST_PATH.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    print("[manifest] Successfully updated Tamil ASR configuration in model_manifest.json (Hindi untouched)")


def main():
    TEMP_DIR.mkdir(parents=True, exist_ok=True)
    TA_STT_DIR.mkdir(parents=True, exist_ok=True)

    vocab_file = TEMP_DIR / "vocab.json"
    model_file = TEMP_DIR / "model.onnx"

    download_file(VOCAB_URL, vocab_file)
    download_file(MODEL_URL, model_file)

    tokens_out = TA_STT_DIR / "tokens.txt"
    vocab_size = build_tokens_txt(vocab_file, tokens_out)
    print(f"[Tamil] tokens.txt created with {vocab_size} tokens at {tokens_out}")

    add_model_metadata(model_file, vocab_size)

    int8_out = TA_STT_DIR / "model.int8.onnx"
    quantize(model_file, int8_out)

    update_manifest()
    print("\n[SUCCESS] Tamil IndicConformer ASR model setup completed efficiently!")


if __name__ == "__main__":
    main()
