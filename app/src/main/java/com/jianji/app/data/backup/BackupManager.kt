package com.jianji.app.data.backup

import androidx.room.withTransaction
import com.jianji.app.data.local.JianjiDatabase
import com.jianji.app.data.local.entity.AccountEntity
import com.jianji.app.data.local.entity.BudgetEntity
import com.jianji.app.data.local.entity.CategoryEntity
import com.jianji.app.data.local.entity.TransactionEntity

/**
 * 备份管理器：导出、校验与恢复。
 *
 * 安全约束：
 * - 恢复在单个数据库事务中完成（先清空再按 FK 顺序写入），
 *   任一步失败整体回滚，不会留下「恢复一半」的脏数据；
 * - 恢复前必须经过 [BackupSerializer.deserialize] 的完整校验，
 *   校验失败不触碰数据库；
 * - 全程无 Double/Float 金额参与。
 */
class BackupManager(
    private val database: JianjiDatabase,
    private val appVersion: String,
) {

    /** 导出当前全部数据为 JSON 字符串。 */
    suspend fun export(): BackupFile {
        val categories = database.categoryDao().getAllSnapshot()
        val accounts = database.accountDao().getAllSnapshot()
        val transactions = database.transactionDao().getAllSnapshot()
        val budgets = database.budgetDao().getAllSnapshot()
        return BackupFile(
            formatVersion = BackupSerializer.FORMAT_VERSION,
            appVersion = appVersion,
            exportedAt = System.currentTimeMillis(),
            counts = BackupCounts(
                categories = categories.size,
                accounts = accounts.size,
                transactions = transactions.size,
                budgets = budgets.size,
            ),
            categories = categories,
            accounts = accounts,
            transactions = transactions,
            budgets = budgets,
        )
    }

    /** 解析并校验备份内容；不写入数据库。 */
    fun validate(json: String): BackupFile = BackupSerializer.deserialize(json)

    /**
     * 用备份内容覆盖当前数据，返回备份内容统计。
     * 整个操作在一个事务内完成；异常时回滚，当前数据保持原状。
     */
    suspend fun restore(backup: BackupFile): BackupCounts = database.withTransaction {
        database.clearAllTables()
        backup.categories.forEach { database.categoryDao().insert(it) }
        backup.accounts.forEach { database.accountDao().insert(it) }
        backup.transactions.forEach { database.transactionDao().insert(it) }
        backup.budgets.forEach { database.budgetDao().insert(it) }
        backup.counts
    }
}
