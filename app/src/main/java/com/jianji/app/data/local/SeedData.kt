package com.jianji.app.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 预置数据：常用收入/支出分类与常用账户。
 *
 * 在数据库首次创建时执行（[SeedDataCallback]），
 * 使用原生 SQL 插入（isSystem=1 标记为预置，用户不可删除）。
 */
object SeedData {

    /** 预置支出分类：name, icon, sortOrder。 */
    private val expenseCategories = listOf(
        Triple("餐饮", "restaurant", 0),
        Triple("交通", "directions_bus", 1),
        Triple("购物", "shopping_bag", 2),
        Triple("居住", "home", 3),
        Triple("娱乐", "sports_esports", 4),
        Triple("医疗", "local_hospital", 5),
        Triple("教育", "school", 6),
        Triple("通讯", "phone_iphone", 7),
        Triple("人情", "card_giftcard", 8),
        Triple("其他", "more_horiz", 9),
    )

    /** 预置收入分类：name, icon, sortOrder。 */
    private val incomeCategories = listOf(
        Triple("工资", "payments", 0),
        Triple("奖金", "emoji_events", 1),
        Triple("理财", "trending_up", 2),
        Triple("兼职", "work", 3),
        Triple("其他收入", "attach_money", 4),
    )

    /** 预置账户：name, icon, type。 */
    private val accounts = listOf(
        Triple("现金", "payments", "CASH"),
        Triple("银行卡", "account_balance", "BANK"),
        Triple("微信", "chat", "E_WALLET"),
        Triple("支付宝", "currency_yuan", "E_WALLET"),
        Triple("信用卡", "credit_card", "CREDIT_CARD"),
    )

    fun insertPresets(db: SupportSQLiteDatabase) {
        expenseCategories.forEach { (name, icon, sortOrder) ->
            db.execSQL(
                "INSERT INTO categories (name, icon, type, isSystem, isArchived, sortOrder, colorArgb) " +
                    "VALUES (?, ?, 'EXPENSE', 1, 0, ?, NULL)",
                arrayOf<Any>(name, icon, sortOrder),
            )
        }
        incomeCategories.forEachIndexed { index, (name, icon, _) ->
            db.execSQL(
                "INSERT INTO categories (name, icon, type, isSystem, isArchived, sortOrder, colorArgb) " +
                    "VALUES (?, ?, 'INCOME', 1, 0, ?, NULL)",
                arrayOf<Any>(name, icon, index),
            )
        }
        accounts.forEach { (name, icon, type) ->
            db.execSQL(
                "INSERT INTO accounts (name, icon, type, initialBalanceMinor, isArchived, createdAt, colorArgb) " +
                    "VALUES (?, ?, ?, 0, 0, ?, NULL)",
                arrayOf<Any>(name, icon, type, System.currentTimeMillis()),
            )
        }
    }
}

/** 数据库首次创建时写入预置分类与账户。 */
class SeedDataCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        SeedData.insertPresets(db)
    }
}
