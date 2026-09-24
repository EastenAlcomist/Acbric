# AGENTS.md — Acbric

2026-09-26 提交核验：用户要求提交 dev.20–dev.21 并开始讨论第一个功能 MOD。47 个变更文件和 4 个交付产物均匹配 dev.21 已验证快照；仅补充本条记录，沿用 872 标准 / 两版 464 集成结果，不重复运行未改代码。没有新增逐项实机矩阵，不扩张 OS/GPU 验收结论。未要求推送。下一步先确定功能 MOD 的玩法目标和首版范围；先前城市升级试点仍未重新授权实施，独立示例/源码快照不属于本 Git 仓库。

2026-09-26 当前 dev.21（未提交/未推送）：用户实测 dev.20 正常，要求 ~ 直接开控制台。已实现固定 GRAVE/Shift+GRAVE 入口与命令框自动焦点，按帧消费开窗输入，保护已有 Acbric 窗口和原生错误/帮助/聊天，Ctrl/Alt/Meta 不触发，按住不重开，Esc 关闭。通用快捷键/改键接口仍未实现。872 标准 / 两版 464 真实 Fabric 检查通过（新增 50 项）。证据位于工作区 99-研究工具/Acbric控制台快捷键-20260926；旧版本数字与状态为历史。

## 当前状态：dev.20（2026-09-26，未提交）

用户批准统一文档、开发者工具和框架控制台；快捷键系统仍暂缓。基于 dev c832f21，新增 `context.commands()`、`command/*`、`diagnostics/*` 和兼容的 `UiNode.onSubmit`。入口是 Acbric API → 详情 → 开发者工具 / 控制台，全部新界面、API 文档和独立示例同步中英文。

文档从 DEVELOPMENT.md / DEVELOPMENT.zh-CN.md 开始；命令契约 COMMANDS、工具契约 DEVELOPER_TOOLS。诊断 public records 是公开 API；报告 JSON、impl、UiRuntime、DeveloperConsoleUi 是内部适配。执行绑定游戏线程，重新取得界面/战役，SHARED 当前始终拒绝；后台导出只接收纯数据，不能操作活游戏对象。诊断缓冲有界、不截获全局 stdout，普通事件异常继续传播。

验证：872 标准检查、两套真实 Fabric 共 414 项，dev.19 的 81 公开类型/449 成员签名保留。两项既有符号链接场景权限跳过；原生绘制终端无 GPU，Windows 剪贴板成功/IME/切屏待实机，不扩张验收结论。独立示例 ../acbric-console-demo 0.1.0，旧 UI 展示 0.3.0 保持兼容；示例不加入默认发行包。证据 ../../99-研究工具/Acbric开发者控制台-20260926，测试手册在工作区 01-文档。

用户授权清理旧构建：按本轮 cleanup-plan/result 逐文件处理，仅清理 03-Acbric/构建产物 内旧二进制包，源码 ZIP 移至 04-开发源码/历史源码快照。不得扩大删除研究夹具、玩家完整运行包或反编译资料。本轮未要求 Git 提交/推送。以下各轮状态/数字为历史，当前状态以本节和 Git 为准。

## 历史开发记录与持续工程约束

2026-09-26 提交核验：用户要求提交 dev.19 并讨论下一步。25 个变更文件与上一轮已验证源码快照一致，构建产物哈希匹配；仅更新本条状态说明，沿用 810 项标准/两版 356 项集成结果，不重复运行未改动代码的测试。本轮没有新的逐项实机验收记录，不扩大 Windows 剪贴板、IME 或切屏覆盖结论。未要求推送；快捷键与诊断的后续设计尚未实施。

dev.19 开发记录（基于 dev cccd4bd）：完成文本选区/鼠标定位/剪贴板/重复键及设置固定页脚、主动关闭确认。契约见 UI.md / UI.zh-CN.md、SETTINGS.md / SETTINGS.zh-CN.md。玩家主动 requestClose 可确认；程序 close 与生命周期清理必须直接执行。原生字体字宽按 UTF-16 累加，选区停在码点边界，输入框关闭游戏格式命令、仅绘制可见片段。显示失焦取消拖动/重复及原拖动释放；禁止合成按钮连发。双语示例 0.3.0。用户本轮批准实施上一轮建议第 1 项；快捷键注册、可视化诊断尚未实施。

