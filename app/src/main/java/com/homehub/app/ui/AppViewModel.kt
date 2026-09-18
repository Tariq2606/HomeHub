package com.homehub.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.homehub.app.HomeHubApp
import com.homehub.app.data.Device
import com.homehub.app.data.DeviceBrand
import com.homehub.app.data.DeviceSchedule
import com.homehub.app.data.SettingsRepository
import com.homehub.app.data.ThresholdAlert
import com.homehub.app.network.TuyaCloudClient
import com.homehub.app.scheduling.Scheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = getApplication<Application>() as HomeHubApp
    private val settings: SettingsRepository get() = app.settingsRepository

    val devices: StateFlow<List<Device>> get() = _devices.asStateFlow()
    private val _devices = MutableStateFlow<List<Device>>(emptyList())

    val thresholds: StateFlow<List<ThresholdAlert>> get() = _thresholds.asStateFlow()
    private val _thresholds = MutableStateFlow<List<ThresholdAlert>>(emptyList())

    val schedules: StateFlow<List<DeviceSchedule>> get() = _schedules.asStateFlow()
    private val _schedules = MutableStateFlow<List<DeviceSchedule>>(emptyList())

    val electricityCostPerKwh: StateFlow<Double> get() = _cost.asStateFlow()
    private val _cost = MutableStateFlow(0.0)

    val tuyaConfig: StateFlow<SettingsRepository.TuyaConfig?> get() = _tuyaConfig.asStateFlow()
    private val _tuyaConfig = MutableStateFlow<SettingsRepository.TuyaConfig?>(null)

    val statusMessage: StateFlow<String?> get() = _statusMessage.asStateFlow()
    private val _statusMessage = MutableStateFlow<String?>(null)

    val isBusy: StateFlow<Boolean> get() = _isBusy.asStateFlow()
    private val _isBusy = MutableStateFlow(false)

    init {
        viewModelScope.launch { _devices.value = settings.devices.first() }
        viewModelScope.launch { _thresholds.value = settings.thresholds.first() }
        viewModelScope.launch { _schedules.value = settings.schedules.first() }
        viewModelScope.launch { _cost.value = settings.electricityCostPerKwh.first() }
        viewModelScope.launch { _tuyaConfig.value = settings.tuyaConfig.first() }
    }

    private fun tuyaClient(): TuyaCloudClient? {
        val c = _tuyaConfig.value ?: return null
        return TuyaCloudClient(c.regionHost, c.accessId, c.accessSecret)
    }

    private fun setStatus(msg: String?) { _statusMessage.value = msg }

    // ---- settings ----

    fun saveTuyaConfig(accessId: String, accessSecret: String, regionHost: String, linkedUid: String) {
        viewModelScope.launch {
            settings.saveTuyaConfig(accessId, accessSecret, regionHost, linkedUid)
            _tuyaConfig.value = SettingsRepository.TuyaConfig(accessId, accessSecret, regionHost, linkedUid)
        }
    }

    fun saveElectricityCost(cost: Double) {
        viewModelScope.launch {
            settings.saveElectricityCost(cost)
            _cost.value = cost
        }
    }

    // ---- device management ----

    private fun upsertDevice(device: Device) {
        val current = _devices.value.toMutableList()
        val idx = current.indexOfFirst { it.id == device.id }
        if (idx >= 0) current[idx] = device else current.add(device)
        _devices.value = current
        viewModelScope.launch { settings.saveDevices(current) }
    }

    fun removeDevice(deviceId: String) {
        val current = _devices.value.filterNot { it.id == deviceId }
        _devices.value = current
        viewModelScope.launch { settings.saveDevices(current) }
    }

    fun addTuyaDeviceById(deviceId: String) {
        val client = tuyaClient() ?: run { setStatus("Set up your Tuya credentials in Settings first"); return }
        viewModelScope.launch {
            _isBusy.value = true
            runCatching { client.getDeviceById(deviceId.trim()) }
                .onSuccess { upsertDevice(it); setStatus("Added ${it.name}") }
                .onFailure { setStatus("Couldn't add device: ${it.message}") }
            _isBusy.value = false
        }
    }

    fun syncTuyaDevicesFromLinkedAccount() {
        val client = tuyaClient() ?: run { setStatus("Set up your Tuya credentials in Settings first"); return }
        val uid = _tuyaConfig.value?.linkedUid.orEmpty()
        if (uid.isBlank()) { setStatus("Add your linked account's UID in Settings first"); return }
        viewModelScope.launch {
            _isBusy.value = true
            runCatching { client.getDevicesForUser(uid) }
                .onSuccess { found -> found.forEach { upsertDevice(it) }; setStatus("Synced ${found.size} Tuya device(s)") }
                .onFailure { setStatus("Sync failed: ${it.message}") }
            _isBusy.value = false
        }
    }

    fun discoverCozyLifeDevices() {
        viewModelScope.launch {
            _isBusy.value = true
            runCatching { app.cozyLifeClient.discover() }
                .onSuccess { found ->
                    found.forEach { d ->
                        // keep an existing name if we already knew this device
                        val existingName = _devices.value.firstOrNull { it.id == d.id }?.name
                        upsertDevice(d.copy(name = existingName ?: "CozyLife device"))
                    }
                    setStatus("Found ${found.size} CozyLife device(s) on your network")
                }
                .onFailure { setStatus("Discovery failed: ${it.message}") }
            _isBusy.value = false
        }
    }

    fun renameDevice(deviceId: String, newName: String) {
        val d = _devices.value.firstOrNull { it.id == deviceId } ?: return
        upsertDevice(d.copy(name = newName))
    }

    // ---- device control ----

    fun toggleDevice(device: Device) {
        viewModelScope.launch {
            val turnOn = !device.isOn
            runCatching {
                when (device.brand) {
                    DeviceBrand.TUYA -> {
                        val client = tuyaClient() ?: error("Tuya not configured")
                        if (turnOn) client.turnOn(device.remoteId) else client.turnOff(device.remoteId)
                    }
                    DeviceBrand.COZYLIFE -> {
                        val ip = device.ip ?: error("No known IP yet, run discovery again")
                        if (turnOn) app.cozyLifeClient.turnOn(ip) else app.cozyLifeClient.turnOff(ip)
                    }
                }
            }.onSuccess {
                upsertDevice(device.copy(isOn = turnOn))
            }.onFailure {
                setStatus("Couldn't switch ${device.name}: ${it.message}")
            }
        }
    }

    fun refreshTuyaStatus(device: Device) {
        if (device.brand != DeviceBrand.TUYA) return
        val client = tuyaClient() ?: return
        viewModelScope.launch {
            runCatching { client.getStatus(device.remoteId) }
                .onSuccess { points ->
                    val on = points.firstOrNull { it.code == "switch_1" }?.value?.toBoolean() ?: device.isOn
                    upsertDevice(device.copy(points = points, isOn = on))
                }
        }
    }

    // ---- thresholds ----

    fun addThreshold(threshold: ThresholdAlert) {
        val updated = _thresholds.value + threshold
        _thresholds.value = updated
        viewModelScope.launch { settings.saveThresholds(updated) }
    }

    fun removeThreshold(id: String) {
        val updated = _thresholds.value.filterNot { it.id == id }
        _thresholds.value = updated
        viewModelScope.launch { settings.saveThresholds(updated) }
    }

    // ---- schedules ----

    fun addSchedule(schedule: DeviceSchedule) {
        val updated = _schedules.value + schedule
        _schedules.value = updated
        viewModelScope.launch { settings.saveSchedules(updated) }
        Scheduler.schedule(app, schedule)
    }

    fun removeSchedule(id: String) {
        val removed = _schedules.value.firstOrNull { it.id == id }
        val updated = _schedules.value.filterNot { it.id == id }
        _schedules.value = updated
        viewModelScope.launch { settings.saveSchedules(updated) }
        removed?.let { Scheduler.cancel(app, it) }
    }

    fun clearStatus() { _statusMessage.value = null }
}
