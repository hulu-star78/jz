package com.jianji.app.domain.model

import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/**
 * 日期工具：项目统一以 LocalDate 的 epochDay（Long）持久化交易日期，
 * 避免设备时区变化导致历史账目归属日期漂移。
 */
object DateUtils {

    /** 账目日期允许的合理范围（防御性约束，非业务限制）。 */
    val MIN_DATE: LocalDate = LocalDate.of(1970, 1, 1)
    val MAX_DATE: LocalDate = LocalDate.of(2199, 12, 31)

    fun toEpochDay(date: LocalDate): Long = date.toEpochDay().toLong()

    fun fromEpochDay(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    /** LocalDate -> "YYYY-MM"（自然月键）。 */
    fun yearMonthOf(date: LocalDate): String =
        String.format(Locale.ROOT, "%04d-%02d", date.year, date.monthValue)

    /** "YYYY-MM" -> YearMonth；格式非法返回 null。 */
    fun parseYearMonth(yearMonth: String): YearMonth? {
        val match = Regex("^(\\d{4})-(\\d{2})$").matchEntire(yearMonth) ?: return null
        val year = match.groupValues[1].toIntOrNull() ?: return null
        val month = match.groupValues[2].toIntOrNull() ?: return null
        if (month !in 1..12) return null
        return YearMonth.of(year, month)
    }

    /** 自然月的 [起始 epochDay, 结束 epochDay] 闭区间；格式非法抛 IllegalArgumentException。 */
    fun monthRange(yearMonth: String): LongRange {
        val ym = requireNotNull(parseYearMonth(yearMonth)) { "非法的 yearMonth: $yearMonth" }
        return ym.atDay(1).toEpochDay().toLong()..ym.atEndOfMonth().toEpochDay().toLong()
    }

    fun isValidDate(date: LocalDate): Boolean = !date.isBefore(MIN_DATE) && !date.isAfter(MAX_DATE)
}
