package com.ai.feishualarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ai.feishualarm.helper.FeishuLauncher

class UnlockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_USER_PRESENT) {
            Log.d("UnlockReceiver", "Device unlocked!")
            val prefs = context.getSharedPreferences("AlarmPrefs", Context.MODE_PRIVATE)
            val pendingOpen = prefs.getBoolean("PENDING_OPEN_FEISHU", false)

            if (pendingOpen) {
                Log.d("UnlockReceiver", "Pending open found, launching Feishu...")
                FeishuLauncher.openFeishu(context)
                prefs.edit().putBoolean("PENDING_OPEN_FEISHU", false).apply()
            }
        }
    }
}
