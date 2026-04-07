package com.ai.feishualarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ai.feishualarm.helper.FeishuLauncher
import kotlinx.coroutines.delay
import androidx.core.content.edit

class UnlockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_USER_PRESENT) {
            Log.d("UnlockReceiver", "Device unlocked!")
            val prefs = context.getSharedPreferences("AlarmPrefs", Context.MODE_PRIVATE)
            val pendingOpen = prefs.getBoolean("PENDING_OPEN_FEISHU", false)

            if (pendingOpen) {
                Log.d("UnlockReceiver", "Pending open found, launching Feishu...")
//                Handler(Looper.getMainLooper()).postDelayed({
//                    FeishuLauncher.openFeishu(context)
//                },1000)
                FeishuLauncher.openFeishu(context)
                prefs.edit { putBoolean("PENDING_OPEN_FEISHU", false) }

            }
        }
    }
}
