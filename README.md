# Acbric — Airships Fabric Mod Framework

2026-09-26 dev.28: Setup.cmd, root Start Acbric.cmd and mods now share the same directory inside Acbric. Saving in setup updates the default instance binding; the root entry launches the most recently saved instance. Instance-local entries remain compatible. Manually copy dev.27 sibling MODs into Acbric/mods. Missing/invalid bindings, missing instances and framework relocation are diagnosed without guessing another instance. Bilingual prompts, path API docs and template targets are updated; dev.27 and earlier records below are historical.

dev.27: Java MOD `.jar` files and native MOD folders now share **`mods` beside Acbric**. Setup shows the full path and offers Open MOD folder. The API still loads automatically from `Acbric/core`; saves, configuration and logs remain in the instance. Instances using the same framework share MOD files but retain separate settings. Only one game session may use a shared MOD folder, preventing concurrent bundled-resource changes. Old folders are not migrated automatically; copy wanted MODs manually. dev.26 and earlier entries below are historical.

2026-09-26 dev.26: first installer phase is implemented as the bilingual Setup.cmd / setup.ps1 instance wizard, reusing external preflight, core verification and instance locking. It previews paths without writing, creates new instances, loads wizard-managed instances for rebinding, atomically saves configuration and generates acbric-launcher/Start Acbric.cmd. It can launch the game and open instance/log folders. Corrupt or modified entries are preserved and stale previews cannot overwrite concurrent saves. Legacy data is not imported. See INSTALLER.md / INSTALLER.zh-CN.md. Automatic discovery, automatic desktop shortcuts, upgrade/rollback and uninstall are not implemented; the GL issue remains deferred. Based on 944435a, uncommitted/unpushed; entries below are historical.

dev.26 validation: 961 standard checks pass (26 new setup/UI checks; two existing symlink-permission skips), plus 36 real external-loading/core-guard checks and six adversarial distribution scan tests. The extracted package is configured through setup.ps1 and its generated CMD is executed; with ARC, initial and post-relocation/rebind launches each render 30 real menu frames and initialize audio. Chinese/space/& paths, busy rejection, changed bundles, invalid installation, UTF-8 template builds and Java 8 rejection pass; all 5,325 installation file contents remain unchanged. Chinese/English panel renders were inspected. Native folder pickers/all DPI settings and full campaign/media/network scenarios were not rerun. Evidence: workspace 99-研究工具/Acbric实例安装器-dev26-20260926.

2026-09-26 dev.25: formal external launch via `start.ps1` / `ExternalLauncher` and a game-free `externalDistZip` are implemented. The API loads from distribution core; child output goes to instance logs and the resolved core is checked before game classpath unlock/preLaunch. Explicit paths, instance locking and cache isolation remain in effect. The template references local game/framework paths through UTF-8 local.properties; distribution allowlists exclude copied dependencies and private paths. See EXTERNAL_START.md / EXTERNAL_START.zh-CN.md. The known GL issue remains deferred and non-blocking. Legacy-data migration is excluded by user decision; players reconfigure. Next: installer/update/uninstall. Changes are recorded on dev, not pushed; legacy distZip is still not a clean framework package. dev.24 and older entries below are historical.

Validation: 935 standard checks pass (two existing symlink-permission skips), plus 36 real external-loading checks. Missing/mismatched resolved cores are rejected before preLaunch; Java 8 is rejected by bootstrap. Two extracted-package production launches in Chinese/space paths render 30 real menu frames and initialize audio; the second verifies bundled Java precedence. Separate API + ARC logs cover two menu launches. Busy-instance rejection, changed-core hashes, missing installation and standalone template compilation using UTF-8 local paths pass. Six adversarial distribution scan tests pass. All runtime scenarios preserve all 5,325 installation file contents. Evidence: workspace 99-研究工具/Acbric正式外部发行-dev25-20260926. Full campaign/media scenarios were not rerun this round; prior dev.24 results keep their original limits and do not imply other-device/Workshop/arbitrary-MOD acceptance.

