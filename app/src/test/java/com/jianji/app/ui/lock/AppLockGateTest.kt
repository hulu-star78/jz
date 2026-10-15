package com.jianji.app.ui.lock

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.jianji.app.JianjiApplication
import com.jianji.app.domain.lock.AppLockAuthenticator
import com.jianji.app.domain.lock.LockCapability
import com.jianji.app.ui.LocalAppContainer
import com.jianji.app.ui.theme.JianJiTheme
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
 * 应用锁闸测试：用假验证器覆盖 未开启 / 开启自动解锁 / 失败重试 三条路径。
 * 真实 BiometricPrompt 依赖系统组件，无法在 JVM 测试，由接口接缝隔离。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppLockGateTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: JianjiApplication

    /** 可控制成败的假验证器。 */
    private class FakeAuthenticator(
        var capability: LockCapability = LockCapability.BIOMETRIC_OR_CREDENTIAL,
        var succeed: Boolean = true,
        var failureMessage: String = "验证未通过，请重试",
    ) : AppLockAuthenticator {
        var callCount = 0
            private set

        override fun capability(): LockCapability = capability

        override fun authenticate(
            onSuccess: () -> Unit,
            onFailure: (String) -> Unit,
        ) {
            callCount++
            if (succeed) onSuccess() else onFailure(failureMessage)
        }
    }

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
    }

    private fun setGate(authenticator: AppLockAuthenticator) {
        composeRule.setContent {
            JianJiTheme(darkTheme = false) {
                CompositionLocalProvider(LocalAppContainer provides app.container) {
                    AppLockGate(authenticator = authenticator) {
                        Text("APP_CONTENT")
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `lock disabled shows content directly`(): Unit = runBlocking {
        app.container.settingsRepository.setAppLockEnabled(false)
        val authenticator = FakeAuthenticator()
        setGate(authenticator)

        // 直接放行，不发起验证
        composeRule.onNodeWithText("APP_CONTENT").assertIsDisplayed()
        assertEquals(0, authenticator.callCount)
    }

    @Test
    fun `lock enabled auto unlocks on entry`(): Unit = runBlocking {
        app.container.settingsRepository.setAppLockEnabled(true)
        val authenticator = FakeAuthenticator(succeed = true)
        setGate(authenticator)

        // 进入即自动验证并放行
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("APP_CONTENT").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue("应自动发起一次验证", authenticator.callCount >= 1)
    }

    @Test
    fun `lock enabled shows lock screen on failure and retry unlocks`(): Unit = runBlocking {
        app.container.settingsRepository.setAppLockEnabled(true)
        val authenticator = FakeAuthenticator(succeed = false)
        setGate(authenticator)

        // 锁屏页 + 错误文案
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("应用已锁定").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("验证未通过，请重试").assertIsDisplayed()

        // 重试成功后放行
        authenticator.succeed = true
        composeRule.onNodeWithText("解锁").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("APP_CONTENT").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals("首次自动验证 + 一次手动重试", 2, authenticator.callCount)
    }

    @Test
    fun `no capability disables unlock button`(): Unit = runBlocking {
        app.container.settingsRepository.setAppLockEnabled(true)
        val authenticator = FakeAuthenticator(
            capability = LockCapability.NONE,
            succeed = false,
            failureMessage = "设备未设置指纹或锁屏密码，请先在系统设置中添加",
        )
        setGate(authenticator)

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("应用已锁定").fetchSemanticsNodes().isNotEmpty()
        }
        // 提示设备未设置验证方式
        composeRule
            .onNodeWithText("设备未设置指纹或锁屏密码，请先在系统设置中添加，然后重试")
            .assertIsDisplayed()
    }
}
