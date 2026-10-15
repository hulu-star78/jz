package com.jianji.app.ui.records

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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jianji.app.domain.model.TransactionType
import com.jianji.app.ui.LocalAppContainer
import com.jianji.app.ui.appViewModelFactory
import com.jianji.app.ui.components.EmptyState
import com.jianji.app.ui.components.JianJiCard
import com.jianji.app.ui.components.LoadingState
import com.jianji.app.ui.components.MonthSelector
import com.jianji.app.ui.components.TagChip
import com.jianji.app.ui.home.TransactionRow
import com.jianji.app.ui.model.DayGroup
import com.jianji.app.ui.theme.AmountTextStyle
import com.jianji.app.ui.theme.Dimens
import com.jianji.app.ui.theme.LocalSemanticColors
import com.jianji.app.ui.util.displayName
import com.jianji.app.ui.util.plain

/**
 * 账单页：搜索 + 类型/分类筛选 + 月份切换 + 按日分组列表。
 */
@Composable
fun RecordsScreen(
    onAddClick: () -> Unit,
    onTransactionClick: (Long) -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: RecordsViewModel = viewModel(
        factory = appViewModelFactory {
            RecordsViewModel(
                transactionRepository = it.transactionRepository,
                accountRepository = it.accountRepository,
                categoryRepository = it.categoryRepository,
            )
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCategorySheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.screenPadding),
    ) {
        Spacer(Modifier.height(Dimens.spaceS))

        // 搜索框
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = Dimens.spaceM, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(Dimens.spaceS))
            androidx.compose.foundation.text.BasicTextField(
                value = state.keyword,
                onValueChange = viewModel::onKeywordChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier.weight(1f),
            ) {
                if (state.keyword.isEmpty()) {
                    Text(
                        text = "搜索备注",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                it()
            }
            if (state.keyword.isNotEmpty()) {
                Icon(
                    Icons.Filled.Clear,
                    contentDescription = "清空",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { viewModel.onKeywordChange("") },
                )
            }
        }

        Spacer(Modifier.height(Dimens.spaceM))

        // 类型筛选
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
            TagChip(
                text = "全部",
                selected = state.typeFilter == null,
                onClick = { viewModel.onTypeFilterChange(null) },
            )
            TagChip(
                text = "支出",
                selected = state.typeFilter == TransactionType.EXPENSE,
                onClick = { viewModel.onTypeFilterChange(TransactionType.EXPENSE) },
                color = LocalSemanticColors.current.expense,
            )
            TagChip(
                text = "收入",
                selected = state.typeFilter == TransactionType.INCOME,
                onClick = { viewModel.onTypeFilterChange(TransactionType.INCOME) },
                color = LocalSemanticColors.current.income,
            )
            TagChip(
                text = "转账",
                selected = state.typeFilter == TransactionType.TRANSFER,
                onClick = { viewModel.onTypeFilterChange(TransactionType.TRANSFER) },
            )
            TagChip(
                text = state.categories.firstOrNull { it.id == state.categoryId }?.name ?: "分类",
                selected = state.categoryId != null,
                onClick = { showCategorySheet = true },
            )
        }

        Spacer(Modifier.height(Dimens.spaceS))
        MonthSelector(
            label = state.month.displayName(),
            onPrevious = viewModel::previousMonth,
            onNext = viewModel::nextMonth,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Dimens.spaceS))

        if (state.loading) {
            LoadingState(modifier = Modifier.fillMaxSize())
            return@Column
        }

        if (state.groups.isEmpty()) {
            EmptyState(
                icon = com.jianji.app.ui.components.BillsEmptyIcon,
                title = "没有符合条件的账目",
                subtitle = "换个筛选条件，或记一笔吧",
                actionText = "记一笔",
                onAction = onAddClick,
                modifier = Modifier.fillMaxSize(),
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceM),
        ) {
            state.groups.forEach { group ->
                item(key = "header-${group.date}") {
                    DayHeader(group = group)
                }
                // key 加前缀：与日期头 key 隔离，避免未来引入其他 id 键时冲突
                items(group.items, key = { "txn-${it.transaction.id}" }) { item ->
                    JianJiCard(onClick = { onTransactionClick(item.transaction.id) }) {
                        TransactionRow(item = item)
                    }
                }
            }
            item { Spacer(Modifier.height(Dimens.spaceXxl)) }
        }
    }

    if (showCategorySheet) {
        CategoryFilterSheet(
            categories = state.categories.filter { category ->
                when (state.typeFilter) {
                    TransactionType.EXPENSE -> category.type == com.jianji.app.domain.model.CategoryType.EXPENSE
                    TransactionType.INCOME -> category.type == com.jianji.app.domain.model.CategoryType.INCOME
                    else -> true
                }
            },
            selectedId = state.categoryId,
            onSelect = {
                viewModel.onCategoryFilterChange(it)
                showCategorySheet = false
            },
            onDismiss = { showCategorySheet = false },
        )
    }
}

@Composable
private fun DayHeader(group: DayGroup) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.spaceS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = group.title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceM)) {
            if (!group.income.isZero) {
                Text(
                    text = "收入 ${group.income.plain()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalSemanticColors.current.income,
                )
            }
            if (!group.expense.isZero) {
                Text(
                    text = "支出 ${group.expense.plain()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalSemanticColors.current.expense,
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFilterSheet(
    categories: List<com.jianji.app.domain.model.Category>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(Dimens.cardPadding)) {
            Text(
                text = "按分类筛选",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = Dimens.spaceM),
            )
            TagChip(
                text = "全部分类",
                selected = selectedId == null,
                onClick = { onSelect(null) },
            )
            Spacer(Modifier.height(Dimens.spaceM))
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
                categories.forEach { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(category.id) }
                            .padding(Dimens.spaceM),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary),
                        ) {}
                        Spacer(Modifier.width(Dimens.spaceM))
                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            Spacer(Modifier.height(Dimens.spaceXl))
        }
    }
}
