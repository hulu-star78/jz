package com.jianji.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jianji.app.JianjiApplication
import com.jianji.app.di.AppContainer

/**
 * ViewModel 工厂：从 Application 中的 AppContainer 取仓储并构造 ViewModel。
 *
 * 用法：
 * ```
 * val vm: HomeViewModel = viewModel(
 *     factory = appViewModelFactory { HomeViewModel(it.transactionRepository, it.statisticsRepository) }
 * )
 * ```
 */
inline fun <reified VM : ViewModel> appViewModelFactory(
    crossinline create: (AppContainer) -> VM,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as JianjiApplication
        create(app.container)
    }
}

/** CreationExtras 扩展：便于在 initializer 中读取容器。 */
val CreationExtras.appContainer: AppContainer
    get() = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as JianjiApplication).container
