package com.jianji.app.domain.model

/**
 * 交易类型。
 *
 * 重要：枚举 name 会通过 TypeConverter 以字符串形式持久化到数据库，
 * 统计 SQL 中也以字面量 'INCOME' / 'EXPENSE' / 'TRANSFER' 出现。
 * 禁止重命名或删除枚举值；如必须调整，需编写数据库 Migration。
 */
enum class TransactionType {
    /** 收入：计入收入统计。 */
    INCOME,

    /** 支出：计入支出统计与预算消耗。 */
    EXPENSE,

    /** 账户间转账：不影响收入/支出统计，但影响两个账户的余额。 */
    TRANSFER,
}

/** 分类类型：收入或支出。转账不挂分类。 */
enum class CategoryType {
    INCOME,
    EXPENSE,
}

/** 账户类型。 */
enum class AccountType {
    CASH,
    BANK,
    E_WALLET,
    CREDIT_CARD,
    OTHER,
}
