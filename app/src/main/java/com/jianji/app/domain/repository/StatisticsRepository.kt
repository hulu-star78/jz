package com.jianji.app.domain.repository

import com.jianji.app.domain.model.CategoryExpense
import com.jianji.app.domain.model.MonthlySummary
import com.jianji.app.domain.model.TrendData
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 统计仓库：所有统计均由 transactions 表实时聚合得出，不读取任何缓存汇总值。
 */
interface StatisticsRepository {

    /** 观察某自然月的收入/支出/结余。 */
    fun observeMonthlySummary(yearMonth: String): Flow<MonthlySummary>

    /** 观察某自然月的支出分类排行（降序）。 */
    fun observeExpenseRanking(yearMonth: String, limit: Int = 10): Flow<List<CategoryExpense>>

    /** 观察某自然月的收入分类排行（降序）。 */
    fun observeIncomeRanking(yearMonth: String, limit: Int = 10): Flow<List<CategoryExpense>>

    /** 观察任意日期区间的支出分类排行（降序，闭区间）。 */
    fun observeExpenseRanking(start: LocalDate, end: LocalDate, limit: Int = 10): Flow<List<CategoryExpense>>

    /** 观察任意日期区间的收入分类排行（降序，闭区间）。 */
    fun observeIncomeRanking(start: LocalDate, end: LocalDate, limit: Int = 10): Flow<List<CategoryExpense>>

    /** 观察某自然月按天的收支趋势（连续补零）。 */
    fun observeMonthlyTrend(yearMonth: String): Flow<TrendData>

    /**
     * 观察任意日期区间的按天收支趋势（闭区间）。
     * 区间超过 366 天时只返回有交易的日期点，不补零。
     */
    fun observeRangeTrend(start: LocalDate, end: LocalDate): Flow<TrendData>

    /**
     * 观察任意日期区间的按月收支趋势（年度视图，闭区间）。
     * 区间内没有交易的月份不返回，由调用方补零。
     */
    fun observeMonthlyRangeTrend(start: LocalDate, end: LocalDate): Flow<TrendData>
}
