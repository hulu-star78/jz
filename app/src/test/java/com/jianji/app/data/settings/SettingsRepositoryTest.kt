package com.jianji.app.data.settings

import androidx.test.core.app.ApplicationProvider
import com.jianji.app.JianjiApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 设置仓储测试：主题模式与应用锁开关的读写与默认值。
 * 使用 Application 的容器，保证与运行期同一 DataStore 实例。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryTest {

    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<JianjiApplication>()
        repository = app.container.settingsRepository
    }

    @Test
    fun `app lock defaults to off`() = runTest {
        assertFalse(repository.appLockEnabled.first())
    }

    @Test
    fun `app lock can be enabled and disabled`() = runTest {
        repository.setAppLockEnabled(true)
        assertTrue(repository.appLockEnabled.first())

        repository.setAppLockEnabled(false)
        assertFalse(repository.appLockEnabled.first())
    }

    @Test
    fun `theme mode still works`() = runTest {
        repository.setThemeMode(ThemeMode.DARK)
        assertTrue(repository.themeMode.first() == ThemeMode.DARK)

        repository.setThemeMode(ThemeMode.LIGHT)
        assertTrue(repository.themeMode.first() == ThemeMode.LIGHT)
    }
}
