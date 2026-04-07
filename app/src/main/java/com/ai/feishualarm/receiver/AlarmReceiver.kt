package com.ai.feishualarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ai.feishualarm.helper.AlarmActionHandler
import com.ai.feishualarm.helper.AlarmHelper
import com.ai.feishualarm.helper.WifiNetworkHelper
import com.ai.feishualarm.service.AlarmService
import java.util.Calendar

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        Log.d("AlarmReceiver", "Received action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED) {
            AlarmHelper.scheduleAllAlarms(context)
            return
        }

        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

        if (dayOfWeek != Calendar.SUNDAY && dayOfWeek != Calendar.SATURDAY) {
            val alarmTime = intent?.getStringExtra("ALARM_TIME") ?: ""
            if (alarmTime.isBlank()) {
                Log.w("AlarmReceiver", "Missing ALARM_TIME, ignoring unexpected broadcast.")
            } else {
                Log.d("AlarmReceiver", "Processing alarm for $alarmTime")
                checkWifiAndHandle(context, alarmTime)
            }
        } else {
            Log.d("AlarmReceiver", "It's weekend, skipping.")
        }

        AlarmHelper.scheduleAllAlarms(context)
    }

    private fun checkWifiAndHandle(context: Context, alarmTime: String) {
        if (WifiNetworkHelper.isConnectedToTargetWifi(context)) {
            Log.d("AlarmReceiver", "Already on ${WifiNetworkHelper.TARGET_SSID} WiFi.")
            AlarmActionHandler.triggerOpenOrNotify(context, alarmTime)
            return
        }

        Log.d("AlarmReceiver", "Not on target WiFi, notify and start monitoring.")
        AlarmActionHandler.sendWaitingForWifiNotification(context, alarmTime)
        AlarmService.startWifiMonitoring(context, alarmTime)
    }
}
