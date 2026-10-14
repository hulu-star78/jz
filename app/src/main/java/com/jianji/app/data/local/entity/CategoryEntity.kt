package com.jianji.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jianji.app.domain.model.CategoryType

/**
 * 分类表。
 *
 * 规则：
 * - 分类永不物理删除，通过 isArchived 软归档，保证历史账目引用有效；
 * - isSystem 为预置分类，用户不可删除（也不可归档）；
 * - icon 为图标键名（如 "restaurant"），由 UI 层映射为具体图标资源；
 * - colorArgb 为可选的 ARGB 颜色，预留给 UI，避免未来为颜色单独做数据库迁移。
 */
@Entity(
    tableName = "categories",
    indices = [Index("type"), Index("sortOrder")],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val name: String,

    /** 图标键名，UI 层映射。 */
    val icon: String,

    val type: CategoryType,

    /** 是否系统预置分类。 */
    val isSystem: Boolean = false,

    /** 是否已归档（软删除）。 */
    val isArchived: Boolean = false,

    /** 展示排序，值越小越靠前。 */
    val sortOrder: Int = 0,

    /** 可选主题色（ARGB），预留给 UI。 */
    val colorArgb: Int? = null,
)
