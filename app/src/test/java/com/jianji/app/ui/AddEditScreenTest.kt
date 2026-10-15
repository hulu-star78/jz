package com.jianji.app.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import com.jianji.app.di.AppContainer
import com.jianji.app.domain.model.Money
import com.jianji.app.ui.add.AddEditTransactionScreen
import com.jianji.app.ui.add.AMOUNT_AREA_TAG
import com.jianji.app.ui.add.SAVE_TOP_TAG
import com.jianji.app.ui.components.SAVE_KEYPAD_TAG
import com.jianji.app.ui.theme.JianJiTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 记一笔页面 UI 测试：保存错误反馈、4×4 键盘、键盘收起/唤回。
 * 保存落库的完整链路由 AddEditTransactionViewModelTest 覆盖。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AddEditScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            container = AppContainer(context)
            // 触发数据库创建（写入预置分类与账户）
            container.categoryRepository.observeAll().first()
        }
    }

    private fun setAddScreen() {
        composeRule.setContent {
            JianJiTheme(darkTheme = false) {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    AddEditTransactionScreen(
                        transactionId = null,
                        onClose = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** 点击分类并等待其进入选中态（横滑项在 Robolectric 下点击有时序波动）。 */
    private fun selectCategory(name: String) {
        composeRule.onNodeWithText(name)
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            try {
                composeRule.onNodeWithText(name).assertIsSelected()
                true
            } catch (e: AssertionError) {
                false
            }
        }
    }

    @Test
    fun saveButtonShowsSpecificErrorForMissingFields() {
        setAddScreen()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("餐饮").fetchSemanticsNodes().isNotEmpty()
        }

        // 无金额：点击右上角保存提示金额错误
        composeRule.onNodeWithTag(SAVE_TOP_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("请输入大于 0 的金额").assertExists()

        // 输入金额后未选分类：提示分类错误
        composeRule.onNodeWithText("1").performClick()
        composeRule.onNodeWithText("2").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(SAVE_TOP_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("请选择分类").assertExists()

        // 选好分类后点击保存：保存被触发（落库或进入保存中状态）
        selectCategory("餐饮")
        composeRule.onNodeWithTag(SAVE_TOP_TAG).performClick()
        composeRule.waitForIdle()
        assertSaveTriggered(expectedAmountMinor = 1200)
    }

    @Test
    fun keypadIsSymmetricWithQuickKeys() {
        setAddScreen()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("餐饮").fetchSemanticsNodes().isNotEmpty()
        }

        // 4×4 网格：功能键齐全
        composeRule.onNodeWithText("00").assertExists()
        composeRule.onNodeWithText("清空").assertExists()
        composeRule.onNodeWithTag(SAVE_KEYPAD_TAG).assertExists()
        composeRule.onNodeWithContentDescription("退格").assertExists()
        composeRule.onNodeWithContentDescription("收起键盘").assertExists()

        // 00 键：1 + 00 = 100
        composeRule.onNodeWithText("1").performClick()
        composeRule.onNodeWithText("00").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("100").assertExists()

        // 清空键：金额回到 0.00
        composeRule.onNodeWithText("清空").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("0.00").assertExists()

        // 键盘保存键与右上角保存同一逻辑：未选分类时提示错误
        composeRule.onNodeWithText("5").performClick()
        composeRule.onNodeWithTag(SAVE_KEYPAD_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("请选择分类").assertExists()
    }

    @Test
    fun hideKeyCollapsesKeypadAndAmountAreaRecallsIt() {
        setAddScreen()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("餐饮").fetchSemanticsNodes().isNotEmpty()
        }

        // 键盘默认可见（退格键存在）
        composeRule.onNodeWithContentDescription("退格").assertExists()

        // 点击「收起键盘」键：键盘隐藏，出现唤回提示
        composeRule.onNodeWithContentDescription("收起键盘").performClick()
        composeRule.waitForIdle()
        assertTrue(
            "收起后数字键盘应不可见",
            composeRule.onAllNodesWithContentDescription("退格").fetchSemanticsNodes().isEmpty(),
        )
        composeRule.onNodeWithText("点击金额区域唤起数字键盘").assertExists()

        // 点击金额区域：键盘恢复
        composeRule.onNodeWithTag(AMOUNT_AREA_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("退格").assertExists()
    }

    @Test
    fun saveButtonVisibleAndUsableWhenKeypadHidden() {
        setAddScreen()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("餐饮").fetchSemanticsNodes().isNotEmpty()
        }

        // 输入金额并选择分类
        composeRule.onNodeWithText("9").performClick()
        selectCategory("餐饮")
        composeRule.waitForIdle()

        // 收起键盘后右上角保存按钮仍可点击
        composeRule.onNodeWithContentDescription("收起键盘").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(SAVE_TOP_TAG).assertIsEnabled()
        composeRule.onNodeWithTag(SAVE_TOP_TAG).performClick()
        composeRule.waitForIdle()
        assertSaveTriggered(expectedAmountMinor = 900)
    }

    /**
     * 保存触发后的两种合法结果：
     * - 已落库（校验金额）；
     * - 写事务进行中（Robolectric 下保存协程不恢复，数据库锁定），停留在“保存中…”。
     * 轮询等待以容忍协程调度时序波动。
     */
    private fun assertSaveTriggered(expectedAmountMinor: Long) {
        var transactions: List<com.jianji.app.domain.model.Transaction> = emptyList()
        var triggered = false
        repeat(50) {
            transactions = try {
                runBlocking { container.transactionRepository.observeAll().first() }
            } catch (e: android.database.sqlite.SQLiteException) {
                emptyList() // 写事务进行中
            }
            val savingShown = composeRule
                .onAllNodesWithText("保存中…")
                .fetchSemanticsNodes()
                .isNotEmpty()
            if (transactions.isNotEmpty() || savingShown) {
                triggered = true
                return@repeat
            }
            composeRule.waitForIdle()
            Thread.sleep(100)
        }
        assertTrue("点击保存后应触发保存（落库或保存中状态）", triggered)
        if (transactions.isNotEmpty()) {
            assertEquals(
                Money.fromMinor(expectedAmountMinor),
                transactions.first().amount,
            )
        }
    }
}
