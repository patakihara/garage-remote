package com.patakihara.garageremote

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.telecom.TelecomManager

fun openGarage(context: Context, phoneNumber: String, hasAnswerPermission: Boolean) {
    context.startActivity(
        Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    )
    scheduleHangupAndCleanup(context, phoneNumber, hasAnswerPermission)
}

fun scheduleHangupAndCleanup(context: Context, phoneNumber: String, hasAnswerPermission: Boolean) {
    Handler(Looper.getMainLooper()).postDelayed({
        if (hasAnswerPermission) {
            try {
                (context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).endCall()
            } catch (_: SecurityException) {}
        }
        // Best-effort call log deletion — only works on Android 9; restricted on 10+
        Handler(Looper.getMainLooper()).postDelayed({
            deleteCallLogEntry(context, phoneNumber)
        }, 2_000L)
    }, 12_000L)
}

private fun deleteCallLogEntry(context: Context, phoneNumber: String) {
    if (context.checkSelfPermission(Manifest.permission.WRITE_CALL_LOG)
        != PackageManager.PERMISSION_GRANTED) return
    try {
        val suffix = phoneNumber.filter { it.isDigit() }.takeLast(10)
        context.contentResolver.delete(
            CallLog.Calls.CONTENT_URI,
            "${CallLog.Calls.NUMBER} LIKE ?",
            arrayOf("%$suffix"),
        )
    } catch (_: Exception) {}
}

fun returnToApp(context: Context) {
    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
        context.startActivity(
            android.content.Intent(context, MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
        )
    }, 1_500L)
}
