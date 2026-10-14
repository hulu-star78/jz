package com.jianji.app.data.repository

import com.jianji.app.data.local.dao.CategoryDao
import com.jianji.app.data.local.entity.CategoryEntity
import com.jianji.app.domain.model.Category
import com.jianji.app.domain.model.CategoryType
import com.jianji.app.domain.repository.CategoryRepository
import com.jianji.app.domain.validation.ResourceValidationException
import com.jianji.app.domain.validation.ResourceValidator
import com.jianji.app.domain.validation.ValidationError
import com.jianji.app.domain.validation.ValidationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 分类仓库实现：分类软删除（归档），系统预置分类不可归档。
 */
class CategoryRepositoryImpl(
    private val categoryDao: CategoryDao,
) : CategoryRepository {

    override fun observeAll(): Flow<List<Category>> =
        categoryDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeActive(): Flow<List<Category>> =
        categoryDao.observeActive().map { list -> list.map { it.toDomain() } }

    override fun observeActiveByType(type: CategoryType): Flow<List<Category>> =
        categoryDao.observeActiveByType(type).map { list -> list.map { it.toDomain() } }

    override suspend fun createCategory(
        name: String,
        icon: String,
        type: CategoryType,
        colorArgb: Int?,
    ): Long {
        when (val result = ResourceValidator.validateName(name, isCategory = true)) {
            is ValidationResult.Invalid -> throw ResourceValidationException(result.error)
            ValidationResult.Valid -> Unit
        }
        val sortOrder = categoryDao.maxSortOrder(type) + 1
        return categoryDao.insert(
            CategoryEntity(
                name = name.trim(),
                icon = icon,
                type = type,
                isSystem = false,
                isArchived = false,
                sortOrder = sortOrder,
                colorArgb = colorArgb,
            ),
        )
    }

    override suspend fun updateCategory(category: Category) {
        when (val result = ResourceValidator.validateName(category.name, isCategory = true)) {
            is ValidationResult.Invalid -> throw ResourceValidationException(result.error)
            ValidationResult.Valid -> Unit
        }
        categoryDao.update(
            CategoryEntity(
                id = category.id,
                name = category.name.trim(),
                icon = category.icon,
                type = category.type,
                isSystem = category.isSystem,
                isArchived = category.isArchived,
                sortOrder = category.sortOrder,
                colorArgb = category.colorArgb,
            ),
        )
    }

    override suspend fun archiveCategory(id: Long) {
        val category = categoryDao.findById(id)
            ?: throw ResourceValidationException(ValidationError.CATEGORY_NAME_INVALID)
        if (category.isSystem) {
            throw ResourceValidationException(ValidationError.CATEGORY_SYSTEM)
        }
        categoryDao.archiveById(id)
    }
}
