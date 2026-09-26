# Acbric API 开发手册

从 dev.21 起，按 **Esc 下方、数字 1 左侧的 ~ 键**（物理键 `GRAVE`，可带 Shift）直接打开控制台并聚焦命令框，开窗按键不会混入文本。Ctrl/Alt/Meta 组合、窗口失焦、原生错误/帮助/聊天覆盖层以及已有 Acbric 窗口时不触发；先关闭其他 Acbric 窗口再使用快捷键。控制台打开后该键仍可输入普通字符，用 Esc/X/关闭退出。长按不会反复开窗。这是固定控制台入口，尚未提供通用改键 API。

[English](API.md) | **中文**

当前文档导航：[开发流程](DEVELOPMENT.zh-CN.md)、[命令 API](COMMANDS.zh-CN.md)、[开发者工具与诊断](DEVELOPER_TOOLS.zh-CN.md)。`context.commands()` 自 dev.20 提供，支持类型参数、帮助/补全和可注销注册；旧公开接口保持兼容。

适用版本：**`acbric_api 0.3.3-dev.22`**，开发版本，尚未发布稳定版。本文以本仓库源码为准；旧 0.3.2 二进制不含 `RENAME_SHIP_*`，战役数据接口要求 dev.3 或更新版本。

- 安装、编译和启动：[README.zh-CN.md](README.zh-CN.md)。
- 本次兼容性调整：[CHANGELOG.zh-CN.md](CHANGELOG.zh-CN.md)。
- UI 事件的英文契约：[EVENTS.md](EVENTS.md)。
- 内嵌原版资源、冲突和迁移：[BUNDLED_RESOURCES.md](BUNDLED_RESOURCES.md)。
- 游戏构建身份与启动报告：[DIAGNOSTICS.zh-CN.md](DIAGNOSTICS.zh-CN.md)。
- 战役数据、迁移与联机边界：[CAMPAIGN_DATA.zh-CN.md](CAMPAIGN_DATA.zh-CN.md)。

- [MOD 配置：默认值、校验、迁移、备份和显式重载](CONFIG.zh-CN.md)，要求 dev.5。

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
    "acbric_api": ">=0.3.3-dev.3",
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
| `commands()` | 当前 MOD 的命令注册入口，dev.20；见 [命令契约](COMMANDS.zh-CN.md) |
| `sharedRules(int version, JSONObject values, SharedRules.Validator validator)` | 注册本 MOD 的共享玩法规则（dev.10）；见 [规则契约](SHARED_RULES.zh-CN.md) |
| `campaignData(Object worldMap)` | 绑定当前 MOD 和指定地图的 `CampaignData`（dev.3 新增） |
| `config(name, dataVersion, defaults, validator)` | 创建配置句柄；显式读写/迁移/重载（dev.5），见 [CONFIG](CONFIG.zh-CN.md) |
| `eventScope(name)` | 创建独立的受管理订阅范围（dev.6），见 [契约](EVENT_SCOPES.zh-CN.md) |

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

公开扩展入口是 `net.fabricacs.api` 及其 `event`、`util`、`save` 包。`impl` 和 `mixin` 是框架内部实现，即使有 public 方法也不等于稳定的第三方扩展协议；不要直接调用 `LifecycleHooks.fire*` 模拟游戏行为。

需要 API 尚未覆盖的能力时，可以在自己的 MOD 声明 Mixin，并对照实际游戏字节码确定签名、调用 owner 和注入位置。当前无映射，使用 `remap=false`。自定义 Mixin 不会因为公开 API 兼容就自动获得跨游戏版本兼容。

Fabric MOD 安装界面会暂存 JAR、校验 `fabric.mod.json` 的 schema/ID/版本/结构并拒绝同名覆盖；不预检全部依赖、嵌套内容或 Mixin 执行，安装后需重启。dev.12 的 MOD 列表同时展示已加载与扫描目录中的已停用 JAR，可选择下次启动启停，见 [MOD 管理](MOD_MANAGEMENT.zh-CN.md)。必须同时更新启动器和 API；不热卸载、不删除归档。

从 `0.3.3-dev.2` 开始，GameProvider 在依赖解析前从字节码读取 `AGame.VERSION`，把可识别版本报告给 Fabric；无法取得或识别时告警并将规范版本回退到 `0.0.0`。精确依赖原占位值 `0.0.0` 的 MOD 现在可能无法通过依赖检查。有序游戏归档哈希用于区分相同版本号的不同构建，不代表兼容性保证。本地会话报告区分 Loader 已加载与 `acbric` 入口初始化成功；详见[诊断与兼容说明](DIAGNOSTICS.zh-CN.md)，报告没有引入新的公开 MOD API。

## 10. 验证范围

当前标准构建通过 597 项无界面回归（含 62 项配置检查）。游戏 1.2.15.2 / 1.2.14 真实 Fabric 探针各通过 10 项战役存储和 31 项生命周期/v3.1 示例和 5 项受管理事件检查，配置策略部分使用显式事件派发；不能替代 GUI 与真实联机验收。用户对 dev.4 反馈暂未发现问题，dev.10 仍待完整人工验收。

