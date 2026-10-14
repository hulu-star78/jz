package com.jianji.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 预算表：按自然月（yearMonth，"YYYY-MM"）设置。
 *
 * - categoryId 为 null 表示月总预算，否则为对应支出分类的预算；
 * - limitMinor 为预算上限，单位：分。
 *
 * 注意：SQLite 唯一索引不约束 NULL，因此 (yearMonth, categoryId=NULL) 的唯一性
 * 由 BudgetRepository 在事务内保证（先查后插/改），此处仅建普通索引加速查询。
 */
@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("yearMonth"), Index("categoryId"), Index(value = ["yearMonth", "categoryId"])],
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** 自然月键，格式 "YYYY-MM"。 */
    val yearMonth: String,

    /** null 表示月总预算。 */
    val categoryId: Long? = null,

    /** 预算上限，单位：分。 */
    val limitMinor: Long,
)
