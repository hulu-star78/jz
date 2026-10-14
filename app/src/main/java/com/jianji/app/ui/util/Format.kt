package com.jianji.app.ui.util

import com.jianji.app.domain.model.CategoryType
import com.jianji.app.domain.model.Money
import com.jianji.app.domain.model.TransactionType
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** 金额显示格式化（仅用于展示，禁止反向参与计算）。 */
fun Money.plain(): String = format(groupSeparator = ',', decimalSeparator = '.')

/** 带符号的金额：收入 +、支出 -、转账无符号。 */
fun Money.signed(type: TransactionType): String = when (type) {
    TransactionType.INCOME -> "+${plain()}"
    TransactionType.EXPENSE -> "-${plain()}"
    TransactionType.TRANSFER -> plain()
}

/** 千元整数概览（趋势轴等紧凑场景），如 1.2k。 */
fun Money.compact(): String {
    val yuan = minor / 100.0
    return when {
        kotlin.math.abs(yuan) >= 100000 -> String.format(Locale.ROOT, "%.1f万", yuan / 10000)
        kotlin.math.abs(yuan) >= 10000 -> String.format(Locale.ROOT, "%.1fw", yuan / 10000)
        kotlin.math.abs(yuan) >= 1000 -> String.format(Locale.ROOT, "%.1fk", yuan / 1000)
        else -> String.format(Locale.ROOT, "%.0f", yuan)
    }
}

/** 日期分组标题：今天 / 昨天 / 10月12日 周六 / 2025年10月12日 周六。 */
fun LocalDate.groupTitle(today: LocalDate = LocalDate.now()): String = when (this) {
    today -> "今天"
    today.minusDays(1) -> "昨天"
    else -> if (year == today.year) {
        val weekday = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINA)
        "${monthValue}月${dayOfMonth}日 $weekday"
    } else {
        val weekday = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINA)
        "${year}年${monthValue}月${dayOfMonth}日 $weekday"
    }
}

/** 账单行副标题：备注优先，否则分类名。 */
fun recordSubtitle(note: String?, categoryName: String?): String =
    note?.takeIf { it.isNotBlank() } ?: categoryName ?: "转账"

/** 月份显示：2026年10月。 */
fun YearMonth.displayName(): String = "${year}年${monthValue}月"

/** 预算周期键：YYYY-MM。 */
fun YearMonth.key(): String = String.format(Locale.ROOT, "%04d-%02d", year, monthValue)

/** 百分比文本（保留 1 位小数，整数时不显示 .0）。 */
fun percentText(part: Long, total: Long): String {
    if (total <= 0L) return "0%"
    val permille = if (part >= total) 1000L else part * 1000 / total
    return if (permille % 10 == 0L) "${permille / 10}%" else "${permille / 10}.${permille % 10}%"
}

/** 分类类型对应的语义色角色判断辅助。 */
fun CategoryType.isIncome(): Boolean = this == CategoryType.INCOME
