package com.jianji.app.data.backup

import com.jianji.app.data.local.entity.AccountEntity
import com.jianji.app.data.local.entity.BudgetEntity
import com.jianji.app.data.local.entity.CategoryEntity
import com.jianji.app.data.local.entity.TransactionEntity
import com.jianji.app.domain.model.AccountType
import com.jianji.app.domain.model.CategoryType
import com.jianji.app.domain.model.DateUtils
import com.jianji.app.domain.model.TransactionType
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * 备份 JSON 序列化 / 反序列化与严格校验。
 *
 * 纯函数实现（不依赖 Android 框架与数据库），便于单元测试。
 * 所有金额以字符串读写；解析端对每个字段做存在性、类型与取值范围校验，
 * 任何不满足都抛出 [BackupException]，绝不静默跳过。
 */
object BackupSerializer {

    const val FORMAT_VERSION = 1

    fun serialize(backup: BackupFile): String {
        val root = JSONObject()
        root.put("formatVersion", backup.formatVersion)
        root.put("appVersion", backup.appVersion)
        root.put("exportedAt", backup.exportedAt)
        root.put(
            "counts",
            JSONObject().apply {
                put("categories", backup.counts.categories)
                put("accounts", backup.counts.accounts)
                put("transactions", backup.counts.transactions)
                put("budgets", backup.counts.budgets)
            },
        )
        root.put("categories", JSONArray().apply { backup.categories.forEach { put(categoryJson(it)) } })
        root.put("accounts", JSONArray().apply { backup.accounts.forEach { put(accountJson(it)) } })
        root.put("transactions", JSONArray().apply { backup.transactions.forEach { put(transactionJson(it)) } })
        root.put("budgets", JSONArray().apply { backup.budgets.forEach { put(budgetJson(it)) } })
        return root.toString(2)
    }

    /** 解析并校验备份文件；失败抛出 [BackupException]。 */
    fun deserialize(json: String): BackupFile {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw BackupException(BackupError.INVALID_JSON, "不是合法的 JSON 文件")
        }
        val formatVersion = root.optInt("formatVersion", -1)
        if (formatVersion != FORMAT_VERSION) {
            throw BackupException(BackupError.UNSUPPORTED_VERSION, "不支持的备份格式版本: $formatVersion")
        }
        val appVersion = root.optString("appVersion").takeIf { it.isNotBlank() }
            ?: throw BackupException(BackupError.MALFORMED_STRUCTURE, "缺少 appVersion")
        val exportedAt = root.optLong("exportedAt", -1L)
        if (exportedAt < 0) {
            throw BackupException(BackupError.MALFORMED_STRUCTURE, "缺少 exportedAt")
        }
        val countsJson = root.optJSONObject("counts")
            ?: throw BackupException(BackupError.MALFORMED_STRUCTURE, "缺少 counts")
        val counts = BackupCounts(
            categories = countsJson.optInt("categories", -1),
            accounts = countsJson.optInt("accounts", -1),
            transactions = countsJson.optInt("transactions", -1),
            budgets = countsJson.optInt("budgets", -1),
        )
        if (counts.categories < 0 || counts.accounts < 0 || counts.transactions < 0 || counts.budgets < 0) {
            throw BackupException(BackupError.MALFORMED_STRUCTURE, "counts 字段不完整")
        }

        val categories = parseArray(root, "categories", ::parseCategory)
        val accounts = parseArray(root, "accounts", ::parseAccount)
        val transactions = parseArray(root, "transactions", ::parseTransaction)
        val budgets = parseArray(root, "budgets", ::parseBudget)

        if (categories.size != counts.categories ||
            accounts.size != counts.accounts ||
            transactions.size != counts.transactions ||
            budgets.size != counts.budgets
        ) {
            throw BackupException(BackupError.MALFORMED_STRUCTURE, "实际数据量与 counts 不一致")
        }