2026-09-26 media acceptance: dev.24 baseline `42426a0` is committed, not pushed; subsequent campaign/media tests and documentation are uncommitted. DLC/native MOD reload and real GIF tests pass 35 functional assertions per process (70 total): four GIFs, normal 960×640/15 frames and half-size slow motion 480×320/30 frames, without overwriting older exports. All 5,325 installation file contents remain unchanged; 924 standard checks pass (two existing permission skips). However, each process records 454,032 repeated native GL errors: status PASS_WITH_NATIVE_GL_ERRORS. A strict legacy-layout control with external adapters inactive reproduces the same terrain-rendering error (2,268 in the first frame). This is not clean graphics acceptance; no rendering fix or error suppression was added. On 2026-09-26 the user deferred this low-priority issue and removed it as a blocker for the installation refactor. It reproduces in the legacy layout, but there is no vanilla control without Acbric, so attribution to the framework or game remains unconfirmed. Normal external startup still returns EXTERNAL_NOT_READY in the current code; next implement the formal external entrypoint and clean distribution/templates, followed by migration and installer work. Product JARs are identical to dev.24; ARC source and player runtimes are unchanged. Legacy distZip is still not a clean release.

Since dev.21, press the **backtick / tilde key** below Esc and left of 1 (physical `GRAVE`; Shift is optional) to open the console with the command field focused. The opening key is consumed. Ctrl/Alt/Meta combinations, inactive displays, native error/help/chat overlays, and existing Acbric windows do not trigger it. Close another Acbric window before using the shortcut. While the console is open this key remains ordinary text; use Esc/X/Close to dismiss. Holding the key cannot repeatedly reopen it. This is a fixed console shortcut, not a general key-binding API.

**English** | [中文](README.zh-CN.md)

A lightweight Fabric-style mod loading framework for *Airships: Conquer the Skies*.

The framework grafts Fabric Loader's `KnotClient` launcher onto the game, so mods can
extend game logic with Java code and mixins while keeping and integrating the game's
native JSON data-mod system.

## Current development API and documentation

Current API: **0.3.3-dev.24** (unreleased). Existing events, managed resources, configuration/campaign data, shared rules/lobby checks, restart-based Java MOD management, shared UI and settings remain available. Command registration and built-in developer tools remain available; this revision fixes external-mode audio loading from Chinese paths and verifies the native menu. Entry: **Mods → Acbric API → Details → Developer tools / Console**.

- [Documentation map and first-MOD workflow](DEVELOPMENT.md)
- [Complete API guide](API.md)
- [Command API](COMMANDS.md) / [Developer tools](DEVELOPER_TOOLS.md)
- [English changelog](CHANGELOG.md) / [中文变更](CHANGELOG.zh-CN.md)

Validation: 872 standard checks and 464 real-game UI/console/settings-restart checks across two game builds; two existing symlink scenarios skipped for host permissions. The drawing terminal has no GPU. Windows clipboard, IME, focus switching and complete gameplay still require interactive acceptance. Code identity and save integration do not automatically synchronize arbitrary MOD state.

---

## ⚠️ This repository contains no game content

It ships **the framework only**. Three things are intentionally absent:

| Absent | Why |
|---|---|
| `libs/` | The game's compiled classes (`asplit-A.zip` / `asplit-B.zip`) and the library jars the game ships with. Redistributing them would violate copyright. |
| `game/` | The game's static data (`data/`, `ships/`, `images/` …) — about 1.3 GB. |
| Feature mods | Not part of the framework. |

The framework **will not compile or run** until you supply the first two. See below.

---

## 1. Prepare the game files

Copy these from your own legitimately owned Airships installation into this project,
keeping the same relative paths.

### `libs/` — compile + runtime dependencies

```
libs/
├── asplit-A.zip                # game classes (compile + runtime)
├── asplit-B.zip                # game classes (compile + runtime)
├── fabric-loader-0.19.3.jar    # Fabric Loader 0.19.3 (from Fabric, not the game)
├── <the game's library jars>
└── native/                     # native libraries
```

`libs/` must end up holding the jars and zips the game itself launches with — typically
`CatEngine.jar`, `CatSlick.jar`, `slick.jar`, `lwjgl.jar`, `lwjgl_util.jar`, `ibxm.jar`,
`jinput.jar`, `jogg-*.jar`, `jorbis-*.jar`, `commons-*.jar`, `joda-time-*.jar`,
`steamworks4j-*.jar` and `FloatIO.jar` — plus the game's `asplit-*.zip` class archives.

