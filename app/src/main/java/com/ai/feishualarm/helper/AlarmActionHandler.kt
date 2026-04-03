package com.ai.feishualarm.helper

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ai.feishualarm.R
import com.ai.feishualarm.ui.page.MainActivity
import androidx.core.net.toUri

object AlarmActionHandler {
    private const val TAG = "AlarmActionHandler"
    private const val REMINDER_CHANNEL_ID = "FeishuReminderChannel_v9"
    private const val NOTIFICATION_ID = 2
    private const val ALARM_PREFS = "AlarmPrefs"
    private const val PENDING_OPEN_FEISHU_KEY = "PENDING_OPEN_FEISHU"

    fun triggerOpenOrNotify(context: Context, alarmTime: String) {
        val appContext = context.applicationContext
        wakeUpScreen(appContext)

        val keyguardManager = appContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val isLocked = keyguardManager.isKeyguardLocked
        Log.d(TAG, "Alarm ready at $alarmTime, device locked: $isLocked")

        if (isLocked) {
            val prefs = appContext.getSharedPreferences(ALARM_PREFS, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(PENDING_OPEN_FEISHU_KEY, true).apply()
            sendReminderNotification(appContext, alarmTime)
        } else {
            FeishuLauncher.openFeishu(appContext)
        }
    }

    fun sendReminderNotification(context: Context, time: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val soundUri = ("android.resource://" + context.packageName + "/" + R.raw.alarm2).toUri()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                REMINDER_CHANNEL_ID,
                "打卡提醒通知",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "进入打卡范围后的提醒通知"
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC

                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
                setSound(soundUri, audioAttributes)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val feishuIntent = context.packageManager.getLaunchIntentForPackage("com.ss.android.lark")
        val pendingIntent = if (feishuIntent != null) {
            feishuIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            PendingIntent.getActivity(
                context,
                0,
                feishuIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            val mainIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            PendingIntent.getActivity(
                context,
                0,
                mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("打卡时间到了！($time)")
            .setContentText("你已进入打卡范围，点击可打开飞书")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setSound(soundUri)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    @SuppressLint("InvalidWakeLockTag")
    private fun wakeUpScreen(context: Context) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "FeishuAlarm:WakeLock"
        )
        wakeLock.acquire(3_000)
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
    }
}
