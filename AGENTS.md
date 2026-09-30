# AGENTS.md — Acbric

## 当前入口与状态

正式仓库为 `04-开发源码/Acbric`。工具默认工作目录可能仍是 Steam 游戏目录，执行命令必须显式指定本仓库。开始先核对 Git 分支和本地改动，保留用户已有工作。

- 当前源码/API：**0.3.4**，分支与提交以 Git 为准。
- 2026-09-27：用户确认 dev.33 实机测试没有问题，并要求将启动器优化及此前文档整理提交至本地 dev；本轮未要求推送。
- 2026-09-27 dev.33：玩家入口改为 Acbric.exe，首次查找/检查/保存合并，首页只保留启动、MOD、设置、帮助。技术细节默认隐藏；日志本地保留、诊断导出需主动选择。旧 CMD 在 advanced；旧实例脚本保持字节兼容。更新交给临时助手等待 JVM 退出；不要破坏使用锁或恢复事务。
- 本轮验证：1040 项/16 套件通过；真实 EXE 首次配置、再次打开英文首页、实际游戏菜单 30 帧/音频和返回首页通过；原游戏 5325 文件未改。旧 dev.32→dev.33→回退逐文件核对通过，原生维护交接等待与中断恢复重开通过。证据 build/player-*.log、build/player-launcher-tests/dev33final（最终包 SHA-256: 6786e3aeb17d971652100a1a0b0701aac1e3d26469ef5e9c47eaf7e1aba73403）、build/player-maintenance-tests/dev33。界面驱动使用仅测试的类加载器夹具；精简 Java 无 java.instrument，不把测试夹具打进包。
- 2026-09-27：用户要求整理项目目录。46 份专题文档移入 `docs/`，根 README 改为当前导航，旧 README 和本指引的逐轮记录完整保留为历史快照。源码、游戏数据、缓存与验证产物不搬迁、不清空。
- 文档入口：[中文索引](docs/README.zh-CN.md)、[English index](docs/README.md)、[构建与测试](docs/BUILDING.zh-CN.md)、[开发流程](docs/DEVELOPMENT.zh-CN.md)、[玩家安装](docs/INSTALLER.zh-CN.md)。
- 本次整理验证：1011 项回归通过；仓库 466 个、发行目录 426 个本地 Markdown 文件链接通过（不含锚点/外网）；干净发行扫描通过；正式旧包→新 docs 布局→回退的隔离测试逐项核对发行文件与玩家标记。日志在 `build/docs-cleanup-*`，更新证据在 `build/docs-layout-update-v87sw39g`。整理阶段未提交/推送，未改游戏行为或玩家数据。
- 先前验证：16 套件 1011 项；Java MOD 空列表有两进程 26 项真实绘制检查；安装重试有 Windows 真实文件句柄实验。用户确认 dev.32 在原失败机器安装成功，尚不能认定具体占用进程。
- 完整旧记录：[代理历史](docs/AGENT_HISTORY.md)、[历史 README](docs/LEGACY_README.zh-CN.md)、[变更记录](docs/CHANGELOG.zh-CN.md)。这些历史状态不覆盖本页当前规则。

## 持续约束

- 保持既有公开 API、旧事件语义和 MOD 兼容；有破坏性取舍时先讨论。
- 新 UI、用户说明与 API 文档同时维护中文和英文；其他游戏语言回退英文。原生中文语言码是 `chi`，也识别 `zh` / `zho`；不按系统 Locale 缓存语言。
- 所有自有 Java 文件使用中文职责注释。源码/文档使用 UTF-8；JSON、Gradle wrapper 和第三方许可证不加自有注释。
- 游戏代码/资源、`libs/`、`game/`、构建包和私有运行记录不得加入 Git；不修改玩家副本或 Steam 安装。运行测试使用隔离实例。
- 干净发行用 `externalDistZip`，文件名自动读取 API 版本；旧 `distZip` 含本地游戏内容。发布保留中英文简明流程和完整 docs。源码目录移位必须同步构建/模板/链接。
- 不自动导入旧布局用户数据；不覆盖修改过的归属文件或损坏配置。保存、更新与回退保留锁、备份、原子发布和错误诊断。
- 存储写入不自动广播；代码一致不等于状态同步/反作弊。完整战役、联机、GPU/输入法等需要针对性实机验证，不扩大自动化检查结论。
- 原生 GL 重复报错已由用户决定暂缓，不屏蔽或伪装成功；框架通用 API 与独立 ARC Overhaul 玩法 MOD 分开维护。
- 修改构建前读双语 BUILDING；具名回归仅注册到 FrameworkRegression.SUITES，先定向、收尾全量。验证路径以实际存在的工具为准：历史 BUILDING 提到的 `Ac source/tools/acbric.cmd` 在当前工作区不存在，不假称执行；涉及 Mixin 时保留字节码目标检查与真实 Knot 注入验证。
- 无用户要求时不自动提交、推送或清理构建证据。专题契约写入 docs，开发过程记录进入 CHANGELOG/历史记录，避免再次堆到根 README。

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
**`docs/BUILDING.zh-CN.md`**（[English](docs/BUILDING.md)）—— 加新脚本或改构建行为前必读。

日常测试同理，别每次都跑全量：

```powershell
test all             # 全部套件 1040 项断言（收尾前必跑）
test event           # 只跑 event（改 Event.java 时）
test ui              # 只跑 ui（改 UiRuntime / 输入遮蔽时）
test devtools        # 只跑 devtools（改控制台 / 命令绑定时）
test settings        # 别名也行（settings -> config）
test list            # 列出套件与覆盖范围
./test.sh event      # Linux / macOS / Git Bash
```

