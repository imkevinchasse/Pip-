package com.example.core

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A download failure. [retryable] tells the caller whether trying the same URL again can help. */
class FetchException(message: String, val retryable: Boolean, cause: Throwable? = null) :
    IOException(message, cause)

/** Thrown when the caller asked to stop. The partial file is kept so the download can resume. */
class FetchCancelledException : RuntimeException("Download cancelled")

data class FetchResult(val bytes: Long, val url: String)

/**
 * Resumable, verified file downloader built on plain [HttpURLConnection].
 *
 * What it guarantees:
 *  - data goes to `<dest>.part` and only becomes [dest] after every check passed, so a half
 *    downloaded file can never be mistaken for a finished one
 *  - interrupted downloads resume with HTTP Range (and restart cleanly if the server ignores it)
 *  - the body must be as long as the server promised and at least [minBytes] (catches
 *    truncated files and "wrong file" mistakes)
 *  - HTML error pages are rejected instead of being saved as a model
 *  - URLs are tried in order; a partial file is only resumed against the URL that produced it
 *  - nothing is ever fabricated: on failure it throws and leaves [dest] untouched
 */
class FileFetcher(
    private val userAgent: String = "PipApp/1.0 (Android; on-device model download)",
    private val connectTimeoutMs: Int = 20_000,
    private val readTimeoutMs: Int = 30_000,
    private val maxAttemptsPerUrl: Int = 3,
    private val backoffMs: Long = 1_500L,
    private val maxRedirects: Int = 8
) {

    fun fetch(
        urls: List<String>,
        dest: File,
        minBytes: Long,
        onProgress: (done: Long, total: Long, bytesPerSec: Long) -> Unit = { _, _, _ -> },
        isCancelled: () -> Boolean = { false }
    ): FetchResult {
        require(urls.isNotEmpty()) { "No download URLs" }
        dest.parentFile?.mkdirs()
        val part = partFile(dest)
        val sourceMarker = File(dest.path + ".part.src")

        var lastError: Exception? = null
        for (url in urls) {
            // Never resume bytes that came from a different URL (mirrors can be different files).
            val previousSource = runCatching { sourceMarker.readText() }.getOrNull()
            if (part.exists() && previousSource != url) part.delete()
            sourceMarker.writeText(url)

            var attempt = 0
            while (attempt < maxAttemptsPerUrl) {
                attempt++
                if (isCancelled()) throw FetchCancelledException()
                try {
                    val size = downloadOnce(url, part, dest, minBytes, onProgress, isCancelled)
                    sourceMarker.delete()
                    return FetchResult(size, url)
                } catch (e: FetchCancelledException) {
                    throw e
                } catch (e: FetchException) {
                    lastError = e
                    if (!e.retryable) break
                } catch (e: IOException) {
                    lastError = e
                }
                if (attempt < maxAttemptsPerUrl) sleepCancellable(backoffMs * attempt, isCancelled)
            }
            // This URL is exhausted; whatever .part exists belongs to it, so drop it before the next.
            if (urls.last() != url) part.delete()
        }
        throw FetchException(
            "Could not download from any source" + (lastError?.message?.let { ": $it" } ?: ""),
            retryable = true,
            cause = lastError
        )
    }

    private fun downloadOnce(
        url: String,
        part: File,
        dest: File,
        minBytes: Long,
        onProgress: (Long, Long, Long) -> Unit,
        isCancelled: () -> Boolean
    ): Long {
        var existing = if (part.exists()) part.length() else 0L
        val conn = openFollowingRedirects(url, existing)
        try {
            val code = conn.responseCode
            var total: Long
            when {
                code == 200 -> {
                    if (existing > 0) {
                        // Server ignored our Range header: start over.
                        part.delete()
                        existing = 0
                    }
                    total = conn.contentLengthLong
                }
                code == 206 -> {
                    val range = conn.getHeaderField("Content-Range")
                    val parsed = parseContentRange(range)
                    if (parsed == null || parsed.first != existing) {
                        part.delete()
                        throw FetchException("Server sent an unexpected byte range", retryable = true)
                    }
                    total = parsed.third
                }
                code == 416 -> {
                    // Our partial file is past what the server has. Maybe it is already complete.
                    val range = conn.getHeaderField("Content-Range")
                    val serverTotal = range?.substringAfter('/')?.trim()?.toLongOrNull()
                    if (existing > 0 && serverTotal == existing) {
                        return finish(part, dest, minBytes)
                    }
                    part.delete()
                    throw FetchException("Partial file did not match the server; restarting", retryable = true)
                }
                code in 500..599 || code == 429 ->
                    throw FetchException("Server busy (HTTP $code)", retryable = true)
                code == 401 || code == 403 ->
                    throw FetchException("Access denied (HTTP $code)", retryable = false)
                code == 404 || code == 410 ->
                    throw FetchException("File not found (HTTP $code)", retryable = false)
                else -> throw FetchException("Unexpected HTTP $code", retryable = false)
            }

            val contentType = conn.contentType.orEmpty().lowercase()
            if (contentType.startsWith("text/html")) {
                throw FetchException("Got a web page instead of a model file", retryable = false)
            }

            var done = existing
            val append = existing > 0
            var lastReport = 0L
            var windowStart = System.nanoTime()
            var windowBytes = 0L
            var speed = 0L

            conn.inputStream.use { input ->
                FileOutputStream(part, append).use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var first = true
                    while (true) {
                        if (isCancelled()) throw FetchCancelledException()
                        val n = input.read(buffer)
                        if (n < 0) break
                        if (first && existing == 0L && looksLikeHtml(buffer, n)) {
                            throw FetchException("Got a web page instead of a model file", retryable = false)
                        }
                        first = false
                        out.write(buffer, 0, n)
                        done += n
                        windowBytes += n

                        val now = System.nanoTime()
                        if ((now - lastReport) / 1_000_000 >= 250) {
                            val windowMs = (now - windowStart) / 1_000_000
                            if (windowMs >= 500) {
                                speed = windowBytes * 1000 / windowMs
                                windowStart = now
                                windowBytes = 0
                            }
                            lastReport = now
                            onProgress(done, total, speed)
                        }
                    }
                }
            }
            if (total > 0 && done != total) {
                throw FetchException("Connection ended early ($done of $total bytes)", retryable = true)
            }
            if (total <= 0) total = done
            onProgress(done, total, speed)
            return finish(part, dest, minBytes)
        } finally {
            conn.disconnect()
        }
    }

    private fun finish(part: File, dest: File, minBytes: Long): Long {
        val size = part.length()
        if (size < minBytes) {
            part.delete()
            throw FetchException(
                "Downloaded file is too small ($size bytes, expected at least $minBytes)",
                retryable = false
            )
        }
        // Not java.nio.file.Files: that needs API 26 and this app supports API 24.
        if (dest.exists() && !dest.delete()) {
            throw FetchException("Could not replace the old file", retryable = false)
        }
        if (!part.renameTo(dest)) {
            part.copyTo(dest, overwrite = true)
            part.delete()
        }
        return size
    }

    private fun openFollowingRedirects(startUrl: String, resumeFrom: Long): HttpURLConnection {
        var current = startUrl
        var hops = 0
        while (true) {
            val conn = URL(current).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = readTimeoutMs
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Accept", "*/*")
            // Without this the stack may gzip the body, which breaks byte counts and resuming.
            conn.setRequestProperty("Accept-Encoding", "identity")
            if (resumeFrom > 0) conn.setRequestProperty("Range", "bytes=$resumeFrom-")

            val code = conn.responseCode
            if (code in intArrayOf(301, 302, 303, 307, 308)) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) throw FetchException("Redirect without a location", retryable = false)
                if (++hops > maxRedirects) throw FetchException("Too many redirects", retryable = false)
                val next = URL(URL(current), location).toString()
                if (current.startsWith("https://", ignoreCase = true) && next.startsWith("http://", ignoreCase = true)) {
                    throw FetchException("Refusing insecure redirect to $next", retryable = false)
                }
                current = next
                continue
            }
            return conn
        }
    }

    private fun looksLikeHtml(buffer: ByteArray, n: Int): Boolean {
        val head = String(buffer, 0, minOf(n, 64), Charsets.ISO_8859_1).trimStart().lowercase()
        return head.startsWith("<!doctype html") || head.startsWith("<html")
    }

    private fun sleepCancellable(ms: Long, isCancelled: () -> Boolean) {
        var left = ms
        while (left > 0) {
            if (isCancelled()) throw FetchCancelledException()
            val step = minOf(left, 100L)
            Thread.sleep(step)
            left -= step
        }
    }

    companion object {
        fun partFile(dest: File) = File(dest.path + ".part")

        /** Parses "bytes start-end/total" (total may be '*'). Returns Triple(start, end, total or -1). */
        internal fun parseContentRange(header: String?): Triple<Long, Long, Long>? {
            if (header == null) return null
            val m = Regex("""bytes\s+(\d+)-(\d+)/(\d+|\*)""").matchEntire(header.trim()) ?: return null
            val start = m.groupValues[1].toLong()
            val end = m.groupValues[2].toLong()
            val total = m.groupValues[3].toLongOrNull() ?: -1L
            return Triple(start, end, total)
        }
    }
}
