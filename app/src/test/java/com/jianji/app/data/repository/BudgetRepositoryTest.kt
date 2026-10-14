package com.jianji.app.data.repository

import com.jianji.app.domain.model.CategoryType
import com.jianji.app.domain.model.Money
import com.jianji.app.domain.model.TransactionDraft
import com.jianji.app.domain.model.TransactionType
import com.jianji.app.domain.validation.ResourceValidationException
import com.jianji.app.testutil.DatabaseTestSupport
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * 预算仓库测试：总预算/分类预算设置、唯一性、实时使用进度。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BudgetRepositoryTest : DatabaseTestSupport() {

    private lateinit var repository: BudgetRepositoryImpl
    private lateinit var transactionRepository: TransactionRepositoryImpl
    private var foodId: Long = 0L
    private var transportId: Long = 0L
    private var cashId: Long = 0L

    private suspend fun seed() {
        foodId = insertCategory("餐饮", CategoryType.EXPENSE)
        transportId = insertCategory("交通", CategoryType.EXPENSE)
        cashId = insertAccount("现金")
        transactionRepository = TransactionRepositoryImpl(
            database = database,
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            accountDao = database.accountDao(),
        )
        repository = BudgetRepositoryImpl(database, database.budgetDao(), database.transactionDao())
    }

    @Test
    fun `total budget progress uses all expenses`() = runTest {
        seed()
        repository.setTotalBudget("2026-10", 100000)
        transactionRepository.save(
            TransactionDraft(
                amountMinor = 30000,
                type = TransactionType.EXPENSE,
                date = LocalDate.of(2026, 10, 2),
                categoryId = foodId,
                accountId = cashId,
            ),
        )
        transactionRepository.save(
            TransactionDraft(
                amountMinor = 20000,
                type = TransactionType.EXPENSE,
                date = LocalDate.of(2026, 10, 3),
                categoryId = transportId,
                accountId = cashId,
            ),
        )

        val progress = repository.observeMonthProgress("2026-10").first()
        assertEquals(1, progress.size)
        val total = progress.first()
        assertEquals(Money.fromMinor(100000), total.budget.limit)
        assertEquals(Money.fromMinor(50000), total.used)
        assertEquals(Money.fromMinor(50000), total.remaining)
        assertFalse(total.isExceeded)
        assertEquals(500, total.usedPermille)
    }

    @Test
    fun `category budget progress uses matching category only`() = runTest {
        seed()
        repository.setCategoryBudget("2026-10", foodId, 40000)
        transactionRepository.save(
            TransactionDraft(
                amountMinor = 30000,
                type = TransactionType.EXPENSE,
                date = LocalDate.of(2026, 10, 2),
                categoryId = foodId,
                accountId = cashId,
            ),
        )
        transactionRepository.save(
            TransactionDraft(
                amountMinor = 20000,
                type = TransactionType.EXPENSE,
                date = LocalDate.of(2026, 10, 3),
                categoryId = transportId,
                accountId = cashId,
            ),
        )

        val progress = repository.observeMonthProgress("2026-10").first()
        assertEquals(1, progress.size)
        assertEquals(Money.fromMinor(30000), progress.first().used)
    }

    @Test
    fun `setting same budget twice keeps single row and updates limit`() = runTest {
        seed()
        val firstId = repository.setTotalBudget("2026-10", 100000)
        val secondId = repository.setTotalBudget("2026-10", 200000)

        assertEquals(firstId, secondId)
        val budgets = repository.observeMonthBudgets("2026-10").first()
        assertEquals(1, budgets.size)
        assertEquals(Money.fromMinor(200000), budgets.first().limit)
    }

    @Test
    fun `total and category budgets coexist`() = runTest {
        seed()
        repository.setTotalBudget("2026-10", 100000)
        repository.setCategoryBudget("2026-10", foodId, 40000)

        val budgets = repository.observeMonthBudgets("2026-10").first()
        assertEquals(2, budgets.size)
        assertTrue(budgets.any { it.categoryId == null })
        assertTrue(budgets.any { it.categoryId == foodId })
    }

    @Test
    fun `progress reflects exceeded budget`() = runTest {
        seed()
        repository.setCategoryBudget("2026-10", foodId, 20000)
        transactionRepository.save(
            TransactionDraft(
                amountMinor = 25000,
                type = TransactionType.EXPENSE,
                date = LocalDate.of(2026, 10, 2),
                categoryId = foodId,
                accountId = cashId,
            ),
        )
        val progress = repository.observeMonthProgress("2026-10").first().first()
        assertTrue(progress.isExceeded)
        assertEquals(1000, progress.usedPermille)
        assertTrue(progress.remaining.isNegative)
    }

    @Test
    fun `invalid budget inputs rejected`() = runTest {
        seed()
        assertThrowsFor { repository.setTotalBudget("2026-10", 0) }
        assertThrowsFor { repository.setTotalBudget("2026-10", -1) }
        assertThrowsFor { repository.setTotalBudget("2026-13", 100) }
        assertThrowsFor { repository.setCategoryBudget("2026-10", foodId, 0) }
    }

    @Test
    fun `delete budget removes it`() = runTest {
        seed()
        val id = repository.setTotalBudget("2026-10", 100000)
        repository.deleteBudget(id)
        assertEquals(0, repository.observeMonthBudgets("2026-10").first().size)
    }

    private suspend fun assertThrowsFor(block: suspend () -> Unit) {
        val ex = runCatching { block() }.exceptionOrNull()
        assertTrue("expected ResourceValidationException but got $ex", ex is ResourceValidationException)
    }
}
