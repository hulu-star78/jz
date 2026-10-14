package com.jianji.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jianji.app.di.AppContainer
import com.jianji.app.ui.add.AddEditTransactionScreen
import com.jianji.app.ui.budget.BudgetScreen
import com.jianji.app.ui.components.BottomBarSurface
import com.jianji.app.ui.components.JianJiBottomBar
import com.jianji.app.ui.components.TopLevelDestination
import com.jianji.app.ui.home.HomeScreen
import com.jianji.app.ui.manage.AccountManageScreen
import com.jianji.app.ui.manage.CategoryManageScreen
import com.jianji.app.ui.navigation.Routes
import com.jianji.app.ui.profile.ProfileScreen
import com.jianji.app.ui.records.RecordsScreen
import com.jianji.app.ui.statistics.StatisticsScreen

/** 全局依赖容器（由 MainActivity 提供）。 */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("LocalAppContainer 未提供")
}

/**
 * 应用骨架：底部导航（仅顶层页面显示）+ 页面容器。
 * 记一笔为全屏二级页，不带底部导航。
 */
@Composable
fun JianJiApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val topLevel = TopLevelDestination.entries.firstOrNull { it.route == currentRoute }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AnimatedVisibility(
                visible = topLevel != null,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                BottomBarSurface {
                    JianJiBottomBar(
                        current = topLevel,
                        onDestinationClick = { destination ->
                            navController.navigateToTopLevel(destination)
                        },
                        onAddClick = { navController.navigate(Routes.addEdit()) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background,
        ) {
            JianJiNavHost(navController = navController)
        }
    }
}

@Composable
private fun JianJiNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onAddClick = { navController.navigate(Routes.addEdit()) },
                onSeeAllBills = { navController.navigateToTopLevel(TopLevelDestination.Records) },
                onBudgetClick = { navController.navigate(Routes.BUDGET) },
                onTransactionClick = { id ->
                    navController.navigate(Routes.addEdit(id))
                },
            )
        }
        composable(Routes.RECORDS) {
            RecordsScreen(
                onAddClick = { navController.navigate(Routes.addEdit()) },
                onTransactionClick = { id ->
                    navController.navigate(Routes.addEdit(id))
                },
            )
        }
        composable(Routes.STATISTICS) {
            StatisticsScreen()
        }
        composable(Routes.PROFILE) {
            ProfileScreen(
                onBudgetClick = { navController.navigate(Routes.BUDGET) },
                onCategoryManageClick = { navController.navigate(Routes.CATEGORY_MANAGE) },
                onAccountManageClick = { navController.navigate(Routes.ACCOUNT_MANAGE) },
            )
        }
        composable(Routes.ADD_EDIT) { backStackEntry ->
            val idArg = backStackEntry.arguments?.getString("transactionId")?.toLongOrNull()
            AddEditTransactionScreen(
                transactionId = idArg,
                onClose = { navController.popBackStack() },
            )
        }
        composable(Routes.CATEGORY_MANAGE) {
            CategoryManageScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ACCOUNT_MANAGE) {
            AccountManageScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.BUDGET) {
            BudgetScreen(onBack = { navController.popBackStack() })
        }
    }
}

/** 顶层 Tab 切换：单例 + 恢复状态。 */
private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
