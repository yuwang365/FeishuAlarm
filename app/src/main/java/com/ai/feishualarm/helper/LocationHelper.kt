package com.ai.feishualarm.helper

import android.content.Context
import android.location.Location

object LocationHelper {
    private const val PREFS_NAME = "LocationPrefs"
    private const val KEY_LATITUDE = "target_latitude"
    private const val KEY_LONGITUDE = "target_longitude"
    private const val KEY_DISTANCE = "target_distance"

    fun saveTargetLocation(context: Context, latitude: Double, longitude: Double) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat(KEY_LATITUDE, latitude.toFloat())
            .putFloat(KEY_LONGITUDE, longitude.toFloat())
            .apply()
    }

    fun getTargetLocation(context: Context): Pair<Double, Double>? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lat = prefs.getFloat(KEY_LATITUDE, -1f)
        val lng = prefs.getFloat(KEY_LONGITUDE, -1f)
        return if (lat != -1f && lng != -1f) {
            Pair(lat.toDouble(), lng.toDouble())
        } else {
            null
        }
    }

    fun setTargetDistance(context: Context, distance: Float) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(KEY_DISTANCE, distance).apply()
    }

    fun getTargetDistance(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_DISTANCE, 30f)
    }

    fun isWithinRange(currentLat: Double, currentLng: Double, targetLat: Double, targetLng: Double, rangeMeters: Float): Boolean {
        val results = FloatArray(1)
        Location.distanceBetween(currentLat, currentLng, targetLat, targetLng, results)
        return results[0] <= rangeMeters
    }
}
