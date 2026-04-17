package com.ai.feishualarm.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ai.feishualarm.helper.AlarmActionHandler
import com.ai.feishualarm.helper.WifiNetworkHelper
import com.ai.feishualarm.R
import com.ai.feishualarm.receiver.UnlockReceiver

class AlarmService : Service() {

    private var unlockReceiver: UnlockReceiver? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingAlarmTime: String? = null
    private val wifiRetryIntervalMs = 15_000L
    private val wifiRetryRunnable = object : Runnable {
        override fun run() {
            val t = pendingAlarmTime
            if (!t.isNullOrBlank()) {
                tryCompleteAlarmIfOnWifi(t)
                if (pendingAlarmTime != null) {
                    mainHandler.postDelayed(this, wifiRetryIntervalMs)
                }
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "FeishuAlarmServiceChannel"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_START_WIFI_MONITORING = "com.ai.feishualarm.action.START_WIFI_MONITORING"
        private const val ACTION_STOP_MONITORING = "com.ai.feishualarm.action.STOP_MONITORING"
        const val ACTION_MONITORING_STATE_CHANGED = "com.ai.feishualarm.action.MONITORING_STATE_CHANGED"
        const val EXTRA_IS_MONITORING = "extra_is_monitoring"
        private const val EXTRA_ALARM_TIME = "extra_alarm_time"
        private const val PREFS_NAME = "AlarmServicePrefs"
        private const val KEY_WIFI_MONITORING_ACTIVE = "wifi_monitoring_active"

        fun startWifiMonitoring(context: Context, alarmTime: String) {
            val intent = Intent(context, AlarmService::class.java).apply {
                action = ACTION_START_WIFI_MONITORING
                putExtra(EXTRA_ALARM_TIME, alarmTime)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopMonitoring(context: Context) {
            val intent = Intent(context, AlarmService::class.java).apply {
                action = ACTION_STOP_MONITORING
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun isWifiMonitoringActive(context: Context): Boolean {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_WIFI_MONITORING_ACTIVE, false)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerUnlockReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarmTime = intent?.getStringExtra(EXTRA_ALARM_TIME)
        val notification = when (intent?.action) {
            ACTION_START_WIFI_MONITORING -> createNotification(
                "未连接 ${WifiNetworkHelper.TARGET_SSID} WiFi，正在等待连接…",
                true
            )
            ACTION_STOP_MONITORING -> createNotification("已关闭 WiFi 监控")
            else -> createNotification()
        }
        startForeground(NOTIFICATION_ID, notification)

        if (intent?.action == ACTION_STOP_MONITORING) {
            stopWifiMonitoringInternal()
            return START_STICKY
        }

        if (intent?.action == ACTION_START_WIFI_MONITORING && !alarmTime.isNullOrBlank()) {
            startWifiMonitoringInternal(alarmTime)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        stopWifiMonitoringInternal()
        unregisterUnlockReceiver()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerUnlockReceiver() {
        if (unlockReceiver == null) {
            unlockReceiver = UnlockReceiver()
            val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
            registerReceiver(unlockReceiver, filter)
        }
    }

    private fun unregisterUnlockReceiver() {
        unlockReceiver?.let {
            unregisterReceiver(it)
            unlockReceiver = null
        }
    }

    private fun createNotification(
        contentText: String = "到时间会提醒打卡",
        showStopAction: Boolean = false
    ): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("飞书打卡监控")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_notification_alarm)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setOngoing(showStopAction)

        if (showStopAction) {
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "关闭监控",
                createStopMonitoringPendingIntent()
            )
        }

        return builder.build()
    }

    private fun createStopMonitoringPendingIntent(): PendingIntent {
        val intent = Intent(this, AlarmService::class.java).apply {
            action = ACTION_STOP_MONITORING
        }
        return PendingIntent.getService(
            this,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun updateForegroundNotification(contentText: String, showStopAction: Boolean) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(
            NOTIFICATION_ID,
            createNotification(contentText, showStopAction)
        )
    }

    private fun updateMonitoringState(isMonitoring: Boolean) {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_WIFI_MONITORING_ACTIVE, isMonitoring).apply()
        sendBroadcast(
            Intent(ACTION_MONITORING_STATE_CHANGED).putExtra(EXTRA_IS_MONITORING, isMonitoring)
        )
    }

    private fun startWifiMonitoringInternal(alarmTime: String) {
        stopWifiMonitoringInternal()
        pendingAlarmTime = alarmTime
        updateMonitoringState(true)
        updateForegroundNotification("未连接 ${WifiNetworkHelper.TARGET_SSID} WiFi，正在等待连接…", true)

        tryCompleteAlarmIfOnWifi(alarmTime)

        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager = cm
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                mainHandler.post { tryCompleteAlarmIfOnWifi(alarmTime) }
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                mainHandler.post { tryCompleteAlarmIfOnWifi(alarmTime) }
            }

            override fun onLost(network: Network) {
                mainHandler.post {
                    updateForegroundNotification(
                        "未连接 ${WifiNetworkHelper.TARGET_SSID} WiFi，正在等待连接…",
                        true
                    )
                }
            }
        }
        networkCallback = callback
        cm.registerNetworkCallback(request, callback)
        mainHandler.postDelayed(wifiRetryRunnable, wifiRetryIntervalMs)
    }

    private fun tryCompleteAlarmIfOnWifi(alarmTime: String) {
        if (!WifiNetworkHelper.hasWifiSsidReadPermission(this)) {
            Log.w("AlarmService", "Still waiting: no permission to read WiFi SSID")
            updateForegroundNotification(
                "无法读取 WiFi 名称，请在应用内授予附近设备或位置权限",
                true
            )
            return
        }
        if (!WifiNetworkHelper.isConnectedToTargetWifi(this)) {
            Log.d("AlarmService", "Still not on ${WifiNetworkHelper.TARGET_SSID}")
            return
        }

        Log.d("AlarmService", "Connected to ${WifiNetworkHelper.TARGET_SSID}, triggering alarm flow")
        pendingAlarmTime = null
        stopWifiMonitoringInternal()
        AlarmActionHandler.triggerOpenOrNotify(this, alarmTime)
        updateForegroundNotification("已连接到 ${WifiNetworkHelper.TARGET_SSID}", false)
    }

    private fun stopWifiMonitoringInternal() {
        mainHandler.removeCallbacks(wifiRetryRunnable)
        val cm = connectivityManager
        val cb = networkCallback
        if (cm != null && cb != null) {
            try {
                cm.unregisterNetworkCallback(cb)
            } catch (_: Exception) {
            }
        }
        connectivityManager = null
        networkCallback = null
        pendingAlarmTime = null
        updateMonitoringState(false)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Feishu Alarm Foreground Service Channel",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }
}
