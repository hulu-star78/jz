package com.jianji.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jianji.app.domain.model.Money
import com.jianji.app.ui.theme.Dimens
import com.jianji.app.ui.theme.LocalSemanticColors
import com.jianji.app.ui.util.compact

/** 趋势图单组（一天/一周/一月）的数据。 */
data class TrendBar(
    val label: String,
    val income: Money,
    val expense: Money,
    val selected: Boolean = false,
)

/**
 * 收支趋势条形图（收入/支出双柱并列，支持点选）。
 *
 * 可读性保证：
 * - 顶部实时显示选中组的收入/支出数值；
 * - 底部 x 轴标签（≤8 组全部显示，否则等距抽样）；
 * - 无数据时由调用方渲染空状态，本组件不画柱子。
 */
@Composable
fun TrendChart(
    bars: List<TrendBar>,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    onBarClick: ((Int) -> Unit)? = null,
) {
    if (bars.isEmpty()) return
    val semantic = LocalSemanticColors.current
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val selectedIndex = bars.indexOfFirst { it.selected }.let { if (it >= 0) it else bars.lastIndex }
    val selected = bars[selectedIndex]
    val incomeColor = semantic.income
    val expenseColor = semantic.expense

    // 无障碍：为图表提供整体朗读描述（选中组数值 + 数据组数）
    val chartDescription = buildString {
        append("收支趋势图，共 ")
        append(bars.size)
        append(" 组。当前显示 ")
        append(selected.label)
        append("，收入 ")
        append(selected.income.compact())
        append("，支出 ")
        append(selected.expense.compact())
        append("。")
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = chartDescription },
    ) {
        Text(
            text = "${selected.label}　收入 ${selected.income.compact()}　支出 ${selected.expense.compact()}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Dimens.spaceS))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .then(
                    if (onBarClick != null) {
                        Modifier.pointerInput(bars.size) {
                            detectTapGestures { offset ->
                                val groupWidth = size.width / bars.size
                                val index = (offset.x / groupWidth).toInt().coerceIn(0, bars.lastIndex)
                                onBarClick(index)
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            val maxValue = maxOf(
                bars.maxOf { it.income.minor },
                bars.maxOf { it.expense.minor },
                1L,
            )
            val barAreaHeight = size.height - 16.dp.toPx() // 预留底部标签
            val groupWidth = size.width / bars.size
            val barWidth = (groupWidth * 0.28f).coerceAtMost(14.dp.toPx())
            val gap = 2.dp.toPx()
            val labelStep = (bars.size / 6).coerceAtLeast(1)

            bars.forEachIndexed { index, bar ->
                val groupStart = index * groupWidth
                val centerX = groupStart + groupWidth / 2f
                val incomeH = (bar.income.minor.toFloat() / maxValue) * barAreaHeight
                val expenseH = (bar.expense.minor.toFloat() / maxValue) * barAreaHeight

                drawRoundBar(
                    left = centerX - barWidth - gap / 2f,
                    bottom = barAreaHeight,
                    width = barWidth,
                    barHeight = incomeH,
                    color = incomeColor,
                    selected = bar.selected,
                )
                drawRoundBar(
                    left = centerX + gap / 2f,
                    bottom = barAreaHeight,
                    width = barWidth,
                    barHeight = expenseH,
                    color = expenseColor,
                    selected = bar.selected,
                )

                val showLabel = bars.size <= 8 || index % labelStep == 0 || index == bars.lastIndex
                if (showLabel) {
                    val result = measurer.measure(bar.label, labelStyle)
                    drawText(
                        textLayoutResult = result,
                        topLeft = Offset(
                            x = centerX - result.size.width / 2f,
                            y = barAreaHeight + 4.dp.toPx(),
                        ),
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawRoundBar(
    left: Float,
    bottom: Float,
    width: Float,
    barHeight: Float,
    color: Color,
    selected: Boolean,
) {
    if (barHeight <= 0.5f) return
    drawRoundRect(
        color = color.copy(alpha = if (selected) 1f else 0.75f),
        topLeft = Offset(left, bottom - barHeight),
        size = Size(width, barHeight),
        cornerRadius = CornerRadius(width / 2f, width / 2f),
    )
}

/** 环形图分片。 */
data class DonutSlice(
    val categoryId: Long?,
    val label: String,
    val amount: Money,
    val color: Color,
    val fraction: Float,
)

/**
 * 分类占比环形图：中心显示总额，分片按占比绘制。
 * 分片颜色由调用方从语义色板派生，保证全局一致。
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    centerLabel: String,
    centerValue: String,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
) {
    val valueColor = MaterialTheme.colorScheme.onSurface
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    // 无障碍：环形图整体朗读描述（总额 + 各分类占比）
    val donutDescription = buildString {
        append(centerLabel)
        append(" ")
        append(centerValue)
        append("。分类构成：")
        slices.forEachIndexed { index, slice ->
            if (index > 0) append("；")
            append(slice.label)
            append(" ")
            append(slice.amount.compact())
        }
        append("。")
    }
    Box(
        modifier = modifier.semantics { contentDescription = donutDescription },
        contentAlignment = Alignment.Center,
    ) {
        val measurer = rememberTextMeasurer()
        Canvas(modifier = Modifier.height(size).fillMaxWidth()) {
            val strokeWidth = 26.dp.toPx()
            val diameter = minOf(this.size.width, this.size.height) - strokeWidth
            val topLeft = Offset(
                x = (this.size.width - diameter) / 2f,
                y = (this.size.height - diameter) / 2f,
            )
            val arcSize = Size(diameter, diameter)
            var startAngle = -90f
            slices.forEach { slice ->
                val sweep = slice.fraction * 360f
                drawArc(
                    color = slice.color,
                    startAngle = startAngle,
                    sweepAngle = (sweep - if (slices.size > 1) 1.5f else 0f).coerceAtLeast(0.5f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                )
                startAngle += sweep
            }
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val valueResult = measurer.measure(
                centerValue,
                TextStyle(fontSize = 18.sp, color = valueColor),
            )
            val labelResult = measurer.measure(
                centerLabel,
                TextStyle(fontSize = 11.sp, color = labelColor),
            )
            drawText(
                textLayoutResult = valueResult,
                topLeft = Offset(cx - valueResult.size.width / 2f, cy - valueResult.size.height - 2.dp.toPx()),
            )
            drawText(
                textLayoutResult = labelResult,
                topLeft = Offset(cx - labelResult.size.width / 2f, cy + 2.dp.toPx()),
            )
        }
    }
}

/** 图例色块。 */
@Composable
fun LegendDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(10.dp)
            .background(color),
    ) {
        Spacer(Modifier.height(10.dp))
    }
}

/** 供统计页复用的语义色板（环形图分片取色，低饱和顺序循环）。 */
fun donutPalette(): List<Color> = listOf(
    Color(0xFF4F7D6B),
    Color(0xFF7FA88F),
    Color(0xFFA8C3B2),
    Color(0xFFC9A227),
    Color(0xFFD9B36A),
    Color(0xFF8E9BA6),
    Color(0xFFB0BEC5),
    Color(0xFFC2594E),
)
