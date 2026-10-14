package com.jianji.app.ui.add

import com.jianji.app.data.repository.AccountRepositoryImpl
import com.jianji.app.data.repository.CategoryRepositoryImpl
import com.jianji.app.data.repository.TransactionRepositoryImpl
import com.jianji.app.domain.model.CategoryType
import com.jianji.app.domain.model.Money
import com.jianji.app.domain.model.TransactionDraft
import com.jianji.app.domain.model.TransactionType
import com.jianji.app.domain.validation.ValidationError
import com.jianji.app.testutil.DatabaseTestSupport
import com.jianji.app.testutil.MainDispatcherRule
import com.jianji.app.ui.components.KeypadKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
/**
 * 记一笔 ViewModel 测试：键盘输入规则、校验、保存落库、编辑回填。
 */
class AddEditTransactionViewModelTest : DatabaseTestSupport() {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: AddEditTransactionViewModel
    private lateinit var transactionRepository: TransactionRepositoryImpl

    private suspend fun setUpViewModel(transactionId: Long? = null) {
        insertCategory("餐饮", CategoryType.EXPENSE)
        insertCategory("工资", CategoryType.INCOME)
        insertAccount("现金")
        insertAccount("银行卡")
        transactionRepository = TransactionRepositoryImpl(
            database = database,
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            accountDao = database.accountDao(),
        )
        viewModel = AddEditTransactionViewModel(
            transactionRepository = transactionRepository,
            categoryRepository = CategoryRepositoryImpl(database.categoryDao()),
            accountRepository = AccountRepositoryImpl(
                accountDao = database.accountDao(),
                transactionRepository = transactionRepository,
            ),
            transactionId = transactionId,
        )
        // 等待 init 协程完成（Room Flow 在后台线程发射）
        viewModel.state.first { !it.loading }
    }

    @Test
    fun `keypad input rules`() = runTest {
        setUpViewModel()
        val vm = viewModel

        vm.onKeypadKey(KeypadKey.Digit1)
        vm.onKeypadKey(KeypadKey.Digit2)
        vm.onKeypadKey(KeypadKey.Dot)
        vm.onKeypadKey(KeypadKey.Digit5)
        vm.onKeypadKey(KeypadKey.Digit5)
        assertEquals("12.55", vm.state.value.amountInput)

        // 小数点后最多两位
        vm.onKeypadKey(KeypadKey.Digit9)
        assertEquals("12.55", vm.state.value.amountInput)

        // 只能有一个小数点
        vm.onKeypadKey(KeypadKey.Dot)
        assertEquals("12.55", vm.state.value.amountInput)

        // 退格
        vm.onKeypadKey(KeypadKey.Backspace)
        vm.onKeypadKey(KeypadKey.Backspace)
        assertEquals("12.", vm.state.value.amountInput)
        vm.onKeypadKey(KeypadKey.Backspace)
        assertEquals("12", vm.state.value.amountInput)
        vm.onKeypadKey(KeypadKey.Backspace)
        vm.onKeypadKey(KeypadKey.Backspace)
        assertEquals("", vm.state.value.amountInput)

        // 前导零替换
        vm.onKeypadKey(KeypadKey.Digit0)
        assertEquals("0", vm.state.value.amountInput)
        vm.onKeypadKey(KeypadKey.Digit7)
        assertEquals("7", vm.state.value.amountInput)
    }

    @Test
    fun `save persists expense with exact minor units`() = runTest {
        setUpViewModel()
        val categories = viewModel.state.value.expenseCategories
        val accounts = viewModel.state.value.accounts

        viewModel.onKeypadKey(KeypadKey.Digit1)
        viewModel.onKeypadKey(KeypadKey.Digit9)
        viewModel.onKeypadKey(KeypadKey.Dot)
        viewModel.onKeypadKey(KeypadKey.Digit9)
        viewModel.onKeypadKey(KeypadKey.Digit9)
        viewModel.onCategorySelected(categories.first().id)
        viewModel.onNoteChange("  四舍五入测试  ")
        viewModel.save()
        viewModel.state.first { it.saved || it.error != null }

        assertTrue(viewModel.state.value.saved)
        val transactions = transactionRepository.observeAll().first()
        assertEquals(1, transactions.size)
        // 键盘限制两位小数：输入 19.99 => 1999 分（精确，无浮点误差）
        assertEquals(Money.fromMinor(1999), transactions.first().amount)
        assertEquals("四舍五入测试", transactions.first().note)
        assertEquals(categories.first().id, transactions.first().categoryId)
        assertEquals(accounts.first().id, transactions.first().accountId)
    }

    @Test
    fun `save reports specific error when form incomplete`() = runTest {
        setUpViewModel()

        // 无金额
        viewModel.save()
        assertEquals(ValidationError.AMOUNT_NOT_POSITIVE, viewModel.state.value.error)
        assertFalse(viewModel.state.value.saved)
        assertEquals(0, transactionRepository.observeAll().first().size)

        // 有金额但未选分类
        viewModel.onKeypadKey(KeypadKey.Digit5)
        viewModel.save()
        assertEquals(ValidationError.CATEGORY_REQUIRED, viewModel.state.value.error)
        assertFalse(viewModel.state.value.saved)
        assertEquals(0, transactionRepository.observeAll().first().size)

        // 选好分类后可以正常保存
        viewModel.onCategorySelected(viewModel.state.value.expenseCategories.first().id)
        viewModel.save()
        viewModel.state.first { it.saved || it.error != null }
        assertTrue(viewModel.state.value.saved)
        assertEquals(1, transactionRepository.observeAll().first().size)
    }

