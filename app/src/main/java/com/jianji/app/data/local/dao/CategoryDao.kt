package com.jianji.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.jianji.app.data.local.entity.CategoryEntity
import com.jianji.app.domain.model.CategoryType
import kotlinx.coroutines.flow.Flow

/**
 * 分类 DAO。
 *
 * 分类采用软删除：archiveById 只置 isArchived，历史账目引用不失效。
 */
@Dao
interface CategoryDao {

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity): Int

    @Query("UPDATE categories SET isArchived = 1 WHERE id = :id")
    suspend fun archiveById(id: Long): Int

    @Query("UPDATE categories SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Int): Int

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun findById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories ORDER BY type, sortOrder, id")
    fun observeAll(): Flow<List<CategoryEntity>>

    /** 一次性快照（导出备份用）。 */
    @Query("SELECT * FROM categories ORDER BY type, sortOrder, id")
    suspend fun getAllSnapshot(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE isArchived = 0 ORDER BY type, sortOrder, id")
    fun observeActive(): Flow<List<CategoryEntity>>

    @Query(
        "SELECT * FROM categories WHERE type = :type AND isArchived = 0 " +
            "ORDER BY sortOrder, id"
    )
    fun observeActiveByType(type: CategoryType): Flow<List<CategoryEntity>>

    /** 某类型下未归档分类数量（用于排序与兜底校验）。 */
    @Query("SELECT COUNT(*) FROM categories WHERE type = :type AND isArchived = 0")
    suspend fun countActiveByType(type: CategoryType): Int

    /** 取某类型当前最大 sortOrder（新建分类时追加到末尾）。 */
    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM categories WHERE type = :type")
    suspend fun maxSortOrder(type: CategoryType): Int
}
