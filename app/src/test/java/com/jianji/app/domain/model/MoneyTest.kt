package com.jianji.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 金额测试：零值、大金额、小数输入、四舍五入、累计计算。
 * 全部使用 Long 整数路径，禁止 Double / Float。
 */
class MoneyTest {

    @Test
    fun `parse zero`() {
        assertEquals(0L, Money.parse("0")!!.minor)
        assertEquals(0L, Money.parse("0.00")!!.minor)
        assertEquals(0L, Money.parse("-0")!!.minor)
        assertEquals(0L, Money.parse("-0.00")!!.minor)
    }

    @Test
    fun `parse decimal input`() {
        assertEquals(1234L, Money.parse("12.34")!!.minor)
        assertEquals(50L, Money.parse("0.5")!!.minor)
        assertEquals(5L, Money.parse("0.05")!!.minor)
        assertEquals(100L, Money.parse("1")!!.minor)
        assertEquals(100L, Money.parse("1.")!!.minor)
        assertEquals(50L, Money.parse(".5")!!.minor)
    }

    @Test
    fun `parse rounds half up beyond two decimals`() {
        assertEquals(1L, Money.parse("0.005")!!.minor)
        assertEquals(0L, Money.parse("0.004")!!.minor)
        assertEquals(200L, Money.parse("1.999")!!.minor)
        assertEquals(101L, Money.parse("1.005")!!.minor)
        assertEquals(100L, Money.parse("0.999")!!.minor)
    }

    @Test
    fun `parse accepts group separators and sign`() {
        assertEquals(123456L, Money.parse("1,234.56")!!.minor)
        assertEquals(-123456L, Money.parse("-1,234.56")!!.minor)
        assertEquals(123456L, Money.parse("+1234.56")!!.minor)
        assertEquals(123456L, Money.parse(" 1234.56 ")!!.minor)
    }

    @Test
    fun `parse rejects invalid input`() {
        assertNull(Money.parse(""))
        assertNull(Money.parse("   "))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("1.2.3"))
        assertNull(Money.parse("--1"))
        assertNull(Money.parse("."))
        assertNull(Money.parse("12abc"))
        assertNull(Money.parse("1.2a"))
        // 超出 Long 范围
        assertNull(Money.parse("999999999999999999999"))
    }

    @Test
    fun `parse large amounts`() {
        val max = Money.parse("92233720368547758.07")!!
        assertEquals(Long.MAX_VALUE, max.minor)
        assertEquals(99999999999L, Money.parse("999999999.99")!!.minor)
    }

    @Test
    fun `arithmetic is exact`() {
        // 经典浮点陷阱：0.1 + 0.2 != 0.3，整数分运算必须严格相等
        val one = Money.parse("0.1")!!
        val two = Money.parse("0.2")!!
        val three = Money.parse("0.3")!!
        assertEquals(three, one + two)
        assertEquals(Money.ZERO, three - three)
    }

    @Test
    fun `accumulation of many small amounts`() {
        val items = List(10) { Money.parse("0.1")!! }
        assertEquals(Money.parse("1.00")!!, items.sumMoney())
        val big = List(1000) { Money.parse("19.99")!! }
        assertEquals(Money.parse("19990.00")!!, big.sumMoney())
    }

    @Test
    fun `overflow throws instead of silently wrapping`() {
        val max = Money.fromMinor(Long.MAX_VALUE)
        assertThrows(ArithmeticException::class.java) { max + Money.fromMinor(1) }
        assertThrows(ArithmeticException::class.java) { max * 2 }
        val min = Money.fromMinor(Long.MIN_VALUE)
        assertThrows(ArithmeticException::class.java) { -min }
    }

    @Test
    fun `format two decimals with grouping`() {
        assertEquals("0.00", Money.ZERO.format())
        assertEquals("0.05", Money.fromMinor(5).format())
        assertEquals("12.34", Money.fromMinor(1234).format())
        assertEquals("1,234.56", Money.fromMinor(123456).format())
        assertEquals("-30.50", Money.fromMinor(-3050).format())
        assertEquals("999,999,999,999.99", Money.fromMinor(99999999999999).format())
        assertEquals("1.234,56", Money.fromMinor(123456).format(groupSeparator = '.', decimalSeparator = ','))
    }

    @Test
    fun `comparison and predicates`() {
        assertTrue(Money.fromMinor(100) > Money.ZERO)
        assertTrue(Money.fromMinor(-100) < Money.ZERO)
        assertTrue(Money.fromMinor(-100).abs() == Money.fromMinor(100))
        assertTrue(Money.ZERO.isZero)
        assertTrue(Money.fromMinor(1).isPositive)
        assertTrue(Money.fromMinor(-1).isNegative)
    }
}
