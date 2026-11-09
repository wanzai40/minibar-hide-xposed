# 小窗提示线移植 与 OShape 对比

对比对象：

- 本项目：`com.minibar.port` 2.4（versionCode 15）
- 参考模块：OShape 0.1.5（API101）中的“小窗提示线”功能

目标机型与系统：

- HyperOS 4 / Android 17
- SystemUI `17.03.260226.r`
- Xposed API 101

## 功能范围对比

| 功能 | OShape | 本项目 2.4 |
|---|---|---|
| 隐藏小窗提示线 | 支持 | 支持 |
| 去掉提示线附带的动作区占位 | 支持 | 支持，并额外处理动作区外层根 |
| 自定义提示线宽度 | 支持 | 支持 |
| 自定义提示线高度 | 支持 | 支持 |
| 自定义触发阈值 | 支持，覆盖 `mini_window_trigger_threshold` | 未实现，设置项目前仅保留界面 |
| 自定义最大触发阈值 | 支持，覆盖 `mini_window_max_trigger_threshold` | 未实现 |
| 应用白名单 | 支持 | 不移植 |
| 默认开启 | 取决于 OShape 配置 | 默认开启 |

## Hook 点对比

### OShape

OShape 的“小窗提示线”页面主要由以下类组成：

- `MiniWindowBarSize`：隐藏提示线、设置宽高、调整动作区间距
- `MiniWindowTriggerThreshold`：覆盖触发阈值 dimen
- `NotificationMiniBarWhitelist`：应用白名单
- `NotificationMiniBarFragment`：设置页面

`MiniWindowBarSize` 在隐藏开启时注册两条核心回调：

| Hook / 回调 | 作用 |
|---|---|
| `ExpandableNotificationRowInjector#updateMiniWindowBar` 之后 | `getMiniBar().setVisibility(8)` |
| `notification_material_action_list` inflate 回调 | `post` 后把 `actions_container_layout.bottomMargin` 设为 0 或 15dp |

OShape 没有为这条规则 Hook 全局 `View.setVisibility`。

尺寸覆盖方面，OShape 使用 `qb1` 的 `ResourcesLoader` / `ResourcesProvider.loadFromApk` 方案，通过资源覆盖替换 dimen；读取 `Resources.getDimension*` 时没有额外的 Java Hook。

### 本项目 2.4

隐藏开启时只安装 2 条 Hook：

| Hook | 作用 |
|---|---|
| `ExpandableNotificationRowInjector#updateMiniWindowBar` 之后 | `getMiniBar().setVisibility(GONE)`，随后 `applyRowSpace` |
| `View.setVisibility` | 拦截 `mini_window_bar` 被重新设为非 `GONE`；无按钮时拦截 `actions_container` 被重新设为 `VISIBLE` |

隐藏关闭时只安装：

| Hook | 作用 |
|---|---|
| `LayoutInflater.inflate` | 应用自定义宽高 |
| `LayoutInflater.parseInclude` | 处理动作区首次 inflate |

本项目不覆盖 `Resources.getDimension*`，宽高直接修改 `ViewGroup.LayoutParams`，与 OShape 的 `m8680` 做法一致。

## 行为差异

### 1. 动作区外层根

OShape 主要调整 `actions_container_layout` 的 `bottomMargin`。本项目额外处理外层 `actions_container`：

- `actions` 没有子按钮时，外层整块 `GONE`
- 有按钮时才 `VISIBLE`，并保留 15dp

原因是退场时 MIUI 模板会把外层根重新设为 `VISIBLE`。外层一显示，即使里面没有按钮，里层 15dp 也会占位。

### 2. 退场拦截

OShape 依赖 `updateMiniWindowBar` 之后设置 `GONE`，没有额外拦截后续的可见性变化。

本项目增加了全局 `View.setVisibility`，在系统把提示线或动作区重新显示时立即改回 `GONE`。这是本项目显示和消失都不留白的关键，也是与 OShape 最大的实现差异。

