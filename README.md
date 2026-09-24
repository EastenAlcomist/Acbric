# Acbric — Airships Fabric Mod Framework

**English** | [中文](README.zh-CN.md)

A lightweight Fabric-style mod loading framework for *Airships: Conquer the Skies*.

The framework grafts Fabric Loader's `KnotClient` launcher onto the game, so mods can
extend game logic with Java code and mixins while keeping and integrating the game's
native JSON data-mod system.

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

- **JDK 21** — `gradle.properties` pins `C:/Program Files/Java/jdk-21`; edit it for your machine.
- Gradle 8.13, via the bundled wrapper.

## 3. Build & Run

```powershell
# Build the framework and start the game
.\gradlew.bat startAirships --console=plain

# Build/install just the API jar
.\gradlew.bat installApiMod --console=plain

# Build a distribution package (bundled JRE via jlink)
.\gradlew.bat distZip --console=plain
```

## 4. Project layout

```
Acbric/
├── src/
│   ├── main/            # Launch shim: AirshipsGameProvider + GameProvider service entry
│   └── apiMod/          # Runtime API (acbric_api): events, entrypoint bridge,
│                        # native-mod UI integration, hook mixins
├── acbric-mod-template/ # Standalone mod project template
├── gradle/              # Gradle wrapper
└── build.gradle
```

### Architecture

| Layer | Location | Responsibility |
|---|---|---|
| Launch | `src/main` | `AirshipsGameProvider` pushes the game into Fabric: it parses `game/Airships.json`, assembles the classpath and reflectively calls `Main.main`. Zero dependencies, no API coupling. |
| API | `src/apiMod` | `acbric_api` — the event system, the entrypoint bridge, native-mod UI integration and 13 hook mixins. |

**Launch chain**: `KnotClient.main` → ServiceLoader discovers `AirshipsGameProvider` →
classpath assembled → Fabric runs `preLaunch` → `AcbricApiPreLaunch` walks every `acbric`
entrypoint and calls `AcbricInitializer.onInitializeAcbric` (individually try/catch) →
game starts → API mixins fire events → mod listeners run.

## 5. License / credits

The framework code in this repository is released under the **MIT License** — see
[`LICENSE`](LICENSE).

The game *Airships: Conquer the Skies* and all of its assets are the property of their
respective owners and are **not** included in this repository.
