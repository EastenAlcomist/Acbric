# Acbric — Airships Fabric Mod Framework · 飞艇 Fabric MOD 框架

A lightweight Fabric-style mod loading framework for *Airships: Conquer the Skies*.
面向《Airships: Conquer the Skies》的轻量级 Fabric 风格 MOD 加载框架。

The framework grafts Fabric Loader's `KnotClient` launcher onto the game so that mods can
extend game logic with Java code + mixins, while keeping and integrating the game's native
JSON data-mod system.
框架把 Fabric Loader 的 `KnotClient` 启动器嫁接到游戏上，让 MOD 能用 Java 代码 + mixin
扩展游戏逻辑，同时保留并整合游戏原生的数据 MOD 系统。

---

## ⚠️ This repository does not contain any game content

This repository ships **the framework only**. Two things are intentionally absent:

| Absent · 不含 | Why · 原因 |
|---|---|
| `libs/` | The game's compiled classes (`asplit-A.zip` / `asplit-B.zip`) and the library jars the game ships with. Redistributing them is a copyright violation. · 游戏 class 与随游戏分发的库，转载涉及版权 |
| `game/` | The game's static data (`data/`, `ships/`, `images/` …), about 1.3 GB. · 游戏静态数据，约 1.3 GB |
| Feature mods · 功能 MOD | Not part of the framework. · 不属于框架本体 |

The framework **will not compile or run** until you supply them. See below.

---

## 1. Prepare the game files · 准备游戏文件

Copy these from your own legitimately owned Airships installation into this project,
keeping the same relative paths:
请从你合法拥有的 Airships 安装目录，按相同相对路径复制到本项目：

### `libs/` — compile + runtime dependencies

```
libs/
├── asplit-A.zip            # game classes   · 游戏 class（编译 + 运行依赖）
├── asplit-B.zip            # game classes   · 游戏 class（编译 + 运行依赖）
├── fabric-loader-0.19.3.jar    # Fabric Loader 0.19.3（从 Maven / Fabric 官网获取）
├── <the game's library jars>   # 随游戏分发的库
└── native/                     # native libraries · 本地库
```

`libs/` must end up holding the jars/zips the game itself launches with (typically
`CatEngine.jar`, `CatSlick.jar`, `slick.jar`, `lwjgl.jar`, `lwjgl_util.jar`, `ibxm.jar`,
`jinput.jar`, `jogg-*.jar`, `jorbis-*.jar`, `commons-*.jar`, `joda-time-*.jar`,
`steamworks4j-*.jar`, `FloatIO.jar`) plus the game's `asplit-*.zip` class archives.
`libs/` 需包含游戏自身启动所用的 jar/zip（见左列）以及游戏的 `asplit-*.zip` class 包。

> `fabric-loader-0.19.3.jar` comes from Fabric, not from the game — download it and drop it
> in `libs/`. The build currently references it as a local file.
> `fabric-loader-0.19.3.jar` 来自 Fabric 而非游戏，请自行下载后放入 `libs/`。

### `game/` — the game's own directory

Copy the game directory that contains `Airships.json` (the file the launch shim parses):

```
game/
├── Airships.json               # required · 启动层需要解析它
├── launch_settings.json        # optional but recommended · 建议保留
├── data/                       # required · 游戏数据
├── lib/ , native libs, ...
└── mods/                       # installed mods land here · MOD 安装目录
```

`game/Airships.json` is the file `AirshipsGameProvider` reads to discover `mainClass` and
`classPath`; without it the launcher cannot locate the game.
`game/Airships.json` 是启动垫片用来发现 `mainClass` 与 `classPath` 的文件，缺失则无法定位游戏。

---

## 2. Requirements · 环境要求

- **JDK 21** (`gradle.properties` pins `C:/Program Files/Java/jdk-21` — edit it for your machine)
- Gradle 8.13 via the bundled wrapper · 通过仓库自带 wrapper 使用 Gradle 8.13

## 3. Build & Run · 构建与运行

```powershell
# Build the framework and start the game · 构建框架并启动游戏
.\gradlew.bat startAirships --console=plain

# Build/install just the API jar · 只构建安装 API jar
.\gradlew.bat installApiMod --console=plain

# Build a distribution package (bundled JRE via jlink) · 打包分发包
.\gradlew.bat distZip --console=plain
```

## 4. Project layout · 项目结构

