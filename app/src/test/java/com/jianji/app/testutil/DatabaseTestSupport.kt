package com.jianji.app.testutil

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jianji.app.data.local.JianjiDatabase
import com.jianji.app.data.local.entity.AccountEntity
import com.jianji.app.data.local.entity.CategoryEntity
import com.jianji.app.domain.model.AccountType
import com.jianji.app.domain.model.CategoryType
import com.jianji.app.domain.model.TransactionType
import org.junit.After
import org.junit.Before
import java.time.LocalDate

/**
 * Robolectric + 内存 Room 数据库测试基类。
 *
 * 注意：不挂 SeedDataCallback，测试自行构造数据，保证用例可控。
 */
abstract class DatabaseTestSupport {

    protected lateinit var database: JianjiDatabase

    @Before
    open fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, JianjiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    open fun tearDown() {
        database.close()
    }

    // ---------- 测试数据构造 ----------

    protected suspend fun insertCategory(
        name: String,
        type: CategoryType,
        isSystem: Boolean = false,
    ): Long = database.categoryDao().insert(
        CategoryEntity(name = name, icon = "test", type = type, isSystem = isSystem),
    )

    protected suspend fun insertAccount(
        name: String,
        type: AccountType = AccountType.CASH,
        initialBalanceMinor: Long = 0L,
    ): Long = database.accountDao().insert(
        AccountEntity(
            name = name,
            icon = "test",
            type = type,
            initialBalanceMinor = initialBalanceMinor,
            createdAt = System.currentTimeMillis(),
        ),
    )

    protected suspend fun insertTransaction(
        amountMinor: Long,
        type: TransactionType,
        date: LocalDate,
        categoryId: Long? = null,
        accountId: Long,
        toAccountId: Long? = null,
        note: String? = null,
    ): Long = database.transactionDao().insert(
        com.jianji.app.data.local.entity.TransactionEntity(
            amountMinor = amountMinor,
            type = type,
            transactionDate = date.toEpochDay().toLong(),
            categoryId = categoryId,
            accountId = accountId,
            toAccountId = toAccountId,
            note = note,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        ),
    )
}
