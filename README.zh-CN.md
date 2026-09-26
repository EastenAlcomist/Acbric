# Acbric — 飞艇 Fabric MOD 框架

dev.23 继续外部安装重构：[实例启动设置、用户输出与纹理缓存隔离](EXTERNAL_INSTALL.zh-CN.md)。924 项标准检查、1.2.15.3 的 36 项外部加载检查、旧布局两版 142 项 ARC 检查通过；源安装 5,325 文件内容未变。新模式仍等待真实菜单/图形/战役验收，尚未开放正常启动；旧 `distZip` 仍含本地游戏依赖，不可作为干净框架包分发。下方运行/构建方法继续适用于 legacy 布局。

从 dev.21 起，按 **Esc 下方、数字 1 左侧的 ~ 键**（物理键 `GRAVE`，可带 Shift）直接打开控制台并聚焦命令框，开窗按键不会混入文本。Ctrl/Alt/Meta 组合、窗口失焦、原生错误/帮助/聊天覆盖层以及已有 Acbric 窗口时不触发；先关闭其他 Acbric 窗口再使用快捷键。控制台打开后该键仍可输入普通字符，用 Esc/X/关闭退出。长按不会反复开窗。这是固定控制台入口，尚未提供通用改键 API。

[English](README.md) | **中文**

面向《Airships: Conquer the Skies》的轻量级 Fabric 风格 MOD 加载框架。

框架把 Fabric Loader 的 `KnotClient` 启动器嫁接到游戏上，让 MOD 能用 Java 代码 + mixin
扩展游戏逻辑，同时保留并整合游戏原生的 JSON 数据 MOD 系统。

## 当前开发版与文档

当前 API 为 **0.3.3-dev.23**（开发版）。框架现有事件、配套资源保护、配置与战役数据、共享规则/大厅检查、Java MOD 重启启停、公共 UI 和设置页，命令注册与游戏内开发者工具继续可用，本版扩展外部安装原型的启动设置与纹理缓存隔离。入口：**MOD 列表 → Acbric API → 详情 → 开发者工具 / 控制台**。

- [开发文档导航与入门流程](DEVELOPMENT.zh-CN.md)
- [完整 API 手册](API.zh-CN.md)
- [命令 API](COMMANDS.zh-CN.md) / [开发者工具](DEVELOPER_TOOLS.zh-CN.md)
- [中文变更](CHANGELOG.zh-CN.md) / [English changelog](CHANGELOG.md)

dev.21 曾通过 872 项标准回归及两套真实游戏共 464 项 UI/控制台/设置重启检查，本轮结果见页首，未重跑完整 UI 矩阵；两个已有符号链接场景因主机权限跳过。绘制终端无 GPU；Windows 剪贴板、输入法、切屏及完整玩法仍需实机验收。代码一致性和存档接入不代表任意 MOD 属性自动联机同步。

---

## ⚠️ 本仓库不含任何游戏内容

本仓库只包含**框架本体**。以下三项不含：

| 不含 | 原因 |
|---|---|
| `libs/` | 游戏的编译后 class（`asplit-A.zip` / `asplit-B.zip`）以及随游戏分发的库 jar。转载涉及版权。 |
| `game/` | 游戏静态数据（`data/`、`ships/`、`images/` 等），约 1.3 GB。 |
| 功能 MOD | 不属于框架本体。 |

在补齐前两项之前，框架**无法编译，也无法运行**。详见下文。

---

## 1. 准备游戏文件

请从你合法拥有的 Airships 安装目录，按相同相对路径复制到本项目。

### `libs/` —— 编译与运行依赖

```
libs/
├── asplit-A.zip                # 游戏 class（编译 + 运行）
├── asplit-B.zip                # 游戏 class（编译 + 运行）
├── fabric-loader-0.19.3.jar    # Fabric Loader 0.19.3（来自 Fabric，不来自游戏）
├── <游戏自带的库 jar>
└── native/                     # 本地库
```

`libs/` 最终需包含游戏自身启动所用的 jar 与 zip —— 通常有 `CatEngine.jar`、
`CatSlick.jar`、`slick.jar`、`lwjgl.jar`、`lwjgl_util.jar`、`ibxm.jar`、`jinput.jar`、
`jogg-*.jar`、`jorbis-*.jar`、`commons-*.jar`、`joda-time-*.jar`、`steamworks4j-*.jar`
和 `FloatIO.jar` —— 以及游戏的 `asplit-*.zip` class 包。

> `fabric-loader-0.19.3.jar` 来自 Fabric 而非游戏，请自行下载后放入 `libs/`；
> 构建脚本目前以本地文件方式引用它。

