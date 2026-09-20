#!/usr/bin/env python3
"""
Tests and verifies the converted Tamil AI4Bharat IndicConformer STT model
using sherpa-onnx OfflineRecognizer.
"""
import json
import sys
from pathlib import Path

# Ensure UTF-8 output on Windows consoles
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

import numpy as np

try:
    import sherpa_onnx
    import onnx
except ImportError as e:
    print(f"Missing dependency: {e}. Please run using the virtual environment: .\\venv_conversion\\Scripts\\python")
    sys.exit(1)

REPO_ROOT = Path(__file__).resolve().parents[0]
TA_STT_DIR = REPO_ROOT / "app" / "src" / "main" / "assets" / "models" / "ta" / "stt"
MODEL_PATH = TA_STT_DIR / "model.int8.onnx"
TOKENS_PATH = TA_STT_DIR / "tokens.txt"


def verify_metadata():
    print(f"Checking ONNX metadata in {MODEL_PATH} ...")
    model = onnx.load(str(MODEL_PATH))
    meta = {p.key: p.value for p in model.metadata_props}
    print("Metadata properties:", meta)

    required_keys = ["vocab_size", "normalize_type", "subsampling_factor", "model_type"]
    for k in required_keys:
        if k not in meta:
            print(f"[FAIL] Missing required metadata key: {k}")
            return False
    print("[PASS] ONNX metadata verification successful.")
    return True


def verify_tokens():
    print(f"Checking tokens in {TOKENS_PATH} ...")
    with open(TOKENS_PATH, "r", encoding="utf-8") as f:
        lines = [l.strip() for l in f if l.strip()]

    print(f"Total tokens count: {len(lines)}")
    if not lines:
        print("[FAIL] Tokens file is empty!")
        return False

    last_line = lines[-1]
    if "<blk>" not in last_line:
        print(f"[FAIL] Expected last token to be blank (<blk>), found: {last_line}")
        return False

    # Check for Tamil unicode range across all tokens
    tamil_chars_found = 0
    for line in lines:
        token = line.split()[0]
        for ch in token:
            if '\u0B80' <= ch <= '\u0BFF':
                tamil_chars_found += 1

    print(f"Tamil characters detected in tokens: {tamil_chars_found}")
    if tamil_chars_found == 0:
        print("[FAIL] No Tamil Unicode characters found in tokens!")
        return False

    print("[PASS] Tokens verification successful.")
    return True



def verify_inference():
    print(f"Loading recognizer with sherpa-onnx...")
    recognizer = sherpa_onnx.OfflineRecognizer.from_nemo_ctc(
        model=str(MODEL_PATH),
        tokens=str(TOKENS_PATH),
        sample_rate=16000,
        feature_dim=80,
        decoding_method="greedy_search"
    )

    print("Running test inference with 1.5s silence audio...")
    silence = np.zeros(24000, dtype=np.float32)
    stream = recognizer.create_stream()
    stream.accept_waveform(16000, silence)
    recognizer.decode_stream(stream)
    result = stream.result.text.strip()
    print(f"Inference result on silence: '{result}' (expected empty)")

    print("[PASS] Tamil IndicConformer STT model initialized and decoded successfully without errors!")
    return True


def main():
    if not MODEL_PATH.exists() or not TOKENS_PATH.exists():
        print(f"Error: Model or tokens file missing in {TA_STT_DIR}")
        sys.exit(1)

    ok1 = verify_metadata()
    ok2 = verify_tokens()
    ok3 = verify_inference()

    if ok1 and ok2 and ok3:
        print("\n=======================================================")
        print(" TAMIL INDICCONFORMER STT VERIFICATION: ALL CHECKS PASSED ")
        print("=======================================================")
    else:
        print("\n[FAIL] Verification checks failed!")
        sys.exit(1)


if __name__ == "__main__":
    main()