dev.19 验证：810 项标准检查（新增 41 项），两版真实 Fabric 各 169 + 5 重启 + 4 损坏配置启动，共 356 项；dev.18 的 80 个公开类型/428 个成员签名保留。剪贴板成功用替身、不可用用真实无头 AWT；原生字体/输入已检查，绘制终端无 GPU，Windows 剪贴板/截图/输入法和失焦仍待实机。以下 dev.18 及更早数字为历史记录。

2026-09-26 实机验收：用户确认 dev.18 测试没有发现问题，并要求提交。提交前全部 28 个变更文件与构建验证快照一致；本次只补充验收说明，沿用 769 项标准检查和两版共 304 项集成检查，不重复测试未改动的代码。未提供逐项实机测试矩阵，此反馈不代表全部 GPU、输入法或第三方 MOD 组合均已覆盖。

最终验证：769 项标准检查（57 项新增）、两版共 304 项真实 Fabric/界面/示例保存/新进程重启及损坏配置启动检查通过，保留 dev.17 的 71 个公开类型/354 个成员。UI 与设置双语文档示例编译检查。绘制终端无 GPU；用户实机反馈见上方，自动化验证不覆盖操作系统显示、输入法和切屏。

dev.18 设置界面：设置契约入口 SETTINGS.md / SETTINGS.zh-CN.md。字段显式声明，草稿独立；ModConfig.save(expected,data) 使用同句柄快照身份检查内存冲突，磁盘保存成功才发布新内存。失败保留草稿，不自动迁移/同步/改已有战役。Ui.textField Supplier 重载须同步写回，默认值/重读用绑定刷新。标准 769 项；SettingsUiRegression 依赖原生语言资源，由真实 Fabric 探针执行。普通 JDK 回归为 FloatIO 单独补丁 jdk.unsupported，生产 Knot 启动不变。展示 MOD 0.2.0，入口“打开设置/查看设置生效状态”，future 仅模拟，不做真实战役试点。用户已确认测试正常并授权本轮提交；未要求推送。

2026-09-26 实机反馈：用户确认 dev.17 本轮 UI 修复可以继续推进。本阶段包含公共组件、详情/工具入口、中英文、输入框定位、切屏坐标与关闭字形修复。已有 712 项标准检查、两版共 230 项集成检查；本次提交前 34 个变更文件与已验证快照完全一致，仅补充验收记录，不重复运行未变更代码的测试。此确认不扩大为全部 GPU/输入法/第三方 MOD 组合已验收。

Fabric 风格的 MOD 加载框架,面向策略游戏《Airships: Conquer the Skies》(Java,
`com.zarkonnen.airships` 包,无混淆、无映射命名空间)。框架把 Fabric Loader 的
`KnotClient` 启动器嫁接到该游戏上,让 MOD 能用 Java 代码 + mixin 扩展游戏逻辑,
同时保留并整合游戏原生的数据 MOD 系统。

> **本仓库只含框架本体**：`src/main`(启动层)+ `src/apiMod`(API 层)+
> `acbric-mod-template/`(MOD 模板)。**不含**任何游戏内容(`libs/`、`game/`)、
> **不含**功能 MOD、**不含** `docs/`。缺少游戏文件时无法编译或运行,准备方式见 `README.md`。

## 构建与运行

**日常用快速入口，别直接用 `gradlew build`**（那会连带跑全部测试、刷上百行输出）：

```powershell
build                              # 只编译（Windows cmd；PowerShell 写 .\build）
build full                         # 编译 + 全部回归
build clean                        # 清理后编译
build <任意 gradle 任务>            # 其余参数原样透传，如 build installApiMod
./build.sh                         # Linux / macOS / Git Bash 的对应写法
```

入口脚本只做两件事：**定位 JDK 21**、**把命令交给 `gradlew`**；
`JAVA_HOME` 指向 Java 8 也会被跳过继续找。完整说明、排错与跨平台约束见
**`BUILDING.zh-CN.md`**（[English](BUILDING.md)）—— 加新脚本或改构建行为前必读。

