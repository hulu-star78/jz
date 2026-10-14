package com.jianji.app.ui.model

import com.jianji.app.domain.model.Account
import com.jianji.app.domain.model.Category
import com.jianji.app.domain.model.Money
import com.jianji.app.domain.model.Transaction
import com.jianji.app.domain.model.TransactionType

/** 账单列表项：交易 + 展示所需的分类/账户信息（由 ViewModel 组装）。 */
data class TransactionItem(
    val transaction: Transaction,
    val categoryName: String?,
    val categoryIcon: String,
    val accountName: String,
    val toAccountName: String?,
)

/** 分类占比项（用于首页概览与统计环形图图例）。 */
data class CategoryShare(
    val categoryId: Long?,
    val name: String,
    val icon: String,
    val amount: Money,
    val fraction: Float,
    val count: Int,
)

/** 按日分组的账单。 */
data class DayGroup(
    val date: java.time.LocalDate,
    val title: String,
    val items: List<TransactionItem>,
    val expense: Money,
    val income: Money,
)

/** Transaction → 列表项（分类/账户名称组装）。 */
fun Transaction.toItem(
    categoryById: Map<Long, Category>,
    accountById: Map<Long, Account>,
): TransactionItem {
    val category = categoryId?.let { categoryById[it] }
    return TransactionItem(
        transaction = this,
        categoryName = category?.name ?: if (type == TransactionType.TRANSFER) "转账" else null,
        categoryIcon = category?.icon ?: "swap_horiz",
        accountName = accountById[accountId]?.name.orEmpty(),
        toAccountName = toAccountId?.let { accountById[it]?.name },
    )
}
