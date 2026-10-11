#!/usr/bin/env bash
# Downloads Pip's two models into app/src/main/assets/models/ so they ship INSIDE the app.
# Run once on any computer with internet, then build the app as usual.
set -euo pipefail

OUT="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/assets/models"
mkdir -p "$OUT"

HF="https://huggingface.co/onnx-community/SmolLM2-135M-Instruct/resolve/main"
STT_URL="https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"

fetch() { # url dest min_bytes
  local url="$1" dest="$2" min="$3"
  if [ -f "$dest" ] && [ "$(stat -c%s "$dest" 2>/dev/null || stat -f%z "$dest")" -ge "$min" ]; then
    echo "have  $(basename "$dest")"; return 0
  fi
  echo "fetch $url"
  curl -fL --retry 4 --retry-delay 3 -o "$dest.part" "$url" || { rm -f "$dest.part"; return 1; }
  local size; size=$(stat -c%s "$dest.part" 2>/dev/null || stat -f%z "$dest.part")
  if [ "$size" -lt "$min" ]; then echo "too small ($size bytes): $url" >&2; rm -f "$dest.part"; return 1; fi
  mv "$dest.part" "$dest"
}

fetch "$STT_URL" "$OUT/vosk-model-small-en-us-0.15.zip" $((30*1024*1024))
fetch "$HF/tokenizer.json" "$OUT/tokenizer.json" $((200*1024))
fetch "$HF/onnx/model_q4.onnx" "$OUT/model.onnx" $((40*1024*1024)) \
  || fetch "$HF/onnx/model_quantized.onnx" "$OUT/model.onnx" $((40*1024*1024))

# Sanity: the zip must be a real zip with the Vosk layout.
unzip -l "$OUT/vosk-model-small-en-us-0.15.zip" | grep -q "/am/final.mdl" \
  || { echo "speech model zip does not look like a Vosk model" >&2; exit 1; }

echo; ls -lh "$OUT"; echo "Done. Build the app now; the models will be inside it."
