package com.jianji.app.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import com.jianji.app.di.AppContainer
import com.jianji.app.domain.model.CategoryType
import com.jianji.app.domain.model.TransactionDraft
import com.jianji.app.domain.model.TransactionType
import com.jianji.app.ui.home.HomeScreen
import com.jianji.app.ui.theme.JianJiTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * 首页集成测试：Robolectric + 真实 Room 数据库 + 预置分类/账户。
 * 验证汇总数字与分类概览使用真实交易数据渲染。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            container = AppContainer(context)
            val categories = container.categoryRepository.observeAll().first()
            val accounts = container.accountRepository.observeAccounts().first()
            val food = categories.first { it.name == "餐饮" && it.type == CategoryType.EXPENSE }
            val salary = categories.first { it.name == "工资" && it.type == CategoryType.INCOME }
            val cash = accounts.first { it.name == "现金" }
            val bank = accounts.first { it.name == "银行卡" }

            // 本月收入 1000.00，支出 35.00 => 结余 965.00
            container.transactionRepository.save(
                TransactionDraft(
                    amountMinor = 100000,
                    type = TransactionType.INCOME,
                    date = LocalDate.now(),
                    categoryId = salary.id,
                    accountId = bank.id,
                ),
            )
            container.transactionRepository.save(
                TransactionDraft(
                    amountMinor = 3500,
                    type = TransactionType.EXPENSE,
                    date = LocalDate.now(),
                    categoryId = food.id,
                    accountId = cash.id,
                    note = "午饭",
                ),
            )
        }
    }

    @Test
    fun homeShowsRealSummaryAndCategory() {
        composeRule.setContent {
            JianJiTheme(darkTheme = false) {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    HomeScreen(
                        onAddClick = {},
                        onSeeAllBills = {},
                        onBudgetClick = {},
                        onTransactionClick = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        // 等待 Room Flow 首批数据渲染
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("本月结余").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("本月结余").assertIsDisplayed()
        // 收入 1,000.00 / 支出 35.00 / 结余 965.00
        composeRule.onNodeWithText("965.00").assertExists()
        composeRule.onNodeWithText("1,000.00").assertExists()
        composeRule.onNodeWithText("35.00").assertExists()
        // 最近账单与分类概览（可能在首屏外，校验存在性；分类名出现在多个区域）
        composeRule.onNodeWithText("最近账单").assertExists()
        composeRule.onAllNodesWithText("餐饮").fetchSemanticsNodes().let { nodes ->
            assertTrue("分类概览应展示「餐饮」", nodes.isNotEmpty())
        }
        composeRule.onNodeWithText("午饭", substring = true).assertExists()
    }
}
