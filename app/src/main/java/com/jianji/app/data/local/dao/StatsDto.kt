package com.jianji.app.data.local.dao

import androidx.room.Embedded
import com.jianji.app.data.local.entity.AccountEntity
import com.jianji.app.domain.model.TransactionType

/** 按交易类型聚合的总额与笔数（TRANSFER 会出现在结果中，由调用方决定是否采用）。 */
data class TypeTotal(
    val type: TransactionType,
    val totalMinor: Long,
    val count: Int,
)

/** 按分类聚合的总额（用于排行与预算消耗）。 */
data class CategoryTotalRow(
    val categoryId: Long?,
    val totalMinor: Long,
    val count: Int,
)

/** 按天聚合的收支趋势行。 */
data class DailyTrendRow(
    val epochDay: Long,
    val incomeMinor: Long,
    val expenseMinor: Long,
)

/** 按自然月聚合的收支趋势行（年度视图）。 */
data class MonthlyTrendRow(
    val yearMonth: String,
    val incomeMinor: Long,
    val expenseMinor: Long,
)

/**
 * 账户及其实时余额（一条 SQL 聚合算出，避免 N 次单账户查询导致的不一致）。
 */
data class AccountWithBalanceRow(
    @Embedded
    val account: AccountEntity,
    val balanceMinor: Long,
)
