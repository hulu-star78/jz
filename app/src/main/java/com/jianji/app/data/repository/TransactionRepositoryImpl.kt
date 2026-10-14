package com.jianji.app.data.repository

import androidx.room.withTransaction
import com.jianji.app.data.local.JianjiDatabase
import com.jianji.app.data.local.dao.AccountDao
import com.jianji.app.data.local.dao.CategoryDao
import com.jianji.app.data.local.dao.TransactionDao
import com.jianji.app.data.local.entity.AccountEntity
import com.jianji.app.data.local.entity.CategoryEntity
import com.jianji.app.data.local.entity.TransactionEntity
import com.jianji.app.domain.model.Account
import com.jianji.app.domain.model.Category
import com.jianji.app.domain.model.DateUtils
import com.jianji.app.domain.model.Money
import com.jianji.app.domain.model.Transaction
import com.jianji.app.domain.model.TransactionDraft
import com.jianji.app.domain.model.TransactionFilter
import com.jianji.app.domain.model.TransactionType
import com.jianji.app.domain.repository.TransactionRepository
import com.jianji.app.domain.validation.TransactionValidationException
import com.jianji.app.domain.validation.TransactionValidator
import com.jianji.app.domain.validation.ValidationError
import com.jianji.app.domain.validation.ValidationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * 交易仓库实现。
 *
 * 写操作流程：纯逻辑校验 -> 事务内存在性校验 -> 落库。
 * 删除后所有统计、余额、预算进度均由 SQL 实时重算，无任何缓存需要维护。
 */
class TransactionRepositoryImpl(
    private val database: JianjiDatabase,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val accountDao: AccountDao,
) : TransactionRepository {

    override suspend fun save(draft: TransactionDraft): Long = database.withTransaction {
        when (val result = TransactionValidator.validate(draft)) {
            is ValidationResult.Invalid -> throw TransactionValidationException(result.error)
            ValidationResult.Valid -> Unit
        }

        // 存在性校验：分类必须存在且未归档；账户必须存在。
        if (draft.type != TransactionType.TRANSFER) {
            val categoryId = draft.categoryId
                ?: throw TransactionValidationException(ValidationError.CATEGORY_REQUIRED)
            val category = categoryDao.findById(categoryId)
                ?: throw TransactionValidationException(ValidationError.CATEGORY_REQUIRED)
            if (category.isArchived) {
                throw TransactionValidationException(ValidationError.CATEGORY_REQUIRED)
            }
        }
        val sourceAccount = accountDao.findById(draft.accountId)
            ?: throw TransactionValidationException(ValidationError.ACCOUNT_REQUIRED)
        if (sourceAccount.isArchived) {
            throw TransactionValidationException(ValidationError.ACCOUNT_REQUIRED)
        }
        if (draft.type == TransactionType.TRANSFER) {
            val targetId = draft.toAccountId
                ?: throw TransactionValidationException(ValidationError.TRANSFER_TARGET_REQUIRED)
            val target = accountDao.findById(targetId)
                ?: throw TransactionValidationException(ValidationError.TRANSFER_TARGET_REQUIRED)
            if (target.isArchived) {
                throw TransactionValidationException(ValidationError.TRANSFER_TARGET_REQUIRED)
            }
        }

        val existing = if (draft.id != 0L) transactionDao.findById(draft.id) else null
        if (draft.id != 0L && existing == null) {
            throw TransactionValidationException(ValidationError.TRANSACTION_NOT_FOUND)
        }

        val now = System.currentTimeMillis()
        val isTransfer = draft.type == TransactionType.TRANSFER
        val entity = TransactionEntity(
            id = draft.id,
            amountMinor = draft.amountMinor,
            type = draft.type,
            transactionDate = DateUtils.toEpochDay(draft.date),
            categoryId = if (isTransfer) null else draft.categoryId,
            accountId = draft.accountId,
            toAccountId = if (isTransfer) draft.toAccountId else null,
            note = draft.note?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        if (existing == null) {
            transactionDao.insert(entity)
        } else {
            transactionDao.update(entity)
            draft.id
        }
    }

    override suspend fun delete(id: Long) {
        transactionDao.deleteById(id)
    }

    override suspend fun findById(id: Long): Transaction? =
        transactionDao.findById(id)?.toDomain()

    override fun observeAll(): Flow<List<Transaction>> =
        transactionDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeByDateRange(start: LocalDate, end: LocalDate): Flow<List<Transaction>> =
        transactionDao.observeByDateRange(
            DateUtils.toEpochDay(start),
            DateUtils.toEpochDay(end),
        ).map { list -> list.map { it.toDomain() } }

    override fun observeFiltered(filter: TransactionFilter): Flow<List<Transaction>> =
        transactionDao.observeFiltered(
            startEpochDay = filter.start?.let { DateUtils.toEpochDay(it) },
            endEpochDay = filter.end?.let { DateUtils.toEpochDay(it) },
            type = filter.type,
            categoryId = filter.categoryId,
            accountId = filter.accountId,
            keyword = filter.keyword?.trim()?.takeIf { it.isNotEmpty() },
        ).map { list -> list.map { it.toDomain() } }
}

/** Entity -> Domain 映射。金额统一包装为 Money（分）。 */
internal fun TransactionEntity.toDomain(): Transaction = Transaction(
    id = id,
    amount = Money.fromMinor(amountMinor),
    type = type,
    date = DateUtils.fromEpochDay(transactionDate),
    categoryId = categoryId,
    accountId = accountId,
    toAccountId = toAccountId,
    note = note,
)

/** Entity -> Domain 映射（分类）。 */
internal fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    icon = icon,
    type = type,
    isSystem = isSystem,
    isArchived = isArchived,
    sortOrder = sortOrder,
    colorArgb = colorArgb,
)

/** Entity -> Domain 映射（账户）。 */
internal fun AccountEntity.toDomain(): Account = Account(
    id = id,
    name = name,
    icon = icon,
    type = type,
    initialBalance = Money.fromMinor(initialBalanceMinor),
    isArchived = isArchived,
    createdAt = createdAt,
    colorArgb = colorArgb,
)
