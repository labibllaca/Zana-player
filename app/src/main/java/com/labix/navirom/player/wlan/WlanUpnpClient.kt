package com.labix.navirom.player.wlan

import android.util.Log
import com.labix.navirom.data.model.NaviromTrack
import kotlinx.coroutines.*
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.*
import java.util.Locale

class WlanUpnpClient {
    companion object {
        private const val TAG = "WlanUpnpClient"
        private const val SSDP_IP = "239.255.255.250"
        private const val SSDP_PORT = 1900
    }

    suspend fun discoverRenderers(timeoutMs: Long = 4000L): List<WlanSpeakerDevice> = withContext(Dispatchers.IO) {
        val discoveredDevices = mutableMapOf<String, WlanSpeakerDevice>()
        var socket: DatagramSocket? = null

        val searchTargets = listOf(
            "urn:schemas-upnp-org:device:MediaRenderer:1",
            "urn:schemas-upnp-org:service:AVTransport:1",
            "ssdp:all"
        )

        try {
            socket = DatagramSocket()
            socket.broadcast = true
            socket.soTimeout = (timeoutMs / 2).toInt().coerceAtLeast(1000)

            val ssdpGroup = InetAddress.getByName(SSDP_IP)

            // Broadcast M-SEARCH requests
            for (st in searchTargets) {
                val query = "M-SEARCH * HTTP/1.1\r\n" +
                        "HOST: $SSDP_IP:$SSDP_PORT\r\n" +
                        "MAN: \"ssdp:discover\"\r\n" +
                        "MX: 3\r\n" +
                        "ST: $st\r\n\r\n"
                val bytes = query.toByteArray()
                val packet = DatagramPacket(bytes, bytes.size, ssdpGroup, SSDP_PORT)
                try {
                    socket.send(packet)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to send M-SEARCH packet: ${e.message}")
                }
            }

            val startTime = System.currentTimeMillis()
            val buffer = ByteArray(4096)

            while (System.currentTimeMillis() - startTime < timeoutMs) {
                try {
                    val receivePacket = DatagramPacket(buffer, buffer.size)
                    socket.receive(receivePacket)
                    val response = String(receivePacket.data, 0, receivePacket.length)
                    val location = extractHeader(response, "LOCATION")
                    if (!location.isNullOrBlank()) {
                        val senderIp = receivePacket.address.hostAddress ?: ""
                        launch {
                            val device = parseDeviceXml(location, senderIp)
                            if (device != null) {
                                synchronized(discoveredDevices) {
                                    discoveredDevices[device.id] = device
                                }
                            }
                        }
                    }
                } catch (_: SocketTimeoutException) {
                    break
                } catch (e: Exception) {
                    Log.d(TAG, "Socket receive finished: ${e.message}")
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SSDP Discovery error: ${e.message}")
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }

        // Wait slightly for background XML parses to finish
        delay(600L)
        synchronized(discoveredDevices) {
            discoveredDevices.values.toList()
        }
    }

    private fun extractHeader(response: String, headerName: String): String? {
        val lines = response.lines()
        for (line in lines) {
            val idx = line.indexOf(":")
            if (idx != -1) {
                val key = line.substring(0, idx).trim()
                if (key.equals(headerName, ignoreCase = true)) {
                    return line.substring(idx + 1).trim()
                }
            }
        }
        return null
    }

    suspend fun parseDeviceXml(locationUrl: String, fallbackIp: String = ""): WlanSpeakerDevice? = withContext(Dispatchers.IO) {
        try {
            val url = URI(locationUrl).toURL()
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.requestMethod = "GET"
            conn.connect()

            if (conn.responseCode != 200) return@withContext null

            val xml = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val baseUrl = "${url.protocol}://${url.host}:${url.port}"
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var currentTag = ""
            var friendlyName: String? = null
            var manufacturer: String? = null
            var modelName: String? = null
            var modelNumber: String? = null
            var iconUrl: String? = null
            var currentServiceType: String? = null
            var avTransportControlUrl: String? = null
            var renderingControlControlUrl: String? = null
            var udn: String? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name.lowercase(Locale.US)
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text.trim()
                        if (text.isNotEmpty()) {
                            when (currentTag) {
                                "friendlyname" -> if (friendlyName == null) friendlyName = text
                                "manufacturer" -> if (manufacturer == null) manufacturer = text
                                "modelname" -> if (modelName == null) modelName = text
                                "modelnumber" -> if (modelNumber == null) modelNumber = text
                                "udn" -> if (udn == null) udn = text
                                "servicetype" -> currentServiceType = text
                                "controlurl" -> {
                                    if (currentServiceType?.contains("AVTransport", ignoreCase = true) == true) {
                                        avTransportControlUrl = resolveUrl(baseUrl, text)
                                    } else if (currentServiceType?.contains("RenderingControl", ignoreCase = true) == true) {
                                        renderingControlControlUrl = resolveUrl(baseUrl, text)
                                    }
                                }
                                "url" -> {
                                    if (iconUrl == null && (text.endsWith(".png", true) || text.endsWith(".jpg", true))) {
                                        iconUrl = resolveUrl(baseUrl, text)
                                    }
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            if (avTransportControlUrl.isNullOrEmpty()) {
                // If it doesn't support AVTransport, it's not an audio renderer
                return@withContext null
            }

            val deviceId = udn ?: "${url.host}:${url.port}"
            val name = friendlyName ?: modelName ?: "WLAN Speaker (${url.host})"
            val mfg = manufacturer ?: "UPnP Device"
            val model = modelName ?: "MediaRenderer"

            val speakerType = when {
                mfg.contains("Sonos", ignoreCase = true) || name.contains("Sonos", ignoreCase = true) -> WlanSpeakerType.SONOS
                mfg.contains("Bose", ignoreCase = true) || name.contains("Bose", ignoreCase = true) -> WlanSpeakerType.BOSE
                mfg.contains("Denon", ignoreCase = true) || mfg.contains("Marantz", ignoreCase = true) || mfg.contains("HEOS", ignoreCase = true) -> WlanSpeakerType.HEOS
                mfg.contains("Google", ignoreCase = true) || name.contains("Chromecast", ignoreCase = true) -> WlanSpeakerType.CHROMECAST_AUDIO
                else -> WlanSpeakerType.DLNA_RENDERER
            }

            return@withContext WlanSpeakerDevice(
                id = deviceId,
                name = name,
                ipAddress = url.host ?: fallbackIp,
                port = url.port.let { if (it > 0) it else 1400 },
                manufacturer = mfg,
                modelName = model,
                modelNumber = modelNumber ?: "",
                speakerType = speakerType,
                locationUrl = locationUrl,
                avTransportControlUrl = avTransportControlUrl,
                renderingControlUrl = renderingControlControlUrl,
                iconUrl = iconUrl,
                isOnline = true
            )
        } catch (e: Exception) {
            Log.d(TAG, "Could not parse UPnP XML from $locationUrl: ${e.message}")
            return@withContext null
        }
    }

    private fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        return if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
            relativeUrl
        } else if (relativeUrl.startsWith("/")) {
            "$baseUrl$relativeUrl"
        } else {
            "$baseUrl/$relativeUrl"
        }
    }

    // SOAP Actions
    suspend fun setAvTransportUri(
        controlUrl: String,
        streamUrl: String,
        track: NaviromTrack?
    ): Boolean = withContext(Dispatchers.IO) {
        val didlMetadata = createDidlLiteXml(streamUrl, track)
        val escapedMetadata = escapeXml(didlMetadata)

        val soapBody = """
            <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                <InstanceID>0</InstanceID>
                <CurrentURI>${escapeXml(streamUrl)}</CurrentURI>
                <CurrentURIMetaData>$escapedMetadata</CurrentURIMetaData>
            </u:SetAVTransportURI>
        """.trimIndent()

        sendSoapAction(controlUrl, "urn:schemas-upnp-org:service:AVTransport:1", "SetAVTransportURI", soapBody)
    }

    suspend fun play(controlUrl: String): Boolean = withContext(Dispatchers.IO) {
        val soapBody = """
            <u:Play xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                <InstanceID>0</InstanceID>
                <Speed>1</Speed>
            </u:Play>
        """.trimIndent()

        sendSoapAction(controlUrl, "urn:schemas-upnp-org:service:AVTransport:1", "Play", soapBody)
    }

    suspend fun pause(controlUrl: String): Boolean = withContext(Dispatchers.IO) {
        val soapBody = """
            <u:Pause xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                <InstanceID>0</InstanceID>
            </u:Pause>
        """.trimIndent()

        sendSoapAction(controlUrl, "urn:schemas-upnp-org:service:AVTransport:1", "Pause", soapBody)
    }

    suspend fun stop(controlUrl: String): Boolean = withContext(Dispatchers.IO) {
        val soapBody = """
            <u:Stop xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                <InstanceID>0</InstanceID>
            </u:Stop>
        """.trimIndent()

        sendSoapAction(controlUrl, "urn:schemas-upnp-org:service:AVTransport:1", "Stop", soapBody)
    }

    suspend fun seek(controlUrl: String, targetPositionMs: Long): Boolean = withContext(Dispatchers.IO) {
        val targetTime = formatDurationToHms(targetPositionMs)
        val soapBody = """
            <u:Seek xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                <InstanceID>0</InstanceID>
                <Unit>REL_TIME</Unit>
                <Target>$targetTime</Target>
            </u:Seek>
        """.trimIndent()

        sendSoapAction(controlUrl, "urn:schemas-upnp-org:service:AVTransport:1", "Seek", soapBody)
    }

    suspend fun getPositionInfo(controlUrl: String): Pair<Long, Long>? = withContext(Dispatchers.IO) {
        val soapBody = """
            <u:GetPositionInfo xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                <InstanceID>0</InstanceID>
            </u:GetPositionInfo>
        """.trimIndent()

        val response = sendSoapActionWithResponse(controlUrl, "urn:schemas-upnp-org:service:AVTransport:1", "GetPositionInfo", soapBody)
            ?: return@withContext null

        val relTimeStr = extractXmlTagValue(response, "RelTime")
        val trackDurStr = extractXmlTagValue(response, "TrackDuration")

        val currentMs = relTimeStr?.let { parseHmsToMillis(it) } ?: 0L
        val durMs = trackDurStr?.let { parseHmsToMillis(it) } ?: 0L

        Pair(currentMs, durMs)
    }

    suspend fun getTransportInfo(controlUrl: String): String? = withContext(Dispatchers.IO) {
        val soapBody = """
            <u:GetTransportInfo xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                <InstanceID>0</InstanceID>
            </u:GetTransportInfo>
        """.trimIndent()

        val response = sendSoapActionWithResponse(controlUrl, "urn:schemas-upnp-org:service:AVTransport:1", "GetTransportInfo", soapBody)
            ?: return@withContext null

        extractXmlTagValue(response, "CurrentTransportState")
    }

    suspend fun setVolume(renderingControlUrl: String, volumePercent: Int): Boolean = withContext(Dispatchers.IO) {
        val vol = volumePercent.coerceIn(0, 100)
        val soapBody = """
            <u:SetVolume xmlns:u="urn:schemas-upnp-org:service:RenderingControl:1">
                <InstanceID>0</InstanceID>
                <Channel>Master</Channel>
                <DesiredVolume>$vol</DesiredVolume>
            </u:SetVolume>
        """.trimIndent()

        sendSoapAction(renderingControlUrl, "urn:schemas-upnp-org:service:RenderingControl:1", "SetVolume", soapBody)
    }

    suspend fun getVolume(renderingControlUrl: String): Int? = withContext(Dispatchers.IO) {
        val soapBody = """
            <u:GetVolume xmlns:u="urn:schemas-upnp-org:service:RenderingControl:1">
                <InstanceID>0</InstanceID>
                <Channel>Master</Channel>
            </u:GetVolume>
        """.trimIndent()

        val response = sendSoapActionWithResponse(renderingControlUrl, "urn:schemas-upnp-org:service:RenderingControl:1", "GetVolume", soapBody)
            ?: return@withContext null

        extractXmlTagValue(response, "CurrentVolume")?.toIntOrNull()
    }

    suspend fun setMute(renderingControlUrl: String, isMuted: Boolean): Boolean = withContext(Dispatchers.IO) {
        val desiredMute = if (isMuted) "1" else "0"
        val soapBody = """
            <u:SetMute xmlns:u="urn:schemas-upnp-org:service:RenderingControl:1">
                <InstanceID>0</InstanceID>
                <Channel>Master</Channel>
                <DesiredMute>$desiredMute</DesiredMute>
            </u:SetMute>
        """.trimIndent()

        sendSoapAction(renderingControlUrl, "urn:schemas-upnp-org:service:RenderingControl:1", "SetMute", soapBody)
    }

    private fun sendSoapAction(controlUrl: String, serviceType: String, actionName: String, bodyContent: String): Boolean {
        return sendSoapActionWithResponse(controlUrl, serviceType, actionName, bodyContent) != null
    }

    private fun sendSoapActionWithResponse(
        controlUrl: String,
        serviceType: String,
        actionName: String,
        bodyContent: String
    ): String? {
        try {
            val url = URI(controlUrl).toURL()
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            conn.setRequestProperty("SOAPACTION", "\"$serviceType#$actionName\"")

            val soapEnvelope = """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                    <s:Body>
                        $bodyContent
                    </s:Body>
                </s:Envelope>
            """.trimIndent()

            val bytes = soapEnvelope.toByteArray(Charsets.UTF_8)
            conn.setRequestProperty("Content-Length", bytes.size.toString())

            conn.outputStream.use { it.write(bytes) }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                return resp
            } else {
                val errorStream = conn.errorStream?.bufferedReader()?.use { it.readText() }
                Log.w(TAG, "SOAP $actionName failed (HTTP $responseCode): $errorStream")
                conn.disconnect()
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "SOAP $actionName error on $controlUrl: ${e.message}")
            return null
        }
    }

    private fun createDidlLiteXml(streamUrl: String, track: NaviromTrack?): String {
        val title = escapeXml(track?.title ?: "Navirom Audio Stream")
        val artist = escapeXml(track?.artist ?: "Unknown Artist")
        val album = escapeXml(track?.album ?: "Navirom")
        val duration = track?.let { formatDurationToHms(it.durationSeconds * 1000L) } ?: "00:00:00"

        return """
            <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"
                       xmlns:dc="http://purl.org/dc/elements/1.1/"
                       xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
                <item id="0" parentID="-1" restricted="1">
                    <dc:title>$title</dc:title>
                    <dc:creator>$artist</dc:creator>
                    <upnp:artist>$artist</upnp:artist>
                    <upnp:album>$album</upnp:album>
                    <upnp:class>object.item.audioItem.musicTrack</upnp:class>
                    <res protocolInfo="http-get:*:audio/mpeg:DLNA.ORG_PN=MP3;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000" duration="$duration">$streamUrl</res>
                </item>
            </DIDL-Lite>
        """.trimIndent()
    }

    private fun escapeXml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun formatDurationToHms(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun parseHmsToMillis(hms: String): Long {
        val parts = hms.trim().split(":")
        if (parts.isEmpty()) return 0L
        try {
            return when (parts.size) {
                3 -> {
                    val h = parts[0].toLong()
                    val m = parts[1].toLong()
                    val s = parts[2].substringBefore(".").toDouble().toLong()
                    (h * 3600 + m * 60 + s) * 1000
                }
                2 -> {
                    val m = parts[0].toLong()
                    val s = parts[1].substringBefore(".").toDouble().toLong()
                    (m * 60 + s) * 1000
                }
                1 -> {
                    val s = parts[0].substringBefore(".").toDouble().toLong()
                    s * 1000
                }
                else -> 0L
            }
        } catch (_: Exception) {
            return 0L
        }
    }

    private fun extractXmlTagValue(xml: String, tagName: String): String? {
        val openTag = "<$tagName"
        val closeTag = "</$tagName>"
        val startIdx = xml.indexOf(openTag)
        if (startIdx == -1) return null
        val contentStart = xml.indexOf(">", startIdx) + 1
        val endIdx = xml.indexOf(closeTag, contentStart)
        if (endIdx == -1) return null
        return xml.substring(contentStart, endIdx).trim()
    }
}
