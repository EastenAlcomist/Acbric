# External game installation: native menu verification (dev.24)

[中文](EXTERNAL_INSTALL.zh-CN.md)

This prototype implements **installation discovery/preflight, instance launch settings and texture-cache isolation**. dev.24 verifies real menu rendering and audio initialization in an isolated test and fixes OpenAL loading from a Chinese installation path. Existing `run.bat`, development launch and legacy layouts retain their behavior. The checker does not start the game and is not an installer. Normal external launch remains blocked pending campaign and broader integration validation; this is not a public installer release.

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

## Settings and output isolation

Before initializing game classes, the external Provider reads installation `launch_settings.json`, overlays instance `config/launch-settings.json`, and atomically writes the effective settings to instance `.fabric/acbric/launch-settings.json`. The native initializer reads that generated file. Omitted fields retain installation values; this is a top-level overlay, not a recursive merge. Missing files mean no overrides; malformed JSON, duplicate keys, linked paths, excessive nesting or files larger than 1 MiB abort instead of silently reverting. Original files and a previous generated file remain intact on parse failure. Edit the instance config and restart; do not edit the generated copy.

Two output fields are always forced, even if the installation or instance config requests somewhere else:

| Native field | Effective location |
|---|---|
| `customDataDirectoryLocation` | Instance `userdata/` |
| `customGIFSaveDirectoryLocation` | Instance `userdata/gifs/` |

The GIF entry checks the instance target again before export. If unavailable it fails before the native Desktop/home/installation fallback. Developer checksum output and randomized userdata paths are disabled in external mode. This does not sandbox arbitrary third-party MOD code. Actual GIF rendering/export has not been exercised by the automated probe.

## Texture cache

External mode intercepts the common native image-file loader and skips native `.tex` migration. It mirrors only the requested image and related image/cache candidates under instance `cache/game-textures/v1/<content-key>/`; it does not copy the full asset tree. The native file reader and raw-cache generator then operate on that mirror. Source ordering remains with the original MOD/DLC/base lookup. Generated and nested images use the same interception; actual DLC content remains untested.

- Source location, content and metadata determine separate cache namespaces. Different MOD sources and different instances cannot share a namespace accidentally.
- If a source PNG exists, unverified original `.tex` files are ignored and the instance raw texture is regenerated. A newer timestamp alone cannot prove a raw texture matches an edited PNG. Assets distributed only as raw textures remain supported; `generated` wins over the old `images` location. Originals are never moved or deleted.
- Repeated lookups use a bounded in-memory metadata cache. File size/time/identity changes trigger recomputation. Same-size, same-time edits are detected after native MOD reload or a process restart; they are not continuously hashed every frame.
- Invalid raw byte lengths are removed from the instance before the native memory-mapped reader and fall back to PNG when available. This is structural validation, not detection of every form of pixel corruption. Mirrored image damage is repaired on reload/restart. Failed isolation throws instead of writing beside original resources.
- Disk namespaces persist for reuse and currently have no automatic garbage collection. Mirrored assets and raw pixels remain local game content; do not include the instance/cache in framework releases. This cache is not a new public MOD API.

## Validation and next phase

```powershell
.\gradlew.bat build preflightTools regressionTestClasses
python tools/test_external_install.py --tag my-check --game-dir "D:/Games/Airships Conquer the Skies" --java-home "D:/Java/jdk-21"
```

Requires Windows, Python 3.11+ and JDK 21, with a fresh tag each run. Output is `build/external-tests/<tag>/`. Installation file contents are hashed before/after; the prototype never calls `Main.main`. Test sources/probe JARs are not packaged in the API. Do not commit or redistribute fixtures/logs/resources. Avoid concurrent game processes modifying the input installation, which would make hash differences ambiguous.

