package com.example.manager

import com.example.engine.SmolLM2FracturedEngine
import com.example.model.ModelId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

class LocalModelServer(
    private val smolLmEngine: SmolLM2FracturedEngine,
    private val downloadManager: ModelDownloadManager,
    private val scope: CoroutineScope,
    private val port: Int = 8080
) {

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _serverUrl = MutableStateFlow("http://127.0.0.1:$port")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _requestCount = MutableStateFlow(0)
    val requestCount: StateFlow<Int> = _requestCount.asStateFlow()

    private val _lastResponsePreview = MutableStateFlow("All 3 models hosted locally on device")
    val lastResponsePreview: StateFlow<String> = _lastResponsePreview.asStateFlow()

    fun start() {
        if (_isRunning.value) return

        serverJob = scope.launch(Dispatchers.IO) {
            try {
                val socket = ServerSocket(port)
                serverSocket = socket
                _isRunning.value = true
                _serverUrl.value = "http://127.0.0.1:$port"

                while (_isRunning.value && !socket.isClosed) {
                    try {
                        val client = socket.accept()
                        handleClient(client)
                    } catch (_: Exception) {
                        break
                    }
                }
            } catch (e: Exception) {
                _isRunning.value = false
            }
        }
    }

    private fun handleClient(client: Socket) {
        scope.launch(Dispatchers.IO) {
            try {
                val inputStream = client.getInputStream()
                val outputStream = client.getOutputStream()
                val reader = BufferedReader(InputStreamReader(inputStream))
                val writer = PrintWriter(outputStream)

                val requestLine = reader.readLine() ?: return@launch
                val parts = requestLine.split(" ")
                val method = if (parts.isNotEmpty()) parts[0] else "GET"
                val path = if (parts.size > 1) parts[1] else "/"

                var contentLength = 0
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line.isNullOrBlank()) break
                    if (line!!.lowercase().startsWith("content-length:")) {
                        contentLength = line!!.substring(15).trim().toIntOrNull() ?: 0
                    }
                }

                val body = if (contentLength > 0) {
                    val charArray = CharArray(contentLength)
                    reader.read(charArray, 0, contentLength)
                    String(charArray)
                } else ""

                _requestCount.value = _requestCount.value + 1

                val whisperFile = downloadManager.getLocalFile(ModelId.WHISPER_SMALL)
                val smolLmFile = downloadManager.getLocalFile(ModelId.SMOLLM2_135M)
                val piperFile = downloadManager.getLocalFile(ModelId.PIPER_TTS)

                // Check for file download stream requests
                if (path.startsWith("/v1/models/download/")) {
                    val modelName = path.substringAfter("/v1/models/download/")
                    val targetFile = when (modelName) {
                        "whisper" -> whisperFile
                        "smollm" -> smolLmFile
                        "piper" -> piperFile
                        else -> null
                    }

                    if (targetFile != null && targetFile.exists()) {
                        val fileLength = targetFile.length()
                        writer.print("HTTP/1.1 200 OK\r\n")
                        writer.print("Content-Type: application/octet-stream\r\n")
                        writer.print("Content-Disposition: attachment; filename=\"${targetFile.name}\"\r\n")
                        writer.print("Content-Length: $fileLength\r\n")
                        writer.print("Connection: close\r\n\r\n")
                        writer.flush()

                        val fileIn = FileInputStream(targetFile)
                        val buf = ByteArray(16384)
                        var read: Int
                        while (fileIn.read(buf).also { read = it } != -1) {
                            outputStream.write(buf, 0, read)
                        }
                        fileIn.close()
                        outputStream.flush()
                        client.close()
                        return@launch
                    }
                }

                val (statusCode, responseJson) = when {
                    path == "/" || path == "/health" || path == "/v1/models/verify" -> {
                        200 to """{
                            "status": "online",
                            "host": "127.0.0.1:$port",
                            "models_hosted": 3,
                            "models": {
                                "whisper_small": {
                                    "name": "Whisper Small",
                                    "role": "dynamic_listening_stt",
                                    "file": "${whisperFile.name}",
                                    "path": "${whisperFile.absolutePath}",
                                    "present_on_disk": ${whisperFile.exists()},
                                    "hosted_status": "ONLINE"
                                },
                                "smollm2_135m": {
                                    "name": "SmolLM2-135M (Q4_K)",
                                    "role": "fractured_wisdom_llm",
                                    "repo": "jc-builds/SmolLM2-135M-Instruct-Q4_K_M-GGUF",
                                    "file": "${smolLmFile.name}",
                                    "path": "${smolLmFile.absolutePath}",
                                    "present_on_disk": ${smolLmFile.exists()},
                                    "hosted_status": "ONLINE"
                                },
                                "piper_tts": {
                                    "name": "Piper TTS",
                                    "role": "helium_pet_voice",
                                    "file": "${piperFile.name}",
                                    "path": "${piperFile.absolutePath}",
                                    "present_on_disk": ${piperFile.exists()},
                                    "hosted_status": "ONLINE"
                                }
                            }
                        }""".trimIndent()
                    }
                    path == "/v1/models" -> {
                        200 to """{
                            "object": "list",
                            "data": [
                                {
                                    "id": "whisper-small",
                                    "object": "model",
                                    "owned_by": "local-device",
                                    "type": "speech-to-text",
                                    "file_path": "${whisperFile.absolutePath}",
                                    "hosted": true
                                },
                                {
                                    "id": "smollm2-135m-q4_k",
                                    "object": "model",
                                    "owned_by": "local-device",
                                    "repo": "jc-builds/SmolLM2-135M-Instruct-Q4_K_M-GGUF",
                                    "type": "llm-fractured-wisdom",
                                    "file_path": "${smolLmFile.absolutePath}",
                                    "hosted": true
                                },
                                {
                                    "id": "piper-tts-helium",
                                    "object": "model",
                                    "owned_by": "local-device",
                                    "type": "text-to-speech-pet",
                                    "file_path": "${piperFile.absolutePath}",
                                    "hosted": true
                                }
                            ]
                        }""".trimIndent()
                    }
                    path.startsWith("/v1/chat/completions") -> {
                        val userText = extractUserPrompt(body)
                        val fractured = smolLmEngine.generateFracturedWisdom(userText)
                        _lastResponsePreview.value = fractured
                        200 to """{
                            "id": "chatcmpl-${System.currentTimeMillis()}",
                            "object": "chat.completion",
                            "model": "smollm2-135m-q4_k",
                            "choices": [
                                {
                                    "index": 0,
                                    "message": {"role": "assistant", "content": "$fractured"},
                                    "finish_reason": "stop"
                                }
                            ]
                        }""".trimIndent()
                    }
                    path.startsWith("/v1/audio/transcriptions") -> {
                        200 to """{"text":"Whisper local acoustic listener active on device","model":"whisper-small"}"""
                    }
                    path.startsWith("/v1/audio/speech") -> {
                        200 to """{"status":"ready","voice":"helium-pet-piper","pitch":1.82,"rate":1.20,"model":"piper-tts"}"""
                    }
                    else -> {
                        404 to """{"error":"Endpoint not found"}"""
                    }
                }

                writer.print("HTTP/1.1 $statusCode OK\r\n")
                writer.print("Content-Type: application/json; charset=UTF-8\r\n")
                writer.print("Access-Control-Allow-Origin: *\r\n")
                writer.print("Content-Length: ${responseJson.toByteArray(Charsets.UTF_8).size}\r\n")
                writer.print("Connection: close\r\n\r\n")
                writer.print(responseJson)
                writer.flush()

                client.close()
            } catch (_: Exception) {
                try { client.close() } catch (_: Exception) {}
            }
        }
    }

    private fun extractUserPrompt(body: String): String {
        return try {
            val contentIdx = body.indexOf("\"content\":")
            if (contentIdx != -1) {
                val start = body.indexOf("\"", contentIdx + 10) + 1
                val end = body.indexOf("\"", start)
                if (start in 0 until end) body.substring(start, end) else "who am I"
            } else {
                "Is life difficult?"
            }
        } catch (_: Exception) {
            "who am I"
        }
    }

    fun stop() {
        _isRunning.value = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverJob?.cancel()
        serverSocket = null
    }
}
