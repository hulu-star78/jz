package com.jianji.app.domain.model

import java.time.LocalDate

/**
 * 交易领域模型。
 *
 * 注意：TRANSFER 的 [toAccountId] 必填，且不计入收入/支出统计。
 */
data class Transaction(
    val id: Long,
    val amount: Money,
    val type: TransactionType,
    val date: LocalDate,
    val categoryId: Long?,
    val accountId: Long,
    val toAccountId: Long?,
    val note: String?,
)

/**
 * 保存交易的草稿模型（新增时 id 传 0）。
 *
 * [amountMinor] 直接使用「分」为单位的整数，由 UI 层通过 [Money.parse] 解析后传入，
 * 全链路不出现 Double / Float。
 */
data class TransactionDraft(
    val id: Long = 0L,
    val amountMinor: Long,
    val type: TransactionType,
    val date: LocalDate,
    val categoryId: Long? = null,
    val accountId: Long,
    val toAccountId: Long? = null,
    val note: String? = null,
)

/** 账单列表筛选条件（null 表示不限）。 */
data class TransactionFilter(
    val start: LocalDate? = null,
    val end: LocalDate? = null,
    val type: TransactionType? = null,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val keyword: String? = null,
)
