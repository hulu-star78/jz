package com.jianji.app.ui.add

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jianji.app.domain.model.Account
import com.jianji.app.domain.model.Category
import com.jianji.app.domain.model.DateUtils
import com.jianji.app.domain.model.TransactionType
import com.jianji.app.ui.LocalAppContainer
import com.jianji.app.ui.appViewModelFactory
import com.jianji.app.ui.components.CategoryAvatar
import com.jianji.app.ui.components.JianJiCard
import com.jianji.app.ui.components.NumberKeypad
import com.jianji.app.ui.components.TagChip
import com.jianji.app.ui.components.iconVector
import com.jianji.app.ui.theme.Dimens
import com.jianji.app.ui.theme.LocalSemanticColors
import com.jianji.app.ui.util.message
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 记一笔 / 编辑账目页。
 *
 * 结构（自上而下）：
 * 1. 顶部：关闭 / 标题 / 删除（编辑态）
 * 2. 收支类型切换（支出 / 收入 / 转账）
 * 3. 金额输入区（页面视觉焦点，光标闪烁提示输入位置）
 * 4. 分类横滑选择 + 「更多」全部分类面板
 * 5. 次级信息卡：日期 / 账户 / 转入账户（转账）/ 备注
 * 6. 保存按钮 + 自定义数字键盘（不唤起系统输入法）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTransactionScreen(
    transactionId: Long?,
    onClose: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: AddEditTransactionViewModel = viewModel(
        key = "addEdit-${transactionId ?: "new"}",
        factory = appViewModelFactory {
            AddEditTransactionViewModel(
                transactionRepository = it.transactionRepository,
                categoryRepository = it.categoryRepository,
                accountRepository = it.accountRepository,
                transactionId = transactionId,
            )
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    var showDatePicker by remember { mutableStateOf(false) }
    var showAccountSheet by remember { mutableStateOf(false) }
    var showToAccountSheet by remember { mutableStateOf(false) }
    var showAllCategories by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // 数字键盘显隐：默认显示；「收起」键隐藏后点击金额区可唤回
    var keypadVisible by remember { mutableStateOf(true) }

    LaunchedEffect(state.saved, state.deleted) {
        if (state.saved || state.deleted) onClose()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // ---------- 顶部栏：关闭 / 标题 / 删除（编辑态）/ 保存 ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spaceS, vertical = Dimens.spaceS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "关闭", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                text = if (state.isEdit) "编辑账目" else "记一笔",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            if (state.isEdit) {
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = "删除",
                        tint = LocalSemanticColors.current.expense,
                    )
                }
            }
            // 保存按钮（右上角）：可保存时主色，否则置灰但仍可点击以提示缺失项
            val saveReady = state.canSave && !state.saving
            TextButton(
                onClick = viewModel::save,
                enabled = !state.saving,
                modifier = Modifier.testTag(SAVE_TOP_TAG),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (saveReady) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Text(
                    text = if (state.saving) "保存中…" else "保存",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        // ---------- 类型切换 ----------
        TypeSelector(
            selected = state.type,
            onSelect = viewModel::onTypeChange,
            modifier = Modifier.padding(horizontal = Dimens.screenPadding),
        )

        // ---------- 金额输入（点击可唤回被收起的键盘） ----------
        AmountDisplay(
            amountInput = state.amountInput,
            onClick = { keypadVisible = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.screenPadding, vertical = Dimens.spaceL),
        )
        if (state.error != null) {
            Text(
                text = state.error!!.message(),
                style = MaterialTheme.typography.bodySmall,
                color = LocalSemanticColors.current.expense,
                modifier = Modifier.padding(horizontal = Dimens.screenPadding),
            )
        }
        if (!keypadVisible) {
            Text(
                text = "点击金额区域唤起数字键盘",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Dimens.screenPadding),
            )
        }

        // ---------- 可滚动内容 ----------
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.screenPadding),
        ) {
            CategorySection(
                categories = state.categories,
                selectedId = state.selectedCategoryId,
                onSelect = viewModel::onCategorySelected,
                onMoreClick = { showAllCategories = true },
            )
            Spacer(Modifier.height(Dimens.spaceM))
            JianJiCard {
                Column(modifier = Modifier.padding(Dimens.cardPadding)) {
                    DetailRow(
                        icon = Icons.Filled.CalendarMonth,
                        label = "日期",
                        value = formatDate(state.date),
                        onClick = { showDatePicker = true },
                    )
                    // 补录快捷：一键选择昨天 / 前天，避免翻日历
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Dimens.spaceS),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS),
                    ) {
                        val today = LocalDate.now()
                        QuickDateChip(
                            label = "昨天",
                            selected = state.date == today.minusDays(1),
                            onClick = { viewModel.onDateChange(today.minusDays(1)) },
                        )
                        QuickDateChip(
                            label = "前天",
                            selected = state.date == today.minusDays(2),
                            onClick = { viewModel.onDateChange(today.minusDays(2)) },
                        )
                    }
                    DetailRow(
                        icon = Icons.Filled.Wallet,
                        label = if (state.type == TransactionType.TRANSFER) "转出账户" else "账户",
                        value = state.selectedAccount?.name ?: "请选择",
                        onClick = { showAccountSheet = true },
                    )
                    if (state.type == TransactionType.TRANSFER) {
                        DetailRow(
                            icon = Icons.Filled.SwapHoriz,
                            label = "转入账户",
                            value = state.selectedToAccount?.name ?: "请选择",
                            onClick = { showToAccountSheet = true },
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Dimens.spaceM),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Notes,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(Dimens.spaceM))
                        BasicTextField(
                            value = state.note,
                            onValueChange = viewModel::onNoteChange,
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (state.note.isEmpty()) {
                                Text(
                                    text = "点此添加备注",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            it()
                        }
                    }
                }
            }
            Spacer(Modifier.height(Dimens.spaceM))
        }

        // ---------- 数字键盘（可收起；含保存键，与右上角保存同一逻辑） ----------
        AnimatedVisibility(
            visible = keypadVisible,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            NumberKeypad(
                onKey = viewModel::onKeypadKey,
                onHide = { keypadVisible = false },
                onSave = viewModel::save,
                modifier = Modifier.padding(top = Dimens.spaceM, bottom = Dimens.spaceM),
            )
        }
    }

    // ---------- 日期选择 ----------
    if (showDatePicker) {
        // 可选范围：1970-01-01 ~ 今天（禁止未来日期）
        val today = LocalDate.now()
        val todayEndUtcMillis = today.plusDays(1).toEpochDay() * 86_400_000L - 1
        val selectableDates = remember(today) {
            object : SelectableDates {
                override fun isSelectableYear(year: Int): Boolean =
                    year in DateUtils.MIN_DATE.year..today.year

                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis <= todayEndUtcMillis
            }
        }
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date.toEpochDay() * 86_400_000L,
            yearRange = IntRange(DateUtils.MIN_DATE.year, today.year),
            selectableDates = selectableDates,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        viewModel.onDateChange(
                            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate(),
                        )
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    // ---------- 账户选择 ----------
    if (showAccountSheet) {
        AccountPickerSheet(
            accounts = state.accounts,
            selectedId = state.accountId,
            onSelect = {
                viewModel.onAccountSelected(it)
                showAccountSheet = false
            },
            onDismiss = { showAccountSheet = false },
        )
    }
    if (showToAccountSheet) {
        AccountPickerSheet(
            accounts = state.accounts.filter { it.id != state.accountId },
            selectedId = state.toAccountId,
            onSelect = {
                viewModel.onToAccountSelected(it)
                showToAccountSheet = false
            },
            onDismiss = { showToAccountSheet = false },
        )
    }

    // ---------- 全部分类 ----------
    if (showAllCategories) {
        ModalBottomSheet(
            onDismissRequest = { showAllCategories = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            Column(modifier = Modifier.padding(Dimens.cardPadding)) {
                Text(
                    text = "选择分类",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = Dimens.spaceM),
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.height(320.dp),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceM),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spaceM),
                ) {
                    gridItems(state.categories, key = { it.id }) { category ->
                        CategoryGridItem(
                            category = category,
                            selected = category.id == state.selectedCategoryId,
                            onClick = {
                                viewModel.onCategorySelected(category.id)
                                showAllCategories = false
                            },
                        )
                    }
                }
            }
        }
    }

    // ---------- 删除确认 ----------
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除这条账目？") },
            text = { Text("删除后无法恢复，相关统计与余额会自动更新。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.delete()
                }) {
                    Text("删除", color = LocalSemanticColors.current.expense)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun TypeSelector(
    selected: TransactionType,
    onSelect: (TransactionType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.TRANSFER)
    val labels = mapOf(
        TransactionType.EXPENSE to "支出",
        TransactionType.INCOME to "收入",
        TransactionType.TRANSFER to "转账",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent,
                    )
                    .clickable { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = labels[option].orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun AmountDisplay(
    amountInput: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), repeatMode = androidx.compose.animation.core.RepeatMode.Reverse),
        label = "cursorAlpha",
    )
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .testTag(AMOUNT_AREA_TAG),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "¥",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp, end = 6.dp),
        )
        Text(
            text = amountInput.ifEmpty { "0.00" },
            style = MaterialTheme.typography.displayLarge,
            color = if (amountInput.isEmpty()) {
                MaterialTheme.colorScheme.outline
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Box(
            modifier = Modifier
                .padding(bottom = 8.dp, start = 2.dp)
                .width(2.dp)
                .height(34.dp)
                .alpha(alpha)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun CategorySection(
    categories: List<Category>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onMoreClick: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "分类",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            TextButton(onClick = onMoreClick) {
                Icon(
                    Icons.Filled.GridView,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("更多", style = MaterialTheme.typography.labelLarge)
            }
        }
        if (categories.isEmpty()) {
            Text(
                text = "暂无可用分类，请先在「我的 → 分类管理」中添加",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceM)) {
                items(categories, key = { it.id }) { category ->
                    CategoryStripItem(
                        category = category,
                        selected = category.id == selectedId,
                        onClick = { onSelect(category.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryStripItem(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(60.dp)
            .clip(RoundedCornerShape(12.dp))
            .semantics(mergeDescendants = true) { this.selected = selected }
            .clickable { onClick() }
            .padding(vertical = Dimens.spaceS),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CategoryAvatar(
            iconKey = category.icon,
            type = category.type,
            selected = selected,
            size = 44.dp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = category.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
    }
}

@Composable
private fun CategoryGridItem(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = Dimens.spaceS),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CategoryAvatar(iconKey = category.icon, type = category.type, selected = selected)
        Spacer(Modifier.height(4.dp))
        Text(
            text = category.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
    }
}

@Composable
private fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = Dimens.spaceM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(Dimens.spaceM))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountPickerSheet(
    accounts: List<Account>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(Dimens.cardPadding)) {
            Text(
                text = "选择账户",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = Dimens.spaceM),
            )
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                items(accounts, key = { it.id }) { account ->
                    val selected = account.id == selectedId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                } else {
                                    androidx.compose.ui.graphics.Color.Transparent
                                },
                            )
                            .clickable { onSelect(account.id) }
                            .padding(Dimens.cardPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CategoryAvatar(
                            iconKey = account.icon,
                            type = null,
                            selected = selected,
                            size = 40.dp,
                        )
                        Spacer(Modifier.width(Dimens.spaceM))
                        Text(
                            text = account.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

/** 日期展示：今天 / 昨天 / 前天 / x月x日（跨年显示年份）。 */
private fun formatDate(date: LocalDate): String {
    val today = LocalDate.now()
    return when {
        date == today -> "今天"
        date == today.minusDays(1) -> "昨天"
        date == today.minusDays(2) -> "前天"
        date.year == today.year -> "${date.monthValue}月${date.dayOfMonth}日"
        else -> "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
    }
}

/** 补录快捷日期按钮（昨天 / 前天）。 */
@Composable
private fun QuickDateChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    TagChip(text = label, selected = selected, onClick = onClick)
}

/** 金额输入区的测试标签（UI 测试用于点击唤回键盘）。 */
internal const val AMOUNT_AREA_TAG: String = "amount_area"

/** 右上角保存按钮的测试标签（与键盘内保存键区分）。 */
internal const val SAVE_TOP_TAG: String = "save_top"
