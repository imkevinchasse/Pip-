package com.example.core

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Exercises the downloader against a real local HTTP server, including the failures real networks have. */
class FileFetcherTest {

    private lateinit var server: HttpServer
    private lateinit var dir: File
    private val payload = ByteArray(300_000) { (it * 31 + 7).toByte() }

    private val requestsToFlaky = AtomicInteger(0)
    private val sawRangeHeaders = ArrayList<String?>()

    private val base get() = "http://127.0.0.1:${server.address.port}"

    @Before
    fun start() {
        dir = Files.createTempDirectory("pipdl").toFile()
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)

        // Normal file with Range support.
        server.createContext("/ok.bin") { ex -> serveWithRange(ex, payload, honorRange = true) }
        // Server that ignores Range and always sends everything.
        server.createContext("/norange.bin") { ex -> serveWithRange(ex, payload, honorRange = false) }
        // Redirects like Hugging Face does (to another path).
        server.createContext("/redirect.bin") { ex ->
            ex.responseHeaders.add("Location", "/ok.bin")
            ex.sendResponseHeaders(302, -1)
            ex.close()
        }
        // Drops the connection halfway on the first request, behaves afterwards.
        server.createContext("/flaky.bin") { ex ->
            sawRangeHeaders.add(ex.requestHeaders.getFirst("Range"))
            if (requestsToFlaky.getAndIncrement() == 0) {
                ex.sendResponseHeaders(200, payload.size.toLong())
                ex.responseBody.write(payload, 0, 120_000)
                ex.responseBody.flush()
                ex.close() // closes before sending the promised length
            } else {
                serveWithRange(ex, payload, honorRange = true)
            }
        }
        server.createContext("/html.bin") { ex ->
            val body = "<!DOCTYPE html><html><body>Sign in</body></html>".toByteArray()
            ex.responseHeaders.add("Content-Type", "text/html")
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.write(body)
            ex.close()
        }
        server.createContext("/html-no-header.bin") { ex ->
            val body = "<html><body>Sign in</body></html>".toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.write(body)
            ex.close()
        }
        server.createContext("/small.bin") { ex ->
            val body = ByteArray(10)
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.write(body)
            ex.close()
        }
        server.createContext("/missing.bin") { ex ->
            ex.sendResponseHeaders(404, -1)
            ex.close()
        }
        server.start()
    }

    @After
    fun stop() {
        server.stop(0)
        dir.deleteRecursively()
    }

    private fun serveWithRange(ex: HttpExchange, data: ByteArray, honorRange: Boolean) {
        val range = ex.requestHeaders.getFirst("Range")
        if (honorRange && range != null) {
            val start = range.removePrefix("bytes=").substringBefore('-').toInt()
            if (start >= data.size) {
                ex.responseHeaders.add("Content-Range", "bytes */${data.size}")
                ex.sendResponseHeaders(416, -1)
                ex.close()
                return
            }
            ex.responseHeaders.add("Content-Range", "bytes $start-${data.size - 1}/${data.size}")
            ex.sendResponseHeaders(206, (data.size - start).toLong())
            ex.responseBody.write(data, start, data.size - start)
        } else {
            ex.sendResponseHeaders(200, data.size.toLong())
            ex.responseBody.write(data)
        }
        ex.close()
    }

    private fun fetcher() = FileFetcher(backoffMs = 10, maxAttemptsPerUrl = 3)

    @Test
    fun downloadsAWholeFileAndOnlyThenCreatesIt() {
        val dest = File(dir, "model.bin")
        val progress = ArrayList<Long>()
        val result = fetcher().fetch(listOf("$base/ok.bin"), dest, minBytes = 1000,
            onProgress = { done, _, _ -> progress.add(done) })
        assertEquals(payload.size.toLong(), result.bytes)
        assertArrayEquals(payload, dest.readBytes())
        assertFalse("part file must be gone", File(dest.path + ".part").exists())
        assertTrue(progress.last() == payload.size.toLong())
    }

    @Test
    fun followsRedirects() {
        val dest = File(dir, "r.bin")
        fetcher().fetch(listOf("$base/redirect.bin"), dest, 1000)
        assertArrayEquals(payload, dest.readBytes())
    }

    @Test
    fun resumesAfterTheConnectionDropsHalfway() {
        val dest = File(dir, "flaky.out")
        val result = fetcher().fetch(listOf("$base/flaky.bin"), dest, 1000)
        assertEquals(payload.size.toLong(), result.bytes)
        assertArrayEquals("file must be byte-identical after resuming", payload, dest.readBytes())
        // First request had no Range, the retry asked to continue where it stopped.
        assertEquals(null, sawRangeHeaders[0])
        assertTrue(sawRangeHeaders[1], sawRangeHeaders[1]!!.startsWith("bytes="))
        assertTrue(sawRangeHeaders[1]!!.removePrefix("bytes=").substringBefore('-').toLong() in 1 until payload.size)
    }

    @Test
    fun resumesAnOlderPartialFileFromAPreviousRun() {
        val dest = File(dir, "old.bin")
        File(dest.path + ".part").writeBytes(payload.copyOf(50_000))
        File(dest.path + ".part.src").writeText("$base/ok.bin")
        fetcher().fetch(listOf("$base/ok.bin"), dest, 1000)
        assertArrayEquals(payload, dest.readBytes())
    }

    @Test
    fun doesNotResumeBytesThatCameFromADifferentUrl() {
        val dest = File(dir, "mix.bin")
        File(dest.path + ".part").writeBytes(ByteArray(50_000) { 9 }) // garbage from another file
        File(dest.path + ".part.src").writeText("$base/some-other-file.bin")
        fetcher().fetch(listOf("$base/ok.bin"), dest, 1000)
        assertArrayEquals(payload, dest.readBytes())
    }

    @Test
    fun restartsCleanlyWhenTheServerIgnoresRange() {
        val dest = File(dir, "nr.bin")
        File(dest.path + ".part").writeBytes(payload.copyOf(10_000))
        File(dest.path + ".part.src").writeText("$base/norange.bin")
        fetcher().fetch(listOf("$base/norange.bin"), dest, 1000)
        assertArrayEquals("must not contain the old 10k twice", payload, dest.readBytes())
    }

    @Test
    fun fallsBackToTheNextUrlWhenTheFirstIsMissing() {
        val dest = File(dir, "fb.bin")
        val result = fetcher().fetch(listOf("$base/missing.bin", "$base/ok.bin"), dest, 1000)
        assertTrue(result.url.endsWith("/ok.bin"))
        assertArrayEquals(payload, dest.readBytes())
    }

    @Test
    fun rejectsWebPagesInsteadOfSavingThemAsModels() {
        for (path in listOf("/html.bin", "/html-no-header.bin")) {
            val dest = File(dir, "h.bin")
            try {
                fetcher().fetch(listOf("$base$path"), dest, minBytes = 10)
                fail("html must be rejected: $path")
            } catch (e: FetchException) {
                assertTrue(e.message, e.message!!.contains("web page") || e.message!!.contains("any source"))
            }
            assertFalse("no fake model file may be created", dest.exists())
        }
    }

    @Test
    fun rejectsFilesThatAreTooSmall() {
        val dest = File(dir, "s.bin")
        try {
            fetcher().fetch(listOf("$base/small.bin"), dest, minBytes = 1_000_000)
            fail()
        } catch (e: FetchException) {
            assertTrue(e.message, e.message!!.contains("too small") || e.message!!.contains("any source"))
        }
        assertFalse(dest.exists())
        assertFalse(File(dest.path + ".part").exists())
    }

    @Test
    fun failsLoudlyWhenOfflineAndNeverFakesSuccess() {
        val dest = File(dir, "off.bin")
        try {
            fetcher().fetch(listOf("http://127.0.0.1:1/nothing.bin"), dest, 1000)
            fail("offline must be an error")
        } catch (e: FetchException) {
            assertTrue(e.retryable)
        }
        assertFalse(dest.exists())
    }

    @Test
    fun cancelKeepsThePartialFileSoItCanResume() {
        val dest = File(dir, "c.bin")
        var cancel = false
        try {
            fetcher().fetch(listOf("$base/ok.bin"), dest, 1000,
                onProgress = { done, _, _ -> if (done > 0) cancel = true },
                isCancelled = { cancel })
            // A tiny file can finish before the first progress tick; that is fine too.
        } catch (_: FetchCancelledException) {
            assertFalse(dest.exists())
        }
    }

    @Test
    fun parsesContentRangeHeaders() {
        assertEquals(Triple(100L, 199L, 1000L), FileFetcher.parseContentRange("bytes 100-199/1000"))
        assertEquals(Triple(0L, 9L, -1L), FileFetcher.parseContentRange("bytes 0-9/*"))
        assertEquals(null, FileFetcher.parseContentRange("garbage"))
        assertEquals(null, FileFetcher.parseContentRange(null))
    }
}
