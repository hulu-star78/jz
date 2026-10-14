package com.jianji.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jianji.app.domain.model.TransactionType

/**
 * 交易记录表：App 的唯一财务事实来源。
 *
 * 设计约定：
 * - [amountMinor] 以「分」为单位的 Long 整数，禁止 Float/Double；
 * - [transactionDate] 为 LocalDate 的 epochDay（Long），不受设备时区影响；
 * - TRANSFER 记录使用 [toAccountId] 指向目标账户，且 categoryId 必须为空；
 * - 分类被物理删除时 categoryId 置空（SET_NULL），账目不丢失；
 * - 账户不允许物理删除（RESTRICT），只能归档，保证账目引用完整。
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["toAccountId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("transactionDate"),
        Index("categoryId"),
        Index("accountId"),
        Index("toAccountId"),
        Index(value = ["transactionDate", "type"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** 金额，单位：分。 */
    val amountMinor: Long,

    val type: TransactionType,

    /** 交易日期，LocalDate.toEpochDay()。 */
    val transactionDate: Long,

    /** 收入/支出的分类；转账时为 null。 */
    val categoryId: Long? = null,

    /** 来源账户（收入/支出的入账账户、转账的转出账户）。 */
    val accountId: Long,

    /** 转账目标账户；非转账时为 null。 */
    val toAccountId: Long? = null,

    val note: String? = null,

    /** 创建时间戳（毫秒）。 */
    val createdAt: Long,

    /** 最近更新时间戳（毫秒）。 */
    val updatedAt: Long,
)
