# Acbric external distribution and launch

[中文](EXTERNAL_START.zh-CN.md)

The external Acbric distribution uses an existing game installation. It does not contain game code, assets, game dependencies or player data. Use `Acbric.exe` normally; `advanced/` contains compatibility and diagnostic tools.

```text
Acbric/
├─ Acbric.exe
├─ advanced/
├─ mods/
├─ core/acbric-api.jar
├─ loader-libs/
├─ runtime/             default Java 21 runtime
└─ instances/default/...
```

Place Java `.jar` files in Acbric's `mods/`. Native MODs use the active data environment's native `mods/`: the vanilla data directory in shared mode or Acbric's `mods/` in isolation. Native folders must directly contain `info.json`; import `.amod` archives through the game. Legacy directories are not scanned automatically.

## Player entry

Open `Acbric.exe`, confirm the game location and select **Use this game**, then select **Start game**. See the [player guide](INSTALLER.md) for Steam launch, environment isolation, updates and recovery.

## Manual launch

Players normally do not need this command. For diagnostics, run it from a complete distribution:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "C:/Tools/Acbric/start.ps1" -GameDir "C:/Games/Airships" -InstanceDir "C:/AcbricInstances/default"
```

`GameDir` must contain `Airships.json` and the game A/B archives. `InstanceDir` is writable and must not contain or be contained by the game. If `JavaHome` is omitted, the package `runtime` takes precedence over `JAVA_HOME`. Do not use the game's Java 8.

The launcher verifies its core and launch dependencies, rechecks the game and starts a child JVM in the instance directory. The API loads from `core/acbric-api.jar`; do not copy it into `mods`. Hashes detect missing or changed files; they are not a digital signature.

## Data locations

- MODs: `<Acbric folder>/mods/`
- Saves and native settings: `<InstanceDir>/userdata/`
- MOD configuration: `<InstanceDir>/config/`
- Launch overrides: `<InstanceDir>/config/launch-settings.json`
- Launch log: `<InstanceDir>/logs/acbric/launcher/launch-*.log`
- Native log: `<InstanceDir>/userdata/log.txt`

Instances are isolated by default and do not import old saves automatically. The first launch creates local texture caches; do not share instances, caches or game copies. Third-party Java MODs can still write outside the instance.

## Build a distribution

```powershell
.\gradlew.bat externalDistZip -PexternalGameDir="C:/Games/Airships"
.\gradlew.bat externalDistZip -PexternalGameDir="C:/Games/Airships" -PbundleRuntime=false
```

The default command uses JDK 21 `jlink` to include a fixed Java 21 runtime; the second creates a minimal package without one. Output is `build/external-dist/Acbric-external-<version>.zip`, with `verification.json` alongside it.

The external distribution does not include the game. `externalGameDir` is used only to verify that no local game content entered the package. Legacy `distZip` includes the local game directory and is intended only for legacy-layout development.

## Known limits

The current player workflow targets Windows x64. Workshop, arbitrary MODs, other devices and different GPU configurations still require targeted testing. The native terrain GL issue remains deferred, and its origin has not been established.
