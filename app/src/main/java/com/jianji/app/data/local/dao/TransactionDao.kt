package com.jianji.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.jianji.app.data.local.entity.TransactionEntity
import com.jianji.app.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

/**
 * 交易 DAO。
 *
 * 约定：
 * - 所有查询返回 Flow，数据变化自动刷新；
 * - 统计全部由 SQL 聚合完成，禁止取回全量数据在 Kotlin 中计算；
 * - SQL 中不出现枚举字面量，类型一律通过参数绑定（配合 Converters），
 *   避免重命名枚举时 SQL 与数据不一致。
 */
@Dao
interface TransactionDao {

    // ---------- 写操作 ----------

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity): Int

    @Delete
    suspend fun delete(transaction: TransactionEntity): Int

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun findById(id: Long): TransactionEntity?

    // ---------- 基础查询 ----------

    @Query(
        "SELECT * FROM transactions " +
            "ORDER BY transactionDate DESC, createdAt DESC, id DESC"
    )
    fun observeAll(): Flow<List<TransactionEntity>>

    /** 一次性快照（导出备份用）。 */
    @Query("SELECT * FROM transactions ORDER BY id")
    suspend fun getAllSnapshot(): List<TransactionEntity>

    @Query(
        "SELECT * FROM transactions " +
            "WHERE transactionDate BETWEEN :startEpochDay AND :endEpochDay " +
            "ORDER BY transactionDate DESC, createdAt DESC, id DESC"
    )
    fun observeByDateRange(startEpochDay: Long, endEpochDay: Long): Flow<List<TransactionEntity>>

    @Query(
        "SELECT * FROM transactions WHERE categoryId = :categoryId " +
            "ORDER BY transactionDate DESC, createdAt DESC, id DESC"
    )
    fun observeByCategory(categoryId: Long): Flow<List<TransactionEntity>>

    /** 账户流水：含作为来源或目标的转账。 */
    @Query(
        "SELECT * FROM transactions " +
            "WHERE accountId = :accountId OR toAccountId = :accountId " +
            "ORDER BY transactionDate DESC, createdAt DESC, id DESC"
    )
    fun observeByAccount(accountId: Long): Flow<List<TransactionEntity>>

    /**
     * 组合筛选查询（null 参数表示不限该项）。
     * 关键词对备注做模糊匹配。
     */
    @Query(
        "SELECT * FROM transactions " +
            "WHERE (:startEpochDay IS NULL OR transactionDate >= :startEpochDay) " +
            "AND (:endEpochDay IS NULL OR transactionDate <= :endEpochDay) " +
            "AND (:type IS NULL OR type = :type) " +
            "AND (:categoryId IS NULL OR categoryId = :categoryId) " +
            "AND (:accountId IS NULL OR accountId = :accountId OR toAccountId = :accountId) " +
            "AND (:keyword IS NULL OR note LIKE '%' || :keyword || '%') " +
            "ORDER BY transactionDate DESC, createdAt DESC, id DESC"
    )
    fun observeFiltered(
        startEpochDay: Long?,
        endEpochDay: Long?,
        type: TransactionType?,
        categoryId: Long?,
        accountId: Long?,
        keyword: String?,
    ): Flow<List<TransactionEntity>>

    // ---------- 统计（SQL 聚合，TRANSFER 不参与收入/支出统计） ----------

    /** 按类型聚合区间内总额；TRANSFER 行由调用方忽略。 */
    @Query(
        "SELECT type AS type, COALESCE(SUM(amountMinor), 0) AS totalMinor, COUNT(*) AS count " +
            "FROM transactions " +
            "WHERE transactionDate BETWEEN :startEpochDay AND :endEpochDay " +
            "GROUP BY type"
    )
    fun observeTotalsByType(startEpochDay: Long, endEpochDay: Long): Flow<List<TypeTotal>>

    /** 区间内指定类型（INCOME 或 EXPENSE）的总额。 */
    @Query(
        "SELECT COALESCE(SUM(amountMinor), 0) FROM transactions " +
            "WHERE transactionDate BETWEEN :startEpochDay AND :endEpochDay AND type = :type"
    )
    fun observeTotalByType(startEpochDay: Long, endEpochDay: Long, type: TransactionType): Flow<Long>

    /** 分类支出/收入排行（降序）。 */
    @Query(
        "SELECT categoryId AS categoryId, COALESCE(SUM(amountMinor), 0) AS totalMinor, COUNT(*) AS count " +
            "FROM transactions " +
            "WHERE transactionDate BETWEEN :startEpochDay AND :endEpochDay AND type = :type " +
            "GROUP BY categoryId " +
            "ORDER BY totalMinor DESC"
    )
    fun observeCategoryTotals(
        startEpochDay: Long,
        endEpochDay: Long,
        type: TransactionType,
    ): Flow<List<CategoryTotalRow>>

    /**
     * 按天聚合的收支趋势。
     * 仅统计 INCOME/EXPENSE（CASE 只累加匹配类型，TRANSFER 两列均为 0，
     * 因此转账日被 HAVING 过滤，不会产生零值数据点）。
     */
    @Query(
        "SELECT transactionDate AS epochDay, " +
            "COALESCE(SUM(CASE WHEN type = :incomeType THEN amountMinor END), 0) AS incomeMinor, " +
            "COALESCE(SUM(CASE WHEN type = :expenseType THEN amountMinor END), 0) AS expenseMinor " +
            "FROM transactions " +
            "WHERE transactionDate BETWEEN :startEpochDay AND :endEpochDay " +
            "GROUP BY transactionDate " +
            "HAVING incomeMinor > 0 OR expenseMinor > 0 " +
            "ORDER BY transactionDate ASC"
    )
    fun observeDailyTrend(
        startEpochDay: Long,
        endEpochDay: Long,
        incomeType: TransactionType,
        expenseType: TransactionType,
    ): Flow<List<DailyTrendRow>>

    /**
     * 按自然月聚合的收支趋势（用于年度视图）。
     * epochDay 经 SQLite date() 换算为 "YYYY-MM"；转账两列均为 0，被 HAVING 过滤。
     */
    @Query(
        "SELECT strftime('%Y-%m', transactionDate * 86400, 'unixepoch') AS yearMonth, " +
            "COALESCE(SUM(CASE WHEN type = :incomeType THEN amountMinor END), 0) AS incomeMinor, " +
            "COALESCE(SUM(CASE WHEN type = :expenseType THEN amountMinor END), 0) AS expenseMinor " +
            "FROM transactions " +
            "WHERE transactionDate BETWEEN :startEpochDay AND :endEpochDay " +
            "GROUP BY yearMonth " +
            "HAVING incomeMinor > 0 OR expenseMinor > 0 " +
            "ORDER BY yearMonth ASC"
    )
    fun observeMonthlyTrend(
        startEpochDay: Long,
        endEpochDay: Long,
        incomeType: TransactionType,
        expenseType: TransactionType,
    ): Flow<List<MonthlyTrendRow>>
}
