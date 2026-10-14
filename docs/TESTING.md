# 简记 · 测试方案与结果

## 1. 测试分层

| 层 | 位置 | 运行方式 | 覆盖 |
|---|---|---|---|
| 纯逻辑单测 | `app/src/test/`（JVM） | `gradlew testDebugUnitTest` | Money、DateUtils、Validator、BudgetProgress |
| 数据库/仓储测试 | `app/src/test/`（Robolectric + 内存 Room） | 同上 | DAO、统计、余额、预算、迁移 |
| ViewModel 测试 | `app/src/test/`（Robolectric + MainDispatcherRule） | 同上 | 记一笔表单状态机、保存/删除、编辑回填 |
| Compose UI 测试 | `app/src/test/`（Robolectric + createComposeRule + 真实 AppContainer） | 同上 | 首页渲染、记一笔表单联动 |

未使用模拟器：Robolectric 可在 JVM 跑真实 SQLite 与 Compose，CI 友好。

## 2. 用例清单

### 金额（MoneyTest，10 例）
零值、大金额（Long.MAX_VALUE 边界）、小数输入、四舍五入（half-up）、
千分位/符号解析、非法输入拒绝、累计计算（0.1×10 = 1.00）、溢出抛异常、
格式化（分组/负号/自定义分隔符）、比较与谓词。

### 日期（DateUtilsTest，7 例）
自然月区间（含月末）、闰年 2 月、跨年（12/31 与 1/1 相邻）、
YYYY-MM 解析与格式化、非法格式拒绝、非法区间抛错、
**时区无关性**（5 个时区下 epochDay 恒定）。

### 校验（TransactionValidatorTest，9 例）
合法收支/转账、金额必须为正、日期范围、收支必须挂分类、
转账必须选目标账户且不能转给自己、备注长度、资源名校验。

### 预算进度（BudgetProgressTest，4 例）
千分比进度、超支封顶 1000、恰好用完不算超支、零使用。

### 数据库（Robolectric，约 30 例）
- TransactionDaoTest：增删改、日期范围、分类/账户查询、组合筛选、
  类型聚合、分类排行、按天趋势（转账不产生趋势点）。
- StatisticsRepositoryTest：月度汇总、**转账不计入收支**、支出/收入排行、
  趋势补零与总额一致、超一年区间不补零。
- AccountRepositoryTest：余额公式（初始+收入-支出-转出+转入）、
  删除后余额实时更新（无缓存）、账户列表余额、账户流水含转入转出、
  账户增改、空名称拒绝、禁止归档最后一个账户。
- BudgetRepositoryTest：总预算/分类预算进度、重复设置不产生第二条、
  超支判定、非法输入拒绝、删除预算。
- TransactionRepositoryTest：保存/更新（保留 createdAt）、各类校验失败路径、
  编辑不存在记录拒绝、删除、组合筛选。
- MigrationTest：schema JSON 已导出、`runMigrationsAndValidate(1)` 通过。

### ViewModel（AddEditTransactionViewModelTest，6 例）
键盘输入规则（两位小数上限、单小数点、退格、前导零替换）、
保存落库精度（19.99 → 1999 分）、表单不完整时忽略保存、
分类被归档后保存返回类型化错误、转账不挂分类并关联双账户、编辑态回填。

### Compose UI（2 例）
- HomeScreenTest：真实 Room + 预置数据渲染首页，校验汇总数字
  （965.00 / 1,000.00 / 35.00）、最近账单、分类概览；
- AddEditScreenTest：保存错误反馈（无金额/未选分类的具体文案）、
  分类选中后保存触发（落库或保存中）、收起键隐藏键盘、点击金额区唤回键盘、
  收起后右上角保存按钮仍可点击、4×4 键盘渲染与 00/清空/键盘保存键行为。
- AddEditTransactionViewModelTest：save() 在表单不完整时返回具体错误码
  （AMOUNT_NOT_POSITIVE / CATEGORY_REQUIRED），补齐后可正常落库；
  `00` 键输入规则（补足两位小数、整数位上限 10 位）、`清空` 键清空输入。

### 备份（BackupSerializerTest 10 例 + BackupManagerTest 5 例）
- 序列化：全字段往返一致、Long 边界金额（MAX/MIN_VALUE）零损失、
  拒绝非法 JSON / 不支持的版本 / counts 不符 / 断链引用 /
  转账缺目标账户 / 非整数金额 / 越界日期；
- 管理器：导出→清库→恢复往返一致、恢复覆盖现有数据、
  恢复后统计口径正确（转账不计入）、校验失败不触碰数据库。

### 历史数据补录（本阶段新增）
- TransactionValidatorTest：未来日期拒绝（支出与转账）、今天与过去日期通过；
- AddEditTransactionViewModelTest：新账目默认今天、补录昨天保存后日期正确、
  未来日期保存被拒绝且不落库；
- StatisticsViewModelTest（新增 8 例，固定 today=2026-10-14）：
  默认当前月、前后翻历史周期、不能超过当前周期、切换周期重置偏移、
  周滑动 7 天窗口（含边界金额）、年度跨年、月度跨年边界。

## 3. 执行结果

```
> Task :app:testDebugUnitTest :app:assembleDebug --rerun-tasks
BUILD SUCCESSFUL
110 tests completed, 0 failed, 0 errors
```

同时 `:app:assembleDebug` 成功产出 `app-debug.apk`（19.1MB），schema 导出至
`app/schemas/com.jianji.app.data.local.JianjiDatabase/1.json`。
以上结果为 clean 后 `--rerun-tasks` 强制重跑，非缓存结果。

同时 `:app:assembleDebug` 成功产出 `app-debug.apk`，schema 导出至
`app/schemas/com.jianji.app.data.local.JianjiDatabase/1.json`。

## 4. 已知测试限制

- Robolectric 使用 SDK 34 配置（与 compileSdk 35 兼容）；
- schema 资产挂到 `debug` 与 `test` sourceSet，供 MigrationTestHelper 读取
  （Robolectric 从应用上下文读资产，故需 debug 变体资产；release 包不含 schema）；
- Compose UI 测试环境对 `viewModelScope` + Room 事务的协程续体调度有限制，
  保存落库链路由 ViewModel 测试覆盖，UI 测试只覆盖渲染与表单联动；
- 后续升级数据库时，必须按 DATABASE.md 第 7 节补充 MigrationTest 用例。
