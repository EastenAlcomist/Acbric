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

- 要求 **JDK 21**(`gradle.properties` 已 pin `C:/Program Files/Java/jdk-21`)、Gradle 8.13。
- **前置游戏文件不在仓库内**,须从自有的 Airships 安装目录复制:
  - `libs/` — `asplit-A.zip` / `asplit-B.zip`(游戏 class)、`fabric-loader-0.19.3.jar`、
    游戏自带的库 jar、`libs/native/`
  - `game/` — `Airships.json`、`launch_settings.json`、`data/` 等
  
  缺失时 `compileApiModJava` 会因找不到 `libs/asplit-*.zip` 失败,`startAirships` 也会因
  GameProvider 定位不到 `game/Airships.json` 而无法启动。
- 无测试 source set;验证靠 `startAirships` 启动后看日志。
- 新增一个 MOD 通常做成一个 Gradle source set,约定任务名形如 `<name>ModJar` /
  `install<Name>Mod`,并在 `runAirshipsFabric` / `distDir` 的 `dependsOn` 中登记。

## 两层架构(本仓库范围)

| 层 | 位置 | 职责 | 依赖 |
|---|---|---|---|
| 启动层 | `src/main` | `AirshipsGameProvider` 把游戏塞进 Fabric(解析 `game/Airships.json`、拼类路径、反射调 `Main.main`) | **零依赖**,不碰 api |
| API 层 | `src/apiMod` | 运行时核心:`acbric_api` v0.3.2 —— 事件系统、入口桥、原生 MOD 界面集成、13 个 hook mixin | 游戏 + fabric-loader |

功能 MOD 不属于本仓库:使用者在自己的项目里编写(可以 `acbric-mod-template/` 为起点),
编译期依赖 `apiMod.output`,通常还依赖 `libs/asplit-*.zip`。

**启动主链路**:`KnotClient.main` → ServiceLoader 发现 `AirshipsGameProvider` →
拼好游戏类路径 → Fabric 跑 `preLaunch` 入口 → `AcbricApiPreLaunch` 遍历所有
`acbric` 入口并调用 `AcbricInitializer.onInitializeAcbric`(逐个 try/catch)→
游戏启动 → api 的 mixin 触发事件 → MOD 监听器执行。

## MOD 的写法(约定)

每个 MOD 目录结构:`src/<modName>/java` + `src/<modName>/resources`。资源里的
`fabric.mod.json` 声明:

```json
{
  "entrypoints": { "acbric": ["net.fabricacs.<mod>.SomeMod"] },
  "mixins": ["acbric-<mod>.mixins.json"],
  "depends": { "acbric_api": ">=0.3.2" }
}
```

- 入口类实现 `net.fabricacs.api.AcbricInitializer`(在 `onInitializeAcbric` 里注册事件监听)。
- 纯数据 MOD 可以不写入口类,只声明 `mixins` / `acbric_vanilla/` 即可。
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
- `src/apiMod/.../api/` — 公开 API(`AcbricInitializer`、`AcbricModContext`、`Event` 系列)。
- `src/apiMod/.../api/impl/` — `AcbricApiPreLaunch`、`FabricModListBridge`、
  `FabricModInstallBridge`、`BundledVanillaModLoader`、`LifecycleHooks`。
- `src/apiMod/.../api/mixin/` — 13 个 hook mixin(生命周期 6 个、原生 MOD 界面 3 个、战斗 UI 4 个)。
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
- `game/config/acbric_mod_manager.json` 的 `disabledMods` **只影响游戏内 MOD 管理,
  拦不住 mixin 加载**——禁用某 MOD 后,它与其他 MOD 的 mixin 冲突依然存在。
- 真实入口是 `src/main` 的 GameProvider + ServiceLoader 注册,不是任何 `fabric.mod.json`。
- API 版本号 `acbric_api` = 0.3.2;MOD 的 `fabric.mod.json` 里 `depends` 写 `">=0.3.2"`。
