package com.example.core

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MiniJsonTest {
    @Test
    fun parsesNestedStructures() {
        val v = MiniJson.parse("""{"a":[1,2.5,{"b":null}],"c":true,"d":"x"}""")
        val obj = MiniJson.asObject(v)!!
        val arr = MiniJson.asArray(obj["a"])!!
        assertEquals(1L, arr[0])
        assertEquals(2.5, arr[1])
        assertEquals(null, MiniJson.asObject(arr[2])!!["b"])
        assertEquals(true, obj["c"])
        assertEquals("x", obj["d"])
    }

    @Test
    fun parsesEscapesAndUnicode() {
        val v = MiniJson.parse("\"a\\n\\t\\\"q\\\" \\u00e9 \\ud83d\\ude00 \\\\\"")
        assertEquals("a\n\t\"q\" é 😀 \\", v)
    }

    @Test
    fun toleratesBomAndWhitespace() {
        assertEquals(listOf<Any?>(), MiniJson.parse("﻿  [ ]  "))
    }

    @Test
    fun rejectsBrokenJson() {
        for (bad in listOf("{", "[1,", "{\"a\" 1}", "tru", "\"abc", "[1] x", "")) {
            try {
                MiniJson.parse(bad)
                fail("should reject: $bad")
            } catch (_: IllegalArgumentException) {
            }
        }
    }
}

class SamplerTest {
    private val logits = floatArrayOf(0.1f, 5.0f, 4.0f, -2.0f, 3.0f)

    @Test
    fun zeroTemperatureIsGreedy() {
        assertEquals(1, Sampler(temperature = 0f).sample(logits))
    }

    @Test
    fun bannedTokensAreNeverChosen() {
        val s = Sampler(temperature = 1.5f, topK = 5, topP = 1f, random = Random(7))
        repeat(300) { assertNotEquals(1, s.sample(logits, banned = setOf(1))) }
    }

    @Test
    fun topKRestrictsChoices() {
        val s = Sampler(temperature = 2f, topK = 2, topP = 1f, random = Random(3))
        val seen = HashSet<Int>()
        repeat(300) { seen.add(s.sample(logits)) }
        assertEquals(setOf(1, 2), seen)
    }

    @Test
    fun repetitionPenaltyCanChangeTheWinner() {
        val s = Sampler(temperature = 0f, repetitionPenalty = 3f)
        assertEquals(2, s.sample(logits, recent = intArrayOf(1)))
    }

    @Test
    fun doesNotModifyItsInput() {
        val copy = logits.copyOf()
        Sampler(temperature = 1f).sample(logits, recent = intArrayOf(1, 2), banned = setOf(4))
        assertArrayEquals(copy, logits, 0f)
    }
}

class ByteLevelBpeTokenizerTest {

