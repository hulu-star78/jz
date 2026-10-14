package com.jianji.app.ui.statistics

import com.jianji.app.data.repository.CategoryRepositoryImpl
import com.jianji.app.data.repository.StatisticsRepositoryImpl
import com.jianji.app.domain.model.CategoryType
import com.jianji.app.domain.model.Money
import com.jianji.app.domain.model.TransactionType
import com.jianji.app.testutil.DatabaseTestSupport
import com.jianji.app.testutil.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * 统计 ViewModel 测试：周期类型与历史偏移的区间正确性（跨月、跨年）。
 * 固定“今天”为 2026-10-14（周三），保证断言确定性。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StatisticsViewModelTest : DatabaseTestSupport() {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 10, 14)

    private suspend fun setUpViewModel(): StatisticsViewModel {
        val foodId = insertCategory("餐饮", CategoryType.EXPENSE)
        val cashId = insertAccount("现金")
        // 本月 10/05：100；本月 10/10：300；上月 09/05：200；去年 2025/08/05：400
        insertTransaction(10000, TransactionType.EXPENSE, LocalDate.of(2026, 10, 5), foodId, cashId)
        insertTransaction(30000, TransactionType.EXPENSE, LocalDate.of(2026, 10, 10), foodId, cashId)
        insertTransaction(20000, TransactionType.EXPENSE, LocalDate.of(2026, 9, 5), foodId, cashId)
        insertTransaction(40000, TransactionType.EXPENSE, LocalDate.of(2025, 8, 5), foodId, cashId)

        val vm = StatisticsViewModel(
            statisticsRepository = StatisticsRepositoryImpl(database.transactionDao(), database.categoryDao()),
            categoryRepository = CategoryRepositoryImpl(database.categoryDao()),
            today = today,
        )
        vm.state.first { !it.loading }
        return vm
    }

    @Test
    fun `defaults to current month`() = runTest {
        val vm = setUpViewModel()
        val state = vm.state.value
        assertEquals(StatsPeriod.MONTH, state.period)
        assertEquals(0, state.periodOffset)
        assertEquals("2026年10月", state.rangeLabel)
        assertFalse(state.canGoNext)
        // 本月两笔：100 + 300 = 400
        assertEquals(Money.fromMinor(40000), state.expense)
    }

    @Test
    fun `previous and next period navigate history`() = runTest {
        val vm = setUpViewModel()

        vm.previousPeriod()
        vm.state.first { it.periodOffset == 1 && !it.loading }
        assertEquals("2026年9月", vm.state.value.rangeLabel)
        assertEquals(Money.fromMinor(20000), vm.state.value.expense)

        vm.previousPeriod()
        vm.state.first { it.periodOffset == 2 && !it.loading }
        assertEquals("2026年8月", vm.state.value.rangeLabel)
        assertTrue(vm.state.value.expense.isZero)

        // 回到上一月
        vm.nextPeriod()
        vm.state.first { it.periodOffset == 1 && !it.loading }
        assertEquals("2026年9月", vm.state.value.rangeLabel)
    }

    @Test
    fun `next period cannot go beyond current period`() = runTest {
        val vm = setUpViewModel()
        vm.nextPeriod()
        vm.state.first { it.periodOffset == 0 && !it.loading }
        assertEquals(0, vm.state.value.periodOffset)
        assertFalse(vm.state.value.canGoNext)
        assertEquals("2026年10月", vm.state.value.rangeLabel)
    }

    @Test
    fun `switching period type resets offset`() = runTest {
        val vm = setUpViewModel()
        vm.previousPeriod()
        vm.state.first { it.periodOffset == 1 && !it.loading }

        vm.onPeriodChange(StatsPeriod.YEAR)
        vm.state.first { it.periodOffset == 0 && it.period == StatsPeriod.YEAR && !it.loading }
        assertEquals("2026年", vm.state.value.rangeLabel)
        // 2026 年三笔：10/05 的 100 + 10/10 的 300 + 09/05 的 200 = 600
        assertEquals(Money.fromMinor(60000), vm.state.value.expense)
    }

    @Test
    fun `week period uses sliding seven day window`() = runTest {
        val vm = setUpViewModel()
        vm.onPeriodChange(StatsPeriod.WEEK)
        vm.state.first { it.period == StatsPeriod.WEEK && it.periodOffset == 0 && !it.loading }

        // 2026-10-08 ~ 2026-10-14：只有 10/10 的 300
        assertEquals("10月8日-10月14日", vm.state.value.rangeLabel)
        assertEquals(7, vm.state.value.bars.size)
        assertEquals(Money.fromMinor(30000), vm.state.value.expense)

        vm.previousPeriod()
        vm.state.first { it.period == StatsPeriod.WEEK && it.periodOffset == 1 && !it.loading }
        // 2026-10-01 ~ 2026-10-07：只有 10/05 的 100
        assertEquals("10月1日-10月7日", vm.state.value.rangeLabel)
        assertEquals(Money.fromMinor(10000), vm.state.value.expense)
    }

    @Test
    fun `year period crosses year boundary`() = runTest {
        val vm = setUpViewModel()
        vm.onPeriodChange(StatsPeriod.YEAR)
        vm.state.first { it.period == StatsPeriod.YEAR && it.periodOffset == 0 && !it.loading }

        vm.previousPeriod()
        vm.state.first { it.period == StatsPeriod.YEAR && it.periodOffset == 1 && !it.loading }
        assertEquals("2025年", vm.state.value.rangeLabel)
        assertEquals(Money.fromMinor(40000), vm.state.value.expense)
        // 年度趋势补满 12 个月
        assertEquals(12, vm.state.value.bars.size)
    }

    @Test
    fun `month period crossing year boundary`() = runTest {
        val vm = setUpViewModel()
        // 从 2026 年 10 月往前翻 12 次到 2025 年 10 月
        repeat(12) { vm.previousPeriod() }
        vm.state.first { it.periodOffset == 12 && !it.loading }
        assertEquals("2025年10月", vm.state.value.rangeLabel)
        assertTrue(vm.state.value.expense.isZero)
    }
}
