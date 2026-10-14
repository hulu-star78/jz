package com.jianji.app.domain.validation

import com.jianji.app.domain.model.DateUtils
import com.jianji.app.domain.model.TransactionDraft
import com.jianji.app.domain.model.TransactionType
import java.time.LocalDate

/** 交易校验错误码。 */
enum class ValidationError {
    /** 金额必须大于 0。 */
    AMOUNT_NOT_POSITIVE,

    /** 日期超出允许范围（早于 1970 或晚于 2199）。 */
    DATE_OUT_OF_RANGE,

    /** 日期晚于今天：记账不允许预录未来账目。 */
    FUTURE_DATE,

    /** 收入/支出必须选择分类。 */
    CATEGORY_REQUIRED,

    /** 必须选择账户。 */
    ACCOUNT_REQUIRED,

    /** 转账必须选择目标账户。 */
    TRANSFER_TARGET_REQUIRED,

    /** 转账源账户与目标账户不能相同。 */
    TRANSFER_TARGET_SAME_ACCOUNT,

    /** 备注超长。 */
    NOTE_TOO_LONG,

    /** 编辑时原记录不存在。 */
    TRANSACTION_NOT_FOUND,

    /** 账户名称非法（空或过长）。 */
    ACCOUNT_NAME_INVALID,

    /** 分类名称非法（空或过长）。 */
    CATEGORY_NAME_INVALID,

    /** 系统预置分类不允许删除。 */
    CATEGORY_SYSTEM,

    /** 不允许归档最后一个可用账户。 */
    ACCOUNT_ARCHIVE_LAST,

    /** 预算金额必须大于 0。 */
    BUDGET_AMOUNT_NOT_POSITIVE,
}

/** 校验结果。 */
sealed interface ValidationResult {
    object Valid : ValidationResult
    data class Invalid(val error: ValidationError) : ValidationResult
}

/** 交易校验异常，携带错误码供 UI 层映射提示文案。 */
class TransactionValidationException(val error: ValidationError) :
    Exception("Transaction validation failed: $error")

/** 账户/分类/预算校验异常。 */
class ResourceValidationException(val error: ValidationError) :
    Exception("Resource validation failed: $error")

/**
 * 交易草稿的纯函数校验器（不依赖数据库，便于单元测试）。
 *
 * 存在性校验（分类/账户是否存在）由 Repository 在事务内完成。
 */
object TransactionValidator {

    const val MAX_NOTE_LENGTH = 200

    fun validate(draft: TransactionDraft): ValidationResult {
        if (draft.amountMinor <= 0L) return ValidationResult.Invalid(ValidationError.AMOUNT_NOT_POSITIVE)
        if (!DateUtils.isValidDate(draft.date)) return ValidationResult.Invalid(ValidationError.DATE_OUT_OF_RANGE)
        if (draft.date.isAfter(LocalDate.now())) {
            return ValidationResult.Invalid(ValidationError.FUTURE_DATE)
        }
        if (draft.note != null && draft.note.length > MAX_NOTE_LENGTH) {
            return ValidationResult.Invalid(ValidationError.NOTE_TOO_LONG)
        }
        return when (draft.type) {
            TransactionType.INCOME, TransactionType.EXPENSE -> {
                if (draft.categoryId == null) {
                    ValidationResult.Invalid(ValidationError.CATEGORY_REQUIRED)
                } else {
                    ValidationResult.Valid
                }
            }

            TransactionType.TRANSFER -> {
                when {
                    draft.accountId <= 0L -> ValidationResult.Invalid(ValidationError.ACCOUNT_REQUIRED)
                    draft.toAccountId == null -> ValidationResult.Invalid(ValidationError.TRANSFER_TARGET_REQUIRED)
                    draft.toAccountId == draft.accountId ->
                        ValidationResult.Invalid(ValidationError.TRANSFER_TARGET_SAME_ACCOUNT)

                    else -> ValidationResult.Valid
                }
            }
        }
    }
}

/** 账户与分类的基础校验。 */
object ResourceValidator {

    const val MAX_NAME_LENGTH = 20

    fun validateName(name: String?, isCategory: Boolean): ValidationResult {
        val trimmed = name?.trim().orEmpty()
        val error = if (isCategory) ValidationError.CATEGORY_NAME_INVALID else ValidationError.ACCOUNT_NAME_INVALID
        return if (trimmed.isEmpty() || trimmed.length > MAX_NAME_LENGTH) {
            ValidationResult.Invalid(error)
        } else {
            ValidationResult.Valid
        }
    }
}
