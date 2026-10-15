# 简记 · 实施状态

> 最后更新：数据层 + 核心界面阶段完成。本文档反映实际工程状态。

## 已完成

### 工程脚手架
- Gradle 工程（Kotlin 2.1.20 / AGP 8.9.2 / Gradle 8.13），版本目录 `gradle/libs.versions.toml`
- minSdk 24 + desugaring，compileSdk/targetSdk 35
- Compose/Material3 + material-icons-extended、Room + KSP、Navigation Compose、DataStore
- `JianjiApplication` + `AppContainer`（手写 DI）+ `MainActivity`（主题模式 + 应用骨架）
- 启动图标、主题资源（暖白/低饱和绿）

### 数据层
- 4 个实体：`transactions` / `categories` / `accounts` / `budgets`（含外键、索引）
- 4 个 DAO：CRUD + 组合筛选 + 全部统计 SQL（参数化，排除 TRANSFER，含年度月度趋势）
- 5 个 Repository 接口与实现：
  - TransactionRepository（校验 + 事务 + 转账规则）
  - StatisticsRepository（月度汇总/排行/趋势/区间排行，全部实时聚合）
  - AccountRepository（实时余额、账户管理、最后一个账户保护）
  - BudgetRepository（自然月预算、事务内唯一性、实时进度）
  - CategoryRepository（软删除、系统分类保护）
- `Money` 值类（分存储、精确运算、解析/格式化）、`DateUtils`（epochDay）
- 预置数据：15 个分类 + 5 个账户（首次建库写入）
- Room 配置：exportSchema=true、schema 已导出、MIGRATIONS 框架、无破坏性回退

### UI 层（本阶段新增）
- 设计系统：`ui/theme/`（颜色/字体/圆角/间距/主题 + 深色模式 + 语义色），见 docs/DESIGN_SYSTEM.md
- 通用组件：卡片、区块标题、金额文本、月份切换、分类头像、数字键盘、
  趋势条形图、环形图、进度条、芯片、底部导航、空态/加载/错误态
- 导航：底部 4 Tab + 中央记账 FAB；二级页（记一笔/预算/分类管理/账户管理）
- 页面（全部接真实数据，Room Flow 驱动）：
  - 记一笔（支出/收入/转账、键盘输入、分类横滑+全量面板、日期/账户/备注、保存/删除/编辑回填）
  - 首页（月份切换、汇总卡、记账入口、最近账单、支出分类概览）
  - 明细（搜索、类型/分类筛选、按日分组、日小计、点击编辑）
  - 统计（周/月/年切换、汇总、趋势图点选、环形图+图例、支出/收入排行）
  - 预算（月切换、总预算卡、分类预算、进度、超支提醒、金额对话框）
  - 我的（分组设置、主题切换持久化、分类/账户管理入口、数据功能明确标注规划中）
  - 分类管理（类型分组、新增弹窗+图标选择、归档、系统分类保护）
  - 账户管理（实时余额列表、新增、归档、最后账户保护）

### 测试
- 92 个单元/集成测试全部通过（见 docs/TESTING.md），含 2 个 Compose UI 测试
- `:app:assembleDebug` 成功（app-debug.apk 19.1MB，release 开 R8 后会显著缩小）

### 数据导出与备份（本阶段新增）
- `BackupSerializer`：JSON 格式（formatVersion=1），金额以字符串存储全程无浮点，
  日期 epochDay；导出/解析逐字段严格校验（结构/版本/金额/枚举/日期/外键引用）
- `BackupManager`：恢复在单事务内「清空 + 按 FK 顺序写入」，异常整体回滚；
  校验失败绝不触碰数据库
- 设置页：导出（SAF CreateDocument，文件名 `jianji_backup_yyyyMMdd_HHmmss.json`）、
  恢复（SAF OpenDocument → 校验 → 确认对话框展示统计与覆盖风险 → 恢复）
- `BackupRepository` 接口 + 实现，错误码映射中文提示

### 异常处理与可访问性（本阶段新增）
- 分类/账户归档的错误不再静默：ViewModel message 通道 + Snackbar 反馈
- 趋势图/环形图增加语义化 contentDescription（供 TalkBack 朗读）

### 历史数据补录与复盘（本阶段新增）
- 记账页：日期行显示相对标签（今天/昨天/前天/x月x日，跨年带年份）；
  新增「昨天」「前天」快捷按钮；日历选择器限定 1970~今年且禁选未来
- **禁止未来日期**：`TransactionValidator` 新增 `FUTURE_DATE` 校验（晚于今天即拒绝），
  保存层拦截；存量数据不动
- 统计页：周期改为「类型 + 偏移」，支持 `‹ 区间 ›` 左右翻阅历史周/月/年
  （不允许超过当前周期；切换周期类型重置偏移；周为滑动 7 天窗口）
- 数据层无需改动：epochDay 存储 + 实时聚合天然支持历史账目归月

### 数字键盘可收起（本阶段新增）
- `NumberKeypad` 第 4 行改为 4 列 [收起][.][0][⌫]，新增 `KeypadKey.Hide` 与 `onHide` 回调
- 记账页：键盘显隐状态 + `AnimatedVisibility` 过渡；收起后点击金额区唤回
  （附提示文案）；保存按钮固定页面最底部，键盘显隐不跳位
