# Acbric API 开发手册

适用版本：**`acbric_api 0.3.3-dev.1`**，开发版本，尚未发布稳定版。本文以本仓库源码为准；旧 0.3.2 二进制不包含新增的 `RENAME_SHIP_*` 字段。

- 安装、编译和启动：[README.zh-CN.md](README.zh-CN.md)。
- 本次兼容性调整：[CHANGELOG.zh-CN.md](CHANGELOG.zh-CN.md)。
- UI 事件的英文契约：[EVENTS.md](EVENTS.md)。
- 内嵌原版资源、冲突和迁移：[BUNDLED_RESOURCES.md](BUNDLED_RESOURCES.md)。

## 1. 开发准备与入口

使用 JDK 21、匹配游戏版本的 `asplit-A.zip` / `asplit-B.zip` 和本版 API JAR。框架 API 输出 Java 21 字节码；模板暂保留 Java 17 源码/目标设置，但读取 API、构建及运行仍需要 JDK 21。游戏版本不由 API 版本号保证，Mixin 必须与实际游戏方法描述符匹配。

从仓库模板创建 MOD：

```powershell
# 在已补齐依赖的框架仓库执行
.\gradlew.bat build syncModTemplateLibs

# 复制完整 acbric-mod-template（包括本地 libs）作为自己的工程后执行
.\gradlew.bat build
.\gradlew.bat installMod -PgameDir='C:/Games/Acbric/game'
```

`installMod` 将 JAR 复制到指定游戏的 `mods/`；重新启动游戏后生效。模板的 `libs/` 仅用于编译，不打进功能 MOD 的 JAR。修改 `gradle.properties` 中的 MOD ID、名称和版本，并同步修改包名、入口路径及图标路径。不要把游戏依赖或用户数据提交到源码仓库。

最小入口实现：

```java
package example;

import net.fabricacs.api.AcbricInitializer;
import net.fabricacs.api.AcbricModContext;
import net.fabricacs.api.event.AirshipsDataEvents;

public final class ExampleMod implements AcbricInitializer {
    @Override
    public void onInitializeAcbric(AcbricModContext context) {
        context.ensureConfigDir();
        context.logger().info("MOD 已初始化");
        AirshipsDataEvents.DATA_LOADED.register(successful -> {
            if (!successful) {
                context.logger().warn("原版数据加载失败，请检查游戏日志");
                return;
            }
            // 在这里访问完成加载的数据；加载可发生多次。
        });
    }

    @Override
    public void onInitializeAcbric() {
        // 接口保留的旧入口；使用带上下文的方法时也需要实现。
    }
}
```

对应 `src/main/resources/fabric.mod.json`：

```json
{
  "schemaVersion": 1,
  "id": "example_mod",
  "version": "0.1.0",
  "name": "Example Mod",
  "environment": "client",
  "entrypoints": { "acbric": ["example.ExampleMod"] },
  "depends": {
    "java": ">=21",
    "fabricloader": ">=0.19.3",
    "acbric_api": ">=0.3.3-dev.1",
    "airships": "*"
  }
}
```

`AcbricEntrypoints.INIT` 的值就是 `acbric`。框架在 Fabric `preLaunch` 中先准备内嵌原版资源，再调用每个入口的 `onInitializeAcbric(context)`。默认实现转调无参方法，旧 MOD 仍可只实现无参入口。入口异常会分别记录；这不代表初始化发生的部分修改会回滚。

预启动时游戏数据和 UI 未必已创建，应先注册对应生命周期事件。当前没有 Acbric 停止入口或自动清理 MOD 状态的卸载流程。

## 2. MOD 上下文和日志

`AcbricModContext` 包装当前 `ModContainer`；通常使用框架传入的实例。

