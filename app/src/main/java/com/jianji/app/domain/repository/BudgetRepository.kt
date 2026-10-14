package com.jianji.app.domain.repository

import com.jianji.app.domain.model.Budget
import com.jianji.app.domain.model.BudgetProgress
import kotlinx.coroutines.flow.Flow

/**
 * 预算仓库：预算周期为自然月，以 yearMonth（"YYYY-MM"）为键。
 *
 * categoryId 为 null 的预算为月总预算；预算使用情况由支出交易实时聚合。
 */
interface BudgetRepository {

    /** 观察某月全部预算（总预算 + 各分类预算）。 */
    fun observeMonthBudgets(yearMonth: String): Flow<List<Budget>>

    /** 观察某月预算执行进度（按 categoryId 排序，总预算在前）。 */
    fun observeMonthProgress(yearMonth: String): Flow<List<BudgetProgress>>

    /** 设置月总预算（已存在则更新）。 */
    suspend fun setTotalBudget(yearMonth: String, limitMinor: Long): Long

    /** 设置某分类的月度预算（已存在则更新）。 */
    suspend fun setCategoryBudget(yearMonth: String, categoryId: Long, limitMinor: Long): Long

    /** 删除一条预算。 */
    suspend fun deleteBudget(id: Long)
}
