# External game installation: phase one (dev.22)

[中文](EXTERNAL_INSTALL.zh-CN.md)

This phase implements **installation discovery/preflight and a minimal external-loading prototype**. Existing `run.bat`, development launch and legacy layouts retain their behavior. The new tool does not start the normal game and is not an installer. Resource-cache separation is still pending; this is not a publicly ready read-only installation mode.

## Check a local installation

Use JDK 21 to build the checker without building the old full distribution:

```powershell
.\gradlew.bat preflightTools
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\build\preflight\check-install.ps1 -GameDir "D:\Games\Airships Conquer the Skies" -InstanceDir "D:\Acbric\instances\default" -JavaHome "D:\Java\jdk-21"
```

Replace all paths. Windows x64, Java 21+. `-JavaHome` defaults to `JAVA_HOME`; the game's Java 8 is unsuitable. The script checks Java before loading framework bytecode, so an old runtime gets a readable English/Chinese error and a bootstrap log.

`GameDir` must contain `Airships.json`, root A/B archives, `lib/`, `data/` and default design resources. Installation and instance must not contain one another. The checker creates an instance lock file, necessary directories and diagnostics and checks writability; it does not import saves, install the API or copy the game. Empty directories/lock files may remain. A lock is held by a process, not by the mere presence of its file.

With compatible Java you can also run the entrypoint directly:

```powershell
java -cp "build/preflight/loader-libs/*" net.fabricacs.acbric.ExternalPreflight --game-dir "D:/Games/Airships Conquer the Skies" --instance-dir "D:/Acbric/instances/default"
```

Exit `0` means this phase's preflight passed; `2` means argument/check failure. Status `PREFLIGHT_OK_NOT_LAUNCHED` explicitly does not mean the game ran or that full asset/MOD compatibility was verified.

## Checks and diagnostics

- Uses Loader's existing JSON parser, handling escapes and unrelated nested values and rejecting duplicate fields, invalid types and trailing data. `classPath` is resolved against the installation root and must stay within it.
- Requires root A/B archives and native `Main.main(String[])`. Only this standard entrypoint is accepted. Configured `vmArgs` and `jrePath` are not executed. Custom launch classes, modified layouts and other platform layouts are outside this phase.
- Configured code order is followed by sorted `lib/` dependencies. The existing steamworks4j 1.3 exclusion/1.9 selection remains. Loader/Mixin/ASM/framework launch archives are excluded by content; duplicate ordinary classes fail instead of silently shadowing one another.
- Reads version and game archive SHA256 without defining or initializing game classes. Checks key dependency classes, selected nonempty resource directories and the PE architecture of four Windows x64 graphics/input DLLs. This is not exhaustive resource validation or actual DLL loading; Steam/Workshop and full game compatibility remain separate checks.
- Version strings 1.2.14 / 1.2.15.2 / 1.2.15.3 still produce `KNOWN_VERSION_UNVERIFIED_CONTENT`. Other parseable versions produce `UNKNOWN_VERSION`: a preflight plan can be reported without claiming support. Unreadable/unknown identity or incomplete fingerprints fail.
- Rejects linked/reparse instance paths and overlapping installation/instance roots. Instance write failures never fall back to global player data. A file lock prevents concurrent external prototype processes on the same instance; legacy launches do not yet use it.

Even bad installation/instance choices get an early report under the system temporary directory: Java `%TEMP%/acbric-preflight-*/preflight.properties`, script `%TEMP%/acbric-bootstrap-*.log`. A successful report is also saved under instance `logs/acbric/preflight/`. The console prints full locations. Reports contain paths, version, code fingerprints, classpath order and warnings. If Java could not start, inspect the bootstrap log. Redact personal paths before sharing logs.

## Internal loading prototype

Provider accepts the paired properties `acbric.external.install` / `acbric.external.instance` and strictly uses that installation. Missing/invalid paths never fall back to the legacy `libs` search. Currently only the repository's `ExternalRuntimeProbe` test entry is accepted; normal external launch fails with `EXTERNAL_NOT_READY`. These properties/test entrypoints are neither public MOD APIs nor a security boundary. Do not distribute bypass commands as player launchers.

The real Knot prototype reads the installation's A/B and `lib` directly. Fabric `gameDir` points to the instance. The path Mixin takes effect before API preLaunch: `AGame.getStaticGameDirectory()` is the installation; `AGame.getGameDirectory()` is instance `userdata/`. Java MODs/config/framework logs stay in the instance, bundled vanilla resources go to instance `userdata/mods`. Legacy mode retains native paths. Public methods including `AirshipsPaths.staticDataDir()` retain their instance-side semantics; no read-only installation resource API is added yet.

## Validation and next phase

```powershell
.\gradlew.bat build preflightTools regressionTestClasses
python tools/test_external_install.py --tag my-check --game-dir "D:/Games/Airships Conquer the Skies" --java-home "D:/Java/jdk-21"
```

Requires Windows, Python 3.11+ and JDK 21, with a fresh tag each run. Output is `build/external-tests/<tag>/`. Installation file contents are hashed before/after; the prototype never calls `Main.main`. Test sources/probe JARs are not packaged in the API. Do not commit or redistribute fixtures/logs/resources. Avoid concurrent game processes modifying the input installation, which would make hash differences ambiguous.

dev.22 validation: 904 standard checks (32 added); 25 real Knot checks against the full 1.2.15.3 installation, with all 5,325 installation file contents unchanged. Checks cover preLaunch extraction, class origins, identity/paths, instance diagnostics/config and failure instead of global userdata fallback. Legacy-layout ARC checks for 1.2.15.2 / 1.2.14 pass 71 each, 142 total. These results do not establish full game compatibility on the new version.

Next: instance `LaunchSettings` overrides, GIF/other user-output paths, base/DLC/vanilla-MOD texture cache reads/generation/invalidation, and all installation write paths; then verify a read-only installation through menu and full campaign. Distribution/template cleanup, migration and installer follow. The current legacy `distZip` still contains local game dependencies and **must not be shared as a clean framework distribution**. This phase's `preflightTools` contains only framework launcher, explicit launch dependencies, script and documentation.
