# 简记 · 设计系统（DESIGN_SYSTEM）

> 视觉原则：极简、清爽、温暖、克制。层级靠颜色与留白表达，不用重阴影与渐变。

## 1. 设计令牌（ui/theme/）

| 令牌 | 浅色 | 深色 | 用途 |
|---|---|---|---|
| primary | `#4F7D6B` | `#8FC3AC` | 主操作、选中态、记账 FAB |
| background | `#FAF9F6` 暖白 | `#15171A` | 页面底色 |
| surface | `#FFFFFF` | `#1D2023` | 卡片 |
| surfaceVariant | `#F1EFE9` | `#262A2E` | 输入框、键盘、芯片底 |
| onSurface | `#23262A` | `#E9EAEC` | 正文 |
| onSurfaceVariant | `#6B6F74` | `#A8ADB3` | 次要文字 |
| outlineVariant | `#EFEDE6` | `#2A2E33` | 卡片描边（0.5dp） |
| income | `#3F8F6B` | `#6FBF97` | 收入金额/图例 |
| expense | `#C2594E` | `#E58376` | 支出金额/超支提醒 |
| tertiaryText | `#9BA0A6` | `#7E848A` | 占位提示 |

语义色通过 `LocalSemanticColors` 下发（income/expense/divider/warning），
不进入 Material ColorScheme，便于独立演进。

## 2. 字体（Type.kt）

- 默认系统字体，不引入外部字体文件；
- 金额样式统一 `fontFeatureSettings = "tnum"`（等宽数字），滚动不跳动；
- 字号阶梯：displayLarge 44（记账页金额）/ displayMedium 32（结余）/
  titleLarge 18 / bodyLarge 15 / bodyMedium 14 / labelLarge 14 / labelSmall 11；
- 适配系统字体缩放：所有尺寸用 sp，布局不写死高度。

## 3. 圆角与间距（Shape.kt / Dimens.kt）

- 圆角：卡片 20dp、控件 14-16dp、芯片/标签圆形；
- 间距 4dp 网格：4 / 8 / 12 / 16 / 20 / 24 / 32；页面水平边距 20dp，卡片内边距 16dp；
- 最小触控区 48dp（底部导航、图标按钮均满足）。

## 4. 组件（ui/components/）

| 组件 | 说明 |
|---|---|
| `JianJiCard` | 暖白卡 + 0.5dp 细边框 + 20dp 圆角，无重阴影 |
| `SectionHeader` | 区块标题 + 右侧文字操作 |
| `AmountText` | 按交易类型着色的金额文本 |
| `MonthSelector` | ‹ 2026年10月 › 月份切换 |
| `CategoryAvatar` | 分类/账户头像（图标键名 → Material 图标映射） |
| `NumberKeypad` | 记账专用键盘（不唤起系统输入法规避键盘遮挡）；第 4 行为 [收起][.][0][⌫] |
| `TrendChart` | 收入/支出双柱趋势图，点选查看数值，支持空数据 |
| `DonutChart` | 分类占比环形图，中心显示总额 |
| `ProgressBar` | 整数 permille 驱动，超 100% 切换提醒色 |
| `TagChip` | 筛选芯片 |
| `JianJiBottomBar` | 4 Tab + 中央记账按钮（无涟漪） |
| `EmptyState` / `LoadingState` / `ErrorState` | 完整状态设计 |

## 5. 图标规范

- 统一 Material 线性/面性图标（material-icons-extended）；
- 数据库只存图标键名（如 `restaurant`），`CategoryIcon.kt` 集中映射，
  换图标库只改一处；未收录键名回退 `Category`；
- 分类头像：支出用主色浅底、收入用收入色浅底，选中加深。

## 6. 数字键盘规范（记一笔页）

- 4 列 × 4 行对称网格，按键高 52dp、圆角 14dp，满足最小触控区：
  ```
  1  2  3  ⌫
  4  5  6  清空
  7  8  9  .
  00 0  保存 ⌄
  ```