| 方法 | 返回/用途 |
| --- | --- |
| `container()` / `metadata()` | Fabric `ModContainer` / `ModMetadata` |
| `modId()` | metadata 的 MOD ID |
| `modName()` | 显示名称；为空时回退到 ID |
| `version()` | Fabric 版本的可读字符串 |
| `description()` | 描述；未提供时为空字符串 |
| `iconPath(int size)` | `Optional<String>`，包内图标路径 |
| `configDir()` / `ensureConfigDir()` | 获取 / 创建本 MOD 的配置目录 |
| `dataDir()` / `ensureDataDir()` | 获取 / 创建本 MOD 的数据目录 |
| `logger()` | 带当前 MOD ID 的 `AcbricLogger` |

`AcbricLogger` 也可通过 `new AcbricLogger(modId)` 创建。支持 `info(String)`、`warn(String)`、`error(String)`、`error(String, Throwable)`。日志格式为 `[Acbric/<modId>/<level>] ...`；INFO 输出到 stdout，WARN/ERROR 输出到 stderr，Throwable 追加堆栈。它不负责独立日志文件的轮转。

## 3. 路径：Fabric 目录与原版用户数据

`net.fabricacs.api.util.AirshipsPaths` 的静态方法基于 Fabric 游戏目录：

| 方法 | 默认布局中的路径 |
| --- | --- |
| `gameDir()` | `<运行包>/game` |
| `configDir()` | Fabric 配置根目录，通常为 `game/config` |
| `modsDir()` | `game/mods`，Java/Fabric JAR |
| `staticDataDir()` | `game/data`，游戏静态数据 |
| `generatedDir()` | `game/generated` |
| `cacheDir()` | `game/.fabric/acbric/cache` |
| `modConfigDir(modId)` | `<configDir>/<modId>` |
| `modDataDir(modId)` | `game/data/acbric/<modId>` |

对应创建方法为 `ensureConfigDir()`、`ensureModsDir()`、`ensureGeneratedDir()`、`ensureCacheDir()`、`ensureModConfigDir(modId)`、`ensureModDataDir(modId)`。`ensureDirectory(Path)` 可创建指定目录；I/O 失败抛出 `UncheckedIOException`。普通路径 getter 不创建目录，传入 ID 的路径方法也不负责校验任意外部输入。

**原版 MOD 和存档另由游戏 `AGame.getGameDirectory()` 定位**，通常是用户数据目录，亦可由 `launch_settings.json` 的 `customDataDirectoryLocation` 指定。原版目录 MOD 应放在其 `mods/<目录>/` 下，而不是 Fabric 的 `game/mods/`。`context.dataDir()` 不等于存档目录，把文件写入那里也不会自动让游戏加载它。

## 4. 事件订阅与注销

公开事件字段类型为 `Event<监听器接口>`。在入口注册，在回调内执行功能：

```java
EventHandle handle = AirshipsClientEvents.CLIENT_TICK_START.registerWithHandle(game -> {
    // 每次 AirshipGame.input 入口执行；不要在这里做阻塞 I/O。
});
// 功能不再需要时，仅注销自己的订阅。
handle.unregister();
```

上述类型分别来自 `net.fabricacs.api.event.EventHandle` 和 `AirshipsClientEvents`。

| `Event<T>` 方法 | 语义 |
| --- | --- |
| `register(T)` | 追加订阅，允许重复注册相同对象，返回 void |
| `registerWithHandle(T)` | 追加订阅，返回只控制本次注册的句柄 |
| `registerOnce(T)` | 返回句柄，监听器最多执行一次；要求监听器由接口实现 |
| `unregister(T)` | 移除首个 `equals` 匹配的订阅，返回是否移除成功 |
| `clearListeners()` | 清除该事件的全部订阅，MOD 不应借此清掉其他 MOD 的监听 |
| `listenerCount()` | 当前注册数 |
| `listeners()` | 独立列表快照；修改列表不会更改注册表 |
| `invoker()` | 当前分发快照；一般由框架调用，MOD 不应随意手动触发游戏事件 |

