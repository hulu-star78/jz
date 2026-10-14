package com.jianji.app.data.repository

import com.jianji.app.data.local.dao.AccountDao
import com.jianji.app.data.local.entity.AccountEntity
import com.jianji.app.domain.model.Account
import com.jianji.app.domain.model.AccountType
import com.jianji.app.domain.model.AccountWithBalance
import com.jianji.app.domain.model.Money
import com.jianji.app.domain.model.Transaction
import com.jianji.app.domain.model.TransactionFilter
import com.jianji.app.domain.model.TransactionType
import com.jianji.app.domain.repository.AccountRepository
import com.jianji.app.domain.repository.TransactionRepository
import com.jianji.app.domain.validation.ResourceValidationException
import com.jianji.app.domain.validation.ResourceValidator
import com.jianji.app.domain.validation.ValidationError
import com.jianji.app.domain.validation.ValidationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 账户仓库实现。
 *
 * 余额实时计算（禁止缓存）：
 * 初始余额 + 收入 - 支出 - 转出 + 转入。
 */
class AccountRepositoryImpl(
    private val accountDao: AccountDao,
    private val transactionRepository: TransactionRepository,
) : AccountRepository {

    override fun observeAccounts(): Flow<List<Account>> =
        accountDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeActiveAccounts(): Flow<List<Account>> =
        accountDao.observeActive().map { list -> list.map { it.toDomain() } }

    override fun observeAccountsWithBalance(): Flow<List<AccountWithBalance>> =
        accountDao.observeAccountsWithBalance(
            incomeType = TransactionType.INCOME,
            expenseType = TransactionType.EXPENSE,
            transferType = TransactionType.TRANSFER,
        ).map { rows ->
            rows.map { row ->
                AccountWithBalance(
                    account = row.account.toDomain(),
                    balance = Money.fromMinor(row.balanceMinor),
                )
            }
        }

    override fun observeBalance(accountId: Long): Flow<Money> =
        accountDao.observeBalance(
            accountId = accountId,
            incomeType = TransactionType.INCOME,
            expenseType = TransactionType.EXPENSE,
            transferType = TransactionType.TRANSFER,
        ).map { Money.fromMinor(it ?: 0L) }

    override fun observeAccountTransactions(accountId: Long): Flow<List<Transaction>> =
        transactionRepository.observeFiltered(TransactionFilter(accountId = accountId))

    override suspend fun createAccount(
        name: String,
        icon: String,
        type: AccountType,
        initialBalanceMinor: Long,
        colorArgb: Int?,
    ): Long {
        when (val result = ResourceValidator.validateName(name, isCategory = false)) {
            is ValidationResult.Invalid -> throw ResourceValidationException(result.error)
            ValidationResult.Valid -> Unit
        }
        return accountDao.insert(
            AccountEntity(
                name = name.trim(),
                icon = icon,
                type = type,
                initialBalanceMinor = initialBalanceMinor,
                isArchived = false,
                createdAt = System.currentTimeMillis(),
                colorArgb = colorArgb,
            ),
        )
    }

    override suspend fun updateAccount(account: Account) {
        when (val result = ResourceValidator.validateName(account.name, isCategory = false)) {
            is ValidationResult.Invalid -> throw ResourceValidationException(result.error)
            ValidationResult.Valid -> Unit
        }
        accountDao.update(
            AccountEntity(
                id = account.id,
                name = account.name.trim(),
                icon = account.icon,
                type = account.type,
                initialBalanceMinor = account.initialBalance.minor,
                isArchived = account.isArchived,
                createdAt = account.createdAt,
                colorArgb = account.colorArgb,
            ),
        )
    }

    override suspend fun archiveAccount(id: Long) {
        val activeCount = accountDao.countActive()
        if (activeCount <= 1) {
            throw ResourceValidationException(ValidationError.ACCOUNT_ARCHIVE_LAST)
        }
        accountDao.archiveById(id)
    }
}
