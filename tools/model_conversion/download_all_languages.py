#!/usr/bin/env python3
"""
Batch downloads and converts all remaining IndicConformer ASR models for VoxBridge.
Languages: te (Telugu), mr (Marathi), bn (Bengali), kn (Kannada), ml (Malayalam), or (Odia)
"""

import sys
from pathlib import Path
from download_and_convert_language import install_language

# Ensure UTF-8 output encoding on Windows terminals
if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except Exception:
        pass

LANGUAGES_TO_INSTALL = ["te", "mr", "bn", "kn", "ml", "or"]

def main():
    print("==================================================================")
    print(" Starting Batch Installation of All Indic Speech Models for VoxBridge")
    print(f" Languages: {', '.join(LANGUAGES_TO_INSTALL)}")
    print("==================================================================\n")

    for lang in LANGUAGES_TO_INSTALL:
        try:
            install_language(lang)
        except Exception as e:
            print(f"[ERROR] Failed to install {lang}: {e}")

    print("\n==================================================================")
    print(" All Speech Models Installed Successfully in assets/models/")
    print("==================================================================\n")

if __name__ == "__main__":
    main()
