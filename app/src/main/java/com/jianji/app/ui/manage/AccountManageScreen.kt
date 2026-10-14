package com.jianji.app.ui.manage

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jianji.app.domain.model.AccountType
import com.jianji.app.domain.model.Money
import com.jianji.app.ui.LocalAppContainer
import com.jianji.app.ui.appViewModelFactory
import com.jianji.app.ui.components.CategoryAvatar
import com.jianji.app.ui.components.EmptyState
import com.jianji.app.ui.components.JianJiCard
import com.jianji.app.ui.components.JianJiTopBar
import com.jianji.app.ui.components.LoadingState
import com.jianji.app.ui.components.SelectableIconKeys
import com.jianji.app.ui.components.iconVector
import com.jianji.app.ui.theme.AmountTextStyle
import com.jianji.app.ui.theme.Dimens
import com.jianji.app.ui.util.message
import com.jianji.app.ui.util.plain
import kotlinx.coroutines.launch

/**
 * 账户管理：账户列表（含实时余额）、新增与归档。
 * 余额由交易数据实时计算，编辑初始余额会同步影响所有统计。
 */
@Composable
fun AccountManageScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: AccountManageViewModel = viewModel(
        factory = appViewModelFactory {
            AccountManageViewModel(accountRepository = it.accountRepository)
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddDialog by remember { mutableStateOf(false) }
    var pendingArchive by remember { mutableStateOf<AccountRow?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        JianJiTopBar(
            title = "账户管理",
            onBack = onBack,
            actions = {
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "新增账户")
                }
            },
        )

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
            if (state.accounts.isEmpty()) {
                item {
                    JianJiCard {
                        EmptyState(
                            icon = Icons.Filled.Wallet,
                            title = "暂无账户",
                            subtitle = "点击右上角 + 新建账户",
                        )
                    }
                }
            } else {
                items(state.accounts, key = { it.id }) { account ->
                    JianJiCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Dimens.cardPadding),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CategoryAvatar(iconKey = account.icon, type = null)
                            Spacer(Modifier.width(Dimens.spaceM))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = account.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = accountTypeLabel(account.type) +
                                        if (account.isArchived) " · 已归档" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = account.balance.plain(),
                                style = AmountTextStyle,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (!account.isArchived) {
                                IconButton(onClick = { pendingArchive = account }) {
                                    Icon(
                                        Icons.Filled.Archive,
                                        contentDescription = "归档",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(Dimens.spaceXxl)) }
        }
    }

    SnackbarHost(hostState = snackbarHostState, modifier = Modifier.fillMaxSize())

    if (showAddDialog) {
        AddAccountDialog(
            onConfirm = { name, icon, type, balance ->
                scope.launch {
                    val minor = Money.parse(balance)?.minor ?: -1L
                    val error = if (minor < 0) {
                        "请输入有效的初始余额"
                    } else {
                        viewModel.createAccount(name, icon, type, minor)
                    }
                    if (error == null) {
                        showAddDialog = false
                        errorMessage = null
                    } else {
                        errorMessage = error
                    }
                }
            },
            onDismiss = {
                showAddDialog = false
                errorMessage = null
            },
            error = errorMessage,
        )
    }

    pendingArchive?.let { account ->
        AlertDialog(
            onDismissRequest = { pendingArchive = null },
            title = { Text("归档「${account.name}」？") },
            text = { Text("归档后不再出现在新账目的账户选择中，历史账目不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.archiveAccount(account.id)
                    pendingArchive = null
                }) { Text("归档") }
            },
            dismissButton = {
                TextButton(onClick = { pendingArchive = null }) { Text("取消") }
            },
        )
    }
}

private fun accountTypeLabel(type: AccountType): String = when (type) {
    AccountType.CASH -> "现金"
    AccountType.BANK -> "银行卡"
    AccountType.E_WALLET -> "电子钱包"
    AccountType.CREDIT_CARD -> "信用卡"
    AccountType.OTHER -> "其他"
}

@Composable
private fun AddAccountDialog(
    onConfirm: (String, String, AccountType, String) -> Unit,
    onDismiss: () -> Unit,
    error: String?,
) {
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("account_balance") }
    var type by remember { mutableStateOf(AccountType.BANK) }
    var balance by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增账户") },
        text = {
            Column {
                DialogInput(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "账户名称（如：招商银行）",
                )
                Spacer(Modifier.height(Dimens.spaceM))
                DialogInput(
                    value = balance,
                    onValueChange = { balance = it },
                    placeholder = "初始余额（可留空，如 1000.00）",
                )
                Spacer(Modifier.height(Dimens.spaceM))
                Text(
                    text = "账户类型",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Dimens.spaceS))
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
                    AccountType.entries.forEach { option ->
                        com.jianji.app.ui.components.TagChip(
                            text = accountTypeLabel(option),
                            selected = type == option,
                            onClick = { type = option },
                        )
                    }
                }
                Spacer(Modifier.height(Dimens.spaceM))
                Text(
                    text = "选择图标",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Dimens.spaceS))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
                    items(SelectableIconKeys, key = { it }) { key ->
                        val selected = key == icon
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                )
                                .clickable { icon = key },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = iconVector(key),
                                contentDescription = key,
                                tint = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                if (error != null) {
                    Spacer(Modifier.height(Dimens.spaceM))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, icon, type, balance) },
                enabled = name.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun DialogInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = Dimens.spaceM, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            it()
        }
    }
}