`EventHandle.unregister()` 可重复调用。相同监听器注册两次会收到两次通知，各句柄只注销各自那次。`registerOnce` 内部注册的是代理，应保存返回句柄注销，而不是依赖 `unregister(原监听器)`。

订阅变更时重建快照，分发按注册顺序执行。普通监听器从注册表移除后，已经取得的旧快照仍可能执行它；一次性监听器另有共享原子状态，递归、并发或旧快照重复调用也只允许一次真实回调。一次性监听器在执行前就被注销，即使抛异常也不会重试。

`registerOnce(DATA_LOADED 的监听器)` 等的是第一次完成，不是第一次成功：失败的 `false` 也会消费订阅。需要等待成功时，使用普通订阅并在成功后通过句柄注销。

事件同步运行在调用线程，不会自动调度到渲染线程或后台线程。注册表同步不等于游戏对象或监听器本身可并发访问。普通事件回调不吞异常：抛出异常会中止本次后续监听器并向调用方传播。

自定义事件可使用 `new Event<>(InvokerFactory<T>)`。工厂接收监听器列表、返回实现同一接口的分发器；空列表也必须合法，并返回该事件的中性值，例如 `PASS`。已消费的一次性代理借此返回中性结果。建议使用单方法监听器接口，并由自己的分发器明确定义返回值与异常策略。

## 5. 生命周期、客户端与数据事件

| 事件类 / 字段 | 监听器方法 | 实际触发点 |
| --- | --- | --- |
| `AirshipsLifecycleEvents.GAME_STARTING` | `onGameStarting(String[] args)` | `Main.main` HEAD |
| `AirshipsLifecycleEvents.CLIENT_CREATED` | `onClientCreated(Object airshipGame)` | `AirshipGame` 构造 RETURN |
| `AirshipsLifecycleEvents.LOADING_SCREEN_CREATED` | `onLoadingScreenCreated(Object loadingScreen)` | 两个 `LoadingScreen` 构造重载 RETURN |
| `AirshipsLifecycleEvents.MAIN_MENU_CREATED` | `onMainMenuCreated(Object mainMenu)` | `MainMenu` 构造 RETURN |
| `AirshipsClientEvents.CLIENT_TICK_START` | `onClientTickStart(Object airshipGame)` | `AirshipGame.input` HEAD |
| `AirshipsClientEvents.CLIENT_TICK_END` | `onClientTickEnd(Object airshipGame)` | `AirshipGame.input` RETURN |
| `AirshipsDataEvents.DATA_LOAD_STARTING` | `onDataLoadStarting()` | `Loadable.load` HEAD |
| `AirshipsDataEvents.DATA_LOADED` | `onDataLoaded(boolean successful)` | `Loadable.load` RETURN，保留真实结果 |

这些监听器返回 void，不能取消原方法。RETURN 指正常返回，不是 `finally`：异常退出不保证发生对应通知。构造方法链可能触发多个构造返回钩子；界面再次创建也会再次通知，不能把这些事件一概视作每次进程只触发一次。

`GAME_STARTING` 每次分发只复制一次参数数组，该次监听器共享这个副本，修改不会直接改变原方法的数组。`CLIENT_TICK_*` 对应输入方法调用，不承诺固定频率或模拟时间步长。`DATA_LOADED(false)` 不会清除原版日志，也不会把数据错误修正为成功。

## 6. UI 事件与上下文

所有字段位于 `AirshipsCombatUiEvents`。以下为推荐接口：

