package com.jianji.app.ui.budget

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jianji.app.domain.model.BudgetProgress
import com.jianji.app.domain.model.Category
import com.jianji.app.ui.LocalAppContainer
import com.jianji.app.ui.appViewModelFactory
import com.jianji.app.ui.components.CategoryAvatar
import com.jianji.app.ui.components.EmptyState
import com.jianji.app.ui.components.JianJiCard
import com.jianji.app.ui.components.JianJiTopBar
import com.jianji.app.ui.components.LoadingState
import com.jianji.app.ui.components.MonthSelector
import com.jianji.app.ui.components.ProgressBar
import com.jianji.app.ui.components.SectionHeader
import com.jianji.app.ui.theme.AmountTextStyle
import com.jianji.app.ui.theme.Dimens
import com.jianji.app.ui.theme.LocalSemanticColors
import com.jianji.app.ui.util.displayName
import com.jianji.app.ui.util.key
import com.jianji.app.ui.util.percentText
import com.jianji.app.ui.util.plain
import kotlinx.coroutines.launch

/**
 * 预算页：月度总预算 + 分类预算，实时进度与超支提醒。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: BudgetViewModel = viewModel(
        factory = appViewModelFactory {
            BudgetViewModel(
                budgetRepository = it.budgetRepository,
                categoryRepository = it.categoryRepository,
            )
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var showTotalDialog by remember { mutableStateOf(false) }
    var showAddCategorySheet by remember { mutableStateOf(false) }
    var pendingCategory by remember { mutableStateOf<Category?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        JianJiTopBar(title = "预算", onBack = onBack)

        if (state.loading) {
            LoadingState(modifier = Modifier.fillMaxSize())
            return@Column
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceM),
        ) {
            item {
                MonthSelector(
                    label = state.month.displayName(),
                    onPrevious = viewModel::previousMonth,
                    onNext = viewModel::nextMonth,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // 总预算卡
            item {
                val total = state.total
                if (total == null) {
                    JianJiCard {
                        Column(modifier = Modifier.padding(Dimens.cardPadding)) {
                            EmptyState(
                                icon = Icons.Filled.Savings,
                                title = "还未设置本月总预算",
                                subtitle = "设置后可以实时看到本月花费进度",
                                actionText = "设置总预算",
                                onAction = { showTotalDialog = true },
                            )
                        }
                    }
                } else {
                    TotalBudgetCard(
                        progress = total,
                        monthLabel = state.month.displayName(),
                        onEdit = { showTotalDialog = true },
                    )
                }
            }

            // 分类预算
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "分类预算",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (state.availableCategories.isNotEmpty()) {
                        TextButton(onClick = { showAddCategorySheet = true }) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("添加", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            if (state.categoryBudgets.isEmpty()) {
                item {
                    JianJiCard {
                        EmptyState(
                            icon = Icons.Filled.Savings,
                            title = "还没有分类预算",
                            subtitle = "为餐饮、交通等常用分类分别设置额度",
                        )
                    }
                }
            } else {
                items(state.categoryBudgets, key = { it.budget.id }) { progress ->
                    val category = state.categories.firstOrNull { it.id == progress.budget.categoryId }
                    JianJiCard {
                        CategoryBudgetRow(
                            progress = progress,
                            categoryName = category?.name ?: "已删除分类",
                            categoryIcon = category?.icon ?: "more_horiz",
                            onDelete = { viewModel.deleteBudget(progress.budget.id) },
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(Dimens.spaceXxl)) }
        }
    }

    // 总预算设置对话框
    if (showTotalDialog) {
        AmountInputDialog(
            title = "设置月度总预算",
            initialValue = state.total?.budget?.limit?.plain().orEmpty(),
            onConfirm = { input ->
                scope.launch {
                    val error = viewModel.saveTotalBudget(input)
                    if (error == null) {
                        showTotalDialog = false
                        errorMessage = null
                    } else {
                        errorMessage = error
                    }
                }
            },
            onDismiss = {
                showTotalDialog = false
                errorMessage = null
            },
            error = errorMessage,
        )
    }

    // 添加分类预算
    if (showAddCategorySheet) {
        ModalBottomSheet(onDismissRequest = { showAddCategorySheet = false }) {
            Column(modifier = Modifier.padding(Dimens.cardPadding)) {
                Text(
                    text = "选择要设置预算的分类",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = Dimens.spaceM),
                )
                state.availableCategories.forEach { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showAddCategorySheet = false
                                pendingCategory = category
                            }
                            .padding(Dimens.spaceM),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CategoryAvatar(
                            iconKey = category.icon,
                            type = category.type,
                            size = 36.dp,
                        )
                        Spacer(Modifier.width(Dimens.spaceM))
                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Spacer(Modifier.height(Dimens.spaceXl))
            }
        }
    }

    // 分类预算金额对话框
    pendingCategory?.let { category ->
        AmountInputDialog(
            title = "「${category.name}」月度预算",
            initialValue = "",
            onConfirm = { input ->
                scope.launch {
                    val error = viewModel.saveCategoryBudget(category.id, input)
                    if (error == null) {
                        pendingCategory = null
                        errorMessage = null
                    } else {
                        errorMessage = error
                    }
                }
            },
            onDismiss = {
                pendingCategory = null
                errorMessage = null
            },
            error = errorMessage,
        )
    }
}

@Composable
private fun TotalBudgetCard(
    progress: BudgetProgress,
    monthLabel: String,
    onEdit: () -> Unit,
) {
    val semantic = LocalSemanticColors.current
    val exceeded = progress.isExceeded
    JianJiCard {
        Column(modifier = Modifier.padding(Dimens.cardPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (exceeded) "已超支" else "本月剩余可用",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onEdit) {
                    Text("调整", color = MaterialTheme.colorScheme.primary)
                }
            }
            Text(
                text = progress.remaining.abs().plain(),
                style = MaterialTheme.typography.displayMedium,
                color = if (exceeded) semantic.expense else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Dimens.spaceL))
            ProgressBar(permille = progress.usedPermille, height = 10.dp)
            Spacer(Modifier.height(Dimens.spaceM))
            Row(modifier = Modifier.fillMaxWidth()) {
                BudgetCell("预算额度", progress.budget.limit.plain(), Modifier.weight(1f))
                BudgetCell("已使用", progress.used.plain(), Modifier.weight(1f))
                BudgetCell(
                    label = if (exceeded) "超出" else "剩余",
                    value = progress.remaining.plain(),
                    modifier = Modifier.weight(1f),
                    highlight = exceeded,
                )
            }
            if (exceeded) {
                Spacer(Modifier.height(Dimens.spaceM))
                Text(
                    text = "$monthLabel 支出已超出预算 ${progress.remaining.abs().plain()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = semantic.expense,
                )
            }
        }
    }
}

@Composable
private fun BudgetCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = AmountTextStyle,
            color = if (highlight) {
                LocalSemanticColors.current.expense
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun CategoryBudgetRow(
    progress: BudgetProgress,
    categoryName: String,
    categoryIcon: String,
    onDelete: () -> Unit,
) {
    val semantic = LocalSemanticColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryAvatar(
            iconKey = categoryIcon,
            type = com.jianji.app.domain.model.CategoryType.EXPENSE,
        )
        Spacer(Modifier.width(Dimens.spaceM))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = categoryName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (progress.isExceeded) {
                        "超支 ${progress.remaining.abs().plain()}"
                    } else {
                        "剩余 ${progress.remaining.plain()}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (progress.isExceeded) semantic.expense else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            ProgressBar(permille = progress.usedPermille, height = 6.dp)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "已用 ${progress.used.plain()} / ${progress.budget.limit.plain()}（${percentText(progress.used.minor, progress.budget.limit.minor)}）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.DeleteOutline,
                contentDescription = "删除该预算",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 金额输入对话框（预算设置共用）。 */
@Composable
private fun AmountInputDialog(
    title: String,
    initialValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    error: String?,
) {
    var input by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = Dimens.spaceM, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "¥",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(Dimens.spaceS))
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (input.isEmpty()) {
                            Text(
                                text = "0.00",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        it()
                    }
                }
                if (error != null) {
                    Spacer(Modifier.height(Dimens.spaceS))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalSemanticColors.current.expense,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(input) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
