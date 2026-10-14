package com.jianji.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import com.jianji.app.data.local.dao.AccountDao
import com.jianji.app.data.local.dao.BudgetDao
import com.jianji.app.data.local.dao.CategoryDao
import com.jianji.app.data.local.dao.TransactionDao
import com.jianji.app.data.local.entity.AccountEntity
import com.jianji.app.data.local.entity.BudgetEntity
import com.jianji.app.data.local.entity.CategoryEntity
import com.jianji.app.data.local.entity.TransactionEntity

/**
 * 「简记」本地数据库。
 *
 * 安全要求：
 * - exportSchema = true：schema JSON 导出到 app/schemas，供 Migration 编写与校验；
 * - 禁止 fallbackToDestructiveMigration：升级不允许清空用户数据；
 * - 所有版本升级必须在 [MIGRATIONS] 中提供 Migration；
 * - 无任何云同步，数据仅存本机。
 */
@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        AccountEntity::class,
        BudgetEntity::class,
    ],
    version = JianjiDatabase.VERSION_1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class JianjiDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao

    abstract fun categoryDao(): CategoryDao

    abstract fun accountDao(): AccountDao

    abstract fun budgetDao(): BudgetDao

    companion object {
        const val DATABASE_NAME = "jianji.db"

        const val VERSION_1 = 1

        /**
         * 数据库迁移列表。
         *
         * 升级版本时的步骤：
         * 1. 提升 @Database version；
         * 2. 在此追加 Migration(FROM, TO)，并在 app/schemas 中确认新旧 schema；
         * 3. 补充 MigrationTest（MigrationTestHelper.runMigrationsAndValidate）。
         */
        val MIGRATIONS: Array<Migration> = emptyArray()
    }
}
