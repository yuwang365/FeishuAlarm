package com.ai.feishualarm.ui.page

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ai.feishualarm.helper.AlarmHelper
import com.ai.feishualarm.helper.FeishuLauncher
import com.ai.feishualarm.helper.WifiNetworkHelper
import com.ai.feishualarm.service.AlarmService
import com.ai.feishualarm.ui.theme.FeishuAlarmTheme
import java.util.Calendar

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        continuePermissionFlow()
    }

    private val backgroundLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        checkOverlayPermission()
    }

    private val appSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkOverlayPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("wangyu","MainActivity Create")
        enableEdgeToEdge()
        initUI()
        val currentTimes = AlarmHelper.getAlarmTimes(this)
        if (!currentTimes.contains("09:15")) {
            AlarmHelper.addAlarmTime(this, "09:15")
        }

        requestWifiRelatedPermissions()
        startAlarmService()
        AlarmHelper.scheduleAllAlarms(this)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    private fun initUI(){
        setContent {
            FeishuAlarmTheme {
                var alarmTimes by remember { mutableStateOf(AlarmHelper.getAlarmTimes(this)) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(title = { Text("飞书打卡助手") })
                    },
                    floatingActionButton = {
                        FloatingActionButton(onClick = {
                            showTimePicker(this) { newTime ->
                                AlarmHelper.addAlarmTime(this, newTime)
                                alarmTimes = AlarmHelper.getAlarmTimes(this)
                            }
                        }) {
                            Text("+", fontSize = 24.sp)
                        }
                    }
                ) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        alarmTimes = alarmTimes,
                        onDeleteTime = { time ->
                            AlarmHelper.removeAlarmTime(this, time)
                            alarmTimes = AlarmHelper.getAlarmTimes(this)
                        },
                        onOpenFeishu = { FeishuLauncher.openFeishu(this) },
                        onRequestPermissions = { requestWifiRelatedPermissions() }
                    )
                }
            }
        }
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

    private fun requestWifiRelatedPermissions() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }
        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.distinct().toTypedArray())
        } else {
            continuePermissionFlow()
        }
    }

    private fun continuePermissionFlow() {
        if (needsBackgroundLocationPermission()) {
            requestBackgroundLocationPermission()
        } else {
            checkOverlayPermission()
        }
    }

    private fun needsBackgroundLocationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false
        }
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) != PackageManager.PERMISSION_GRANTED
    }

    private fun requestBackgroundLocationPermission() {
        if (!needsBackgroundLocationPermission()) {
            checkOverlayPermission()
            return
        }

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            backgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            return
        }

        Toast.makeText(
            this,
            "请在接下来的设置页面中将位置信息权限改为“始终允许”，这样后台才能读取 WiFi 名称。",
            Toast.LENGTH_LONG
        ).show()
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        appSettingsLauncher.launch(intent)
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
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    alarmTimes: List<String>,
    onDeleteTime: (String) -> Unit,
    onOpenFeishu: () -> Unit,
    onRequestPermissions: () -> Unit
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
                Text(text = "打卡条件", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "到达设定时间后，若手机已连接 WiFi「${WifiNetworkHelper.TARGET_SSID}」，将自动打开飞书；否则会提醒并等待连接该 WiFi。锁屏时会先提醒你，解锁后再打开飞书。",
                    fontSize = 14.sp
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
            onClick = onRequestPermissions,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("检查并授权通知、定位与 WiFi 权限")
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
