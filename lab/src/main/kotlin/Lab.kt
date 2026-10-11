import ai.onnxruntime.OrtEnvironment
import com.example.core.ByteLevelBpeTokenizer
import com.example.core.Generator
import com.example.core.Sampler
import com.example.core.SpeechFilter
import com.example.core.ZipExtractor
import com.example.engine.OnnxTokenModel
import com.example.personality.FracturedEnglish
import java.io.File
import kotlin.random.Random
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer

private val out = StringBuilder()
private fun say(s: String = "") { println(s); out.appendLine(s) }

private fun rssMb(): Long =
    runCatching {
        File("/proc/self/status").readLines().first { it.startsWith("VmRSS") }
            .filter { it.isDigit() }.toLong() / 1024
    }.getOrDefault(-1)

fun main(args: Array<String>) {
    val models = File(args[0])
    val wav = File(args[1])
    val resultFile = File(args[2])
    try {
        llmLab(models)
    } catch (t: Throwable) {
        say("LLM LAB CRASHED: $t"); t.printStackTrace()
    }
    try {
        voskLab(models, wav)
    } catch (t: Throwable) {
        say("VOSK LAB CRASHED: $t"); t.printStackTrace()
    }
    resultFile.writeText(out.toString())
}

private fun llmLab(models: File) {
    say("=== LLM (SmolLM2-135M, real file, real tokenizer, app's own code) ===")
    val tokJson = File(models, "tokenizer.json").readText()
    val tokenizer = ByteLevelBpeTokenizer.fromJson(tokJson)
    val probe = "Hello, Pip! Should I quit my job?"
    val ids = tokenizer.encode(probe)
    say("tokenizer round trip: ids=${ids.size} decoded='${tokenizer.decode(ids)}' ok=${tokenizer.decode(ids) == probe}")
    say("special ids: im_start=${tokenizer.tokenId("<|im_start|>")} im_end=${tokenizer.tokenId("<|im_end|>")}")

    val rss0 = rssMb()
    val t0 = System.currentTimeMillis()
    val model = OnnxTokenModel.load(File(models, "model.onnx").absolutePath, threads = 2)
    say("model load: ${System.currentTimeMillis() - t0} ms, RSS ${rss0} -> ${rssMb()} MB")
    val gen = Generator(tokenizer, Sampler(temperature = 0.6f, topK = 30, topP = 0.9f), model)

    val questions = listOf(
        "Should I quit my job?", "Why do cats purr?", "I have a big test tomorrow.", "How do I make friends?",
        "I feel lonely.", "What should I cook tonight?", "Will I ever be happy?", "Is it okay to be lazy sometimes?",
        "Are you real?", "Thank you.", "Tell me something wise.", "What is the meaning of life?",
        "I'm scared of the dark.", "Should I learn guitar?", "Who are you?", "Should I buy a meme coin?"
    )
    var accepted = 0
    var totalMs = 0L
    for (q in questions) {
        val s = System.currentTimeMillis()
        val raw = gen.generate(q, budgetMs = 60_000L)
        val ms = System.currentTimeMillis() - s
        totalMs += ms
        val polished = FracturedEnglish.polish(raw, q)
        if (polished != null) accepted++
        say("Q: $q")
        say("   raw     : ${raw.replace("\n", " / ")}")
        say("   polished: ${polished ?: "(rejected -> instinct) " + FracturedEnglish.compose(q, Random(1))}")
        say("   ${ms} ms")
    }
    say("model answers accepted by Pip's voice check: $accepted / ${questions.size}; avg ${totalMs / questions.size} ms; RSS now ${rssMb()} MB")
    model.close()
}

private fun voskLab(models: File, wav: File) {
    say()
    say("=== Vosk small (real model, real recording) ===")
    val dir = File(models, "vosk-extracted")
    if (!File(dir, "am").isDirectory) ZipExtractor.extract(File(models, "vosk-model-small-en-us-0.15.zip"), dir)
    LibVosk.setLogLevel(LogLevel.WARNINGS)
    val rss0 = rssMb()
    val t0 = System.currentTimeMillis()
    val model = Model(dir.absolutePath)
    say("load: ${System.currentTimeMillis() - t0} ms, RSS ${rss0} -> ${rssMb()} MB")
    val pcm = readWav16k(wav)
    say("audio: ${pcm.size / 16000.0} s")
    val text = transcribe(model, pcm)
    say("heard: '$text'")
    say("expected: 'and so my fellow americans ask not what your country can do for you ask what you can do for your country'")
    model.close()
}

internal fun transcribe(model: Model, pcm: ShortArray): String {
    val rec = Recognizer(model, 16000f)
    val finals = ArrayList<String>()
    var i = 0
    val chunk = 1600
    while (i < pcm.size) {
        val n = minOf(chunk, pcm.size - i)
        val buf = pcm.copyOfRange(i, i + n)
        if (rec.acceptWaveForm(buf, n)) SpeechFilter.fromRecognizerJson(rec.result, "text").takeIf { it.isNotBlank() }?.let(finals::add)
        i += n
    }
    SpeechFilter.fromRecognizerJson(rec.finalResult, "text").takeIf { it.isNotBlank() }?.let(finals::add)
    rec.close()
    return finals.joinToString(" ")
}

/** Minimal 16 kHz mono 16-bit WAV reader (skips the header). */
internal fun readWav16k(f: File): ShortArray {
    val bytes = f.readBytes()
    var pos = 12
    while (pos + 8 < bytes.size) {
        val id = String(bytes, pos, 4)
        val size = java.nio.ByteBuffer.wrap(bytes, pos + 4, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
        if (id == "data") {
            val bb = java.nio.ByteBuffer.wrap(bytes, pos + 8, minOf(size, bytes.size - pos - 8)).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            return ShortArray(bb.remaining() / 2) { bb.short }
        }
        pos += 8 + size
    }
    error("no data chunk in ${f.name}")
}
