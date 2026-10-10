package com.example.core

import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/** Safe zip extraction (blocks path traversal, caps total size, strips a single wrapper folder). */
object ZipExtractor {

    /**
     * Extracts [zipFile] into [destDir]. When every entry lives under one top-level folder
     * (as Vosk model zips do), that folder is stripped so [destDir] holds the content directly.
     *
     * @return number of files written
     */
    fun extract(
        zipFile: File,
        destDir: File,
        maxTotalBytes: Long = 3L * 1024 * 1024 * 1024,
        isCancelled: () -> Boolean = { false }
    ): Int {
        destDir.mkdirs()
        val canonicalDest = destDir.canonicalFile
        var written = 0
        var totalBytes = 0L

        ZipFile(zipFile).use { zip ->
            val entries = zip.entries().toList()
            val strip = commonRoot(entries.map { it.name })

            for (entry in entries) {
                if (isCancelled()) throw IOException("Cancelled")
                var name = entry.name.replace('\\', '/')
                if (strip != null) name = name.removePrefix(strip)
                if (name.isEmpty()) continue

                val target = File(canonicalDest, name).canonicalFile
                if (target.path != canonicalDest.path &&
                    !target.path.startsWith(canonicalDest.path + File.separator)
                ) {
                    throw IOException("Blocked unsafe path in zip: ${entry.name}")
                }

                if (entry.isDirectory) {
                    target.mkdirs()
                    continue
                }
                target.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            totalBytes += n
                            if (totalBytes > maxTotalBytes) throw IOException("Zip is unexpectedly large")
                            output.write(buffer, 0, n)
                        }
                    }
                }
                written++
            }
        }
        return written
    }

    /** Returns "folder/" when every entry sits under that single top-level folder. */
    internal fun commonRoot(names: List<String>): String? {
        if (names.isEmpty()) return null
        val normalized = names.map { it.replace('\\', '/') }
        val first = normalized.first().substringBefore('/', missingDelimiterValue = "")
        if (first.isEmpty()) return null
        val prefix = "$first/"
        if (!normalized.all { it.startsWith(prefix) }) return null
        // A single plain file named "x/" is not a wrapper worth stripping.
        return if (normalized.any { it.length > prefix.length }) prefix else null
    }
}
