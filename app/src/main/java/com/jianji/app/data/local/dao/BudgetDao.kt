package com.jianji.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.jianji.app.data.local.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

/**
 * 预算 DAO。
 *
 * 注意：SQLite 唯一索引不约束 NULL，因此「每月一条总预算」的唯一性
 * 由 BudgetRepository 在事务内保证（先查后写）。
 */
@Dao
interface BudgetDao {

    @Insert
    suspend fun insert(budget: BudgetEntity): Long

    @Update
    suspend fun update(budget: BudgetEntity): Int

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun findById(id: Long): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE yearMonth = :yearMonth ORDER BY categoryId")
    fun observeByMonth(yearMonth: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE yearMonth = :yearMonth AND categoryId IS NULL LIMIT 1")
    fun observeTotalBudget(yearMonth: String): Flow<BudgetEntity?>

    @Query("SELECT * FROM budgets WHERE yearMonth = :yearMonth AND categoryId = :categoryId LIMIT 1")
    fun observeCategoryBudget(yearMonth: String, categoryId: Long): Flow<BudgetEntity?>

    /** 事务内查找总预算（yearMonth + categoryId IS NULL 唯一性校验用）。 */
    @Query("SELECT * FROM budgets WHERE yearMonth = :yearMonth AND categoryId IS NULL LIMIT 1")
    suspend fun findTotalByMonth(yearMonth: String): BudgetEntity?

    /** 事务内查找分类预算。 */
    @Query("SELECT * FROM budgets WHERE yearMonth = :yearMonth AND categoryId = :categoryId LIMIT 1")
    suspend fun findCategoryBudget(yearMonth: String, categoryId: Long): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE yearMonth = :yearMonth")
    suspend fun findByMonth(yearMonth: String): List<BudgetEntity>

    /** 一次性快照（导出备份用）。 */
    @Query("SELECT * FROM budgets ORDER BY yearMonth, categoryId")
    suspend fun getAllSnapshot(): List<BudgetEntity>
}
