package com.example.smartschedule

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

private fun formatTime(millis: Long): String = DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    refresh: Int,
    onNotificationSettings: () -> Unit,
    onAlarmSettings: () -> Unit,
    onSend: (Schedule) -> Unit,
    onAllowSms: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val dao = remember { AppDatabase.dao(context) }
    val schedules by remember { dao.observe() }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    // null: dialog closed, 0: new task, otherwise the id being edited.
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    val notifications = remember(refresh) { notificationsAllowed(context) }
    val exact = remember(refresh) { Alarms.canBeExact(context) }

    val sms = remember(refresh) { smsAllowed(context) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Scheduled messages") }) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { editing = 0L }) { Text("Add") } },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!notifications) item {
                Warning("Notifications are off, so you will not be told when a message is due.", onNotificationSettings)
            }
            if (!exact) item {
                Warning("Exact alarms are off, so reminders may arrive late.", onAlarmSettings)
            }
            if (!sms) item {
                Warning("Allow SMS to send text messages automatically.", onAllowSms, "Allow SMS")
            }
            if (schedules.isEmpty()) item {
                Text("Nothing scheduled. Tap Add. SMS is sent automatically. For other apps, tap the notification and press Send.")
            }
            items(schedules, key = { it.id }) { s ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${targetName(context, s)} ${s.phone}", style = MaterialTheme.typography.titleMedium)
                                Text(formatTime(s.triggerAt) + if (s.daily) ", daily" else "")
                            }
                            Switch(
                                checked = s.enabled,
                                modifier = Modifier.semantics { contentDescription = "Schedule enabled" },
                                onCheckedChange = { on ->
                                    scope.launch {
                                        val now = System.currentTimeMillis()
                                        val time = if (on) nextDaily(s.triggerAt, now) else s.triggerAt
                                        val updated = s.copy(enabled = on, triggerAt = time)
                                        dao.update(updated)
                                        Alarms.sync(context, updated)
                                    }
                                },
                            )
                        }
                        Text(s.message, maxLines = 3)
                        Row {
                            TextButton(onClick = { onSend(s) }) { Text("Send now") }
                            TextButton(onClick = { editing = s.id }) { Text("Edit") }
                            TextButton(onClick = {
                                scope.launch {
                                    Alarms.cancel(context, s.id)
                                    dao.delete(s)
                                }
                            }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }

    val existing = schedules.firstOrNull { it.id == editing }
    if (editing == 0L || existing != null) {
        TaskDialog(
            existing = existing,
            onDismiss = { editing = null },
            onSave = { schedule ->
                editing = null
                scope.launch {
                    val saved = if (schedule.id == 0L) {
                        schedule.copy(id = dao.insert(schedule))
                    } else {
                        dao.update(schedule)
                        schedule
                    }
                    Alarms.sync(context, saved)
                }
            },
        )
    }
}

@Composable
private fun Warning(text: String, onFix: () -> Unit, action: String = "Open settings") {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(text, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onFix) { Text(action) }
        }
    }
}

@Composable
private fun TaskDialog(existing: Schedule?, onDismiss: () -> Unit, onSave: (Schedule) -> Unit) {
    val context = LocalContext.current
    var target by rememberSaveable { mutableStateOf(existing?.target ?: Target.WHATSAPP) }
    var phone by rememberSaveable { mutableStateOf(existing?.phone.orEmpty()) }
    var message by rememberSaveable { mutableStateOf(existing?.message.orEmpty()) }
    var daily by rememberSaveable { mutableStateOf(existing?.daily ?: false) }
    var time by rememberSaveable { mutableStateOf(existing?.triggerAt ?: (System.currentTimeMillis() + 60 * 60 * 1000)) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var app by rememberSaveable { mutableStateOf(existing?.app.orEmpty()) }
    var sound by rememberSaveable { mutableStateOf(existing?.sound.orEmpty()) }
    var choosingApp by remember { mutableStateOf(false) }
    val apps = remember { shareApps(context) }

    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            val uri = IntentCompat.getParcelableExtra(data, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            sound = if (uri == null || uri == Settings.System.DEFAULT_NOTIFICATION_URI) "" else uri.toString()
        }
    }

    fun pickSound() {
        val current = if (sound.isEmpty()) Settings.System.DEFAULT_NOTIFICATION_URI else sound.toUri()
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)
        try {
            soundPicker.launch(intent)
        } catch (e: ActivityNotFoundException) {
            error = "This phone has no sound picker"
        }
    }

    fun pickTime() {
        val current = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDateTime()
        DatePickerDialog(context, { _, year, month, day ->
            TimePickerDialog(context, { _, hour, minute ->
                time = LocalDateTime.of(year, month + 1, day, hour, minute)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }, current.hour, current.minute, DateFormat.is24HourFormat(context)).show()
        }, current.year, current.monthValue - 1, current.dayOfMonth).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New task" else "Edit task") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Target.entries.forEach { option ->
                    FilterChip(
                        selected = target == option,
                        onClick = { target = option },
                        label = { Text(option.label) },
                    )
                }
                if (target.needsPhone) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Phone number with country code") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                    )
                }
                if (target == Target.OTHER) {
                    Box {
                        OutlinedButton(onClick = { choosingApp = true }) {
                            Text("App: " + (apps.firstOrNull { it.first == app }?.second ?: "choose"))
                        }
                        DropdownMenu(expanded = choosingApp, onDismissRequest = { choosingApp = false }) {
                            apps.forEach { (id, name) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        app = id
                                        choosingApp = false
                                    },
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it.take(1000) },
                    label = { Text("Message") },
                    minLines = 3,
                )
                OutlinedButton(onClick = ::pickTime) { Text(formatTime(time)) }
                OutlinedButton(onClick = ::pickSound) { Text("Sound: " + soundName(context, sound)) }
                Row(
                    modifier = Modifier.toggleable(value = daily, onValueChange = { daily = it }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = daily, onCheckedChange = null)
                    Text("Repeat daily", Modifier.padding(start = 8.dp))
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = validate(target, phone, message, time, System.currentTimeMillis())
                    ?: if (target == Target.OTHER && app.isEmpty()) "Choose an app" else null
                if (error == null) {
                    val number = if (target.needsPhone) cleanPhone(phone) else ""
                    val chosen = if (target == Target.OTHER) app else ""
                    onSave(
                        Schedule(
                            id = existing?.id ?: 0, target = target, phone = number, message = message,
                            triggerAt = time, daily = daily, app = chosen, sound = sound,
                        ),
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
