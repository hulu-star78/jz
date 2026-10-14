package com.jianji.app.data.backup

import com.jianji.app.data.local.entity.AccountEntity
import com.jianji.app.data.local.entity.BudgetEntity
import com.jianji.app.data.local.entity.CategoryEntity
import com.jianji.app.data.local.entity.TransactionEntity
import com.jianji.app.domain.model.AccountType
import com.jianji.app.domain.model.CategoryType
import com.jianji.app.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 备份序列化测试：往返一致、严格校验、拒绝非法文件。
 * 依赖 org.json（Android 框架实现），故在 Robolectric 下运行。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupSerializerTest {

    private fun sampleBackup() = BackupFile(
        formatVersion = BackupSerializer.FORMAT_VERSION,
        appVersion = "0.1.0",
        exportedAt = 1_700_000_000_000,
        counts = BackupCounts(categories = 2, accounts = 2, transactions = 3, budgets = 1),
        categories = listOf(
            CategoryEntity(1L, "餐饮", "restaurant", CategoryType.EXPENSE),
            CategoryEntity(2L, "工资", "payments", CategoryType.INCOME, isSystem = true, sortOrder = 0),
        ),
        accounts = listOf(
            AccountEntity(1L, "现金", "payments", AccountType.CASH, 100000, false, 1000L),
            AccountEntity(2L, "银行卡", "account_balance", AccountType.BANK, -5000, false, 1001L),
        ),
        transactions = listOf(
            TransactionEntity(1L, 3500, TransactionType.EXPENSE, 20000L, 1L, 1L, null, "午饭", 100L, 100L),
            TransactionEntity(2L, 100000, TransactionType.INCOME, 20001L, 2L, 2L, null, null, 101L, 101L),
            TransactionEntity(3L, 20000, TransactionType.TRANSFER, 20002L, null, 1L, 2L, null, 102L, 102L),
        ),
        budgets = listOf(
            BudgetEntity(1L, "2026-10", null, 300000),
        ),
    )

    @Test
    fun `round trip preserves every field`() {
        val backup = sampleBackup()
        val parsed = BackupSerializer.deserialize(BackupSerializer.serialize(backup))

        assertEquals(backup.formatVersion, parsed.formatVersion)
        assertEquals(backup.appVersion, parsed.appVersion)
        assertEquals(backup.exportedAt, parsed.exportedAt)
        assertEquals(backup.counts, parsed.counts)
        assertEquals(backup.categories, parsed.categories)
        assertEquals(backup.accounts, parsed.accounts)
        assertEquals(backup.transactions, parsed.transactions)
        assertEquals(backup.budgets, parsed.budgets)
    }

    @Test
    fun `amounts survive round trip exactly`() {
        // 覆盖 Long 边界与负值（信用卡初始余额）
        val backup = sampleBackup().copy(
            accounts = listOf(
                AccountEntity(1L, "极端", "x", AccountType.OTHER, Long.MAX_VALUE, false, 1L),
                AccountEntity(2L, "极端负", "x", AccountType.OTHER, Long.MIN_VALUE, false, 1L),
            ),
            transactions = listOf(
                TransactionEntity(1L, Long.MAX_VALUE, TransactionType.EXPENSE, 20000L, 1L, 1L, null, null, 1L, 1L),
            ),
            counts = BackupCounts(categories = 2, accounts = 2, transactions = 1, budgets = 1),
        )
        val parsed = BackupSerializer.deserialize(BackupSerializer.serialize(backup))
        assertEquals(Long.MAX_VALUE, parsed.accounts[0].initialBalanceMinor)
        assertEquals(Long.MIN_VALUE, parsed.accounts[1].initialBalanceMinor)
        assertEquals(Long.MAX_VALUE, parsed.transactions[0].amountMinor)
    }

    @Test
    fun `rejects invalid json`() {
        val ex = assertThrows(BackupException::class.java) {
            BackupSerializer.deserialize("这不是 JSON")
        }
        assertEquals(BackupError.INVALID_JSON, ex.error)
    }

    @Test
    fun `rejects unsupported version`() {
        val json = BackupSerializer.serialize(sampleBackup())
            .replace("\"formatVersion\": 1", "\"formatVersion\": 99")
        val ex = assertThrows(BackupException::class.java) {
            BackupSerializer.deserialize(json)
        }
        assertEquals(BackupError.UNSUPPORTED_VERSION, ex.error)
    }

    @Test
    fun `rejects counts mismatch`() {
        val json = BackupSerializer.serialize(sampleBackup())
            .replace("\"transactions\": 3", "\"transactions\": 5")
        val ex = assertThrows(BackupException::class.java) {
            BackupSerializer.deserialize(json)
        }
        assertTrue(
            "应为结构错误，实际: ${ex.error}",
            ex.error == BackupError.MALFORMED_STRUCTURE,
        )
    }

    @Test
    fun `rejects broken account reference`() {
        val broken = sampleBackup().copy(
            transactions = listOf(
                TransactionEntity(1L, 100, TransactionType.EXPENSE, 20000L, 1L, 999L, null, null, 1L, 1L),
            ),
            counts = BackupCounts(2, 2, 1, 1),
        )
        val ex = assertThrows(BackupException::class.java) {
            BackupSerializer.deserialize(BackupSerializer.serialize(broken))
        }
        assertEquals(BackupError.BROKEN_REFERENCE, ex.error)
    }

    @Test
    fun `rejects transfer without target account`() {
        val broken = sampleBackup().copy(
            transactions = listOf(
                TransactionEntity(1L, 100, TransactionType.TRANSFER, 20000L, null, 1L, null, null, 1L, 1L),
            ),
            counts = BackupCounts(2, 2, 1, 1),
        )
        val ex = assertThrows(BackupException::class.java) {
            BackupSerializer.deserialize(BackupSerializer.serialize(broken))
        }
        assertEquals(BackupError.BROKEN_REFERENCE, ex.error)
    }

    @Test
    fun `rejects non integer amount`() {
        val json = BackupSerializer.serialize(sampleBackup())
            .replace("\"limitMinor\": \"300000\"", "\"limitMinor\": \"3000.5\"")
        val ex = assertThrows(BackupException::class.java) {
            BackupSerializer.deserialize(json)
        }
        assertEquals(BackupError.INVALID_AMOUNT, ex.error)
    }

    @Test
    fun `rejects out of range date`() {
        val json = BackupSerializer.serialize(sampleBackup())
            .replace("\"transactionDate\": 20000", "\"transactionDate\": -100")
        val ex = assertThrows(BackupException::class.java) {
            BackupSerializer.deserialize(json)
        }
        assertEquals(BackupError.INVALID_DATE, ex.error)
    }
}
