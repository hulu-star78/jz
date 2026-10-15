package com.jianji.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jianji.app.data.backup.BackupCounts
import com.jianji.app.data.backup.BackupException
import com.jianji.app.data.repository.userMessage
import com.jianji.app.data.settings.SettingsRepository
import com.jianji.app.data.settings.ThemeMode
import com.jianji.app.domain.repository.BackupRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 恢复结果：成功返回内容统计，失败返回可展示的中文原因。 */
sealed interface RestoreResult {
    data class Success(val counts: BackupCounts) : RestoreResult
    data class Failure(val message: String) : RestoreResult
}

class ProfileViewModel(
    private val backupRepository: BackupRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    /** 应用锁开关（默认关闭）。 */
    val appLockEnabled: StateFlow<Boolean> = settingsRepository.appLockEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAppLockEnabled(enabled) }
    }

    /** 导出全部数据为 JSON 文本。 */
    suspend fun exportJson(): String = backupRepository.exportJson()

    fun suggestedFileName(): String = backupRepository.suggestedFileName()

    /**
     * 解析并校验备份内容（不写入）。
     * 调用方在校验通过后展示确认对话框，再调用 [restore]。
     */
    suspend fun validateBackup(json: String): Result<BackupCounts> = try {
        Result.success(backupRepository.validate(json).counts)
    } catch (e: BackupException) {
        Result.failure(IllegalStateException(e.userMessage(), e))
    } catch (e: Exception) {
        Result.failure(IllegalStateException("备份文件读取失败", e))
    }

    /** 执行恢复（覆盖当前数据）。 */
    suspend fun restore(json: String): RestoreResult = try {
        RestoreResult.Success(backupRepository.restore(json))
    } catch (e: BackupException) {
        RestoreResult.Failure(e.userMessage())
    } catch (e: Exception) {
        RestoreResult.Failure("恢复失败：${e.message ?: "未知错误"}")
    }
}
