# AGENTS.md — Acbric

Fabric 风格的 MOD 加载框架,面向策略游戏《Airships: Conquer the Skies》(Java,
`com.zarkonnen.airships` 包,无混淆、无映射命名空间)。框架把 Fabric Loader 的
`KnotClient` 启动器嫁接到该游戏上,让 MOD 能用 Java 代码 + mixin 扩展游戏逻辑,
同时保留并整合游戏原生的数据 MOD 系统。

## 构建与运行

```powershell
.\gradlew.bat startAirships        # 构建全部 MOD 并启动游戏(KnotClient)
.\gradlew.bat installSampleMod     # 只构建/安装单个 MOD 到 game/mods/
.\gradlew.bat distZip              # 打分发包(含 jlink 内置 JRE)
```

- 要求 **JDK 21**(`gradle.properties` 已 pin `C:/Program Files/Java/jdk-21`)、Gradle 8.13。
- 每个 MOD 是一个 Gradle source set,任务名形如 `<name>ModJar` / `install<Name>Mod`
  (如 `heroEffectModJar` / `installHeroEffectMod`)。
- 无测试 source set(`src/test` 为空);验证靠 `startAirships` 启动后看日志。

## 三层架构

| 层 | 位置 | 职责 | 依赖 |
|---|---|---|---|
| 启动层 | `src/main` | `AirshipsGameProvider` 把游戏塞进 Fabric(解析 `game/Airships.json`、拼类路径、反射调 `Main.main`) | **零依赖**,不碰 api |
| API 层 | `src/apiMod` | 运行时核心:`acbric_api` v0.3.2 —— 事件系统、入口桥、原生 MOD 界面集成、13 个 hook mixin | 游戏 + fabric-loader |
| 功能层 | `src/*Mod` | 22 个功能 MOD | 编译期依赖 `apiMod.output`,多数还依赖 `libs/asplit-*.zip` |

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
- 纯数据 MOD 可以不写入口类(如 `crewHiResMod`、`moduleBuildLimitMod`、`turretRotationMod`)。
- 想同时带原生数据:在 jar 内放 `acbric_vanilla/` 目录,api 的 `BundledVanillaModLoader`
  会在启动时自动解包到 `game/mods/<modId>/`,让原生 `Loadable` 系统识别。

## 三种扩展机制

1. **Mixin 直接挂钩**(代码级):`@Mixin` 游戏类,`remap = false`,直接 hook 真实类/方法/字段名。
2. **事件系统**(解耦钩子):MOD 订阅 `Event` 字段
   (`AirshipsLifecycleEvents` / `AirshipsClientEvents` / `AirshipsDataEvents` /
   `AirshipsCombatUiEvents`)。战斗 UI 事件在每帧热路径,api 会先 `listenerCount()==0`
   短路避免分配对象。
3. **JSON 数据扩展**(声明式):给游戏数据 JSON 加字段 + 写 mixin 在正确时机读该字段。
   大部分功能(`docs/need_for_p.md` 的 7 项需求)走这条路。

## Mixin 约束(重要,踩过坑)

此项目用仓库自带 `sponge-mixin 0.17.3+mixin.0.8.7`(编译+runtime 类路径),与标准
Fabric 略有差异:

- **没有 `@Local` 注解**(`org.spongepowered.asm.mixin.injection.callback.Local` 不存在),
  拿不到方法内局部变量。
- **同一调用点只允许一个 `@Redirect`**。同优先级下后处理的会被跳过,日志打
  `@Redirect conflict. Skipping ...`(WARN 而非报错),功能静默失效。加载顺序决定谁赢。
- `@Redirect` handler 的**尾参**可以捕获目标方法的**入参**(见
  `CityDetailMixin.acbric_cu$drawUpgradeButtonNextToRemove`),但拿不到局部变量。
- `@ModifyArg`/`@ModifyVariable` 若目标节点已被 `@Redirect` 替换,会抛
  `"Variable modifier target ... was removed by another injector"`;而 `@Inject` 在同节点
  仍可存活(所有 injector 先 find 再 apply)。
- **MixinExtras 0.5.4 运行时可用但不在编译类路径**(只在 `game/.fabric/processedMods`)。
  若需多 MOD 在同一调用点共存,`@WrapOperation` 是正路,但需先接入 `libs/` + 编译依赖。
- 冲突高发区:`ModuleType` 被 8 个 MOD 各自独立 `@Mixin`(airAssist、ammoSystem、
  buildLimit、deployKey、moduleHiRes、shield、shotIntercept、turretRotation);
  `CityDetail` 被 cityUpgrade 与 heroEffect 同时 hook。

## 关键文件位置

- `src/main/.../AirshipsGameProvider.java` — Fabric 启动垫片(唯一零 api 依赖代码)。
- `src/apiMod/.../api/` — 公开 API(`AcbricInitializer`、`AcbricModContext`、`Event` 系列)。
- `src/apiMod/.../api/impl/` — `AcbricApiPreLaunch`、`FabricModListBridge`、
  `FabricModInstallBridge`、`BundledVanillaModLoader`、`LifecycleHooks`。
- `libs/asplit-A.zip` / `asplit-B.zip` — 游戏 class 文件(编译+runtime 依赖)。
- `game/mods/` — 已安装 MOD jar;`game/config/acbric_mod_manager.json` — MOD 加载配置;
  `game/Airships.json` — 原生启动配置(被 GameProvider 解析)。
- `docs/` — 每个 MOD 的英文/中文文档;`docs/need_for_p.md` — 7 项需求实现记录。
- `vanillamod/` — 5 个纯数据原生 MOD 示例;`acbric-mod-template/` — 独立 MOD 模板项目。

## 陷阱清单

- `remap=false` 意味着 mixin 签名必须**逐字精确**;游戏升级会使 mixin 失效。
- `game/config/acbric_mod_manager.json` 的 `disabledMods` **只影响游戏内 MOD 管理,
  拦不住 mixin 加载**——禁用某 MOD 后,它与其他 MOD 的 mixin 冲突依然存在。
- 根目录 `fabric.mod.json` 是残留的 deploy-key 副本(id 为 `acbric_deploy_key`),
  不是项目真实入口;真实入口是 `src/main` 的 GameProvider + ServiceLoader 注册。
- API 版本号 `acbric_api` = 0.3.2;各 MOD `depends` 写 `">=0.3.2"`。
