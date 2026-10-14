package com.jianji.app.ui.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jianji.app.ui.LocalAppContainer
import com.jianji.app.ui.appViewModelFactory
import com.jianji.app.ui.components.DonutChart
import com.jianji.app.ui.components.DonutSlice
import com.jianji.app.ui.components.EmptyState
import com.jianji.app.ui.components.JianJiCard
import com.jianji.app.ui.components.LoadingState
import com.jianji.app.ui.components.ProgressBar
import com.jianji.app.ui.components.SectionHeader
import com.jianji.app.ui.components.TagChip
import com.jianji.app.ui.components.TrendBar
import com.jianji.app.ui.components.TrendChart
import com.jianji.app.ui.components.donutPalette
import com.jianji.app.ui.model.CategoryShare
import com.jianji.app.ui.theme.AmountTextStyle
import com.jianji.app.ui.theme.Dimens
import com.jianji.app.ui.theme.LocalSemanticColors
import com.jianji.app.ui.util.percentText
import com.jianji.app.ui.util.plain

/**
 * 统计页：周期切换 → 汇总 → 趋势图 → 分类占比环形图 → 支出/收入排行。
 * 每个模块都有明确空状态；图表数值可读优先于装饰。
 */
@Composable
fun StatisticsScreen() {
    val container = LocalAppContainer.current
    val viewModel: StatisticsViewModel = viewModel(
        factory = appViewModelFactory {
            StatisticsViewModel(
                statisticsRepository = it.statisticsRepository,
                categoryRepository = it.categoryRepository,
            )
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedBar by remember(state.bars) { mutableIntStateOf(state.bars.lastIndex.coerceAtLeast(0)) }

    if (state.loading) {
        LoadingState(modifier = Modifier.fillMaxSize())
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceM),
    ) {
        item {
            Spacer(Modifier.height(Dimens.spaceS))
            // 周期切换：‹ 区间 ›，箭头可翻历史周期（不允许超过当前周期）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = viewModel::previousPeriod) {
                    Icon(
                        Icons.Filled.ChevronLeft,
                        contentDescription = "上一个周期",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = state.rangeLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = viewModel::nextPeriod,
                    enabled = state.canGoNext,
                ) {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = "下一个周期",
                        tint = if (state.canGoNext) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
                StatsPeriod.entries.forEach { period ->
                    TagChip(
                        text = period.label,
                        selected = state.period == period,
                        onClick = { viewModel.onPeriodChange(period) },
                    )
                }
                Spacer(Modifier.weight(1f))
                if (state.periodOffset > 0) {
                    Text(
                        text = "查看历史",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }
        }

        // 汇总
        item {
            JianJiCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dimens.cardPadding),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    StatCell("收入", state.income.plain(), LocalSemanticColors.current.income)
                    StatCell("支出", state.expense.plain(), LocalSemanticColors.current.expense)
                    StatCell("结余", state.balance.plain(), MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        // 趋势
        item { SectionHeader(title = "收支趋势") }
        item {
            JianJiCard {
                Column(modifier = Modifier.padding(Dimens.cardPadding)) {
                    if (state.bars.isEmpty() || !state.hasData) {
                        EmptyState(
                            icon = Icons.Filled.TrendingUp,
                            title = "暂无趋势数据",
                            subtitle = "记录几笔账目后即可查看趋势",
                        )
                    } else {
                        val bars = state.bars.mapIndexed { index, bar ->
                            bar.copy(selected = index == selectedBar)
                        }
                        TrendChart(
                            bars = bars,
                            onBarClick = { selectedBar = it },
                        )
                        Spacer(Modifier.height(Dimens.spaceS))
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceL)) {
                            LegendLabel(color = LocalSemanticColors.current.income, text = "收入")
                            LegendLabel(color = LocalSemanticColors.current.expense, text = "支出")
                        }
                    }
                }
            }
        }

        // 分类占比
        item { SectionHeader(title = "支出分类占比") }
        item {
            JianJiCard {
                Column(modifier = Modifier.padding(Dimens.cardPadding)) {
                    if (state.expenseShares.isEmpty()) {
                        EmptyState(
                            icon = Icons.Filled.PieChart,
                            title = "暂无支出",
                            subtitle = "当前周期还没有支出记录",
                        )
                    } else {
                        val palette = donutPalette()
                        val slices = state.expenseShares.mapIndexed { index, share ->
                            DonutSlice(
                                categoryId = share.categoryId,
                                label = share.name,
                                amount = share.amount,
                                color = palette[index % palette.size],
                                fraction = share.fraction.coerceIn(0.02f, 1f),
                            )
                        }
                        DonutChart(
                            slices = slices,
                            centerLabel = "总支出",
                            centerValue = state.expense.plain(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(Dimens.spaceL))
                        slices.forEach { slice ->
                            val share = state.expenseShares.first { it.categoryId == slice.categoryId }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(slice.color),
                                )
                                Spacer(Modifier.width(Dimens.spaceS))
                                Text(
                                    text = slice.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = percentText(share.amount.minor, state.expense.minor),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(Dimens.spaceM))
                                Text(
                                    text = share.amount.plain(),
                                    style = AmountTextStyle,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 支出排行
        item { SectionHeader(title = "支出排行") }
        if (state.expenseShares.isEmpty()) {
            item {
                JianJiCard {
                    EmptyState(
                        icon = Icons.Filled.PieChart,
                        title = "暂无支出排行",
                        subtitle = "当前周期没有支出数据",
                    )
                }
            }
        } else {
            itemsIndexed(state.expenseShares) { index, share ->
                JianJiCard {
                    RankingRow(index = index + 1, share = share, total = state.expense)
                }
            }
        }

        // 收入排行
        item { SectionHeader(title = "收入排行") }
        if (state.incomeShares.isEmpty()) {
            item {
                JianJiCard {
                    EmptyState(
                        icon = Icons.Filled.TrendingUp,
                        title = "暂无收入排行",
                        subtitle = "当前周期没有收入数据",
                    )
                }
            }
        } else {
            itemsIndexed(state.incomeShares) { index, share ->
                JianJiCard {
                    RankingRow(index = index + 1, share = share, total = state.income)
                }
            }
        }

        item { Spacer(Modifier.height(Dimens.spaceXxl)) }
    }
}

@Composable
private fun StatCell(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(text = value, style = AmountTextStyle, color = color)
    }
}

@Composable
private fun LegendLabel(color: androidx.compose.ui.graphics.Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RankingRow(index: Int, share: CategoryShare, total: com.jianji.app.domain.model.Money) {
    val permille = if (total.minor > 0) {
        ((share.amount.minor * 1000) / total.minor).toInt().coerceIn(0, 1000)
    } else {
        0
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$index",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(20.dp),
        )
        Spacer(Modifier.width(Dimens.spaceS))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = share.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = share.amount.plain(),
                    style = AmountTextStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(6.dp))
            ProgressBar(permille = permille, height = 6.dp)
        }
        Spacer(Modifier.width(Dimens.spaceM))
        Text(
            text = percentText(share.amount.minor, total.minor),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
