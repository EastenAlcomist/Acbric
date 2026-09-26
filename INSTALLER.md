# Acbric setup and launch (dev.28)

[中文](INSTALLER.zh-CN.md)

Extract `Acbric-external.zip` and open its `Acbric` folder. **Setup.cmd, Start Acbric.cmd and mods now sit together.**

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

## First setup

1. Double-click **Setup.cmd** and choose 中文 or English.
2. Select the complete game folder containing `Airships.json`.
3. Select a separate instance, defaulting to `Acbric/instances/default`, or another writable location. It stores saves, settings, caches and logs. Game and instance cannot contain each other; inside the framework only instances under `instances` are allowed.
4. Select Check folders and review the game, instance and MOD paths. Checking does not create the instance or change the launch binding.
5. Select Save & create launcher. **Start Acbric.cmd** in the same folder now launches this saved instance.

On subsequent runs, double-click **Start Acbric.cmd** without opening setup. With several instances, the root entry launches the **most recently saved instance**. To switch, select the desired instance in Setup, load, check and save. Existing instance-local launch entries still work.

## Install MODs

Select Open MOD folder or open **mods** beside Setup:

- Place Java MOD `.jar` files here.
- Place native MOD folders directly containing `info.json` here. Import `.amod` archives through the game's Install MOD action.
- The API loads from `core/acbric-api.jar` automatically; do not add another copy.

The dev.27 sibling mods folder, old instance `mods`/`userdata/mods`, and the original User folder are not scanned for local MODs. Close the game and manually copy wanted MODs to `Acbric/mods`; old files are not moved or deleted automatically. Instances share MOD files while retaining separate settings. Only one game session can use a shared MOD folder at a time.

## Update or relocate

After extracting a new package, run Setup from its new location, select the existing instance, Load existing instance, confirm the game path, check and save. Saves and settings remain intact. Relocate the whole Acbric folder, which now includes mods. If the game or an external instance moves, select its new path and save again.

Internal instance bindings use relative paths; external instances use absolute paths. After relocation, Setup must still update the framework path recorded in the instance. Before first setup, Start explains how to configure it. Missing instances and invalid bindings are diagnosed without guessing another instance.

A corrupt `.acbric-active-instance.json` is not silently overwritten. Back it up and move it aside, then load the existing instance in Setup and save to rebuild the binding. Modified/corrupt instance configuration or scripts are also preserved. Instance and MOD locks protect saving, and stale previews cannot replace another wizard's newer binding. Instance configuration and root binding are published atomically as separate files; if the last step fails, check and save again. Already saved instances are not deleted.

## Java and logs

The full package includes independent Java 21. For a lightweight package, set `JAVA_HOME` or run:

```powershell
.\setup.ps1 -JavaHome "C:/Java/jdk-21"
```

Java 8 is rejected. Launch logs are in instance `logs/acbric/launcher`, accessible through Open logs. Native logs are in `userdata/log.txt`. Setup errors report an `acbric-setup-*.log` in the system temporary directory.

Automated setup without a window or game launch:

```powershell
.\setup.ps1 -GameDir "C:/Games/Airships" -InstanceDir "C:/AcbricInstances/default" -Language en
```

Internal JSON files contain path data and are not public MOD APIs. Update/rollback, uninstall, automatic detection, legacy-data migration and automatic desktop shortcuts are not implemented. You may create a shortcut to Start Acbric.cmd; do not move the entry file by itself.

## Verification

```powershell
.\gradlew.bat build externalDistZip -PbundleRuntime -PexternalGameDir="C:/Games/Airships"
python tools/test_external_release.py --game-dir "C:/Games/Airships" --java-home "C:/Java/jdk-21" --tag rootentrycheck --installer
```

The harness executes the root CMD from an unrelated working directory, checking MOD discovery/installation, relocation/rebinding, lock rejection, invalid configuration and template installation targets. Add `--arc-jar` to include ARC. Native folder pickers, all DPI settings, full campaigns, multiplayer and Workshop are outside this acceptance scope.

dev.28 validation: 977 standard checks, 36 real external-loading checks and six adversarial scanner tests pass. The root CMD beside Setup launches ARC before and after relocation/rebinding, each rendering 30 menu frames with native audio. MOD discovery/installation, bundles, manager selection and template targets pass. Two script routing checks cover relative/absolute instances and Chinese/space/& paths. Invalid/missing bindings, missing instances, busy locks and relocation diagnostics are covered; all 5,325 source game files remain unchanged. Chinese/English panels inspected; full campaign/network/Workshop not rerun. Uncommitted/unpushed, player runtimes unchanged. Evidence: workspace 99-研究工具/Acbric同层启动与MOD目录-dev28-20260926.
