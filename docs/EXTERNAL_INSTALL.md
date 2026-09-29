# External game installation: implementation and validation (dev.25)

2026-09-26 dev.28: Setup.cmd, root Start Acbric.cmd and mods now share the same directory inside Acbric. Saving in setup updates the default instance binding; the root entry launches the most recently saved instance. Instance-local entries remain compatible. Manually copy dev.27 sibling MODs into Acbric/mods. Missing/invalid bindings, missing instances and framework relocation are diagnosed without guessing another instance. Bilingual prompts, path API docs and template targets are updated; dev.27 and earlier records below are historical.

dev.27: Java MOD `.jar` files and native MOD folders now share **`mods` beside Acbric**. Setup shows the full path and offers Open MOD folder. The API still loads automatically from `Acbric/core`; saves, configuration and logs remain in the instance. Instances using the same framework share MOD files but retain separate settings. Only one game session may use a shared MOD folder, preventing concurrent bundled-resource changes. Old folders are not migrated automatically; copy wanted MODs manually. dev.26 and earlier entries below are historical.

[中文](EXTERNAL_INSTALL.zh-CN.md)

This prototype implements **installation discovery/preflight, instance launch settings and texture-cache isolation**. dev.24 verifies real menu rendering and audio initialization in an isolated test and fixes OpenAL loading from a Chinese installation path. Existing `run.bat`, development launch and legacy layouts retain their behavior. The checker does not start the game and is not an installer. Targeted campaign and media functional validation is complete; the known GL issue is deferred by user decision. dev.25 provides the formal entrypoint and a clean distribution; see [launch instructions](EXTERNAL_START.md). dev.26 provides the basic [instance setup wizard](INSTALLER.md); automatic legacy-data migration is excluded by user decision and players will reconfigure using the new instructions.


Validation: 935 standard checks pass (two existing symlink-permission skips), plus 36 real external-loading checks. Missing/mismatched resolved cores are rejected before preLaunch; Java 8 is rejected by bootstrap. Two extracted-package production launches in Chinese/space paths render 30 real menu frames and initialize audio; the second verifies bundled Java precedence. Separate API + ARC logs cover two menu launches. Busy-instance rejection, changed-core hashes, missing installation and standalone template compilation using UTF-8 local paths pass. Six adversarial distribution scan tests pass. All runtime scenarios preserve all 5,325 installation file contents. Evidence: workspace 99-研究工具/Acbric正式外部发行-dev25-20260926. Full campaign/media scenarios were not rerun this round; prior dev.24 results keep their original limits and do not imply other-device/Workshop/arbitrary-MOD acceptance.


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

Provider accepts the paired properties `acbric.external.install` / `acbric.external.instance` and strictly uses that installation. Missing/invalid paths never fall back to the legacy `libs` search. In dev.25 normal launch calls game `Main` and requires the selected distribution core. Missing core fails with `CORE_REQUIRED`; a resolved core version/origin mismatch is rejected before preLaunch. `ExternalRuntimeProbe` remains internal regression support. These properties/test entrypoints are neither public MOD APIs nor a security boundary. Do not distribute bypass commands as player launchers.

The real Knot prototype reads the installation's A/B and `lib` directly. Fabric `gameDir` points to the instance. The path Mixin takes effect before API preLaunch: `AGame.getStaticGameDirectory()` is the installation; `AGame.getGameDirectory()` is instance `userdata/`. Since dev.28 the production entrypoint directs Java and native MODs to `mods` inside the framework, beside Setup; configuration/framework logs remain in the instance. Internal legacy-layout probes may retain instance MOD paths. Legacy mode retains native paths. Public methods including `AirshipsPaths.staticDataDir()` retain their instance-side semantics; no read-only installation resource API is added yet.

## Settings and output isolation

Before initializing game classes, the external Provider reads installation `launch_settings.json`, overlays instance `config/launch-settings.json`, and atomically writes the effective settings to instance `.fabric/acbric/launch-settings.json`. The native initializer reads that generated file. Omitted fields retain installation values; this is a top-level overlay, not a recursive merge. Missing files mean no overrides; malformed JSON, duplicate keys, linked paths, excessive nesting or files larger than 1 MiB abort instead of silently reverting. Original files and a previous generated file remain intact on parse failure. Edit the instance config and restart; do not edit the generated copy.

Two output fields are always forced, even if the installation or instance config requests somewhere else:

| Native field | Effective location |
|---|---|
| `customDataDirectoryLocation` | Instance `userdata/` |
| `customGIFSaveDirectoryLocation` | Instance `userdata/gifs/` |