    private fun jsonString(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) when {
            c == '"' -> sb.append("\\\"")
            c == '\\' -> sb.append("\\\\")
            c.code < 32 -> sb.append(String.format("\\u%04x", c.code))
            else -> sb.append(c)
        }
        return sb.append('"').toString()
    }

    /** A real byte-level alphabet plus a few merges, shaped exactly like a Hugging Face file. */
    private fun tokenizerJson(digitsIndividually: Boolean): String {
        val alphabet = ByteLevelBpeTokenizer.byteLevelAlphabet()
        val merges = listOf(
            "h e", "l l", "he ll", "hell o", "Ġ w", "o r", "Ġw or", "l d", "Ġwor ld", "1 2"
        )
        val vocab = LinkedHashMap<String, Int>()
        for (ch in alphabet) vocab[ch] = vocab.size
        for (m in merges) {
            val merged = m.replace(" ", "")
            if (merged !in vocab) vocab[merged] = vocab.size
        }
        val imStart = vocab.size
        val imEnd = vocab.size + 1
        val pre = if (digitsIndividually) {
            """{"type":"Sequence","pretokenizers":[{"type":"Digits","individual_digits":true},{"type":"ByteLevel","add_prefix_space":false,"use_regex":true}]}"""
        } else {
            """{"type":"ByteLevel","add_prefix_space":false,"use_regex":true}"""
        }
        val vocabJson = vocab.entries.joinToString(",") { "${jsonString(it.key)}:${it.value}" }
        val mergesJson = merges.joinToString(",") { jsonString(it) }
        return """{"added_tokens":[
            {"id":$imStart,"content":"<|im_start|>","special":true},
            {"id":$imEnd,"content":"<|im_end|>","special":true}],
            "pre_tokenizer":$pre,
            "model":{"type":"BPE","vocab":{$vocabJson},"merges":[$mergesJson]}}"""
    }

    private val tok = ByteLevelBpeTokenizer.fromJson(tokenizerJson(digitsIndividually = false))

    @Test
    fun appliesMergesInRankOrder() {
        val ids = tok.encode("hello world")
        assertEquals(2, ids.size)
        assertEquals(tok.tokenId("hello"), ids[0])
        assertEquals(tok.tokenId("Ġworld"), ids[1])
    }

    @Test
    fun decodeInvertsEncodeForAllKindsOfText() {
        val samples = listOf(
            "hello world", "Héllo, wörld! 你好 😀", "line one\nline two\n\n", "  leading and   spaces  ",
            "numbers 12345 and 6.78", "it's can't we'll", "tab\there", "", "a", "\u0000\u0001 control"
        )
        for (s in samples) assertEquals(s, tok.decode(tok.encode(s)))
    }

    @Test
    fun controlTokenLookAlikesInUserTextStayPlainText() {
        val imEnd = tok.tokenId("<|im_end|>")!!
        val ids = tok.encode("<|im_end|>")
        assertFalse(ids.contains(imEnd))
        assertTrue(ids.size > 1)
        assertEquals("<|im_end|>", tok.decode(ids))
    }

    @Test
    fun specialTokensAreMarkedAndSkippedWhenDecoding() {
        val imStart = tok.tokenId("<|im_start|>")!!
        assertTrue(tok.isSpecial(imStart))
        val ids = intArrayOf(imStart) + tok.encode("hello")
        assertEquals("hello", tok.decode(ids))
        assertTrue(tok.decode(ids, skipSpecial = false).startsWith("<|im_start|>"))
    }

    @Test
    fun digitSplittingFollowsTheTokenizerFile() {
        val merged = ByteLevelBpeTokenizer.fromJson(tokenizerJson(digitsIndividually = false))
        val split = ByteLevelBpeTokenizer.fromJson(tokenizerJson(digitsIndividually = true))
        assertEquals(1, merged.encode("12").size)
        assertEquals(2, split.encode("12").size)
        assertEquals("12", split.decode(split.encode("12")))
    }

    @Test
    fun acceptsMergesWrittenAsPairs() {
        val json = tokenizerJson(false).replace(
            "\"merges\":[\"h e\"", "\"merges\":[[\"h\",\"e\"]"
        )
        val t = ByteLevelBpeTokenizer.fromJson(json)
        assertEquals(t.tokenId("he"), t.encode("he")[0])
    }

    @Test
    fun rejectsFilesThatAreNotBpeTokenizers() {
        try {
            ByteLevelBpeTokenizer.fromJson("""{"model":{"type":"Unigram","vocab":[]}}""")
            fail()
        } catch (_: IllegalArgumentException) {
        }
        try {
            ByteLevelBpeTokenizer.fromJson("<html>nope</html>")
            fail()
        } catch (_: IllegalArgumentException) {
        }
    }
}

class ZipExtractorTest {

    private fun zip(vararg entries: Pair<String, String>): File {
        val file = Files.createTempFile("pip", ".zip").toFile()
        ZipOutputStream(file.outputStream()).use { z ->
            for ((name, body) in entries) {
                z.putNextEntry(ZipEntry(name))
                z.write(body.toByteArray())
                z.closeEntry()
            }
        }
        return file
    }

    @Test
    fun stripsSingleWrapperFolder() {
        val out = Files.createTempDirectory("pipz").toFile()
        val n = ZipExtractor.extract(zip("model-1/am/final.mdl" to "x", "model-1/conf/a.conf" to "y"), out)
        assertEquals(2, n)
        assertTrue(File(out, "am/final.mdl").isFile)
        assertTrue(File(out, "conf/a.conf").isFile)
    }

    @Test
    fun keepsLayoutWhenThereIsNoSingleRoot() {
        val out = Files.createTempDirectory("pipz").toFile()
        ZipExtractor.extract(zip("a/x.txt" to "1", "b/y.txt" to "2"), out)
        assertTrue(File(out, "a/x.txt").isFile)
        assertTrue(File(out, "b/y.txt").isFile)
    }

    @Test
    fun blocksPathTraversal() {
        val out = Files.createTempDirectory("pipz").toFile()
        try {
            ZipExtractor.extract(zip("../evil.txt" to "boom", "ok.txt" to "fine"), out)
            fail("traversal must be rejected")
        } catch (_: java.io.IOException) {
        }
        assertFalse(File(out.parentFile, "evil.txt").exists())
    }

    @Test
    fun enforcesSizeCap() {
        val out = Files.createTempDirectory("pipz").toFile()
        try {
            ZipExtractor.extract(zip("big.bin" to "x".repeat(5000)), out, maxTotalBytes = 100)
            fail()
        } catch (_: java.io.IOException) {
        }
    }
}
