# Developer documentation map

2026-09-26 dev.28: Setup.cmd, root Start Acbric.cmd and mods now share the same directory inside Acbric. Saving in setup updates the default instance binding; the root entry launches the most recently saved instance. Instance-local entries remain compatible. Manually copy dev.27 sibling MODs into Acbric/mods. Missing/invalid bindings, missing instances and framework relocation are diagnosed without guessing another instance. Bilingual prompts, path API docs and template targets are updated; dev.27 and earlier records below are historical.

dev.27: Java MOD `.jar` files and native MOD folders now share **`mods` beside Acbric**. Setup shows the full path and offers Open MOD folder. The API still loads automatically from `Acbric/core`; saves, configuration and logs remain in the instance. Instances using the same framework share MOD files but retain separate settings. Only one game session may use a shared MOD folder, preventing concurrent bundled-resource changes. Old folders are not migrated automatically; copy wanted MODs manually. dev.26 and earlier entries below are historical.

2026-09-26 dev.26: first installer phase is implemented as the bilingual Setup.cmd / setup.ps1 instance wizard, reusing external preflight, core verification and instance locking. It previews paths without writing, creates new instances, loads wizard-managed instances for rebinding, atomically saves configuration and generates acbric-launcher/Start Acbric.cmd. It can launch the game and open instance/log folders. Corrupt or modified entries are preserved and stale previews cannot overwrite concurrent saves. Legacy data is not imported. See INSTALLER.md / INSTALLER.zh-CN.md. Automatic discovery, automatic desktop shortcuts, upgrade/rollback and uninstall are not implemented; the GL issue remains deferred. Based on 944435a, uncommitted/unpushed; entries below are historical.

dev.26 validation: 961 standard checks pass (26 new setup/UI checks; two existing symlink-permission skips), plus 36 real external-loading/core-guard checks and six adversarial distribution scan tests. The extracted package is configured through setup.ps1 and its generated CMD is executed; with ARC, initial and post-relocation/rebind launches each render 30 real menu frames and initialize audio. Chinese/space/& paths, busy rejection, changed bundles, invalid installation, UTF-8 template builds and Java 8 rejection pass; all 5,325 installation file contents remain unchanged. Chinese/English panel renders were inspected. Native folder pickers/all DPI settings and full campaign/media/network scenarios were not rerun. Evidence: workspace 99-研究工具/Acbric实例安装器-dev26-20260926.

2026-09-26 dev.25: formal external launch via `start.ps1` / `ExternalLauncher` and a game-free `externalDistZip` are implemented. The API loads from distribution core; child output goes to instance logs and the resolved core is checked before game classpath unlock/preLaunch. Explicit paths, instance locking and cache isolation remain in effect. The template references local game/framework paths through UTF-8 local.properties; distribution allowlists exclude copied dependencies and private paths. See EXTERNAL_START.md / EXTERNAL_START.zh-CN.md. The known GL issue remains deferred and non-blocking. Legacy-data migration is excluded by user decision; players reconfigure. Next: installer/update/uninstall. Changes are recorded on dev, not pushed; legacy distZip is still not a clean framework package. dev.24 and older entries below are historical.

Validation: 935 standard checks pass (two existing symlink-permission skips), plus 36 real external-loading checks. Missing/mismatched resolved cores are rejected before preLaunch; Java 8 is rejected by bootstrap. Two extracted-package production launches in Chinese/space paths render 30 real menu frames and initialize audio; the second verifies bundled Java precedence. Separate API + ARC logs cover two menu launches. Busy-instance rejection, changed-core hashes, missing installation and standalone template compilation using UTF-8 local paths pass. Six adversarial distribution scan tests pass. All runtime scenarios preserve all 5,325 installation file contents. Evidence: workspace 99-研究工具/Acbric正式外部发行-dev25-20260926. Full campaign/media scenarios were not rerun this round; prior dev.24 results keep their original limits and do not imply other-device/Workshop/arbitrary-MOD acceptance.

2026-09-26 media acceptance: dev.24 baseline `42426a0` is committed, not pushed; subsequent campaign/media tests and documentation are uncommitted. DLC/native MOD reload and real GIF tests pass 35 functional assertions per process (70 total): four GIFs, normal 960×640/15 frames and half-size slow motion 480×320/30 frames, without overwriting older exports. All 5,325 installation file contents remain unchanged; 924 standard checks pass (two existing permission skips). However, each process records 454,032 repeated native GL errors: status PASS_WITH_NATIVE_GL_ERRORS. A strict legacy-layout control with external adapters inactive reproduces the same terrain-rendering error (2,268 in the first frame). This is not clean graphics acceptance; no rendering fix or error suppression was added. On 2026-09-26 the user deferred this low-priority issue and removed it as a blocker for the installation refactor. It reproduces in the legacy layout, but there is no vanilla control without Acbric, so attribution to the framework or game remains unconfirmed. Normal external startup still returns EXTERNAL_NOT_READY in the current code; next implement the formal external entrypoint and clean distribution/templates, followed by migration and installer work. Product JARs are identical to dev.24; ARC source and player runtimes are unchanged. Legacy distZip is still not a clean release.

Since dev.21, press the **backtick / tilde key** below Esc and left of 1 (physical `GRAVE`; Shift is optional) to open the console with the command field focused. The opening key is consumed. Ctrl/Alt/Meta combinations, inactive displays, native error/help/chat overlays, and existing Acbric windows do not trigger it. Close another Acbric window before using the shortcut. While the console is open this key remains ordinary text; use Esc/X/Close to dismiss. Holding the key cannot repeatedly reopen it. This is a fixed console shortcut, not a general key-binding API.

[中文](DEVELOPMENT.zh-CN.md) · Current source/API: **0.3.3-dev.24**, unreleased.

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
