package com.jianji.app.domain.repository

import com.jianji.app.domain.model.Category
import com.jianji.app.domain.model.CategoryType
import kotlinx.coroutines.flow.Flow

/**
 * 分类仓库：分类采用软删除（归档），保证历史账目的分类引用始终有效。
 */
interface CategoryRepository {

    /** 观察全部分类（含归档，供历史账目显示名称）。 */
    fun observeAll(): Flow<List<Category>>

    /** 观察未归档分类。 */
    fun observeActive(): Flow<List<Category>>

    /** 观察某类型的未归档分类。 */
    fun observeActiveByType(type: CategoryType): Flow<List<Category>>

    /** 新建自定义分类，返回 id。 */
    suspend fun createCategory(
        name: String,
        icon: String,
        type: CategoryType,
        colorArgb: Int? = null,
    ): Long

    /** 更新分类。 */
    suspend fun updateCategory(category: Category)

    /** 归档分类（软删除）。系统预置分类不可归档。 */
    suspend fun archiveCategory(id: Long)
}
