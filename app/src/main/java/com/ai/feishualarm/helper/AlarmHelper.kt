package com.ai.feishualarm.helper

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.ai.feishualarm.receiver.AlarmReceiver
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object AlarmHelper {
    private const val PREFS_NAME = "AlarmPrefs"
    private const val ALARM_TIMES_KEY = "AlarmTimes"
    private const val LAST_FIRED_PREFIX = "last_fired_"

    private val dayKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private var timeTickReceiver: BroadcastReceiver? = null
    private var timeTickRegistered = false

    fun getAlarmTimes(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(ALARM_TIMES_KEY, setOf())?.toList()?.sorted() ?: listOf()
    }

    fun addAlarmTime(context: Context, time: String) {
        Log.i("wangyu", "add Alarm: $time")
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
            prefs.edit()
                .putStringSet(ALARM_TIMES_KEY, times)
                .remove(LAST_FIRED_PREFIX + time)
                .apply()
            cancelAlarm(context, time)
            scheduleAllAlarms(context)
        }
    }

    /**
     * 取消旧版 AlarmManager 的 PendingIntent，并注册/注销基于 [Intent.ACTION_TIME_TICK] 的分钟监听。
     * 到点时向 [AlarmReceiver] 发送与原先一致的显式广播（携带 [ALARM_TIME]）。
     */
    fun scheduleAllAlarms(context: Context) {
        val appCtx = context.applicationContext
        val times = getAlarmTimes(appCtx)
        for (time in times) {
            cancelAlarm(appCtx, time)
        }
        if (times.isNotEmpty()) {
            ensureTimeTickRegistered(appCtx)
        } else {
            maybeUnregisterTimeTick(appCtx)
        }
    }

    private fun ensureTimeTickRegistered(appContext: Context) {
        synchronized(this) {
            if (timeTickRegistered) return
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent?) {
                    when (intent?.action) {
                        Intent.ACTION_TIME_TICK,
                        Intent.ACTION_TIME_CHANGED -> onClockEvent(ctx.applicationContext)
                    }
                }
            }
            timeTickReceiver = receiver
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    appContext.registerReceiver(
                        receiver,
                        filter,
                        Context.RECEIVER_NOT_EXPORTED
                    )
                } else {
                    @Suppress("DEPRECATION")
                    appContext.registerReceiver(receiver, filter)
                }
                timeTickRegistered = true
                Log.d("AlarmHelper", "Registered TIME_TICK / TIME_CHANGED for alarm slots")
            } catch (e: Exception) {
                timeTickReceiver = null
                Log.e("AlarmHelper", "registerReceiver failed", e)
            }
        }
    }

    private fun maybeUnregisterTimeTick(appContext: Context) {
        synchronized(this) {
            if (!timeTickRegistered) return
            val r = timeTickReceiver ?: return
            try {
                appContext.unregisterReceiver(r)
            } catch (_: IllegalArgumentException) {
                // already unregistered
            }
            timeTickReceiver = null
            timeTickRegistered = false
            Log.d("AlarmHelper", "Unregistered TIME_TICK receiver (no alarm times)")
        }
    }

    private fun onClockEvent(appContext: Context) {
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
            return
        }

        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val timeStr = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)

        val times = getAlarmTimes(appContext)
        if (!times.contains(timeStr)) return

        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = dayKeyFormat.format(calendar.time)
        val lastKey = LAST_FIRED_PREFIX + timeStr
        if (prefs.getString(lastKey, null) == today) return

        prefs.edit().putString(lastKey, today).apply()

        val broadcast = Intent(appContext, AlarmReceiver::class.java).apply {
            putExtra("ALARM_TIME", timeStr)
        }
        appContext.sendBroadcast(broadcast)
        Log.d("AlarmHelper", "Sent alarm broadcast for $timeStr")
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
