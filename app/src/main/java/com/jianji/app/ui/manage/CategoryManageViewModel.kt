package com.jianji.app.ui.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jianji.app.domain.model.Category
import com.jianji.app.domain.model.CategoryType
import com.jianji.app.domain.repository.CategoryRepository
import com.jianji.app.domain.validation.ResourceValidationException
import com.jianji.app.ui.util.message
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoryManageUiState(
    val loading: Boolean = true,
    val type: CategoryType = CategoryType.EXPENSE,
    val categories: List<Category> = emptyList(),
)

class CategoryManageViewModel(
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val type = MutableStateFlow(CategoryType.EXPENSE)

    /** 操作反馈消息（错误或成功提示），由 UI 展示后消费。 */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<CategoryManageUiState> = type
        .flatMapLatest { current ->
            categoryRepository.observeActiveByType(current).map { active ->
                CategoryManageUiState(
                    loading = false,
                    type = current,
                    categories = active,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryManageUiState())

    fun onTypeChange(value: CategoryType) {
        type.value = value
    }

    /** 新建分类；返回错误信息（null 表示成功）。 */
    suspend fun createCategory(name: String, icon: String): String? = try {
        categoryRepository.createCategory(name.trim(), icon, type.value)
        null
    } catch (e: ResourceValidationException) {
        e.error.message()
    } catch (e: Exception) {
        "保存失败：${e.message ?: "未知错误"}"
    }

    fun archiveCategory(category: Category) {
        viewModelScope.launch {
            try {
                categoryRepository.archiveCategory(category.id)
                _message.value = "已归档「${category.name}」"
            } catch (e: ResourceValidationException) {
                _message.value = e.error.message()
            } catch (e: Exception) {
                _message.value = "归档失败：${e.message ?: "未知错误"}"
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
