package com.jianji.app.domain.repository

import com.jianji.app.domain.model.Account
import com.jianji.app.domain.model.AccountType
import com.jianji.app.domain.model.AccountWithBalance
import com.jianji.app.domain.model.Money
import com.jianji.app.domain.model.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * 账户仓库。
 *
 * 余额公式（实时计算，禁止缓存 currentBalance）：
 * 初始余额 + 收入 - 支出 - 转出 + 转入。
 * 其中转账不属于收入/支出，但会同时减少源账户、增加目标账户的资金。
 */
interface AccountRepository {

    /** 观察全部账户（含已归档）。 */
    fun observeAccounts(): Flow<List<Account>>

    /** 观察未归档账户。 */
    fun observeActiveAccounts(): Flow<List<Account>>

    /** 观察全部账户及其实时余额。 */
    fun observeAccountsWithBalance(): Flow<List<AccountWithBalance>>

    /** 观察单个账户的实时余额。 */
    fun observeBalance(accountId: Long): Flow<Money>

    /** 观察单个账户的流水（含作为源或目标的转账，按日期倒序）。 */
    fun observeAccountTransactions(accountId: Long): Flow<List<Transaction>>

    /** 新建账户，返回账户 id。 */
    suspend fun createAccount(
        name: String,
        icon: String,
        type: AccountType,
        initialBalanceMinor: Long,
        colorArgb: Int? = null,
    ): Long

    /** 更新账户资料（含初始余额修正）。 */
    suspend fun updateAccount(account: Account)

    /** 归档账户（软删除，保留历史账目）。不允许归档最后一个可用账户。 */
    suspend fun archiveAccount(id: Long)
}
