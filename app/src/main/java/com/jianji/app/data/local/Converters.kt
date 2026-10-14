package com.jianji.app.data.local

import androidx.room.TypeConverter
import com.jianji.app.domain.model.AccountType
import com.jianji.app.domain.model.CategoryType
import com.jianji.app.domain.model.TransactionType
import java.time.LocalDate

/**
 * Room 类型转换器。
 *
 * 枚举以 name 字符串持久化（可读性好，便于备份与调试）。
 * 禁止重命名枚举值：重命名会使历史数据 valueOf 失败，必须配套数据库 Migration。
 *
 * 日期以 epochDay（Long）持久化，规避时区问题。
 */
class Converters {

    @TypeConverter
    fun transactionTypeToString(value: TransactionType): String = value.name

    @TypeConverter
    fun stringToTransactionType(value: String): TransactionType =
        TransactionType.valueOf(value)

    @TypeConverter
    fun categoryTypeToString(value: CategoryType): String = value.name

    @TypeConverter
    fun stringToCategoryType(value: String): CategoryType = CategoryType.valueOf(value)

    @TypeConverter
    fun accountTypeToString(value: AccountType): String = value.name

    @TypeConverter
    fun stringToAccountType(value: String): AccountType = AccountType.valueOf(value)

    @TypeConverter
    fun localDateToEpochDay(date: LocalDate): Long = date.toEpochDay().toLong()

    @TypeConverter
    fun epochDayToLocalDate(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)
}
