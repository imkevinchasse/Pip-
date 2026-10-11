package com.example.manager

import com.example.core.FetchCancelledException
import com.example.core.FetchException
import com.example.core.FileFetcher
import com.example.core.ZipExtractor
import com.example.model.ModelId
import com.example.model.ModelItem
import com.example.model.ModelStatus
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class RemoteFile(val name: String, val urls: List<String>, val minBytes: Long)

/** Where each model comes from. Everything is downloaded once and then runs fully offline. */
data class ModelCatalog(
    val sttZip: RemoteFile,
    val llmModel: RemoteFile,
    val llmTokenizer: RemoteFile
) {
    companion object {
        private const val MB = 1024L * 1024L

        val DEFAULT = ModelCatalog(
            sttZip = RemoteFile(
                name = "vosk-model-small-en-us-0.15.zip",
                urls = listOf("https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"),
                minBytes = 30 * MB
            ),
            llmModel = RemoteFile(
                name = "model.onnx",
                urls = listOf(
                    "https://huggingface.co/onnx-community/SmolLM2-135M-Instruct/resolve/main/onnx/model_q4.onnx",
                    "https://huggingface.co/onnx-community/SmolLM2-135M-Instruct/resolve/main/onnx/model_quantized.onnx"
                ),
                minBytes = 40 * MB
            ),
            llmTokenizer = RemoteFile(
                name = "tokenizer.json",
                urls = listOf("https://huggingface.co/onnx-community/SmolLM2-135M-Instruct/resolve/main/tokenizer.json"),
                minBytes = 200 * 1024L
            )
        )
    }
}

/**
 * Model files that ship inside the app itself (the APK's assets). When present, Pip installs
 * from these with no internet at all. [open] returns null for a file that was not bundled.
 */
fun interface BundledModels {
    fun open(name: String): InputStream?

    companion object {
        /** Asset names, relative to assets/models/. Same names the download catalog uses. */
        const val STT_ZIP = "vosk-model-small-en-us-0.15.zip"
        const val LLM_MODEL = "model.onnx"
        const val LLM_TOKENIZER = "tokenizer.json"
    }
}

/**
 * Downloads, verifies and installs Pip's models into app-private storage.
 *
 * Honest by construction: a model is READY only if its real files are on disk and passed
 * verification. Failures are reported with a plain-language reason. Nothing is ever faked.
 */
