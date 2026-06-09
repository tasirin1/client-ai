package com.example.aiclient.terminal

import android.content.Context
import java.io.*
import java.net.ServerSocket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class CodeServer(private val context: Context, private val port: Int = 8080) {
    private var serverSocket: ServerSocket? = null
    private var running = false
    private val workspace: File
        get() = File(context.filesDir, "workspace")

    interface Callback {
        fun onOutput(text: String)
    }
    private var callback: Callback? = null
    fun setCallback(cb: Callback) { callback = cb }

    fun start() {
        if (running) return
        workspace.mkdirs()
        running = true
        serverSocket = ServerSocket(port)
        Thread { serve() }.apply { 
            name = "CodeServer" 
            isDaemon = true
            start() 
        }
    }

    fun stop() {
        running = false
        serverSocket?.close()
    }

    fun isRunning(): Boolean = running
    fun getPort(): Int = port

    private fun serve() {
        while (running) {
            try {
                val client = serverSocket?.accept() ?: break
                Thread { handleClient(client) }.start()
            } catch (_: Exception) { if (!running) break }
        }
    }

    private fun handleClient(client: java.net.Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val rawPath = parts[1]
            val path = URLDecoder.decode(rawPath.split("?").first(), "UTF-8")
            val query = if (rawPath.contains("?")) rawPath.substringAfter("?") else ""

            // Read headers
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) break
                val colon = line.indexOf(':')
                if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
            }

            // Read body if POST
            var body = ""
            if (method == "POST") {
                val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                if (contentLength > 0) {
                    val buf = CharArray(contentLength)
                    reader.read(buf, 0, contentLength)
                    body = String(buf)
                }
            }

            val response = when {
                path == "/" || path == "/index.html" -> serveFile("index.html", "text/html")
                path == "/terminal" -> serveFile("terminal.html", "text/html")
                path.startsWith("/api/") -> handleApi(method, path, query, body)
                path.startsWith("/static/") -> serveAsset(path.removePrefix("/static/"))
                else -> "HTTP/1.1 404 NOT FOUND\r\nContent-Length: 0\r\n\r\n"
            }

            client.getOutputStream().write(response.toByteArray(StandardCharsets.UTF_8))
        } catch (_: Exception) { 
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun handleApi(method: String, path: String, query: String, body: String): String {
        return when {
            path == "/api/exec" && method == "POST" -> execCommand(body)
            path == "/api/files" && method == "GET" -> listFiles()
            path == "/api/files" && method == "POST" -> writeFile(body)
            path == "/api/read" && method == "GET" -> readFile(query)
            path == "/api/delete" && method == "POST" -> deleteFile(body)
            path == "/api/status" -> statusJson()
            else -> jsonResponse(mapOf("error" to "not found"), 404)
        }
    }

    private fun execCommand(body: String): String {
        try {
            val cmd = parseJson(body)["command"]?.trim() ?: return errorJson("no command")
            callback?.onOutput("$ %s\n".format(cmd))
            
            val process = ProcessBuilder()
                .command("/system/bin/sh", "-c", cmd)
                .directory(workspace)
                .redirectErrorStream(true)
                .start()
            
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            
            callback?.onOutput(output)
            
            return jsonResponse(mapOf(
                "exitCode" to exitCode,
                "output" to output
            ))
        } catch (e: Exception) {
            return errorJson(e.message ?: "error")
        }
    }

    private fun listFiles(): String {
        val files = workspace.walkTopDown().map { file ->
            mapOf(
                "name" to file.name,
                "path" to file.absolutePath.removePrefix(workspace.absolutePath).ifEmpty { "/" },
                "isDir" to file.isDirectory,
                "size" to file.length(),
                "lastModified" to file.lastModified()
            )
        }.toList()
        return jsonResponse(mapOf("files" to files, "cwd" to workspace.absolutePath))
    }

    private fun writeFile(body: String): String {
        try {
            val data = parseJson(body)
            val filePath = data["path"] ?: return errorJson("no path")
            val content = data["content"] ?: ""
            val file = File(workspace, filePath)
            file.parentFile?.mkdirs()
            file.writeText(content)
            return jsonResponse(mapOf("success" to true, "path" to filePath))
        } catch (e: Exception) {
            return errorJson(e.message ?: "error")
        }
    }

    private fun readFile(query: String): String {
        try {
            val params = parseQuery(query)
            val filePath = params["path"] ?: return errorJson("no path")
            val file = File(workspace, filePath)
            if (!file.exists() || !file.isFile) return errorJson("not found")
            val content = file.readText()
            return jsonResponse(mapOf("content" to content, "path" to filePath))
        } catch (e: Exception) {
            return errorJson(e.message ?: "error")
        }
    }

    private fun deleteFile(body: String): String {
        try {
            val data = parseJson(body)
            val path = data["path"] ?: return errorJson("no path")
            val file = File(workspace, path)
            file.deleteRecursively()
            return jsonResponse(mapOf("success" to true))
        } catch (e: Exception) {
            return errorJson(e.message ?: "error")
        }
    }

    private fun statusJson(): String = jsonResponse(mapOf(
        "running" to running,
        "port" to port,
        "workspace" to workspace.absolutePath,
        "os" to System.getProperty("os.name", "unknown"),
        "arch" to System.getProperty("os.arch", "unknown"),
    ))

    private fun serveFile(filename: String, mime: String): String {
        return try {
            val content = readAssetFile(filename)
            "HTTP/1.1 200 OK\r\nContent-Type: $mime; charset=utf-8\r\nAccess-Control-Allow-Origin: *\r\n\r\n$content"
        } catch (_: Exception) {
            "HTTP/1.1 404 NOT FOUND\r\nContent-Length: 0\r\n\r\n"
        }
    }

    private fun serveAsset(path: String): String {
        return try {
            val content = readAssetFile(path)
            val mime = when {
                path.endsWith(".js") -> "application/javascript"
                path.endsWith(".css") -> "text/css"
                path.endsWith(".html") -> "text/html"
                path.endsWith(".png") -> "image/png"
                else -> "text/plain"
            }
            "HTTP/1.1 200 OK\r\nContent-Type: $mime; charset=utf-8\r\nAccess-Control-Allow-Origin: *\r\n\r\n$content"
        } catch (_: Exception) {
            "HTTP/1.1 404 NOT FOUND\r\nContent-Length: 0\r\n\r\n"
        }
    }

    private fun readAssetFile(filename: String): String {
        return context.assets.open("code_server/$filename").bufferedReader().readText()
    }

    private fun jsonResponse(data: Map<String, Any?>, code: Int = 200): String {
        val json = buildJson(data)
        return "HTTP/1.1 $code OK\r\nContent-Type: application/json\r\nAccess-Control-Allow-Origin: *\r\n\r\n$json"
    }

    private fun errorJson(msg: String): String = jsonResponse(mapOf("error" to msg), 400)

    companion object {
        fun buildJson(data: Map<String, Any?>): String {
            val sb = StringBuilder("{")
            data.entries.forEachIndexed { idx, (k, v) ->
                if (idx > 0) sb.append(",")
                sb.append("\"$k\":")
                when (v) {
                    is String -> sb.append("\"").append(v.replace("\"", "\\\"")).append("\"")
                    is Number -> sb.append(v)
                    is Boolean -> sb.append(v)
                    is List<*> -> sb.append("[" + v.joinToString(",") { item -> "\"$item\"" } + "]")
                    is Map<*, *> -> sb.append(buildJson(v as Map<String, Any?>))
                    null -> sb.append("null")
                    else -> sb.append("\"$v\"")
                }
            }
            sb.append("}")
            return sb.toString()
        }

        fun parseJson(jsonStr: String): Map<String, String> {
            val map = mutableMapOf<String, String>()
            // Simple JSON parser - works for flat key-value objects
            val cleaned = jsonStr.trim().removeSurrounding("{", "}")
            // Find all "key":"value" pairs
            val regex = """\s*"([^"]+)"\s*:\s*"([^"]*)"\s*""".toRegex()
            regex.findAll(cleaned).forEach { match ->
                map[match.groupValues[1]] = match.groupValues[2]
            }
            // Fallback: find all key:value without quotes
            if (map.isEmpty()) {
                val bareRegex = """\s*"?([^":]+)"?\s*:\s*"?([^",}]+)"?\s*""".toRegex()
                bareRegex.findAll(cleaned).forEach { match ->
                    map[match.groupValues[1].trim()] = match.groupValues[2].trim()
                }
            }
            return map
        }

        fun parseQuery(query: String): Map<String, String> {
            return query.split("&").filter { it.isNotBlank() }.associate {
                val parts = it.split("=", limit = 2)
                URLDecoder.decode(parts[0], "UTF-8") to (parts.getOrNull(1)?.let { URLDecoder.decode(it, "UTF-8") } ?: "")
            }
        }
    }
}
