# 简记 · 数据库设计

> 数据层唯一事实来源：Room 本地数据库 `jianji.db`。离线优先，无云同步。
> 所有统计均由 SQL 实时聚合，禁止保存任何汇总缓存。

## 1. 实体总览

```
categories (分类)
   ↑ FK.categoryId          ↑ FK.categoryId
transactions (交易) ──────── accounts (账户)
   ↑ FK.accountId              ↑ FK.accountId (RESTRICT)
   ↑ FK.toAccountId (RESTRICT)
budgets (预算) ── FK.categoryId → categories (CASCADE)
```

| 表 | 职责 |
|---|---|
| `transactions` | 唯一财务事实来源：收入、支出、转账 |
| `categories` | 收入/支出分类，软删除（归档） |
| `accounts` | 账户（现金/银行卡/电子钱包/信用卡等），软删除 |
| `budgets` | 自然月预算（总预算 + 分类预算） |

## 2. transactions（核心表）

| 列 | 类型 | 说明 |
|---|---|---|
| id | Long PK 自增 | |
| amountMinor | Long | **金额，单位：分**。禁止 Float/Double |
| type | 枚举字符串 | INCOME / EXPENSE / TRANSFER |
| transactionDate | Long | LocalDate 的 epochDay，**不受设备时区影响** |
| categoryId | Long? | 收入/支出分类；转账为 null；分类删除时置空（SET_NULL） |
| accountId | Long | 来源账户（收入入账账户 / 支出账户 / 转账转出账户），RESTRICT |
| toAccountId | Long? | 仅转账：目标账户，RESTRICT |
| note | String? | 备注，最长 200 字符 |
| createdAt | Long | 创建时间戳（毫秒） |
| updatedAt | Long | 更新时间戳（毫秒） |

索引：`transactionDate`、`categoryId`、`accountId`、`toAccountId`、`(transactionDate, type)`。

**转账语义**：TRANSFER 不属于收入也不属于支出，所有收支统计 SQL 均排除；
但会减少 `accountId` 账户、增加 `toAccountId` 账户的资金。

## 3. categories

| 列 | 类型 | 说明 |
|---|---|---|
| id | Long PK | |
| name | String | ≤ 20 字符 |
| icon | String | 图标键名（如 `restaurant`），UI 层映射 |
| type | 枚举字符串 | INCOME / EXPENSE |
| isSystem | Boolean | 预置分类不可删除/归档 |
| isArchived | Boolean | **软删除**：归档后不再展示，但历史账目引用仍有效 |
| sortOrder | Int | 排序 |
| colorArgb | Int? | 可选主题色（预留给 UI，避免未来为颜色单独迁移） |

## 4. accounts

| 列 | 类型 | 说明 |
|---|---|---|
| id | Long PK | |
| name | String | ≤ 20 字符 |
| icon | String | 图标键名 |
| type | 枚举字符串 | CASH / BANK / E_WALLET / CREDIT_CARD / OTHER |
| initialBalanceMinor | Long | 初始余额（分）。信用卡可用负值表示建账时已透支 |
| isArchived | Boolean | 软删除；不允许归档最后一个可用账户 |
| createdAt | Long | |
| colorArgb | Int? | 预留给 UI |

**余额公式（实时计算，禁止保存 currentBalance）**：

```
balance = initialBalanceMinor
        + Σ(INCOME, accountId = a)
        - Σ(EXPENSE, accountId = a)
        - Σ(TRANSFER 转出, accountId = a)
        + Σ(TRANSFER 转入, toAccountId = a)
```

对应 `AccountDao.observeBalance` / `observeAccountsWithBalance`（单条 SQL 聚合，避免 N 次查询不一致）。

## 5. budgets

| 列 | 类型 | 说明 |
|---|---|---|
| id | Long PK | |
| yearMonth | String | `YYYY-MM`，自然月键 |
| categoryId | Long? | null = 月总预算；否则为该支出分类的预算 |
| limitMinor | Long | 预算上限（分） |

**唯一性说明**：SQLite 唯一索引不约束 NULL，因此「每月一条总预算」由
`BudgetRepositoryImpl.setTotalBudget` 在事务内先查后写保证，schema 层只建普通索引。

## 6. 预置数据（SeedData）

首次建库时插入：

- 支出分类：餐饮、交通、购物、居住、娱乐、医疗、教育、通讯、人情、其他（isSystem=1）
- 收入分类：工资、奖金、理财、兼职、其他收入（isSystem=1）
- 账户：现金、银行卡、微信、支付宝、信用卡（初始余额 0）

用户新增分类 `isSystem=0`，可归档；系统分类不可归档。

## 7. 迁移策略

- `@Database(exportSchema = true)`，schema JSON 导出至 `app/schemas/`（已提交）。
- **禁止** `fallbackToDestructiveMigration()`；升级不清库。
- 升级步骤：
  1. 提升 `JianjiDatabase` 的 version；
  2. 在 `JianjiDatabase.MIGRATIONS` 追加 `Migration(FROM, TO)`；
  3. 在 `MigrationTest` 追加 `runMigrationsAndValidate(TEST_DB, N, true, MIGRATION_x_TO_y)`；
  4. 枚举值（type 等）禁止重命名/删除，否则历史数据 `valueOf` 失败，必须配套 Migration 做值映射。

## 8. 统计查询口径

| 统计 | 实现 | 转账处理 |
|---|---|---|
| 月收入/支出 | `TransactionDao.observeTotalByType` | 只统计 INCOME / EXPENSE |
| 分类排行 | `observeCategoryTotals`（GROUP BY categoryId 降序） | 不参与 |
| 按天趋势 | `observeDailyTrend`（CASE 求和 + HAVING 过滤零行） | 两列均为 0，被 HAVING 过滤 |
| 预算消耗 | `observeCategoryTotals(EXPENSE)` | 不参与 |
| 账户余额 | `AccountDao` 子查询聚合 | 转出 -、转入 + |

SQL 中不出现枚举字面量，类型一律参数绑定（配合 `Converters`），重命名枚举不会导致 SQL 与数据不一致。
