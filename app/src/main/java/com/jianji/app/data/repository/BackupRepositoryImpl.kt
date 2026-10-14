package com.jianji.app.data.repository

import com.jianji.app.data.backup.BackupCounts
import com.jianji.app.data.backup.BackupException
import com.jianji.app.data.backup.BackupFile
import com.jianji.app.data.backup.BackupManager
import com.jianji.app.data.backup.BackupSerializer
import com.jianji.app.domain.repository.BackupRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class BackupRepositoryImpl(
    private val backupManager: BackupManager,
) : BackupRepository {

    override suspend fun exportJson(): String =
        BackupSerializer.serialize(backupManager.export())

    override suspend fun validate(json: String): BackupFile = backupManager.validate(json)

    override suspend fun restore(json: String): BackupCounts {
        val backup = backupManager.validate(json)
        backupManager.restore(backup)
        return backup.counts
    }

    override fun suggestedFileName(nowMillis: Long): String {
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
        val stamp = Instant.ofEpochMilli(nowMillis)
            .atZone(ZoneId.systemDefault())
            .format(formatter)
        return "jianji_backup_$stamp.json"
    }
}

/** 备份异常的中文提示（UI 层复用）。 */
fun BackupException.userMessage(): String = when (error) {
    com.jianji.app.data.backup.BackupError.INVALID_JSON -> "文件不是有效的 JSON 备份"
    com.jianji.app.data.backup.BackupError.MALFORMED_STRUCTURE -> "备份文件结构不完整"
    com.jianji.app.data.backup.BackupError.UNSUPPORTED_VERSION -> "备份版本不受支持，请升级 App 后重试"
    com.jianji.app.data.backup.BackupError.INVALID_AMOUNT -> "备份中存在非法金额"
    com.jianji.app.data.backup.BackupError.INVALID_ENUM -> "备份中存在无法识别的类型"
    com.jianji.app.data.backup.BackupError.INVALID_DATE -> "备份中存在非法日期"
    com.jianji.app.data.backup.BackupError.BROKEN_REFERENCE -> "备份数据关联不完整，已终止恢复"
}
