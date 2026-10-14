package com.jianji.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 预算进度测试：进度为千分比整数，不引入浮点。 */
class BudgetProgressTest {

    private fun budget(limitMinor: Long) = Budget(
        id = 1L,
        yearMonth = "2026-10",
        categoryId = null,
        limit = Money.fromMinor(limitMinor),
    )

    @Test
    fun `progress permille within budget`() {
        val progress = BudgetProgress(budget(100000L), Money.fromMinor(25000L))
        assertEquals(250, progress.usedPermille)
        assertFalse(progress.isExceeded)
        assertEquals(Money.fromMinor(75000L), progress.remaining)
    }

    @Test
    fun `progress caps at 1000 when exceeded`() {
        val progress = BudgetProgress(budget(100000L), Money.fromMinor(150000L))
        assertEquals(1000, progress.usedPermille)
        assertTrue(progress.isExceeded)
        assertTrue(progress.remaining.isNegative)
    }

    @Test
    fun `exact limit is not exceeded`() {
        val progress = BudgetProgress(budget(100000L), Money.fromMinor(100000L))
        assertEquals(1000, progress.usedPermille)
        assertFalse(progress.isExceeded)
        assertTrue(progress.remaining.isZero)
    }

    @Test
    fun `zero used is zero progress`() {
        val progress = BudgetProgress(budget(100000L), Money.ZERO)
        assertEquals(0, progress.usedPermille)
        assertFalse(progress.isExceeded)
    }
}
