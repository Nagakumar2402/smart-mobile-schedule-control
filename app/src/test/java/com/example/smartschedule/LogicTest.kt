package com.example.smartschedule

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
    private val zone = ZoneId.of("Europe/London")

    private fun at(day: Int, hour: Int) = ZonedDateTime.of(2026, 3, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun nextDailyKeepsClockTimeAcrossDstAndMissedDays() {
        assertEquals(at(29, 9), nextDaily(at(28, 9), at(28, 9), zone))
        assertEquals(at(31, 9), nextDaily(at(20, 9), at(30, 12), zone))
        assertEquals(at(25, 9), nextDaily(at(25, 9), at(24, 9), zone))
    }

    @Test
    fun validatesInput() {
        assertNull(validate(Target.WHATSAPP, "+91 98765-43210", "Hi", 2, 1))
        assertNull(validate(Target.OTHER, "", "Hi", 2, 1))
        assertNotNull(validate(Target.WHATSAPP, "9876543210", "Hi", 2, 1))
        assertNotNull(validate(Target.SMS, "abc", "Hi", 2, 1))
        assertNotNull(validate(Target.SMS, "12345", " ", 2, 1))
        assertNotNull(validate(Target.SMS, "12345", "Hi", 1, 1))
    }

    @Test
    fun whatsAppUrlEncodesMessage() {
        assertEquals(
            "https://wa.me/919876543210?text=a%20%26%20b%2Bc",
            whatsAppUrl("+91 98765 43210", "a & b+c"),
        )
    }

    @Test
    fun onlyRealSendButtonsMatch() {
        assertTrue(isSendButton("Send", null))
        assertTrue(isSendButton(null, "com.whatsapp:id/send"))
        assertFalse(isSendButton("Send money", "app:id/send_money"))
        assertFalse(isSendButton(null, null))
    }

    @Test
    fun oldMessagesAreNotAutoSent() {
        assertFalse(tooLateToAutoSend(0, 60_000))
        assertTrue(tooLateToAutoSend(0, 3_600_000))
    }
}
