package com.ai.feishualarm.helper

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

object WifiNetworkHelper {
    private const val TAG = "WifiNetworkHelper"
    const val TARGET_SSID = "candymobi"

    fun hasWifiSsidReadPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isLocationServiceEnabled(context: Context): Boolean {
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    private fun normalizeSsid(raw: String?): String? {
        val ssid = raw?.trim('"')?.trim().orEmpty()
        return if (ssid.isEmpty() || ssid == WifiManager.UNKNOWN_SSID || ssid == "<unknown ssid>") {
            null
        } else {
            ssid
        }
    }

    @SuppressLint("MissingPermission")
    fun getCurrentWifiSsid(context: Context): String? {
        if (!hasWifiSsidReadPermission(context)) {
            Log.w(TAG, "Cannot read SSID: missing ACCESS_FINE_LOCATION permission")
            return null
        }

        if (!isLocationServiceEnabled(context)) {
            Log.w(TAG, "Cannot read SSID: location service is disabled")
            return null
        }

        val appContext = context.applicationContext
        val connectivityManager =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) {
            Log.d(TAG, "Active network is not WiFi")
            return null
        }

//        val transportSsid = (capabilities.transportInfo as? WifiInfo)?.ssid
//        normalizeSsid(transportSsid)?.let { return it }

        val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val fallbackSsid = wifiManager.connectionInfo?.ssid
        return normalizeSsid(fallbackSsid).also {
            if (it == null) {
                Log.w(TAG, "SSID is unavailable, got raw value: ${fallbackSsid ?: "null"}")
            }
        }
    }

    fun isConnectedToTargetWifi(context: Context): Boolean {
        val ssid = getCurrentWifiSsid(context) ?: return false
        return ssid.equals(TARGET_SSID, ignoreCase = true)
    }
}