        validateReferences(categories, accounts, transactions, budgets)
        return BackupFile(
            formatVersion = formatVersion,
            appVersion = appVersion,
            exportedAt = exportedAt,
            counts = counts,
            categories = categories,
            accounts = accounts,
            transactions = transactions,
            budgets = budgets,
        )
    }

    // ---------- 序列化 ----------

    private fun categoryJson(c: CategoryEntity) = JSONObject().apply {
        put("id", c.id)
        put("name", c.name)
        put("icon", c.icon)
        put("type", c.type.name)
        put("isSystem", c.isSystem)
        put("isArchived", c.isArchived)
        put("sortOrder", c.sortOrder)
        c.colorArgb?.let { put("colorArgb", it) }
    }

    private fun accountJson(a: AccountEntity) = JSONObject().apply {
        put("id", a.id)
        put("name", a.name)
        put("icon", a.icon)
        put("type", a.type.name)
        put("initialBalanceMinor", a.initialBalanceMinor.toString())
        put("isArchived", a.isArchived)
        put("createdAt", a.createdAt)
        a.colorArgb?.let { put("colorArgb", it) }
    }

    private fun transactionJson(t: TransactionEntity) = JSONObject().apply {
        put("id", t.id)
        put("amountMinor", t.amountMinor.toString())
        put("type", t.type.name)
        put("transactionDate", t.transactionDate)
        t.categoryId?.let { put("categoryId", it) }
        put("accountId", t.accountId)
        t.toAccountId?.let { put("toAccountId", it) }
        t.note?.let { put("note", it) }
        put("createdAt", t.createdAt)
        put("updatedAt", t.updatedAt)
    }

    private fun budgetJson(b: BudgetEntity) = JSONObject().apply {
        put("id", b.id)
        put("yearMonth", b.yearMonth)
        b.categoryId?.let { put("categoryId", it) }
        put("limitMinor", b.limitMinor.toString())
    }

    // ---------- 解析 ----------

    private inline fun <reified T> parseArray(
        root: JSONObject,
        key: String,
        parse: (JSONObject) -> T,
    ): List<T> {
        val array = root.optJSONArray(key)
            ?: throw BackupException(BackupError.MALFORMED_STRUCTURE, "缺少 $key 数组")
        val result = ArrayList<T>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i)
                ?: throw BackupException(BackupError.MALFORMED_STRUCTURE, "$key[$i] 不是对象")
            result += parse(item)
        }
        return result
    }

    private fun parseCategory(o: JSONObject) = CategoryEntity(
        id = requirePositiveId(o),
        name = requireString(o, "name"),
        icon = o.optString("icon", "more_horiz"),
        type = parseEnum(o, "type", CategoryType.entries.toTypedArray()) { CategoryType.valueOf(it) },
        isSystem = o.optBoolean("isSystem", false),
        isArchived = o.optBoolean("isArchived", false),
        sortOrder = o.optInt("sortOrder", 0),
        colorArgb = if (o.has("colorArgb")) o.getInt("colorArgb") else null,
    )

    private fun parseAccount(o: JSONObject) = AccountEntity(
        id = requirePositiveId(o),
        name = requireString(o, "name"),
        icon = o.optString("icon", "more_horiz"),
        type = parseEnum(o, "type", AccountType.entries.toTypedArray()) { AccountType.valueOf(it) },
        initialBalanceMinor = requireMinor(o, "initialBalanceMinor"),
        isArchived = o.optBoolean("isArchived", false),
        createdAt = o.optLong("createdAt", 0L),
        colorArgb = if (o.has("colorArgb")) o.getInt("colorArgb") else null,
    )

    private fun parseTransaction(o: JSONObject) = TransactionEntity(
        id = requirePositiveId(o),
        amountMinor = requireMinor(o, "amountMinor"),
        type = parseEnum(o, "type", TransactionType.entries.toTypedArray()) { TransactionType.valueOf(it) },
        transactionDate = requireEpochDay(o, "transactionDate"),
        categoryId = if (o.has("categoryId")) o.getLong("categoryId") else null,
        accountId = o.optLong("accountId", -1L).also {
            if (it <= 0) throw BackupException(BackupError.MALFORMED_STRUCTURE, "交易缺少 accountId")
        },
        toAccountId = if (o.has("toAccountId")) o.getLong("toAccountId") else null,
        note = o.optString("note").takeIf { it.isNotBlank() },
        createdAt = o.optLong("createdAt", 0L),
        updatedAt = o.optLong("updatedAt", 0L),
    )

    private fun parseBudget(o: JSONObject) = BudgetEntity(
        id = requirePositiveId(o),
        yearMonth = requireYearMonth(o),
        categoryId = if (o.has("categoryId")) o.getLong("categoryId") else null,
        limitMinor = requireMinor(o, "limitMinor"),
    )

    // ---------- 字段校验 ----------

    private fun requirePositiveId(o: JSONObject): Long {
        val id = o.optLong("id", -1L)
        if (id <= 0) throw BackupException(BackupError.MALFORMED_STRUCTURE, "id 必须为正整数")
        return id
    }

    private fun requireString(o: JSONObject, key: String): String {
        val value = o.optString(key)
        if (value.isBlank()) throw BackupException(BackupError.MALFORMED_STRUCTURE, "缺少 $key")
        return value
    }

    /** 金额字段：JSON 字符串形式的整数分，严格校验。 */
    private fun requireMinor(o: JSONObject, key: String): Long {
        val raw = o.optString(key)
        val value = raw.toLongOrNull()
            ?: throw BackupException(BackupError.INVALID_AMOUNT, "$key 不是整数字符串: '$raw'")
        return value
    }

    private fun requireEpochDay(o: JSONObject, key: String): Long {
        val day = o.optLong(key, Long.MIN_VALUE)
        if (day == Long.MIN_VALUE) {
            throw BackupException(BackupError.MALFORMED_STRUCTURE, "缺少 $key")
        }
        val date = LocalDate.ofEpochDay(day)
        if (!DateUtils.isValidDate(date)) {
            throw BackupException(BackupError.INVALID_DATE, "$key 超出允许范围: $day")
        }
        return day
    }

    private fun requireYearMonth(o: JSONObject): String {
        val value = o.optString("yearMonth")
        if (DateUtils.parseYearMonth(value) == null) {
            throw BackupException(BackupError.MALFORMED_STRUCTURE, "yearMonth 格式非法: '$value'")
        }
        return value
    }

    private fun <T : Enum<T>> parseEnum(
        o: JSONObject,
        key: String,
        values: Array<T>,
        mapper: (String) -> T,
    ): T {
        val raw = o.optString(key)
        if (raw.isBlank() || values.none { it.name == raw }) {
            throw BackupException(BackupError.INVALID_ENUM, "$key 值非法: '$raw'")
        }
        return mapper(raw)
    }

    /** 外键完整性：交易的账户/分类、预算的分类必须在备份内存在。 */
    private fun validateReferences(
        categories: List<CategoryEntity>,
        accounts: List<AccountEntity>,
        transactions: List<TransactionEntity>,
        budgets: List<BudgetEntity>,
    ) {
        val accountIds = accounts.map { it.id }.toSet()
        val categoryIds = categories.map { it.id }.toSet()
        transactions.forEach { t ->
            if (t.accountId !in accountIds) {
                throw BackupException(BackupError.BROKEN_REFERENCE, "交易 ${t.id} 引用了不存在的账户 ${t.accountId}")
            }
            if (t.type == TransactionType.TRANSFER) {
                if (t.toAccountId == null || t.toAccountId !in accountIds) {
                    throw BackupException(
                        BackupError.BROKEN_REFERENCE,
                        "转账交易 ${t.id} 缺少或引用了不存在的目标账户",
                    )
                }
                if (t.categoryId != null) {
                    throw BackupException(BackupError.BROKEN_REFERENCE, "转账交易 ${t.id} 不应挂分类")
                }
            } else if (t.categoryId == null || t.categoryId !in categoryIds) {
                throw BackupException(BackupError.BROKEN_REFERENCE, "交易 ${t.id} 引用了不存在的分类")
            }
        }
        budgets.forEach { b ->
            if (b.categoryId != null && b.categoryId !in categoryIds) {
                throw BackupException(BackupError.BROKEN_REFERENCE, "预算 ${b.id} 引用了不存在的分类")
            }
        }
    }
}
