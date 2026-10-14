package com.jianji.app.data.repository

import com.jianji.app.domain.model.AccountType
import com.jianji.app.domain.model.CategoryType
import com.jianji.app.domain.model.Money
import com.jianji.app.domain.model.TransactionDraft
import com.jianji.app.domain.model.TransactionType
import com.jianji.app.domain.validation.ResourceValidationException
import com.jianji.app.domain.validation.ValidationError
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
 * 账户仓库测试：余额公式（初始 + 收入 - 支出 - 转出 + 转入）、账户管理。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountRepositoryTest : DatabaseTestSupport() {

    private lateinit var repository: AccountRepositoryImpl
    private lateinit var transactionRepository: TransactionRepositoryImpl
    private var foodId: Long = 0L
    private var salaryId: Long = 0L
    private var cashId: Long = 0L
    private var bankId: Long = 0L
    private var walletId: Long = 0L

    private suspend fun seed() {
        foodId = insertCategory("餐饮", CategoryType.EXPENSE)
        salaryId = insertCategory("工资", CategoryType.INCOME)
        cashId = insertAccount("现金", initialBalanceMinor = 100000) // 1000.00
        bankId = insertAccount("银行卡", initialBalanceMinor = 50000) // 500.00
        walletId = insertAccount("微信钱包", type = AccountType.E_WALLET, initialBalanceMinor = 0)
        transactionRepository = TransactionRepositoryImpl(
            database = database,
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            accountDao = database.accountDao(),
        )
        repository = AccountRepositoryImpl(database.accountDao(), transactionRepository)
    }

    @Test
    fun `balance equals initial plus income minus expense plus transfers`() = runTest {
        seed()

        // 收入 +500，支出 -200，转出 -100，转入 +300 => 1000 + 500 - 200 - 100 + 300 = 1500
        transactionRepository.save(
            TransactionDraft(
                amountMinor = 50000,
                type = TransactionType.INCOME,
                date = LocalDate.of(2026, 10, 1),
                categoryId = salaryId,
                accountId = cashId,
            ),
        )
        transactionRepository.save(
            TransactionDraft(
                amountMinor = 20000,
                type = TransactionType.EXPENSE,
                date = LocalDate.of(2026, 10, 2),
                categoryId = foodId,
                accountId = cashId,
            ),
        )
        transactionRepository.save(
            TransactionDraft(
                amountMinor = 10000,
                type = TransactionType.TRANSFER,
                date = LocalDate.of(2026, 10, 3),
                accountId = cashId,
                toAccountId = bankId,
            ),
        )
        transactionRepository.save(
            TransactionDraft(
                amountMinor = 30000,
                type = TransactionType.TRANSFER,
                date = LocalDate.of(2026, 10, 4),
                accountId = walletId,
                toAccountId = cashId,
            ),
        )

        assertEquals(Money.fromMinor(150000), repository.observeBalance(cashId).first())
        assertEquals(Money.fromMinor(60000), repository.observeBalance(bankId).first())
        // 微信钱包初始 0，转出 300 元 => -300.00（允许透支，语义由用户通过初始余额管理）
        assertEquals(Money.fromMinor(-30000), repository.observeBalance(walletId).first())
    }

    @Test
    fun `balance updates after delete without any cache`() = runTest {
        seed()
        val id = transactionRepository.save(
            TransactionDraft(
                amountMinor = 20000,
                type = TransactionType.EXPENSE,
                date = LocalDate.of(2026, 10, 2),
                categoryId = foodId,
                accountId = cashId,
            ),
        )
        assertEquals(Money.fromMinor(80000), repository.observeBalance(cashId).first())

        transactionRepository.delete(id)
        assertEquals(Money.fromMinor(100000), repository.observeBalance(cashId).first())
    }

    @Test
    fun `accounts with balance lists all accounts`() = runTest {
        seed()
        val accounts = repository.observeAccountsWithBalance().first()
        assertEquals(3, accounts.size)
        assertEquals(Money.fromMinor(100000), accounts.first { it.account.id == cashId }.balance)
        assertTrue(accounts.first { it.account.id == walletId }.balance.isZero)
    }

    @Test
    fun `account transactions include transfers in and out`() = runTest {
        seed()
        transactionRepository.save(
            TransactionDraft(
                amountMinor = 10000,
                type = TransactionType.TRANSFER,
                date = LocalDate.of(2026, 10, 3),
                accountId = cashId,
                toAccountId = bankId,
            ),
        )
        transactionRepository.save(
            TransactionDraft(
                amountMinor = 20000,
                type = TransactionType.TRANSFER,
                date = LocalDate.of(2026, 10, 4),
                accountId = walletId,
                toAccountId = cashId,
            ),
        )
        val cashFlow = repository.observeAccountTransactions(cashId).first()
        assertEquals(2, cashFlow.size)
        assertEquals(Money.fromMinor(20000), cashFlow[0].amount)
    }

    @Test
    fun `create and update account`() = runTest {
        seed()
        val id = repository.createAccount(
            name = "支付宝",
            icon = "test",
            type = AccountType.E_WALLET,
            initialBalanceMinor = 12345,
        )
        assertEquals(Money.fromMinor(12345), repository.observeBalance(id).first())

        val account = repository.observeAccounts().first().first { it.id == id }
        repository.updateAccount(account.copy(name = "支付宝2"))
        assertEquals(
            "支付宝2",
            repository.observeAccounts().first().first { it.id == id }.name,
        )
    }

    @Test
    fun `blank account name rejected`() = runTest {
        seed()
        val result = runCatching {
            repository.createAccount("  ", "test", AccountType.CASH, 0)
        }
        val ex = result.exceptionOrNull()
        assertTrue(ex is ResourceValidationException)
        assertEquals(ValidationError.ACCOUNT_NAME_INVALID, (ex as ResourceValidationException).error)
    }

    @Test
    fun `cannot archive last active account`() = runTest {
        val onlyId = insertAccount("唯一账户")
        transactionRepository = TransactionRepositoryImpl(
            database = database,
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            accountDao = database.accountDao(),
        )
        repository = AccountRepositoryImpl(database.accountDao(), transactionRepository)

        val result = runCatching { repository.archiveAccount(onlyId) }
        val ex = result.exceptionOrNull()
        assertTrue(ex is ResourceValidationException)
        assertEquals(ValidationError.ACCOUNT_ARCHIVE_LAST, (ex as ResourceValidationException).error)
    }
}
