package com.ai.feishualarm.ui.page

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ai.feishualarm.helper.AlarmHelper
import com.ai.feishualarm.helper.FeishuLauncher
import com.ai.feishualarm.helper.LocationHelper
import com.ai.feishualarm.service.AlarmService
import com.ai.feishualarm.ui.theme.FeishuAlarmTheme
import java.util.Calendar

class MainActivity : ComponentActivity() {
    private var distanceLocationManager: LocationManager? = null
    private var distanceLocationListener: LocationListener? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocation = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocation || coarseLocation) {
            Log.d("MainActivity", "Foreground location granted")
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val currentTimes = AlarmHelper.getAlarmTimes(this)
        if (!currentTimes.contains("09:15")) {
            AlarmHelper.addAlarmTime(this, "09:15")
        }

        setContent {
            FeishuAlarmTheme {
                var alarmTimes by remember { mutableStateOf(AlarmHelper.getAlarmTimes(this)) }
                var targetLocation by remember { mutableStateOf(LocationHelper.getTargetLocation(this)) }
                var targetDistance by remember { mutableStateOf(LocationHelper.getTargetDistance(this).toString()) }
                var showBgLocationRationale by remember { mutableStateOf(false) }
                var currentPage by remember { mutableStateOf("home") }
                var liveDistanceText by remember { mutableStateOf("进入页面后将开始实时测距") }

                DisposableEffect(currentPage, targetLocation) {
                    if (currentPage == "measure") {
                        startDistanceMonitoring(
                            onDistanceChanged = { distanceMeters ->
                                liveDistanceText = "当前位置距离目标打卡点 %.1f 米".format(distanceMeters)
                            },
                            onStatusChanged = { status ->
                                liveDistanceText = status
                            }
                        )
                    }

                    onDispose {
                        stopDistanceMonitoring()
                    }
                }

                if (showBgLocationRationale) {
                    AlertDialog(
                        onDismissRequest = { showBgLocationRationale = false },
                        title = { Text("需要后台定位权限") },
                        text = { Text("为了在手机锁屏或后台时也能根据位置自动打开飞书，请在设置中将定位权限设置为“始终允许”。") },
                        confirmButton = {
                            TextButton(onClick = {
                                showBgLocationRationale = false
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", packageName, null)
                                }
                                startActivity(intent)
                            }) {
                                Text("去设置")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showBgLocationRationale = false }) {
                                Text("取消")
                            }
                        }
                    )
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(if (currentPage == "home") "飞书打卡助手" else "实时测距")
                            },
                            navigationIcon = {
                                if (currentPage == "measure") {
                                    TextButton(onClick = { currentPage = "home" }) {
                                        Text("返回")
                                    }
                                }
                            },
                            actions = {
                                if (currentPage == "home") {
                                    TextButton(onClick = {
                                        liveDistanceText = "进入页面后将开始实时测距"
                                        currentPage = "measure"
                                    }) {
                                        Text("测距")
                                    }
                                }
                            }
                        )
                    },
                    floatingActionButton = {
                        if (currentPage == "home") {
                            FloatingActionButton(onClick = {
                                showTimePicker(this) { newTime ->
                                    AlarmHelper.addAlarmTime(this, newTime)
                                    alarmTimes = AlarmHelper.getAlarmTimes(this)
                                }
                            }) {
                                Text("+", fontSize = 24.sp)
                            }
                        }
                    }
                ) { innerPadding ->
                    if (currentPage == "home") {
                        MainScreen(
                            modifier = Modifier.padding(innerPadding),
                            alarmTimes = alarmTimes,
                            targetLocation = targetLocation,
                            targetDistance = targetDistance,
                            onDeleteTime = { time ->
                                AlarmHelper.removeAlarmTime(this, time)
                                alarmTimes = AlarmHelper.getAlarmTimes(this)
                            },
                            onOpenFeishu = { FeishuLauncher.openFeishu(this) },
                            onCheckPermissions = {
                                requestForegroundPermissions()
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    showBgLocationRationale = true
                                }
                            },
                            onRecordLocation = {
                                recordCurrentLocationNative { loc ->
                                    targetLocation = loc
                                }
                            },
                            onDistanceChange = {
                                targetDistance = it
                                it.toFloatOrNull()?.let { dist ->
                                    LocationHelper.setTargetDistance(this, dist)
                                }
                            }
                        )
                    } else {
                        DistanceMeasureScreen(
                            modifier = Modifier.padding(innerPadding),
                            targetLocation = targetLocation,
                            liveDistanceText = liveDistanceText
                        )
                    }
                }
            }
        }

        startAlarmService()
        AlarmHelper.scheduleAllAlarms(this)
    }

    private fun showTimePicker(context: Context, onTimeSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        TimePickerDialog(
            context,
            { _, hour, minute ->
                val time = String.format("%02d:%02d", hour, minute)
                onTimeSelected(time)
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun requestForegroundPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        requestPermissionLauncher.launch(permissions.toTypedArray())
        checkOverlayPermission()
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
                Toast.makeText(this, "请授予“在其他应用上层显示”权限", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startAlarmService() {
        val intent = Intent(this, AlarmService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun recordCurrentLocationNative(onRecorded: (Pair<Double, Double>) -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestForegroundPermissions()
            return
        }

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider = if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            LocationManager.NETWORK_PROVIDER
        } else if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            LocationManager.GPS_PROVIDER
        } else {
            null
        }

        if (provider == null) {
            Toast.makeText(this, "请开启定位服务", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "正在获取位置，请稍候...", Toast.LENGTH_SHORT).show()

        try {
            locationManager.requestSingleUpdate(provider, object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    LocationHelper.saveTargetLocation(this@MainActivity, location.latitude, location.longitude)
                    onRecorded(Pair(location.latitude, location.longitude))
                    Toast.makeText(
                        this@MainActivity,
                        "位置已记录: ${"%.4f".format(location.latitude)}, ${"%.4f".format(location.longitude)}",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}

                override fun onProviderEnabled(p: String) {}

                override fun onProviderDisabled(p: String) {}
            }, null)
        } catch (e: SecurityException) {
            Toast.makeText(this, "权限不足", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startDistanceMonitoring(
        onDistanceChanged: (Float) -> Unit,
        onStatusChanged: (String) -> Unit
    ) {
        val targetLoc = LocationHelper.getTargetLocation(this)
        if (targetLoc == null) {
            onStatusChanged("请先在主页记录目标打卡位置")
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
            onStatusChanged("缺少定位权限，请先授权")
            requestForegroundPermissions()
            return
        }

        stopDistanceMonitoring()
        onStatusChanged("正在实时监听位置...")
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        distanceLocationManager = locationManager

        val providers = buildList {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                add(LocationManager.NETWORK_PROVIDER)
            }
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                add(LocationManager.GPS_PROVIDER)
            }
        }
        if (providers.isEmpty()) {
            onStatusChanged("请开启定位服务后重试")
            return
        }

        val updateDistance: (Location) -> Unit = { location ->
            val result = FloatArray(1)
            Location.distanceBetween(
                location.latitude,
                location.longitude,
                targetLoc.first,
                targetLoc.second,
                result
            )
            onDistanceChanged(result[0])
        }

        providers.asSequence()
            .mapNotNull { provider ->
                try {
                    locationManager.getLastKnownLocation(provider)
                } catch (_: SecurityException) {
                    null
                }
            }
            .minByOrNull { it.accuracy }
            ?.let(updateDistance)

        distanceLocationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                Log.d("MainActivity", "Distance page location update: $location")
                updateDistance(location)
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

            override fun onProviderEnabled(provider: String) {
                onStatusChanged("定位已开启，正在实时监听位置...")
            }

            override fun onProviderDisabled(provider: String) {
                onStatusChanged("定位提供器已关闭，可能无法继续更新距离")
            }
        }

        try {
            providers.forEach { provider ->
                locationManager.requestLocationUpdates(
                    provider,
                    2_000L,
                    1f,
                    distanceLocationListener!!,
                    mainLooper
                )
            }
        } catch (e: SecurityException) {
            onStatusChanged("定位权限不足，无法开始测距")
        }
    }

    private fun stopDistanceMonitoring() {
        val locationManager = distanceLocationManager
        val listener = distanceLocationListener
        if (locationManager != null && listener != null) {
            locationManager.removeUpdates(listener)
        }
        distanceLocationManager = null
        distanceLocationListener = null
    }

    override fun onDestroy() {
        stopDistanceMonitoring()
        super.onDestroy()
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    alarmTimes: List<String>,
    targetLocation: Pair<Double, Double>?,
    targetDistance: String,
    onDeleteTime: (String) -> Unit,
    onOpenFeishu: () -> Unit,
    onCheckPermissions: () -> Unit,
    onRecordLocation: () -> Unit,
    onDistanceChange: (String) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "目标打卡位置:", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (targetLocation != null) {
                        "纬度: ${"%.4f".format(targetLocation.first)}, 经度: ${"%.4f".format(targetLocation.second)}"
                    } else {
                        "未设置位置"
                    },
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onRecordLocation, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.LocationOn, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("记录当前位置")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = targetDistance,
                    onValueChange = onDistanceChange,
                    label = { Text("判定范围 (米)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "监控时间点 (排除周末):", fontSize = 16.sp, color = Color.Gray)
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            items(alarmTimes) { time ->
                AlarmTimeItem(time = time, onDelete = { onDeleteTime(time) })
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onOpenFeishu,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3370FF))
        ) {
            Text("立即打开飞书", color = Color.White, fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onCheckPermissions,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("检查并授权必要权限")
        }
    }
}

@Composable
fun AlarmTimeItem(time: String, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = time, fontSize = 20.sp, style = MaterialTheme.typography.bodyLarge)
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除", tint = Color.Red)
            }
        }
    }
}
