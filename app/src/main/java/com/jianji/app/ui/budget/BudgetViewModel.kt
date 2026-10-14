package com.jianji.app.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jianji.app.domain.model.BudgetProgress
import com.jianji.app.domain.model.Category
import com.jianji.app.domain.model.CategoryType
import com.jianji.app.domain.model.Money
import java.time.YearMonth
import com.jianji.app.domain.repository.BudgetRepository
import com.jianji.app.domain.repository.CategoryRepository
import com.jianji.app.domain.validation.ResourceValidationException
import com.jianji.app.ui.util.key
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BudgetUiState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val total: BudgetProgress? = null,
    val categoryBudgets: List<BudgetProgress> = emptyList(),
    val categories: List<Category> = emptyList(),
) {
    /** 尚未设置预算的支出分类（供新增分类预算选择）。 */
    val availableCategories: List<Category>
        get() = categories.filter { category ->
            categoryBudgets.none { it.budget.categoryId == category.id }
        }
}

class BudgetViewModel(
    private val budgetRepository: BudgetRepository,
    categoryRepository: CategoryRepository,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<BudgetUiState> = month
        .flatMapLatest { ym ->
            combine(
                budgetRepository.observeMonthProgress(ym.key()),
                categoryRepository.observeActiveByType(CategoryType.EXPENSE),
            ) { progress, categories ->
                BudgetUiState(
                    loading = false,
                    month = ym,
                    total = progress.firstOrNull { it.budget.categoryId == null },
                    categoryBudgets = progress.filter { it.budget.categoryId != null },
                    categories = categories,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetUiState())

    fun previousMonth() {
        month.value = month.value.minusMonths(1)
    }

    fun nextMonth() {
        val next = month.value.plusMonths(1)
        if (!next.isAfter(YearMonth.now())) month.value = next
    }

    /** 保存总预算；返回错误信息（null 表示成功）。 */
    suspend fun saveTotalBudget(input: String): String? {
        val money = Money.parse(input) ?: return "请输入有效金额"
        if (money.minor <= 0) return "预算金额必须大于 0"
        return try {
            budgetRepository.setTotalBudget(month.value.key(), money.minor)
            null
        } catch (e: ResourceValidationException) {
            e.error.name
        }
    }

    /** 保存分类预算；返回错误信息（null 表示成功）。 */
    suspend fun saveCategoryBudget(categoryId: Long, input: String): String? {
        val money = Money.parse(input) ?: return "请输入有效金额"
        if (money.minor <= 0) return "预算金额必须大于 0"
        return try {
            budgetRepository.setCategoryBudget(month.value.key(), categoryId, money.minor)
            null
        } catch (e: ResourceValidationException) {
            e.error.name
        }
    }

    fun deleteBudget(budgetId: Long) {
        viewModelScope.launch { budgetRepository.deleteBudget(budgetId) }
    }
}
