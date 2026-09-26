# Acbric external distribution launcher (dev.29)

[中文](EXTERNAL_START.zh-CN.md)

Experimental Windows x64 framework distribution. Bring a complete local game installation and Java 21. No game code, resources, game libraries or player data are included. The graphical setup wizard includes Steam discovery. Local ZIP update/restoration and bilingual quick-start files are available; online auto-update is not implemented. Use a fresh instance and follow these instructions to reinstall MODs and configure settings; automatic migration from legacy layouts is not provided.

```text
Acbric/
├─ Setup.cmd             配置 / Setup
├─ Start Acbric.cmd      启动 / Play
├─ mods/
│  ├─ arc-overhaul.jar
│  └─ native-mod/info.json
├─ core/acbric-api.jar
└─ instances/default/...
```

Old instance `mods`, `userdata/mods` and the original User folder are not scanned for local MODs. Close the game and copy MODs manually; native folders must directly contain `info.json`. Import native `.amod` archives using the game’s Install MOD action.

## Recommended: setup wizard

Double-click **Setup.cmd**, select game and instance folders, check and save. Use `Start Acbric.cmd` beside Setup afterwards without entering paths again. See [setup and relocation](INSTALLER.md).

## Manual launch

Extract the package and run in PowerShell, replacing paths with your local paths:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "C:/Tools/Acbric/start.ps1" -GameDir "C:/Games/Airships" -InstanceDir "C:/AcbricInstances/default" -JavaHome "C:/Java/jdk-21"
```

`GameDir` contains `Airships.json` and `asplit-A.zip`. `InstanceDir` is a separate writable directory; neither may contain the other. Pass both paths on each launch without copying the game. If `JavaHome` is omitted, the optional bundled `runtime` takes precedence over `JAVA_HOME`. Do not use the game's Java 8. After moving directories, pass their new paths.

The launcher verifies SHA-256 hashes of the core and launch dependencies, rechecks the game, then starts a child JVM with the instance as its working directory. The API loads from `core/acbric-api.jar` in the package; **do not copy another API into mods**. The manifest detects missing/changed files; it is not a digital signature. Do not distribute hand-built Knot or internal probe commands as player launchers.

Only one game process can use an instance at a time. Original installation settings stay unchanged; instances are isolated and do not automatically import existing saves.

## MODs, settings and logs

- Java MODs, native MODs and bundled resources: `<Acbric framework folder>/mods/`.
- Saves and native settings: `<InstanceDir>/userdata/`.
- MOD configuration: `<InstanceDir>/config/`.
- Launch overrides: `<InstanceDir>/config/launch-settings.json`; native data and GIF output paths remain forced inside the instance.
- Complete launch output: `<InstanceDir>/logs/acbric/launcher/launch-*.log`; native log: `userdata/log.txt`.
- Early lookup/core failures: the console prints the temporary `acbric-bootstrap-*.log` or `acbric-launch-*.log` location.

First launch creates local texture caches. Do not share instances, caches or game copies with the framework. Arbitrary third-party Java MODs can still write outside the instance.

## Build a distribution or MOD

```powershell
.\gradlew.bat externalDistZip -PexternalGameDir="C:/Games/Airships"
# Optional: include an independent runtime built from the current JDK 21
.\gradlew.bat externalDistZip -PexternalGameDir="C:/Games/Airships" -PbundleRuntime
```

Output: `build/external-dist/Acbric-external-<version>.zip`; recursive content verification: `verification.json` alongside it. Do not share legacy `distZip`: it still includes local game content and remains only for legacy development.

In `acbric-mod-template/`, copy `local.properties.example` to `local.properties`. Set local `gameInstallDir`, this distribution's `frameworkDir`, and target `instanceDir`; build using JDK 21. Dependencies are referenced without copying the game into the template. Private paths and legacy libs are excluded from Git/distribution lists. `installMod` requires an explicitly configured target instance.

## Known limits

See [external validation](EXTERNAL_INSTALL.md) for menu, campaign and media evidence and limitations. The terrain GL error also reproduced in the legacy layout is deferred by user decision and does not block the installation refactor; it is not fixed. Workshop, arbitrary MODs, other devices and GPU combinations need further acceptance. Setup and local update/restoration are available; uninstall remains a later phase; automatic legacy-data migration is outside the development plan.

## Developer acceptance

```powershell
python tools/test_distribution_scan.py
python tools/test_external_release.py --game-dir "C:/Games/Airships" --java-home "C:/Java/jdk-21" --tag releasecheck
```

Build `regressionTestClasses` and `externalDistZip` first. This creates a fresh isolated directory under build, briefly opens the real game and exits via a test-only MOD after observing menu frames. It also verifies lock rejection, bundle corruption, an invalid installation and independent template compilation. `--arc-jar <path>` optionally adds ARC. Reports are in `build/external-release-tests/<tag>/`; use a fresh tag each time. The production bundle contains no test MOD.
