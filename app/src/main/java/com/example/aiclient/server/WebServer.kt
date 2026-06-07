package com.example.aiclient.server

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.example.aiclient.data.ChatRepository
import com.example.aiclient.data.MessageEntity
import com.example.aiclient.data.SessionEntity
import com.example.aiclient.data.SettingsStore
import com.example.aiclient.network.GenericApiClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.map

class WebServer(
    private val context: Context,
    private val settingsStore: SettingsStore,
    private val chatRepository: ChatRepository,
    private val apiClient: GenericApiClient,
) {
    private var serverSocket: ServerSocket? = null
    private var running = false
    private var serverThread: Thread? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var htmlCache: String? = null
    private val port = 8080

    fun getLocalIp(): String {
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ipInt = wifi.connectionInfo?.ipAddress ?: 0
            return String.format("%d.%d.%d.%d", ipInt and 0xff, ipInt shr 8 and 0xff, ipInt shr 16 and 0xff, ipInt shr 24 and 0xff)
        } catch (e: Exception) {
            return "127.0.0.1"
        }
    }

    fun start() {
        if (running) return
        running = true
        serverThread = thread(true) {
            try {
                serverSocket = ServerSocket(port)
                Log.i("WebServer", "Server started on port $port")
                while (running) {
                    try {
                        val client = serverSocket?.accept() ?: continue
                        thread(false) { handleClient(client) }
                    } catch (e: Exception) {
                        if (running) Log.e("WebServer", "Accept error", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("WebServer", "Start error", e)
                running = false
            }
        }
    }

    fun stop() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serverThread?.join(1000)
        serverSocket = null
        serverThread = null
    }

    val isRunning get() = running

    private fun handleClient(client: Socket) {
        try {
            client.use { socket ->
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0]
                val fullPath = parts[1]
                val path = fullPath.split("?").first()
                val query = fullPath.split("?").let { if (it.size > 1) it[1] else "" }
                val queryParams = parseQuery(query)

                // Read headers
                val headers = mutableMapOf<String, String>()
                var line: String?
                var contentLength = 0
                while (reader.readLine().also { line = it } != null) {
                    val h = line ?: break
                    if (h.isBlank()) break
                    val colon = h.indexOf(':')
                    if (colon > 0) {
                        val key = h.substring(0, colon).trim().lowercase()
                        val value = h.substring(colon + 1).trim()
                        headers[key] = value
                        if (key == "content-length") contentLength = value.toIntOrNull() ?: 0
                    }
                }

                // Read body
                val body = if (contentLength > 0) {
                    val buf = CharArray(contentLength)
                    reader.read(buf, 0, contentLength)
                    String(buf)
                } else ""

                when {
                    method == "GET" && path == "/" -> serveFile(socket, "index.html")
                    method == "GET" && path == "/api/status" -> handleApi(socket, """{"status":"ok","ip":"${getLocalIp()}","port":$port}""")
                    method == "GET" && path == "/api/sessions" -> handleSessions(socket)
                    method == "GET" && path == "/api/messages" -> handleMessages(socket, queryParams)
                    method == "POST" && path == "/api/chat" -> handleChat(socket, body)
                    method == "POST" && path == "/api/session/new" -> handleNewSession(socket)
                    method == "POST" && path == "/api/session/delete" -> handleDeleteSession(socket, body)
                    method == "GET" && path == "/api/prefs" -> handlePrefs(socket)
                    method == "GET" && path.startsWith("/") -> serveFile(socket, path.removePrefix("/"))
                    else -> sendResponse(socket, 404, "Not Found")
                }
            }
        } catch (e: Exception) {
            Log.e("WebServer", "Handle error", e)
        }
    }

    private fun handleSessions(socket: Socket) {
        scope.launch {
            val sessions = chatRepository.observeSessions().first()
            val lastMessages = chatRepository.observeLastMessagesForAllSessions().first()
            val msgMap = lastMessages.associateBy { it.sessionId }
            val json = buildString {
                append("[")
                sessions.forEachIndexed { i, s ->
                    if (i > 0) append(",")
                    val last = msgMap[s.id]
                    append("""{"id":"${s.id}","title":"${esc(s.title)}","createdAt":${s.createdAt},"updatedAt":${s.updatedAt},"lastMessage":"${esc(last?.content?.take(100) ?: "")}"}""")
                }
                append("]")
            }
            handleApi(socket, json)
        }
    }

    private fun handleMessages(socket: Socket, query: Map<String, String>) {
        val sessionId = query["sessionId"] ?: run {
            sendResponse(socket, 400, "Missing sessionId")
            return
        }
        scope.launch {
            val messages = chatRepository.getMessagesOnce(sessionId)
            val json = buildString {
                append("[")
                messages.forEachIndexed { i, m ->
                    if (i > 0) append(",")
                    append("""{"id":"${m.id}","role":"${m.role}","content":${jsonStr(m.content)},"createdAt":${m.createdAt}}""")
                }
                append("]")
            }
            handleApi(socket, json)
        }
    }

    private fun handleChat(socket: Socket, body: String) {
        scope.launch {
            try {
                val json = org.json.JSONObject(body)
                val input = json.optString("message", "")
                val sessionId = json.optString("sessionId", "")
                val prefs = settingsStore.prefsFlow.first()

                // Get or create session
                val session = if (sessionId.isNotBlank()) {
                    chatRepository.getSessionOnce(sessionId) ?: chatRepository.createSession("Chat Baru")
                } else {
                    chatRepository.createSession("Chat Baru")
                }

                val history = chatRepository.getMessagesOnce(session.id)

                // Build & send request (mirror AppViewModel logic)
                val (url, headers, body) = AppViewModelBridge.buildRequest(prefs, history, input)
                chatRepository.addMessage(session.id, "request", input)

                val result = apiClient.execute(url, "POST", headers, body)
                val extracted = AppViewModelBridge.extractResponseText(prefs.apiProvider, result.responseBody)
                chatRepository.addMessage(session.id, "response", extracted)

                // Auto rename
                val currentSession = chatRepository.getSessionOnce(session.id)
                if (currentSession?.title?.startsWith("Chat") != false) {
                    chatRepository.renameSession(session.id, input.take(28).trim().ifBlank { "Chat ${session.id.take(4)}" })
                }

                val respJson = """{"sessionId":"${session.id}","response":${jsonStr(extracted)}}"""
                handleApi(socket, respJson)
            } catch (e: Exception) {
                handleApi(socket, """{"error":"${esc(e.message ?: "Unknown error")}"}""")
            }
        }
    }

    private fun handleNewSession(socket: Socket) {
        scope.launch {
            val session = chatRepository.createSession("Chat Baru")
            handleApi(socket, """{"id":"${session.id}","title":"Chat Baru"}""")
        }
    }

    private fun handleDeleteSession(socket: Socket, body: String) {
        scope.launch {
            try {
                val json = org.json.JSONObject(body)
                val sessionId = json.optString("sessionId", "")
                if (sessionId.isNotBlank()) chatRepository.deleteSession(sessionId)
                handleApi(socket, """{"status":"ok"}""")
            } catch (e: Exception) {
                handleApi(socket, """{"error":"${esc(e.message ?: "")}"}""")
            }
        }
    }

    private fun handlePrefs(socket: Socket) {
        scope.launch {
            val prefs = settingsStore.prefsFlow.first()
            handleApi(socket, """{"provider":"${esc(prefs.apiProvider)}","model":"${esc(prefs.model)}"}""")
        }
    }

    private fun handleApi(socket: Socket, json: String) {
        sendResponse(socket, 200, json, "application/json")
    }

    private fun serveFile(socket: Socket, path: String) {
        try {
            val html = getHtmlContent()
            val mime = when {
                path.endsWith(".css") -> "text/css"
                path.endsWith(".js") -> "application/javascript"
                path.endsWith(".png") -> "image/png"
                path.endsWith(".ico") -> "image/x-icon"
                else -> "text/html; charset=utf-8"
            }
            sendResponse(socket, 200, html, mime)
        } catch (e: Exception) {
            sendResponse(socket, 404, "Not Found")
        }
    }

    private fun getHtmlContent(): String {
        if (htmlCache == null) {
            try {
                val inputStream = context.assets.open("web/index.html")
                htmlCache = inputStream.bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                htmlCache = "<html><body><h1>AI Client Web</h1><p>Server running</p></body></html>"
            }
        }
        return htmlCache!!
    }

    private fun sendResponse(socket: Socket, code: Int, body: String, mime: String = "text/plain") {
        val writer = OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)
        val status = when (code) {
            200 -> "OK"; 400 -> "Bad Request"; 404 -> "Not Found"; 500 -> "Internal Server Error"
            else -> "Unknown"
        }
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        writer.write("HTTP/1.1 $code $status\r\n")
        writer.write("Content-Type: $mime\r\n")
        writer.write("Content-Length: ${bytes.size}\r\n")
        writer.write("Access-Control-Allow-Origin: *\r\n")
        writer.write("Connection: close\r\n")
        writer.write("\r\n")
        writer.flush()
        socket.getOutputStream().write(bytes)
        socket.getOutputStream().flush()
    }

    private fun parseQuery(query: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        query.split("&").forEach { pair ->
            val eq = pair.indexOf('=')
            if (eq > 0) {
                val key = URLDecoder.decode(pair.substring(0, eq), "UTF-8")
                val value = URLDecoder.decode(pair.substring(eq + 1), "UTF-8")
                map[key] = value
            }
        }
        return map
    }

    companion object {
        fun esc(s: String): String = s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

        fun jsonStr(s: String): String = "\"" + esc(s) + "\""
    }
}
