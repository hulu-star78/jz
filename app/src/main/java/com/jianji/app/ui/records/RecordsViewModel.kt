package com.jianji.app.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jianji.app.domain.model.Category
import com.jianji.app.domain.model.Money
import com.jianji.app.domain.model.TransactionFilter
import com.jianji.app.domain.model.TransactionType
import java.time.YearMonth
import com.jianji.app.domain.repository.AccountRepository
import com.jianji.app.domain.repository.CategoryRepository
import com.jianji.app.domain.repository.TransactionRepository
import com.jianji.app.ui.model.DayGroup
import com.jianji.app.ui.model.toItem
import com.jianji.app.ui.util.groupTitle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class RecordsUiState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val keyword: String = "",
    val typeFilter: TransactionType? = null,
    val categoryId: Long? = null,
    val groups: List<DayGroup> = emptyList(),
    val categories: List<Category> = emptyList(),
    val monthExpense: Money = Money.ZERO,
    val monthIncome: Money = Money.ZERO,
)

class RecordsViewModel(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())
    private val keyword = MutableStateFlow("")
    private val typeFilter = MutableStateFlow<TransactionType?>(null)
    private val categoryFilter = MutableStateFlow<Long?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<RecordsUiState> =
        combine(month, keyword, typeFilter, categoryFilter) { ym, kw, type, categoryId ->
            FilterSnapshot(ym, kw, type, categoryId)
        }.flatMapLatest { snapshot ->
            val filter = TransactionFilter(
                start = snapshot.month.atDay(1),
                end = snapshot.month.atEndOfMonth(),
                type = snapshot.type,
                categoryId = snapshot.categoryId,
                keyword = snapshot.keyword.takeIf { it.isNotBlank() },
            )
            combine(
                transactionRepository.observeFiltered(filter),
                categoryRepository.observeAll(),
                accountRepository.observeAccounts(),
            ) { transactions, categories, accounts ->
                val categoryById = categories.associateBy { it.id }
                val accountById = accounts.associateBy { it.id }
                val items = transactions.map { it.toItem(categoryById, accountById) }
                val groups = items
                    .groupBy { it.transaction.date }
                    .toSortedMap(reverseOrder())
                    .map { (date, dayItems) ->
                        DayGroup(
                            date = date,
                            title = date.groupTitle(),
                            items = dayItems,
                            expense = dayItems
                                .filter { it.transaction.type == TransactionType.EXPENSE }
                                .fold(Money.ZERO) { acc, i -> acc + i.transaction.amount },
                            income = dayItems
                                .filter { it.transaction.type == TransactionType.INCOME }
                                .fold(Money.ZERO) { acc, i -> acc + i.transaction.amount },
                        )
                    }
                RecordsUiState(
                    loading = false,
                    month = snapshot.month,
                    keyword = snapshot.keyword,
                    typeFilter = snapshot.type,
                    categoryId = snapshot.categoryId,
                    groups = groups,
                    categories = categories,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordsUiState())

    private data class FilterSnapshot(
        val month: YearMonth,
        val keyword: String,
        val type: TransactionType?,
        val categoryId: Long?,
    )

    fun previousMonth() {
        month.value = month.value.minusMonths(1)
    }

    fun nextMonth() {
        val next = month.value.plusMonths(1)
        if (!next.isAfter(YearMonth.now())) month.value = next
    }

    fun onKeywordChange(value: String) {
        keyword.value = value
    }

    fun onTypeFilterChange(type: TransactionType?) {
        typeFilter.value = if (typeFilter.value == type) null else type
    }

    fun onCategoryFilterChange(categoryId: Long?) {
        categoryFilter.value = if (categoryFilter.value == categoryId) null else categoryId
    }

    private fun reverseOrder() = Comparator<LocalDate> { a, b -> b.compareTo(a) }
}
