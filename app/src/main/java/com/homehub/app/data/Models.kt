package com.homehub.app.data

import kotlinx.serialization.Serializable

/** Which backend a device is controlled through. */
@Serializable
enum class DeviceBrand { TUYA, COZYLIFE }

/**
 * A single status/data point reported by a device, e.g. power switch,
 * brightness, or (for metering-capable plugs) current/voltage/power.
 */
@Serializable
data class DevicePoint(
    val code: String,
    val value: String
)

@Serializable
data class Device(
    val id: String,
    val brand: DeviceBrand,
    val name: String,
    // Tuya: the cloud device_id. CozyLife: the "did" from discovery.
    val remoteId: String,
    // CozyLife only: last known LAN IP, needed to send TCP commands.
    val ip: String? = null,
    val isOn: Boolean = false,
    val online: Boolean = true,
    val points: List<DevicePoint> = emptyList()
) {
    /** Metering codes Tuya uses on power-monitoring plugs. Null if unsupported/unknown. */
    fun wattage(): Double? = points.firstOrNull { it.code == "cur_power" }?.value?.toDoubleOrNull()?.div(10.0)
    fun voltage(): Double? = points.firstOrNull { it.code == "cur_voltage" }?.value?.toDoubleOrNull()?.div(10.0)
    fun current(): Double? = points.firstOrNull { it.code == "cur_current" }?.value?.toDoubleOrNull()
}

@Serializable
data class ThresholdAlert(
    val id: String,
    val deviceId: String,
    val label: String,
    val metric: String, // "watts" or "voltage"
    val belowValue: Double,
    val enabled: Boolean = true
)

@Serializable
enum class ScheduleAction { ON, OFF }

@Serializable
data class DeviceSchedule(
    val id: String,
    val deviceId: String,
    val label: String,
    val hour: Int,
    val minute: Int,
    val action: ScheduleAction,
    val daysOfWeek: Set<Int>, // 1 = Sunday .. 7 = Saturday, Calendar-style
    val enabled: Boolean = true
)
