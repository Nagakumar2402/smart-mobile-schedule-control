package com.example.smartschedule

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

private const val CHANNEL = "due_messages"

/** Opens the other app with the message filled in. The user presses Send there. */
fun sendIntent(s: Schedule): Intent = when (s.target) {
    Target.WHATSAPP -> Intent(Intent.ACTION_VIEW, whatsAppUrl(s.phone, s.message).toUri())
    Target.SMS -> Intent(Intent.ACTION_SENDTO, "smsto:${cleanPhone(s.phone)}".toUri())
        .putExtra("sms_body", s.message)
    Target.OTHER -> Intent.createChooser(
        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, s.message),
        "Send with",
    )
}.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

fun notificationsAllowed(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

fun notifyDue(context: Context, s: Schedule) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) return

    context.getSystemService(NotificationManager::class.java).createNotificationChannel(
        NotificationChannel(CHANNEL, "Messages due", NotificationManager.IMPORTANCE_HIGH),
    )
    val open = PendingIntent.getActivity(
        context,
        s.id.toInt(),
        sendIntent(s),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("Tap to send via ${s.target.label}")
        .setContentText(s.message)
        .setStyle(NotificationCompat.BigTextStyle().bigText(s.message))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .setContentIntent(open)
        .setAutoCancel(true)
        .build()
    NotificationManagerCompat.from(context).notify(s.id.toInt(), notification)
}
