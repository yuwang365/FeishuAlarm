package com.ai.feishualarm.helper

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

object FeishuLauncher {
    private const val PACKAGE_NAME = "com.ss.android.lark"

    fun openFeishu(context: Context) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(PACKAGE_NAME)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(launchIntent)
            Log.d("FeishuLauncher", "Feishu opened successfully")
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // 清除该应用之前发送的所有通知
            notificationManager.cancelAll()
        } else {
            val errorMsg = "未找到飞书应用，请检查是否安装"
            Log.e("FeishuLauncher", errorMsg)
            // 只有在 Context 是 Activity 时才弹出 Toast，或者直接使用 applicationContext
            Toast.makeText(context.applicationContext, errorMsg, Toast.LENGTH_SHORT).show()
        }
    }
}
