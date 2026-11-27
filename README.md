# Acbric — Airships Java MOD framework

[中文](README.zh-CN.md) · [Documentation index](docs/README.md)

Acbric connects Fabric Loader to *Airships: Conquer the Skies*. Java MODs extend the game through events, configuration, campaign storage, shared/combat UI, console commands and Mixins, alongside native JSON MODs.

Current development version: **0.3.8**. [fabric.mod.json](src/apiMod/resources/fabric.mod.json) is the version source; see the [changelog](docs/CHANGELOG.md).

The current compile baseline is *Airships: Conquer the Skies* **1.2.15.3** (`libs/asplit-*.zip`), and the runtime also supports 1.2.15.2. Other game versions are not guaranteed.

## Start here

| Goal | Entry |
| --- | --- |
| Install, play, add MODs, update or troubleshoot | [Player guide](docs/INSTALLER.md) |
| Create a first Java MOD | [Development workflow](docs/DEVELOPMENT.md), [standalone template](acbric-mod-template/README.md) |
| Look up existing interfaces | [API guide](docs/API.md), [topic index](docs/README.md) |
| Build, test or maintain the framework | [Build guide](docs/BUILDING.md), [agent and maintenance guidance](AGENTS.md) |

## Player setup

Fully extract `Acbric-external-<version>.zip` with bundled Java and open `Acbric.exe`. Confirm the automatically found Steam game and select **Use this game**, or choose its folder manually. Use the same EXE next time. Java MOD JARs always use the adjacent `mods` folder; native MODs use the active data environment's `mods` folder. See the [player guide](docs/INSTALLER.md).

The game must be installed separately. An instance stores configuration-specific settings, caches and logs; by default it uses the vanilla saves and MODs directly. Neither this repository nor the external distribution includes game code, game assets or third-party gameplay MODs.

## Framework development

Prepare JDK 21 or newer and local compilation dependencies from your own game, following the [build guide](docs/BUILDING.md). Run these commands from the repository root:

```powershell
.\build.cmd       # Compile
.\test.cmd all    # Full regression
.\test.cmd mods   # Select a suite for the change
```

Use `externalDistZip` for redistribution; it includes a Java 21 runtime by default. Add `-PbundleRuntime=false` for a minimal package. Commands are in the [external distribution guide](docs/EXTERNAL_START.md). Legacy `distZip` includes local game content and is only for legacy-layout research, not public distribution.

## Directory layout

| Directory | Contents |
| --- | --- |
| `docs/` | English/Chinese usage, development, API, mechanisms and history |
| `src/main/`, `src/shared/` | Launching, installation and protocols shared by both layers |
| `src/apiMod/` | Core API, game adapters and Mixins |
| `src/regressionTest/`, `tools/` | Isolated regressions, real-runtime fixtures and acceptance tools |
| `src/dist/` | Distribution launchers and player quick-start instructions |
| `acbric-mod-template/` | Template for an independent MOD project |
| `gradle/` | Build wrapper |
| `build/`, `.gradle/` | Local build outputs and caches; Git-ignored |
| `libs/`, `game/` | Locally supplied dependencies and legacy test data; Git-ignored |

Java MOD selection requires a restart. Campaign extension writes do not automatically synchronize over the network. Code matching is neither anti-cheat nor a guarantee for arbitrary MOD combinations. Known native GL errors remain deferred; see [external installation](docs/EXTERNAL_INSTALL.md).

Acbric uses the [MIT license](LICENSE). Game and third-party components retain their respective owners and licenses.