dev.23 validation: 924 standard checks (20 added since dev.22); 36 real Knot checks against the full 1.2.15.3 installation, with all 5,325 installation file contents unchanged. Checks cover preLaunch, origins/paths, effective launch settings, native texture-file reads/writes/cache fallback and userdata failure without global fallback. A test-only Mixin replaces the terminal Slick Image construction, so this exercises native file IO without a GPU. Legacy-layout ARC checks for 1.2.15.2 / 1.2.14 pass 71 each, 142 total. These results do not establish full graphics, native-library, DLC, Workshop or campaign compatibility. Probe results: `build/external-tests/dev23-final/`; test replacements are absent from production JARs.

### Real menu and audio test (dev.24)

```powershell
.\gradlew.bat build preflightTools
python tools/test_external_menu.py --tag my-menu-check --game-dir "D:/Games/Airships Conquer the Skies" --java-home "D:/Java/jdk-21"
```

Use **JDK 21** for this test harness: its Java write/network interceptor uses the deprecated SecurityManager, only in the test process. The production framework does not install it. A new output tag is required. Two launches run against one isolated instance by default (fresh cache, then restart); `--runs 1` selects only the first. Each process has a 90-second deadline, calls the real `Main.main`, and automatically exits after 30 completed menu renders. Native graphics, assets, audio and texture IO are not mocked. A game window and audio may briefly appear; do not use this as a player launcher. A render-return checkpoint is not a visual inspection of every widget or a clean-exit acceptance test.

Reports and console/native logs are in `build/external-menu/<tag>/`. The test blocks Java file writes outside that test directory and all Java network connections. Permission queries such as ZipFS `Files.isWritable` remain allowed; actual write handles remain blocked. It is not an OS sandbox or an ACL read-only test and does not intercept native driver writes. The original installation is separately hashed before/after. Network functionality is deliberately untested.

dev.24 passes 924 standard checks, 36 headless external checks and 142 legacy ARC checks, plus both real menu launches on the 1.2.15.3 input. Each menu process renders 30 frames with an NVIDIA RTX 5060 / OpenGL 4.6 context and OpenAL Soft; 147 raw texture files remain in the instance after each run. All 5,325 installation file contents remain unchanged. Standard tests still skip two pre-existing symlink cases on this host. These are local device results, not broad GPU compatibility or listening-quality verification.

The initial real test exposed OpenAL failure on the Chinese installation path (LWJGL native lookup error 126). External `Main` now preloads the selected installation's `OpenAL64.dll` through JVM `System.load`; LWJGL can then obtain the loaded module by name. This copies no DLL and does not change global PATH. Failure preserves the DLL cause in launch diagnostics. Legacy startup does not preload it.

### Write-path review and remaining acceptance

| Path family | Reviewed handling / current limit |
|---|---|
| Base/DLC checksum files | Read from installation; external `doWritechecksum=false` blocks development writes. Base and heroes checksum loading observed in the menu test. |
| Default designs, settings, log, recording cleanup | `AGame` copies/overlays into userdata; settings/log/recording paths use that root. Real initialization writes stay in the test instance. |
| Image/raw caches | Common loader uses instance mirrors; original `.tex` migration is skipped. Cold/warm menu launches pass. |
| MOD derived images/fragments | Generated under the source MOD directory; normal instance MODs are writable there. Linked/custom external MOD folders and Workshop remain unaccepted. |
| Missions/monsters | Static assets are read; backend locks protect static missions and monsters. User missions use userdata. No mission-editor workflow acceptance yet. |
| GIF and manual exports | GIF guard is implemented, actual export remains untested. User-selected export destinations are a separate explicit operation. |
| Offline author tools | Not invoked by normal Main. Their hard-coded development output paths are not a supported framework workflow. |

Review compares relevant 1.2.15.3 classes with the existing 1.2.15.2 decompilation; changed classes were checked separately. This is targeted source/bytecode review plus runtime write observation, not a proof covering arbitrary MOD code or every game branch.

Next: full campaign creation/save/reload, GIF export and DLC/MOD combinations before enabling normal external launch. Distribution/template cleanup, migration and installer follow. The current legacy `distZip` still contains local game dependencies and **must not be shared as a clean framework distribution**. `preflightTools` contains only framework launcher, explicit launch dependencies, script and documentation.