| 字段 | 游戏类/方法 | 监听器接口 |
| --- | --- | --- |
| `SHIP_STATUS_BAR_BEFORE_DRAW` | `ShipStatusChrome.draw` HEAD | `ShipStatusBarBeforeDraw` |
| `SHIP_STATUS_BAR_AFTER_DRAW` | `ShipStatusChrome.draw` RETURN | `ShipStatusBarAfterDraw` |
| `PLAYER_CONTROL_PANEL_BEFORE_DRAW` / `AFTER_DRAW` | `CommandButtonsPanel.draw`、`DirectControlPanel.draw` | `PlayerControlPanelBeforeDraw` / `PlayerControlPanelAfterDraw` |
| `PLAYER_CONTROL_PANEL_BEFORE_TICK` / `AFTER_TICK` | 上述两面板的 `tick` | `PlayerControlPanelBeforeTick` / `PlayerControlPanelAfterTick` |
| `RENAME_SHIP_BEFORE_DRAW` / `AFTER_DRAW` | `RenameShipPanel.draw` | 复用 `PlayerControlPanelBeforeDraw` / `PlayerControlPanelAfterDraw` |
| `RENAME_SHIP_BEFORE_TICK` / `AFTER_TICK` | `RenameShipPanel.tick` | 复用 `PlayerControlPanelBeforeTick` / `PlayerControlPanelAfterTick` |

接口均为 `AirshipsCombatUiEvents` 的嵌套接口。状态条方法名为 `beforeShipStatusBarDraw` / `afterShipStatusBarDraw`；面板方法名为 `beforePlayerControlPanelDraw` / `afterPlayerControlPanelDraw`、`beforePlayerControlPanelTick` / `afterPlayerControlPanelTick`。使用 lambda 时不需手写这些方法名。

BEFORE 返回 `EventResult.PASS` 或 `CANCEL`；`shouldCancel()` 仅在 CANCEL 时为 true。首个 CANCEL 停止后续监听器，跳过对应原方法及 AFTER。AFTER 返回 void，不可取消。原方法正常提前返回仍触发 AFTER。异常继续传播。第三方 Mixin 修改同一方法时，需要额外检查执行顺序。

| 上下文 | getter 及真实含义 |
| --- | --- |
| `ShipStatusBarContext` | `statusBar()`：ShipStatusChrome；`draw()`：MyDraw；`mouse()`：Pt；`airship()`：Airship；`side()`：Combat.Side；`x()/y()/width()/height()`：int 位置及尺寸；`screenMode()`：ScreenMode；`screen()`：UniScreen |
| `PlayerControlPanelDrawContext` | `panelType()`：CombatUiPanelType；`panel()`：对应面板；`draw()`：MyDraw；`mouse()`：Pt；`screenMode()`：ScreenMode；`hooks()`：Hooks；`screen()`：UniScreen |
| `PlayerControlPanelTickContext` | `panelType()`、`panel()`；`input()`：Input；`elapsedMs()`：int 毫秒参数；`screen()`：UniScreen |

游戏类型位于 `com.zarkonnen.airships`，Input/Hooks 位于 `com.zarkonnen.catengine`，Pt/ScreenMode 位于 `com.zarkonnen.catengine.util`。除枚举与整数外，getter 的公开返回类型为 `Object`。需要访问具体成员时按匹配的游戏类型转换。三个上下文类均有依次接收表中字段的公开构造器；一般无需自行构造，框架会传入真实参数引用，不复制底层可变对象，也不额外保证参数非空。

`CombatUiPanelType` 的推荐值为 `COMMAND_BUTTONS`、`DIRECT_CONTROL`、`RENAME_SHIP`。通用 `PLAYER_CONTROL_PANEL_*` 不包括重命名面板。UI 事件是方法调用事件，没有活动对话框时也可能发生；`RENAME_SHIP_AFTER_TICK` 不表示用户已确认改名。

```java
AirshipsCombatUiEvents.RENAME_SHIP_BEFORE_TICK.register(context -> {
    // 若需要阻止该次面板输入处理，可返回 CANCEL；通常应保留默认行为。
    return EventResult.PASS;
});
```

## 7. 旧 ONE_SHOT 接口的兼容与迁移

以下三组各有 `BEFORE_DRAW`、`AFTER_DRAW`、`BEFORE_TICK`、`AFTER_TICK` 四个字段，共 12 个：

- `ONE_SHOT_WEAPONS_*`
- `ONE_SHOT_BUOYANCY_*`
- `ONE_SHOT_POWER_*`

