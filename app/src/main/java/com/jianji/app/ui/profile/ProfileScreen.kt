package com.jianji.app.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jianji.app.data.backup.BackupCounts
import com.jianji.app.data.settings.ThemeMode
import com.jianji.app.ui.LocalAppContainer
import com.jianji.app.ui.appViewModelFactory
import com.jianji.app.ui.components.JianJiCard
import com.jianji.app.ui.components.SettingRow
import com.jianji.app.ui.theme.Dimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 待确认的恢复：备份 JSON + 内容统计。 */
private data class PendingRestore(val json: String, val counts: BackupCounts)

/**
 * 我的 / 设置页：分组列表 + 主题切换（真实生效）+ 数据导出/备份/恢复。
 * 所有文件操作通过系统 SAF 由用户选择位置，数据不经过网络。
 */
@Composable
fun ProfileScreen(
    onBudgetClick: () -> Unit,
    onCategoryManageClick: () -> Unit,
    onAccountManageClick: () -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: ProfileViewModel = viewModel(
        factory = appViewModelFactory {
            ProfileViewModel(
                backupRepository = it.backupRepository,
                settingsRepository = it.settingsRepository,
            )
        },
    )
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showThemeDialog by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<PendingRestore?>(null) }

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    // 导出：用户选择保存位置后写入 JSON
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val json = viewModel.exportJson()
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("无法打开所选文件")
                }
            }
            busy = false
            showMessage(
                result.fold(
                    onSuccess = { "已导出备份文件" },
                    onFailure = { "导出失败：${it.message ?: "未知错误"}" },
                ),
            )
        }
    }

    // 恢复：读取所选文件 → 校验 → 确认对话框 → 恢复
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val validation = withContext(Dispatchers.IO) {
                runCatching {
                    val json = context.contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    } ?: error("无法打开所选文件")
                    json to viewModel.validateBackup(json)
                }
            }
            busy = false
            validation.fold(
                onSuccess = { (json, result) ->
                    result.fold(
                        onSuccess = { counts -> pendingRestore = PendingRestore(json, counts) },
                        onFailure = { showMessage(it.message ?: "备份文件无效") },
                    )
                },
                onFailure = { showMessage("读取失败：${it.message ?: "未知错误"}") },
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.screenPadding),
    ) {
        Spacer(Modifier.height(Dimens.spaceL))

        // 应用信息卡
        JianJiCard {
            Row(
                modifier = Modifier.padding(Dimens.cardPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "记",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Spacer(Modifier.width(Dimens.spaceM))
                Column {
                    Text(
                        text = "简记",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "本地记账 · 数据仅保存在本机",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(Dimens.spaceL))
        SettingsGroupTitle("记账管理")
        JianJiCard {
            Column {
                SettingRow(
                    icon = Icons.Filled.Savings,
                    title = "预算管理",
                    subtitle = "月度总预算与分类预算",
                    onClick = onBudgetClick,
                )
                SettingRow(
                    icon = Icons.Filled.Category,
                    title = "分类管理",
                    subtitle = "自定义收入与支出分类",
                    onClick = onCategoryManageClick,
                )
                SettingRow(
                    icon = Icons.Filled.Wallet,
                    title = "账户管理",
                    subtitle = "现金、银行卡、电子钱包",
                    onClick = onAccountManageClick,
                )
            }
        }

        Spacer(Modifier.height(Dimens.spaceL))
        SettingsGroupTitle("通用")
        JianJiCard {
            Column {
                SettingRow(
                    icon = Icons.Filled.DarkMode,
                    title = "主题",
                    subtitle = "浅色 / 深色 / 跟随系统",
                    trailingText = themeMode.label(),
                    onClick = { showThemeDialog = true },
                )
            }
        }

        Spacer(Modifier.height(Dimens.spaceL))
        SettingsGroupTitle("数据")
        JianJiCard {
            Column {
                SettingRow(
                    icon = Icons.Filled.FileDownload,
                    title = "导出数据",
                    subtitle = "导出为 JSON 文件，保存到所选位置",
                    trailingText = if (busy) "处理中…" else null,
                    onClick = {
                        if (!busy) {
                            exportLauncher.launch(viewModel.suggestedFileName())
                        }
                    },
                )
                SettingRow(
                    icon = Icons.Filled.Restore,
                    title = "恢复数据",
                    subtitle = "从 JSON 备份恢复（将覆盖当前数据）",
                    onClick = {
                        if (!busy) {
                            importLauncher.launch(arrayOf("application/json", "*/*"))
                        }
                    },
                )
            }
        }

        Spacer(Modifier.height(Dimens.spaceL))
        SettingsGroupTitle("关于")
        JianJiCard {
            Column {
                SettingRow(
                    icon = Icons.Filled.Info,
                    title = "版本",
                    trailingText = "0.1.0",
                )
            }
        }

        Spacer(Modifier.height(Dimens.spaceXxxl))
    }

    if (busy) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    }

    SnackbarHost(hostState = snackbarHostState, modifier = Modifier.fillMaxSize())

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("主题") },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                            )
                            Spacer(Modifier.width(Dimens.spaceS))
                            Text(text = mode.label(), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("完成") }
            },
        )
    }

    // 恢复确认对话框：展示备份统计与覆盖风险
    pendingRestore?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("恢复备份？") },
            text = {
                Text(
                    "备份包含：分类 ${pending.counts.categories} 个、账户 ${pending.counts.accounts} 个、" +
                        "账目 ${pending.counts.transactions} 笔、预算 ${pending.counts.budgets} 条。\n\n" +
                        "恢复将覆盖当前全部数据，且不可撤销。建议先导出当前数据。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val json = pending.json
                        pendingRestore = null
                        busy = true
                        scope.launch {
                            val result = viewModel.restore(json)
                            busy = false
                            showMessage(
                                when (result) {
                                    is RestoreResult.Success ->
                                        "已恢复 ${result.counts.transactions} 笔账目"

                                    is RestoreResult.Failure -> result.message
                                },
                            )
                        }
                    },
                ) { Text("覆盖恢复") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SettingsGroupTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Dimens.spaceS, bottom = Dimens.spaceS),
    )
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
}
