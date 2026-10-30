package com.daytoday.util

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

    @Test
    fun `formatSteps groups exact counts with locale separators`() {
        assertEquals("0", formatSteps(0L, Locale.US))
        assertEquals("999", formatSteps(999L, Locale.US))
        assertEquals("1,000", formatSteps(1_000L, Locale.US))
        assertEquals("7,639", formatSteps(7_639L, Locale.US))
        assertEquals("999,999", formatSteps(999_999L, Locale.US))
        assertEquals("1,000,000", formatSteps(1_000_000L, Locale.US))
    }

    @Test
    fun `formatSteps keeps every digit instead of abbreviating`() {
        assertEquals("12,345", formatSteps(12_345L, Locale.US))
    }

    @Test
    fun `formatSteps respects the requested locale`() {
        assertEquals("7.639", formatSteps(7_639L, Locale.GERMANY))
    }
}
