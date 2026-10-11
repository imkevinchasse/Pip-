Model files for Pip go here (they are NOT committed: too big for git).
Run `tools/fetch_models.sh` to fill this folder. When these files exist at build time,
the app installs them on first launch with no download and no internet.

  vosk-model-small-en-us-0.15.zip   speech-to-text
  model.onnx                        tiny language model (SmolLM2-135M, 4-bit)
  tokenizer.json                    its tokenizer
