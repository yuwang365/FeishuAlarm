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
import java.time.LocalTime

object AlarmActionHandler {
    private const val TAG = "AlarmActionHandler"
    private const val REMINDER_CHANNEL_ID = "FeishuReminderChannel_v9"
    private const val REMINDER_CHANNEL_COMMON_ID = "FeishuReminderChannel_common_v1"
    private const val WAIT_WIFI_CHANNEL_ID = "FeishuWaitWifiChannel_v1"
    private const val REMINDER_NOTIFICATION_ID = 2
    private const val WAIT_WIFI_NOTIFICATION_ID = 3
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
            sendReminderNotification(appContext, alarmTime, defaultSound = LocalTime.now().hour >= 12)
        } else {
            FeishuLauncher.openFeishu(appContext)
        }
    }

    fun sendReminderNotification(context: Context, time: String,defaultSound: Boolean) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val soundUri = if(defaultSound)
            RingtoneManager.getDefaultUri (RingtoneManager.TYPE_NOTIFICATION)
        else (("android.resource://" + context.packageName + "/" + R.raw.alarm2).toUri())
        val channelId = if(defaultSound) REMINDER_CHANNEL_COMMON_ID else REMINDER_CHANNEL_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "打卡提醒通知",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "已连接 candymobi 或到达打卡时间后的提醒"
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

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("打卡时间到了！($time)")
            .setContentText("已连接 ${WifiNetworkHelper.TARGET_SSID} WiFi，解锁后自动打开飞书")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setSound(soundUri)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(REMINDER_NOTIFICATION_ID, notification)
    }

    fun sendWaitingForWifiNotification(context: Context, time: String) {
        val appContext = context.applicationContext
        wakeUpScreen(appContext)

        val notificationManager =
            appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                WAIT_WIFI_CHANNEL_ID,
                "等待连接 WiFi",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "提醒连接 candymobi WiFi 以完成打卡"
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()
                setSound(soundUri, audioAttributes)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val mainIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            1,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, WAIT_WIFI_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("打卡时间到了！($time)")
            .setContentText("请连接 WiFi「${WifiNetworkHelper.TARGET_SSID}」，连接后将自动打开飞书")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setSound(soundUri)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(WAIT_WIFI_NOTIFICATION_ID, notification)
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
