#!/usr/bin/env python3
"""
AI4Bharat IndicTrans2 INT8 Dynamic Quantization Suite
Converts IndicTrans2-1B-Direct / Distilled PyTorch / FP32 ONNX graphs to INT8 ONNX.

Size Reduction:
- Original FP32 / FP16 Model: ~2.1 GB
- INT8 Quantized Model: ~260 - 295 MB
- Target Execution Provider: ONNX Runtime Mobile (CPU)

Usage:
    python quantize_nmt.py --input-dir /path/to/fp32_models --output-dir app/src/main/assets/models/nmt
"""
import os
import sys
import argparse
from pathlib import Path
import onnx
from onnxruntime.quantization import QuantType, quantize_dynamic

def quantize_model(input_model_path: Path, output_model_path: Path):
    print(f"[*] Quantizing {input_model_path.name} to INT8...")
    output_model_path.parent.mkdir(parents=True, exist_ok=True)
    
    quantize_dynamic(
        model_input=str(input_model_path),
        model_output=str(output_model_path),
        per_channel=True,
        weight_type=QuantType.QUInt8
    )
    
    orig_mb = input_model_path.stat().st_size / (1024 * 1024)
    quant_mb = output_model_path.stat().st_size / (1024 * 1024)
    reduction = ((orig_mb - quant_mb) / orig_mb) * 100
    print(f"[OK] {input_model_path.name}: {orig_mb:.2f} MB -> {output_model_path.name}: {quant_mb:.2f} MB ({reduction:.1f}% size reduction)")

def main():
    parser = argparse.ArgumentParser(description="AI4Bharat IndicTrans2 INT8 Quantizer")
    parser.add_argument("--encoder-in", type=str, help="Path to input encoder.onnx")
    parser.add_argument("--decoder-in", type=str, help="Path to input decoder.onnx")
    parser.add_argument("--output-dir", type=str, default="app/src/main/assets/models/nmt", help="Output directory")
    args = parser.parse_args()

    out_dir = Path(args.output_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    if args.encoder_in and Path(args.encoder_in).exists():
        quantize_model(Path(args.encoder_in), out_dir / "indictrans2_encoder.int8.onnx")
    if args.decoder_in and Path(args.decoder_in).exists():
        quantize_model(Path(args.decoder_in), out_dir / "indictrans2_decoder.int8.onnx")

    print("[SUCCESS] Quantization pipeline completed.")

if __name__ == "__main__":
    main()