套件 16 个、共 1040 项断言：原有 `data` `bundle` `classpath` `event` `rename` `mods`
（对应 CHANGELOG 的 F01–F12），加上 dev 功能线的 `campaign` `config` `scopes`
`manifest` `handshake` `rules` `identity` `ui` `devtools` 和外部安装 `external`；每个套件的覆盖范围与断言数见
`docs/BUILDING.zh-CN.md` 的套件表（实测值，加完套件要更新）。选择支持唯一前缀（`test ev`）
与别名；未知或歧义会直接报错。
**`check` 仍然依赖 `regressionTest`，不带选择参数时跑全部套件，所以 CI 行为没变。**

其余 Gradle 任务不变：

```powershell
.\gradlew.bat startAirships        # 构建框架并启动游戏(KnotClient)
.\gradlew.bat installApiMod        # 只构建/安装 API jar 到 game/mods/
.\gradlew.bat distZip              # 旧布局研究包，含游戏内容，不对外分享
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
- `build.cmd` 只编译；`build.cmd full` / `gradlew build` 才包含无界面检查；`regressionTest` 隔离用户数据，符号链接权限不足时会报告跳过。仍需单独验收真实游戏玩法。
- 分发的 `loader-libs/` 仅进入启动类路径，`libs/` 保存游戏依赖。Provider 会按归档内容排除 Loader/Mixin/ASM/启动垫片，兼容旧的混合库目录；不要把这些类再次加入游戏加载器。
- UI 事件契约与迁移见 `docs/EVENTS.md`：旧 `ONE_SHOT_*` 保留 RenameShipPanel 触发和标签，禁止悄悄重定向；新功能使用 `RENAME_SHIP_*`。新事件排在旧事件之后，任一 BEFORE 取消则跳过后续组、原方法及所有 AFTER。
- 完整 API 手册见 `docs/API.md`（英文）和 `docs/API.zh-CN.md`（中文），累计中文变更见 `docs/CHANGELOG.zh-CN.md`；修改公开接口时同步中英文手册、示例与依赖版本。
- 用户已批准首批通用接口：按 MOD ID 隔离的战役 JSON 存储，见 `docs/CAMPAIGN_DATA.md` / `docs/CAMPAIGN_DATA.zh-CN.md`。用户进一步批准战役生命周期事件及独立存档示例；城市升级等玩法属于独立 ARC Overhaul，框架通用接口按当前任务范围确定。构建身份/会话诊断见 `docs/DIAGNOSTICS.md` / `docs/DIAGNOSTICS.zh-CN.md`，构建诊断文件格式不是公开 API；dev.20 诊断 records 的公开范围以 docs/DEVELOPER_TOOLS.md 为准。
- 战役数据必须保留缺失 MOD 的命名空间；声明的块损坏或格式不支持时中止加载，不回退默认值。迁移显式执行且只处理副本；保存/校验不得执行 MOD 迁移回调。写入不会广播，勿把接入原生状态恢复描述成自动网络同步。
- 所有自有 Java 源码使用 UTF-8 中文文件头，说明职责；复杂流程注释说明约束和原因，避免逐行复述。JSON 不添加注释，自动生成的 Gradle wrapper 与第三方许可证保持原样。
- 当前行为变更见 `docs/CHANGELOG.md`；资源更新、冲突保护和显式迁移见 `docs/BUNDLED_RESOURCES.md`。不要清空 Loadable 诊断或把失败结果改成成功。
- 资源更新只能改写哈希仍匹配的已归属文件；保留用户修改和旧无归属目录。不要绕过备份、锁和恢复日志直接覆盖目录。
- `registerOnce` 使用独立订阅和原子触发状态；已消费的代理通过空监听器 invoker 返回中性结果。普通事件异常仍向调用方传播，不在本轮改变策略。
- 功能 MOD 优先从独立模板建立项目；不要默认把功能 MOD 的 source set 或发行产物塞进框架本体。

## 两层架构(本仓库范围)

| 层 | 位置 | 职责 | 依赖 |
|---|---|---|---|
| 启动层 | `src/main` | `AirshipsGameProvider` 把游戏塞进 Fabric(解析 `game/Airships.json`、拼类路径、反射调 `Main.main`) | Fabric Loader；不依赖游戏 API 或 Acbric API |
| API 层 | `src/apiMod` | 运行时核心:`acbric_api` v0.3.4（未发布） —— 事件系统、入口桥、原生 MOD 界面集成、战役数据、游戏适配 Mixin | 游戏 + fabric-loader |

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
- `src/apiMod/.../api/mixin/` — 游戏适配 Mixin(生命周期 6 个、原生 MOD 界面 3 个、战斗 UI 4 个、地图存储 1 个、战役/恢复 2 个、大厅/连接 3 个、生成规则 1 个、选档预检查 1 个)。
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
- dev.12 的 `disabledMods` 在 Provider 定位阶段读取，交由固定 Loader 候选过滤；必须同时更新启动器和 API。Java 启停重启后生效，原生热重载按钮不影响此选择，见 docs/MOD_MANAGEMENT.md。不可实现成只跳过 acbric 入口，否则 Mixin 仍生效。
- 真实入口是 `src/main` 的 GameProvider + ServiceLoader 注册,不是任何 `fabric.mod.json`。
- API 版本号 `acbric_api` = 0.3.4（未发布）;使用战役接口的 MOD 在 `fabric.mod.json` 里声明 `">=0.3.3-dev.3"`。
