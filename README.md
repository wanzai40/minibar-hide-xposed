# 小窗提示线移植（MiniBarPort）

一个面向小米 HyperOS 4 / Android 17 的独立 Xposed 模块，用于隐藏通知小窗提示线，并去掉提示线附带的整块占位，做到通知显示和消失全过程都不留白。

本项目参考 OShape 中“小窗提示线”功能的实现思路，但为独立编写的精简移植版，不包含白名单功能。

## 功能

- 隐藏通知小窗提示线（`mini_window_bar`）
- 隐藏提示线附带的动作区占位，通知消失时也不会重新留白
- 可选自定义提示线宽度、高度
- 默认开启，安装后无需手动打开开关

## 兼容性

| 项目 | 值 |
|---|---|
| 系统 | HyperOS 4 / Android 17 |
| SystemUI 版本 | `17.03.260226.r` |
| 包名 | `com.minibar.port` |
| 当前版本 | 2.4（versionCode 15） |
| Xposed API | 101 |
| Hook 范围 | `com.android.systemui` |

已在上述 SystemUI 版本实机验证。其他 HyperOS 4 版本可能因 MIUI 通知模板实现变化而失效。

## 安装

1. 安装 APK。
2. 在 LSPosed 中启用模块，作用域只勾选“系统界面”（`com.android.systemui`）。
3. 重启手机，或至少重启 SystemUI。
4. 打开模块设置页，确认“隐藏小窗提示线”处于开启状态。

修改“隐藏”开关或宽高设置后，需要重启 SystemUI 才会重新安装对应的 Hook 组。

## 实现方式

模块按开关状态只安装必要的一组 Hook。

隐藏开启（默认）时安装 2 条：

| Hook | 作用 |
|---|---|
| `ExpandableNotificationRowInjector#updateMiniWindowBar` | 系统更新完提示线状态后，把提示线设为 `GONE`，并清理动作区占位 |
| `View.setVisibility` | 拦截退场时系统把 `mini_window_bar` 或 `actions_container` 重新设为 `VISIBLE` |

隐藏关闭时安装 2 条：

| Hook | 作用 |
|---|---|
| `LayoutInflater.inflate` | 应用自定义提示线宽高 |
| `LayoutInflater.parseInclude` | 处理 `notification_material_action_list` 的首次 inflate |

关键点：

- 只隐藏线不够。动作区外层 `actions_container`（`0x7f0b00ad`）在退场时会被 MIUI 模板重新设为 `VISIBLE`，里层 15dp 的 `marginBottom` 会再次占位。
- 因此除了隐藏线，还要在没有动作按钮时把整个动作区外层设为 `GONE`。
- 全局 `View.setVisibility` 是退场留白的关键拦截点，去掉后 2.3 版本会重新出现留白。

## 构建

依赖：

- JDK 17
- Android SDK `platforms/android-33/android.jar`
- Android Build Tools 34（`aapt2`、`d8`、`zipalign`、`apksigner`）
- libxposed API 101 的 `classes.jar` 或 `libxposed-api-101.0.1.jar`

构建流程：

```bash
export JAVA_HOME=/path/to/jdk-17
export PATH="$JAVA_HOME/bin:$PATH"

AAPT2=/path/to/build-tools/34.0.0/aapt2
D8=/path/to/build-tools/34.0.0/d8
ZIPALIGN=/path/to/build-tools/34.0.0/zipalign
APKSIGNER=/path/to/build-tools/34.0.0/apksigner
ANDROID_JAR=/path/to/platforms/android-33/android.jar

rm -rf build_new
mkdir -p build_new/classes build_new/dex

"$AAPT2" compile --dir app/src/main/res -o build_new/res.zip
"$AAPT2" link -o build_new/base.apk \
  -I "$ANDROID_JAR" \
  --manifest app/src/main/AndroidManifest.xml \
  --min-sdk-version 26 --target-sdk-version 33 \
  -R build_new/res.zip --auto-add-overlay

javac -source 8 -target 8 -encoding UTF-8 \
  -cp "$ANDROID_JAR:app/libs/libxposed-api-101.0.1.jar" \
  -d build_new/classes $(find app/src/main/java -name '*.java')

"$D8" --release --min-api 26 --lib "$ANDROID_JAR" \
  --output build_new/dex $(find build_new/classes -name '*.class')
```

随后把 `classes.dex` 与 `META-INF/xposed/` 下的 `module.prop`、`java_init.list`、`scope.list` 写入 APK，再执行 `zipalign` 和 `apksigner` 签名。

## 版本历史

| 版本 | 主要变化 |
|---|---|
| 1.0 | 新工程首个版本，补全 LayoutInflater 三条 Hook |
| 1.1 | 清理 MIUI 通知行自身占位，解决显示阶段留白 |
| 1.2 | 增加可见性拦截，但退场仍留白 |
| 1.3 | 处理动作区外层根，显示和消失均不留白 |
| 1.4 | 精简掉边距、字段、可见性和尺寸 Hook，留白回归 |
| 1.5 | 恢复全局可见性 Hook，留白全部消失 |
| 1.6 | 删除 `createViewFromTag`，精简资源 id |
| 1.7 | 可见性热路径提前返回，资源 id 单条缓存 |
| 1.8 | 合并重复 post，目标 id 缓存为静态变量 |
| 1.9 | 按隐藏开关裁剪 Hook 组 |
| 2.0 | 隐藏开启时只保留 2 条 Hook |
| 2.1 | 去掉重复的 post 清理 |
| 2.2 | 缓存反射 Method / Field |
| 2.3 | 尝试模板类级 Hook，留白回归，已删除 |
| 2.4 | 回退到 2.2 的全局 Hook 方案，提升 versionCode 便于覆盖安装 |

完整的逐版本差异见 `小窗提示线移植-版本差异.md`。

## 已知限制

- 不包含白名单，所有通知统一隐藏。
- “触发阈值”“最大触发阈值”设置项目前仅保留界面，未实现尺寸覆盖。
- 全局 `View.setVisibility` 会在 SystemUI 的每次可见性变化时执行一次 id 判断，开销很低，但不是零成本。
- 系统更新后，MIUI 通知模板或资源 id 变化可能导致 Hook 失效。

## License

本项目采用 [MIT License](LICENSE)。允许个人或组织在保留版权与许可声明的前提下使用、复制、修改、合并、发布、分发和再授权；作者不承担任何使用担保或责任。

## 声明

本项目仅供个人学习与系统定制使用，不隶属于小米、HyperOS 或 OShape。使用 Xposed 模块存在系统不稳定风险，请自行备份并承担使用后果。

