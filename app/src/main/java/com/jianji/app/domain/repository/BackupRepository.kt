package com.jianji.app.domain.repository

import com.jianji.app.data.backup.BackupCounts
import com.jianji.app.data.backup.BackupFile

/**
 * 备份仓库：导出、校验、恢复本地数据。
 *
 * 数据仅在用户选择的本地文件与本机数据库之间流动，不经过任何网络。
 */
interface BackupRepository {

    /** 导出当前全部数据为 JSON 字符串。 */
    suspend fun exportJson(): String

    /** 解析并校验备份文件内容（不写入数据库）；失败抛 [com.jianji.app.data.backup.BackupException]。 */
    suspend fun validate(json: String): BackupFile

    /** 恢复备份：覆盖当前数据，返回备份内容统计。失败抛 [com.jianji.app.data.backup.BackupException]。 */
    suspend fun restore(json: String): BackupCounts

    /** 生成建议的备份文件名。 */
    fun suggestedFileName(nowMillis: Long = System.currentTimeMillis()): String
}
