#!/usr/bin/env python3
"""
Downloads the English STT and TTS source models needed forVoxBridge:
  - STT: csukuangfj/sherpa-onnx-nemo-ctc-en-conformer-medium from Hugging Face
  - TTS: AI4Bharat/Indic-TTS v1-checkpoints-release en.zip from GitHub
"""
import os
import sys
import zipfile
import urllib.request
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parents[2]
DL_DIR = ROOT_DIR / "downloads" / "en"
DL_DIR.mkdir(parents=True, exist_ok=True)

STT_FILES = {
    "model.onnx": "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-ctc-en-conformer-medium/resolve/main/model.onnx",
    "model.int8.onnx": "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-ctc-en-conformer-medium/resolve/main/model.int8.onnx",
    "tokens.txt": "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-ctc-en-conformer-medium/resolve/main/tokens.txt",
}

TTS_ZIP_URL = "https://github.com/AI4Bharat/Indic-TTS/releases/download/v1-checkpoints-release/en.zip"

def download_file(url: str, dest_path: Path) -> None:
    dest_path.parent.mkdir(parents=True, exist_ok=True)
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    with urllib.request.urlopen(req) as response:
        length = response.headers.get('Content-Length')
        total_size = int(length) if length else 0
        if dest_path.exists() and total_size > 0 and dest_path.stat().st_size == total_size:
            print(f"Skipping {dest_path.name} (already verified: {dest_path.stat().st_size / 1e6:.1f} MB)")
            return
        elif dest_path.exists() and total_size > 0 and dest_path.stat().st_size != total_size:
            print(f"Size mismatch on {dest_path.name} ({dest_path.stat().st_size} != {total_size} bytes). Re-downloading...")
            dest_path.unlink(missing_ok=True)

        print(f"Downloading {dest_path.name} from {url}...")
        with open(dest_path, "wb") as out_file:
            downloaded = 0
            block_size = 1024 * 1024
            while True:
                buffer = response.read(block_size)
                if not buffer:
                    break
                out_file.write(buffer)
                downloaded += len(buffer)
                if total_size > 0:
                    percent = (downloaded / total_size) * 100
                    print(f"  Progress: {downloaded / 1e6:.1f} MB / {total_size / 1e6:.1f} MB ({percent:.1f}%)", end="\r")

    final_size = dest_path.stat().st_size
    if total_size > 0 and final_size != total_size:
        dest_path.unlink(missing_ok=True)
        raise IOError(f"Integrity check failed for {dest_path}: Expected {total_size} bytes, got {final_size} bytes.")
    print(f"\nSaved and verified {dest_path.name} ({final_size / 1e6:.1f} MB)")

def main():
    print("=== Downloading English STT Source Files ===")
    for filename, url in STT_FILES.items():
        dest = DL_DIR / filename
        download_file(url, dest)

    print("\n=== Downloading English TTS Source Archive ===")
    tts_zip = DL_DIR / "en.zip"
    download_file(TTS_ZIP_URL, tts_zip)


    extract_dir = DL_DIR / "tts_extracted"
    if not extract_dir.exists() or not (extract_dir / "en").exists():
        print(f"\nExtracting {tts_zip.name} to {extract_dir}...")
        extract_dir.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(tts_zip, "r") as zip_ref:
            zip_ref.extractall(extract_dir)
        print("Extraction complete.")
    else:
        print("TTS extracted folder already present.")

    print("\n=== ALL ENGLISH SOURCE DOWNLOADS COMPLETED SUCCESSFULLY! ===")

if __name__ == "__main__":
    main()
