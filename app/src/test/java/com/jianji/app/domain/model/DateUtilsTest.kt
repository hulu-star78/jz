package com.jianji.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.util.TimeZone

/**
 * 日期测试：月初/月末、跨年、闰年、时区变化。
 */
class DateUtilsTest {

    @Test
    fun `month range covers whole calendar month`() {
        val range = DateUtils.monthRange("2026-10")
        assertEquals(LocalDate.of(2026, 10, 1), DateUtils.fromEpochDay(range.first))
        assertEquals(LocalDate.of(2026, 10, 31), DateUtils.fromEpochDay(range.last))
    }

    @Test
    fun `month range handles leap and common february`() {
        val leap = DateUtils.monthRange("2024-02")
        assertEquals(LocalDate.of(2024, 2, 1), DateUtils.fromEpochDay(leap.first))
        assertEquals(LocalDate.of(2024, 2, 29), DateUtils.fromEpochDay(leap.last))

        val common = DateUtils.monthRange("2025-02")
        assertEquals(LocalDate.of(2025, 2, 28), DateUtils.fromEpochDay(common.last))
    }

    @Test
    fun `month range across year boundary`() {
        val dec = DateUtils.monthRange("2026-12")
        assertEquals(LocalDate.of(2026, 12, 1), DateUtils.fromEpochDay(dec.first))
        assertEquals(LocalDate.of(2026, 12, 31), DateUtils.fromEpochDay(dec.last))

        val jan = DateUtils.monthRange("2027-01")
        assertEquals(LocalDate.of(2027, 1, 1), DateUtils.fromEpochDay(jan.first))
        // 12 月最后一天的下一天必须是次年 1 月 1 日
        assertEquals(jan.first, dec.last + 1)
    }

    @Test
    fun `year month format and parse round trip`() {
        assertEquals("2026-01", DateUtils.yearMonthOf(LocalDate.of(2026, 1, 5)))
        assertEquals("2026-12", DateUtils.yearMonthOf(LocalDate.of(2026, 12, 31)))
        assertEquals(YearMonth.of(2026, 10), DateUtils.parseYearMonth("2026-10"))
    }

    @Test
    fun `parse year month rejects invalid input`() {
        assertNull(DateUtils.parseYearMonth("2026-13"))
        assertNull(DateUtils.parseYearMonth("2026-00"))
        assertNull(DateUtils.parseYearMonth("2026-1"))
        assertNull(DateUtils.parseYearMonth("26-01"))
        assertNull(DateUtils.parseYearMonth("2026/10"))
        assertNull(DateUtils.parseYearMonth(""))
    }

    @Test
    fun `invalid month range throws`() {
        val ex = runCatching { DateUtils.monthRange("2026-13") }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `epoch day is timezone independent`() {
        val original = TimeZone.getDefault()
        try {
            val date = LocalDate.of(2026, 10, 13)
            val zones = listOf("UTC", "Pacific/Kiritimati", "Pacific/Midway", "Asia/Shanghai", "America/New_York")
            val reference = date.toEpochDay()
            for (zone in zones) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                // 无论设备时区如何变化，历史日期的 epochDay 必须不变
                assertEquals(reference, date.toEpochDay())
                assertEquals(reference, DateUtils.toEpochDay(date))
                assertEquals(date, DateUtils.fromEpochDay(reference))
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `date validity bounds`() {
        assertTrue(DateUtils.isValidDate(DateUtils.MIN_DATE))
        assertTrue(DateUtils.isValidDate(DateUtils.MAX_DATE))
        assertFalse(DateUtils.isValidDate(DateUtils.MIN_DATE.minusDays(1)))
        assertFalse(DateUtils.isValidDate(DateUtils.MAX_DATE.plusDays(1)))
    }
}
