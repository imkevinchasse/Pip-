package com.example.core

/**
 * Tiny dependency-free JSON parser.
 *
 * Objects become [Map] (insertion ordered), arrays become [List], numbers become [Long] or
 * [Double], and strings / booleans / null map to their Kotlin equivalents.
 *
 * It exists so that code which has to run in plain JVM unit tests (the tokenizer, the model
 * checks) does not depend on android.org.json, which is stubbed out in local tests.
 */
object MiniJson {

    fun parse(text: String): Any? {
        val parser = Parser(text)
        parser.skipWhitespace()
        val value = parser.readValue()
        parser.skipWhitespace()
        if (parser.pos != text.length) {
            throw IllegalArgumentException("Unexpected trailing data at ${parser.pos}")
        }
        return value
    }

    @Suppress("UNCHECKED_CAST")
    fun asObject(value: Any?): Map<String, Any?>? = value as? Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    fun asArray(value: Any?): List<Any?>? = value as? List<Any?>

    private class Parser(private val s: String) {
        var pos = 0

        fun skipWhitespace() {
            // A UTF-8 BOM sneaks in sometimes; treat it as whitespace.
            while (pos < s.length) {
                val c = s[pos]
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '﻿') pos++ else break
            }
        }

        fun readValue(): Any? {
            if (pos >= s.length) throw IllegalArgumentException("Unexpected end of JSON")
            return when (val c = s[pos]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> readLiteral("true", true)
                'f' -> readLiteral("false", false)
                'n' -> readLiteral("null", null)
                else -> if (c == '-' || c in '0'..'9') readNumber()
                else throw IllegalArgumentException("Unexpected '$c' at $pos")
            }
        }

        private fun readLiteral(word: String, value: Any?): Any? {
            if (!s.startsWith(word, pos)) throw IllegalArgumentException("Bad literal at $pos")
            pos += word.length
            return value
        }

        private fun readObject(): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            pos++ // {
            skipWhitespace()
            if (pos < s.length && s[pos] == '}') {
                pos++
                return map
            }
            while (true) {
                skipWhitespace()
                if (pos >= s.length || s[pos] != '"') throw IllegalArgumentException("Expected key at $pos")
                val key = readString()
                skipWhitespace()
                if (pos >= s.length || s[pos] != ':') throw IllegalArgumentException("Expected ':' at $pos")
                pos++
                skipWhitespace()
                map[key] = readValue()
                skipWhitespace()
                if (pos >= s.length) throw IllegalArgumentException("Unterminated object")
                when (s[pos]) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return map
                    }
                    else -> throw IllegalArgumentException("Expected ',' or '}' at $pos")
                }
            }
        }

        private fun readArray(): List<Any?> {
            val list = ArrayList<Any?>()
            pos++ // [
            skipWhitespace()
            if (pos < s.length && s[pos] == ']') {
                pos++
                return list
            }
            while (true) {
                skipWhitespace()
                list.add(readValue())
                skipWhitespace()
                if (pos >= s.length) throw IllegalArgumentException("Unterminated array")
                when (s[pos]) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return list
                    }
                    else -> throw IllegalArgumentException("Expected ',' or ']' at $pos")
                }
            }
        }

        private fun readString(): String {
            pos++ // opening quote
            val sb = StringBuilder()
            while (true) {
                if (pos >= s.length) throw IllegalArgumentException("Unterminated string")
                val c = s[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (pos >= s.length) throw IllegalArgumentException("Bad escape")
                        when (val e = s[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > s.length) throw IllegalArgumentException("Bad \\u escape")
                                sb.append(s.substring(pos, pos + 4).toInt(16).toChar())
                                pos += 4
                            }
                            else -> throw IllegalArgumentException("Bad escape '\\$e' at ${pos - 1}")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun readNumber(): Any {
            val start = pos
            var isFloating = false
            while (pos < s.length) {
                val c = s[pos]
                if (c == '.' || c == 'e' || c == 'E') isFloating = true
                if (c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E' || c in '0'..'9') pos++ else break
            }
            val token = s.substring(start, pos)
            return if (isFloating) token.toDouble() else token.toLongOrNull() ?: token.toDouble()
        }
    }
}
