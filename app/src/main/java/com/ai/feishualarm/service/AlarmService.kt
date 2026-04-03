package com.ai.feishualarm.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ai.feishualarm.helper.AlarmActionHandler
import com.ai.feishualarm.helper.LocationHelper
import com.ai.feishualarm.R
import com.ai.feishualarm.receiver.UnlockReceiver

class AlarmService : Service() {

    private var unlockReceiver: UnlockReceiver? = null
    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null

    companion object {
        private const val CHANNEL_ID = "FeishuAlarmServiceChannel"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_START_MONITORING = "com.ai.feishualarm.action.START_MONITORING"
        private const val ACTION_STOP_MONITORING = "com.ai.feishualarm.action.STOP_MONITORING"
        private const val EXTRA_ALARM_TIME = "extra_alarm_time"

        fun startLocationMonitoring(context: Context, alarmTime: String) {
            val intent = Intent(context, AlarmService::class.java).apply {
                action = ACTION_START_MONITORING
                putExtra(EXTRA_ALARM_TIME, alarmTime)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
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
            ACTION_START_MONITORING -> createNotification("未进入打卡范围，正在实时监控位置", true)
            ACTION_STOP_MONITORING -> createNotification("已关闭位置监控")
            else -> createNotification()
        }
        startForeground(NOTIFICATION_ID, notification)

        if (intent?.action == ACTION_STOP_MONITORING) {
            stopRealtimeLocationMonitoring()
            return START_STICKY
        }

        if (intent?.action == ACTION_START_MONITORING && !alarmTime.isNullOrBlank()) {
            startRealtimeLocationMonitoring(alarmTime)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        stopRealtimeLocationMonitoring()
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
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
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

    private fun startRealtimeLocationMonitoring(alarmTime: String) {
        val targetLocation = LocationHelper.getTargetLocation(this)
        if (targetLocation == null) {
            Log.w("AlarmService", "Target location missing, trigger directly.")
            AlarmActionHandler.triggerOpenOrNotify(this, alarmTime)
            stopRealtimeLocationMonitoring()
            return
        }

        val hasFineLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFineLocation && !hasCoarseLocation) {
            Log.w("AlarmService", "Location permission missing, trigger directly.")
            AlarmActionHandler.triggerOpenOrNotify(this, alarmTime)
            stopRealtimeLocationMonitoring()
            return
        }

        stopRealtimeLocationMonitoring()
        updateForegroundNotification("未进入打卡范围，正在实时监控位置", true)
        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationManager = manager

        val providers = buildList {
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                add(LocationManager.NETWORK_PROVIDER)
            }
            if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                add(LocationManager.GPS_PROVIDER)
            }
        }
        if (providers.isEmpty()) {
            Log.w("AlarmService", "No enabled location provider, trigger directly.")
            AlarmActionHandler.triggerOpenOrNotify(this, alarmTime)
            stopRealtimeLocationMonitoring()
            return
        }

        var alarmHandled = false
        val handleLocation: (Location) -> Unit = { location ->
            val targetDistance = LocationHelper.getTargetDistance(this)
            val result = FloatArray(1)
            Location.distanceBetween(
                location.latitude,
                location.longitude,
                targetLocation.first,
                targetLocation.second,
                result
            )
            val distanceMeters = result[0]
            updateForegroundNotification(
                "距离目标打卡点 %.1f 米".format(distanceMeters),
                true
            )
            val isWithinRange = distanceMeters <= targetDistance
            Log.d(
                "AlarmService",
                "Realtime location update, distance=$distanceMeters, within range: $isWithinRange"
            )

            if (isWithinRange) {
                alarmHandled = true
                AlarmActionHandler.triggerOpenOrNotify(this, alarmTime)
                stopRealtimeLocationMonitoring()
                updateForegroundNotification("已进入打卡范围", false)
            }
        }

        providers.asSequence()
            .mapNotNull { provider ->
                try {
                    manager.getLastKnownLocation(provider)
                } catch (_: SecurityException) {
                    null
                }
            }
            .minByOrNull { it.accuracy }
            ?.let(handleLocation)
        if (alarmHandled) {
            return
        }

        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                handleLocation(location)
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

            override fun onProviderEnabled(provider: String) {
                Log.d("AlarmService", "Provider enabled: $provider")
            }

            override fun onProviderDisabled(provider: String) {
                Log.w("AlarmService", "Provider disabled: $provider")
            }
        }

        try {
            providers.forEach { provider ->
                manager.requestLocationUpdates(
                    provider,
                    5_000L,
                    5f,
                    locationListener!!,
                    mainLooper
                )
            }
        } catch (e: SecurityException) {
            Log.e("AlarmService", "Failed to request location updates", e)
            AlarmActionHandler.triggerOpenOrNotify(this, alarmTime)
            stopRealtimeLocationMonitoring()
        }
    }

    private fun stopRealtimeLocationMonitoring() {
        val manager = locationManager
        val listener = locationListener
        if (manager != null && listener != null) {
            manager.removeUpdates(listener)
        }
        locationManager = null
        locationListener = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Feishu Alarm Foreground Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }
}
