package com.homehub.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "homehub_settings")

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * Everything the app needs to remember between launches, lives in one
 * DataStore. Nothing here is sent anywhere except directly to Tuya's
 * official cloud API (for Tuya devices) or directly to your own devices
 * on your own LAN (for CozyLife devices).
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val TUYA_ACCESS_ID = stringPreferencesKey("tuya_access_id")
        val TUYA_ACCESS_SECRET = stringPreferencesKey("tuya_access_secret")
        val TUYA_REGION_HOST = stringPreferencesKey("tuya_region_host")
        val TUYA_LINKED_UID = stringPreferencesKey("tuya_linked_uid")
        val ELECTRICITY_COST_PER_KWH = stringPreferencesKey("electricity_cost_per_kwh")
        val DEVICES_JSON = stringPreferencesKey("devices_json")
        val THRESHOLDS_JSON = stringPreferencesKey("thresholds_json")
        val SCHEDULES_JSON = stringPreferencesKey("schedules_json")
    }

    data class TuyaConfig(
        val accessId: String,
        val accessSecret: String,
        val regionHost: String,
        val linkedUid: String
    )

    val tuyaConfig: Flow<TuyaConfig?> = context.dataStore.data.map { prefs ->
        val id = prefs[Keys.TUYA_ACCESS_ID]
        val secret = prefs[Keys.TUYA_ACCESS_SECRET]
        val host = prefs[Keys.TUYA_REGION_HOST]
        val uid = prefs[Keys.TUYA_LINKED_UID] ?: ""
        if (id.isNullOrBlank() || secret.isNullOrBlank() || host.isNullOrBlank()) null
        else TuyaConfig(id, secret, host, uid)
    }

    suspend fun saveTuyaConfig(accessId: String, accessSecret: String, regionHost: String, linkedUid: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TUYA_ACCESS_ID] = accessId
            prefs[Keys.TUYA_ACCESS_SECRET] = accessSecret
            prefs[Keys.TUYA_REGION_HOST] = regionHost
            prefs[Keys.TUYA_LINKED_UID] = linkedUid
        }
    }

    val electricityCostPerKwh: Flow<Double> = context.dataStore.data.map { prefs ->
        prefs[Keys.ELECTRICITY_COST_PER_KWH]?.toDoubleOrNull() ?: 0.0
    }

    suspend fun saveElectricityCost(cost: Double) {
        context.dataStore.edit { prefs -> prefs[Keys.ELECTRICITY_COST_PER_KWH] = cost.toString() }
    }

    val devices: Flow<List<Device>> = context.dataStore.data.map { prefs ->
        prefs[Keys.DEVICES_JSON]?.let {
            runCatching { json.decodeFromString<List<Device>>(it) }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    suspend fun saveDevices(devices: List<Device>) {
        context.dataStore.edit { prefs -> prefs[Keys.DEVICES_JSON] = json.encodeToString(devices) }
    }

    val thresholds: Flow<List<ThresholdAlert>> = context.dataStore.data.map { prefs ->
        prefs[Keys.THRESHOLDS_JSON]?.let {
            runCatching { json.decodeFromString<List<ThresholdAlert>>(it) }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    suspend fun saveThresholds(thresholds: List<ThresholdAlert>) {
        context.dataStore.edit { prefs -> prefs[Keys.THRESHOLDS_JSON] = json.encodeToString(thresholds) }
    }

    val schedules: Flow<List<DeviceSchedule>> = context.dataStore.data.map { prefs ->
        prefs[Keys.SCHEDULES_JSON]?.let {
            runCatching { json.decodeFromString<List<DeviceSchedule>>(it) }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    suspend fun saveSchedules(schedules: List<DeviceSchedule>) {
        context.dataStore.edit { prefs -> prefs[Keys.SCHEDULES_JSON] = json.encodeToString(schedules) }
    }
}
