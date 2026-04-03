package com.ai.feishualarm.receiver

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.ai.feishualarm.helper.AlarmActionHandler
import com.ai.feishualarm.helper.AlarmHelper
import com.ai.feishualarm.helper.LocationHelper
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
            Log.d("AlarmReceiver", "Processing alarm for $alarmTime")
            checkLocationAndHandle(context, alarmTime)
        } else {
            Log.d("AlarmReceiver", "It's weekend, skipping.")
        }

        AlarmHelper.scheduleAllAlarms(context)
    }

    private fun checkLocationAndHandle(context: Context, alarmTime: String) {
        val targetLoc = LocationHelper.getTargetLocation(context)
        val targetDist = LocationHelper.getTargetDistance(context)

        if (targetLoc == null) {
            Log.d("AlarmReceiver", "No target location set, processing alarm directly.")
            AlarmActionHandler.triggerOpenOrNotify(context, alarmTime)
            return
        }

        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFineLocation && !hasCoarseLocation) {
            Log.w("AlarmReceiver", "Location permission missing, fallback to direct trigger.")
            AlarmActionHandler.triggerOpenOrNotify(context, alarmTime)
            return
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = locationManager.getProviders(true)
        var bestLocation: Location? = null
        for (provider in providers) {
            try {
                val location = locationManager.getLastKnownLocation(provider) ?: continue
                if (bestLocation == null || location.accuracy < bestLocation.accuracy) {
                    bestLocation = location
                }
            } catch (_: SecurityException) {
            }
        }

        if (bestLocation != null && isRecent(bestLocation)) {
            if (LocationHelper.isWithinRange(
                bestLocation.latitude,
                bestLocation.longitude,
                targetLoc.first,
                targetLoc.second,
                targetDist
            )) {
                Log.d("AlarmReceiver", "Last known location is within range.")
                AlarmActionHandler.triggerOpenOrNotify(context, alarmTime)
                return
            }
        }

        val hasEnabledProvider = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        if (!hasEnabledProvider) {
            Log.w("AlarmReceiver", "No location provider enabled, fallback to direct trigger.")
            AlarmActionHandler.triggerOpenOrNotify(context, alarmTime)
            return
        }

        Log.d("AlarmReceiver", "Location out of range, start realtime monitoring.")
        AlarmService.startLocationMonitoring(context, alarmTime)
    }

    private fun isRecent(location: Location): Boolean {
        return System.currentTimeMillis() - location.time < 5 * 60 * 1000
    }
}
