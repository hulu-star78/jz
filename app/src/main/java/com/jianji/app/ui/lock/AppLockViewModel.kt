package com.jianji.app.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jianji.app.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * 应用锁状态（Activity 作用域：旋转屏幕不重新锁定）。
 *
 * 状态来源：
 * - 应用锁开关来自设置（DataStore），默认关闭；
 * - 解锁状态仅存于内存——每次冷启动都需重新验证。
 */
class AppLockViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /** 应用锁开关（设置项）。 */
    val lockEnabled: StateFlow<Boolean> = settingsRepository.appLockEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _unlocked = MutableStateFlow(false)

    /** 是否应显示锁屏：开关打开且尚未解锁。 */
    val locked: StateFlow<Boolean> = combine(lockEnabled, _unlocked) { enabled, unlocked ->
        enabled && !unlocked
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _authError = MutableStateFlow<String?>(null)

    /** 最近一次验证失败的可展示信息。 */
    val authError: StateFlow<String?> = _authError.asStateFlow()

    fun onAuthSuccess() {
        _unlocked.value = true
        _authError.value = null
    }

    fun onAuthFailure(message: String) {
        _authError.value = message
    }

    fun clearError() {
        _authError.value = null
    }

    /**
     * 从后台返回时调用：离开超过阈值（见 MainActivity）且应用锁开启时重新锁定。
     * 关闭状态下调用无副作用。
     */
    fun lockIfEnabled() {
        if (lockEnabled.value) {
            _unlocked.value = false
        }
    }
}