日常测试同理，别每次都跑全量：

```powershell
test all             # 全部套件 872 项断言（收尾前必跑）
test event           # 只跑 event（改 Event.java 时）
test ui              # 只跑 ui（改 UiRuntime / 输入遮蔽时）
test devtools        # 只跑 devtools（改控制台 / 命令绑定时）
test settings        # 别名也行（settings -> config）
test list            # 列出套件与覆盖范围
./test.sh event      # Linux / macOS / Git Bash
```

套件 15 个、共 872 项断言：原有 `data` `bundle` `classpath` `event` `rename` `mods`
（对应 CHANGELOG 的 F01–F12），加上 dev 功能线的 `campaign` `config` `scopes`
`manifest` `handshake` `rules` `identity` `ui` `devtools`；每个套件的覆盖范围与断言数见
`BUILDING.zh-CN.md` 的套件表（实测值，加完套件要更新）。选择支持唯一前缀（`test ev`）
与别名；未知或歧义会直接报错。
**`check` 仍然依赖 `regressionTest`，不带选择参数时跑全部套件，所以 CI 行为没变。**

其余 Gradle 任务不变：

```powershell
.\gradlew.bat startAirships        # 构建框架并启动游戏(KnotClient)
.\gradlew.bat installApiMod        # 只构建/安装 API jar 到 game/mods/
.\gradlew.bat distZip              # 打分发包(含 jlink 内置 JRE)
```

- 要求 **JDK 21**（使用 `JAVA_HOME` 或 `-Dorg.gradle.java.home` 指定）、Gradle 8.13；JavaCompile 固定 UTF-8 和 Java 21 输出目标。
- **不要在 `build.cmd`/`build.sh`/`test.cmd`/`test.sh` 里加逻辑**：它们只是"找 JDK + 转发"。
  要加行为就加 Gradle 任务，这样 IDE 与 CI 同样受益。
- **前置游戏文件不在仓库内**,须从自有的 Airships 安装目录复制:
  - `libs/` — `asplit-A.zip` / `asplit-B.zip`(游戏 class)、`fabric-loader-0.19.3.jar`、
    游戏自带的库 jar、`libs/native/`
  - `game/` — `Airships.json`、`launch_settings.json`、`data/` 等
  
  缺失时 `compileApiModJava` 会因找不到 `libs/asplit-*.zip` 失败,`startAirships` 也会因
  GameProvider 定位不到 `game/Airships.json` 而无法启动。
- `build` 包含启动层、`apiModJar` 和 `src/regressionTest` 下的无界面检查；`regressionTest` 隔离用户数据，符号链接权限不足时会报告跳过。仍需单独验收真实游戏玩法。
- 分发的 `loader-libs/` 仅进入启动类路径，`libs/` 保存游戏依赖。Provider 会按归档内容排除 Loader/Mixin/ASM/启动垫片，兼容旧的混合库目录；不要把这些类再次加入游戏加载器。
- UI 事件契约与迁移见 `EVENTS.md`：旧 `ONE_SHOT_*` 保留 RenameShipPanel 触发和标签，禁止悄悄重定向；新功能使用 `RENAME_SHIP_*`。新事件排在旧事件之后，任一 BEFORE 取消则跳过后续组、原方法及所有 AFTER。
- 完整 API 手册见 `API.md`（英文）和 `API.zh-CN.md`（中文），累计中文变更见 `CHANGELOG.zh-CN.md`；修改公开接口时同步中英文手册、示例与依赖版本。
- 用户已批准首批通用接口：按 MOD ID 隔离的战役 JSON 存储，见 `CAMPAIGN_DATA.md` / `CAMPAIGN_DATA.zh-CN.md`。用户进一步批准战役生命周期事件及独立存档示例；城市升级试点仍暂缓，后续接口另行确定。构建身份/会话诊断见 `DIAGNOSTICS.md` / `DIAGNOSTICS.zh-CN.md`，诊断类和报告格式不是公开 API。
- 战役数据必须保留缺失 MOD 的命名空间；声明的块损坏或格式不支持时中止加载，不回退默认值。迁移显式执行且只处理副本；保存/校验不得执行 MOD 迁移回调。写入不会广播，勿把接入原生状态恢复描述成自动网络同步。
- 所有自有 Java 源码使用 UTF-8 中文文件头，说明职责；复杂流程注释说明约束和原因，避免逐行复述。JSON 不添加注释，自动生成的 Gradle wrapper 与第三方许可证保持原样。
- 当前行为变更见 `CHANGELOG.md`；资源更新、冲突保护和显式迁移见 `BUNDLED_RESOURCES.md`。不要清空 Loadable 诊断或把失败结果改成成功。
- 资源更新只能改写哈希仍匹配的已归属文件；保留用户修改和旧无归属目录。不要绕过备份、锁和恢复日志直接覆盖目录。
- `registerOnce` 使用独立订阅和原子触发状态；已消费的代理通过空监听器 invoker 返回中性结果。普通事件异常仍向调用方传播，不在本轮改变策略。
- 功能 MOD 优先从独立模板建立项目；不要默认把功能 MOD 的 source set 或发行产物塞进框架本体。

