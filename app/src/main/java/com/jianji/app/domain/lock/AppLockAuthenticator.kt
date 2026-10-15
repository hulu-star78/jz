package com.jianji.app.domain.lock

/**
 * 应用锁验证能力。
 *
 * App 不自建密码体系：统一委托系统 BiometricPrompt，
 * 使用设备已有的指纹/人脸或锁屏密码（PIN/图案/密码）验证。
 */
enum class LockCapability {
    /** 支持生物特征 + 设备凭据（系统弹窗内可二选一，API 30+）。 */
    BIOMETRIC_OR_CREDENTIAL,

    /** 仅支持生物特征。 */
    BIOMETRIC_ONLY,

    /** 仅支持设备凭据（系统锁屏密码）。 */
    CREDENTIAL_ONLY,

    /** 设备未设置任何验证方式。 */
    NONE,
}

/**
 * 应用锁验证器接口。
 *
 * 抽象出来是为了可测试性：锁闸 UI 只依赖本接口，
 * 单测可用假实现覆盖全部状态流转；真实实现见 data 层。
 */
interface AppLockAuthenticator {

    /** 当前设备可用的验证能力（决定系统弹窗形态与提示文案）。 */
    fun capability(): LockCapability

    /**
     * 发起系统验证。
     * 验证成功回调 [onSuccess]；失败/取消/不可用回调 [onFailure]（带可展示信息）。
     */
    fun authenticate(
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,
    )
}
