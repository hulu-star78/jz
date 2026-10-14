package com.jianji.app.domain.repository

import com.jianji.app.domain.model.Transaction
import com.jianji.app.domain.model.TransactionDraft
import com.jianji.app.domain.model.TransactionFilter
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 交易仓库：账目的唯一写入口与读出口。
 *
 * 保存前执行校验（金额、日期、分类/账户存在性、转账规则）；
 * 所有写操作在数据库事务中完成。
 */
interface TransactionRepository {

    /** 新增或更新一笔账目，返回账目 id。校验失败抛出 [com.jianji.app.domain.validation.TransactionValidationException]。 */
    suspend fun save(draft: TransactionDraft): Long

    /** 删除一笔账目。 */
    suspend fun delete(id: Long)

    /** 查询单笔账目（一次性）。 */
    suspend fun findById(id: Long): Transaction?

    /** 观察全部账目（按日期倒序）。 */
    fun observeAll(): Flow<List<Transaction>>

    /** 观察日期区间内的账目（闭区间，按日期倒序）。 */
    fun observeByDateRange(start: LocalDate, end: LocalDate): Flow<List<Transaction>>

    /** 按组合条件观察账目。 */
    fun observeFiltered(filter: TransactionFilter): Flow<List<Transaction>>
}