## 两层架构(本仓库范围)

| 层 | 位置 | 职责 | 依赖 |
|---|---|---|---|
| 启动层 | `src/main` | `AirshipsGameProvider` 把游戏塞进 Fabric(解析 `game/Airships.json`、拼类路径、反射调 `Main.main`) | Fabric Loader；不依赖游戏 API 或 Acbric API |
| API 层 | `src/apiMod` | 运行时核心:`acbric_api` v0.3.3-dev.19（未发布） —— 事件系统、入口桥、原生 MOD 界面集成、战役数据、21 个 hook mixin | 游戏 + fabric-loader |

功能 MOD 不属于本仓库:使用者在自己的项目里编写(可以 `acbric-mod-template/` 为起点),
编译期依赖 API JAR，通常还依赖 `libs/asplit-*.zip`。独立模板使用其本地 API JAR；同工程 source set 才可直接依赖 `apiMod.output`。

**启动主链路**:`KnotClient.main` → ServiceLoader 发现 `AirshipsGameProvider` →
拼好游戏类路径 → Fabric 跑 `preLaunch` 入口 → `AcbricApiPreLaunch` 遍历所有
`acbric` 入口并调用 `AcbricInitializer.onInitializeAcbric`(逐个 try/catch)→
游戏启动 → api 的 mixin 触发事件 → MOD 监听器执行。

## MOD 的写法(约定)

独立模板目录结构:`src/main/java` + `src/main/resources`。资源里的
`fabric.mod.json` 声明:

```json
{
  "entrypoints": { "acbric": ["net.fabricacs.<mod>.SomeMod"] },
  "mixins": ["acbric-<mod>.mixins.json"],
  "depends": { "acbric_api": ">=0.3.3-dev.3" }
}
```

- 入口类实现 `net.fabricacs.api.AcbricInitializer`(在 `onInitializeAcbric` 里注册事件监听)。
- 纯资源 MOD 可以不写入口类，提供 metadata 与 `acbric_vanilla/` 即可；只在确有注入类时声明 `mixins`。
- 想同时带原生数据:在 jar 内放 `acbric_vanilla/` 目录,api 的 `BundledVanillaModLoader`
  会在启动时自动解包到**游戏自己的 MOD 目录** —— `AGame.getGameDirectory()/mods/<modId>/`
  (通常即 `%APPDATA%/AirshipsGame/mods/`),让原生 `Loadable` 系统识别。
  **注意不要解包到 Fabric 的 `game/mods/`**:那里只放 Fabric `.jar` MOD,
  原生 `Mod.refreshMods()` 不扫描它。

## 三种扩展机制

1. **Mixin 直接挂钩**(代码级):`@Mixin` 游戏类,`remap = false`,直接 hook 真实类/方法/字段名。
2. **事件系统**(解耦钩子):MOD 订阅 `Event` 字段
   (`AirshipsLifecycleEvents` / `AirshipsClientEvents` / `AirshipsDataEvents` /
   `AirshipsCombatUiEvents`)。战斗 UI 事件在每帧热路径,api 会先 `listenerCount()==0`
   短路避免分配对象。
