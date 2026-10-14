package com.jianji.app.domain.validation

import com.jianji.app.domain.model.TransactionDraft
import com.jianji.app.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 交易校验器测试（纯逻辑，不依赖数据库）。 */
class TransactionValidatorTest {

    private val date: LocalDate = LocalDate.of(2026, 10, 13)

    private fun draft(
        id: Long = 0L,
        amountMinor: Long = 1000L,
        type: TransactionType = TransactionType.EXPENSE,
        categoryId: Long? = 1L,
        accountId: Long = 1L,
        toAccountId: Long? = null,
        note: String? = null,
    ) = TransactionDraft(
        id = id,
        amountMinor = amountMinor,
        type = type,
        date = date,
        categoryId = categoryId,
        accountId = accountId,
        toAccountId = toAccountId,
        note = note,
    )

    @Test
    fun `valid expense passes`() {
        assertEquals(ValidationResult.Valid, TransactionValidator.validate(draft()))
    }

    @Test
    fun `valid income passes`() {
        assertEquals(
            ValidationResult.Valid,
            TransactionValidator.validate(draft(type = TransactionType.INCOME, categoryId = 2L)),
        )
    }

    @Test
    fun `valid transfer passes`() {
        assertEquals(
            ValidationResult.Valid,
            TransactionValidator.validate(
                draft(
                    type = TransactionType.TRANSFER,
                    categoryId = null,
                    accountId = 1L,
                    toAccountId = 2L,
                ),
            ),
        )
    }

    @Test
    fun `amount must be positive`() {
        assertEquals(
            ValidationResult.Invalid(ValidationError.AMOUNT_NOT_POSITIVE),
            TransactionValidator.validate(draft(amountMinor = 0L)),
        )
        assertEquals(
            ValidationResult.Invalid(ValidationError.AMOUNT_NOT_POSITIVE),
            TransactionValidator.validate(draft(amountMinor = -100L)),
        )
    }

    @Test
    fun `date must be in range`() {
        assertEquals(
            ValidationResult.Invalid(ValidationError.DATE_OUT_OF_RANGE),
            TransactionValidator.validate(draft().copy(date = LocalDate.of(1969, 12, 31))),
        )
        assertEquals(
            ValidationResult.Invalid(ValidationError.DATE_OUT_OF_RANGE),
            TransactionValidator.validate(draft().copy(date = LocalDate.of(2200, 1, 1))),
        )
    }

    @Test
    fun `income and expense require category`() {
        assertEquals(
            ValidationResult.Invalid(ValidationError.CATEGORY_REQUIRED),
            TransactionValidator.validate(draft(categoryId = null)),
        )
        assertEquals(
            ValidationResult.Invalid(ValidationError.CATEGORY_REQUIRED),
            TransactionValidator.validate(draft(type = TransactionType.INCOME, categoryId = null)),
        )
    }

    @Test
    fun `transfer requires target account and forbids same account`() {
        assertEquals(
            ValidationResult.Invalid(ValidationError.TRANSFER_TARGET_REQUIRED),
            TransactionValidator.validate(
                draft(type = TransactionType.TRANSFER, toAccountId = null),
            ),
        )
        assertEquals(
            ValidationResult.Invalid(ValidationError.TRANSFER_TARGET_SAME_ACCOUNT),
            TransactionValidator.validate(
                draft(type = TransactionType.TRANSFER, toAccountId = 1L),
            ),
        )
    }

    @Test
    fun `future date is rejected`() {
        val tomorrow = LocalDate.now().plusDays(1)
        assertEquals(
            ValidationResult.Invalid(ValidationError.FUTURE_DATE),
            TransactionValidator.validate(draft().copy(date = tomorrow)),
        )
        // 转账同样禁止未来日期
        assertEquals(
            ValidationResult.Invalid(ValidationError.FUTURE_DATE),
            TransactionValidator.validate(
                draft(type = TransactionType.TRANSFER, toAccountId = 2L).copy(date = tomorrow),
            ),
        )
    }

    @Test
    fun `today and past dates pass`() {
        assertEquals(
            ValidationResult.Valid,
            TransactionValidator.validate(draft().copy(date = LocalDate.now())),
        )
        assertEquals(
            ValidationResult.Valid,
            TransactionValidator.validate(draft().copy(date = LocalDate.now().minusYears(5))),
        )
    }

    @Test
    fun `note length limit`() {
        assertEquals(
            ValidationResult.Invalid(ValidationError.NOTE_TOO_LONG),
            TransactionValidator.validate(draft(note = "x".repeat(TransactionValidator.MAX_NOTE_LENGTH + 1))),
        )
        assertEquals(
            ValidationResult.Valid,
            TransactionValidator.validate(draft(note = "x".repeat(TransactionValidator.MAX_NOTE_LENGTH))),
        )
    }

    @Test
    fun `resource name validation`() {
        assertTrue(ResourceValidator.validateName("餐饮", isCategory = true) == ValidationResult.Valid)
        assertTrue(ResourceValidator.validateName("  银行卡 ", isCategory = false) == ValidationResult.Valid)
        assertEquals(
            ValidationResult.Invalid(ValidationError.CATEGORY_NAME_INVALID),
            ResourceValidator.validateName("", isCategory = true),
        )
        assertEquals(
            ValidationResult.Invalid(ValidationError.ACCOUNT_NAME_INVALID),
            ResourceValidator.validateName("x".repeat(ResourceValidator.MAX_NAME_LENGTH + 1), isCategory = false),
        )
    }
}
