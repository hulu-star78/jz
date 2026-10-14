package com.jianji.app.di

import android.content.Context
import androidx.room.Room
import com.jianji.app.data.backup.BackupManager
import com.jianji.app.data.local.JianjiDatabase
import com.jianji.app.data.local.SeedDataCallback
import com.jianji.app.data.repository.AccountRepositoryImpl
import com.jianji.app.data.repository.BackupRepositoryImpl
import com.jianji.app.data.repository.BudgetRepositoryImpl
import com.jianji.app.data.repository.CategoryRepositoryImpl
import com.jianji.app.data.repository.StatisticsRepositoryImpl
import com.jianji.app.data.repository.TransactionRepositoryImpl
import com.jianji.app.data.settings.SettingsRepository
import com.jianji.app.domain.repository.AccountRepository
import com.jianji.app.domain.repository.BackupRepository
import com.jianji.app.domain.repository.BudgetRepository
import com.jianji.app.domain.repository.CategoryRepository
import com.jianji.app.domain.repository.StatisticsRepository
import com.jianji.app.domain.repository.TransactionRepository

/**
 * 手写依赖容器。
 *
 * 当前阶段依赖图简单，使用容器而非 DI 框架；后续如需 Hilt 可平滑替换。
 * 数据库构建要点：
 * - 注册预置数据回调；
 * - 显式添加迁移（禁止 fallbackToDestructiveMigration，升级不清库）。
 */
class AppContainer(context: Context) {

    private val applicationContext = context.applicationContext

    private val database: JianjiDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            JianjiDatabase::class.java,
            JianjiDatabase.DATABASE_NAME,
        )
            .addCallback(SeedDataCallback())
            .addMigrations(*JianjiDatabase.MIGRATIONS)
            .build()
    }

    private val backupManager: BackupManager by lazy {
        BackupManager(database = database, appVersion = appVersionName())
    }

    val backupRepository: BackupRepository by lazy {
        BackupRepositoryImpl(backupManager = backupManager)
    }

    val transactionRepository: TransactionRepository by lazy {
        TransactionRepositoryImpl(
            database = database,
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            accountDao = database.accountDao(),
        )
    }

    val statisticsRepository: StatisticsRepository by lazy {
        StatisticsRepositoryImpl(
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
        )
    }

    val accountRepository: AccountRepository by lazy {
        AccountRepositoryImpl(
            accountDao = database.accountDao(),
            transactionRepository = transactionRepository,
        )
    }

    val budgetRepository: BudgetRepository by lazy {
        BudgetRepositoryImpl(
            database = database,
            budgetDao = database.budgetDao(),
            transactionDao = database.transactionDao(),
        )
    }

    val categoryRepository: CategoryRepository by lazy {
        CategoryRepositoryImpl(
            categoryDao = database.categoryDao(),
        )
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(context)
    }

    private fun appVersionName(): String = try {
        val info = applicationContext.packageManager
            .getPackageInfo(applicationContext.packageName, 0)
        info.versionName ?: "unknown"
    } catch (e: Exception) {
        "unknown"
    }
}