3. **JSON 数据扩展**(声明式):给游戏数据 JSON 加字段 + 写 mixin 在正确时机读该字段,
   是改动最小、对游戏升级最稳健的一类扩展。

## Mixin 约束(重要,踩过坑)

此项目用仓库自带 `sponge-mixin 0.17.3+mixin.0.8.7`(编译+runtime 类路径),与标准
Fabric 略有差异:

- **没有 `@Local` 注解**(`org.spongepowered.asm.mixin.injection.callback.Local` 不存在),
  拿不到方法内局部变量。
- **同一调用点只允许一个 `@Redirect`**。同优先级下后处理的会被跳过,日志打
  `@Redirect conflict. Skipping ...`(WARN 而非报错),功能静默失效。加载顺序决定谁赢。
- `@Redirect` handler 的**尾参**可以捕获目标方法的**入参**(在接收者与重定向方法的参数
  之后继续声明目标方法的参数),但拿不到局部变量。需要读取目标方法内部状态时,
  改用 `@Inject` + `@Shadow` 字段。
- `@ModifyArg`/`@ModifyVariable` 若目标节点已被 `@Redirect` 替换,会抛
  `"Variable modifier target ... was removed by another injector"`;而 `@Inject` 在同节点
  仍可存活(所有 injector 先 find 再 apply)。
- **MixinExtras 0.5.4 运行时可用但不在编译类路径**(只在 `game/.fabric/processedMods`)。
  若需多 MOD 在同一调用点共存,`@WrapOperation` 是正路,但需先接入 `libs/` + 编译依赖。
- **冲突高发区**:同一个游戏类被多个 MOD 各自独立 `@Mixin` 时最易出事;游戏里被高频
  hook 的类(例如 `ModuleType`,以及各类 UI 面板)尤其要注意调用点撞车。规划 mixin 时
  先确认目标调用点是否已被别的 MOD 占用。

## 关键文件位置

- `src/main/.../AirshipsGameProvider.java` — Fabric 启动垫片(唯一零 api 依赖代码)。
- `src/main/resources/META-INF/services/net.fabricmc.loader.impl.game.GameProvider` —
  ServiceLoader 注册文件,去掉它启动垫片就不会被发现。
- `src/apiMod/.../api/` — 公开 API(`AcbricInitializer`、`AcbricModContext`、`Event` 系列、`config/` 配置和 `save/` 战役数据)。
- `src/apiMod/.../api/impl/` — `AcbricApiPreLaunch`、`FabricModListBridge`、
  `FabricModInstallBridge`、`BundledVanillaModLoader`、`LifecycleHooks`。
- `src/apiMod/.../api/mixin/` — 21 个 hook mixin(生命周期 6 个、原生 MOD 界面 3 个、战斗 UI 4 个、地图存储 1 个、战役/恢复 2 个、大厅/连接 3 个、生成规则 1 个、选档预检查 1 个)。
- `acbric-mod-template/` — 独立 MOD 模板项目(不含 `libs/`,需 `syncModTemplateLibs` 或手动补齐)。

**不在本仓库、需自备**(见 `README.md`):

- `libs/asplit-A.zip` / `asplit-B.zip` — 游戏 class 文件(编译+runtime 依赖)。
- `libs/*.jar`、`libs/native/` — 游戏自带的库与 native 文件。
- `game/Airships.json` — 原生启动配置(被 GameProvider 解析);`game/data/` — 游戏静态数据;
  `game/mods/` — 已安装的 Fabric MOD jar;`game/config/acbric_mod_manager.json` — MOD 加载配置。

## 陷阱清单

- `remap=false` 意味着 mixin 签名必须**逐字精确**;游戏升级会使 mixin 失效。
- **`@Redirect` 的 target 要按字节码写,不能凭直觉**:方法引用的 owner 是接收者的
  **静态类型**,不是方法的声明类。实测案例——`Shot` 里的字段是 `public Airship target`,
  而 `getX()` 是 `PhysicsRect` 上的 `final` 方法,但 `target.getX()` 编译出来的仍是
  `invokevirtual com/zarkonnen/airships/Airship.getX:()D`(owner = `Airship`)。
  按 `PhysicsRect` 写会得到 `Scanned 0 target(s)` 的注入失败。不确定时用
  `javap -p -c` 反编译 `libs/asplit-*.zip` 里的目标 class 核对。
