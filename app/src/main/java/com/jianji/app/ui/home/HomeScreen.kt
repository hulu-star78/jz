package com.jianji.app.ui.home

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jianji.app.domain.model.Money
import com.jianji.app.domain.model.TransactionType
import com.jianji.app.ui.LocalAppContainer
import com.jianji.app.ui.appViewModelFactory
import com.jianji.app.ui.components.AmountText
import com.jianji.app.ui.components.CategoryAvatar
import com.jianji.app.ui.components.EmptyState
import com.jianji.app.ui.components.JianJiCard
import com.jianji.app.ui.components.LoadingState
import com.jianji.app.ui.components.MonthSelector
import com.jianji.app.ui.components.ProgressBar
import com.jianji.app.ui.components.SectionHeader
import com.jianji.app.ui.components.iconVector
import com.jianji.app.ui.model.CategoryShare
import com.jianji.app.ui.model.TransactionItem
import com.jianji.app.ui.theme.AmountTextStyle
import com.jianji.app.ui.theme.Dimens
import com.jianji.app.ui.theme.LocalSemanticColors
import com.jianji.app.ui.util.displayName
import com.jianji.app.ui.util.groupTitle
import com.jianji.app.ui.util.percentText
import com.jianji.app.ui.util.plain
import com.jianji.app.ui.util.recordSubtitle
import java.time.LocalDate

/**
 * 首页：月份切换 → 本月汇总卡（结余突出）→ 记账入口 → 最近账单 → 支出分类概览。
 */
@Composable
fun HomeScreen(
    onAddClick: () -> Unit,
    onSeeAllBills: () -> Unit,
    onBudgetClick: () -> Unit,
    onTransactionClick: (Long) -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: HomeViewModel = viewModel(
        factory = appViewModelFactory {
            HomeViewModel(
                transactionRepository = it.transactionRepository,
                statisticsRepository = it.statisticsRepository,
                categoryRepository = it.categoryRepository,
                accountRepository = it.accountRepository,
            )
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

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
            MonthSelector(
                label = state.month.displayName(),
                onPrevious = viewModel::previousMonth,
                onNext = viewModel::nextMonth,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // 汇总卡
        item {
            JianJiCard(color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.padding(Dimens.cardPadding)) {
                    Text(
                        text = "本月结余",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Dimens.spaceXs))
                    Text(
                        text = state.balance.plain(),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(Dimens.spaceL))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        SummaryCell(
                            label = "收入",
                            money = state.income,
                            type = TransactionType.INCOME,
                            modifier = Modifier.weight(1f),
                        )
                        SummaryCell(
                            label = "支出",
                            money = state.expense,
                            type = TransactionType.EXPENSE,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // 记账入口
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceM),
            ) {
                QuickAction(
                    icon = Icons.Filled.Add,
                    label = "记一笔",
                    modifier = Modifier.weight(1f),
                    onClick = onAddClick,
                )
                QuickAction(
                    icon = Icons.Filled.Savings,
                    label = "预算",
                    modifier = Modifier.weight(1f),
                    onClick = onBudgetClick,
                )
            }
        }

        // 最近账单
        item {
            SectionHeader(
                title = "最近账单",
                actionText = "查看全部",
                onActionClick = onSeeAllBills,
            )
        }
        if (state.recent.isEmpty()) {
            item {
                JianJiCard {
                    EmptyState(
                        icon = Icons.Filled.Receipt,
                        title = "本月还没有账目",
                        subtitle = "点击右下角按钮开始记账",
                    )
                }
            }
        } else {
            items(state.recent, key = { it.transaction.id }) { item ->
                JianJiCard(onClick = { onTransactionClick(item.transaction.id) }) {
                    TransactionRow(item = item, showDate = true)
                }
            }
        }

        // 支出分类概览
        item {
            SectionHeader(title = "支出分类")
        }
        if (state.categoryShares.isEmpty()) {
            item {
                JianJiCard {
                    EmptyState(
                        icon = Icons.Filled.PieChart,
                        title = "暂无支出数据",
                        subtitle = "记一笔支出后，这里会显示分类占比",
                    )
                }
            }
        } else {
            items(state.categoryShares, key = { it.categoryId ?: -1L }) { share ->
                JianJiCard {
                    CategoryShareRow(share = share, total = state.expense)
                }
            }
        }

        item { Spacer(Modifier.height(Dimens.spaceXxl)) }
    }
}

@Composable
private fun SummaryCell(
    label: String,
    money: Money,
    type: TransactionType,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (type == TransactionType.INCOME) {
                        LocalSemanticColors.current.income
                    } else {
                        LocalSemanticColors.current.expense
                    },
                ),
        )
        Spacer(Modifier.width(Dimens.spaceS))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = money.plain(),
                style = AmountTextStyle,
                color = if (type == TransactionType.INCOME) {
                    LocalSemanticColors.current.income
                } else {
                    LocalSemanticColors.current.expense
                },
            )
        }
    }
}

@Composable
private fun QuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable { onClick() }
            .padding(vertical = 14.dp, horizontal = Dimens.spaceL),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(Dimens.spaceS))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/** 账单行（首页与账单页共用）。 */
@Composable
fun TransactionRow(
    item: TransactionItem,
    modifier: Modifier = Modifier,
    showDate: Boolean = false,
) {
    val semantic = LocalSemanticColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimens.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryAvatar(
            iconKey = item.categoryIcon,
            type = if (item.transaction.type == TransactionType.TRANSFER) {
                null
            } else {
                if (item.transaction.type == TransactionType.INCOME) {
                    com.jianji.app.domain.model.CategoryType.INCOME
                } else {
                    com.jianji.app.domain.model.CategoryType.EXPENSE
                }
            },
        )
        Spacer(Modifier.width(Dimens.spaceM))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.categoryName ?: "未分类",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = if (item.transaction.type == TransactionType.TRANSFER) {
                "${item.accountName} → ${item.toAccountName.orEmpty()}"
            } else {
                listOfNotNull(
                    item.accountName.takeIf { it.isNotEmpty() },
                    item.transaction.note?.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
            }
            Text(
                text = subtitle.ifEmpty { recordSubtitle(item.transaction.note, item.categoryName) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showDate) {
            Text(
                text = item.transaction.date.groupTitle(LocalDate.now()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(Dimens.spaceM))
        }
        AmountText(money = item.transaction.amount, type = item.transaction.type)
    }
}

/** 分类占比行：名称 + 进度条 + 金额与百分比。 */
@Composable
fun CategoryShareRow(
    share: CategoryShare,
    total: Money,
    modifier: Modifier = Modifier,
) {
    val permille = if (total.minor > 0) {
        ((share.amount.minor * 1000) / total.minor).toInt().coerceIn(0, 1000)
    } else {
        0
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimens.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryAvatar(iconKey = share.icon, type = com.jianji.app.domain.model.CategoryType.EXPENSE)
        Spacer(Modifier.width(Dimens.spaceM))
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
