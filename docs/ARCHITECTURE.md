# 简记 · 技术架构

## 1. 分层

```
UI（Compose，后续阶段）
   ↓ 观察 StateFlow / 调用 suspend 方法
ViewModel（后续阶段）
   ↓ 只依赖 domain.repository 接口
Repository 接口（domain/repository）
   ↑ 实现
Repository 实现（data/repository）
   ↓
DAO（data/local/dao，Room，Flow）
   ↓
Room Database（jianji.db，本地唯一事实来源）
```

- ViewModel 禁止直接访问 DAO；
- data 层不依赖 UI；domain 层为纯 Kotlin（除少量 java.time，靠 desugaring 兼容 minSdk 24）；
- 依赖方向单向：ui → domain ← data。

## 2. 目录结构

```
app/src/main/java/com/jianji/app/
├─ JianjiApplication.kt          # Application，持有 AppContainer
├─ MainActivity.kt               # 占位入口（UI 阶段替换）
├─ di/AppContainer.kt            # 手写依赖容器（后续可换 Hilt）
├─ domain/
│  ├─ model/                     # Money、枚举、Transaction、统计模型、DateUtils
│  ├─ validation/                # TransactionValidator、ResourceValidator、错误码
│  └─ repository/                # 5 个仓储接口
└─ data/
   ├─ local/
   │  ├─ JianjiDatabase.kt       # @Database(version=1, exportSchema=true)
   │  ├─ Converters.kt           # 枚举/日期 TypeConverter
   │  ├─ SeedData.kt             # 预置分类/账户 + 建库回调
   │  ├─ entity/                 # 4 个实体
   │  └─ dao/                    # 4 个 DAO + 统计 DTO
   └─ repository/                # 5 个仓储实现（校验 + 事务 + 领域映射）
```

## 3. 关键技术决策

| 决策 | 选择 | 理由 |
|---|---|---|
| 金额表示 | `Money(minor: Long)` 值类，单位「分」 | 整数运算零精度误差；溢出即抛异常；禁止 Double/Float |
| 日期持久化 | `LocalDate → epochDay (Long)` | 设备时区变化不影响历史账目归属 |
| 统计 | DAO 内 SQL 聚合（SUM/GROUP BY/CASE），返回 Flow | 改删账目后统计自动一致，无需缓存维护；禁止全量取回内存计算 |
| 响应式 | Room Flow → Repository map → UI | 单一数据源 |
| 事务 | `androidx.room.withTransaction` + Repository 内校验 | 预算 upsert、保存校验+落库等需原子性 |
| 依赖注入 | 手写 AppContainer | 当前依赖图简单，减少构建复杂度；接口已就位，可平滑换 Hilt |
| 迁移 | exportSchema=true + MIGRATIONS 数组 + MigrationTest | 禁止破坏性迁移，升级不清库 |
| 校验 | domain 层纯函数 Validator + Repository 事务内存在性校验 | 可单测；错误码供 UI 映射文案 |
| 软删除 | 分类/账户 isArchived | 历史账目引用不失效 |
| minSdk | 24 + coreLibraryDesugaring | java.time 兼容 |

## 4. 错误处理约定

- 校验失败抛 `TransactionValidationException(ValidationError)` 或 `ResourceValidationException`；
- UI 层按 `ValidationError` 枚举映射中文提示（UI 阶段实现）；
- 金额运算溢出抛 `ArithmeticException`（视为程序缺陷，需修复而非吞掉）。

## 5. 已知边界与后续演进

- 备份/导入导出（JSON，SAF）：数据层已预留 schema 版本字段思路，实现在后续阶段；
- 多币种：当前固定「分」为最小单位；如支持多币种需新增 currency 列并编写 Migration；
- Hilt：接口齐备，引入时代价仅限 AppContainer 替换。
