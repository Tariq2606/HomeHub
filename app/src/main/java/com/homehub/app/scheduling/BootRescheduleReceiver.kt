package com.homehub.app.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.homehub.app.HomeHubApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        val app = context.applicationContext as HomeHubApp
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val schedules = app.settingsRepository.schedules.first()
                Scheduler.rescheduleAll(context, schedules)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
