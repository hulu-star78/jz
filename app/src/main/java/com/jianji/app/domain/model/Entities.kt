package com.jianji.app.domain.model

/**
 * 分类领域模型（与数据库实体分离，便于演进）。
 */
data class Category(
    val id: Long,
    val name: String,
    val icon: String,
    val type: CategoryType,
    val isSystem: Boolean,
    val isArchived: Boolean,
    val sortOrder: Int,
    val colorArgb: Int?,
)

/** 账户领域模型。initialBalance 为建账时填入的初始余额。 */
data class Account(
    val id: Long,
    val name: String,
    val icon: String,
    val type: AccountType,
    val initialBalance: Money,
    val isArchived: Boolean,
    val createdAt: Long,
    val colorArgb: Int?,
)

/** 账户 + 实时余额（余额由交易数据实时计算，不落库缓存）。 */
data class AccountWithBalance(
    val account: Account,
    val balance: Money,
)

/** 预算领域模型。categoryId 为 null 表示月总预算。 */
data class Budget(
    val id: Long,
    val yearMonth: String,
    val categoryId: Long?,
    val limit: Money,
)

/**
 * 预算执行进度。
 *
 * [usedPermille] 为千分比整数进度（0..1000+），用于进度条展示，
 * 不引入浮点；[isExceeded] 表示已超支。
 */
data class BudgetProgress(
    val budget: Budget,
    val used: Money,
) {
    val remaining: Money get() = budget.limit - used

    val isExceeded: Boolean get() = used > budget.limit

    val usedPermille: Int
        get() = if (budget.limit.minor <= 0L) {
            if (used.minor > 0) Int.MAX_VALUE else 0
        } else if (used.minor >= budget.limit.minor) {
            1000
        } else {
            try {
                Math.multiplyExact(used.minor, 1000L) / budget.limit.minor
            } catch (e: ArithmeticException) {
                1000
            }.toInt()
        }
}
