package com.jianji.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** 沿 ContextWrapper 链查找宿主 Activity（用于设置系统栏外观）。 */
private fun findActivity(context: android.content.Context): android.app.Activity? {
    var ctx: android.content.Context? = context
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * 语义色：收入/支出等不随 Material ColorScheme 演进的语义角色，
 * 通过 CompositionLocal 下发，浅色/深色各一套。
 */
@Immutable
data class SemanticColors(
    val income: Color,
    val incomeContainer: Color,
    val expense: Color,
    val expenseContainer: Color,
    val tertiaryText: Color,
    val warning: Color,
    val divider: Color,
)

private val LightSemanticColors = SemanticColors(
    income = IncomeGreen,
    incomeContainer = IncomeGreenContainer,
    expense = ExpenseRed,
    expenseContainer = ExpenseRedContainer,
    tertiaryText = TertiaryText,
    warning = WarningAmber,
    divider = OutlineVariantWarm,
)

private val DarkSemanticColors = SemanticColors(
    income = IncomeGreenDark,
    incomeContainer = IncomeGreenContainerDark,
    expense = ExpenseRedDark,
    expenseContainer = ExpenseRedContainerDark,
    tertiaryText = TertiaryTextDark,
    warning = WarningAmberDark,
    divider = OutlineVariantDark,
)

val LocalSemanticColors = staticCompositionLocalOf { LightSemanticColors }

private val LightScheme = lightColorScheme(
    primary = PrimaryGreen,
    onPrimary = OnPrimaryGreen,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = SecondaryGreen,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    background = WarmWhite,
    onBackground = OnSurfaceWarm,
    surface = SurfaceWhite,
    onSurface = OnSurfaceWarm,
    surfaceVariant = SurfaceVariantWarm,
    onSurfaceVariant = OnSurfaceVariantWarm,
    outline = OutlineWarm,
    outlineVariant = OutlineVariantWarm,
)

private val DarkScheme = darkColorScheme(
    primary = PrimaryGreenDark,
    onPrimary = OnPrimaryGreenDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
)

/**
 * 「简记」主题。
 *
 * @param darkTheme 是否深色（由设置决定，默认跟随系统）
 */
@Composable
fun JianJiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    val semantic = if (darkTheme) DarkSemanticColors else LightSemanticColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        findActivity(view.context)?.window?.let { window ->
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }
    CompositionLocalProvider(LocalSemanticColors provides semantic) {
        MaterialTheme(
            colorScheme = scheme,
            typography = JianJiTypography,
            shapes = JianJiShapes,
            content = content,
        )
    }
}
