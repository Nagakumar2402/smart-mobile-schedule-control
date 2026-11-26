package com.example.smartschedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val EXTRA_ID = "schedule_id"

object Alarms {
    fun canBeExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun pendingIntent(context: Context, id: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        id.toInt(),
        Intent(context, AlarmReceiver::class.java).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Sets or cancels the alarm to match the schedule. A past time fires immediately. */
    fun sync(context: Context, s: Schedule) {
        if (!s.enabled) return cancel(context, s.id)
        val manager = context.getSystemService(AlarmManager::class.java)
        val intent = pendingIntent(context, s.id)
        if (canBeExact(context)) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, s.triggerAt, intent)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, s.triggerAt, intent)
        }
    }

    fun cancel(context: Context, id: Long) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, id))
    }

    suspend fun syncAll(context: Context) {
        AppDatabase.dao(context).enabled().forEach { sync(context, it) }
    }

    /** Periodic safety net for alarms the system dropped. */
    fun startWatchdog(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "rearm-alarms",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RearmWorker>(6, TimeUnit.HOURS).build(),
        )
    }
}

class RearmWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Alarms.syncAll(applicationContext)
        return Result.success()
    }
}

fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

// Stops two deliveries of the same alarm from both sending.
private val claim = Mutex()

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1)
        runAsync {
            val dao = AppDatabase.dao(context)
            // Move the schedule on before sending, so it can never be sent twice.
            val due = claim.withLock {
                val s = dao.byId(id) ?: return@withLock null
                val now = System.currentTimeMillis()
                if (!s.enabled || s.triggerAt > now) return@withLock null
                val next = if (s.daily) s.copy(triggerAt = nextDaily(s.triggerAt, now)) else s.copy(enabled = false)
                dao.update(next)
                Alarms.sync(context, next)
                s
            }
            due?.let { deliver(context, it) }
        }
    }
}

/** Alarms are lost on reboot and update, and must be reset after clock or permission changes. */
class RearmReceiver : BroadcastReceiver() {
    private val actions = setOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
        "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in actions) return
        runAsync { Alarms.syncAll(context) }
    }
}
