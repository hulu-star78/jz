package com.jianji.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jianji.app.ui.theme.Dimens

/**
 * 记账专用数字键盘（不唤起系统输入法，避免遮挡与键盘切换干扰）。
 *
 * 4 列 × 4 行对称网格：
 * ```
 * 1  2  3  ⌫
 * 4  5  6  清空
 * 7  8  9  .
 * 00 0  保存 ⌄
 * ```
 * - 数字键：灰底；保存键：主色强调；⌫/清空/./00/⌄：无底色弱化样式
 * - 「⌄」收起键盘（点击金额区可唤回）；「保存」与右上角保存调用同一逻辑
 * - 输入规则（两位小数、位数上限）由调用方处理，本组件只转发按键事件。
 */
@Composable
fun NumberKeypad(
    onKey: (KeypadKey) -> Unit,
    onHide: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = listOf(
        listOf(KeypadKey.Digit1, KeypadKey.Digit2, KeypadKey.Digit3, KeypadKey.Backspace),
        listOf(KeypadKey.Digit4, KeypadKey.Digit5, KeypadKey.Digit6, KeypadKey.Clear),
        listOf(KeypadKey.Digit7, KeypadKey.Digit8, KeypadKey.Digit9, KeypadKey.Dot),
        listOf(KeypadKey.DoubleZero, KeypadKey.Digit0, KeypadKey.Save, KeypadKey.Hide),
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spaceXs),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceS),
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS),
            ) {
                row.forEach { key ->
                    KeypadButton(
                        key = key,
                        onClick = {
                            when (key) {
                                KeypadKey.Hide -> onHide()
                                KeypadKey.Save -> onSave()
                                else -> onKey(key)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

sealed interface KeypadKey {
    data class Digit(val value: Int) : KeypadKey
    data object Dot : KeypadKey
    data object Backspace : KeypadKey

    /** 收起键盘（隐藏键）。 */
    data object Hide : KeypadKey

    /** 一次输入两个 0。 */
    data object DoubleZero : KeypadKey

    /** 清空已输入金额。 */
    data object Clear : KeypadKey

    /** 保存（与右上角保存同一逻辑）。 */
    data object Save : KeypadKey

    companion object {
        val Digit0 = Digit(0)
        val Digit1 = Digit(1)
        val Digit2 = Digit(2)
        val Digit3 = Digit(3)
        val Digit4 = Digit(4)
        val Digit5 = Digit(5)
        val Digit6 = Digit(6)
        val Digit7 = Digit(7)
        val Digit8 = Digit(8)
        val Digit9 = Digit(9)
    }
}

/** 键盘内「保存」键的测试标签（与右上角保存区分）。 */
const val SAVE_KEYPAD_TAG: String = "save_keypad"

@Composable
private fun KeypadButton(
    key: KeypadKey,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSave = key == KeypadKey.Save
    val isDigit = key is KeypadKey.Digit || key == KeypadKey.Dot
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    isSave -> MaterialTheme.colorScheme.primary
                    isDigit -> MaterialTheme.colorScheme.surfaceVariant
                    else -> Color.Transparent
                },
            )
            .then(if (isSave) Modifier.testTag(SAVE_KEYPAD_TAG) else Modifier)
            .semantics(mergeDescendants = true) {}
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        when (key) {
            is KeypadKey.Digit -> Text(
                text = key.value.toString(),
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            KeypadKey.Dot -> Text(
                text = ".",
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            KeypadKey.Backspace -> androidx.compose.material3.Icon(
                imageVector = Icons.Filled.Backspace,
                contentDescription = "退格",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(6.dp)
                    .size(24.dp),
            )
            KeypadKey.Hide -> androidx.compose.material3.Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "收起键盘",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .padding(6.dp)
                    .size(24.dp),
            )
            KeypadKey.Clear -> Text(
                text = "清空",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            KeypadKey.DoubleZero -> Text(
                text = "00",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            KeypadKey.Save -> Text(
                text = "保存",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

/** 键盘与内容之间的占位。 */
@Composable
fun KeypadSpacer() = Spacer(Modifier.height(Dimens.spaceM))
