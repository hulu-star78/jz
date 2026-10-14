package com.jianji.app.ui.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jianji.app.domain.model.AccountType
import com.jianji.app.domain.model.Money
import com.jianji.app.domain.repository.AccountRepository
import com.jianji.app.domain.validation.ResourceValidationException
import com.jianji.app.ui.util.message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountManageUiState(
    val loading: Boolean = true,
    val accounts: List<AccountRow> = emptyList(),
)

data class AccountRow(
    val id: Long,
    val name: String,
    val icon: String,
    val type: AccountType,
    val balance: Money,
    val isArchived: Boolean,
)

class AccountManageViewModel(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    /** 操作反馈消息（错误或成功提示），由 UI 展示后消费。 */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    val state: StateFlow<AccountManageUiState> = accountRepository.observeAccountsWithBalance()
        .map { rows ->
            AccountManageUiState(
                loading = false,
                accounts = rows.map { row ->
                    AccountRow(
                        id = row.account.id,
                        name = row.account.name,
                        icon = row.account.icon,
                        type = row.account.type,
                        balance = row.balance,
                        isArchived = row.account.isArchived,
                    )
                },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountManageUiState())

    /** 新建账户；返回错误信息（null 表示成功）。 */
    suspend fun createAccount(
        name: String,
        icon: String,
        type: AccountType,
        initialBalanceMinor: Long,
    ): String? = try {
        accountRepository.createAccount(name.trim(), icon, type, initialBalanceMinor)
        null
    } catch (e: ResourceValidationException) {
        e.error.message()
    } catch (e: Exception) {
        "保存失败：${e.message ?: "未知错误"}"
    }

    fun archiveAccount(id: Long) {
        viewModelScope.launch {
            try {
                accountRepository.archiveAccount(id)
                _message.value = "已归档账户"
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
