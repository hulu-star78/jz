package com.jianji.app.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jianji.app.domain.model.Account
import com.jianji.app.domain.model.Category
import com.jianji.app.domain.model.CategoryType
import com.jianji.app.domain.model.Money
import com.jianji.app.domain.model.Transaction
import com.jianji.app.domain.model.TransactionDraft
import com.jianji.app.domain.model.TransactionFilter
import com.jianji.app.domain.model.TransactionType
import com.jianji.app.domain.repository.AccountRepository
import com.jianji.app.domain.repository.CategoryRepository
import com.jianji.app.domain.repository.TransactionRepository
import com.jianji.app.domain.validation.TransactionValidationException
import com.jianji.app.domain.validation.ValidationError
import com.jianji.app.ui.components.KeypadKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 记一笔 / 编辑账目 的 UI 状态。
 *
 * amountInput 保存用户原始输入串（如 "12.5"），保存时才经 Money.parse 转换，
 * 全链路无 Double/Float。
 */
data class AddEditUiState(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val type: TransactionType = TransactionType.EXPENSE,
    val amountInput: String = "",
    val selectedCategoryId: Long? = null,
    val date: LocalDate = LocalDate.now(),
    val accountId: Long = 0L,
    val toAccountId: Long? = null,
    val note: String = "",
    val expenseCategories: List<Category> = emptyList(),
    val incomeCategories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val saving: Boolean = false,
    val saved: Boolean = false,
    val deleted: Boolean = false,
    val error: ValidationError? = null,
) {
    val categories: List<Category>
        get() = if (type == TransactionType.INCOME) incomeCategories else expenseCategories

    val selectedCategory: Category?
        get() = categories.firstOrNull { it.id == selectedCategoryId }

    val selectedAccount: Account?
        get() = accounts.firstOrNull { it.id == accountId }

    val selectedToAccount: Account?
        get() = accounts.firstOrNull { it.id == toAccountId }

    val amountValid: Boolean
        get() = Money.parse(amountInput)?.let { it.minor > 0 } == true

    val canSave: Boolean
        get() = amountValid && accountId != 0L &&
            (type == TransactionType.TRANSFER || selectedCategoryId != null) &&
            (type != TransactionType.TRANSFER || toAccountId != null)
}

class AddEditTransactionViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val transactionId: Long?,
) : ViewModel() {

    private val _state = MutableStateFlow(AddEditUiState())
    val state: StateFlow<AddEditUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val accounts = accountRepository.observeActiveAccounts().first()
            val expense = categoryRepository.observeActiveByType(CategoryType.EXPENSE).first()
            val income = categoryRepository.observeActiveByType(CategoryType.INCOME).first()
            // 编辑态先取回原账目，再一次性发布完整状态，避免中间态闪烁
            val existing = transactionId?.let { transactionRepository.findById(it) }
            _state.value = AddEditUiState(
                loading = false,
                isEdit = existing != null,
                type = existing?.type ?: TransactionType.EXPENSE,
                amountInput = existing?.let { formatMinor(it.amount.minor) } ?: "",
                selectedCategoryId = existing?.categoryId,
                date = existing?.date ?: LocalDate.now(),
                accountId = existing?.accountId ?: accounts.firstOrNull()?.id ?: 0L,
                toAccountId = existing?.toAccountId,
                note = existing?.note.orEmpty(),
                accounts = accounts,
                expenseCategories = expense,
                incomeCategories = income,
            )
        }
    }

    /** Long 分 → 输入串（最多两位小数，去掉多余的 0）。 */
    private fun formatMinor(minor: Long): String {
        val negative = minor < 0
        val abs = if (negative) -minor else minor
        val yuan = abs / 100
        val cents = abs % 100
        val text = if (cents == 0L) "$yuan" else "$yuan.${cents.toString().padStart(2, '0')}"
        return if (negative) "-$text" else text
    }

    fun onTypeChange(type: TransactionType) {
        _state.update { it.copy(type = type, error = null) }
    }

    fun onCategorySelected(categoryId: Long) {
        _state.update { it.copy(selectedCategoryId = categoryId, error = null) }
    }

    fun onDateChange(date: LocalDate) {
        _state.update { it.copy(date = date) }
    }

    fun onAccountSelected(accountId: Long) {
        _state.update { it.copy(accountId = accountId) }
    }

    fun onToAccountSelected(accountId: Long) {
        _state.update { it.copy(toAccountId = accountId, error = null) }
    }

    fun onNoteChange(note: String) {
        if (note.length <= com.jianji.app.domain.validation.TransactionValidator.MAX_NOTE_LENGTH) {
            _state.update { it.copy(note = note) }
        }
    }

    /** 数字键盘输入：最多两位小数，整数部分最多 9 位。 */
    fun onKeypadKey(key: KeypadKey) {
        _state.update { state ->
            state.copy(amountInput = applyKey(state.amountInput, key), error = null)
        }
    }

    private fun applyKey(input: String, key: KeypadKey): String = when (key) {
        is KeypadKey.Digit -> {
            if (input.contains('.')) {
                val decimals = input.substringAfter('.')
                if (decimals.length >= 2) input else input + key.value
            } else {
                when {
                    input == "0" -> key.value.toString()
                    input == "-0" -> "-${key.value}"
                    input.length >= 10 -> input
                    else -> input + key.value
                }
            }
        }
        KeypadKey.Dot -> when {
            input.contains('.') -> input
            input.isEmpty() -> "0."
            else -> "$input."
        }
        KeypadKey.Backspace -> input.dropLast(1)
        // 一次输入两个 0：遵守两位小数与整数位上限（放不下时补足到上限）
        KeypadKey.DoubleZero -> {
            if (input.contains('.')) {
                val decimals = input.substringAfter('.')
                val allowed = 2 - decimals.length
                if (allowed <= 0) input else input + "0".repeat(allowed)
            } else {
                val room = 10 - input.length
                if (room <= 0) input else input + "0".repeat(minOf(2, room))
            }
        }
        // 清空已输入金额
        KeypadKey.Clear -> ""
        // 「收起」「保存」由 UI 层处理，不改变输入内容
        KeypadKey.Hide, KeypadKey.Save -> input
    }

    fun save() {
        val state = _state.value
        if (state.saving) return
        // 表单不完整时给出具体错误（保存按钮保持可点击，仅样式置灰）
        val missing = when {
            (Money.parse(state.amountInput)?.minor ?: 0L) <= 0L ->
                ValidationError.AMOUNT_NOT_POSITIVE

            state.type != TransactionType.TRANSFER && state.selectedCategoryId == null ->
                ValidationError.CATEGORY_REQUIRED

            state.accountId == 0L ->
                ValidationError.ACCOUNT_REQUIRED

            state.type == TransactionType.TRANSFER && state.toAccountId == null ->
                ValidationError.TRANSFER_TARGET_REQUIRED

            else -> null
        }
        if (missing != null) {
            _state.update { it.copy(error = missing) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            try {
                val draft = TransactionDraft(
                    id = transactionId ?: 0L,
                    amountMinor = requireNotNull(Money.parse(state.amountInput)).minor,
                    type = state.type,
                    date = state.date,
                    categoryId = if (state.type == TransactionType.TRANSFER) null else state.selectedCategoryId,
                    accountId = state.accountId,
                    toAccountId = if (state.type == TransactionType.TRANSFER) state.toAccountId else null,
                    note = state.note.trim().ifEmpty { null },
                )
                transactionRepository.save(draft)
                _state.update { it.copy(saving = false, saved = true) }
            } catch (e: TransactionValidationException) {
                _state.update { it.copy(saving = false, error = e.error) }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, error = ValidationError.AMOUNT_NOT_POSITIVE) }
            }
        }
    }

    fun delete() {
        val id = transactionId ?: return
        viewModelScope.launch {
            transactionRepository.delete(id)
            _state.update { it.copy(deleted = true) }
        }
    }
}
