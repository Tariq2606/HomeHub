package com.homehub.app.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.homehub.app.data.DeviceSchedule
import java.util.Calendar

/**
 * Turns a [DeviceSchedule] into a real Android exact alarm. Android has no
 * "repeat weekly on these days" primitive that survives Doze reliably, so
 * instead we always schedule the single NEXT matching occurrence, and the
 * receiver re-arms the following one right after it fires (see
 * ScheduleAlarmReceiver).
 */
object Scheduler {

    const val EXTRA_SCHEDULE_ID = "schedule_id"
    const val EXTRA_DEVICE_ID = "device_id"
    const val EXTRA_ACTION = "action" // "ON" or "OFF"

    private fun pendingIntentFor(context: Context, schedule: DeviceSchedule): PendingIntent {
        val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            putExtra(EXTRA_SCHEDULE_ID, schedule.id)
            putExtra(EXTRA_DEVICE_ID, schedule.deviceId)
            putExtra(EXTRA_ACTION, schedule.action.name)
        }
        return PendingIntent.getBroadcast(
            context,
            schedule.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Calendar.DAY_OF_WEEK is 1=Sunday..7=Saturday, matching our stored daysOfWeek. */
    private fun nextTriggerMillis(schedule: DeviceSchedule): Long {
        val now = Calendar.getInstance()
        for (dayOffset in 0..7) {
            val candidate = (now.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, dayOffset)
                set(Calendar.HOUR_OF_DAY, schedule.hour)
                set(Calendar.MINUTE, schedule.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val dayMatches = schedule.daysOfWeek.contains(candidate.get(Calendar.DAY_OF_WEEK))
            if (dayMatches && candidate.timeInMillis > now.timeInMillis) {
                return candidate.timeInMillis
            }
        }
        // Fallback: same time tomorrow, shouldn't normally hit this.
        return now.apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
    }

    fun schedule(context: Context, schedule: DeviceSchedule) {
        if (!schedule.enabled || schedule.daysOfWeek.isEmpty()) return
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val triggerAt = nextTriggerMillis(schedule)
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pendingIntentFor(context, schedule)
        )
    }

    fun cancel(context: Context, schedule: DeviceSchedule) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(pendingIntentFor(context, schedule))
    }

    fun rescheduleAll(context: Context, schedules: List<DeviceSchedule>) {
        schedules.forEach { schedule(context, it) }
    }
}