尝试删除这条 Hook、改为模板类级 `onContentUpdated` 的 2.3 版本会重新留白，说明当前 MIUI 的重新显示路径不只走模板刷新。

### 3. 尺寸覆盖

| 项目 | OShape | 本项目 2.4 |
|---|---|---|
| 宽高 | inflate / attach 后改 `LayoutParams` | 相同 |
| 阈值 dimen | `ResourcesLoader` 覆盖 | 未实现 |
| 每次读取 dimen 的 Hook 开销 | 无 | 无 |

OShape 的 `ResourcesLoader` 需要为目标系统准备资源覆盖包；本项目选择不实现阈值，因此不需要这套机制。

### 4. 白名单

OShape 通过 Hook `NotificationSettingsManager` 构造函数和 `onCloudDataUpdated`，把白名单写入 `mAllowNotificationSlide` 等字段。

本项目不包含白名单，所有通知统一处理。

## 耗电与性能对比

| 项目 | OShape | 本项目 2.4 |
|---|---|---|
| 隐藏开启 Hook 数 | 2 条核心回调 + 共用 inflate 基础设施 | 2 条 |
| 全局可见性 Hook | 无 | 有，1 条 |
| 尺寸读取 Hook | 无，使用资源覆盖 | 无 |
| 每条通知重算 | `updateMiniWindowBar` 时一次 | 相同 |
| 定时器 / 轮询 / WakeLock | 无 | 无 |
| 待机额外唤醒 | 无 | 无 |

本项目比 OShape 多一条全局 `View.setVisibility`。这条 Hook 的成本特征：

- SystemUI 每次可见性变化都会进入
- 先比较 `view.getId()` 与缓存的两个目标 id
- 不命中直接返回，不读开关、不查资源
- 命中后才读 `Prefs.hide()` 并执行一次 `setVisibility`

通知栏静止时不会触发；通知展开、收起、来去时会有几百到上千次可见性变化，按每次几微秒计算，属于毫秒级开销，不构成持续耗电。

OShape 的尺寸覆盖使用 `ResourcesLoader`，在资源读取阶段没有 Java Hook，这一点比直接 Hook `Resources.getDimension*` 更轻。本项目已经删除尺寸 Hook，因此两者在这部分开销接近。

综合来看：

- 待机耗电：两者都没有额外唤醒，基本一致
- 使用耗电：本项目多一条全局可见性 Hook，略高一点，但差距很小
- 功能完整度：OShape 多了阈值和白名单；本项目专注隐藏和去留白

## 兼容性对比

OShape 的规则面向多个 HyperOS 版本做了分支：

- `DynamicIslandBaseContentView#updateMiniBar`
- `MiuiExpandableNotificationRow#updateMiniWindowBar`
- `ExpandableNotificationRowInjector#updateMiniWindowBar`

在本项目目标 SystemUI `17.03.260226.r` 中：

- `DynamicIslandBaseContentView#updateMiniBar` 不存在
- `MiuiExpandableNotificationRow#updateMiniWindowBar` 不存在
- `NotificationSettingsManager#updateMiniBar` 不存在

因此本项目以 `ExpandableNotificationRowInjector#updateMiniWindowBar` 为主路径，另外两个旧类只在存在时尝试 Hook。

## 结论

本项目不是 OShape 的完整复制，而是只保留“隐藏提示线并去掉占位”所需的最小实现：

- 与 OShape 相同的部分：隐藏时机、动作区 15dp 处理、宽高通过 `LayoutParams` 设置
- 比 OShape 多的部分：动作区外层根处理、全局可见性拦截
- 比 OShape 少的部分：触发阈值、最大触发阈值、白名单、`ResourcesLoader` 资源覆盖

在当前机型上，本项目用 2 条 Hook 实现了“显示和消失全过程不留白”；代价是牺牲了阈值和白名单功能，并依赖一条全局可见性 Hook。
