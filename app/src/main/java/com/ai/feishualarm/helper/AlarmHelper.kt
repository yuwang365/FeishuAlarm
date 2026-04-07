package com.ai.feishualarm.helper

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.ai.feishualarm.receiver.AlarmReceiver
import java.util.Calendar

object AlarmHelper {
    private const val PREFS_NAME = "AlarmPrefs"
    private const val ALARM_TIMES_KEY = "AlarmTimes"

    fun getAlarmTimes(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(ALARM_TIMES_KEY, setOf())?.toList()?.sorted() ?: listOf()
    }

    fun addAlarmTime(context: Context, time: String) {
        Log.i("wangyu","add Alarm: $time")
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val times = prefs.getStringSet(ALARM_TIMES_KEY, setOf())?.toMutableSet() ?: mutableSetOf()
        if (times.add(time)) {
            prefs.edit().putStringSet(ALARM_TIMES_KEY, times).apply()
            scheduleAllAlarms(context)
        }
    }

    fun removeAlarmTime(context: Context, time: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val times = prefs.getStringSet(ALARM_TIMES_KEY, setOf())?.toMutableSet() ?: mutableSetOf()
        if (times.remove(time)) {
            prefs.edit().putStringSet(ALARM_TIMES_KEY, times).apply()
            cancelAlarm(context, time)
        }
    }

    fun scheduleAllAlarms(context: Context) {
        val times = getAlarmTimes(context)
        for (time in times) {
            scheduleAlarm(context, time)
        }
    }

    private fun scheduleAlarm(context: Context, time: String) {
        val parts = time.split(":")
        if (parts.size != 2) return
        val hour = parts[0].toInt()
        val minute = parts[1].toInt()

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("ALARM_TIME", time)
        }
        
        // Use hash of time string as request code to keep it unique for each time slot
        val requestCode = time.hashCode()
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        }
        
        Log.d("AlarmHelper", "Alarm scheduled for $time at ${calendar.time}")
    }

    private fun cancelAlarm(context: Context, time: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val requestCode = time.hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }
}
