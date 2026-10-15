package com.jianji.app

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jianji.app.data.lock.BiometricAppLockAuthenticator
import com.jianji.app.data.settings.ThemeMode
import com.jianji.app.ui.JianJiApp
import com.jianji.app.ui.LocalAppContainer
import com.jianji.app.ui.appViewModelFactory
import com.jianji.app.ui.lock.AppLockGate
import com.jianji.app.ui.lock.AppLockViewModel
import com.jianji.app.ui.theme.JianJiTheme

/**
 * 主 Activity。
 *
 * 继承 FragmentActivity（而非 ComponentActivity）：系统 BiometricPrompt
 * 的构造函数要求 FragmentActivity；Compose 的 setContent / enableEdgeToEdge
 * 等扩展均以 ComponentActivity 为接收者，对子类完全兼容。
 */
class MainActivity : FragmentActivity() {

    /** 应用锁 ViewModel（在 setContent 中创建；供前后台切换时重新锁定）。 */
    private var appLockViewModel: AppLockViewModel? = null

    /** 上次进入后台的单调时钟（毫秒）；0 表示未曾进入后台。 */
    private var stoppedAtElapsed = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as JianjiApplication).container
        setContent {
            val themeMode by container.settingsRepository.themeMode
                .collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val lockViewModel: AppLockViewModel = viewModel(
                factory = appViewModelFactory {
                    AppLockViewModel(it.settingsRepository)
                },
            )
            appLockViewModel = lockViewModel
            val authenticator = remember {
                BiometricAppLockAuthenticator { this }
            }
            JianJiTheme(
                darkTheme = when (themeMode) {
                    ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                },
            ) {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.background,
                    ) {
                        // 应用锁闸：未解锁时应用内容完全不可见
                        AppLockGate(authenticator = authenticator) {
                            JianJiApp()
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        stoppedAtElapsed = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        // 离开超过 60 秒返回：重新锁定（应用锁开启时生效）
        if (stoppedAtElapsed > 0 &&
            SystemClock.elapsedRealtime() - stoppedAtElapsed > RELOCK_THRESHOLD_MS
        ) {
            appLockViewModel?.lockIfEnabled()
        }
    }

    private companion object {
        /** 后台返回重新锁定的间隔阈值：60 秒。 */
        const val RELOCK_THRESHOLD_MS = 60_000L
    }
}