> `fabric-loader-0.19.3.jar` comes from Fabric, not from the game. Download it and drop it
> into `libs/`; the build references it as a local file.

### `game/` — the game's own directory

Copy the game directory that contains `Airships.json` (the file the launch shim parses):

```
game/
├── Airships.json               # required — the launcher parses it
├── launch_settings.json        # optional but recommended
├── data/                       # required — game data
├── lib/, native libs, ...
└── mods/                       # installed mods land here
```

`game/Airships.json` is the file `AirshipsGameProvider` reads to discover `mainClass` and
`classPath`; without it the launcher cannot locate the game.

---

## 2. Requirements

- **JDK 21** — set `JAVA_HOME` to your JDK, or pass `-Dorg.gradle.java.home=<JDK path>` to Gradle. No machine-specific JDK path is committed.
- Gradle 8.13, via the bundled wrapper.

## 3. Build & Run

```powershell
# Compile both framework layers and run headless regression checks
.\gradlew.bat build --console=plain

# Build the framework and start the game
.\gradlew.bat startAirships --console=plain

# Build/install just the API jar
.\gradlew.bat installApiMod --console=plain

# Build a distribution package (bundled JRE via jlink)
.\gradlew.bat distZip --console=plain
```

`build` includes `apiModJar` and the `regressionTest` task. The latter checks data-result
reporting, bundled resource extraction and classpath boundaries under `build/regression-sandbox/`.
It also covers managed resource upgrades/recovery, event subscription semantics and
Fabric metadata validation. It requires the local compile dependencies but does not start the GUI or use real saves.
Symbolic-link checks report a skip when the OS does not grant link-creation privileges.
These checks do not replace gameplay, save or multiplayer testing.

The distribution launcher is `run.bat`. `loader-libs/` contains the launch shim,
Fabric Loader, Mixin and ASM; `libs/` contains game dependencies. Keep these directories,
`game/` and `jre/` together. The provider also excludes launcher classes from old combined
`libs/` layouts by archive contents. The framework LICENSE is included in the distribution.
Building a ZIP does not verify that you supplied the complete game assets.

See [unreleased changes](CHANGELOG.md) for behavior changes and remaining limitations.
For resource conflicts, backups and old-directory migration, see [bundled resources](BUNDLED_RESOURCES.md).

New UI events, cancellation/order and legacy migration: [event contract](EVENTS.md).
The current API build is `0.3.3-dev.21`; the template requires `>=0.3.3-dev.10`; the template includes an opt-in campaign-data example.

## 4. Project layout

```
Acbric/
├── src/
│   ├── main/            # Launch shim: AirshipsGameProvider + GameProvider service entry
│   ├── apiMod/          # Runtime API (acbric_api): events, entrypoint bridge,
│                        # native-mod UI integration, hook mixins
│   └── regressionTest/  # Headless checks and transformed-panel probe entry
├── acbric-mod-template/ # Standalone mod project template
├── gradle/              # Gradle wrapper
└── build.gradle
```

### Architecture

| Layer | Location | Responsibility |
|---|---|---|
| Launch | `src/main` | `AirshipsGameProvider` pushes the game into Fabric: it parses `game/Airships.json`, assembles the classpath and reflectively calls `Main.main`. Depends on Fabric Loader, not game classes or the Acbric API. |
| API | `src/apiMod` | `acbric_api` — events, entrypoint bridge, native-mod UI integration, campaign data, lobby checks and 21 hook mixins. |

**Launch chain**: `KnotClient.main` → ServiceLoader discovers `AirshipsGameProvider` →
classpath assembled → Fabric runs `preLaunch` → `AcbricApiPreLaunch` walks every `acbric`
entrypoint and calls `AcbricInitializer.onInitializeAcbric` (individually try/catch) →
game starts → API mixins fire events → mod listeners run.

## 5. License / credits

The framework code in this repository is released under the **MIT License** — see
[`LICENSE`](LICENSE).

The game *Airships: Conquer the Skies* and all of its assets are the property of their
respective owners and are **not** included in this repository.
