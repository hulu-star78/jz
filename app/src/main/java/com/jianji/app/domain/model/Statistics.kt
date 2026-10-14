package com.jianji.app.domain.model

import java.time.LocalDate

/** 月度汇总：收入、支出与结余（结余由收入减支出实时得出，不落库）。 */
data class MonthlySummary(
    val yearMonth: String,
    val income: Money,
    val expense: Money,
) {
    val balance: Money get() = income - expense
}

/**
 * 分类收支排行项。
 *
 * [type] 标明金额语义（收入排行或支出排行）；
 * [categoryId] 为 null 表示分类已被删除或未设置，[categoryName] 显示为「未分类」。
 */
data class CategoryExpense(
    val type: CategoryType,
    val categoryId: Long?,
    val categoryName: String,
    val amount: Money,
    val count: Int,
)

/** 趋势图单个数据点（按天）。 */
data class TrendPoint(
    val date: LocalDate,
    val income: Money,
    val expense: Money,
) {
    val balance: Money get() = income - expense
}

/** 趋势数据：连续日期序列（无交易的日子补零，保证图表连续）。 */
data class TrendData(val points: List<TrendPoint>) {
    val totalIncome: Money get() = points.fold(Money.ZERO) { acc, p -> acc + p.income }
    val totalExpense: Money get() = points.fold(Money.ZERO) { acc, p -> acc + p.expense }
}
