# Developer documentation map

[中文](DEVELOPMENT.zh-CN.md) · Current source/API: **0.3.8**.

Topic documents define current behavior; changelogs and historical snapshots do not replace current contracts. New public interfaces need matching English/Chinese documentation, an example and bounded failure behavior.

## First MOD workflow

1. Prepare your own game files and JDK 21 or newer as described in the [build guide](BUILDING.md). Keep a separate game/user-data directory for testing.
2. Copy `acbric-mod-template` to an independent project and configure `local.properties` as described in its [README](../acbric-mod-template/README.md). Do not redistribute game classes or resources with MOD source.
3. Give the MOD a unique ID and an `acbric` entrypoint. Implement both initializer methods when overriding the context form; the old no-argument method remains required for compatibility. See [API](API.md).
4. Pick the smallest suitable service from the table below. Use an existing event/API before introducing a game-version-specific mixin, and declare the minimum API version actually used.
5. Build the independent JAR, place one version in the configured external framework's `Acbric/mods/`, restart, and open **Acbric API → Details → Developer tools**. Check entrypoint status separately from loaded status. Inspect errors and export diagnostics when needed.
6. Test English and Chinese, unavailable contexts, failure paths and registration cleanup.

## Capabilities and authoritative contracts

| Need | Contract | Minimum API / boundary |
|---|---|---|
| Initialization, context, paths and logging | [API](API.md) | Follow the current API guide. |
| Events and renamed UI callbacks | [Events](EVENTS.md) | Old `ONE_SHOT_*` remains a compatibility hook. |
| Managed subscriptions and event errors | [Scopes](EVENT_SCOPES.md) | Exceptions still propagate. |
| Managed native resources | [Resources](BUNDLED_RESOURCES.md) | Preserve user changes; explicit migration. |
| Local JSON config | [Config](CONFIG.md) | No automatic broadcasting. |
| Campaign JSON and recovery | [Data](CAMPAIGN_DATA.md), [lifecycle](CAMPAIGN_LIFECYCLE.md) | Storage is not runtime synchronization. |
| Shared rule declarations | [Rules](SHARED_RULES.md) | Freeze at creation; read from the save on resume. |
| Explicit old-save conversion | [Save migration](RULE_SAVE_MIGRATION.md) | Preview and write a separate destination. |
| Java MOD management | [Management](MOD_MANAGEMENT.md) | Restart-based; no runtime unload. |
| Shared UI / text / cleanup | [UI](UI.md) | Components, input, window lifetime and scaling. |
| Combat command-panel controls | [Command-panel UI](COMMAND_PANEL_UI.md) | Native controls and anchors; no gameplay synchronization. |
| Draft-based settings forms | [Settings](SETTINGS.md) | The MOD defines effect timing. |
| Commands and help/completion | [Commands](COMMANDS.md) | Shared gameplay execution is not available. |
| In-game tools / diagnostic snapshots | [Developer tools](DEVELOPER_TOOLS.md) | Bounded local capture and export. |
| Startup identity / reports | [Diagnostics](DIAGNOSTICS.md) | Report layouts are internal. |
| Code identity and lobby protocol | [Manifests](CODE_MANIFEST.md), [handshake](CODE_HANDSHAKE.md), [lobby](LOBBY_HANDSHAKE.md) | Not authentication or state replication. |

Public packages define MOD contracts. Classes in `impl`, `mixin`, `UiRuntime` and `DeveloperConsoleUi` are internal adapters even when Java visibility is public. Diagnostic record APIs are public; JSON/file layouts remain internal. Existing public signatures and documented event ordering must remain compatible unless a separately agreed change says otherwise.

## Maintainer checks

Run `build full` before release and use `externalDistZip` for a framework-only distribution with a Java 21 runtime by default; use `-PbundleRuntime=false` for a minimal package. Keep standalone test MODs outside the framework source set and default distribution. Mixin changes require checks against real game bytecode and loading; recording-renderer tests do not replace IME, focus or OpenGL testing.

Release history: [English](CHANGELOG.md) / [Chinese](CHANGELOG.zh-CN.md).
