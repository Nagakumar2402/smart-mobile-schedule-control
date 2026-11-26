package com.example.smartschedule

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Telephony
import android.telephony.SmsManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat

private const val EXTRA_ID = "schedule_id"

fun smsAllowed(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

/** Sends without help where Android allows it, otherwise asks the user with a notification. */
fun deliver(context: Context, s: Schedule) {
    val started = when {
        tooLateToAutoSend(s.triggerAt, System.currentTimeMillis()) -> false
        s.target == Target.SMS -> sendSms(context, s)
        else -> AutoSendService.instance?.send(s) == true
    }
    if (!started) notifyDue(context, s)
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

/**
 * Opens the target app and presses its Send button. It reads the screen only while a send is
 * pending, only in the target app, and only presses Send if the scheduled text is in the input box.
 */
class AutoSendService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())

    @Volatile private var busy = false

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    /** Returns false when automatic sending is not possible right now. */
    @Synchronized
    fun send(s: Schedule): Boolean {
        val target = when (s.target) {
            Target.WHATSAPP -> "com.whatsapp"
            Target.OTHER -> s.app
            Target.SMS -> Telephony.Sms.getDefaultSmsPackage(this).orEmpty()
        }
        val usable = !busy && target.isNotEmpty() &&
            getSystemService(PowerManager::class.java).isInteractive &&
            !getSystemService(KeyguardManager::class.java).isKeyguardLocked
        if (!usable) return false
        try {
            startActivity(sendIntent(this, s).setPackage(target).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK))
        } catch (e: ActivityNotFoundException) {
            return false
        }
        busy = true
        // Give the app time to open before looking, then retry for about 30 seconds.
        handler.postDelayed({ press(s, target, 30) }, 2000)
        return true
    }

    private fun press(s: Schedule, target: String, triesLeft: Int) {
        val root = rootInActiveWindow
        if (root?.packageName?.toString() == target) {
            val nodes = flatten(root)
            val filled = nodes.any { it.isEditable && it.text?.contains(s.message.trim()) == true }
            val button = nodes.firstOrNull { isSendButton(it.contentDescription?.toString(), it.viewIdResourceName) }
            val clickable = generateSequence(button) { it.parent }.firstOrNull { it.isClickable }
            if (filled && clickable?.isEnabled == true && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                busy = false
                notifyDue(this, s, "Pressed Send. Tap to check")
                return
            }
        }
        if (triesLeft > 0) {
            handler.postDelayed({ press(s, target, triesLeft - 1) }, 1000)
        } else {
            busy = false
            notifyDue(this, s, "Could not send automatically. Tap to send")
        }
    }

    private fun flatten(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> =
        listOf(node) + (0 until node.childCount).mapNotNull { node.getChild(it) }.flatMap(::flatten)

    companion object {
        @Volatile var instance: AutoSendService? = null
            private set
    }
}
