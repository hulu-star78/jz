package com.jianji.app.data.repository

import com.jianji.app.domain.model.CategoryType
import com.jianji.app.domain.model.Money
import com.jianji.app.domain.model.TransactionDraft
import com.jianji.app.domain.model.TransactionFilter
import com.jianji.app.domain.model.TransactionType
import com.jianji.app.domain.validation.TransactionValidationException
import com.jianji.app.domain.validation.ValidationError
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
 * 交易仓库测试：保存前校验、事务内存在性校验、更新与删除。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TransactionRepositoryTest : DatabaseTestSupport() {

    private lateinit var repository: TransactionRepositoryImpl
    private var foodId: Long = 0L
    private var cashId: Long = 0L
    private var bankId: Long = 0L

    private suspend fun seed() {
        foodId = insertCategory("餐饮", CategoryType.EXPENSE)
        cashId = insertAccount("现金")
        bankId = insertAccount("银行卡")
        repository = TransactionRepositoryImpl(
            database = database,
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            accountDao = database.accountDao(),
        )
    }

    private fun expenseDraft(
        amountMinor: Long = 3500,
        date: LocalDate = LocalDate.of(2026, 10, 13),
        categoryId: Long? = foodId,
        accountId: Long = cashId,
        note: String? = null,
    ) = TransactionDraft(
        amountMinor = amountMinor,
        type = TransactionType.EXPENSE,
        date = date,
        categoryId = categoryId,
        accountId = accountId,
        note = note,
    )

    @Test
    fun `save inserts new transaction`() = runTest {
        seed()
        val id = repository.save(expenseDraft(note = "午饭"))
        val saved = repository.findById(id)
        assertNotNull(saved)
        assertEquals(Money.fromMinor(3500), saved!!.amount)
        assertEquals(TransactionType.EXPENSE, saved.type)
        assertEquals(foodId, saved.categoryId)
        assertEquals(cashId, saved.accountId)
        assertEquals("午饭", saved.note)
    }

    @Test
    fun `save updates existing transaction`() = runTest {
        seed()
        val id = repository.save(expenseDraft())

        repository.save(expenseDraft(amountMinor = 4000).copy(id = id, note = "改价"))
        val updated = repository.findById(id)!!
        assertEquals(id, updated.id)
        assertEquals(Money.fromMinor(4000), updated.amount)
        assertEquals("改价", updated.note)
        // 更新而非新增：全库仍只有一条记录
        assertEquals(1, repository.observeAll().first().size)
    }

    @Test
    fun `save rejects invalid amount and date`() = runTest {
        seed()
        assertValidationError(ValidationError.AMOUNT_NOT_POSITIVE) {
            repository.save(expenseDraft(amountMinor = 0))
        }
        assertValidationError(ValidationError.DATE_OUT_OF_RANGE) {
            repository.save(expenseDraft(date = LocalDate.of(1960, 1, 1)))
        }
    }

    @Test
    fun `save rejects missing or archived category`() = runTest {
        seed()
        assertValidationError(ValidationError.CATEGORY_REQUIRED) {
            repository.save(expenseDraft(categoryId = null))
        }
        assertValidationError(ValidationError.CATEGORY_REQUIRED) {
            repository.save(expenseDraft(categoryId = 99999L))
        }
        database.categoryDao().archiveById(foodId)
        assertValidationError(ValidationError.CATEGORY_REQUIRED) {
            repository.save(expenseDraft())
        }
    }

    @Test
    fun `save rejects missing or archived account`() = runTest {
        seed()
        assertValidationError(ValidationError.ACCOUNT_REQUIRED) {
            repository.save(expenseDraft(accountId = 99999L))
        }
        database.accountDao().archiveById(cashId)
        assertValidationError(ValidationError.ACCOUNT_REQUIRED) {
            repository.save(expenseDraft())
        }
    }

    @Test
    fun `transfer rules enforced`() = runTest {
        seed()
        // 合法转账
        val id = repository.save(
            TransactionDraft(
                amountMinor = 10000,
                type = TransactionType.TRANSFER,
                date = LocalDate.of(2026, 10, 13),
                accountId = cashId,
                toAccountId = bankId,
            ),
        )
        val saved = repository.findById(id)!!
        assertNull(saved.categoryId)
        assertEquals(bankId, saved.toAccountId)

        assertValidationError(ValidationError.TRANSFER_TARGET_REQUIRED) {
            repository.save(
                TransactionDraft(
                    amountMinor = 10000,
                    type = TransactionType.TRANSFER,
                    date = LocalDate.of(2026, 10, 13),
                    accountId = cashId,
                    toAccountId = null,
                ),
            )
        }
        assertValidationError(ValidationError.TRANSFER_TARGET_SAME_ACCOUNT) {
            repository.save(
                TransactionDraft(
                    amountMinor = 10000,
                    type = TransactionType.TRANSFER,
                    date = LocalDate.of(2026, 10, 13),
                    accountId = cashId,
                    toAccountId = cashId,
                ),
            )
        }
    }

    @Test
    fun `editing missing transaction rejected`() = runTest {
        seed()
        assertValidationError(ValidationError.TRANSACTION_NOT_FOUND) {
            repository.save(expenseDraft().copy(id = 99999L))
        }
    }

    @Test
    fun `delete removes transaction`() = runTest {
        seed()
        val id = repository.save(expenseDraft())
        repository.delete(id)
        assertNull(repository.findById(id))
        assertEquals(0, repository.observeAll().first().size)
    }

    @Test
    fun `observe filtered works through repository`() = runTest {
        seed()
        repository.save(expenseDraft(note = "午饭"))
        repository.save(expenseDraft(amountMinor = 2000, note = "晚饭"))

        val byKeyword = repository.observeFiltered(TransactionFilter(keyword = "午饭")).first()
        assertEquals(1, byKeyword.size)
        val byMonth = repository.observeFiltered(
            TransactionFilter(start = LocalDate.of(2026, 10, 1), end = LocalDate.of(2026, 10, 31)),
        ).first()
        assertEquals(2, byMonth.size)
        val outsideMonth = repository.observeFiltered(
            TransactionFilter(start = LocalDate.of(2026, 11, 1), end = LocalDate.of(2026, 11, 30)),
        ).first()
        assertEquals(0, outsideMonth.size)
    }

    private suspend fun assertValidationError(expected: ValidationError, block: suspend () -> Unit) {
        val ex = runCatching { block() }.exceptionOrNull()
        assertTrue("expected TransactionValidationException($expected) but got $ex", ex is TransactionValidationException)
        assertEquals(expected, (ex as TransactionValidationException).error)
    }
}