- dev.12 的 `disabledMods` 在 Provider 定位阶段读取，交由固定 Loader 候选过滤；必须同时更新启动器和 API。Java 启停重启后生效，原生热重载按钮不影响此选择，见 MOD_MANAGEMENT.md。不可实现成只跳过 acbric 入口，否则 Mixin 仍生效。
- 真实入口是 `src/main` 的 GameProvider + ServiceLoader 注册,不是任何 `fabric.mod.json`。
- API 版本号 `acbric_api` = 0.3.3-dev.19（未发布）;使用战役接口的 MOD 在 `fabric.mod.json` 里声明 `">=0.3.3-dev.3"`。

## 最近验证状态

当前标准构建通过 769 项无界面回归。游戏 1.2.15.2 / 1.2.14 的真实 Fabric 探针各通过 10 项新增战役存储检查，包括原生二进制往返和实际 StoredState 恢复。用户已确认 dev.1、dev.2 运行正常，并对 dev.4 测试反馈“暂时没有发现问题”；未提供逐项测试记录，不推断全部联机和恢复场景已验收。详细边界以 CHANGELOG.md 为准；不能据此声称完整战役、双机联机和全部第三方 MOD 兼容。

- dev.4 生命周期见 `CAMPAIGN_LIFECYCLE.md` / 中文版。CREATED 在生成的 setupPlayer 后、首次自动保存前；LOADED 只挂 JSON 战役构造成功出口；RESTORED 不执行初始化/迁移。EXITED 在客户端 input 边界或正常退出时发出，不保证强杀回调。
- 独立工作区示例在相邻 `../acbric-campaign-demo/`，不加入框架 source set 或默认 MOD。用户对 dev.4 反馈暂未发现问题，完整逐场景覆盖仍未确认。

- dev.5 新增 MOD 配置，见 `CONFIG.md` / `CONFIG.zh-CN.md`。构造/load 不写盘，迁移/save/reload 显式执行；损坏文件不自动重置，磁盘冲突不覆盖。本地配置不广播，共享玩法值在创建时固化到战役；独立 v3 示例验证此边界。

- dev.6 范围/诊断契约见 `EVENT_SCOPES.md` / 中文版：仅受管理订阅归属可知；旧注册不自动包裹。关闭释放监听器并屏蔽未开始的旧快照调用，不等待在途回调。应用与战役范围由 MOD 显式分开，异常记录后仍原样抛出。

- dev.7 本地代码清单与离线比较见 `CODE_MANIFEST.md` / 中文版。自动导出在 acbric 入口完成后，使用实际 Loader 根；NO_ACBRIC_ENTRYPOINT 不表示所有初始化成功。CODE_MATCH 不含原生资源、配置、战役状态或身份认证。清单结果不等于完整联机兼容；dev.9 大厅接入见下文。

- dev.8 内部代码握手核心见 `CODE_HANDSHAKE.md` / 中文版，433 项回归含 77 项新增握手检查。两版真实 Server/双 Fabric Client 已验证协议，dev.8 当时适配器仅在探针中；dev.9 产品接入见下文。不要把 CODE_MATCH 当成房间共识或反作弊；32 人/32000 字节清单/40000 字节报文、5 次尝试/10 秒超时/30 秒一致有效期。dev.7/dev.8 已提交为 b82f8cb；当前实际状态以 Git 为准。

- dev.9 已接入战役大厅，见 `LOBBY_HANDSHAKE.md` / 中文版；新增 3 个 Mixin，共 19 个。477 项标准回归、两版共 104 项实际大厅检查通过。LAN 战役可用 channel 0，须按欢迎元数据判断；重连看 socket/连接代次，不能只看 isConnectedRaw。准备绑定全员会话批次，恢复重建大厅需重新握手。原生资源与玩家条件不得绕过。兼容旧功能 MOD 接口不等于支持与旧框架混用联机；完整 GUI/成功开局/官方服务器仍待人工验收。开发提交及工作树状态以 Git 为准。