- 键盘按键语义合并（mergeDescendants），读屏可朗读“退格/收起键盘”

### 保存按钮移至右上角（本阶段新增）
- 记账页顶部栏右侧新增文字保存按钮（`[X][标题][删除(编辑态)][保存]`），
  移除底部保存按钮；标题 maxLines=1 防窄屏挤压
- 保存按钮始终可点击（仅样式置灰），点击时按缺失项给出具体错误：
  金额 →“请输入大于 0 的金额”；分类 →“请选择分类”；账户 →“请选择账户”；
  转账未选目标 →“请选择转入账户”
- 分类横滑项增加选中语义（selected），读屏可感知选中态

### 数字键盘 4×4 对称网格（本阶段新增）
- 键盘重排为 4 列 × 4 行：`1-9/⌫`、`4-6/清空`、`7-9/.`、`00 0 保存 ⌄`
- 新增键：`清空`（一键清空金额）、`00`（快捷双零，遵守小数与位数规则）、
  `保存`（主色强调，与右上角保存调用同一 `save()`）
- 退格移至顶行右侧，收起键盘移至右下角
- 两处保存入口行为一致；测试以 testTag（save_top / save_keypad）区分

### 应用锁：指纹 / 系统密码验证（本阶段新增）
- 新增依赖 `androidx.biometric:biometric:1.1.0`；`MainActivity` 基类改为
  `FragmentActivity`（BiometricPrompt 构造要求；Compose 扩展完全兼容）
- 锁闸 `AppLockGate`：未解锁时应用内容完全不可见；进入自动验证，失败可重试
- `AppLockAuthenticator` 接口（可测接缝）+ `BiometricAppLockAuthenticator` 实现：
  能力自适应（API 30+ 组合验证 / 24-29 单一能力 / NONE 提示），
  异常时降级重试，全程不接触用户密码
- 设置：`appLockEnabled`（默认关）；「我的 → 安全」分组含应用锁开关
  （开启前先过一次系统验证）与「后台返回重新锁定」说明（离开超 60 秒重验）
- 锁定时 `FLAG_SECURE`（禁截屏/防最近任务预览）
- 已知边界：只锁 UI 入口，数据库文件未加密（SQLCipher 未实现）

### 紧急修复：首页 LazyColumn 键冲突崩溃（本阶段）
- **现象**：录入数据后返回首页崩溃；连续录入两笔必崩（用户实测 + 雷电模拟器复现）
- **根因**：首页 LazyColumn 中「最近账单」用 `transaction.id` 作 key、
  「支出分类」用 `categoryId` 作 key，两块 items 共用同一 key 空间；
  预置分类占用 id 1-15，第 2 笔交易 id=2 与「交通」分类 id=2 碰撞 →
  `IllegalArgumentException: Key "2" was already used`
- **修复**：两块 key 分别加 `txn-` / `cat-` 前缀隔离；明细页交易 key 同步加前缀防御
- **回归测试**：`HomeScreenTest.categorySectionComposesWithoutKeyCollision`
  （滚动到分类区使其组合；已验证无修复时该测试失败、有修复时通过）
- **复盘**：Lazy 列表多块 items 禁止裸用同类 id 作 key，必须加类型前缀

## 进行中

- 无。

## 待完成（后续阶段）

| 阶段 | 内容 | 依赖 |
|---|---|---|
| 阶段 12 | P2 功能：周期记账、桌面小组件、标签 | 当前阶段 |
| 阶段 13 | 打磨：页面转场动画、TalkBack 真机走查、Compose UI 测试扩充、R8/签名配置 | 当前阶段 |

## 已知问题 / 决策记录

1. **转账允许账户余额为负**：未拦截「转出超过余额」，信用类账户靠负初始余额管理；如需拦截在 `TransactionRepositoryImpl.save` 加校验即可。
2. **恢复为覆盖式导入**：导入前强制确认对话框展示统计与风险；不提供合并导入（避免主键/分类映射歧义）。
3. **备份文件名固定 ASCII**：`jianji_backup_yyyyMMdd_HHmmss.json`，规避文件系统编码问题。
4. **图标为字符串键名映射**（`CategoryIcon.kt` 集中维护，约 45 个键）。
5. **手写 DI**：接口齐备，后续可平滑迁移 Hilt。
6. **schema 资产进入 debug 包**：为 MigrationTestHelper 服务，release 不含。
7. **Compose UI 测试中保存落库由 ViewModel 测试覆盖**：Robolectric 下 Compose 测试环境对
   `viewModelScope` + Room 事务的协程续体调度存在限制（保存协程可启动但不恢复），
   已按「UI 测试管渲染与表单联动、ViewModel 测试管落库」拆分覆盖，避免脆弱断言。
8. **Robolectric SDK 34**：与 compileSdk 35 兼容；升级 compileSdk 时同步验证。
9. **无真机验证**：全部验证基于 JVM/Robolectric；视觉效果与 TalkBack 体验需真机走查。
