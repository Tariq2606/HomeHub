package com.homehub.app.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.homehub.app.HomeHubApp
import com.homehub.app.data.DeviceBrand
import com.homehub.app.data.ScheduleAction
import com.homehub.app.network.TuyaCloudClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ScheduleAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val deviceId = intent.getStringExtra(Scheduler.EXTRA_DEVICE_ID) ?: return
        val actionName = intent.getStringExtra(Scheduler.EXTRA_ACTION) ?: return
        val scheduleId = intent.getStringExtra(Scheduler.EXTRA_SCHEDULE_ID)

        val pendingResult = goAsync()
        val app = context.applicationContext as HomeHubApp

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val devices = app.settingsRepository.devices.first()
                val device = devices.firstOrNull { it.id == deviceId }
                val turnOn = ScheduleAction.valueOf(actionName) == ScheduleAction.ON

                if (device != null) {
                    when (device.brand) {
                        DeviceBrand.TUYA -> {
                            val cfg = app.settingsRepository.tuyaConfig.first()
                            if (cfg != null) {
                                val client = TuyaCloudClient(cfg.regionHost, cfg.accessId, cfg.accessSecret)
                                if (turnOn) client.turnOn(device.remoteId) else client.turnOff(device.remoteId)
                            }
                        }
                        DeviceBrand.COZYLIFE -> {
                            device.ip?.let { ip ->
                                if (turnOn) app.cozyLifeClient.turnOn(ip) else app.cozyLifeClient.turnOff(ip)
                            }
                        }
                    }
                }

                // Re-arm the next occurrence of this schedule.
                val schedules = app.settingsRepository.schedules.first()
                schedules.firstOrNull { it.id == scheduleId }?.let { Scheduler.schedule(context, it) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
