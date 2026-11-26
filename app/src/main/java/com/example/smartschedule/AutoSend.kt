package com.example.smartschedule

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

private const val EXTRA_ID = "schedule_id"

fun smsAllowed(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

/** Sends SMS directly; for everything else asks the user with a notification. */
fun deliver(context: Context, s: Schedule) {
    val sent = s.target == Target.SMS &&
        !tooLateToAutoSend(s.triggerAt, System.currentTimeMillis()) &&
        sendSms(context, s)
    if (!sent) notifyDue(context, s)
}

private fun sendSms(context: Context, s: Schedule): Boolean {
    if (!smsAllowed(context)) return false
    val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(SmsManager::class.java)
    } else {
        @Suppress("DEPRECATION")
        SmsManager.getDefault()
    }
    return try {
        val parts = manager.divideMessage(s.message)
        val result = PendingIntent.getBroadcast(
            context,
            s.id.toInt(),
            Intent(context, SmsResultReceiver::class.java).putExtra(EXTRA_ID, s.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.sendMultipartTextMessage(s.phone, null, parts, ArrayList(parts.map { result }), null)
        true
    } catch (e: IllegalArgumentException) {
        false
    } catch (e: UnsupportedOperationException) {
        false
    }
}

/** Tells the user when the network rejects an SMS. */
class SmsResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (resultCode == Activity.RESULT_OK) return
        val id = intent.getLongExtra(EXTRA_ID, -1)
        runAsync {
            AppDatabase.dao(context).byId(id)?.let { notifyDue(context, it, "SMS failed. Tap to send it yourself") }
        }
    }
}
