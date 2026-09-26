# Acbric API Developer Guide

Since dev.21, press the **backtick / tilde key** below Esc and left of 1 (physical `GRAVE`; Shift is optional) to open the console with the command field focused. The opening key is consumed. Ctrl/Alt/Meta combinations, inactive displays, native error/help/chat overlays, and existing Acbric windows do not trigger it. Close another Acbric window before using the shortcut. While the console is open this key remains ordinary text; use Esc/X/Close to dismiss. Holding the key cannot repeatedly reopen it. This is a fixed console shortcut, not a general key-binding API.

**English** | [中文](API.zh-CN.md)

Current navigation: [development workflow](DEVELOPMENT.md), [command API](COMMANDS.md), [developer tools and diagnostics](DEVELOPER_TOOLS.md). `context.commands()` is available since dev.20, with typed arguments, help/completion and closeable registrations; existing public APIs remain compatible.

Applies to **`acbric_api 0.3.3-dev.23`**, an unpublished development version, not a stable release. This guide follows the source in this repository; older 0.3.2 binaries lack `RENAME_SHIP_*`, and campaign data requires dev.3 or newer.

- Installation, building and launching: [README.md](README.md).
- Compatibility changes in this revision: [CHANGELOG.md](CHANGELOG.md).
- UI event contract: [EVENTS.md](EVENTS.md).
- Bundled native resources, conflicts and migration: [BUNDLED_RESOURCES.md](BUNDLED_RESOURCES.md).
- Game build identity and startup reports: [DIAGNOSTICS.md](DIAGNOSTICS.md).
- Campaign persistence, migration and multiplayer boundaries: [CAMPAIGN_DATA.md](CAMPAIGN_DATA.md).

- [MOD configuration: defaults, validation, migration, backups and explicit reload](CONFIG.md), requires dev.5.

## 1. Development setup and entrypoints

Use JDK 21, `asplit-A.zip` / `asplit-B.zip` matching your game build, and this version of the API JAR. The framework API produces Java 21 bytecode. The template currently retains Java 17 source/target settings, but consuming the API, building and running still require JDK 21. The API version does not guarantee compatibility with a particular game version: mixins must match the actual game method descriptors.

Create a mod from the repository template:

```powershell
# Run in the framework repository after supplying its dependencies
.\gradlew.bat build syncModTemplateLibs

# Copy the entire acbric-mod-template (including its local libs) to your own project,
# then run these commands there
.\gradlew.bat build
.\gradlew.bat installMod -PgameDir='C:/Games/Acbric/game'
```

`installMod` copies the JAR into the specified game's `mods/` directory; restart the game for it to take effect. The template's `libs/` directory is used only for compilation and is not bundled into the feature mod's JAR. Change the mod ID, name and version in `gradle.properties`, and update the package name, entrypoint path and icon path accordingly. Do not commit game dependencies or user data to your source repository.

A minimal entrypoint:

```java
package example;

import net.fabricacs.api.AcbricInitializer;
import net.fabricacs.api.AcbricModContext;
import net.fabricacs.api.event.AirshipsDataEvents;

public final class ExampleMod implements AcbricInitializer {
    @Override
    public void onInitializeAcbric(AcbricModContext context) {
        context.ensureConfigDir();
        context.logger().info("Mod initialized");
        AirshipsDataEvents.DATA_LOADED.register(successful -> {
            if (!successful) {
                context.logger().warn("Native data loading failed; check the game log");
                return;
            }
            // Access loaded data here; loading may happen more than once.
        });
    }

    @Override
    public void onInitializeAcbric() {
        // Legacy entrypoint retained by the interface; implement it even when
        // using the context-aware method.
    }
}
```

Corresponding `src/main/resources/fabric.mod.json`:

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

`AcbricEntrypoints.INIT` has the value `acbric`. During Fabric `preLaunch`, the framework prepares bundled native resources, then calls each entrypoint's `onInitializeAcbric(context)`. The default implementation delegates to the no-argument method, so older mods may continue to implement only that method. Entrypoint exceptions are logged individually; this does not roll back partial changes made during initialization.

Game data and UI objects may not yet exist during pre-launch. Register the appropriate lifecycle listeners first. There is currently no Acbric shutdown entrypoint or unload process that automatically cleans up mod state.