    @Test
    fun `archived category surfaces typed error on save`() = runTest {
        setUpViewModel()
        val categoryId = viewModel.state.value.expenseCategories.first().id
        viewModel.onKeypadKey(KeypadKey.Digit5)
        viewModel.onCategorySelected(categoryId)

        // 选择后分类被归档（模拟并发场景）
        database.categoryDao().archiveById(categoryId)

        viewModel.save()
        viewModel.state.first { it.saved || it.error != null }
        assertEquals(ValidationError.CATEGORY_REQUIRED, viewModel.state.value.error)
        assertFalse(viewModel.state.value.saved)
        assertEquals(0, transactionRepository.observeAll().first().size)
    }

    @Test
    fun `transfer saves without category and links accounts`() = runTest {
        setUpViewModel()
        val accounts = viewModel.state.value.accounts

        viewModel.onTypeChange(TransactionType.TRANSFER)
        viewModel.onKeypadKey(KeypadKey.Digit1)
        viewModel.onKeypadKey(KeypadKey.Digit0)
        viewModel.onKeypadKey(KeypadKey.Digit0)
        viewModel.onAccountSelected(accounts[0].id)
        viewModel.onToAccountSelected(accounts[1].id)
        viewModel.save()
        viewModel.state.first { it.saved || it.error != null }

        assertTrue(viewModel.state.value.saved)
        val saved = transactionRepository.observeAll().first().first()
        assertEquals(TransactionType.TRANSFER, saved.type)
        assertNull(saved.categoryId)
        assertEquals(accounts[0].id, saved.accountId)
        assertEquals(accounts[1].id, saved.toAccountId)
        // 输入 100 => 100 元 => 10000 分
        assertEquals(Money.fromMinor(10000), saved.amount)
    }

    @Test
    fun `double zero key appends two zeros within limits`() = runTest {
        setUpViewModel()
        val vm = viewModel

        // 整数部分：1 + 00 = 100
        vm.onKeypadKey(KeypadKey.Digit1)
        vm.onKeypadKey(KeypadKey.DoubleZero)
        assertEquals("100", vm.state.value.amountInput)

        // 小数后只补足到两位：100.5 + 00 = 100.50
        vm.onKeypadKey(KeypadKey.Dot)
        vm.onKeypadKey(KeypadKey.Digit5)
        vm.onKeypadKey(KeypadKey.DoubleZero)
        assertEquals("100.50", vm.state.value.amountInput)

        // 已有两位小数：00 不再追加
        vm.onKeypadKey(KeypadKey.DoubleZero)
        assertEquals("100.50", vm.state.value.amountInput)
    }

    @Test
    fun `double zero fills up to integer digit limit`() = runTest {
        setUpViewModel()
        // 9 位后再按 00：只补足到 10 位上限
        repeat(9) { viewModel.onKeypadKey(KeypadKey.Digit9) }
        assertEquals("999999999", viewModel.state.value.amountInput)
        viewModel.onKeypadKey(KeypadKey.DoubleZero)
        assertEquals("9999999990", viewModel.state.value.amountInput)
    }

    @Test
    fun `clear key empties input`() = runTest {
        setUpViewModel()
        viewModel.onKeypadKey(KeypadKey.Digit1)
        viewModel.onKeypadKey(KeypadKey.Digit2)
        assertEquals("12", viewModel.state.value.amountInput)

        viewModel.onKeypadKey(KeypadKey.Clear)
        assertEquals("", viewModel.state.value.amountInput)
    }

    @Test
    fun `new transaction defaults to today`() = runTest {
        setUpViewModel()
        assertEquals(LocalDate.now(), viewModel.state.value.date)
    }

    @Test
    fun `backdated save records the chosen date`() = runTest {
        setUpViewModel()
        val yesterday = LocalDate.now().minusDays(1)

        viewModel.onKeypadKey(KeypadKey.Digit9)
        viewModel.onCategorySelected(viewModel.state.value.expenseCategories.first().id)
        viewModel.onDateChange(yesterday)
        viewModel.save()
        viewModel.state.first { it.saved || it.error != null }

        assertTrue(viewModel.state.value.saved)
        val saved = transactionRepository.observeAll().first().first()
        assertEquals(yesterday, saved.date)
        assertEquals(Money.fromMinor(900), saved.amount)
    }

    @Test
    fun `future date is rejected on save`() = runTest {
        setUpViewModel()
        viewModel.onKeypadKey(KeypadKey.Digit5)
        viewModel.onCategorySelected(viewModel.state.value.expenseCategories.first().id)
        viewModel.onDateChange(LocalDate.now().plusDays(1))
        viewModel.save()
        viewModel.state.first { it.saved || it.error != null }

        assertEquals(ValidationError.FUTURE_DATE, viewModel.state.value.error)
        assertFalse(viewModel.state.value.saved)
        assertEquals(0, transactionRepository.observeAll().first().size)
    }

    @Test
    fun `edit mode loads existing transaction`() = runTest {
        setUpViewModel()
        val foodId = viewModel.state.value.expenseCategories.first().id
        val accountId = viewModel.state.value.accounts.first().id
        val id = transactionRepository.save(
            TransactionDraft(
                amountMinor = 12345,
                type = TransactionType.EXPENSE,
                date = LocalDate.of(2026, 10, 1),
                categoryId = foodId,
                accountId = accountId,
                note = "旧账",
            ),
        )

        setUpViewModel(transactionId = id)

        val state = viewModel.state.value
        assertTrue(state.isEdit)
        assertEquals("123.45", state.amountInput)
        assertEquals(foodId, state.selectedCategoryId)
        assertEquals("旧账", state.note)
        assertEquals(LocalDate.of(2026, 10, 1), state.date)
    }
}
