# Pip

A tiny AI companion that lives on your phone. No servers, no cloud.

Pip speaks broken English that sounds vaguely inspiring *because* the grammar is broken:

> **Should I put my savings in a meme coin?**
> Lose, better keep save. Save bank. Lose is you if meme coin.
>
> **Who are you?**
> Pip is pip.

## How it works (all on the phone)

| Part | What | Size | RAM |
|---|---|---|---|
| Ears | [Vosk](https://alphacephei.com/vosk/) `vosk-model-small-en-us-0.15`, offline speech-to-text | ~40 MB download | ~300 MB while listening |
| Brain | SmolLM2-135M-Instruct, 4-bit ONNX, run with ONNX Runtime | ~100-200 MB download | ~250 MB while thinking, released when idle |
| Voice | The phone's built-in text-to-speech, offline voices only | no download | negligible |

The Ears and Brain can ship **inside the app** (see *Bundling the models*), so there is nothing to download. Otherwise the internet is used **once**, to download them. After that Pip needs no connection, the microphone audio never leaves the phone, and cloud backup is switched off so the models are never uploaded anywhere.

Pip measures its own real memory use (shown in the app) and aims to stay far below 1.5 GB: the language model loads only when thinking and is released after 60 s idle; the speech model is released when the mic has been off for 60 s.

### How an answer is chosen (`PipelineController`)
1. **Safety** – crisis messages get a clear, caring answer with a pointer to real help. Never a joke.
2. **Signature answers** – the hand-written ones above.
3. **Memory** – recently asked questions (text only, in RAM).
4. **The language model** – prompted with examples, then forced into Pip's voice by `FracturedEnglish.polish`. Anything chatty or off-brand is rejected.
5. **Pip's instinct** – a pattern brain that always works, even with no models downloaded.

Every reply in the app shows which of these produced it.

Pip never listens while it speaks (the mic is closed and a short tail is ignored), so it cannot answer its own voice.

## Bundling the models (no download on the phone)
If the model files are inside the APK, Pip installs them on first launch with no internet and no tap needed.

- **Easiest:** on GitHub open *Actions → Build Pip APK (models included) → Run workflow*. GitHub's servers fetch the models, run the tests and build the APK; download the `pip-apk` artifact and install it.
- **By hand:** run `tools/fetch_models.sh` (needs internet; fills `app/src/main/assets/models/`), then build in Android Studio.

The model files are git-ignored (too big for git), so a build made without that step simply falls back to the in-app download.

## Layout
```
app/src/main/java/com/example/
  core/         pure Kotlin: pipeline, generator, tokenizer, sampler, downloader, zip, JSON
  personality/  Pip's voice (FracturedEnglish)
  manager/      ModelDownloadManager (real, resumable, verified downloads)
  engine/       Android/native glue: Vosk ears, ONNX brain, system voice, RAM meter
  ui/           Compose screens
```

## Tests
`core/`, `personality/` and `manager/` have no Android dependency and are covered by JVM unit tests
(downloads are tested against a real local HTTP server, including dropped connections, resume,
redirects, HTML error pages, corrupt zips, no internet, no disk space and cancel):

```
./gradlew :app:testDebugUnitTest
```

## Honest notes
- **Voice is not Piper.** The previous version claimed Piper but used Android's speech engine. This version says what it is. `Voice` is an interface, so a real Piper engine is a one-class change later.
- **Speaker ("owner voice") recognition was removed.** It was a fake (fixed numbers and a timer). Real speaker ID needs a speaker-embedding model.
- **No localhost server.** The old fake HTTP server on port 8080 was removed.
- **Unverified in the build environment:** the Android UI, the Vosk and ONNX Runtime calls, and the model download URLs (Hugging Face `onnx-community/SmolLM2-135M-Instruct`) could not be compiled or run where this was written (no Android SDK, no access to Maven or Hugging Face). Everything in `core/`, `personality/` and `manager/` is tested. If a download URL is wrong, the app shows a clear error and each model has an editable link in the *Pip's brains* sheet.
- A 135M-parameter model is tiny. Often its output will not pass the voice check and Pip answers from instinct instead; that is by design.