## 2. Mod context and logging

`AcbricModContext` wraps the current `ModContainer`. Normally, use the instance supplied by the framework.

| Method | Return value / purpose |
| --- | --- |
| `container()` / `metadata()` | Fabric `ModContainer` / `ModMetadata` |
| `modId()` | Mod ID from the metadata |
| `modName()` | Display name, falling back to the ID when empty |
| `version()` | Readable string for the Fabric version |
| `description()` | Description, or an empty string when absent |
| `iconPath(int size)` | `Optional<String>` containing an icon path inside the mod package |
| `configDir()` / `ensureConfigDir()` | Get / create this mod's configuration directory |
| `dataDir()` / `ensureDataDir()` | Get / create this mod's data directory |
| `logger()` | `AcbricLogger` tagged with this mod's ID |
| `commands()` | Owner-scoped command registration since dev.20; see [command contract](COMMANDS.md). |
| `sharedRules(int version, JSONObject values, SharedRules.Validator validator)` | Declare gameplay rules for this MOD (dev.10); see [contract](SHARED_RULES.md) |
| `campaignData(Object worldMap)` | `CampaignData` scoped to this mod and the given map (since dev.3) |
| `config(name, dataVersion, defaults, validator)` | Create a configuration handle; explicit I/O, migration and reload (dev.5), see [CONFIG](CONFIG.md) |
| `eventScope(name)` | Create an independent managed subscription scope (dev.6), see [contracts](EVENT_SCOPES.md) |

You can also construct a logger with `new AcbricLogger(modId)`. It supports `info(String)`, `warn(String)`, `error(String)` and `error(String, Throwable)`. Messages use the format `[Acbric/<modId>/<level>] ...`. INFO goes to stdout; WARN and ERROR go to stderr. A supplied Throwable adds its stack trace. The logger does not manage rotation of separate log files.

## 3. Paths: Fabric directories and native user data

The static methods in `net.fabricacs.api.util.AirshipsPaths` use the Fabric game directory:

| Method | Path in the default layout |
| --- | --- |
| `gameDir()` | `<runtime package>/game` |
| `configDir()` | Fabric configuration root, normally `game/config` |
| `modsDir()` | `game/mods`, for Java/Fabric JARs |
| `staticDataDir()` | `game/data`, for static game data |
| `generatedDir()` | `game/generated` |
| `cacheDir()` | `game/.fabric/acbric/cache` |
| `modConfigDir(modId)` | `<configDir>/<modId>` |
| `modDataDir(modId)` | `game/data/acbric/<modId>` |

The corresponding creation methods are `ensureConfigDir()`, `ensureModsDir()`, `ensureGeneratedDir()`, `ensureCacheDir()`, `ensureModConfigDir(modId)` and `ensureModDataDir(modId)`. `ensureDirectory(Path)` creates a specified directory; I/O failures throw `UncheckedIOException`. Plain path getters do not create directories. Path methods accepting an ID do not validate arbitrary external input.

**Native mods and saves are located separately through the game's `AGame.getGameDirectory()`**. This normally points to the user data directory and can be configured with `customDataDirectoryLocation` in `launch_settings.json`. Native directory mods belong under its `mods/<directory>/`, not Fabric's `game/mods/`. `context.dataDir()` is not the save directory, and writing files there does not make the game load them automatically.

## 4. Event subscription and removal

Public event fields have the type `Event<ListenerInterface>`. Register from your entrypoint and perform the work in the callback:

```java
EventHandle handle = AirshipsClientEvents.CLIENT_TICK_START.registerWithHandle(game -> {
    // Runs on entry to each AirshipGame.input call; avoid blocking I/O here.
});
// When the feature is no longer needed, remove only your own subscription.
handle.unregister();
```

The types above are `net.fabricacs.api.event.EventHandle` and `net.fabricacs.api.event.AirshipsClientEvents`.

| `Event<T>` method | Semantics |
| --- | --- |
| `register(T)` | Append a subscription; duplicate registrations of the same object are allowed; returns void |
| `registerWithHandle(T)` | Append a subscription and return a handle controlling only this registration |
| `registerOnce(T)` | Return a handle for a listener that runs at most once; the listener must implement an interface |
| `unregister(T)` | Remove the first subscription matching via `equals`; return whether one was removed |
| `clearListeners()` | Remove every subscription to this event; mods should not use this to clear other mods' listeners |
| `listenerCount()` | Current number of registrations |
| `listeners()` | Independent list snapshot; editing it does not change the registry |
| `invoker()` | Current dispatch snapshot; normally called by the framework, not for mods to trigger game events manually |

