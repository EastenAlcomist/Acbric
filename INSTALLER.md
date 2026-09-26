# Acbric setup and launch (dev.29)

[中文](INSTALLER.zh-CN.md)

Extract `Acbric-external.zip` and open its `Acbric` folder. **Setup.cmd, Start Acbric.cmd and mods now sit together.**

```text
Acbric/
├─ Setup.cmd             配置 / Setup
├─ Start Acbric.cmd      启动 / Play
├─ Update Acbric.cmd     Update / restore
├─ 使用说明.txt          Chinese quick start
├─ QUICK_START.txt       English quick start
├─ mods/
│  ├─ arc-overhaul.jar
│  └─ native-mod/info.json
├─ core/acbric-api.jar
└─ instances/default/...
```

## First setup

1. Double-click **Setup.cmd** and choose 中文 or English.
2. Select Find game in Steam, or browse to the complete game folder containing `Airships.json`. Discovery reads Steam's registry and modern/legacy library lists, including other drives. Multiple valid installations prompt a choice; no result preserves the current path and asks for manual selection. This does not install the game or modify Steam.
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

## Local ZIP updates and restoration

Close the game and Setup. Open **Update Acbric.cmd**, select the existing Acbric folder, choose Yes and select a trusted new `Acbric-external.zip`. Review the target and version before confirming. Choose No to restore the framework from before the last update. Do not extract an update over the old directory.

For the first update from dev.28, extract the new release elsewhere, run its updater and select the old folder. Older releases without a recognized manifest require fresh extraction and setup. Use a Java-bundled update for an installation with bundled Java; a minimal package cannot remove its runtime.

Only manifest-owned, hash-matching framework files are replaced. MODs (including mods/README.md), instances, saves, settings and the default launch binding are preserved. Modified/missing framework files and unowned name collisions stop the operation. Checks cover ZIP paths, hashes, the core manifest and API version; they do not authenticate the publisher.

Files and transaction records are backed up before replacement. A write failure attempts automatic restoration. An interrupted operation blocks normal startup until the updater recovers it. Keep `.acbric-maintenance`; backups are not automatically removed. Do not relocate an interrupted installation. If the old release has no updater, or the local entry is unavailable after interruption, run the updater from a separately extracted new release and select the original target.

Restoration changes framework files only, not MODs, saves or their data formats. An older framework may not read data saved after updating. Additional user edits stop restoration. Preserve files and backups and read the temporary log path shown by the updater.

For automation, use `update.ps1 -TargetDir <folder> -PackagePath <ZIP> -CheckOnly`; omit `-CheckOnly` to apply, or use `-TargetDir <folder> -Restore` to restore. No automatic downloads or legacy-data imports occur.

## Relocation or a fresh extracted folder

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

Internal JSON and maintenance records are not public MOD APIs. Automatic online updates, uninstall, legacy-data migration and automatic desktop shortcuts are not implemented. You may create a shortcut to Start Acbric.cmd; do not move the entry file by itself.

## Verification

```powershell
.\gradlew.bat build externalDistZip -PbundleRuntime -PexternalGameDir="C:/Games/Airships"
python tools/test_external_release.py --game-dir "C:/Games/Airships" --java-home "C:/Java/jdk-21" --tag rootentrycheck --installer
python tools/test_maintenance.py
```

The harness executes the root CMD from an unrelated working directory, checking MOD discovery/installation, relocation/rebinding, lock rejection, invalid configuration and template installation targets. Add `--arc-jar` to include ARC. Native folder pickers, all DPI settings, full campaigns, multiplayer and Workshop are outside this acceptance scope.

dev.29 validation: 987 standard checks, 25 maintenance checks, six adversarial scanner tests and two root-entry routing cases pass. Actual Steam registry/library discovery finds the local H-drive installation; both language panels were inspected. A verified dev.28 ZIP was configured, upgraded, relocated/rebound and restored, with ARC present for three actual menu launches, each rendering 30 frames with native audio. Reapplying the update and template build/install pass. Player-file hashes are unchanged across update/restoration; all 5,325 source game files are unchanged. Reproduce with `--upgrade-from <dev.28.zip> --installer`. Native file pickers/update dialogs were not clicked automatically; full campaigns/network/Workshop were not rerun. Older evidence follows.

dev.28 validation: 977 standard checks, 36 real external-loading checks and six adversarial scanner tests pass. The root CMD beside Setup launches ARC before and after relocation/rebinding, each rendering 30 menu frames with native audio. MOD discovery/installation, bundles, manager selection and template targets pass. Two script routing checks cover relative/absolute instances and Chinese/space/& paths. Invalid/missing bindings, missing instances, busy locks and relocation diagnostics are covered; all 5,325 source game files remain unchanged. Chinese/English panels inspected; full campaign/network/Workshop not rerun. Uncommitted/unpushed, player runtimes unchanged. Evidence: workspace 99-研究工具/Acbric同层启动与MOD目录-dev28-20260926.
