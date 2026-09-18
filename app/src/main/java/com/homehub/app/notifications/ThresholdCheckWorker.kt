package com.homehub.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.homehub.app.HomeHubApp
import com.homehub.app.data.DeviceBrand
import com.homehub.app.network.TuyaCloudClient
import kotlinx.coroutines.flow.first

/**
 * Runs on a periodic schedule (see setup() below) and checks every enabled
 * threshold against a fresh reading. Currently only Tuya devices are
 * checked, since that's the brand we've confirmed reports metering data;
 * CozyLife devices are skipped here unless/until we confirm their metering
 * dp ids (see CozyLifeClient).
 *
 * Note: Android's WorkManager enforces a 15-minute minimum interval for
 * periodic work, so alerts fire on a check-every-~15-minutes cadence, not
 * instantly.
 */
class ThresholdCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as HomeHubApp
        val thresholds = app.settingsRepository.thresholds.first().filter { it.enabled }
        if (thresholds.isEmpty()) return Result.success()

        val devices = app.settingsRepository.devices.first()
        val tuyaConfig = app.settingsRepository.tuyaConfig.first() ?: return Result.success()
        val client = TuyaCloudClient(tuyaConfig.regionHost, tuyaConfig.accessId, tuyaConfig.accessSecret)

        thresholds.forEach { threshold ->
            val device = devices.firstOrNull { it.id == threshold.deviceId } ?: return@forEach
            if (device.brand != DeviceBrand.TUYA) return@forEach

            val points = runCatching { client.getStatus(device.remoteId) }.getOrNull() ?: return@forEach
            val code = if (threshold.metric == "voltage") "cur_voltage" else "cur_power"
            val divisor = 10.0 // Tuya reports these as tenths
            val raw = points.firstOrNull { it.code == code }?.value?.toDoubleOrNull() ?: return@forEach
            val actual = raw / divisor

            if (actual < threshold.belowValue) {
                NotificationHelper.notify(
                    applicationContext,
                    notificationId = threshold.id.hashCode(),
                    title = threshold.label,
                    body = "${device.name}: ${threshold.metric} is $actual, below your ${threshold.belowValue} threshold"
                )
            }
        }

        return Result.success()
    }
}
