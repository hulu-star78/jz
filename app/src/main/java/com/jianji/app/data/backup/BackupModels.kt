package com.jianji.app.data.backup

import com.jianji.app.data.local.entity.AccountEntity
import com.jianji.app.data.local.entity.BudgetEntity
import com.jianji.app.data.local.entity.CategoryEntity
import com.jianji.app.data.local.entity.TransactionEntity

/**
 * 备份文件的数据模型。
 *
 * 设计约定：
 * - 金额（amountMinor / initialBalanceMinor / limitMinor）在 JSON 中以字符串存储，
 *   全程不经过 Double/Float，保证导出-导入往返零精度损失；
 * - 日期（transactionDate）为 epochDay 整数，不受时区影响；
 * - formatVersion 用于后续格式演进，恢复时严格校验。
 */
data class BackupFile(
    val formatVersion: Int,
    val appVersion: String,
    val exportedAt: Long,
    val counts: BackupCounts,
    val categories: List<CategoryEntity>,
    val accounts: List<AccountEntity>,
    val transactions: List<TransactionEntity>,
    val budgets: List<BudgetEntity>,
)

/** 备份内容统计（用于导出信息展示与恢复前确认）。 */
data class BackupCounts(
    val categories: Int,
    val accounts: Int,
    val transactions: Int,
    val budgets: Int,
) {
    val total: Int get() = categories + accounts + transactions + budgets
}

/** 备份错误码。 */
enum class BackupError {
    /** 文件不是合法 JSON。 */
    INVALID_JSON,

    /** 缺少必需字段或结构不符。 */
    MALFORMED_STRUCTURE,

    /** 格式版本不受支持。 */
    UNSUPPORTED_VERSION,

    /** 金额字段非法（非整数字符串）。 */
    INVALID_AMOUNT,

    /** 枚举值非法。 */
    INVALID_ENUM,

    /** 日期超出允许范围。 */
    INVALID_DATE,

    /** 外键引用不完整（如交易引用了不存在的账户）。 */
    BROKEN_REFERENCE,
}

/** 备份异常：携带错误码，UI 层映射为中文提示。 */
class BackupException(val error: BackupError, message: String) : Exception(message)
