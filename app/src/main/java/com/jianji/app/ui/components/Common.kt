package com.jianji.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jianji.app.domain.model.Money
import com.jianji.app.domain.model.TransactionType
import com.jianji.app.ui.theme.AmountTextStyle
import com.jianji.app.ui.theme.Dimens
import com.jianji.app.ui.theme.LocalSemanticColors
import com.jianji.app.ui.util.plain
import com.jianji.app.ui.util.signed

/**
 * 标准卡片：暖白表面 + 细边框 + 大圆角。
 * 刻意不使用重阴影，层级靠颜色与留白表达。
 */
@Composable
fun JianJiCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val base = modifier
        .fillMaxWidth()
        .clip(MaterialTheme.shapes.large)
        .background(color)
        .border(
            width = Dimens.divider,
            color = MaterialTheme.colorScheme.outlineVariant,
            shape = MaterialTheme.shapes.large,
        )
    if (onClick != null) {
        Box(modifier = base.clickable { onClick() }) { content() }
    } else {
        Box(modifier = base) { content() }
    }
}

/** 区块标题：可选右侧操作。 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.spaceS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (actionText != null && onActionClick != null) {
            TextButton(onClick = onActionClick) {
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** 金额文本：按交易类型着色（收入绿/支出红/转账主色）。 */
@Composable
fun AmountText(
    money: Money,
    type: TransactionType,
    modifier: Modifier = Modifier,
    showSign: Boolean = true,
    style: TextStyle = AmountTextStyle,
) {
    val semantic = LocalSemanticColors.current
    val color = when (type) {
        TransactionType.INCOME -> semantic.income
        TransactionType.EXPENSE -> semantic.expense
        TransactionType.TRANSFER -> MaterialTheme.colorScheme.onSurface
    }
    Text(
        text = if (showSign) money.signed(type) else money.plain(),
        style = style,
        color = color,
        modifier = modifier,
    )
}

/** 月份切换器：‹ 2026年10月 ›。 */
@Composable
fun MonthSelector(
    label: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    nextEnabled: Boolean = true,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(Dimens.minTouchTarget)) {
            Icon(
                imageVector = Icons.Filled.ChevronLeft,
                contentDescription = "上一个月",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(120.dp),
        )
        IconButton(
            onClick = onNext,
            enabled = nextEnabled,
            modifier = Modifier.size(Dimens.minTouchTarget),
        ) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "下一个月",
                tint = if (nextEnabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
            )
        }
    }
}

/** 通用空状态。 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.spaceXxxl, horizontal = Dimens.spaceXl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(Dimens.spaceL))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(Dimens.spaceXs))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(Dimens.spaceM))
            TextButton(onClick = onAction) {
                Text(actionText, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** 页面级加载中。 */
@Composable
fun LoadingState(modifier: Modifier = Modifier, text: String = "加载中") {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.spaceXxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(Dimens.spaceM))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 页面级错误状态（带重试）。 */
@Composable
fun ErrorState(
    message: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.spaceXxxl, horizontal = Dimens.spaceXl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(Dimens.spaceM))
            TextButton(onClick = onRetry) {
                Text("重试", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/**
 * 进度条（预算/占比）。
 * 使用整数 permille 驱动，避免浮点；超过 100% 时颜色切换为提醒色。
 */
@Composable
fun ProgressBar(
    permille: Int,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    exceededColor: Color = LocalSemanticColors.current.expense,
    normalColor: Color = MaterialTheme.colorScheme.primary,
) {
    val target = (permille.coerceIn(0, 1000)) / 1000f
    val progress by animateFloatAsState(targetValue = target, label = "progress")
    val color = if (permille > 1000) exceededColor else normalColor
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .fillMaxSize()
                .clip(CircleShape)
                .background(color),
        )
    }
}

/** 小型标签芯片（筛选、类型标记）。 */
@Composable
fun TagChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val bg = if (selected) color.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = Dimens.spaceM, vertical = 7.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

/** 设置列表行。 */
@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailingText: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = Dimens.cardPadding, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(Dimens.spaceM))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailingText != null) {
            Text(
                text = trailingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 页面顶部简单标题栏（返回 + 标题 + 右侧动作）。 */
@Composable
fun JianJiTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.minTouchTarget + 8.dp)
            .padding(horizontal = Dimens.spaceS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ChevronLeft,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        } else {
            Spacer(Modifier.width(Dimens.spaceS))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (onBack != null) 0.dp else Dimens.spaceS),
        )
        actions()
    }
}

/** 账单空状态图标。 */
val BillsEmptyIcon: ImageVector = Icons.Filled.Inbox
