package com.jianji.app.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jianji.app.domain.model.CategoryType
import com.jianji.app.domain.model.Money
import com.jianji.app.domain.repository.CategoryRepository
import com.jianji.app.domain.repository.StatisticsRepository
import com.jianji.app.ui.components.TrendBar
import com.jianji.app.ui.model.CategoryShare
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

enum class StatsPeriod(val label: String) {
    WEEK("周"),
    MONTH("月"),
    YEAR("年"),
}

data class StatsUiState(
    val loading: Boolean = true,
    val period: StatsPeriod = StatsPeriod.MONTH,
    /** 0 = 当前周期；1 = 上一个周期；以此类推。 */
    val periodOffset: Int = 0,
    /** 是否还能向前翻（offset > 0 时才能回到更近的周期）。 */
    val canGoNext: Boolean = false,
    val rangeLabel: String = "",
    val income: Money = Money.ZERO,
    val expense: Money = Money.ZERO,
    val balance: Money = Money.ZERO,
    val bars: List<TrendBar> = emptyList(),
    val expenseShares: List<CategoryShare> = emptyList(),
    val incomeShares: List<CategoryShare> = emptyList(),
) {
    val hasData: Boolean get() = !income.isZero || !expense.isZero
}

class StatisticsViewModel(
    private val statisticsRepository: StatisticsRepository,
    private val categoryRepository: CategoryRepository,
    private val today: LocalDate = LocalDate.now(),
) : ViewModel() {

    private val period = MutableStateFlow(StatsPeriod.MONTH)
    private val offset = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<StatsUiState> = combine(period, offset) { p, o -> p to o }
        .flatMapLatest { (currentPeriod, currentOffset) ->
            val range = rangeOf(currentPeriod, currentOffset)
            val trendFlow = if (currentPeriod == StatsPeriod.YEAR) {
                statisticsRepository.observeMonthlyRangeTrend(range.start, range.end)
            } else {
                statisticsRepository.observeRangeTrend(range.start, range.end)
            }
            combine(
                trendFlow,
                statisticsRepository.observeExpenseRanking(range.start, range.end, limit = 8),
                statisticsRepository.observeIncomeRanking(range.start, range.end, limit = 8),
                categoryRepository.observeAll(),
            ) { trend, expenseRanking, incomeRanking, categories ->
                val iconById = categories.associate { it.id to it.icon }
                val bars = trend.points.map { point ->
                    TrendBar(
                        label = barLabel(point.date, currentPeriod),
                        income = point.income,
                        expense = point.expense,
                    )
                }
                val toShare: (com.jianji.app.domain.model.CategoryExpense) -> CategoryShare = { row ->
                    CategoryShare(
                        categoryId = row.categoryId,
                        name = row.categoryName,
                        icon = row.categoryId?.let { iconById[it] } ?: "more_horiz",
                        amount = row.amount,
                        fraction = if (row.type == CategoryType.EXPENSE && trend.totalExpense.minor > 0) {
                            (row.amount.minor.toDouble() / trend.totalExpense.minor.toDouble()).toFloat()
                        } else if (trend.totalIncome.minor > 0) {
                            (row.amount.minor.toDouble() / trend.totalIncome.minor.toDouble()).toFloat()
                        } else {
                            0f
                        },
                        count = row.count,
                    )
                }
                StatsUiState(
                    loading = false,
                    period = currentPeriod,
                    periodOffset = currentOffset,
                    canGoNext = currentOffset > 0,
                    rangeLabel = range.label,
                    income = trend.totalIncome,
                    expense = trend.totalExpense,
                    balance = trend.totalIncome - trend.totalExpense,
                    bars = bars,
                    expenseShares = expenseRanking.map(toShare),
                    incomeShares = incomeRanking.map(toShare),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun onPeriodChange(value: StatsPeriod) {
        period.value = value
        // 切换周期类型时回到当前周期，避免停留在过深的偏移
        offset.value = 0
    }

    /** 向前翻（更早的周期）。 */
    fun previousPeriod() {
        offset.value = offset.value + 1
    }

    /** 向后翻（更近的周期），不允许超过当前周期。 */
    fun nextPeriod() {
        if (offset.value > 0) offset.value = offset.value - 1
    }

    private data class PeriodRange(
        val start: LocalDate,
        val end: LocalDate,
        val label: String,
    )

    private fun rangeOf(current: StatsPeriod, currentOffset: Int): PeriodRange = when (current) {
        StatsPeriod.WEEK -> {
            // 滑动 7 天窗口：offset=0 为最近 7 天（含今天），offset=1 为再往前 7 天
            val end = today.minusDays(7L * currentOffset)
            val start = end.minusDays(6)
            PeriodRange(start, end, formatRangeLabel(start, end))
        }

        StatsPeriod.MONTH -> {
            val ym = YearMonth.from(today).minusMonths(currentOffset.toLong())
            PeriodRange(ym.atDay(1), ym.atEndOfMonth(), "${ym.year}年${ym.monthValue}月")
        }

        StatsPeriod.YEAR -> {
            val year = today.year - currentOffset
            PeriodRange(
                LocalDate.of(year, 1, 1),
                LocalDate.of(year, 12, 31),
                "${year}年",
            )
        }
    }

    private fun formatRangeLabel(start: LocalDate, end: LocalDate): String =
        if (start.year == end.year) {
            "${start.monthValue}月${start.dayOfMonth}日-${end.monthValue}月${end.dayOfMonth}日"
        } else {
            "${start.year}年${start.monthValue}月${start.dayOfMonth}日-" +
                "${end.year}年${end.monthValue}月${end.dayOfMonth}日"
        }

    private fun barLabel(date: LocalDate, period: StatsPeriod): String = when (period) {
        StatsPeriod.WEEK -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINA)
        StatsPeriod.MONTH -> "${date.dayOfMonth}"
        StatsPeriod.YEAR -> "${date.monthValue}月"
    }
}