class ModelDownloadManager(
    private val modelsDir: File,
    private val scope: CoroutineScope,
    private val catalog: ModelCatalog = ModelCatalog.DEFAULT,
    private val fetcher: FileFetcher = FileFetcher(),
    private val isOnline: () -> Boolean = { true },
    private val freeBytes: () -> Long = { modelsDir.usableSpace },
    private val ioContext: kotlin.coroutines.CoroutineContext = Dispatchers.IO,
    private val bundled: BundledModels? = null
) {

    val sttDir = File(modelsDir, "stt/vosk-small-en")
    val llmDir = File(modelsDir, "llm/smollm2-135m")
    val llmModelFile get() = File(llmDir, catalog.llmModel.name)
    val llmTokenizerFile get() = File(llmDir, catalog.llmTokenizer.name)

    private val _state = MutableStateFlow(initialState())
    val modelsState: StateFlow<Map<ModelId, ModelItem>> = _state.asStateFlow()

    private val jobs = HashMap<ModelId, Job>()
    private val urlOverrides = HashMap<ModelId, String>()

    private var ttsReady = false
    private var ttsMessage: String? = null

    init {
        modelsDir.mkdirs()
        refresh()
    }

    // ---- what is installed -----------------------------------------------------------------

    fun isReady(id: ModelId): Boolean = _state.value[id]?.status == ModelStatus.READY

    private fun sttInstalled(): Boolean =
        File(sttDir, "am").isDirectory && File(sttDir, "conf").isDirectory

    private fun llmInstalled(): Boolean =
        llmModelFile.length() >= catalog.llmModel.minBytes &&
            llmTokenizerFile.length() >= catalog.llmTokenizer.minBytes

    private fun initialState(): Map<ModelId, ModelItem> = ModelId.entries.associateWith { ModelItem(it) }

    /** Re-reads the disk. Safe to call any time. */
    fun refresh() {
        update(ModelId.STT) { current ->
            if (current.status == ModelStatus.DOWNLOADING || current.status == ModelStatus.INSTALLING) current
            else if (sttInstalled()) ModelItem(ModelId.STT, ModelStatus.READY, sizeOnDiskBytes = sttDir.sizeOnDisk())
            else if (current.status == ModelStatus.FAILED) current
            else ModelItem(ModelId.STT, ModelStatus.NOT_DOWNLOADED)
        }
        update(ModelId.LLM) { current ->
            if (current.status == ModelStatus.DOWNLOADING || current.status == ModelStatus.INSTALLING) current
            else if (llmInstalled()) ModelItem(ModelId.LLM, ModelStatus.READY, sizeOnDiskBytes = llmDir.sizeOnDisk())
            else if (current.status == ModelStatus.FAILED) current
            else ModelItem(ModelId.LLM, ModelStatus.NOT_DOWNLOADED)
        }
        update(ModelId.TTS) {
            ModelItem(
                ModelId.TTS,
                if (ttsReady) ModelStatus.READY else if (ttsMessage != null) ModelStatus.FAILED else ModelStatus.NOT_DOWNLOADED,
                error = if (ttsReady) null else ttsMessage
            )
        }
    }

    /** The voice is the phone's own offline text-to-speech; the app tells us whether it works. */
    fun setTtsStatus(ready: Boolean, message: String? = null) {
        ttsReady = ready
        ttsMessage = if (ready) null else (message ?: "Text-to-speech is not ready")
        refresh()
    }

    val allDownloadsReady: Boolean get() = isReady(ModelId.STT) && isReady(ModelId.LLM)

    val missingDownloads: List<ModelId>
        get() = listOf(ModelId.STT, ModelId.LLM).filter { !isReady(it) }

    // ---- URLs the user may override -------------------------------------------------------

    fun urlFor(id: ModelId): String = urlsFor(id).firstOrNull().orEmpty()

    fun setUrl(id: ModelId, url: String) {
        val trimmed = url.trim()
        if (trimmed.startsWith("https://")) urlOverrides[id] = trimmed else urlOverrides.remove(id)
    }

    private fun urlsFor(id: ModelId): List<String> {
        val defaults = when (id) {
            ModelId.STT -> catalog.sttZip.urls
            ModelId.LLM -> catalog.llmModel.urls
            ModelId.TTS -> emptyList()
        }
        val override = urlOverrides[id] ?: return defaults
        return listOf(override) + defaults
    }

    // ---- models that ship inside the app ----------------------------------------------------

    /** True when the app was built with this model's files inside it. */
    fun isBundled(id: ModelId): Boolean {
        val source = bundled ?: return false
        val names = when (id) {
            ModelId.STT -> listOf(BundledModels.STT_ZIP)
            ModelId.LLM -> listOf(BundledModels.LLM_MODEL, BundledModels.LLM_TOKENIZER)
            ModelId.TTS -> return false
        }
        return names.all { name ->
            try {
                source.open(name)?.use { true } ?: false
            } catch (_: IOException) {
                false
            }
        }
    }

    /**
     * Installs every missing model that ships inside the app. No internet needed. Call once at
     * start-up; it does nothing when the models are already installed or were not bundled.
     */
    fun installBundled() {
        if (bundled == null) return
        scope.launch(ioContext) {
            for (id in missingDownloads) {
                if (isBundled(id)) download(id).join()
            }
        }
    }

    // ---- downloading ----------------------------------------------------------------------

    fun downloadAllMissing() {
        scope.launch(ioContext) {
            for (id in missingDownloads) {
                download(id).join()
            }
        }
    }

    @Synchronized
    fun download(id: ModelId): Job {
        if (id == ModelId.TTS) return scope.launch { }
        jobs[id]?.let { if (it.isActive) return it }
        val job = scope.launch(ioContext) { runDownload(id) { !isActive } }
        jobs[id] = job
        return job
    }

    @Synchronized
    fun cancel(id: ModelId) {
        jobs[id]?.cancel()
        jobs.remove(id)
        update(id) { ModelItem(id, ModelStatus.NOT_DOWNLOADED) }
        refresh()
    }

    fun delete(id: ModelId) {
        cancel(id)
        when (id) {
            ModelId.STT -> sttDir.deleteRecursively()
            ModelId.LLM -> llmDir.deleteRecursively()
            ModelId.TTS -> Unit
        }
        refresh()
    }

    private fun runDownload(id: ModelId, isCancelled: () -> Boolean) {
        try {
            val fromApp = isBundled(id)
            if (!fromApp && !isOnline()) {
                fail(id, "No internet connection. Connect once so Pip can download this, then it works offline.")
                return
            }
            val needed = when (id) {
                ModelId.STT -> 120L * 1024 * 1024
                ModelId.LLM -> 300L * 1024 * 1024
                ModelId.TTS -> 0L
            }
            val free = freeBytes()
            if (free in 0 until needed) {
                fail(id, "Not enough free space. Pip needs about ${needed / (1024 * 1024)} MB free for this.")
                return
            }
            when (id) {
                ModelId.STT -> if (fromApp) installSttFromApp(isCancelled) else installStt(isCancelled)
                ModelId.LLM -> if (fromApp) installLlmFromApp(isCancelled) else installLlm(isCancelled)
                ModelId.TTS -> Unit
            }
        } catch (_: FetchCancelledException) {
            // The user cancelled; cancel() already reset the state.
        } catch (e: FetchException) {
            fail(id, e.message ?: "Download failed")
        } catch (e: IOException) {
            fail(id, "Could not save the file: ${e.message ?: "storage error"}")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(id, "Unexpected problem: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun installStt(isCancelled: () -> Boolean) {
        val zip = File(modelsDir, "stt/${catalog.sttZip.name}")
        progress(ModelId.STT, ModelStatus.DOWNLOADING, 0, 0, 0, catalog.sttZip.name)
        fetcher.fetch(
            urls = urlsFor(ModelId.STT),
            dest = zip,
            minBytes = catalog.sttZip.minBytes,
            onProgress = { done, total, speed ->
                if (!isCancelled()) progress(ModelId.STT, ModelStatus.DOWNLOADING, done, total, speed, catalog.sttZip.name)
            },
            isCancelled = isCancelled
        )

        unpackStt(zip, isCancelled)
    }

    private fun installSttFromApp(isCancelled: () -> Boolean) {
        val zip = File(modelsDir, "stt/${catalog.sttZip.name}")
        progress(ModelId.STT, ModelStatus.INSTALLING, 0, 0, 0, "Copying Pip's ears")
        copyFromApp(BundledModels.STT_ZIP, zip, ModelId.STT, isCancelled)
        unpackStt(zip, isCancelled)
    }

    private fun unpackStt(zip: File, isCancelled: () -> Boolean) {
        progress(ModelId.STT, ModelStatus.INSTALLING, 0, 0, 0, "Unpacking")
        val staging = File(modelsDir, "stt/vosk-small-en.tmp")
        staging.deleteRecursively()
        try {
            ZipExtractor.extract(zip, staging, isCancelled = isCancelled)
            if (!File(staging, "am").isDirectory || !File(staging, "conf").isDirectory) {
                throw FetchException("The speech model looks wrong (missing files)", retryable = false)
            }
            sttDir.deleteRecursively()
            if (!staging.renameTo(sttDir)) throw IOException("could not move the model into place")
        } catch (e: java.util.zip.ZipException) {
            zip.delete() // a corrupt zip must be downloaded again
            throw FetchException("The speech model file was damaged. Please try again.", retryable = true, cause = e)
        } finally {
            staging.deleteRecursively()
        }
        zip.delete()
        settle(ModelId.STT)
    }

    private fun installLlmFromApp(isCancelled: () -> Boolean) {
        llmDir.mkdirs()
        progress(ModelId.LLM, ModelStatus.INSTALLING, 0, 0, 0, "Copying Pip's brain")
        copyFromApp(BundledModels.LLM_TOKENIZER, llmTokenizerFile, ModelId.LLM, isCancelled)
        copyFromApp(BundledModels.LLM_MODEL, llmModelFile, ModelId.LLM, isCancelled)
        settle(ModelId.LLM)
    }

    /** Copies one bundled file to [dest] through a .part file, so a half copy is never mistaken for a model. */
    private fun copyFromApp(name: String, dest: File, id: ModelId, isCancelled: () -> Boolean) {
        val source = bundled?.open(name) ?: throw IOException("$name is not bundled in this app")
        dest.parentFile?.mkdirs()
        val part = File(dest.path + ".part")
        part.delete()
        var done = 0L
        try {
            source.use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        if (isCancelled()) throw FetchCancelledException()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        update(id) { it.copy(downloadedBytes = done, currentFile = name) }
                    }
                }
            }
            dest.delete()
            if (!part.renameTo(dest)) throw IOException("could not move $name into place")
        } finally {
            part.delete()
        }
    }

    private fun installLlm(isCancelled: () -> Boolean) {
        llmDir.mkdirs()
        // Small file first: if the network is unusable we find out in seconds, not after 100 MB.
        progress(ModelId.LLM, ModelStatus.DOWNLOADING, 0, 0, 0, catalog.llmTokenizer.name)
        fetcher.fetch(
            urls = catalog.llmTokenizer.urls,
            dest = llmTokenizerFile,
            minBytes = catalog.llmTokenizer.minBytes,
            onProgress = { done, total, speed ->
                if (!isCancelled()) progress(ModelId.LLM, ModelStatus.DOWNLOADING, done, total, speed, catalog.llmTokenizer.name)
            },
            isCancelled = isCancelled
        )
        progress(ModelId.LLM, ModelStatus.DOWNLOADING, 0, 0, 0, "language model")
        fetcher.fetch(
            urls = urlsFor(ModelId.LLM),
            dest = llmModelFile,
            minBytes = catalog.llmModel.minBytes,
            onProgress = { done, total, speed ->
                if (!isCancelled()) progress(ModelId.LLM, ModelStatus.DOWNLOADING, done, total, speed, "language model")
            },
            isCancelled = isCancelled
        )
        settle(ModelId.LLM)
    }

    /** After an install attempt: the state becomes exactly what is verified on disk. */
    private fun settle(id: ModelId) {
        val installed = when (id) {
            ModelId.STT -> sttInstalled()
            ModelId.LLM -> llmInstalled()
            ModelId.TTS -> ttsReady
        }
        update(id) {
            if (installed) {
                ModelItem(id, ModelStatus.READY, sizeOnDiskBytes = when (id) {
                    ModelId.STT -> sttDir.sizeOnDisk()
                    ModelId.LLM -> llmDir.sizeOnDisk()
                    ModelId.TTS -> 0L
                })
            } else {
                ModelItem(id, ModelStatus.FAILED, error = "The download finished but the files did not check out. Please try again.")
            }
        }
    }

    // ---- state helpers --------------------------------------------------------------------

    private fun progress(id: ModelId, status: ModelStatus, done: Long, total: Long, speed: Long, file: String) {
        update(id) {
            it.copy(
                status = status,
                downloadedBytes = done,
                totalBytes = total,
                bytesPerSecond = speed,
                currentFile = file,
                error = null
            )
        }
    }

    private fun fail(id: ModelId, message: String) {
        update(id) { ModelItem(id, ModelStatus.FAILED, error = message) }
    }

    @Synchronized
    private fun update(id: ModelId, transform: (ModelItem) -> ModelItem) {
        val map = _state.value.toMutableMap()
        val current = map[id] ?: ModelItem(id)
        map[id] = transform(current)
        _state.value = map
    }

    fun totalStorageUsedBytes(): Long = modelsDir.sizeOnDisk()

    private fun File.sizeOnDisk(): Long =
        if (!exists()) 0L else if (isFile) length() else (listFiles()?.sumOf { it.sizeOnDisk() } ?: 0L)
}
