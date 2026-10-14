package com.jianji.app.data.repository

import com.jianji.app.domain.model.CategoryType
import com.jianji.app.domain.model.Money
import com.jianji.app.domain.model.TransactionType
import com.jianji.app.testutil.DatabaseTestSupport
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * 统计仓库测试：收入/支出统计、分类排行、趋势、转账不计入统计。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StatisticsRepositoryTest : DatabaseTestSupport() {

    private lateinit var repository: StatisticsRepositoryImpl
    private var foodId: Long = 0L
    private var transportId: Long = 0L
    private var cashId: Long = 0L
    private var bankId: Long = 0L

    private suspend fun seed() {
        foodId = insertCategory("餐饮", CategoryType.EXPENSE)
        transportId = insertCategory("交通", CategoryType.EXPENSE)
        cashId = insertAccount("现金")
        bankId = insertAccount("银行卡")
        repository = StatisticsRepositoryImpl(database.transactionDao(), database.categoryDao())
    }

    @Test
    fun `monthly summary computes income expense and balance`() = runTest {
        seed()
        insertTransaction(100000, TransactionType.INCOME, LocalDate.of(2026, 10, 1), null, cashId)
        insertTransaction(30000, TransactionType.EXPENSE, LocalDate.of(2026, 10, 2), foodId, cashId)
        insertTransaction(20000, TransactionType.EXPENSE, LocalDate.of(2026, 10, 3), transportId, cashId)
        // 上月数据不得计入
        insertTransaction(99900, TransactionType.EXPENSE, LocalDate.of(2026, 9, 15), foodId, cashId)

        val summary = repository.observeMonthlySummary("2026-10").first()
        assertEquals(Money.fromMinor(100000), summary.income)
        assertEquals(Money.fromMinor(50000), summary.expense)
        assertEquals(Money.fromMinor(50000), summary.balance)
    }

    @Test
    fun `transfer is excluded from income and expense statistics`() = runTest {
        seed()
        insertTransaction(10000, TransactionType.INCOME, LocalDate.of(2026, 10, 1), null, cashId)
        insertTransaction(3000, TransactionType.EXPENSE, LocalDate.of(2026, 10, 2), foodId, cashId)
        insertTransaction(5000, TransactionType.TRANSFER, LocalDate.of(2026, 10, 3), null, cashId, toAccountId = bankId)

        val summary = repository.observeMonthlySummary("2026-10").first()
        assertEquals(Money.fromMinor(10000), summary.income)
        assertEquals(Money.fromMinor(3000), summary.expense)
        assertEquals(Money.fromMinor(7000), summary.balance)
    }

    @Test
    fun `expense ranking sorts descending with names`() = runTest {
        seed()
        insertTransaction(3000, TransactionType.EXPENSE, LocalDate.of(2026, 10, 1), foodId, cashId)
        insertTransaction(2000, TransactionType.EXPENSE, LocalDate.of(2026, 10, 2), transportId, cashId)
        insertTransaction(1000, TransactionType.EXPENSE, LocalDate.of(2026, 10, 3), foodId, cashId)

        val ranking = repository.observeExpenseRanking("2026-10").first()
        assertEquals(2, ranking.size)
        assertEquals("餐饮", ranking[0].categoryName)
        assertEquals(Money.fromMinor(4000), ranking[0].amount)
        assertEquals(2, ranking[0].count)
        assertEquals("交通", ranking[1].categoryName)
        assertEquals(Money.fromMinor(2000), ranking[1].amount)
        assertEquals(CategoryType.EXPENSE, ranking[0].type)
    }

    @Test
    fun `income ranking works separately`() = runTest {
        seed()
        val salaryCategory = insertCategory("工资", CategoryType.INCOME)
        val investCategory = insertCategory("理财", CategoryType.INCOME)
        insertTransaction(50000, TransactionType.INCOME, LocalDate.of(2026, 10, 1), investCategory, cashId)
        insertTransaction(80000, TransactionType.INCOME, LocalDate.of(2026, 10, 2), salaryCategory, bankId)

        val ranking = repository.observeIncomeRanking("2026-10").first()
        assertEquals(2, ranking.size)
        assertEquals("工资", ranking[0].categoryName)
        assertEquals(Money.fromMinor(80000), ranking[0].amount)
        assertEquals("理财", ranking[1].categoryName)
        assertEquals(Money.fromMinor(50000), ranking[1].amount)
    }

    @Test
    fun `monthly trend fills every day and totals match`() = runTest {
        seed()
        insertTransaction(10000, TransactionType.INCOME, LocalDate.of(2026, 10, 1), null, cashId)
        insertTransaction(3000, TransactionType.EXPENSE, LocalDate.of(2026, 10, 15), foodId, cashId)
        insertTransaction(2000, TransactionType.EXPENSE, LocalDate.of(2026, 10, 15), transportId, cashId)
        insertTransaction(5000, TransactionType.TRANSFER, LocalDate.of(2026, 10, 20), null, cashId, toAccountId = bankId)

        val trend = repository.observeMonthlyTrend("2026-10").first()
        // 10 月共 31 天，必须补齐
        assertEquals(31, trend.points.size)
        assertEquals(LocalDate.of(2026, 10, 1), trend.points.first().date)
        assertEquals(LocalDate.of(2026, 10, 31), trend.points.last().date)
        assertEquals(Money.fromMinor(10000), trend.totalIncome)
        assertEquals(Money.fromMinor(5000), trend.totalExpense)
        // 无交易日补零
        assertEquals(Money.ZERO, trend.points[5].income)
        // 转账日两列均为 0
        assertEquals(Money.ZERO, trend.points[19].income)
        assertEquals(Money.ZERO, trend.points[19].expense)
    }

    @Test
    fun `range trend does not fill gaps beyond one year`() = runTest {
        seed()
        insertTransaction(1000, TransactionType.EXPENSE, LocalDate.of(2026, 1, 10), foodId, cashId)
        insertTransaction(2000, TransactionType.EXPENSE, LocalDate.of(2026, 6, 10), foodId, cashId)

        val trend = repository.observeRangeTrend(
            LocalDate.of(2025, 1, 1),
            LocalDate.of(2026, 12, 31),
        ).first()
        // 超过 366 天，仅返回有交易的日期点
        assertEquals(2, trend.points.size)
        assertTrue(trend.totalExpense == Money.fromMinor(3000))
    }
}
