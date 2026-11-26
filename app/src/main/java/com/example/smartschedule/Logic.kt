package com.example.smartschedule

import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId

enum class Target(val label: String, val needsPhone: Boolean) {
    WHATSAPP("WhatsApp", true),
    SMS("SMS", true),
    OTHER("Other app", false),
}

/** Keeps digits and a leading +, dropping the spaces, dashes and brackets people type. */
fun cleanPhone(raw: String): String {
    val digits = raw.filter { it in '0'..'9' }
    return if (raw.trim().startsWith("+")) "+$digits" else digits
}

/** Returns an error to show the user, or null when the input is usable. */
fun validate(target: Target, phone: String, message: String, triggerAt: Long, now: Long): String? {
    val number = cleanPhone(phone)
    return when {
        message.isBlank() -> "Enter a message"
        target.needsPhone && number.removePrefix("+").length !in 3..15 -> "Enter a valid phone number"
        target == Target.WHATSAPP && (!number.startsWith("+") || number.length < 9) ->
            "WhatsApp needs the country code, for example +91 98765 43210"
        triggerAt <= now -> "Pick a time in the future"
        else -> null
    }
}

private fun encode(text: String) = URLEncoder.encode(text, "UTF-8").replace("+", "%20")

fun whatsAppUrl(phone: String, message: String) =
    "https://wa.me/${cleanPhone(phone).removePrefix("+")}?text=${encode(message)}"

/** First time strictly after [now] that has the same local clock time as [from]. */
// shortcut: only the instant is stored, so daily times drift after a time zone or DST change; store local time if that matters.
fun nextDaily(from: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
    val start = Instant.ofEpochMilli(from).atZone(zone)
    var days = 0L
    var next = start
    while (next.toInstant().toEpochMilli() <= now) next = start.plusDays(++days)
    return next.toInstant().toEpochMilli()
}

/** Messages older than this (phone was off, for example) are not sent without asking. */
fun tooLateToAutoSend(triggerAt: Long, now: Long) = now - triggerAt > 15 * 60 * 1000

// shortcut: English labels only; add the labels your apps use if the phone is in another language.
private val sendLabels = setOf("send", "send message", "send prompt", "send sms")

/** Exact matches only, so buttons such as "Send money" or "Send feedback" are never pressed. */
fun isSendButton(description: String?, viewId: String?) =
    description?.trim()?.lowercase() in sendLabels ||
        viewId?.substringAfterLast('/') in setOf("send", "send_button", "send_message_button")
