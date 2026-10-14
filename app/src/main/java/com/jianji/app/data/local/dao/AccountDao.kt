package com.jianji.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.jianji.app.data.local.entity.AccountEntity
import com.jianji.app.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

/**
 * 账户 DAO。
 *
 * 余额不落库：由交易数据实时聚合（见 [observeBalance]、[observeAccountsWithBalance]）。
 * 余额公式：初始余额 + 收入 - 支出 - 转出 + 转入。
 * （转账不属于收入/支出，但会减少源账户、增加目标账户资金。）
 */
@Dao
interface AccountDao {

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity): Int

    @Query("UPDATE accounts SET isArchived = 1 WHERE id = :id")
    suspend fun archiveById(id: Long): Int

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun findById(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts ORDER BY isArchived, createdAt, id")
    fun observeAll(): Flow<List<AccountEntity>>

    /** 一次性快照（导出备份用）。 */
    @Query("SELECT * FROM accounts ORDER BY isArchived, createdAt, id")
    suspend fun getAllSnapshot(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE isArchived = 0 ORDER BY createdAt, id")
    fun observeActive(): Flow<List<AccountEntity>>

    @Query("SELECT COUNT(*) FROM accounts WHERE isArchived = 0")
    suspend fun countActive(): Int

    /** 单账户实时余额；账户不存在时返回 null。 */
    @Query(
        "SELECT " +
            "a.initialBalanceMinor " +
            "+ COALESCE((SELECT SUM(amountMinor) FROM transactions WHERE accountId = a.id AND type = :incomeType), 0) " +
            "- COALESCE((SELECT SUM(amountMinor) FROM transactions WHERE accountId = a.id AND type = :expenseType), 0) " +
            "- COALESCE((SELECT SUM(amountMinor) FROM transactions WHERE accountId = a.id AND type = :transferType), 0) " +
            "+ COALESCE((SELECT SUM(amountMinor) FROM transactions WHERE toAccountId = a.id AND type = :transferType), 0) " +
            "FROM accounts a WHERE a.id = :accountId"
    )
    fun observeBalance(
        accountId: Long,
        incomeType: TransactionType,
        expenseType: TransactionType,
        transferType: TransactionType,
    ): Flow<Long?>

    /** 全部账户及其实时余额（一条 SQL 聚合，保证一致性）。 */
    @Query(
        "SELECT a.*, " +
            "a.initialBalanceMinor " +
            "+ COALESCE((SELECT SUM(amountMinor) FROM transactions WHERE accountId = a.id AND type = :incomeType), 0) " +
            "- COALESCE((SELECT SUM(amountMinor) FROM transactions WHERE accountId = a.id AND type = :expenseType), 0) " +
            "- COALESCE((SELECT SUM(amountMinor) FROM transactions WHERE accountId = a.id AND type = :transferType), 0) " +
            "+ COALESCE((SELECT SUM(amountMinor) FROM transactions WHERE toAccountId = a.id AND type = :transferType), 0) " +
            "AS balanceMinor " +
            "FROM accounts a " +
            "ORDER BY a.isArchived, a.createdAt, a.id"
    )
    fun observeAccountsWithBalance(
        incomeType: TransactionType,
        expenseType: TransactionType,
        transferType: TransactionType,
    ): Flow<List<AccountWithBalanceRow>>
}
