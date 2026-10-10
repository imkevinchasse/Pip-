package com.example.manager

import com.example.core.FileFetcher
import com.example.model.ModelId
import com.example.model.ModelStatus
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ModelDownloadManagerTest {

    private lateinit var server: HttpServer
    private lateinit var dir: File
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val base get() = "http://127.0.0.1:${server.address.port}"

    private val modelBytes = ByteArray(120_000) { (it % 251).toByte() }
    private val tokenizerBytes = ByteArray(9_000) { (it % 13).toByte() }

    private fun voskZip(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for ((name, size) in listOf(
                "vosk-model-small-en-us-0.15/am/final.mdl" to 60_000,
                "vosk-model-small-en-us-0.15/conf/model.conf" to 200,
                "vosk-model-small-en-us-0.15/graph/HCLr.fst" to 50_000
            )) {
                z.putNextEntry(ZipEntry(name))
                z.write(ByteArray(size).also { java.util.Random(size.toLong()).nextBytes(it) })
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Before
    fun start() {
        dir = Files.createTempDirectory("pipmodels").toFile()
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        val zip = voskZip()
        fun serve(path: String, bytes: ByteArray, type: String = "application/octet-stream") =
            server.createContext(path) { ex ->
                ex.responseHeaders.add("Content-Type", type)
                ex.sendResponseHeaders(200, bytes.size.toLong())
                ex.responseBody.write(bytes)
                ex.close()
            }
        serve("/stt.zip", zip, "application/zip")
        serve("/model.onnx", modelBytes)
        serve("/tokenizer.json", tokenizerBytes)
        serve("/corrupt.zip", ByteArray(50_000) { 1 }, "application/zip")
        serve("/page", "<!DOCTYPE html><html>login</html>".toByteArray(), "text/html")
        server.createContext("/slow.bin") { ex ->
            ex.sendResponseHeaders(200, 5_000_000)
            try {
                val chunk = ByteArray(10_000)
                repeat(500) { ex.responseBody.write(chunk); ex.responseBody.flush(); Thread.sleep(10) }
            } catch (_: Exception) {
            }
            ex.close()
        }
        server.start()
    }

    @After
    fun stop() {
        scope.cancel()
        server.stop(0)
        dir.deleteRecursively()
    }

    private fun catalog(
        stt: String = "/stt.zip",
        model: String = "/model.onnx",
        tokenizer: String = "/tokenizer.json"
    ) = ModelCatalog(
        RemoteFile("stt.zip", listOf(base + stt), minBytes = 10_000),
        RemoteFile("model.onnx", listOf(base + model), minBytes = 10_000),
        RemoteFile("tokenizer.json", listOf(base + tokenizer), minBytes = 1_000)
    )

    private fun manager(
        catalog: ModelCatalog = catalog(),
        online: Boolean = true,
        free: Long = Long.MAX_VALUE
    ) = ModelDownloadManager(
        modelsDir = File(dir, "models"),
        scope = scope,
        catalog = catalog,
        fetcher = FileFetcher(backoffMs = 5, maxAttemptsPerUrl = 2),
        isOnline = { online },
        freeBytes = { free }
    )

    private fun ModelDownloadManager.status(id: ModelId) = modelsState.value[id]!!

    private fun waitFor(timeoutMs: Long = 5_000, cond: () -> Boolean) = runBlocking {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            if (System.currentTimeMillis() > end) throw AssertionError("timed out")
            delay(10)
        }
    }

    @Test
    fun freshInstallStartsEmptyNeverPretendingToHaveModels() {
        val m = manager()
        assertEquals(ModelStatus.NOT_DOWNLOADED, m.status(ModelId.STT).status)
        assertEquals(ModelStatus.NOT_DOWNLOADED, m.status(ModelId.LLM).status)
        assertFalse(m.allDownloadsReady)
        assertEquals(listOf(ModelId.STT, ModelId.LLM), m.missingDownloads)
        assertFalse(m.llmModelFile.exists())
    }

    @Test
    fun speechModelDownloadsUnpacksAndBecomesReady() {
        val m = manager()
        runBlocking { m.download(ModelId.STT).join() }
        val item = m.status(ModelId.STT)
        assertEquals(item.error, ModelStatus.READY, item.status)
        assertTrue(File(m.sttDir, "am/final.mdl").isFile)
        assertTrue(File(m.sttDir, "conf/model.conf").isFile)
        assertFalse("zip must be deleted after unpacking", File(dir, "models/stt/stt.zip").exists())
        assertFalse(File(dir, "models/stt/vosk-small-en.tmp").exists())
        assertTrue(item.sizeOnDiskBytes > 100_000)
    }

    @Test
    fun languageModelDownloadsBothFilesAndBecomesReady() {
        val m = manager()
        runBlocking { m.download(ModelId.LLM).join() }
        assertEquals(m.status(ModelId.LLM).error, ModelStatus.READY, m.status(ModelId.LLM).status)
        assertTrue(m.llmModelFile.readBytes().contentEquals(modelBytes))
        assertTrue(m.llmTokenizerFile.readBytes().contentEquals(tokenizerBytes))
    }

    @Test
    fun downloadAllMissingFetchesEverythingAndSurvivesARestart() {
        val m = manager()
        m.downloadAllMissing()
        waitFor { m.allDownloadsReady }
        // "App restart": a brand new manager sees the files on disk.
        val again = manager(online = false)
        assertTrue(again.allDownloadsReady)
        assertEquals(emptyList<ModelId>(), again.missingDownloads)
    }

    @Test
    fun offlineGivesAClearMessageAndCreatesNoFiles() {
        val m = manager(online = false)
        runBlocking { m.download(ModelId.LLM).join() }
        val item = m.status(ModelId.LLM)
        assertEquals(ModelStatus.FAILED, item.status)
        assertTrue(item.error!!, item.error!!.contains("No internet"))
        assertFalse(m.llmModelFile.exists())
        assertFalse(m.isReady(ModelId.LLM))
    }

    @Test
    fun notEnoughSpaceIsReportedBeforeDownloading() {
        val m = manager(free = 10_000_000)
        runBlocking { m.download(ModelId.LLM).join() }
        assertEquals(ModelStatus.FAILED, m.status(ModelId.LLM).status)
        assertTrue(m.status(ModelId.LLM).error!!.contains("free space"))
    }

    @Test
    fun notFoundIsAFailureNotASuccess() {
        val m = manager(catalog(model = "/nope.onnx"))
        runBlocking { m.download(ModelId.LLM).join() }
        assertEquals(ModelStatus.FAILED, m.status(ModelId.LLM).status)
        assertFalse(m.isReady(ModelId.LLM))
        assertFalse("must not leave a broken model behind", m.llmModelFile.exists())
    }

    @Test
    fun webPageInsteadOfModelIsRejected() {
        val m = manager(catalog(model = "/page"))
        runBlocking { m.download(ModelId.LLM).join() }
        assertEquals(ModelStatus.FAILED, m.status(ModelId.LLM).status)
        assertFalse(m.isReady(ModelId.LLM))
    }

    @Test
    fun corruptZipFailsAndIsDeletedSoRetryStartsFresh() {
        val m = manager(catalog(stt = "/corrupt.zip"))
        runBlocking { m.download(ModelId.STT).join() }
        assertEquals(ModelStatus.FAILED, m.status(ModelId.STT).status)
        assertFalse(m.isReady(ModelId.STT))
        assertFalse(File(dir, "models/stt/stt.zip").exists())
        assertFalse(m.sttDir.exists())
    }

    @Test
    fun failedModelCanBeRetriedAndSucceeds() {
        val failing = manager(online = false)
        runBlocking { failing.download(ModelId.STT).join() }
        assertEquals(ModelStatus.FAILED, failing.status(ModelId.STT).status)
        val retry = manager()
        runBlocking { retry.download(ModelId.STT).join() }
        assertEquals(ModelStatus.READY, retry.status(ModelId.STT).status)
    }

    @Test
    fun progressIsReportedWhileDownloading() {
        val seen = java.util.Collections.synchronizedList(ArrayList<Pair<ModelStatus, Float>>())
        val m = manager(catalog(model = "/slow.bin"))
        val m2 = ModelDownloadManager(
            File(dir, "models2"), scope,
            ModelCatalog(
                RemoteFile("stt.zip", listOf(base + "/stt.zip"), 10_000),
                RemoteFile("model.onnx", listOf(base + "/slow.bin"), 10_000),
                RemoteFile("tokenizer.json", listOf(base + "/tokenizer.json"), 1_000)
            ),
            FileFetcher(backoffMs = 5), { true }, { Long.MAX_VALUE }
        )
        val job = m2.download(ModelId.LLM)
        val collector = Thread {
            while (job.isActive) {
                val it = m2.status(ModelId.LLM)
                seen.add(it.status to it.progress)
                Thread.sleep(20)
            }
        }
        collector.start()
        runBlocking { job.join() }
        collector.join()
        assertTrue(seen.any { it.first == ModelStatus.DOWNLOADING && it.second > 0f && it.second < 1f })
        assertEquals(ModelStatus.READY, m2.status(ModelId.LLM).status)
        assertNotNull(m)
    }

    @Test
    fun cancelStopsTheDownloadAndResetsTheState() {
        val m = ModelDownloadManager(
            File(dir, "models3"), scope,
            ModelCatalog(
                RemoteFile("stt.zip", listOf(base + "/stt.zip"), 10_000),
                RemoteFile("model.onnx", listOf(base + "/slow.bin"), 10_000),
                RemoteFile("tokenizer.json", listOf(base + "/tokenizer.json"), 1_000)
            ),
            FileFetcher(backoffMs = 5), { true }, { Long.MAX_VALUE }
        )
        val job = m.download(ModelId.LLM)
        waitFor { m.status(ModelId.LLM).status == ModelStatus.DOWNLOADING && m.status(ModelId.LLM).downloadedBytes > 0 }
        m.cancel(ModelId.LLM)
        runBlocking { job.join() }
        assertEquals(ModelStatus.NOT_DOWNLOADED, m.status(ModelId.LLM).status)
        assertFalse(m.llmModelFile.exists())
    }

    @Test
    fun deleteRemovesFilesAndGoesBackToNotDownloaded() {
        val m = manager()
        runBlocking { m.download(ModelId.LLM).join() }
        m.delete(ModelId.LLM)
        assertEquals(ModelStatus.NOT_DOWNLOADED, m.status(ModelId.LLM).status)
        assertFalse(m.llmModelFile.exists())
    }

    @Test
    fun customUrlsAreTriedFirstButMustBeHttps() {
        val m = manager()
        m.setUrl(ModelId.LLM, "http://evil.example/model.onnx")
        assertTrue(m.urlFor(ModelId.LLM).startsWith(base)) // plain http override ignored
        m.setUrl(ModelId.LLM, "https://example.com/my.onnx")
        assertEquals("https://example.com/my.onnx", m.urlFor(ModelId.LLM))
    }

    @Test
    fun voiceStatusComesFromTheSystemEngine() {
        val m = manager()
        assertEquals(ModelStatus.NOT_DOWNLOADED, m.status(ModelId.TTS).status)
        m.setTtsStatus(true)
        assertEquals(ModelStatus.READY, m.status(ModelId.TTS).status)
        m.setTtsStatus(false, "No voice installed")
        assertEquals(ModelStatus.FAILED, m.status(ModelId.TTS).status)
        assertEquals("No voice installed", m.status(ModelId.TTS).error)
        assertNull(manager().status(ModelId.STT).error)
    }
}
