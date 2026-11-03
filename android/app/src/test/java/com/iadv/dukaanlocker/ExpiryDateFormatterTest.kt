package com.iadv.dukaanlocker

import com.iadv.dukaanlocker.ui.screens.formatExpiryBadgeDate
import com.iadv.dukaanlocker.ui.screens.formatExpiryBodyDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ExpiryDateFormatterTest {

    @Test
    fun `iso datetime is formatted as dd MMM for badge`() {
        assertEquals("02 Jan", formatExpiryBadgeDate("2026-01-02T00:00:00", Locale.US))
    }

    @Test
    fun `plain yyyy-MM-dd is formatted for badge`() {
        assertEquals("31 Dec", formatExpiryBadgeDate("2025-12-31", Locale.US))
    }

    @Test
    fun `badge date with time component is truncated to date part`() {
        assertEquals("15 Mar", formatExpiryBadgeDate("2026-03-15T18:30:00", Locale.US))
    }

    @Test
    fun `blank input returns blank`() {
        assertEquals("", formatExpiryBadgeDate("", Locale.US))
        assertEquals("   ", formatExpiryBadgeDate("   ", Locale.US))
    }

    @Test
    fun `garbage input returns raw string unchanged`() {
        assertEquals("not-a-date", formatExpiryBadgeDate("not-a-date", Locale.US))
        assertEquals("12/31/2026", formatExpiryBadgeDate("12/31/2026", Locale.US))
    }

    @Test
    fun `body date includes year`() {
        assertEquals("02 Jan 2026", formatExpiryBodyDate("2026-01-02T00:00:00", Locale.US))
    }

    @Test
    fun `body date garbage returns raw`() {
        assertEquals("garbage", formatExpiryBodyDate("garbage", Locale.US))
        assertEquals("", formatExpiryBodyDate("", Locale.US))
    }

    @Test
    fun `badge date never returns null`() {
        val result = formatExpiryBadgeDate("2026-01-02T00:00:00", Locale.US)
        assertTrue(result.isNotBlank())
    }
}