`EventHandle.unregister()` is safe to call repeatedly. Registering the same listener twice produces two notifications, and each handle removes only its own registration. `registerOnce` registers a proxy internally: retain the returned handle to remove it instead of relying on `unregister(originalListener)`.

Subscription changes rebuild the snapshot, and dispatch follows registration order. After an ordinary listener is removed from the registry, a previously obtained snapshot may still invoke it. Once-listeners additionally share atomic state: recursive calls, concurrent calls and repeated invocation through old snapshots still allow at most one actual callback. A once-listener is unregistered before its callback runs, so an exception does not cause a retry.

Using `registerOnce` with `DATA_LOADED` waits for the first completion, not the first success: a `false` result also consumes the subscription. To wait for success, use an ordinary subscription and unregister through its handle after success.

Events execute synchronously on the calling thread; they do not automatically switch to a render or background thread. Synchronizing the registry does not make game objects or listeners safe for concurrent access. Ordinary event callbacks do not swallow exceptions: an exception stops the remaining listeners in that dispatch and propagates to the caller.

For custom events, use `new Event<>(InvokerFactory<T>)`. The factory receives a listener list and returns a dispatcher implementing the same interface. An empty list must also be valid and produce the event's neutral result, such as `PASS`; consumed once-listener proxies use this result. Prefer a listener interface with a single method, and define return-value and exception policies explicitly in your dispatcher.

## 5. Lifecycle, client and data events

| Event class / field | Listener method | Actual trigger |
| --- | --- | --- |
| `AirshipsLifecycleEvents.GAME_STARTING` | `onGameStarting(String[] args)` | `Main.main` HEAD |
| `AirshipsLifecycleEvents.CLIENT_CREATED` | `onClientCreated(Object airshipGame)` | `AirshipGame` constructor RETURN |
| `AirshipsLifecycleEvents.LOADING_SCREEN_CREATED` | `onLoadingScreenCreated(Object loadingScreen)` | RETURN of both `LoadingScreen` constructor overloads |
| `AirshipsLifecycleEvents.MAIN_MENU_CREATED` | `onMainMenuCreated(Object mainMenu)` | `MainMenu` constructor RETURN |
| `AirshipsClientEvents.CLIENT_TICK_START` | `onClientTickStart(Object airshipGame)` | `AirshipGame.input` HEAD |
| `AirshipsClientEvents.CLIENT_TICK_END` | `onClientTickEnd(Object airshipGame)` | `AirshipGame.input` RETURN |
| `AirshipsDataEvents.DATA_LOAD_STARTING` | `onDataLoadStarting()` | `Loadable.load` HEAD |
| `AirshipsDataEvents.DATA_LOADED` | `onDataLoaded(boolean successful)` | `Loadable.load` RETURN, preserving the actual result |

These listeners return void and cannot cancel the original method. RETURN means normal return, not `finally`: an exceptional exit does not guarantee a corresponding notification. Constructor chaining may trigger multiple constructor-return hooks. Recreating a screen also triggers another notification, so these events should not all be treated as once-per-process events.

`GAME_STARTING` copies the argument array once per dispatch. Listeners in that dispatch share the copy; modifying it does not directly change the original method's array. `CLIENT_TICK_*` corresponds to calls to the input method and promises neither a fixed frequency nor a simulation timestep. `DATA_LOADED(false)` neither clears native diagnostics nor converts data errors into success.

## 6. UI events and contexts

All fields are in `AirshipsCombatUiEvents`. The recommended interfaces are:

| Field | Game class / method | Listener interface |
| --- | --- | --- |
| `SHIP_STATUS_BAR_BEFORE_DRAW` | `ShipStatusChrome.draw` HEAD | `ShipStatusBarBeforeDraw` |
| `SHIP_STATUS_BAR_AFTER_DRAW` | `ShipStatusChrome.draw` RETURN | `ShipStatusBarAfterDraw` |
| `PLAYER_CONTROL_PANEL_BEFORE_DRAW` / `AFTER_DRAW` | `CommandButtonsPanel.draw`, `DirectControlPanel.draw` | `PlayerControlPanelBeforeDraw` / `PlayerControlPanelAfterDraw` |
| `PLAYER_CONTROL_PANEL_BEFORE_TICK` / `AFTER_TICK` | `tick` on those two panels | `PlayerControlPanelBeforeTick` / `PlayerControlPanelAfterTick` |
| `RENAME_SHIP_BEFORE_DRAW` / `AFTER_DRAW` | `RenameShipPanel.draw` | Reuses `PlayerControlPanelBeforeDraw` / `PlayerControlPanelAfterDraw` |
| `RENAME_SHIP_BEFORE_TICK` / `AFTER_TICK` | `RenameShipPanel.tick` | Reuses `PlayerControlPanelBeforeTick` / `PlayerControlPanelAfterTick` |

All listener interfaces are nested in `AirshipsCombatUiEvents`. Status-bar methods are named `beforeShipStatusBarDraw` / `afterShipStatusBarDraw`; panel methods are `beforePlayerControlPanelDraw` / `afterPlayerControlPanelDraw` and `beforePlayerControlPanelTick` / `afterPlayerControlPanelTick`. Lambdas do not require you to write these method names explicitly.

BEFORE listeners return `EventResult.PASS` or `CANCEL`; `shouldCancel()` is true only for CANCEL. The first CANCEL stops subsequent listeners and skips both the original method and AFTER. AFTER listeners return void and cannot cancel. A normal early return from the original method still triggers AFTER. Exceptions propagate. If third-party mixins modify the same method, check the resulting execution order separately.

| Context | Getters and actual values |
| --- | --- |
| `ShipStatusBarContext` | `statusBar()`: ShipStatusChrome; `draw()`: MyDraw; `mouse()`: Pt; `airship()`: Airship; `side()`: Combat.Side; `x()/y()/width()/height()`: int position and dimensions; `screenMode()`: ScreenMode; `screen()`: UniScreen |
| `PlayerControlPanelDrawContext` | `panelType()`: CombatUiPanelType; `panel()`: the corresponding panel; `draw()`: MyDraw; `mouse()`: Pt; `screenMode()`: ScreenMode; `hooks()`: Hooks; `screen()`: UniScreen |
| `PlayerControlPanelTickContext` | `panelType()`, `panel()`; `input()`: Input; `elapsedMs()`: int duration argument in milliseconds; `screen()`: UniScreen |

Game types are in `com.zarkonnen.airships`; Input/Hooks are in `com.zarkonnen.catengine`, and Pt/ScreenMode are in `com.zarkonnen.catengine.util`. Except for enums and integers, the getters publicly return `Object`. Cast to the matching game type when accessing specific members. All three context classes have public constructors accepting the fields in the order shown above. Normally you do not need to construct them: the framework passes references to the actual arguments, without copying underlying mutable objects or providing additional non-null guarantees.

The recommended `CombatUiPanelType` values are `COMMAND_BUTTONS`, `DIRECT_CONTROL` and `RENAME_SHIP`. The general `PLAYER_CONTROL_PANEL_*` events do not include the rename panel. UI events track method calls and can occur even when no dialog is active; `RENAME_SHIP_AFTER_TICK` does not mean that the user has confirmed a rename.

```java
AirshipsCombatUiEvents.RENAME_SHIP_BEFORE_TICK.register(context -> {
    // Return CANCEL to block this panel input call if needed;
    // normally, preserve the default behavior.
    return EventResult.PASS;
});
```

## 7. Legacy ONE_SHOT compatibility and migration

Each of these three groups has `BEFORE_DRAW`, `AFTER_DRAW`, `BEFORE_TICK` and `AFTER_TICK` fields, for a total of 12:

- `ONE_SHOT_WEAPONS_*`
- `ONE_SHOT_BUOYANCY_*`
- `ONE_SHOT_POWER_*`

Historically, all of them hook **RenameShipPanel**, not one-shot weapon, buoyancy or power actions. These fields and the three corresponding enum labels are deprecated but retained. Their event objects remain independent: they are neither aliases of the new fields nor forwarded to the new events.

