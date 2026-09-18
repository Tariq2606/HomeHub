package com.homehub.app.network

import com.homehub.app.data.Device
import com.homehub.app.data.DeviceBrand
import com.homehub.app.data.DevicePoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Talks to Tuya's official Cloud OpenAPI (https://developer.tuya.com), the
 * same API Home Assistant / Homebridge integrations use. Nothing here is
 * reverse-engineered: it's built from Tuya's current published signing
 * spec (HMAC-SHA256, updated algorithm as of their June 2021 change) and
 * documented Device Management / Device Control endpoints.
 *
 * You need:
 *  - A free Cloud Project on https://iot.tuya.com (Development Method: Smart Home)
 *  - Your Access ID / Access Secret from that project's Overview page
 *  - Your Tuya/Smart Life app account linked to the project (Devices tab ->
 *    Link Tuya App Account), which is how the project is allowed to see
 *    and control YOUR devices
 */
class TuyaCloudClient(
    private val regionHost: String,   // e.g. "openapi.tuyaus.com"
    private val accessId: String,
    private val accessSecret: String
) {
    private val http = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    private var cachedToken: String? = null
    private var tokenExpiresAtMillis: Long = 0L

    private val baseUrl get() = "https://$regionHost"

    // ---- signing -----------------------------------------------------

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun hmacSha256Upper(message: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val raw = mac.doFinal(message.toByteArray(Charsets.UTF_8))
        return raw.joinToString("") { "%02x".format(it) }.uppercase()
    }

    /**
     * Builds the headers Tuya requires on every call.
     * pathWithQuery must include the leading "/" and any query string, e.g. "/v1.0/token?grant_type=1"
     * body is the raw JSON body string, or "" for GET requests with no body.
     */
    private fun signedHeaders(method: String, pathWithQuery: String, body: String, accessToken: String?): Map<String, String> {
        val t = System.currentTimeMillis().toString()
        val contentSha256 = sha256Hex(body)
        val stringToSign = listOf(method, contentSha256, "", pathWithQuery).joinToString("\n")
        val toSign = buildString {
            append(accessId)
            if (accessToken != null) append(accessToken)
            append(t)
            append(stringToSign)
        }
        val sign = hmacSha256Upper(toSign, accessSecret)

        return buildMap {
            put("client_id", accessId)
            put("sign", sign)
            put("sign_method", "HMAC-SHA256")
            put("t", t)
            if (accessToken != null) put("access_token", accessToken)
        }
    }

    private suspend fun request(method: String, pathWithQuery: String, body: String? = null, needsToken: Boolean = true): JsonObject =
        withContext(Dispatchers.IO) {
            val token = if (needsToken) getValidAccessToken() else null
            val headers = signedHeaders(method, pathWithQuery, body ?: "", token)

            val builder = Request.Builder().url(baseUrl + pathWithQuery)
            headers.forEach { (k, v) -> builder.addHeader(k, v) }

            val req = when (method) {
                "GET" -> builder.get().build()
                "POST" -> builder.post((body ?: "{}").toRequestBody("application/json".toMediaType())).build()
                "PUT" -> builder.put((body ?: "{}").toRequestBody("application/json".toMediaType())).build()
                else -> error("Unsupported method $method")
            }

            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                json.parseToJsonElement(text.ifBlank { "{}" }).jsonObject
            }
        }

    private suspend fun getValidAccessToken(): String {
        val now = System.currentTimeMillis()
        cachedToken?.let { if (now < tokenExpiresAtMillis) return it }

        val result = request("GET", "/v1.0/token?grant_type=1", needsToken = false)
        val success = result["success"]?.jsonPrimitive?.boolean ?: false
        if (!success) error("Tuya token request failed: ${result["msg"]?.jsonPrimitive?.contentOrNull}")

        val r = result["result"]!!.jsonObject
        val token = r["access_token"]!!.jsonPrimitive.content
        val expiresInSeconds = r["expire_time"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600L

        cachedToken = token
        // refresh a little early to be safe
        tokenExpiresAtMillis = now + (expiresInSeconds - 60).coerceAtLeast(30) * 1000
        return token
    }

    // ---- public API ----------------------------------------------------

    /** The uid of your linked app account, needed to auto-discover devices. Find it on the
     *  IoT Platform project's Devices > Link Tuya App Account list. */
    suspend fun getDevicesForUser(uid: String): List<Device> {
        val result = request("GET", "/v1.0/users/$uid/devices")
        val list = result["result"]?.jsonArray ?: JsonArray(emptyList())
        return list.map { el ->
            val o = el.jsonObject
            Device(
                id = "tuya_${o["id"]!!.jsonPrimitive.content}",
                brand = DeviceBrand.TUYA,
                name = o["name"]?.jsonPrimitive?.contentOrNull ?: "Tuya device",
                remoteId = o["id"]!!.jsonPrimitive.content,
                online = o["online"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
            )
        }
    }

    /** Add a single device manually by its Tuya device_id (from the IoT Platform device list),
     *  useful before you've wired up uid-based auto-discovery. */
    suspend fun getDeviceById(deviceId: String): Device {
        val result = request("GET", "/v1.0/devices/$deviceId")
        val o = result["result"]!!.jsonObject
        return Device(
            id = "tuya_$deviceId",
            brand = DeviceBrand.TUYA,
            name = o["name"]?.jsonPrimitive?.contentOrNull ?: "Tuya device",
            remoteId = deviceId,
            online = o["online"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
        )
    }

    suspend fun getStatus(deviceId: String): List<DevicePoint> {
        val result = request("GET", "/v1.0/devices/$deviceId/status")
        val list = result["result"]?.jsonArray ?: JsonArray(emptyList())
        return list.map { el ->
            val o = el.jsonObject
            val rawValue = o["value"]
            DevicePoint(
                code = o["code"]!!.jsonPrimitive.content,
                // Most status values are simple primitives (bool/number/string).
                // A few device types return structured values, fall back to
                // the raw JSON text for those rather than crashing.
                value = runCatching { rawValue?.jsonPrimitive?.contentOrNull }.getOrNull()
                    ?: rawValue?.toString().orEmpty()
            )
        }
    }

    /** Fetches the list of commands/status codes this specific device supports,
     *  so brightness/color codes don't have to be hardcoded per model. */
    suspend fun getFunctions(deviceId: String): JsonArray {
        val result = request("GET", "/v1.0/devices/$deviceId/functions")
        return result["result"]?.jsonObject?.get("functions")?.jsonArray ?: JsonArray(emptyList())
    }

    suspend fun sendCommand(deviceId: String, code: String, value: Any) {
        val valueJson = when (value) {
            is Boolean -> value.toString()
            is Int -> value.toString()
            is Double -> value.toString()
            is String -> "\"$value\""
            else -> "\"$value\""
        }
        val body = """{"commands":[{"code":"$code","value":$valueJson}]}"""
        request("POST", "/v1.0/devices/$deviceId/commands", body)
    }

    suspend fun turnOn(deviceId: String) = sendCommand(deviceId, "switch_1", true)
    suspend fun turnOff(deviceId: String) = sendCommand(deviceId, "switch_1", false)
}
