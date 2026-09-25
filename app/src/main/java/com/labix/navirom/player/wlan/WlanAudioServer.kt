package com.labix.navirom.player.wlan

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.labix.navirom.data.cache.OfflineDownloadManager
import com.labix.navirom.data.model.NaviromTrack
import kotlinx.coroutines.*
import java.io.*
import java.net.*
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class WlanAudioServer(
    private val context: Context,
    private val downloadManager: OfflineDownloadManager,
    private val urlResolverProvider: () -> ((String) -> String)?,
    private val currentTrackProvider: () -> NaviromTrack?,
    private val currentQueueProvider: () -> List<NaviromTrack>
) {
    companion object {
        private const val TAG = "WlanAudioServer"
        const val DEFAULT_PORT = 8765
    }

    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var actualPort: Int = DEFAULT_PORT
        private set

    fun getLocalIpAddress(): String {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager != null) {
                @Suppress("DEPRECATION")
                val ipInt = wifiManager.connectionInfo.ipAddress
                if (ipInt != 0) {
                    val ip = String.format(
                        Locale.US,
                        "%d.%d.%d.%d",
                        ipInt and 0xff,
                        ipInt shr 8 and 0xff,
                        ipInt shr 16 and 0xff,
                        ipInt shr 24 and 0xff
                    )
                    if (ip != "0.0.0.0") return ip
                }
            }
        } catch (_: Exception) {}

        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (_: Exception) {}

        return "127.0.0.1"
    }

    fun getTrackStreamUrl(trackId: String): String {
        val ip = getLocalIpAddress()
        return "http://$ip:$actualPort/audio/track/$trackId"
    }

    fun getCurrentTrackStreamUrl(): String {
        val ip = getLocalIpAddress()
        return "http://$ip:$actualPort/audio/current"
    }

    fun getPlaylistUrl(): String {
        val ip = getLocalIpAddress()
        return "http://$ip:$actualPort/playlist.m3u"
    }

    @Synchronized
    fun start(preferredPort: Int = DEFAULT_PORT) {
        if (isRunning.get()) return

        serverJob = scope.launch {
            var port = preferredPort
            var attempts = 0
            while (attempts < 10 && !isRunning.get()) {
                try {
                    val ss = ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"))
                    serverSocket = ss
                    actualPort = ss.localPort
                    isRunning.set(true)
                    Log.i(TAG, "WLAN Audio Server started on http://${getLocalIpAddress()}:$actualPort")
                    listenForClients(ss)
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "Port $port unavailable, trying next port: ${e.message}")
                    port++
                    attempts++
                }
            }
        }
    }

    private suspend fun listenForClients(ss: ServerSocket) = withContext(Dispatchers.IO) {
        while (isRunning.get() && !ss.isClosed) {
            try {
                val clientSocket = ss.accept()
                launch {
                    handleClient(clientSocket)
                }
            } catch (e: Exception) {
                if (isRunning.get()) {
                    Log.e(TAG, "ServerSocket accept exception: ${e.message}")
                }
            }
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 30000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val firstLine = reader.readLine() ?: return
            val parts = firstLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0].uppercase(Locale.US)
            val uri = parts[1]

            val headers = mutableMapOf<String, String>()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrBlank()) break
                val headerIdx = line!!.indexOf(":")
                if (headerIdx != -1) {
                    val key = line!!.substring(0, headerIdx).trim().lowercase(Locale.US)
                    val value = line!!.substring(headerIdx + 1).trim()
                    headers[key] = value
                }
            }

            val rangeHeader = headers["range"]

            when {
                uri.startsWith("/audio/track/") -> {
                    val trackId = uri.removePrefix("/audio/track/").substringBefore("?").substringBefore(".mp3")
                    serveTrack(socket, method, trackId, rangeHeader)
                }
                uri.startsWith("/audio/current") -> {
                    val cur = currentTrackProvider()
                    if (cur != null) {
                        serveTrack(socket, method, cur.id, rangeHeader)
                    } else {
                        sendNotFound(socket)
                    }
                }
                uri.startsWith("/playlist.m3u") -> {
                    servePlaylist(socket)
                }
                uri.startsWith("/status") -> {
                    serveStatus(socket)
                }
                else -> {
                    sendNotFound(socket)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Client socket handled/closed: ${e.message}")
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun serveTrack(socket: Socket, method: String, trackId: String, rangeHeader: String?) {
        val cachedFile = downloadManager.getLocalFileForTrack(trackId)
        if (cachedFile.exists() && cachedFile.length() > 0) {
            serveLocalFile(socket, method, cachedFile, rangeHeader)
            return
        }

        val resolver = urlResolverProvider()
        val streamUrl = resolver?.invoke(trackId)
        if (!streamUrl.isNullOrEmpty()) {
            serveProxyUrl(socket, method, streamUrl, rangeHeader)
            return
        }

        sendNotFound(socket)
    }

    private fun serveLocalFile(socket: Socket, method: String, file: File, rangeHeader: String?) {
        val totalLength = file.length()
        val mimeType = when {
            file.name.endsWith(".flac", ignoreCase = true) -> "audio/flac"
            file.name.endsWith(".wav", ignoreCase = true) -> "audio/wav"
            file.name.endsWith(".aac", ignoreCase = true) -> "audio/aac"
            file.name.endsWith(".m4a", ignoreCase = true) -> "audio/mp4"
            file.name.endsWith(".ogg", ignoreCase = true) -> "audio/ogg"
            else -> "audio/mpeg"
        }

        var startByte = 0L
        var endByte = totalLength - 1L
        var isPartial = false

        if (!rangeHeader.isNullOrBlank() && rangeHeader.startsWith("bytes=")) {
            val rangeVal = rangeHeader.removePrefix("bytes=").trim()
            val dashIdx = rangeVal.indexOf("-")
            if (dashIdx != -1) {
                val startStr = rangeVal.substring(0, dashIdx).trim()
                val endStr = rangeVal.substring(dashIdx + 1).trim()
                if (startStr.isNotEmpty()) {
                    startByte = startStr.toLongOrNull() ?: 0L
                }
                if (endStr.isNotEmpty()) {
                    endByte = endStr.toLongOrNull() ?: (totalLength - 1L)
                }
                if (startByte <= endByte && endByte < totalLength) {
                    isPartial = true
                }
            }
        }

        val contentLength = endByte - startByte + 1L
        val out = BufferedOutputStream(socket.getOutputStream())
        val writer = PrintWriter(OutputStreamWriter(out))

        if (isPartial) {
            writer.print("HTTP/1.1 206 Partial Content\r\n")
            writer.print("Content-Range: bytes $startByte-$endByte/$totalLength\r\n")
        } else {
            writer.print("HTTP/1.1 200 OK\r\n")
        }
        writer.print("Content-Type: $mimeType\r\n")
        writer.print("Content-Length: $contentLength\r\n")
        writer.print("Accept-Ranges: bytes\r\n")
        writer.print("Connection: keep-alive\r\n")
        writer.print("Access-Control-Allow-Origin: *\r\n")
        writer.print("\r\n")
        writer.flush()

        if (method != "HEAD") {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(startByte)
                val buffer = ByteArray(32 * 1024)
                var bytesRemaining = contentLength
                while (bytesRemaining > 0) {
                    val toRead = minOf(buffer.size.toLong(), bytesRemaining).toInt()
                    val read = raf.read(buffer, 0, toRead)
                    if (read == -1) break
                    out.write(buffer, 0, read)
                    bytesRemaining -= read
                }
                out.flush()
            }
        }
    }

    private fun serveProxyUrl(socket: Socket, method: String, urlString: String, rangeHeader: String?) {
        try {
            val url = URI(urlString).toURL()
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.requestMethod = method
            if (!rangeHeader.isNullOrBlank()) {
                conn.setRequestProperty("Range", rangeHeader)
            }
            conn.setRequestProperty("User-Agent", "Navirom/WlanAudioServer")
            conn.connect()

            val responseCode = conn.responseCode
            val out = BufferedOutputStream(socket.getOutputStream())
            val writer = PrintWriter(OutputStreamWriter(out))

            writer.print("HTTP/1.1 $responseCode ${conn.responseMessage ?: "OK"}\r\n")
            val contentType = conn.contentType ?: "audio/mpeg"
            writer.print("Content-Type: $contentType\r\n")
            val length = conn.contentLengthLong
            if (length > 0) {
                writer.print("Content-Length: $length\r\n")
            }
            conn.getHeaderField("Content-Range")?.let {
                writer.print("Content-Range: $it\r\n")
            }
            writer.print("Accept-Ranges: bytes\r\n")
            writer.print("Connection: keep-alive\r\n")
            writer.print("Access-Control-Allow-Origin: *\r\n")
            writer.print("\r\n")
            writer.flush()

            if (method != "HEAD") {
                val input = conn.inputStream
                val buffer = ByteArray(32 * 1024)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    out.write(buffer, 0, read)
                }
                out.flush()
            }
            conn.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "Error proxying audio stream from $urlString: ${e.message}")
            try {
                val out = socket.getOutputStream()
                val writer = PrintWriter(OutputStreamWriter(out))
                writer.print("HTTP/1.1 502 Bad Gateway\r\n\r\n")
                writer.flush()
            } catch (_: Exception) {}
        }
    }

    private fun servePlaylist(socket: Socket) {
        val queue = currentQueueProvider()
        val ip = getLocalIpAddress()
        val out = BufferedOutputStream(socket.getOutputStream())
        val writer = PrintWriter(OutputStreamWriter(out))
        writer.print("HTTP/1.1 200 OK\r\n")
        writer.print("Content-Type: audio/x-mpegurl\r\n")
        writer.print("Access-Control-Allow-Origin: *\r\n")
        writer.print("\r\n")
        writer.print("#EXTM3U\r\n")
        queue.forEach { track ->
            val dur = track.durationSeconds.toLong().coerceAtLeast(0L)
            writer.print("#EXTINF:$dur,${track.artist} - ${track.title}\r\n")
            writer.print("http://$ip:$actualPort/audio/track/${track.id}\r\n")
        }
        writer.flush()
    }

    private fun serveStatus(socket: Socket) {
        val track = currentTrackProvider()
        val ip = getLocalIpAddress()
        val durationMs = (track?.durationSeconds ?: 0) * 1000L
        val json = """
            {
                "status": "running",
                "ip": "$ip",
                "port": $actualPort,
                "currentTrack": {
                    "id": "${track?.id ?: ""}",
                    "title": "${track?.title?.replace("\"", "\\\"") ?: ""}",
                    "artist": "${track?.artist?.replace("\"", "\\\"") ?: ""}",
                    "durationMs": $durationMs
                }
            }
        """.trimIndent()

        val out = BufferedOutputStream(socket.getOutputStream())
        val writer = PrintWriter(OutputStreamWriter(out))
        writer.print("HTTP/1.1 200 OK\r\n")
        writer.print("Content-Type: application/json\r\n")
        writer.print("Content-Length: ${json.toByteArray().size}\r\n")
        writer.print("Access-Control-Allow-Origin: *\r\n")
        writer.print("\r\n")
        writer.print(json)
        writer.flush()
    }

    private fun sendNotFound(socket: Socket) {
        try {
            val out = socket.getOutputStream()
            val writer = PrintWriter(OutputStreamWriter(out))
            writer.print("HTTP/1.1 404 Not Found\r\n")
            writer.print("Content-Type: text/plain\r\n")
            writer.print("Content-Length: 9\r\n\r\n")
            writer.print("Not Found")
            writer.flush()
        } catch (_: Exception) {}
    }

    @Synchronized
    fun stop() {
        isRunning.set(false)
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
        Log.i(TAG, "WLAN Audio Server stopped.")
    }
}
