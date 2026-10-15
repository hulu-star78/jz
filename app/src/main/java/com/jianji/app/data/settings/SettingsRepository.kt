package com.jianji.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 主题模式。 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * 设置仓储：本地偏好（主题模式、应用锁）。
 * 数据不出设备；后续货币、启动页等设置可在此扩展。
 */
class SettingsRepository(private val context: Context) {

    private val themeKey = stringPreferencesKey("theme_mode")
    private val appLockKey = booleanPreferencesKey("app_lock_enabled")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[themeKey]?.let { stored ->
            runCatching { ThemeMode.valueOf(stored) }.getOrNull()
        } ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[themeKey] = mode.name
        }
    }

    /** 应用锁开关；默认关闭（用户手动开启，开启前需通过一次系统验证）。 */
    val appLockEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[appLockKey] ?: false
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[appLockKey] = enabled
        }
    }
}
