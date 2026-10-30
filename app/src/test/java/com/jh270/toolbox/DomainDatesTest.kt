package com.jh270.toolbox

import com.jh270.toolbox.domain.DomainDates
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainDatesTest {
    @Test
    fun parsesUtcAndNumericOffsets() {
        val utc = DomainDates.parse("2024-08-13T04:00:00Z")
        val shifted = DomainDates.parse("2024-08-13T12:00:00+08:00")
        val compact = DomainDates.parse("2024-08-13T04:00:00+0000")
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.clear()
        calendar.set(2024, Calendar.AUGUST, 13, 4, 0, 0)
        assertEquals(calendar.timeInMillis, utc)
        assertEquals(utc, shifted)
        assertEquals(utc, compact)
    }

    @Test
    fun parsesDateOnlyAndMonthName() {
        val numeric = DomainDates.parse("2024-08-13")
        val named = DomainDates.parse("13-Aug-2024")
        assertEquals(numeric, named)
    }

    @Test
    fun describesExpiryWindow() {
        val now = DomainDates.parse("2024-08-01T00:00:00Z")!!
        val soon = DomainDates.expiryHint("2024-08-13T00:00:00Z", now)
        val today = DomainDates.expiryHint("2024-08-01T18:00:00Z", now)
        val expired = DomainDates.expiryHint("2024-07-30T00:00:00Z", now)
        assertEquals("还有 12 天到期", soon?.text)
        assertTrue(soon!!.urgent)
        assertEquals("今天到期", today?.text)
        assertEquals("已过期 2 天", expired?.text)
    }
}
