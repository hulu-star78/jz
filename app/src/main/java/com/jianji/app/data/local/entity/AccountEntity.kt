package com.jianji.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.jianji.app.domain.model.AccountType

/**
 * 账户表。
 *
 * 余额公式（实时计算，禁止保存 currentBalance）：
 * initialBalanceMinor + 收入 - 支出 - 转出 + 转入。
 * 信用卡等负债账户可用负的 initialBalanceMinor 表示建账时已透支金额。
 *
 * 账户永不物理删除（交易表对其 RESTRICT），只能归档。
 */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val name: String,

    /** 图标键名，UI 层映射。 */
    val icon: String,

    val type: AccountType = AccountType.CASH,

    /** 初始余额，单位：分。 */
    val initialBalanceMinor: Long = 0L,

    /** 是否已归档（软删除）。 */
    val isArchived: Boolean = false,

    /** 创建时间戳（毫秒）。 */
    val createdAt: Long,

    /** 可选主题色（ARGB），预留给 UI。 */
    val colorArgb: Int? = null,
)