### `game/` —— 游戏自己的目录

请复制包含 `Airships.json` 的那个游戏目录：

```
game/
├── Airships.json               # 必需 —— 启动器解析它
├── launch_settings.json        # 可选，建议保留
├── data/                       # 必需 —— 游戏数据
├── lib/、native 库 等
└── mods/                       # MOD 安装目录
```

`game/Airships.json` 是 `AirshipsGameProvider` 用来发现 `mainClass` 与 `classPath` 的文件；
缺少它，启动器无法定位游戏。

---

## 2. 环境要求

- **JDK 21** —— 使用 `JAVA_HOME` 或 Gradle 参数 `-Dorg.gradle.java.home=<JDK 路径>` 指定；仓库不再固定某台机器的安装路径。
- Gradle 8.13（通过仓库自带 wrapper 使用）。

## 3. 构建与运行

```powershell
# 编译启动层与 API，并运行无界面回归检查
.\gradlew.bat build --console=plain

# 构建框架并启动游戏
.\gradlew.bat startAirships --console=plain

# 只构建/安装 API jar
.\gradlew.bat installApiMod --console=plain

# 打包分发包（含 jlink 内置 JRE）
.\gradlew.bat distZip --console=plain
```

`build` 已包含 `apiModJar` 和 `regressionTest`。回归检查覆盖数据加载结果通知、内嵌资源解包和类路径边界，
使用 `build/regression-sandbox/` 下的独立数据，需要本地编译依赖，不启动 GUI、不使用真实存档。
操作系统不允许创建符号链接时，对应检查会明确报告跳过。这些检查不能替代对局、存档与联机验收。
检查还覆盖受管理资源升级／恢复、事件订阅语义及 Fabric 元数据校验。

分发入口为 `run.bat`；`loader-libs/` 保存启动垫片、Fabric Loader、Mixin 和 ASM，`libs/` 保存游戏依赖。
两者与 `game/`、`jre/` 保持相邻。Provider 也会按归档内容排除旧式混合 `libs/` 中的启动基础设施，避免重复类身份。
分发包包含框架 LICENSE。生成 ZIP 成功不代表已补齐完整游戏资源。

行为变更和仍待处理的限制见 [未发布变更记录](CHANGELOG.md)。
内嵌原版资源的哈希归属、冲突保留、备份和旧目录手动迁移见 [资源管理说明](BUNDLED_RESOURCES.md)。

新增 UI 事件、触发顺序、取消规则与旧接口迁移见 [事件契约](EVENTS.md)。
当前开发 API 构建为 `0.3.3-dev.20`（尚未发布）；模板最低依赖为 `>=0.3.3-dev.10`，包含需显式调用的战役数据示例。

## 4. 项目结构

```
Acbric/
├── src/
│   ├── main/            # 启动垫片：AirshipsGameProvider + GameProvider 服务注册
│   ├── apiMod/          # 运行时 API（acbric_api）：事件系统、入口桥、
│                        # 原生 MOD 界面集成、hook mixin
│   └── regressionTest/  # 无界面回归及真实面板探针入口
├── acbric-mod-template/ # 独立 MOD 模板项目
├── gradle/              # Gradle wrapper
└── build.gradle
```

### 架构

| 层 | 位置 | 职责 |
|---|---|---|
| 启动层 | `src/main` | `AirshipsGameProvider` 解析 `game/Airships.json`、拼装类路径、反射调用 `Main.main`。 |
| API 层 | `src/apiMod` | `acbric_api` —— 事件系统、入口桥、原生 MOD 界面集成、战役数据、大厅检查及 21 个 hook mixin。 |

**启动主链路**：`KnotClient.main` → ServiceLoader 发现 `AirshipsGameProvider` →
拼装类路径 → Fabric 跑 `preLaunch` 入口 → `AcbricApiPreLaunch` 遍历所有 `acbric`
入口并调用 `AcbricInitializer.onInitializeAcbric`（逐个 try/catch）→ 游戏启动 →
API 的 mixin 触发事件 → MOD 监听器执行。

## 5. 许可与声明

本仓库的框架代码以 **MIT 许可**发布，见 [`LICENSE`](LICENSE)。

游戏《Airships: Conquer the Skies》及其全部资源归其各自所有者所有，**不在**本仓库内。

战役创建、加载、恢复和退出事件从 dev.4 提供，见[生命周期手册](CAMPAIGN_LIFECYCLE.zh-CN.md)。独立存档示例作为单独 MOD 提供，不内置到框架。
