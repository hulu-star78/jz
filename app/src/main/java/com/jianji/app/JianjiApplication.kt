package com.jianji.app

import android.app.Application
import com.jianji.app.di.AppContainer

/**
 * 「简记」应用入口。
 *
 * 采用手写依赖容器（AppContainer）而非 DI 框架：
 * 当前为数据层阶段，依赖关系简单，手写容器足够清晰且减少构建复杂度。
 * 后续引入 Hilt 时可平滑替换。
 */
class JianjiApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)
    }
}