For a single draw/tick call, the order is: legacy WEAPONS BEFORE → legacy BUOYANCY BEFORE → legacy POWER BEFORE → new RENAME_SHIP BEFORE → original method → the three legacy AFTER groups in the same order → new AFTER. Any BEFORE cancellation stops subsequent groups, the original method and all AFTER groups. Any callback exception also prevents subsequent execution.

Older mods extending the rename panel can subscribe to the matching `RENAME_SHIP_*` events and update their `panelType()` checks. Do not keep both old and new subscriptions for the same work, or it will run twice. Actual weapon/buoyancy/power actions require separately designed and verified hooks; this version does not provide those action events.

## 8. Bundled native resources

Place native mod content under `acbric_vanilla/` inside the JAR:

```text
example-mod.jar
├── fabric.mod.json
├── example/ExampleMod.class
└── acbric_vanilla/
    ├── info.json
    └── <native mod data and assets>
```

On first launch, resources are extracted to `<AGame user data>/mods/<Fabric mod ID>/`. Minimal metadata is generated if `info.json` is absent. Top-level JAR/ZIP sources are supported; nested JARs and unpacked directory sources are not currently processed. Resource-only mods can omit a Java entrypoint and do not need to declare a mixin they do not use.

Updates use the SHA-256 ownership records in `.acbric-bundle.json`. Unchanged managed files may be updated or removed; user edits, deletions and collisions with unowned files are preserved and reported. Old directories without ownership records are not adopted automatically, and corrupt records stop that update. Original directory backups are kept under `<user data>/.acbric-bundles/<id>/backups/`. The transaction journal supports recovery from process interruption; it does not guarantee full durability against hardware failures.

With the game closed, use `BundledVanillaModMigration` to explicitly migrate an old directory. It first retains a complete backup, then adopts only files identical to those in the specified package. Different or missing files are recorded as conflicts rather than automatically replaced. See [resource management](BUNDLED_RESOURCES.md) for commands, conflict resolution and recovery. Removing a Java mod does not automatically delete its extracted resources.

## 9. Mixins, internal implementation and current limits

The public extension surface is `net.fabricacs.api` and its `event`, `util` and `save` packages. The `impl` and `mixin` packages are framework internals: a public method there is not necessarily a stable third-party extension contract. Do not call `LifecycleHooks.fire*` directly to simulate game behavior.

For capabilities not yet covered by the API, declare mixins in your own mod and use the actual game bytecode to determine signatures, invocation owners and injection points. There are currently no mappings; use `remap=false`. Public API compatibility does not automatically make custom mixins compatible across game versions.

The Fabric mod installation UI stages JARs, validates the schema/ID/version/structure of `fabric.mod.json`, and rejects overwriting an existing file with the same name. It does not preflight all dependencies, nested contents or mixin execution. Restart after installation. Since dev.12, the list also shows disabled archives in the scanned mod directory and supports enable/disable on the next full restart; see [MOD management](MOD_MANAGEMENT.md). Update both launcher and API; no hot unloading or archive removal.

Since `0.3.3-dev.2`, GameProvider reads `AGame.VERSION` from bytecode before dependency resolution and reports recognized versions to Fabric. If unavailable or unrecognized, the normalized version falls back to `0.0.0` with a warning. Mods pinned to the former `0.0.0` placeholder may now fail dependency checks. Ordered game archive hashes distinguish different builds sharing a version number; they do not guarantee compatibility. Local session reports distinguish loaded mods from successful `acbric` entrypoints. See [diagnostics and compatibility details](DIAGNOSTICS.md); these reports introduce no new public mod API.

## 10. Validation scope

The standard build passes 597 headless assertions, including 62 config checks. Real Fabric probes on games 1.2.15.2 and 1.2.14 each pass 10 campaign-data and 31 lifecycle/demo-v3.1 checks plus 5 managed-event runtime checks. The latter include explicit event dispatch for configuration policies; they do not replace GUI or live multiplayer acceptance. User feedback for dev.4 reports no issues so far; dev.10 remains pending full manual acceptance.

## 11. Campaign data

