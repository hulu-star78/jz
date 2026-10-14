package com.jianji.app.data.repository

import androidx.room.withTransaction
import com.jianji.app.data.local.JianjiDatabase
import com.jianji.app.data.local.dao.BudgetDao
import com.jianji.app.data.local.dao.TransactionDao
import com.jianji.app.data.local.entity.BudgetEntity
import com.jianji.app.domain.model.Budget
import com.jianji.app.domain.model.BudgetProgress
import com.jianji.app.domain.model.DateUtils
import com.jianji.app.domain.model.Money
import com.jianji.app.domain.model.TransactionType
import com.jianji.app.domain.repository.BudgetRepository
import com.jianji.app.domain.validation.ResourceValidationException
import com.jianji.app.domain.validation.ValidationError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * 预算仓库实现。
 *
 * 预算周期为自然月；使用情况由支出交易实时聚合。
 * 「每月一条总预算」由事务内先查后写保证唯一
 * （SQLite 唯一索引不约束 NULL，无法在 schema 层强制）。
 */
class BudgetRepositoryImpl(
    private val database: JianjiDatabase,
    private val budgetDao: BudgetDao,
    private val transactionDao: TransactionDao,
) : BudgetRepository {

    override fun observeMonthBudgets(yearMonth: String): Flow<List<Budget>> {
        requireValidYearMonth(yearMonth)
        return budgetDao.observeByMonth(yearMonth).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun observeMonthProgress(yearMonth: String): Flow<List<BudgetProgress>> {
        requireValidYearMonth(yearMonth)
        val range = DateUtils.monthRange(yearMonth)
        return combine(
            budgetDao.observeByMonth(yearMonth),
            transactionDao.observeCategoryTotals(range.first, range.last, TransactionType.EXPENSE),
        ) { budgets, usage ->
            val usageByCategory = usage.associate { it.categoryId to it.totalMinor }
            val totalUsedMinor = usage.sumOf { it.totalMinor }
            budgets.map { entity ->
                val usedMinor = if (entity.categoryId == null) {
                    totalUsedMinor
                } else {
                    usageByCategory[entity.categoryId] ?: 0L
                }
                BudgetProgress(
                    budget = entity.toDomain(),
                    used = Money.fromMinor(usedMinor),
                )
            }
        }
    }

    override suspend fun setTotalBudget(yearMonth: String, limitMinor: Long): Long =
        database.withTransaction {
            requireValidYearMonth(yearMonth)
            requirePositiveLimit(limitMinor)
            val existing = budgetDao.findTotalByMonth(yearMonth)
            if (existing == null) {
                budgetDao.insert(
                    BudgetEntity(yearMonth = yearMonth, categoryId = null, limitMinor = limitMinor),
                )
            } else {
                budgetDao.update(existing.copy(limitMinor = limitMinor))
                existing.id
            }
        }

    override suspend fun setCategoryBudget(
        yearMonth: String,
        categoryId: Long,
        limitMinor: Long,
    ): Long = database.withTransaction {
        requireValidYearMonth(yearMonth)
        requirePositiveLimit(limitMinor)
        val existing = budgetDao.findCategoryBudget(yearMonth, categoryId)
        if (existing == null) {
            budgetDao.insert(
                BudgetEntity(yearMonth = yearMonth, categoryId = categoryId, limitMinor = limitMinor),
            )
        } else {
            budgetDao.update(existing.copy(limitMinor = limitMinor))
            existing.id
        }
    }

    override suspend fun deleteBudget(id: Long) {
        budgetDao.deleteById(id)
    }

    private fun requireValidYearMonth(yearMonth: String) {
        if (DateUtils.parseYearMonth(yearMonth) == null) {
            throw ResourceValidationException(ValidationError.BUDGET_AMOUNT_NOT_POSITIVE)
        }
    }

    private fun requirePositiveLimit(limitMinor: Long) {
        if (limitMinor <= 0L) {
            throw ResourceValidationException(ValidationError.BUDGET_AMOUNT_NOT_POSITIVE)
        }
    }
}

internal fun BudgetEntity.toDomain(): Budget = Budget(
    id = id,
    yearMonth = yearMonth,
    categoryId = categoryId,
    limit = Money.fromMinor(limitMinor),
)
