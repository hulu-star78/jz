package com.jianji.app.domain.model

/**
 * 金额值类型：以「分」为最小货币单位的整数存储。
 *
 * 项目铁律：
 * - 禁止使用 Double / Float 表示、传递或计算金额；
 * - 所有金额运算必须经过本类型，利用 Long 整数运算保证精度；
 * - 数据库、参数、返回值中的金额一律是 [minor]（分）。
 */
@JvmInline
value class Money(val minor: Long) : Comparable<Money> {

    val isZero: Boolean get() = minor == 0L
    val isPositive: Boolean get() = minor > 0
    val isNegative: Boolean get() = minor < 0

    /** 溢出时抛出 ArithmeticException，避免静默产生错误金额。 */
    operator fun plus(other: Money): Money = Money(Math.addExact(minor, other.minor))

    operator fun minus(other: Money): Money = Money(Math.subtractExact(minor, other.minor))

    operator fun times(quantity: Int): Money = Money(Math.multiplyExact(minor, quantity.toLong()))

    operator fun unaryMinus(): Money = Money(Math.negateExact(minor))

    fun abs(): Money = if (isNegative) Money(Math.negateExact(minor)) else this

    override fun compareTo(other: Money): Int = minor.compareTo(other.minor)

    /**
     * 格式化为两位小数字符串，如 1234.56、-12.30。
     * 千分位分组与小数点分隔符可配置（用于多区域显示）。
     * 注意：仅用于展示，不得反向用于计算。
     */
    fun format(groupSeparator: Char = ',', decimalSeparator: Char = '.'): String {
        val negative = minor < 0
        // 无符号运算取绝对值，避免 Long.MIN_VALUE 取负溢出
        val absMinor: ULong = if (negative) {
            (-(minor + 1)).toULong() + 1u
        } else {
            minor.toULong()
        }
        val intPart = (absMinor / 100u).toLong()
        val fracPart = (absMinor % 100u).toInt()
        val sb = StringBuilder()
        if (negative) sb.append('-')
        val digits = intPart.toString()
        for (i in digits.indices) {
            if (i > 0 && (digits.length - i) % 3 == 0) sb.append(groupSeparator)
            sb.append(digits[i])
        }
        sb.append(decimalSeparator)
        sb.append(fracPart.toString().padStart(2, '0'))
        return sb.toString()
    }

    companion object {
        val ZERO: Money = Money(0)

        fun fromMinor(minor: Long): Money = Money(minor)

        /**
         * 解析用户输入的金额字符串。
         *
         * 规则：
         * - 允许前导 +/-，允许空格、千分位逗号（会被剔除）；
         * - 最多两位小数；超过两位时对第三位四舍五入（half-up）；
         * - 非法输入（空串、多个小数点、非数字字符、超出 Long 范围）返回 null；
         * - 全程字符串与整数运算，不经过 Double / Float。
         */
        fun parse(input: String): Money? {
            var s = input.trim().replace(",", "").replace(" ", "").replace("，", "")
            if (s.isEmpty()) return null

            var negative = false
            if (s[0] == '-' || s[0] == '+') {
                negative = s[0] == '-'
                s = s.substring(1)
            }
            if (s.isEmpty() || s == ".") return null

            var intPartStr = s
            var fracPartStr = ""
            val dotIndex = s.indexOf('.')
            if (dotIndex >= 0) {
                intPartStr = s.substring(0, dotIndex)
                fracPartStr = s.substring(dotIndex + 1)
                if (fracPartStr.indexOf('.') >= 0) return null
            }
            if (intPartStr.isEmpty()) intPartStr = "0"
            if (!intPartStr.all { it in '0'..'9' }) return null
            if (fracPartStr.isNotEmpty() && !fracPartStr.all { it in '0'..'9' }) return null

            var frac = when (fracPartStr.length) {
                0 -> 0L
                1 -> fracPartStr.toLong() * 10
                else -> {
                    val head = fracPartStr.substring(0, 2).toLong()
                    if (fracPartStr.length > 2 && fracPartStr[2] >= '5') head + 1 else head
                }
            }
            val intValue = intPartStr.toLongOrNull() ?: return null
            var totalMinor = try {
                Math.multiplyExact(intValue, 100L)
            } catch (e: ArithmeticException) {
                return null
            }
            totalMinor = try {
                Math.addExact(totalMinor, frac)
            } catch (e: ArithmeticException) {
                return null
            }
            if (totalMinor == 0L) negative = false
            return Money(if (negative) -totalMinor else totalMinor)
        }
    }
}

/** 金额列表安全求和（任一步溢出即抛异常，由调用方处理）。 */
fun List<Money>.sumMoney(): Money = fold(Money.ZERO) { acc, money -> acc + money }