- dev.10 用户已批准共享规则声明和一致性检查；见 `SHARED_RULES.md` / 中文版。规则只显式声明，不自动接管配置；新战役 WorldGenScreen 构造 RETURN 固化，续局读存档，缺失/版本不符拒绝加载，不补默认或迁移。保留 `acbric_api` 存储命名空间。总计 541 项回归，两版 14 轮/204 项网络探针通过，完整 GUI/生成/跨机器仍待验收。模板最低 API dev.10，但规则示例默认不调用。不要把未声明字段或状态同步说成已覆盖。

- dev.11 用户批准显式旧档接纳/规则迁移和独立测试 MOD。见 `RULE_SAVE_MIGRATION.md` / 中文版。普通加载依旧不迁移，OpenGameMission 文件入口预检查；转换 API 仅支持原生目录存档，快照→显式预览→新目录发布，原档从不改写。缺失值必须明确传入，较旧版本必须有注册转换器。独立 `../acbric-rules-test` 提供互斥 legacy/v1/v2，不能作为框架默认 MOD。597 项标准检查，六进程 136 项原生规则/存档检查、四轮 57 项网络检查；不声称已完整程序生成世界/GUI/跨机器验收。

- dev.12 Java MOD 管理：顶层 JAR 启停、待重启状态、依赖预检查和受管理配套资源屏蔽。`src/shared/java` 是两层共用的无游戏依赖配置协议，不共享可变静态状态。核心/嵌套/外部来源只读；无归属配套资源须先显式迁移，普通原生偏好保留。用户暂缓运行期同步和反作弊；本轮未新增公开 API。详见 MOD_MANAGEMENT.md / 中文版。

- dev.13 公共 UI：见 UI.md / UI.zh-CN.md；context.ui() 注册工具，Ui/UiNode/UiWindow/UiWindowHandle 为公开入口。UiRuntime 虽为 public 但仅用于内部适配。一个根窗口、最多 8 层；关闭释放资源，清理回调不得重开。input 在原生 ScaledInput 之后遮蔽，保留 tick/网络；鼠标左键编号为 1。框架详情页与独立 ../acbric-ui-showcase 共用组件。标准 698 项；两版真实 Fabric 探针覆盖原生 input 和录制绘制器，不代表 OpenGL/全部第三方 MOD 验收。

- UI 中英文为后续开发必需：使用游戏当前语言（原生中文为 `chi`，同时识别 `zh`/`zho`），禁止按系统默认 Locale 或启动期缓存来选择界面语言。框架和示例文案、默认按钮、工具入口及对应文档同步中英文；其他语言回退英文。优先复用 `AcbricLanguage` 与动态入口名称，并测试英文系统+中文游戏、中文系统+英文游戏及切换。

- dev.15 原生绘制约束：MyDraw.in/button 不接受 null。遮蔽输入后必须在 render HEAD、Screen.render 之前恢复绘制坐标；晚到覆盖层绘制才恢复会使详情窗口直接报错回主菜单。关闭释放帧也必须恢复；不应通过取消输入遮蔽来修复。新增真实 render/button 探针在工作区 AcbricUI绘制修复-20260926；绘制替身不能替代这条路径。原生日志在用户数据目录 log.txt。

- dev.16：输入框光标必须用正文 FOUNT 的 textSize，不能用开关宽度 tw。输入诊断位于游戏数据目录的 acbric-ui-input*.log，两份各 64 KiB，不记录文本。间歇点击失效尚未复现，禁止描述为已经解决；诊断是观测，不强行重置输入或焦点。

- dev.17：日志发现点击事件位置与轮询位置约差 119 像素。Slick 输入用经过 ScaledInput 的 cursor 命中，clicked 仅作为触发；自定义 Input 和缺失 cursor 保留事件位置。禁止从 unwrap 后取未缩放坐标。关闭使用已验证有字形的 X。两版坐标回放通过，不宣称已自动复现 Windows 失焦；诊断保留 cursor/event。
