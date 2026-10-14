package com.jianji.app.data.local.dao

import com.jianji.app.domain.model.CategoryType
import com.jianji.app.domain.model.TransactionType
import com.jianji.app.testutil.DatabaseTestSupport
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * 交易 DAO 测试：新增、修改、删除、日期范围、分类、账户、组合筛选。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TransactionDaoTest : DatabaseTestSupport() {

    private var foodId: Long = 0L
    private var transportId: Long = 0L
    private var cashId: Long = 0L
    private var bankId: Long = 0L

    private suspend fun seed() {
        foodId = insertCategory("餐饮", CategoryType.EXPENSE)
        transportId = insertCategory("交通", CategoryType.EXPENSE)
        cashId = insertAccount("现金")
        bankId = insertAccount("银行卡")
    }

    @Test
    fun `insert update and delete`() = runTest {
        seed()
        val id = insertTransaction(
            amountMinor = 3500,
            type = TransactionType.EXPENSE,
            date = LocalDate.of(2026, 10, 2),
            categoryId = foodId,
            accountId = cashId,
            note = "午饭",
        )
        val inserted = database.transactionDao().findById(id)
        assertNotNull(inserted)
        assertEquals(3500L, inserted!!.amountMinor)
        assertEquals(TransactionType.EXPENSE, inserted.type)
        assertEquals(LocalDate.of(2026, 10, 2).toEpochDay().toLong(), inserted.transactionDate)
        assertEquals("午饭", inserted.note)

        val updatedRows = database.transactionDao().update(
            inserted.copy(amountMinor = 4000, note = "午饭改"),
        )
        assertEquals(1, updatedRows)
        assertEquals(4000L, database.transactionDao().findById(id)!!.amountMinor)

        val deletedRows = database.transactionDao().deleteById(id)
        assertEquals(1, deletedRows)
        assertNull(database.transactionDao().findById(id))
    }

    @Test
    fun `observe by date range`() = runTest {
        seed()
        insertTransaction(1000, TransactionType.EXPENSE, LocalDate.of(2026, 9, 30), foodId, cashId)
        insertTransaction(2000, TransactionType.EXPENSE, LocalDate.of(2026, 10, 1), foodId, cashId)
        insertTransaction(3000, TransactionType.EXPENSE, LocalDate.of(2026, 10, 31), foodId, cashId)
        insertTransaction(4000, TransactionType.EXPENSE, LocalDate.of(2026, 11, 1), foodId, cashId)

        val october = database.transactionDao().observeByDateRange(
            LocalDate.of(2026, 10, 1).toEpochDay().toLong(),
            LocalDate.of(2026, 10, 31).toEpochDay().toLong(),
        ).first()
        assertEquals(2, october.size)
        // 按日期倒序
        assertEquals(LocalDate.of(2026, 10, 31).toEpochDay().toLong(), october[0].transactionDate)
        assertEquals(LocalDate.of(2026, 10, 1).toEpochDay().toLong(), october[1].transactionDate)
    }

    @Test
    fun `observe by category and account`() = runTest {
        seed()
        insertTransaction(1000, TransactionType.EXPENSE, LocalDate.of(2026, 10, 1), foodId, cashId)
        insertTransaction(2000, TransactionType.EXPENSE, LocalDate.of(2026, 10, 2), transportId, bankId)
        insertTransaction(3000, TransactionType.EXPENSE, LocalDate.of(2026, 10, 3), foodId, bankId)

        assertEquals(2, database.transactionDao().observeByCategory(foodId).first().size)
        assertEquals(1, database.transactionDao().observeByCategory(transportId).first().size)

        assertEquals(1, database.transactionDao().observeByAccount(cashId).first().size)
        assertEquals(2, database.transactionDao().observeByAccount(bankId).first().size)
    }

    @Test
    fun `observe filtered combines conditions`() = runTest {
        seed()
        insertTransaction(1000, TransactionType.EXPENSE, LocalDate.of(2026, 10, 1), foodId, cashId, note = "午餐")
        insertTransaction(2000, TransactionType.INCOME, LocalDate.of(2026, 10, 2), foodId, bankId, note = "报销")
        insertTransaction(3000, TransactionType.EXPENSE, LocalDate.of(2026, 10, 5), transportId, cashId, note = "地铁")

        val byKeyword = database.transactionDao().observeFiltered(
            null, null, null, null, null, "午餐",
        ).first()
        assertEquals(1, byKeyword.size)

        val byType = database.transactionDao().observeFiltered(
            null, null, TransactionType.INCOME, null, null, null,
        ).first()
        assertEquals(1, byType.size)

        val byMonth = database.transactionDao().observeFiltered(
            LocalDate.of(2026, 10, 1).toEpochDay().toLong(),
            LocalDate.of(2026, 10, 3).toEpochDay().toLong(),
            null, null, null, null,
        ).first()
        assertEquals(2, byMonth.size)

        val byAccount = database.transactionDao().observeFiltered(
            null, null, null, null, cashId, null,
        ).first()
        assertEquals(2, byAccount.size)
    }

    @Test
    fun `totals by type exclude transfer from income and expense`() = runTest {
        seed()
        insertTransaction(10000, TransactionType.INCOME, LocalDate.of(2026, 10, 1), null, cashId)
        insertTransaction(3000, TransactionType.EXPENSE, LocalDate.of(2026, 10, 2), foodId, cashId)
        insertTransaction(5000, TransactionType.TRANSFER, LocalDate.of(2026, 10, 3), null, cashId, toAccountId = bankId)

        val start = LocalDate.of(2026, 10, 1).toEpochDay().toLong()
        val end = LocalDate.of(2026, 10, 31).toEpochDay().toLong()
        val totals = database.transactionDao().observeTotalsByType(start, end).first()
        val income = totals.first { it.type == TransactionType.INCOME }
        val expense = totals.first { it.type == TransactionType.EXPENSE }
        val transfer = totals.first { it.type == TransactionType.TRANSFER }
        assertEquals(10000L, income.totalMinor)
        assertEquals(3000L, expense.totalMinor)
        assertEquals(5000L, transfer.totalMinor)
    }

    @Test
    fun `category totals group and rank`() = runTest {
        seed()
        insertTransaction(3000, TransactionType.EXPENSE, LocalDate.of(2026, 10, 1), foodId, cashId)
        insertTransaction(2000, TransactionType.EXPENSE, LocalDate.of(2026, 10, 2), transportId, cashId)
        insertTransaction(1000, TransactionType.EXPENSE, LocalDate.of(2026, 10, 3), foodId, cashId)

        val start = LocalDate.of(2026, 10, 1).toEpochDay().toLong()
        val end = LocalDate.of(2026, 10, 31).toEpochDay().toLong()
        val rows = database.transactionDao()
            .observeCategoryTotals(start, end, TransactionType.EXPENSE)
            .first()
        assertEquals(2, rows.size)
        assertEquals(foodId, rows[0].categoryId)
        assertEquals(4000L, rows[0].totalMinor)
        assertEquals(2, rows[0].count)
        assertEquals(transportId, rows[1].categoryId)
    }

    @Test
    fun `daily trend ignores transfer`() = runTest {
        seed()
        insertTransaction(10000, TransactionType.INCOME, LocalDate.of(2026, 10, 1), null, cashId)
        insertTransaction(3000, TransactionType.EXPENSE, LocalDate.of(2026, 10, 2), foodId, cashId)
        insertTransaction(5000, TransactionType.TRANSFER, LocalDate.of(2026, 10, 3), null, cashId, toAccountId = bankId)

        val rows = database.transactionDao().observeDailyTrend(
            LocalDate.of(2026, 10, 1).toEpochDay().toLong(),
            LocalDate.of(2026, 10, 31).toEpochDay().toLong(),
            TransactionType.INCOME,
            TransactionType.EXPENSE,
        ).first()
        assertEquals(2, rows.size)
        assertEquals(10000L, rows[0].incomeMinor)
        assertEquals(0L, rows[0].expenseMinor)
        assertEquals(0L, rows[1].incomeMinor)
        assertEquals(3000L, rows[1].expenseMinor)
        // 转账日不出现在趋势中（既无收入也无支出）
        assertTrue(rows.none { it.epochDay == LocalDate.of(2026, 10, 3).toEpochDay().toLong() })
    }
}