The GIF entry checks the instance target again before export. If unavailable it fails before the native Desktop/home/installation fallback. Developer checksum output and randomized userdata paths are disabled in external mode. This does not sandbox arbitrary third-party MOD code. Actual GIF rendering/export has not been exercised by the automated probe.

## Texture cache

External mode intercepts the common native image-file loader and skips native `.tex` migration. It mirrors only the requested image and related image/cache candidates under instance `cache/game-textures/v1/<content-key>/`; it does not copy the full asset tree. The native file reader and raw-cache generator then operate on that mirror. Source ordering remains with the original MOD/DLC/base lookup. Generated and nested images use the same interception; Heroes definitions/textures are covered below, not complete hero gameplay or arbitrary combinations.

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

### Real campaign creation, save/load and exit

```powershell
.\gradlew.bat build preflightTools
python tools/test_external_menu.py --campaign --tag campaign-api --game-dir "D:/Games/Airships Conquer the Skies" --java-home "D:/Java/jdk-21"
python tools/test_external_menu.py --campaign --arc-jar "D:/Mods/ARC-Overhaul-0.1.0-dev.3.jar" --tag campaign-arc --game-dir "D:/Games/Airships Conquer the Skies" --java-home "D:/Java/jdk-21"
```

Uses the Windows/JDK 21 write/network guard described above. Output is `build/external-campaign/<tag>/`; exactly two separate processes run, each with a 240-second limit. The optional ARC argument reads only the supplied JAR, requires no ARC source, and adds nothing to default distributions. Tests create a fresh instance and never use player saves.

The real Main menu leads to conquest setup; native `startGame` runs the full WorldGenScreen pipeline and the strategic map renders 30 frames. The single-player scenario uses VERY_SMALL, seed 89412 and hero generation disabled. Map, roads, fleets and defences are not replaced. Native SaveGameMission saves and ExitScreen exits normally; a new process loads through OpenGameMission. Assertions compare world ID, road/water digests, cities/towns, empire balances, and fleet/defence ownership and name lists. Asset checks are not a full ship serialization byte comparison and do not cover combat.

CREATED/LOADED/EXITED counts and MOD campaign data are asserted. ARC starts human players with 3 cities, 3 towns and 12345 cash; AI settlement counts stay native. Before saving, 123 is deducted. Before restart, local candidate settings change to 1 city, 0 towns and 999 cash; the old save must retain 3+3 settlements and 12222 cash without another grant. This local offline scenario does not establish complete DLC hero gameplay, extreme starting options or multiplayer acceptance.

Validation after dev.24 commit `42426a0`: API-only 16+15=31 and API + ARC dev.3 17+15=32, totaling 63 campaign assertions plus 3 preLaunch path assertions per process. All four processes render 30 strategic-map frames and exit natively. Each scenario leaves all 5,325 installation file contents unchanged; each instance has 200 raw cache files. The 924 standard checks pass, with two existing symlink permission skips. Product JARs are unchanged, API remains dev.24, and these tests/docs are uncommitted. Twelve relevant 1.2.15.3 classes match the previous decompiled input byte for byte. Java network-denial messages are expected test isolation; networking was not accepted.

### DLC, native MOD and GIF tests

```powershell
.\gradlew.bat build preflightTools
python tools/test_external_menu.py --media --tag media-check --game-dir "D:/Games/Airships Conquer the Skies" --java-home "D:/Java/jdk-21"
```

Requires Heroes and Villains DLC already present in the selected installation and the Windows/JDK 21 isolation conditions above. Output is `build/external-media/<tag>/`. By default two processes cover initial launch and cache restart, each limited to 240 seconds. `--runs 1` runs once; `--media-part gif` isolates replay/GIF diagnostics. Cannot combine with `--campaign`. A game window and audio may appear briefly.

The test creates a minimal native MOD inside the instance, with English/Chinese metadata, a derived cannon and pixel comparison textures. Native reload covers DLC+MOD, MOD only, neither, and DLC only: definitions, texture override precedence, edited texture reload and restoration of the original DLC image. This MOD is a fixture, not acceptance of existing overhauls such as SteamProgress, and is absent from framework distributions.

A short two-ship scene uses vanilla default designs and native simulation to create a recording. Native PlaybackIntent, GPU capture and GIF encoder produce normal and half-size slow-motion exports. Checks cover multi-frame decoding, dimensions and destination; repeated exports across restart must preserve previous file contents. A file temporarily occupying the instance GIF directory tests rejection and is then restored; desktop/installation paths are not modified. Render callbacks only mark checkpoints; actions run at the next native input boundary.

