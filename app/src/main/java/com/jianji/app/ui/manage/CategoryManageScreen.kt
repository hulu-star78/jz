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
import androidx.compose.material.icons.filled.Category
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
import com.jianji.app.domain.model.CategoryType
import com.jianji.app.ui.LocalAppContainer
import com.jianji.app.ui.appViewModelFactory
import com.jianji.app.ui.components.CategoryAvatar
import com.jianji.app.ui.components.EmptyState
import com.jianji.app.ui.components.JianJiCard
import com.jianji.app.ui.components.JianJiTopBar
import com.jianji.app.ui.components.LoadingState
import com.jianji.app.ui.components.SelectableIconKeys
import com.jianji.app.ui.components.TagChip
import com.jianji.app.ui.components.iconVector
import com.jianji.app.ui.theme.Dimens
import kotlinx.coroutines.launch

/**
 * 分类管理：支出/收入分组，支持新增与归档（软删除）。
 * 系统预置分类不可删除；归档后历史账目仍可正常显示名称。
 */
@Composable
fun CategoryManageScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: CategoryManageViewModel = viewModel(
        factory = appViewModelFactory {
            CategoryManageViewModel(categoryRepository = it.categoryRepository)
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddDialog by remember { mutableStateOf(false) }
    var pendingArchive by remember { mutableStateOf<com.jianji.app.domain.model.Category?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        JianJiTopBar(
            title = "分类管理",
            onBack = onBack,
            actions = {
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "新增分类")
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
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
                    TagChip(
                        text = "支出",
                        selected = state.type == CategoryType.EXPENSE,
                        onClick = { viewModel.onTypeChange(CategoryType.EXPENSE) },
                    )
                    TagChip(
                        text = "收入",
                        selected = state.type == CategoryType.INCOME,
                        onClick = { viewModel.onTypeChange(CategoryType.INCOME) },
                    )
                }
            }

            if (state.categories.isEmpty()) {
                item {
                    JianJiCard {
                        EmptyState(
                            icon = Icons.Filled.Category,
                            title = "暂无分类",
                            subtitle = "点击右上角 + 新建分类",
                        )
                    }
                }
            } else {
                items(state.categories, key = { it.id }) { category ->
                    JianJiCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Dimens.cardPadding),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CategoryAvatar(iconKey = category.icon, type = category.type)
                            Spacer(Modifier.width(Dimens.spaceM))
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            if (category.isSystem) {
                                Text(
                                    text = "预置",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                IconButton(onClick = { pendingArchive = category }) {
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
        AddCategoryDialog(
            onConfirm = { name, icon ->
                scope.launch {
                    val error = viewModel.createCategory(name, icon)
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

    pendingArchive?.let { category ->
        AlertDialog(
            onDismissRequest = { pendingArchive = null },
            title = { Text("归档「${category.name}」？") },
            text = { Text("归档后该分类不再显示在新账目中，历史账目不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.archiveCategory(category)
                    pendingArchive = null
                }) { Text("归档") }
            },
            dismissButton = {
                TextButton(onClick = { pendingArchive = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun AddCategoryDialog(
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
    error: String?,
) {
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf(SelectableIconKeys.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增分类") },
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
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (name.isEmpty()) {
                            Text(
                                text = "分类名称（如：宠物）",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        it()
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
                                .size(44.dp)
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
                                modifier = Modifier.size(22.dp),
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
                onClick = { onConfirm(name, icon) },
                enabled = name.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
