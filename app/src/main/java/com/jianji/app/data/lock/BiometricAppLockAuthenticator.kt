package com.jianji.app.data.lock

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.jianji.app.domain.lock.AppLockAuthenticator
import com.jianji.app.domain.lock.LockCapability

/**
 * 基于系统 BiometricPrompt 的应用锁验证器。
 *
 * 能力自适应（覆盖 minSdk 24 ~ 35）：
 * - API 30+：优先组合验证（生物特征 + 锁屏密码二选一，系统弹窗提供「使用密码」入口）；
 * - API 24-29：退化为单一能力（仅生物特征 / 仅设备凭据）；
 * - 设备未设置任何验证方式时返回 [LockCapability.NONE]，由 UI 层提示用户。
 *
 * 安全边界：App 不接触用户的密码与生物特征本身，验证由系统完成
 * （Keystore 保障）；本实现只负责调起与接收结果。
 */
class BiometricAppLockAuthenticator(
    private val activityProvider: () -> FragmentActivity?,
) : AppLockAuthenticator {

    override fun capability(): LockCapability {
        val activity = activityProvider() ?: return LockCapability.NONE
        val manager = BiometricManager.from(activity)
        // 组合验证（BIOMETRIC_WEAK | DEVICE_CREDENTIAL）仅 API 30+ 受支持
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            manager.canAuthenticate(COMBINED) == BiometricManager.BIOMETRIC_SUCCESS
        ) {
            return LockCapability.BIOMETRIC_OR_CREDENTIAL
        }
        return when {
            manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
                BiometricManager.BIOMETRIC_SUCCESS -> LockCapability.BIOMETRIC_ONLY

            manager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL) ==
                BiometricManager.BIOMETRIC_SUCCESS -> LockCapability.CREDENTIAL_ONLY

            else -> LockCapability.NONE
        }
    }

    override fun authenticate(
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,
    ) {
        val activity = activityProvider()
        if (activity == null) {
            onFailure("无法调起系统验证")
            return
        }
        val authenticators = authenticatorsFor(capability())
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onFailure(errString.toString())
            }

            override fun onAuthenticationFailed() {
                onFailure("验证未通过，请重试")
            }
        }
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("简记 · 应用锁")
            .setSubtitle("验证身份后进入")
            .setAllowedAuthenticators(authenticators)
            .setConfirmationRequired(false)
            .build()
        try {
            BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
        } catch (e: IllegalArgumentException) {
            // 个别 ROM 对组合验证支持不完整：退化为单一能力重试
            val fallback = when (authenticators) {
                COMBINED -> BiometricManager.Authenticators.BIOMETRIC_WEAK
                else -> authenticators
            }
            if (fallback == authenticators) {
                onFailure("当前设备不支持该验证方式")
                return
            }
            val retryInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("简记 · 应用锁")
                .setSubtitle("验证身份后进入")
                .setAllowedAuthenticators(fallback)
                .setConfirmationRequired(false)
                .build()
            try {
                BiometricPrompt(activity, executor, callback).authenticate(retryInfo)
            } catch (e2: IllegalArgumentException) {
                onFailure("当前设备不支持该验证方式")
            }
        }
    }

    private fun authenticatorsFor(capability: LockCapability): Int = when (capability) {
        LockCapability.BIOMETRIC_OR_CREDENTIAL -> COMBINED
        LockCapability.BIOMETRIC_ONLY -> BiometricManager.Authenticators.BIOMETRIC_WEAK
        LockCapability.CREDENTIAL_ONLY -> BiometricManager.Authenticators.DEVICE_CREDENTIAL
        LockCapability.NONE -> BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }

    private companion object {
        const val COMBINED =
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
}
