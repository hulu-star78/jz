package com.jianji.app.data.repository

import com.jianji.app.data.local.dao.CategoryDao
import com.jianji.app.data.local.dao.TransactionDao
import com.jianji.app.domain.model.CategoryExpense
import com.jianji.app.domain.model.CategoryType
import com.jianji.app.domain.model.DateUtils
import com.jianji.app.domain.model.Money
import com.jianji.app.domain.model.MonthlySummary
import com.jianji.app.domain.model.TransactionType
import com.jianji.app.domain.model.TrendData
import com.jianji.app.domain.model.TrendPoint
import com.jianji.app.domain.repository.StatisticsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth

/**
 * 统计仓库实现：所有数字均由 transactions 表 SQL 实时聚合，
 * 不读取、不维护任何汇总缓存；账目修改/删除后统计自动保持一致。
 */
class StatisticsRepositoryImpl(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
) : StatisticsRepository {

    override fun observeMonthlySummary(yearMonth: String): Flow<MonthlySummary> {
        val range = DateUtils.monthRange(yearMonth)
        return combine(
            transactionDao.observeTotalByType(range.first, range.last, TransactionType.INCOME),
            transactionDao.observeTotalByType(range.first, range.last, TransactionType.EXPENSE),
        ) { incomeMinor, expenseMinor ->
            MonthlySummary(
                yearMonth = yearMonth,
                income = Money.fromMinor(incomeMinor),
                expense = Money.fromMinor(expenseMinor),
            )
        }
    }

    override fun observeExpenseRanking(yearMonth: String, limit: Int): Flow<List<CategoryExpense>> {
        val range = DateUtils.monthRange(yearMonth)
        return observeRanking(range.first, range.last, CategoryType.EXPENSE, limit)
    }

    override fun observeIncomeRanking(yearMonth: String, limit: Int): Flow<List<CategoryExpense>> {
        val range = DateUtils.monthRange(yearMonth)
        return observeRanking(range.first, range.last, CategoryType.INCOME, limit)
    }

    override fun observeExpenseRanking(start: LocalDate, end: LocalDate, limit: Int): Flow<List<CategoryExpense>> =
        observeRanking(DateUtils.toEpochDay(start), DateUtils.toEpochDay(end), CategoryType.EXPENSE, limit)

    override fun observeIncomeRanking(start: LocalDate, end: LocalDate, limit: Int): Flow<List<CategoryExpense>> =
        observeRanking(DateUtils.toEpochDay(start), DateUtils.toEpochDay(end), CategoryType.INCOME, limit)

    private fun observeRanking(
        startEpochDay: Long,
        endEpochDay: Long,
        categoryType: CategoryType,
        limit: Int,
    ): Flow<List<CategoryExpense>> {
        val transactionType =
            if (categoryType == CategoryType.EXPENSE) TransactionType.EXPENSE else TransactionType.INCOME
        return combine(
            transactionDao.observeCategoryTotals(startEpochDay, endEpochDay, transactionType),
            categoryDao.observeAll(),
        ) { rows, categories ->
            val nameById = categories.associate { it.id to it.name }
            rows.map { row ->
                CategoryExpense(
                    type = categoryType,
                    categoryId = row.categoryId,
                    categoryName = row.categoryId?.let { nameById[it] } ?: UNCATEGORIZED_NAME,
                    amount = Money.fromMinor(row.totalMinor),
                    count = row.count,
                )
            }.take(limit)
        }
    }

    override fun observeMonthlyTrend(yearMonth: String): Flow<TrendData> {
        val range = DateUtils.monthRange(yearMonth)
        return observeTrend(range.first, range.last, fillGaps = true)
    }

    override fun observeRangeTrend(start: LocalDate, end: LocalDate): Flow<TrendData> {
        val startDay = DateUtils.toEpochDay(start)
        val endDay = DateUtils.toEpochDay(end)
        return observeTrend(startDay, endDay, fillGaps = (endDay - startDay) <= MAX_FILL_GAP_DAYS)
    }

    override fun observeMonthlyRangeTrend(start: LocalDate, end: LocalDate): Flow<TrendData> {
        val startDay = DateUtils.toEpochDay(start)
        val endDay = DateUtils.toEpochDay(end)
        return transactionDao.observeMonthlyTrend(
            startEpochDay = startDay,
            endEpochDay = endDay,
            incomeType = TransactionType.INCOME,
            expenseType = TransactionType.EXPENSE,
        ).map { rows ->
            val byMonth = rows.associateBy { it.yearMonth }
            val points = ArrayList<TrendPoint>()
            var ym = YearMonth.from(start)
            val endYm = YearMonth.from(end)
            while (!ym.isAfter(endYm)) {
                val key = String.format(java.util.Locale.ROOT, "%04d-%02d", ym.year, ym.monthValue)
                val row = byMonth[key]
                points += TrendPoint(
                    date = ym.atDay(1),
                    income = Money.fromMinor(row?.incomeMinor ?: 0L),
                    expense = Money.fromMinor(row?.expenseMinor ?: 0L),
                )
                ym = ym.plusMonths(1)
            }
            TrendData(points)
        }
    }

    private fun observeTrend(
        startEpochDay: Long,
        endEpochDay: Long,
        fillGaps: Boolean,
    ): Flow<TrendData> =
        transactionDao.observeDailyTrend(
            startEpochDay = startEpochDay,
            endEpochDay = endEpochDay,
            incomeType = TransactionType.INCOME,
            expenseType = TransactionType.EXPENSE,
        ).map { rows ->
            if (!fillGaps) {
                return@map TrendData(
                    rows.map { row ->
                        TrendPoint(
                            date = DateUtils.fromEpochDay(row.epochDay),
                            income = Money.fromMinor(row.incomeMinor),
                            expense = Money.fromMinor(row.expenseMinor),
                        )
                    },
                )
            }
            val byDay = rows.associateBy { it.epochDay }
            val points = ArrayList<TrendPoint>()
            var day = startEpochDay
            while (day <= endEpochDay) {
                val row = byDay[day]
                points += TrendPoint(
                    date = DateUtils.fromEpochDay(day),
                    income = Money.fromMinor(row?.incomeMinor ?: 0L),
                    expense = Money.fromMinor(row?.expenseMinor ?: 0L),
                )
                day++
            }
            TrendData(points)
        }

    private companion object {
        /** 超过一年的区间不补零，避免产生过多数据点。 */
        const val MAX_FILL_GAP_DAYS = 366L

        /** 分类被删除或未设置时的展示名。 */
        const val UNCATEGORIZED_NAME = "未分类"
    }
}
