package com.homehub.app.network

import com.homehub.app.data.Device
import com.homehub.app.data.DeviceBrand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Talks directly to CozyLife smart plugs/lights on your own WiFi network.
 * Reimplemented from scratch based on observing the (unencrypted) protocol
 * their own app uses: no cloud, no account, no encryption at this layer.
 *
 * Discovery: UDP broadcast on port 6095.
 * Control:   plain JSON + "\r\n" over TCP on port 5555, straight to the
 *            device's own LAN IP.
 */
class CozyLifeClient {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        const val DISCOVERY_PORT = 6095
        const val CONTROL_PORT = 5555
        const val BROADCAST_ADDRESS = "255.255.255.255"

        // Command ids used on the wire.
        private const val CMD_SEARCH = 0
        private const val CMD_SET_PROPERTY = 3
    }

    /**
     * Broadcasts a discovery request and collects replies for [timeoutMs].
     * Any CozyLife device already on this WiFi network and already paired
     * (via the CozyLife app, one-time) will reply with its info.
     */
    suspend fun discover(timeoutMs: Int = 3000): List<Device> = withContext(Dispatchers.IO) {
        val found = mutableMapOf<String, Device>()
        val socket = DatagramSocket(null).apply {
            reuseAddress = true
            broadcast = true
            soTimeout = 500
            bind(InetSocketAddress(0))
        }

        try {
            val sn = System.currentTimeMillis().toString()
            val request = """{"cmd":$CMD_SEARCH,"pv":0,"sn":"$sn","msg":{}}"""
            val requestBytes = request.toByteArray(Charsets.UTF_8)
            val broadcastAddr = InetAddress.getByName(BROADCAST_ADDRESS)

            val deadline = System.currentTimeMillis() + timeoutMs
            // Send a few times, UDP broadcast can get dropped and devices
            // may be momentarily busy.
            repeat(4) {
                socket.send(DatagramPacket(requestBytes, requestBytes.size, broadcastAddr, DISCOVERY_PORT))
                val buf = ByteArray(2048)
                val readUntil = System.currentTimeMillis() + 400
                while (System.currentTimeMillis() < readUntil) {
                    try {
                        val packet = DatagramPacket(buf, buf.size)
                        socket.receive(packet)
                        val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        parseDiscoveryReply(text)?.let { found[it.remoteId] = it }
                    } catch (_: Exception) {
                        // timeout on this read attempt, fine, keep looping
                    }
                }
            }

            // Drain anything still trickling in until the overall deadline.
            while (System.currentTimeMillis() < deadline) {
                try {
                    val buf = ByteArray(2048)
                    val packet = DatagramPacket(buf, buf.size)
                    socket.receive(packet)
                    val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    parseDiscoveryReply(text)?.let { found[it.remoteId] = it }
                } catch (_: Exception) {
                    break
                }
            }
        } finally {
            socket.close()
        }

        found.values.toList()
    }

    private fun parseDiscoveryReply(text: String): Device? = runCatching {
        val root = json.parseToJsonElement(text).jsonObject
        val msg = root["msg"]?.jsonObject ?: return null
        val did = msg["did"]?.jsonPrimitive?.contentOrNull ?: return null
        val ip = msg["ip"]?.jsonPrimitive?.contentOrNull
        Device(
            id = "cozylife_$did",
            brand = DeviceBrand.COZYLIFE,
            name = "CozyLife device",
            remoteId = did,
            ip = ip
        )
    }.getOrNull()

    /** Sends a JSON command to the device's own IP and returns immediately;
     *  CozyLife's own app does not wait for an acknowledgement on this path. */
    private suspend fun sendRaw(ip: String, cmd: Int, msgJson: String, timeoutMs: Int = 1500) =
        withContext(Dispatchers.IO) {
            val sn = System.currentTimeMillis().toString()
            val payload = """{"msg":$msgJson,"pv":0,"cmd":$cmd,"sn":"$sn"}"""
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, CONTROL_PORT), timeoutMs)
                socket.getOutputStream().write((payload + "\r\n").toByteArray(Charsets.UTF_8))
                socket.getOutputStream().flush()
            }
        }

    suspend fun turnOn(ip: String) = sendRaw(ip, CMD_SET_PROPERTY, """{"data":{"1":255},"attr":[1]}""")
    suspend fun turnOff(ip: String) = sendRaw(ip, CMD_SET_PROPERTY, """{"data":{"1":0},"attr":[1]}""")

    /**
     * Sets an arbitrary data point (brightness, color, etc).
     * NOTE: dp id "1" is confirmed as the power switch. Brightness/color/
     * color-temperature dp ids are model-specific and weren't in the code
     * we mapped, capture them by watching what the real CozyLife app sends
     * (e.g. with a packet capture on your phone) once your device is in
     * hand, then wire the id in here.
     */
    suspend fun setDataPoint(ip: String, dpId: String, value: Int) =
        sendRaw(ip, CMD_SET_PROPERTY, """{"data":{"$dpId":$value},"attr":[$dpId]}""")
}