- 样式层级：数字键灰底；`保存` 主色强调（与右上角保存同一逻辑）；
  `⌫/清空/./00/⌄` 无底色弱化
- `00`：一次输入两个 0（遵守两位小数与 10 位整数上限，放不下时补足到上限）；
  `清空`：一键清空已输金额
- 「⌄」收起键盘：`AnimatedVisibility` 纵向收起；点击金额显示区唤回
  （收起时显示提示“点击金额区域唤起数字键盘”）
- 每个按键语义合并（mergeDescendants），读屏可正确朗读“退格/收起键盘”等标签

- 仅三处：底部导航显隐（slideVertical）、进度条（animateFloatAsState）、
  记账页光标闪烁（infiniteTransition）；
- 无页面转场特效、无装饰性动画。

## 7. 应用锁（安全）

- **入口**：冷启动即验证；`MainActivity` 外层 `AppLockGate` 拦截，未解锁时应用内容完全不可见
- **验证方式**：系统 `BiometricPrompt`（`BIOMETRIC_WEAK | DEVICE_CREDENTIAL`），
  App 不接触密码/生物特征；API 30+ 支持指纹/人脸/锁屏密码二选一，
  API 24-29 自适应降级为单一能力；设备无任何验证方式时开关禁用并提示
- **锁屏页**：应用标识（“记”徽章）+ 简记 + “应用已锁定” + 能力说明文案 +
  错误文案（支出红）+ 「解锁」按钮；进入时自动发起一次验证，失败/取消可点按钮重试
- **锁定时机**：冷启动必验；离开超过 **60 秒** 返回需重新验证（`MainActivity` 前后台计时）
- **开启流程**：「我的 → 安全 → 应用锁」开启时**先通过一次系统验证**才生效（防他人偷关）；
  默认关闭
- **隐私**：锁定时加 `FLAG_SECURE`（禁截屏、防最近任务预览），解锁后移除
- **安全边界（已知限制）**：只锁 UI 入口，Room 数据库文件未加密；
  root/ADB 可绕过；彻底的数据静态加密（SQLCipher）属独立工程，未实现

## 8. 记一笔页布局规范

- 顶部栏：`[X 关闭] [标题] [删除(编辑态)] [保存]`——**保存按钮固定右上角**，
  文字按钮样式：可保存时主色、否则置灰；标题 `maxLines=1` 防窄屏挤压
- 保存按钮**始终可点击**（仅样式置灰）：点击时按缺失项给出具体错误
  （金额/分类/账户/转入账户），不做“点了没反应”的禁用按钮
- 页面主体：类型切换 → 金额输入（视觉焦点）→ 分类横滑 → 日期/账户/备注卡
- 底部：数字键盘（可收起）；无底部保存按钮，键盘收起时底部为空白
- 分类横滑项带选中语义（`selected`），读屏可感知选中状态

## 9. 状态与反馈

- 加载中：`LoadingState`（旋转指示 + 文案）；
- 空数据：`EmptyState`（图标 + 标题 + 副标题 + 可选行动按钮）；
- 错误：表单内联错误（金额/分类/账户/日期校验文案见 `ErrorMessages.kt`）+
  页面级 `ErrorState` 带重试；
- 保存成功：页面自动关闭并返回列表（数据已实时刷新）；
- 删除：二次确认对话框；
- 未实现功能（导出/备份）：设置页明确标注「规划中」，不做无行为装饰按钮。

## 8. 适配

- 状态栏/导航栏：`enableEdgeToEdge` + `WindowCompat` 随主题切换深浅色图标；
- 底部导航 `navigationBarsPadding`，键盘页 `imePadding`；
- Compose `WindowInsets` + `sp` 单位适配系统字体缩放；
- 布局支持不同屏幕宽度（横滑分类、网格分类面板、自适应图表宽度）。

## 9. 已知取舍

- debug 包 19.1MB：material-icons-extended 体积较大，release 开启 R8 后会大幅缩减；
- 主题持久化用 DataStore（`SettingsRepository`），当前仅主题模式一项。
