package com.jianji.app.ui.lock

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jianji.app.domain.lock.AppLockAuthenticator
import com.jianji.app.domain.lock.LockCapability
import com.jianji.app.ui.LocalAppContainer
import com.jianji.app.ui.appViewModelFactory
import com.jianji.app.ui.theme.Dimens
import com.jianji.app.ui.theme.LocalSemanticColors

/**
 * 应用锁闸：未解锁时只显示锁屏页（应用内容完全不可见），
 * 解锁成功后放行 [content]。
 *
 * 行为：
 * - 进入锁屏自动发起一次系统验证；失败/取消后用户可点「解锁」重试；
 * - 锁定时加 `FLAG_SECURE`：禁截屏、防最近任务列表预览，解锁后移除；
 * - 验证能力由 [AppLockAuthenticator] 自适应（见 data 层实现）。
 *
 * @param authenticator 验证器；MainActivity 传入真实实现，测试可传入假实现。
 */
@Composable
fun AppLockGate(
    authenticator: AppLockAuthenticator,
    content: @Composable () -> Unit,
) {
    val activity = LocalContext.current as? FragmentActivity
    val viewModel: AppLockViewModel = viewModel(
        factory = appViewModelFactory {
            AppLockViewModel(it.settingsRepository)
        },
    )
    val locked by viewModel.locked.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()
    val capability = remember(authenticator, activity) { authenticator.capability() }

    val tryUnlock: () -> Unit = {
        if (capability == LockCapability.NONE) {
            viewModel.onAuthFailure("设备未设置指纹或锁屏密码，请先在系统设置中添加")
        } else {
            viewModel.clearError()
            authenticator.authenticate(
                onSuccess = viewModel::onAuthSuccess,
                onFailure = viewModel::onAuthFailure,
            )
        }
    }

    // 进入锁屏自动验证一次
    LaunchedEffect(locked, capability) {
        if (locked) tryUnlock()
    }

    // 锁定时禁截屏 / 禁最近任务预览
    DisposableEffect(locked, activity) {
        val window = (activity as? Activity)?.window
        if (locked) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    if (locked) {
        LockScreen(
            capability = capability,
            error = authError,
            onUnlockClick = tryUnlock,
        )
    } else {
        content()
    }
}

/** 锁屏页：应用标识 + 状态说明 + 解锁按钮。 */
@Composable
private fun LockScreen(
    capability: LockCapability,
    error: String?,
    onUnlockClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(Dimens.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "记",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Spacer(Modifier.height(Dimens.spaceL))
        Text(
            text = "简记",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Dimens.spaceS))
        Text(
            text = "应用已锁定",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Dimens.spaceM))
        Text(
            text = when (capability) {
                LockCapability.BIOMETRIC_OR_CREDENTIAL ->
                    "请使用指纹、人脸或锁屏密码验证身份"

                LockCapability.BIOMETRIC_ONLY ->
                    "请使用指纹或人脸验证身份"

                LockCapability.CREDENTIAL_ONLY ->
                    "请输入锁屏密码验证身份"

                LockCapability.NONE ->
                    "设备未设置指纹或锁屏密码，请先在系统设置中添加，然后重试"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (error != null) {
            Spacer(Modifier.height(Dimens.spaceM))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = LocalSemanticColors.current.expense,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(Dimens.spaceXxl))
        Button(
            onClick = onUnlockClick,
            enabled = capability != LockCapability.NONE,
            modifier = Modifier.height(48.dp),
            shape = CircleShape,
        ) {
            Text(text = "解锁", style = MaterialTheme.typography.titleMedium)
        }
    }
}
