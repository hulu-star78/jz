package com.jianji.app.data.backup

import com.jianji.app.testutil.DatabaseTestSupport
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 备份管理器测试：导出/恢复往返、恢复覆盖语义、校验失败不动数据。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupManagerTest : DatabaseTestSupport() {

    private lateinit var manager: BackupManager

    private suspend fun setUpManager() {
        manager = BackupManager(database = database, appVersion = "0.1.0")
    }

    private suspend fun seedSampleData() {
        val foodId = insertCategory("餐饮", com.jianji.app.domain.model.CategoryType.EXPENSE)
        val salaryId = insertCategory("工资", com.jianji.app.domain.model.CategoryType.INCOME)
        val cashId = insertAccount("现金", initialBalanceMinor = 100000)
        val bankId = insertAccount("银行卡", initialBalanceMinor = 50000)
        insertTransaction(3500, com.jianji.app.domain.model.TransactionType.EXPENSE, java.time.LocalDate.of(2026, 10, 2), foodId, cashId, note = "午饭")
        insertTransaction(100000, com.jianji.app.domain.model.TransactionType.INCOME, java.time.LocalDate.of(2026, 10, 1), salaryId, bankId)
        insertTransaction(20000, com.jianji.app.domain.model.TransactionType.TRANSFER, java.time.LocalDate.of(2026, 10, 3), null, cashId, toAccountId = bankId)
    }

    @Test
    fun `export then restore round trips all data`() = runTest {
        setUpManager()
        seedSampleData()
        val originalTransactions = database.transactionDao().getAllSnapshot()
        val originalCategories = database.categoryDao().getAllSnapshot()
        val originalAccounts = database.accountDao().getAllSnapshot()

        val json = BackupSerializer.serialize(manager.export())

        // 清空后从同一份 JSON 恢复
        database.clearAllTables()
        assertEquals(0, database.transactionDao().getAllSnapshot().size)

        val counts = manager.restore(manager.validate(json))
        assertEquals(3, counts.transactions)
        assertEquals(2, counts.categories)
        assertEquals(2, counts.accounts)

        val restoredTransactions = database.transactionDao().getAllSnapshot()
        assertEquals(originalTransactions.size, restoredTransactions.size)
        // 往返后逐字段一致（含 id、金额、日期、转账关系）
        originalTransactions.forEach { original ->
            val restored = restoredTransactions.first { it.id == original.id }
            assertEquals(original.amountMinor, restored.amountMinor)
            assertEquals(original.type, restored.type)
            assertEquals(original.transactionDate, restored.transactionDate)
            assertEquals(original.categoryId, restored.categoryId)
            assertEquals(original.accountId, restored.accountId)
            assertEquals(original.toAccountId, restored.toAccountId)
            assertEquals(original.note, restored.note)
        }
        assertEquals(originalCategories.map { it.name }, database.categoryDao().getAllSnapshot().map { it.name })
        assertEquals(originalAccounts.map { it.initialBalanceMinor }, database.accountDao().getAllSnapshot().map { it.initialBalanceMinor })
    }

    @Test
    fun `restore replaces existing data`() = runTest {
        setUpManager()
        seedSampleData()
        val json = BackupSerializer.serialize(manager.export())

        // 恢复前新增一笔无关数据
        insertTransaction(999, com.jianji.app.domain.model.TransactionType.EXPENSE, java.time.LocalDate.of(2026, 10, 5), database.categoryDao().getAllSnapshot().first().id, database.accountDao().getAllSnapshot().first().id)
        assertEquals(4, database.transactionDao().getAllSnapshot().size)

        manager.restore(manager.validate(json))
        val after = database.transactionDao().getAllSnapshot()
        assertEquals(3, after.size)
        assertTrue(after.none { it.amountMinor == 999L })
    }

    @Test
    fun `statistics stay correct after restore`() = runTest {
        setUpManager()
        seedSampleData()
        val json = BackupSerializer.serialize(manager.export())

        // 恢复前新增一笔会改变统计的无关数据
        val catId = database.categoryDao().getAllSnapshot().first().id
        val accId = database.accountDao().getAllSnapshot().first().id
        insertTransaction(
            777,
            com.jianji.app.domain.model.TransactionType.EXPENSE,
            java.time.LocalDate.of(2026, 10, 9),
            catId,
            accId,
        )
        assertEquals(4, database.transactionDao().getAllSnapshot().size)

        manager.restore(manager.validate(json))

        // 恢复后统计回到备份口径：收入 100000，支出 3500（转账不计入）
        val start = java.time.LocalDate.of(2026, 10, 1).toEpochDay().toLong()
        val end = java.time.LocalDate.of(2026, 10, 31).toEpochDay().toLong()
        val income = database.transactionDao()
            .observeTotalByType(start, end, com.jianji.app.domain.model.TransactionType.INCOME).first()
        val expense = database.transactionDao()
            .observeTotalByType(start, end, com.jianji.app.domain.model.TransactionType.EXPENSE).first()
        assertEquals(100000L, income)
        assertEquals(3500L, expense)
    }

    @Test
    fun `invalid backup never touches the database`() = runTest {
        setUpManager()
        seedSampleData()
        val before = database.transactionDao().getAllSnapshot()

        val ex = assertThrows(BackupException::class.java) {
            manager.validate("{ 无效的备份 }")
        }
        assertEquals(BackupError.INVALID_JSON, ex.error)

        val after = database.transactionDao().getAllSnapshot()
        assertEquals(before.size, after.size)
        assertEquals(before.map { it.amountMinor }, after.map { it.amountMinor })
    }

    @Test
    fun `broken reference backup is rejected before restore`() = runTest {
        setUpManager()
        seedSampleData()
        val brokenJson = """
            {
              "formatVersion": 1,
              "appVersion": "0.1.0",
              "exportedAt": 1,
              "counts": {"categories": 1, "accounts": 0, "transactions": 1, "budgets": 0},
              "categories": [{"id": 1, "name": "餐饮", "icon": "restaurant", "type": "EXPENSE", "isSystem": false, "isArchived": false, "sortOrder": 0}],
              "accounts": [],
              "transactions": [{"id": 1, "amountMinor": "100", "type": "EXPENSE", "transactionDate": 20000, "categoryId": 1, "accountId": 42, "createdAt": 1, "updatedAt": 1}],
              "budgets": []
            }
        """.trimIndent()

        val ex = assertThrows(BackupException::class.java) { manager.validate(brokenJson) }
        assertEquals(BackupError.BROKEN_REFERENCE, ex.error)
        // 数据库未被触碰
        assertEquals(3, database.transactionDao().getAllSnapshot().size)
    }
}
