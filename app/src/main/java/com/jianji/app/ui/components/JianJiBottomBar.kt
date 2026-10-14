package com.jianji.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.jianji.app.ui.theme.Dimens

/** 顶层导航目标。 */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
) {
    Home("home", "首页", Icons.Filled.Home, Icons.Outlined.Home),
    Records("records", "明细", Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong),
    Statistics("statistics", "统计", Icons.Filled.PieChart, Icons.Outlined.PieChart),
    Profile("profile", "我的", Icons.Filled.Person, Icons.Outlined.Person),
}

/**
 * 底部导航：4 个 Tab + 中央记账悬浮按钮。
 * 选中态用主色，未选中用中性色；所有触控区 ≥ 48dp。
 */
@Composable
fun JianJiBottomBar(
    current: TopLevelDestination?,
    onDestinationClick: (TopLevelDestination) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = TopLevelDestination.entries
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .height(Dimens.bottomBarHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.take(2).forEach { destination ->
            BottomBarItem(
                destination = destination,
                selected = current == destination,
                onClick = { onDestinationClick(destination) },
                modifier = Modifier.weight(1f),
            )
        }
        // 中央记账按钮（无涟漪，避免与大圆角冲突）
        Box(
            modifier = Modifier
                .weight(1f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onAddClick() },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "记一笔",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        items.drop(2).forEach { destination ->
            BottomBarItem(
                destination = destination,
                selected = current == destination,
                onClick = { onDestinationClick(destination) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BottomBarItem(
    destination: TopLevelDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = modifier
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (selected) destination.selectedIcon else destination.icon,
            contentDescription = destination.label,
            tint = color,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = destination.label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

/** 顶部圆角与分割线，让底栏与内容区平滑过渡。 */
@Composable
fun BottomBarSurface(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        content()
    }
}
