package com.jianji.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jianji.app.domain.model.Money
import java.time.YearMonth
import com.jianji.app.domain.repository.AccountRepository
import com.jianji.app.domain.repository.CategoryRepository
import com.jianji.app.domain.repository.StatisticsRepository
import com.jianji.app.domain.repository.TransactionRepository
import com.jianji.app.ui.model.CategoryShare
import com.jianji.app.ui.model.TransactionItem
import com.jianji.app.ui.model.toItem
import com.jianji.app.ui.util.key
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val income: Money = Money.ZERO,
    val expense: Money = Money.ZERO,
    val balance: Money = Money.ZERO,
    val recent: List<TransactionItem> = emptyList(),
    val categoryShares: List<CategoryShare> = emptyList(),
)

class HomeViewModel(
    transactionRepository: TransactionRepository,
    statisticsRepository: StatisticsRepository,
    categoryRepository: CategoryRepository,
    accountRepository: AccountRepository,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<HomeUiState> = month
        .flatMapLatest { ym ->
            combine(
                statisticsRepository.observeMonthlySummary(ym.key()),
                transactionRepository.observeByDateRange(ym.atDay(1), ym.atEndOfMonth()),
                statisticsRepository.observeExpenseRanking(ym.key(), limit = 5),
                categoryRepository.observeAll(),
                accountRepository.observeAccounts(),
            ) { summary, transactions, ranking, categories, accounts ->
                val categoryById = categories.associateBy { it.id }
                val accountById = accounts.associateBy { it.id }
                val items = transactions.map { it.toItem(categoryById, accountById) }
                val shares = ranking.map { row ->
                    CategoryShare(
                        categoryId = row.categoryId,
                        name = row.categoryName,
                        icon = categoryById[row.categoryId]?.icon ?: "more_horiz",
                        amount = row.amount,
                        fraction = if (summary.expense.minor > 0) {
                            (row.amount.minor.toDouble() / summary.expense.minor.toDouble()).toFloat()
                        } else {
                            0f
                        },
                        count = row.count,
                    )
                }
                HomeUiState(
                    loading = false,
                    month = ym,
                    income = summary.income,
                    expense = summary.expense,
                    balance = summary.balance,
                    recent = items.take(5),
                    categoryShares = shares,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun previousMonth() {
        month.value = month.value.minusMonths(1)
    }

    fun nextMonth() {
        val next = month.value.plusMonths(1)
        if (!next.isAfter(YearMonth.now())) month.value = next
    }
}
