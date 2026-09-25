# AGENTS.md — Acbric

Fabric 风格的 MOD 加载框架,面向策略游戏《Airships: Conquer the Skies》(Java,
`com.zarkonnen.airships` 包,无混淆、无映射命名空间)。框架把 Fabric Loader 的
`KnotClient` 启动器嫁接到该游戏上,让 MOD 能用 Java 代码 + mixin 扩展游戏逻辑,
同时保留并整合游戏原生的数据 MOD 系统。

> **本仓库只含框架本体**：`src/main`(启动层)+ `src/apiMod`(API 层)+
> `acbric-mod-template/`(MOD 模板)。**不含**任何游戏内容(`libs/`、`game/`)、
> **不含**功能 MOD、**不含** `docs/`。缺少游戏文件时无法编译或运行,准备方式见 `README.md`。

## 构建与运行

```powershell
.\gradlew.bat startAirships        # 构建框架并启动游戏(KnotClient)
.\gradlew.bat installApiMod        # 只构建/安装 API jar 到 game/mods/
.\gradlew.bat distZip              # 打分发包(含 jlink 内置 JRE)
```

- 要求 **JDK 21**（使用 `JAVA_HOME` 或 `-Dorg.gradle.java.home` 指定）、Gradle 8.13；JavaCompile 固定 UTF-8 和 Java 21 输出目标。
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
| API 层 | `src/apiMod` | 运行时核心:`acbric_api` v0.3.3-dev.8（未发布） —— 事件系统、入口桥、原生 MOD 界面集成、战役数据、16 个 hook mixin | 游戏 + fabric-loader |

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
- `src/apiMod/.../api/mixin/` — 16 个 hook mixin(生命周期 6 个、原生 MOD 界面 3 个、战斗 UI 4 个、地图存储 1 个、战役/恢复 2 个)。
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
- 当前源码未实现读取 `disabledMods` 配置；合成 Fabric 列表展示已加载 MOD，不代表具备完整禁用／卸载能力。
- 真实入口是 `src/main` 的 GameProvider + ServiceLoader 注册,不是任何 `fabric.mod.json`。
- API 版本号 `acbric_api` = 0.3.3-dev.8（未发布）;使用战役接口的 MOD 在 `fabric.mod.json` 里声明 `">=0.3.3-dev.3"`。

## 最近验证状态

当前标准构建通过 433 项无界面回归。游戏 1.2.15.2 / 1.2.14 的真实 Fabric 探针各通过 10 项新增战役存储检查，包括原生二进制往返和实际 StoredState 恢复。用户已确认 dev.1、dev.2 运行正常，并对 dev.4 测试反馈“暂时没有发现问题”；未提供逐项测试记录，不推断全部联机和恢复场景已验收。详细边界以 CHANGELOG.md 为准；不能据此声称完整战役、双机联机和全部第三方 MOD 兼容。

- dev.4 生命周期见 `CAMPAIGN_LIFECYCLE.md` / 中文版。CREATED 在生成的 setupPlayer 后、首次自动保存前；LOADED 只挂 JSON 战役构造成功出口；RESTORED 不执行初始化/迁移。EXITED 在客户端 input 边界或正常退出时发出，不保证强杀回调。
- 独立工作区示例在相邻 `../acbric-campaign-demo/`，不加入框架 source set 或默认 MOD。用户对 dev.4 反馈暂未发现问题，完整逐场景覆盖仍未确认。

- dev.5 新增 MOD 配置，见 `CONFIG.md` / `CONFIG.zh-CN.md`。构造/load 不写盘，迁移/save/reload 显式执行；损坏文件不自动重置，磁盘冲突不覆盖。本地配置不广播，共享玩法值在创建时固化到战役；独立 v3 示例验证此边界。

- dev.6 范围/诊断契约见 `EVENT_SCOPES.md` / 中文版：仅受管理订阅归属可知；旧注册不自动包裹。关闭释放监听器并屏蔽未开始的旧快照调用，不等待在途回调。应用与战役范围由 MOD 显式分开，异常记录后仍原样抛出。

- dev.7 本地代码清单与离线比较见 `CODE_MANIFEST.md` / 中文版。自动导出在 acbric 入口完成后，使用实际 Loader 根；NO_ACBRIC_ENTRYPOINT 不表示所有初始化成功。CODE_MATCH 不含原生资源、配置、战役状态或身份认证。尚未接入大厅/网络；禁止把此结果描述为完整联机兼容。

- dev.8 内部代码握手核心见 `CODE_HANDSHAKE.md` / 中文版，433 项回归含 77 项新增握手检查。两版真实 Server/双 Fabric Client 已验证协议，适配器仅在工作区探针中；产品尚无大厅/准备/开局接入。不要把 CODE_MATCH 当成房间共识或反作弊；32 人/32000 字节清单/40000 字节报文、5 次尝试/10 秒超时/30 秒一致有效期。本阶段包含 dev.7 与 dev.8 实现；提交状态以 Git 为准。
