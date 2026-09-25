# Acbric — Airships Fabric Mod Framework

**English** | [中文](README.zh-CN.md)

A lightweight Fabric-style mod loading framework for *Airships: Conquer the Skies*.

The framework grafts Fabric Loader's `KnotClient` launcher onto the game, so mods can
extend game logic with Java code and mixins while keeping and integrating the game's
native JSON data-mod system.

## Current development API and documentation

- [Campaign lobby code check](LOBBY_HANDSHAKE.md): automatic checking, preparation invalidation and start guards.

- [Internal code handshake](CODE_HANDSHAKE.md): bounded requests, fresh sessions, retries, timeouts and explicit results.

- [Local code manifests and offline comparison](CODE_MANIFEST.md): export loaded code identities and locate differences.

- [Runtime diagnostics and event scopes](EVENT_SCOPES.md): MOD attribution, grouped cleanup and error reporting.

The current API build is **0.3.3-dev.9**. Campaign lobbies now automatically check startup code and gate preparation/start. Peers need matching framework/game/MOD code; this is not state synchronization or support for mixed framework versions. Runtime event diagnostics and managed subscription scopes remain available. Campaign lifecycle and native save/recovery integration remain available. Existing public members and event semantics remain compatible.

- [Complete API guide](API.md) / [中文](API.zh-CN.md): entrypoints, context, paths, events and resources.
- [Change record (Chinese)](CHANGELOG.zh-CN.md) / [English changelog](CHANGELOG.md).
- [UI event contract](EVENTS.md) / [Bundled resource updates](BUNDLED_RESOURCES.md).
- [Standalone mod template](acbric-mod-template/README.md).
- [Game identity and startup diagnostics](DIAGNOSTICS.md): real game versions, build fingerprints and per-entrypoint results.
- [Campaign lifecycle events](CAMPAIGN_LIFECYCLE.md): creation, loading, restoration and exit.
- [Campaign data API](CAMPAIGN_DATA.md): persistence, schema migration and multiplayer boundaries.

The build runs 477 headless assertions, including 44 lobby and 77 handshake checks, 45 manifest checks, 81 scope checks, 27 runtime diagnostic checks and 62 configuration checks. User feedback for dev.4 reports no issues so far; no complete checklist was supplied. Ten isolated native Server/dual Fabric Client experiments pass 104 top-level checks across games 1.2.15.2 and 1.2.14. Full dev.9 GUI, successful world start and cross-machine/official-server acceptance remain manual.

- [Configuration API](CONFIG.md): local preferences, explicit reload and frozen campaign rules.

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
The current API build is `0.3.3-dev.9`; the template requires `>=0.3.3-dev.6`; the template includes an opt-in campaign-data example.

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
| API | `src/apiMod` | `acbric_api` — events, entrypoint bridge, native-mod UI integration, campaign data, lobby checks and 19 hook mixins. |

**Launch chain**: `KnotClient.main` → ServiceLoader discovers `AirshipsGameProvider` →
classpath assembled → Fabric runs `preLaunch` → `AcbricApiPreLaunch` walks every `acbric`
entrypoint and calls `AcbricInitializer.onInitializeAcbric` (individually try/catch) →
game starts → API mixins fire events → mod listeners run.

## 5. License / credits

The framework code in this repository is released under the **MIT License** — see
[`LICENSE`](LICENSE).

The game *Airships: Conquer the Skies* and all of its assets are the property of their
respective owners and are **not** included in this repository.
