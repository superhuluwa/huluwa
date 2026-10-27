package com.huluwa.android.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Xml
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.util.concurrent.TimeUnit

/** 不依赖 Google Play 服务的局域网 UPnP AVTransport 投屏。 */
data class CastDevice(val id: String, val name: String, val controlUrl: String, val serviceType: String, val network: Network)

class DlnaCastClient(context: Context) {
    private val application = context.applicationContext
    private val connectivity = application.getSystemService(ConnectivityManager::class.java)
    private val http = OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS).followRedirects(false).build()

    suspend fun discover(): List<CastDevice> = withContext(Dispatchers.IO) {
        val network = connectivity.allNetworks.firstOrNull { net ->
            connectivity.getNetworkCapabilities(net)?.let {
                it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            } == true
        } ?: throw IllegalStateException("请连接与电视相同的局域网")
        val lock = application.getSystemService(WifiManager::class.java)?.createMulticastLock("huluwa-dlna")
        val locations = linkedSetOf<String>()
        try {
            lock?.setReferenceCounted(false)
            lock?.acquire()
            DatagramSocket().use { socket ->
                network.bindSocket(socket)
                socket.soTimeout = 600
                val group = InetAddress.getByName("239.255.255.250")
                repeat(2) {
                    listOf("urn:schemas-upnp-org:device:MediaRenderer:1", "urn:schemas-upnp-org:service:AVTransport:1").forEach { target ->
                        val message = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\n" +
                            "MAN: \"ssdp:discover\"\r\nMX: 2\r\nST: $target\r\n\r\n").toByteArray(Charsets.US_ASCII)
                        socket.send(DatagramPacket(message, message.size, group, 1900))
                    }
                }
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while (System.nanoTime() < deadline && locations.size < 24) {
                    currentCoroutineContext().ensureActive()
                    val packet = DatagramPacket(ByteArray(8192), 8192)
                    try { socket.receive(packet) } catch (_: SocketTimeoutException) { continue }
                    val response = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    response.lineSequence().firstOrNull { it.substringBefore(':').equals("location", true) }
                        ?.substringAfter(':')?.trim()?.takeIf { validHttp(it) }?.let(locations::add)
                }
            }
        } finally { if (lock?.isHeld == true) lock.release() }
        coroutineScope {
            locations.map { location -> async {
                try { describe(network, location) }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) { null }
            } }.awaitAll().filterNotNull().distinctBy { it.id }.sortedBy { it.name }
        }
    }

    private fun client(network: Network): OkHttpClient = http.newBuilder().socketFactory(network.socketFactory)
        .dns(object : okhttp3.Dns {
            override fun lookup(hostname: String): List<InetAddress> = network.getAllByName(hostname).toList()
        }).build()

    private fun describe(network: Network, location: String): CastDevice? {
        val xml = client(network).newCall(Request.Builder().url(location).get().build()).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.string()?.takeIf { it.length <= 1_000_000 } ?: return null
        }
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(StringReader(xml))
        }
        var name = ""; var uuid = ""; var base = location
        var service = ""; var control = ""; var selectedService = ""; var selectedControl = ""
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) when (parser.name) {
                "friendlyName" -> { val value = parser.nextText(); if (name.isBlank()) name = value }
                "UDN" -> { val value = parser.nextText(); if (uuid.isBlank()) uuid = value }
                "URLBase" -> parser.nextText().trim().takeIf(::validHttp)?.let { base = it }
                "service" -> { service = ""; control = "" }
                "serviceType" -> service = parser.nextText().trim()
                "controlURL" -> control = parser.nextText().trim()
            }
            if (parser.eventType == XmlPullParser.END_TAG && parser.name == "service" &&
                service.startsWith("urn:schemas-upnp-org:service:AVTransport:") && selectedService.isBlank()) {
                selectedService = service
                selectedControl = control
            }
            parser.next()
        }
        if (selectedService.isBlank() || selectedControl.isBlank()) return null
        val url = URI(base).resolve(selectedControl).toString()
        if (!validHttp(url)) return null
        return CastDevice(uuid.ifBlank { url }, name.ifBlank { URI(location).host }, url, selectedService, network)
    }

    suspend fun cast(device: CastDevice, media: BrowserVideo) = withContext(Dispatchers.IO) {
        require(MovieWebVideo.isM3u8(media.url)) { "请先选择可播放的 m3u8 视频" }
        val metadata = "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" " +
            "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">" +
            "<item id=\"0\" parentID=\"-1\" restricted=\"1\"><dc:title>${escape(media.title)}</dc:title>" +
            "<upnp:class>object.item.videoItem.movie</upnp:class>" +
            "<res protocolInfo=\"http-get:*:application/vnd.apple.mpegurl:*\">${escape(media.url)}</res></item></DIDL-Lite>"
        // 部分电视要求先退出原播放状态，空闲设备拒绝 Stop 时仍可继续设置新资源。
        try { soap(device, "Stop", "<InstanceID>0</InstanceID>") } catch (_: Exception) { }
        currentCoroutineContext().ensureActive()
        soap(device, "SetAVTransportURI", "<InstanceID>0</InstanceID><CurrentURI>${escape(media.url)}</CurrentURI>" +
            "<CurrentURIMetaData>${escape(metadata)}</CurrentURIMetaData>")
        soap(device, "Play", "<InstanceID>0</InstanceID><Speed>1</Speed>")
    }
    suspend fun play(device: CastDevice) = withContext(Dispatchers.IO) {
        soap(device, "Play", "<InstanceID>0</InstanceID><Speed>1</Speed>")
    }
    suspend fun pause(device: CastDevice) = withContext(Dispatchers.IO) { soap(device, "Pause", "<InstanceID>0</InstanceID>") }
    suspend fun stop(device: CastDevice) = withContext(Dispatchers.IO) { soap(device, "Stop", "<InstanceID>0</InstanceID>") }

    private fun soap(device: CastDevice, action: String, arguments: String) {
        val body = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
            "<s:Body><u:$action xmlns:u=\"${escape(device.serviceType)}\">$arguments</u:$action></s:Body></s:Envelope>"
        val request = Request.Builder().url(device.controlUrl).header("SOAPAction", "\"${device.serviceType}#$action\"")
            .post(body.toRequestBody("text/xml; charset=utf-8".toMediaType())).build()
        client(device.network).newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("电视未接受播放指令（${response.code}），请检查设备或切换视频线路")
            val text = response.body?.string().orEmpty()
            if (text.contains("<errorCode>") || text.contains(":Fault")) throw IllegalStateException("电视暂不支持此播放指令，请切换设备或视频线路")
        }
    }

    private fun validHttp(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.host != null && uri.userInfo == null && uri.scheme in setOf("http", "https")
    }.getOrDefault(false)
    private fun escape(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