```
Acbric/
├── src/
│   ├── main/            # Launch shim: AirshipsGameProvider + GameProvider service entry
│   └── apiMod/          # Runtime API (acbric_api): events, entrypoint bridge,
│                        # native-mod UI integration, hook mixins
├── acbric-mod-template/ # Standalone mod project template · 独立 MOD 模板项目
├── gradle/              # Gradle wrapper
└── build.gradle
```

### Three layers · 三层架构

| Layer | Location | Responsibility |
|---|---|---|
| Launch · 启动层 | `src/main` | `AirshipsGameProvider` pushes the game into Fabric: parses `game/Airships.json`, assembles the classpath, reflectively calls `Main.main`. Zero dependencies, no API coupling. |
| API · API 层 | `src/apiMod` | `acbric_api` — event system, entrypoint bridge, native-mod UI integration, 13 hook mixins. |
| Feature · 功能层 | *your mods* | Feature mods depend on `apiMod.output` (and usually `libs/asplit-*.zip`). |

**Launch chain · 启动主链路**: `KnotClient.main` → ServiceLoader discovers
`AirshipsGameProvider` → classpath assembled → Fabric runs `preLaunch` →
`AcbricApiPreLaunch` walks every `acbric` entrypoint and calls
`AcbricInitializer.onInitializeAcbric` (individually try/catch) → game starts →
API mixins fire events → mod listeners run.

## 5. Writing a mod · 写一个 MOD

Copy `acbric-mod-template/` as your starting point. A mod is a directory with
`src/main/java` + `src/main/resources`; `fabric.mod.json` declares:

```json
{
  "schemaVersion": 1,
  "id": "your_mod_id",
  "version": "0.1.0",
  "environment": "client",
  "entrypoints": { "acbric": ["net.example.your_mod.YourMod"] },
  "mixins": ["your-mod.mixins.json"],
  "depends": { "acbric_api": ">=0.3.2" }
}
```

The entrypoint class implements `net.fabricacs.api.AcbricInitializer`:

```java
public class YourMod implements AcbricInitializer {
    @Override public void onInitializeAcbric(AcbricModContext ctx) {
        AirshipsLifecycleEvents.CLIENT_CREATED.register(game -> { /* ... */ });
    }
    @Override public void onInitializeAcbric() {}
}
```

Pure-data mods need no entrypoint class. To bundle native data, put an
`acbric_vanilla/` directory inside your jar — the API extracts it at startup into the
game's own mods directory so the native `Loadable` system picks it up.

### Three extension mechanisms · 三种扩展机制

1. **Mixins**（代码级）— `@Mixin` the game class with `remap = false`, hooking real
   class/method/field names.
2. **Event system**（解耦钩子）— subscribe to `Event` fields on
   `AirshipsLifecycleEvents` / `AirshipsClientEvents` / `AirshipsDataEvents` /
   `AirshipsCombatUiEvents`. Combat UI events are on the per-frame hot path; the API
   short-circuits on `listenerCount() == 0` to avoid allocations.
3. **JSON data extension**（声明式）— add a field to a game data JSON and write a mixin
   that reads it at the right moment.

## 6. Mixin constraints · Mixin 约束（重要）

This project uses the repository-local `sponge-mixin 0.17.3+mixin.0.8.7` (compile + runtime
classpath), which differs slightly from stock Fabric:

- **No `@Local` annotation** — you cannot capture method-local variables.
- **Only one `@Redirect` per call site.** A second one at the same priority is skipped with
  a `@Redirect conflict. Skipping ...` WARN (not an error), silently disabling the feature.
- A `@Redirect` handler's **trailing parameters** can capture the target method's
  **input arguments** (not its locals).
- If a node has already been replaced by a `@Redirect`, `@ModifyArg` / `@ModifyVariable`
  throw `"Variable modifier target ... was removed by another injector"`, while `@Inject`
  on the same node still survives.
- **MixinExtras 0.5.4 is available at runtime but not on the compile classpath** (only in
  `game/.fabric/processedMods`). Use `@WrapOperation` when several mods must coexist at one
  call site, but you must add it to `libs/` and the compile classpath first.
- `remap = false` means mixin signatures must match **character for character**; a game
  update will break them.

## 7. License / credits · 许可

The framework code in this repository. The game *Airships: Conquer the Skies* and all game
assets are the property of their respective owners and are **not** included here.
本仓库为本框架代码；游戏《Airships: Conquer the Skies》及其全部资源归其所有者所有，本仓库不包含。