历史实现都挂在 **RenameShipPanel**，不是一次性武器、浮力或动力动作。这些字段及三个同名枚举标签标记弃用，但不删除。事件对象仍相互独立，不是新字段的别名，也不向新事件转发。

同一次 draw/tick 的顺序为：旧 WEAPONS BEFORE → 旧 BUOYANCY BEFORE → 旧 POWER BEFORE → 新 RENAME_SHIP BEFORE → 原方法 → 三组旧 AFTER（同序）→ 新 AFTER。任一 BEFORE 取消就停止后续组、原方法及所有 AFTER；任一回调异常也阻止其后的执行。

扩展重命名面板的旧 MOD 可改订阅对应 `RENAME_SHIP_*`，并更新 `panelType()` 判断。不要为同一段工作同时保留新旧订阅，避免执行两次。真正的武器/浮力/动力动作需要另外设计并验证钩子，本版没有这些动作事件。

## 8. 内嵌原版资源

把原版 MOD 内容放在 JAR 的 `acbric_vanilla/` 中：

```text
example-mod.jar
├── fabric.mod.json
├── example/ExampleMod.class
└── acbric_vanilla/
    ├── info.json
    └── <原版 MOD 数据及资源>
```

首次启动解包到 `<AGame用户数据>/mods/<Fabric MOD ID>/`，缺少 `info.json` 时生成最小元数据。顶层 JAR/ZIP 来源受支持；嵌套 JAR 和展开的目录来源暂不处理。资源型 MOD 可省略 Java 入口，不需要凭空声明一个 Mixin。

资源更新依据 `.acbric-bundle.json` 中的 SHA-256 归属记录：未改动的受管理文件可更新/移除；用户修改、删除、未归属碰撞保留并报告。没有归属记录的旧目录不自动接管，损坏记录会停止该次更新。原目录备份保存在 `<用户数据>/.acbric-bundles/<id>/backups/`；事务日志用于恢复进程中断，不代表硬件故障下的完整持久化保证。

关闭游戏后可用 `BundledVanillaModMigration` 显式迁移旧目录：它先保留完整备份，只接管与指定包内容一致的文件。不同或缺失的文件记录为冲突，不自动替换。命令、冲突解决与恢复方式见 [资源管理说明](BUNDLED_RESOURCES.md)。移除 Java MOD 不会自动删除其已解包资源。

## 9. Mixin、实现层与当前限制

公开扩展入口是 `net.fabricacs.api`、`event`、`util`。`impl` 和 `mixin` 是框架内部实现，即使有 public 方法也不等于稳定的第三方扩展协议；不要直接调用 `LifecycleHooks.fire*` 模拟游戏行为。

需要 API 尚未覆盖的能力时，可以在自己的 MOD 声明 Mixin，并对照实际游戏字节码确定签名、调用 owner 和注入位置。当前无映射，使用 `remap=false`。自定义 Mixin 不会因为公开 API 兼容就自动获得跨游戏版本兼容。

Fabric MOD 安装界面会暂存 JAR、校验 `fabric.mod.json` 的 schema/ID/版本/结构并拒绝同名覆盖；不预检全部依赖、嵌套内容或 Mixin 执行，安装后需重启。合成 MOD 列表只展示已加载内容，`disabledMods` 尚未实现，不能用它保证禁用 MOD；停用时关闭游戏并把 JAR 移出扫描目录。

GameProvider 当前报告的游戏版本是占位值 `0.0.0`（原始值 `unknown`）。不能借此精确约束真实游戏版本，应自行记录并验证所用游戏构建。

## 10. 验证范围

本版完成 80 项无界面回归、公开 API 成员/描述符兼容对照，以及两套本地游戏输入的部分真实 Fabric/Mixin 加载检查。独立运行包经本地人工测试，反馈运行正常、表现与原包基本一致；未记录完整测试矩阵或 MOD 清单，因此不扩大为全部 GUI、存档、联机和第三方 MOD 的兼容承诺。
