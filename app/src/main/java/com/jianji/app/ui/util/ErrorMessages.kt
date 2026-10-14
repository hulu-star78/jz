package com.jianji.app.ui.util

import com.jianji.app.domain.validation.ValidationError

/** 校验错误码 → 中文提示（UI 层统一出口）。 */
fun ValidationError.message(): String = when (this) {
    ValidationError.AMOUNT_NOT_POSITIVE -> "请输入大于 0 的金额"
    ValidationError.DATE_OUT_OF_RANGE -> "日期不在允许范围内"
    ValidationError.FUTURE_DATE -> "不能选择未来的日期"
    ValidationError.CATEGORY_REQUIRED -> "请选择分类"
    ValidationError.ACCOUNT_REQUIRED -> "请选择账户"
    ValidationError.TRANSFER_TARGET_REQUIRED -> "请选择转入账户"
    ValidationError.TRANSFER_TARGET_SAME_ACCOUNT -> "转入账户不能与转出账户相同"
    ValidationError.NOTE_TOO_LONG -> "备注过长"
    ValidationError.TRANSACTION_NOT_FOUND -> "原记录不存在，可能已被删除"
    ValidationError.ACCOUNT_NAME_INVALID -> "账户名称不能为空且不超过 20 字"
    ValidationError.CATEGORY_NAME_INVALID -> "分类名称不能为空且不超过 20 字"
    ValidationError.CATEGORY_SYSTEM -> "系统预置分类不可删除"
    ValidationError.ACCOUNT_ARCHIVE_LAST -> "至少需要保留一个可用账户"
    ValidationError.BUDGET_AMOUNT_NOT_POSITIVE -> "预算金额必须大于 0"
}
