package com.homehub.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.homehub.app.data.SettingsRepository
import com.homehub.app.network.CozyLifeClient
import com.homehub.app.notifications.ThresholdCheckWorker
import java.util.concurrent.TimeUnit

class HomeHubApp : Application() {

    lateinit var settingsRepository: SettingsRepository
    val cozyLifeClient = CozyLifeClient()

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        createNotificationChannel()
        scheduleThresholdChecks()
    }

    private fun scheduleThresholdChecks() {
        // 15 minutes is WorkManager's minimum interval for periodic work.
        val request = PeriodicWorkRequestBuilder<ThresholdCheckWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "threshold_check",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                THRESHOLD_CHANNEL_ID,
                "Device alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts you set for low current/voltage/wattage on a device"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val THRESHOLD_CHANNEL_ID = "threshold_alerts"
    }
}