## 11. 战役数据

取得实际地图后使用 `context.campaignData(campaignWorld.map)`。`net.fabricacs.api.save` 提供 `read`、`write`、`remove` 和显式 `migrate`；读取返回独立 `CampaignDataSnapshot`，迁移失败保留原数据。数据按 MOD ID 隔离，通过原版存档管线保存，并随地图恢复，包含暂时没有对应 MOD 的命名空间。序列化不调用 MOD 回调。

写入改变本地共享游戏状态，**不会广播**；需要各端通过确定性模拟或同步指令一致执行。句柄绑定一个地图实例，地图替换后重新获取。支持的值、完整契约、格式处理及存储/恢复边界见[战役数据手册](CAMPAIGN_DATA.zh-CN.md)。

## 12. 战役生命周期

从 dev.4 起，`AirshipsCampaignEvents` 提供 `CREATED`、`LOADED`、`RESTORED`、`EXITED`。创建通知早于初次自动保存，加载通知在扩展数据读回后；恢复回调仅重新绑定本地句柄，不修改共享状态。参数、时机及边界见[生命周期契约](CAMPAIGN_LIFECYCLE.zh-CN.md)。旧公开成员继续保留。

## 13. MOD 配置（dev.5）

使用 `context.config(...)`，再显式 `load/migrate/save/reload`。默认值只补缺失字段，失败保留旧快照；磁盘更新有备份和冲突检查。本地偏好可重载，共享玩法值应固化到战役，详见 [配置接口手册](CONFIG.zh-CN.md)。

## 14. 运行期诊断与订阅范围（dev.6）

`context.eventScope(name)` 提供 register/registerOnce/close。受管理回调记录 MOD、事件和异常；旧注册语义保持。见 [完整契约](EVENT_SCOPES.zh-CN.md)。新增 81 项范围与 27 项诊断检查；真实加载检查详见本轮变更记录，GUI/联机仍待人工验收。

## 15. 本地代码清单（dev.7）

[导出与离线比较](CODE_MANIFEST.zh-CN.md) 属于内部诊断工具，不是新增公开 MOD API。597 项标准回归包含 45 项新增清单检查。CODE_MATCH 仅表示启动代码身份一致，dev.9 已接入大厅，dev.10 新增显式声明规则检查，仍不提供自动状态同步；内部握手核心见下节。

## 16. 内部代码握手（dev.8）

见[协议与状态契约](CODE_HANDSHAKE.zh-CN.md)。这是包内框架基础设施，本轮有 77 项新增回归，不是公开 MOD 网络接口。两套游戏快照的真实 Server/Client 探针已调用验证；dev.9 在战役大厅自动运行。核心结果仍是本机证据，不直接代表开局许可。

## 17. 战役大厅接入（dev.9）

见[玩家行为及接入契约](LOBBY_HANDSHAKE.zh-CN.md)。代码检查失败阻断准备/开局，上下文变化撤销准备。所有玩家需使用相同代码及支持该检查的框架；保留旧 MOD 接口不等于支持新旧框架混用联机。本轮没有新增公开网络 API，也没有自动同步配置或状态。

## 18. 共享玩法规则（dev.10）

通过 `context.sharedRules` 声明，由 `SharedRules` 管理候选值，`forCampaign(world.map)` 读取已固化值。见 [完整契约与示例](SHARED_RULES.zh-CN.md)。校验器无副作用；规则缺失或版本不符的旧档不能静默补值。大厅全员一致后才可准备，准备后修改撤销旧凭据，不覆盖本地配置。本轮新增 `rules` 公开包及 1 个 Mixin，共 20 个。

## 19. 存档规则预检查与转换（dev.11）

`SharedRules.migration` 注册旧版本到当前版本的显式转换；`CampaignRuleSaves.inspect` 返回报告，`prepare` 生成前后值预览，`writeNew` 只发布到不存在的新目录。普通加载不迁移、不补默认。详见 [完整契约](RULE_SAVE_MIGRATION.zh-CN.md)。新增 `OpenGameMissionMixin`，共 21 个 Mixin；结构错误、原档变化和目标冲突明确拒绝。

## 20. 公共 UI 组件（dev.13）

`context.ui()` 注册 MOD 工具入口并打开组件窗口。`Ui` 构建标签、按钮、开关、单行文本、行列布局、面板和滚动区域；`UiWindowHandle` 管理子弹窗和托管资源。完整签名、线程与清理契约、示例及限制见 [UI.zh-CN.md](UI.zh-CN.md)。模态窗口不暂停模拟和网络 tick。框架 [MOD 详情页](MOD_MANAGEMENT.zh-CN.md) 使用同一套组件。

本地化：dev.14 新增 `AcbricLanguage.text(英文, 中文)` 与 `ModUi.register(id, Supplier<String>, factory)`，见 [UI.zh-CN.md](UI.zh-CN.md)。
