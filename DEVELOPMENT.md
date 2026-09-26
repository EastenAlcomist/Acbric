# Developer documentation map

dev.22 starts external-install refactoring: [installation preflight and internal loading prototype](EXTERNAL_INSTALL.md). This phase passes 904 standard checks, 25 external-loading checks on 1.2.15.3, and 142 legacy-layout ARC checks across two builds. Normal external game launch awaits resource-cache isolation. Legacy `distZip` still includes local game dependencies and is not a clean framework distribution. Console behavior and the existing launch/build instructions below continue to apply to the legacy layout.

Since dev.21, press the **backtick / tilde key** below Esc and left of 1 (physical `GRAVE`; Shift is optional) to open the console with the command field focused. The opening key is consumed. Ctrl/Alt/Meta combinations, inactive displays, native error/help/chat overlays, and existing Acbric windows do not trigger it. Close another Acbric window before using the shortcut. While the console is open this key remains ordinary text; use Esc/X/Close to dismiss. Holding the key cannot repeatedly reopen it. This is a fixed console shortcut, not a general key-binding API.

[中文](DEVELOPMENT.zh-CN.md) · Current source/API: **0.3.3-dev.22**, unreleased.

Start here for the current contracts. READMEs cover installation/build; topic documents define behavior. Changelogs and dated workspace research are historical evidence, not alternate current specifications. New interfaces need matching English/Chinese documentation, a minimal example, bounded failure behavior and regression coverage.

## First MOD workflow

1. Prepare your own game files and JDK 21 as described in [README](README.md). Keep a separate game/user-data directory for testing.
2. Run `gradlew.bat build syncModTemplateLibs`, copy `acbric-mod-template` to an independent project and follow its [README](acbric-mod-template/README.md). Do not redistribute game classes/resources with MOD source.
3. Give the MOD a unique ID and an `acbric` entrypoint. Implement both initializer methods when overriding the context form; the old no-argument method remains required for compatibility. See [API](API.md).
4. Pick the smallest suitable service from the table below. Use an existing event/API before introducing a game-version-specific mixin. Declare the minimum API version actually used; the template's dev.10 minimum does not cover newer UI/settings/command calls.
5. Build the independent JAR, place one version in the test copy's `game/mods/`, restart, and open **Acbric API → Details → Developer tools**. Check entrypoint status separately from loaded status. Inspect errors and export diagnostics when needed.
6. Test English and Chinese, unavailable contexts, failure paths and registration cleanup. Save code and logs needed to reproduce the result. GUI acceptance and multiplayer tests are separate from headless unit checks.

## Capabilities and authoritative contracts

| Need | Contract | Minimum API / boundary |
|---|---|---|
| Initialization, context, paths and logging | [API](API.md) | Per-member versions apply. |
| Events and renamed UI callbacks | [Events](EVENTS.md) | dev.1; old `ONE_SHOT_*` stays historical. |
| Managed subscriptions and event errors | [Scopes](EVENT_SCOPES.md) | dev.6; exceptions still propagate. |
| Managed native resources | [Resources](BUNDLED_RESOURCES.md) | Preserve user changes; explicit migration. |
| Local JSON config | [Config](CONFIG.md) | dev.5; no automatic broadcasting. |
| Campaign JSON and recovery | [Data](CAMPAIGN_DATA.md), [lifecycle](CAMPAIGN_LIFECYCLE.md) | dev.3 / dev.4; storage is not runtime synchronization. |
| Shared rule declarations | [Rules](SHARED_RULES.md) | dev.10; freeze at creation, read from save on resume. |
| Explicit old-save conversion | [Save migration](RULE_SAVE_MIGRATION.md) | dev.11; preview and separate destination. |
| Java MOD management | [Management](MOD_MANAGEMENT.md) | dev.12; restart-based, not runtime unload. |
| Shared UI / text / cleanup | [UI](UI.md) | dev.13 base, dev.18 binding/choice, dev.19 editing/footer, dev.20 submit. |
| Draft-based settings forms | [Settings](SETTINGS.md) | dev.18; MOD defines effect timing. |
| Commands and help/completion | [Commands](COMMANDS.md) | dev.20; shared gameplay execution reserved. |
| In-game tools / diagnostic snapshots | [Developer tools](DEVELOPER_TOOLS.md) | dev.20; bounded local capture/export. |
| Startup identity / reports | [Diagnostics](DIAGNOSTICS.md) | Since dev.2; report layouts are internal. |
| Code identity and lobby protocol | [Manifests](CODE_MANIFEST.md), [handshake](CODE_HANDSHAKE.md), [lobby](LOBBY_HANDSHAKE.md) | dev.7–9; not authentication or state replication. |

Public packages define MOD contracts. Classes in `impl`, `mixin`, `UiRuntime` and `DeveloperConsoleUi` are internal adapters even when Java visibility is public. Diagnostic record APIs are public; JSON/file layouts remain internal. Existing public signatures and documented event ordering must remain compatible unless a separately agreed change says otherwise.

## Maintainer validation

Run `gradlew.bat build` for isolated regression fixtures. `distZip` assembles a local distribution after dependencies are prepared; it is not a public game-content redistribution package. Keep standalone test MODs outside the framework source set/default distribution. Compile documentation examples against the produced API JAR. Compare prior public binary signatures, then exercise new hooks through real Fabric/Mixin on both maintained game builds. Recording-renderer tests do not replace Windows clipboard/IME/focus or OpenGL acceptance.

When adding a command, put the behavior in a reusable service first. Expose bilingual metadata and a closeable registration; query runtime state again when executing. The first console release intentionally limits built-ins to diagnosis and local export. Future settings/save commands must use existing validation and synchronization contracts.

Release history: [English](CHANGELOG.md) / [Chinese](CHANGELOG.zh-CN.md).
