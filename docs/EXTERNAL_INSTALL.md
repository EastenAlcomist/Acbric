# External installation and isolation

[English] · [中文](EXTERNAL_INSTALL.zh-CN.md)

This page describes how Acbric uses an existing game installation, isolates instance data and loads a distribution core. Player steps are in the [player guide](INSTALLER.md); packaging and manual launch are in [external distribution](EXTERNAL_START.md).

## Directory relationship

External mode uses two separate directories:

- **GameDir**: the installed game, containing `Airships.json`, root A/B archives, `lib/`, `data/` and default design assets.
- **InstanceDir**: configuration, user data, logs and caches.

They cannot be the same or contain one another. Acbric does not guess a new location after either directory moves.

## Preflight

Preflight checks:

- `Airships.json` structure, `classPath` and the standard `Main.main(String[])` entrypoint.
- Game A/B archives, critical dependencies, duplicate classes and basic resource directories.
- Game version and code-archive fingerprint.
- PE architecture of Windows x64 native libraries.
- Instance writability, non-overlapping roots and linked/reparse-path rejection.

Preflight does not initialize game classes or launch the game. Success only means the inputs can be launched; it does not prove complete asset, DLC, MOD or gameplay compatibility.

To inspect an installation directly:

```powershell
.\gradlew.bat preflightTools
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\build\preflight\check-install.ps1 -GameDir "D:/Games/Airships Conquer the Skies" -InstanceDir "D:/Acbric/instances/default" -JavaHome "D:/Java/jdk-21"
```

Exit `0` means this phase passed and `2` means an argument or check failed. The console prints the report path. Early failures use a system temporary report; successful reports are copied under the instance's `logs/acbric/preflight/`.

## Launch flow

After confirming the game, instance and core, the launcher:

1. `ExternalLauncher` locks the instance and verifies distribution hashes.
2. The Provider reads the selected game and builds the game classpath from `Airships.json`.
3. The API loads from `core/acbric-api.jar` and verifies its version and origin before pre-launch.
4. Acbric sets the instance working directory and merged launch settings.
5. The child JVM invokes the game, and Fabric runs `acbric` entrypoints and game-adapter mixins.

Missing/mismatched core, invalid game inputs or an occupied instance fail before the game main class. Internal launch properties and probe commands are not public MOD APIs or a security boundary.

## MOD roots

Java and native MODs use different roots:

- Java `.jar`: `fabric.modsFolder`, normally `mods/` beside `Acbric.exe`.
- Native MODs: the active data environment's native `mods/`.
- A Java MOD's `acbric_vanilla/`: extracted into the corresponding native root and filtered according to Java MOD state.

Shared mode places native MODs in vanilla user data; isolated mode keeps them in the instance. Do not place Java `.jar` files in the native directory.

## Data and launch settings

The instance input is `instances/<id>/config/launch-settings.json`. Acbric applies its top-level keys over the installation's `launch_settings.json` and writes the result to the instance `.fabric/acbric/launch-settings.json`. Omitted fields retain installation values. Malformed input aborts launch instead of silently reverting.

Two output fields are always routed through the active data environment:

| Field | Effective location |
| --- | --- |
| `customDataDirectoryLocation` | Instance `userdata/`, or the vanilla data directory in shared mode |
| `customGIFSaveDirectoryLocation` | Instance `userdata/gifs/` |

Failure to use instance user data does not fall back to a global user directory. Arbitrary third-party Java MODs may still write elsewhere, so this is not a complete sandbox.

## Texture cache

External mode reads source images on demand and creates instance caches under `cache/game-textures/v1/<content-key>/`:

- The complete asset tree is not copied, and source files are never moved or deleted.
- When an image exists, raw textures are regenerated instead of trusting an older `.tex`.
- Source, content and metadata separate cache namespaces across MODs and instances.
- Invalid raw lengths are removed before native mapping and rebuilt from the source image when possible.
- Old caches are not collected automatically and must not be included in a framework distribution.

## Diagnostics

- Instance preflight: `<InstanceDir>/logs/acbric/preflight/`
- Launcher logs: `%LOCALAPPDATA%/Acbric/launcher-logs`
- Complete launch output: `<InstanceDir>/logs/acbric/launcher/launch-*.log`
- Native log: `<InstanceDir>/userdata/log.txt`
- Early script failure: `acbric-bootstrap-*.log` in the system temporary directory

Reports contain local paths and version information; review them before sharing.

## Known limits

The current workflow targets Windows x64. Custom launch classes, other platform layouts, official-server play, cross-device GPU combinations and arbitrary third-party MODs are not guaranteed by this layer. A native terrain-rendering GL issue remains deferred, and its origin has not been established. The framework does not suppress the error.