Use `context.campaignData(campaignWorld.map)` after obtaining the actual map. The `net.fabricacs.api.save` API provides `read`, `write`, `remove` and explicit `migrate`; reads return independent `CampaignDataSnapshot` values, and failed migrations preserve the original. Data is namespaced by mod ID, stored through the native save pipeline and restored with the map, including unknown mod namespaces. Serialization runs no mod callbacks.

Writes affect local shared game state and **do not broadcast**. Use deterministic simulation/command execution on all peers. Handles belong to one map instance and must be reacquired after replacement. Supported values, exact contracts, schema handling and storage/recovery limits are in the [campaign data guide](CAMPAIGN_DATA.md).

## 12. Campaign lifecycle

Since dev.4, `AirshipsCampaignEvents` exposes `CREATED`, `LOADED`, `RESTORED` and `EXITED`. Creation precedes the initial autosave; loaded JSON includes campaign extension data; restoration only rebinds local handles and must not mutate shared state. See [the full lifecycle contract](CAMPAIGN_LIFECYCLE.md). Existing public members remain available.

## 13. MOD configuration (dev.5)

Use `context.config(...)` followed by explicit `load/migrate/save/reload`. Defaults fill missing fields only; failures retain the previous snapshot. Disk commits use backups and conflict checks. Local preferences may reload, while shared gameplay rules belong in campaign data. See [the configuration guide](CONFIG.md).

## 14. Runtime diagnostics and event scopes (dev.6)

`context.eventScope(name)` provides register/registerOnce/close. Managed failures report MOD/event/exception context; legacy semantics remain. See [full contracts](EVENT_SCOPES.md). Adds 81 scope and 27 diagnostic checks; see the changelog for real-loader coverage. GUI/live multiplayer acceptance remains manual.

## 15. Local code manifests (dev.7)

[Export and offline comparison](CODE_MANIFEST.md) are internal diagnostic tools, not a new public MOD API. The 597 standard checks include 45 new manifest checks. A CODE_MATCH result covers startup code identity only; lobby integration is available in dev.9, and dev.10 adds declared-rule checks; automatic state synchronization is not implemented. The internal handshake core is described below.

## 16. Internal code handshake (dev.8)

See [protocol and state contract](CODE_HANDSHAKE.md). This is package-private framework infrastructure with 77 new regression checks, not a public MOD networking API. Real native Server/Client probes exercise it on both supported game snapshots; dev.9 runs it automatically in campaign lobbies. The core result remains local evidence rather than permission to start.

## 17. Campaign lobby integration (dev.9)

See [player behavior and integration contract](LOBBY_HANDSHAKE.md). Code failure blocks ready/start; changing context revokes preparation. All participants need matching code and a supporting framework. Existing MOD interfaces remain compatible, but this does not permit mixed old/new frameworks in a room. There is no new public networking API or automatic state/configuration synchronization.

## 18. Shared gameplay rules (dev.10)

Declare with `context.sharedRules`, retain a `SharedRules` handle for candidates, and read frozen values through `forCampaign(world.map)`. See the [complete contract and example](SHARED_RULES.md). Validators must be pure; missing/incompatible saved rules never silently adopt local defaults. All peers must match before preparation; changed candidates revoke preparation without overwriting configuration. Adds the public `rules` package and one mixin, for 20 in total.

## 19. Saved rule preflight and conversion (dev.11)

`SharedRules.migration` registers direct schema upgrades. `CampaignRuleSaves.inspect` returns a report, `prepare` previews before/after values, and `writeNew` publishes only to an absent new directory. Normal loading never migrates or adopts defaults. See the [complete contract](RULE_SAVE_MIGRATION.md). Adds `OpenGameMissionMixin`, for 21 mixins; malformed storage, changed sources and destination conflicts explicitly fail.

## 20. Shared UI components (dev.13)

`context.ui()` registers MOD tools and opens native-style component windows. `Ui` builds labels, buttons, toggles, single-line text, rows, columns, panels and scroll regions. `UiWindowHandle` owns dialogs and cleanup resources. See [UI.md](UI.md) for signatures, lifecycle, thread rules, examples and limitations. Native simulation/network ticks continue under modal UI. Framework [MOD details](MOD_MANAGEMENT.md) use the same components.

Localization: dev.14 adds `AcbricLanguage.text(english, chinese)` and `ModUi.register(id, Supplier<String>, factory)`; see [UI.md](UI.md).
