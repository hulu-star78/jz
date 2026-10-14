package com.jianji.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ChildFriendly
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CurrencyYuan
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jianji.app.domain.model.CategoryType
import com.jianji.app.ui.theme.Dimens
import com.jianji.app.ui.theme.LocalSemanticColors

/**
 * 图标键名 → Material 图标映射。
 *
 * 数据库只存字符串键名（如 "restaurant"），UI 层在此集中映射，
 * 新增分类时优先从本表选取键名；未收录的键名回退为 Category 图标。
 * 这样更换图标库或调整样式只需改这一处。
 */
val CategoryIcons: Map<String, ImageVector> = mapOf(
    // 餐饮
    "restaurant" to Icons.Filled.Restaurant,
    "fastfood" to Icons.Filled.Fastfood,
    "local_cafe" to Icons.Filled.LocalCafe,
    "cake" to Icons.Filled.Cake,
    // 交通
    "directions_bus" to Icons.Filled.DirectionsBus,
    "directions_car" to Icons.Filled.DirectionsCar,
    "train" to Icons.Filled.Train,
    "flight" to Icons.Filled.Flight,
    // 购物
    "shopping_bag" to Icons.Filled.ShoppingBag,
    "local_mall" to Icons.Filled.LocalMall,
    "checkroom" to Icons.Filled.Checkroom,
    // 居住
    "home" to Icons.Filled.Home,
    "bolt" to Icons.Filled.Bolt,
    "water_drop" to Icons.Filled.WaterDrop,
    "wifi" to Icons.Filled.Wifi,
    // 娱乐
    "sports_esports" to Icons.Filled.SportsEsports,
    "movie" to Icons.Filled.Movie,
    "music_note" to Icons.Filled.MusicNote,
    "subscriptions" to Icons.Filled.Subscriptions,
    // 医疗
    "local_hospital" to Icons.Filled.LocalHospital,
    "spa" to Icons.Filled.Spa,
    // 教育
    "school" to Icons.Filled.School,
    "brush" to Icons.Filled.Brush,
    // 通讯
    "phone_iphone" to Icons.Filled.PhoneIphone,
    // 人情
    "card_giftcard" to Icons.Filled.CardGiftcard,
    "pets" to Icons.Filled.Pets,
    "child_friendly" to Icons.Filled.ChildFriendly,
    // 其他
    "more_horiz" to Icons.Filled.Category,
    "swap_horiz" to Icons.Filled.SwapHoriz,
    // 收入
    "payments" to Icons.Filled.Payments,
    "emoji_events" to Icons.Filled.EmojiEvents,
    "trending_up" to Icons.Filled.TrendingUp,
    "work" to Icons.Filled.Work,
    "attach_money" to Icons.Filled.AttachMoney,
    // 账户
    "account_balance" to Icons.Filled.AccountBalance,
    "account_balance_wallet" to Icons.Filled.AccountBalanceWallet,
    "chat" to Icons.Filled.Chat,
    "currency_yuan" to Icons.Filled.CurrencyYuan,
    "credit_card" to Icons.Filled.CreditCard,
    "savings" to Icons.Filled.Savings,
)

/** 分类/账户编辑时可选的图标键名（按分组排序，与映射表一致）。 */
val SelectableIconKeys: List<String> = CategoryIcons.keys.sorted()

fun iconVector(key: String): ImageVector = CategoryIcons[key] ?: Icons.Filled.Category

/**
 * 分类头像：圆角方块 + 类型语义色。
 *
 * 支出用主色浅底，收入用收入色浅底；选中态加深底色以示区分。
 */
@Composable
fun CategoryAvatar(
    iconKey: String,
    type: CategoryType?,
    modifier: Modifier = Modifier,
    size: Dp = Dimens.categoryAvatar,
    selected: Boolean = false,
) {
    val semantic = LocalSemanticColors.current
    val (bg, fg) = when (type) {
        CategoryType.INCOME -> {
            val c = if (selected) semantic.income.copy(alpha = 0.22f) else semantic.income.copy(alpha = 0.12f)
            c to semantic.income
        }
        CategoryType.EXPENSE -> {
            val c = if (selected) semantic.income.copy(alpha = 0.10f) else semantic.income.copy(alpha = 0.06f)
            c to semantic.income
        }
        null -> Color.Transparent to semantic.income
    }
    val container = if (type == null) semantic.income.copy(alpha = 0.10f) else bg
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3))
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = iconVector(iconKey),
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}