`--strict-gl` enables strict LWJGL checks and aborts on GL errors even before a file operation fails. Normal media tests use the game's usual non-strict setting while retaining the first error stack and error count on drivers supporting KHR_debug. They never clear GL errors to fake a pass. Such diagnostics produce summary status `PASS_WITH_NATIVE_GL_ERRORS`, distinct from a clean `PASS`.

For isolation, add `--media-part gif --legacy-control --strict-gl --runs 1`: copy local game resources/libraries into a fresh test directory and run the same short replay in the legacy framework layout, with external path/cache adapters inactive. This optional control consumes about 1.6 GB locally; do not distribute the copy. The test entry preloads the copied OpenAL DLL to exclude the known Unicode-path audio issue; product code is unchanged.

Historical media acceptance before the formal entrypoint (2026-09-26): dev.24 baseline `42426a0` is committed, not pushed; subsequent campaign/media tests and documentation are uncommitted. DLC/native MOD reload and real GIF tests pass 35 functional assertions per process (70 total): four GIFs, normal 960×640/15 frames and half-size slow motion 480×320/30 frames, without overwriting older exports. All 5,325 installation file contents remain unchanged; 924 standard checks pass (two existing permission skips). However, each process records 454,032 repeated native GL errors: status PASS_WITH_NATIVE_GL_ERRORS. A strict legacy-layout control with external adapters inactive reproduces the same terrain-rendering error (2,268 in the first frame). This is not clean graphics acceptance; no rendering fix or error suppression was added. On 2026-09-26 the user deferred this low-priority issue and removed it as a blocker for the installation refactor. It reproduces in the legacy layout, but there is no vanilla control without Acbric, so attribution to the framework or game remains unconfirmed. Normal external startup returned EXTERNAL_NOT_READY at that point; dev.25 replaces this restriction with the formal entrypoint and required-core checks. Product JARs are identical to dev.24; ARC source and player runtimes are unchanged. Legacy distZip is still not a clean release.

### Known issue: terrain-rendering GL error (deferred)

- **Decision (2026-09-26):** The user considers the affected feature infrequently used and deferred the fix. This is not a blocker for the installation refactor and does not change the result into clean graphics acceptance.
- **Symptoms and scope:** Terrain rendering during GIF replay validation produces GL_INVALID_OPERATION (1282), first traced through `ShaderProgram.bind → Appearance.drawBevelled → LandFormation.drawNonSoil`. Non-strict mode completes exports; strict mode aborts. Impact is not proven to be limited to GIF export.
- **Attribution limit:** Legacy-layout Acbric with external adapters inactive also reproduces the error, ruling out an external-adapter-only regression. No vanilla control without Acbric has been run; framework versus game attribution remains unconfirmed.
- **Follow-up:** Investigate shader binding within a `glBegin/glEnd` batch after adding a vanilla control. Retain the reproduction commands above, first stack, counters and local evidence in `99-研究工具/Acbric外部媒体验收-20260926`. Do not suppress errors or rewrite historical results.

### Write-path review and remaining acceptance

| Path family | Reviewed handling / current limit |
|---|---|
| Base/DLC checksum files | Read from installation; external `doWritechecksum=false` blocks development writes. Base and heroes checksum loading observed in the menu test. |
| Default designs, settings, log, recording cleanup | `AGame` copies/overlays into userdata; settings/log/recording paths use that root. Real initialization writes stay in the test instance. |
| Image/raw caches | Common loader uses instance mirrors; original `.tex` migration is skipped. Cold/warm menu launches pass. |
| MOD derived images/fragments | Generated under the source MOD directory; normal instance MODs are writable there. Linked/custom external MOD folders and Workshop remain unaccepted. |
| Missions/monsters | Static assets are read; backend locks protect static missions and monsters. User missions use userdata. No mission-editor workflow acceptance yet. |
| GIF and manual exports | GIF guard, normal/half-size slow export and repeated naming are tested; the native graphics diagnostic finding above remains open. User-selected export destinations are a separate explicit operation. |
| Offline author tools | Not invoked by normal Main. Their hard-coded development output paths are not a supported framework workflow. |

Review compares relevant 1.2.15.3 classes with the existing 1.2.15.2 decompilation; changed classes were checked separately. This is targeted source/bytecode review plus runtime write observation, not a proof covering arbitrary MOD code or every game branch.

The campaign and media functional scenarios above pass, but native terrain rendering fails strict GL checks. By user decision this remains a deferred known issue and does not block the installation refactor. dev.25 implements the formal entrypoint and clean distribution/templates; dev.26 adds the basic instance wizard, with framework update/uninstall to follow; automatic legacy-data migration has been removed from the plan. The current legacy `distZip` still contains local game dependencies and **must not be shared as a clean framework distribution**. `preflightTools` contains only framework launcher, explicit launch dependencies, script and documentation.
