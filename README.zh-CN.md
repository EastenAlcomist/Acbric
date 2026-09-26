# Acbric — 飞艇 Fabric MOD 框架

2026-09-26 dev.29：安装器新增“从 Steam 查找游戏”，读取注册表和多游戏库配置，找不到时提示手动选择；新增本地 ZIP 更新、失败自动恢复与手动恢复上一版。更新保留 MOD、实例、存档、设置与启动绑定；修改冲突拒绝覆盖，中断后先恢复再启动。发行根目录附 使用说明.txt / QUICK_START.txt，入口 Update Acbric.cmd；详见 INSTALLER.zh-CN.md / INSTALLER.md。无联网自动更新，不新增 MOD API；下方旧记录为历史。

2026-09-26 dev.28：按用户要求，Setup.cmd、根目录 Start Acbric.cmd 与 mods 统一位于 Acbric 内同一层。安装器保存时更新默认实例绑定，根入口启动最近保存的实例；原实例入口兼容保留。dev.27 外层 mods 请手动复制到 Acbric/mods。启动绑定缺失/损坏、实例缺失和框架搬迁均明确诊断；不自动猜测实例。双语提示、路径 API 文档和模板安装目标已同步。下方 dev.27 及更早记录为历史。

dev.27：Java MOD 的 `.jar` 和原版 MOD 文件夹统一放到 **Acbric 同级的 `mods`**。安装器显示完整路径并提供“打开 MOD 目录”。核心 API 仍从 `Acbric/core` 自动加载；存档、配置、日志留在实例中。多个实例使用同一框架时共用 MOD 文件，但保留各自设置；同一 MOD 目录只允许一个游戏会话，避免配套资源被并发修改。旧目录不自动搬迁，手动复制需要的 MOD 即可。下方 dev.26 及更早记录为历史。

2026-09-26 dev.26：用户批准安装器首版，已实现 Setup.cmd / setup.ps1 中英文实例配置向导，复用外部预检查、发行核心验证和实例锁。可只读检查目录、创建新实例、读取本向导创建的实例并重新绑定路径，原子保存配置及生成 acbric-launcher/Start Acbric.cmd；可启动游戏、打开实例/日志目录。配置和入口损坏/用户修改时拒绝覆盖，过期预览不能覆盖并发保存；不导入旧布局数据。文档入口 INSTALLER.zh-CN.md / INSTALLER.md。暂未实现自动探测、自动桌面快捷方式、升级/回滚或卸载；GL 问题继续暂缓。基于 944435a，尚未提交/推送；下方为历史记录。

dev.26 验证：961 项标准回归通过（新增 26 项实例配置/界面检查，两项既有符号链接权限跳过）；36 项真实外部加载及核心保护通过；6 项发行扫描对抗测试通过。正式解压包经 setup.ps1 配置，再实际执行生成的 CMD，带 ARC 首次/框架搬迁重新绑定后两次真实菜单各绘制 30 帧并初始化音频。覆盖中文、空格和 & 路径、占用拒绝、坏包、错误安装、UTF-8 模板构建、Java 8 提前拒绝；源安装 5,325 文件内容不变。中英文面板截图已检查；未自动操作原生文件选择器或验证所有 DPI，也未重跑完整战役/媒体/联机。证据在工作区 99-研究工具/Acbric实例安装器-dev26-20260926。

2026-09-26 dev.25：已实现正式外部启动器 `start.ps1` / `ExternalLauncher` 和不含游戏内容的 `externalDistZip`，核心 API 从发行包 core 加载；启动输出保存到实例日志，所选核心在游戏类路径开放及 preLaunch 前核对。实例锁、显式路径及缓存隔离沿用既有流程。模板改为 UTF-8 local.properties 引用本机游戏/框架，发行允许清单排除本地依赖及个人路径。使用说明见 EXTERNAL_START.zh-CN.md / EXTERNAL_START.md。GL 已知问题继续暂缓、不阻塞；按用户决定不开发旧布局自动迁移；玩家重新配置，下一步为安装器/更新/卸载。本阶段变更已纳入 dev 分支，未推送；旧 distZip 仍不能作为干净框架包分享。下方 dev.24 及更早记录为历史。

本轮验证：935 项标准回归通过（两项既有符号链接权限跳过），36 项真实外部加载检查通过；另测解析后核心缺失/版本错配在 preLaunch 前拒绝、Java 8 引导拒绝。解压包在中文/空格路径下用正式入口启动两次，每次真实菜单绘制 30 帧并创建音频，第二次验证包内 Java 优先；另有 API + ARC 两次菜单启动记录。实例占用、核心哈希损坏、错误游戏路径、独立模板 UTF-8 本地路径构建均通过。6 项发行扫描对抗测试通过；所有运行场景源安装 5,325 文件内容未变。证据：工作区 99-研究工具/Acbric正式外部发行-dev25-20260926。此轮不重跑完整战役/媒体；此前 dev.24 结果保留其原边界，不扩张为跨设备/Workshop/全部 MOD 验收。

2026-09-26 媒体验收：dev.24 基线 `42426a0` 已提交、未推送；后续战役及媒体验收代码/文档尚未提交。新增 DLC/原生 MOD 重载与真实 GIF 测试，两进程各 35 项，共 70 项功能检查通过；普通 960×640/15 帧、半尺寸慢速 480×320/30 帧，共 4 个 GIF，旧文件未覆盖。源安装 5,325 文件内容未变，924 标准回归通过（两项既有权限跳过）。但两进程各记录 454,032 次重复原生 GL 错误，状态为 PASS_WITH_NATIVE_GL_ERRORS；关闭外部适配的旧布局严格对照也在地形绘制处复现（首帧 2,268 次）。这不是完整图形验收通过，未修复/屏蔽该错误。2026-09-26 用户决定将该问题记为低优先级、暂缓处理，不阻塞本次安装重构；旧布局也能复现，但尚无完全不加载 Acbric 的纯原版对照，不能据此断定归因于框架或游戏。当前代码的正常外部入口仍为 EXTERNAL_NOT_READY；下一步实现正式外部入口与干净发行/模板，再推进迁移和安装器。产品 JAR 与 dev.24 相同，ARC 源码和玩家运行副本未改；旧 distZip 仍非干净发行包。

从 dev.21 起，按 **Esc 下方、数字 1 左侧的 ~ 键**（物理键 `GRAVE`，可带 Shift）直接打开控制台并聚焦命令框，开窗按键不会混入文本。Ctrl/Alt/Meta 组合、窗口失焦、原生错误/帮助/聊天覆盖层以及已有 Acbric 窗口时不触发；先关闭其他 Acbric 窗口再使用快捷键。控制台打开后该键仍可输入普通字符，用 Esc/X/关闭退出。长按不会反复开窗。这是固定控制台入口，尚未提供通用改键 API。

[English](README.md) | **中文**

面向《Airships: Conquer the Skies》的轻量级 Fabric 风格 MOD 加载框架。

框架把 Fabric Loader 的 `KnotClient` 启动器嫁接到游戏上，让 MOD 能用 Java 代码 + mixin
扩展游戏逻辑，同时保留并整合游戏原生的 JSON 数据 MOD 系统。

## 当前开发版与文档

当前 API 为 **0.3.3-dev.29**（开发版）。框架现有事件、配套资源保护、配置与战役数据、共享规则/大厅检查、Java MOD 重启启停、公共 UI 和设置页，命令注册与游戏内开发者工具继续可用，本版修复外部模式中文安装路径音频并验证真实菜单。入口：**MOD 列表 → Acbric API → 详情 → 开发者工具 / 控制台**。

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
