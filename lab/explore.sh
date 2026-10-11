#!/usr/bin/env bash
# Scouts which model files exist (sizes) and what the configs say. Output goes to the report.
for repo in onnx-community/whisper-tiny.en onnx-community/whisper-base.en onnx-community/whisper-small.en \
            onnx-community/Qwen2.5-0.5B-Instruct onnx-community/SmolLM2-360M-Instruct HuggingFaceTB/SmolLM2-360M-Instruct; do
  echo "=== $repo"
  curl -sL "https://huggingface.co/api/models/$repo/tree/main?recursive=true" | python3 -c '
import sys, json
try:
    items = json.load(sys.stdin)
except Exception as e:
    print("  (could not read:", e, ")"); sys.exit()
if isinstance(items, dict):
    print("  ", items); sys.exit()
for it in items:
    p = it.get("path", "")
    size = (it.get("lfs") or {}).get("size", it.get("size", 0))
    if p.endswith((".onnx", ".json", ".txt")) and "/" not in p or p.startswith("onnx/"):
        print("  %-48s %8.1f MB" % (p, size/1e6))
'
done
echo
echo "=== whisper-base.en preprocessor_config.json keys"
curl -sL https://huggingface.co/onnx-community/whisper-base.en/resolve/main/preprocessor_config.json | python3 -c '
import sys, json
d = json.load(sys.stdin)
for k, v in d.items():
    if k == "mel_filters": print("  mel_filters: %d x %d" % (len(v), len(v[0])))
    else: print("  ", k, "=", v)
'
echo "=== whisper-base.en generation_config.json"
curl -sL https://huggingface.co/onnx-community/whisper-base.en/resolve/main/generation_config.json | head -c 1500
echo
echo "=== whisper-base.en config.json (selected)"
curl -sL https://huggingface.co/onnx-community/whisper-base.en/resolve/main/config.json | python3 -c '
import sys, json
d = json.load(sys.stdin)
for k in ["d_model","encoder_layers","decoder_layers","decoder_attention_heads","encoder_attention_heads","vocab_size","num_mel_bins","max_source_positions","max_target_positions","decoder_start_token_id","eos_token_id","bos_token_id"]:
    print("  ", k, "=", d.get(k))
'
