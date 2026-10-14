package com.jianji.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 圆角规范：卡片大圆角、控件中圆角、芯片/标签小圆角。 */
val JianJiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

object Dimens {
    // 间距（4dp 网格）
    val spaceXs: Dp = 4.dp
    val spaceS: Dp = 8.dp
    val spaceM: Dp = 12.dp
    val spaceL: Dp = 16.dp
    val spaceXl: Dp = 20.dp
    val spaceXxl: Dp = 24.dp
    val spaceXxxl: Dp = 32.dp

    /** 页面水平边距 */
    val screenPadding: Dp = 20.dp

    /** 卡片内边距 */
    val cardPadding: Dp = 16.dp

    /** 底部导航高度 */
    val bottomBarHeight: Dp = 64.dp

    /** 最小触控区域（无障碍要求 48dp） */
    val minTouchTarget: Dp = 48.dp

    /** 分类头像尺寸 */
    val categoryAvatar: Dp = 40.dp
    val categoryAvatarLarge: Dp = 48.dp

    /** 分隔线粗细 */
    val divider: Dp = 0.5.dp
}
