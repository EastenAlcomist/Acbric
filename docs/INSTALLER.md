# Acbric player guide (dev.33)

[中文](INSTALLER.zh-CN.md)

## First use

1. Fully extract the distribution with bundled Java and open **Acbric.exe** in its `Acbric` folder. Install the game separately.
2. The launcher searches Steam automatically. Confirm the displayed location and select **Use this game**. Use **Choose game folder** if nothing was found or you want another installation.
3. Select **Start game** when ready. Open the same EXE next time; no repeated setup is needed.

The Chinese/English choice at the top right is saved. The default data folder is configured automatically; ordinary players do not need to manage an “instance”. Launcher actions are disabled while the game runs and restored after it closes.

```text
Acbric/
├─ Acbric.exe            One everyday entry
├─ mods/                 Put MODs here
├─ 使用说明.txt / QUICK_START.txt
├─ docs/                 Full documentation
├─ advanced/             Legacy setup, launch and maintenance tools
├─ core/, loader-libs/, runtime/ and supporting files
└─ instances/default/    Data created during first setup
```

There is one EXE **entry**, not a single-file framework. Keep the entire folder together; create an EXE shortcut for desktop access. Supporting PowerShell scripts do not need to be opened manually.

## Add MODs

Select **Open MOD folder**. Place Java `.jar` MODs or native folders directly containing `info.json` here. Import `.amod` through the game's Install MOD action. The API is included; do not add a duplicate API JAR. Restart after adding, removing or enabling/disabling Java MODs.

Old layouts/User folders are not automatically imported. Close the game before copying wanted MODs. Saves and game settings live in `instances/default/userdata`; MOD settings in `instances/default/config`. Multiple instances share the adjacent `mods` folder but keep separate settings and saves.

## Settings and maintenance

- **Change game location**: select a moved or different game, retaining the original data folder, then confirm.
- **Install update package**: close the game and choose a trusted `Acbric-external-<version>.zip` in Settings. The launcher exits before updating and reopens on success. Do not extract updates over the old directory.
- **Restore previous version**: restore framework files from before the last update. MODs, saves and settings are not rolled back; older frameworks may not read newer saved data.
- **Advanced settings**: choose a custom data folder or switch existing instances. Select the instance, load, check and save. Everyday use does not require this screen.

First upgrade from dev.32 or earlier: extract the new package elsewhere, run its `advanced/Update Acbric.cmd`, and select the original Acbric folder and new ZIP. The old updater does not recognize the EXE file manifest. Releases without a supported manifest require fresh setup; legacy data is not migrated automatically. This compatibility tool is for initial upgrades/recovery; later updates use the EXE.

Updates still verify ownership manifests, file hashes and core versions. Edited files, path conflicts and occupied files block replacement. Verification does not authenticate the publisher. Keep `.acbric-maintenance` backups. After interrupted updates the EXE offers recovery first. If the entry itself is missing or damaged, use maintenance tools from a separately extracted complete package and select the original folder. Do not relocate it before recovery.

## Getting help

Normal errors show a readable explanation and **Retry**, without a Java stack trace. Use **Help → Export diagnostics** to save a ZIP and send it to the developer yourself. Export requires confirmation: it contains the version, recent launcher/game-launch logs and potentially local paths, but no saves. Nothing is uploaded automatically. Each category contributes up to 10 logs, at most 1 MiB per log and 10 MiB total; large logs are truncated. Existing reports are not overwritten.

**Help → Technical details** displays the latest operation's exception only when requested. If Java is missing or the GUI cannot open, the EXE shows a short explanation and an Open diagnostics folder button.

Diagnostic locations:

- Launcher/maintenance logs: `%LOCALAPPDATA%/Acbric/launcher-logs`.
- Complete game launch output: `<instance>/logs/acbric/launcher/launch-*.log`.
- Native game log: `<instance>/userdata/log.txt`; provide separately if needed. Export does not collect userdata.
- Advanced setup failures also retain temporary `acbric-setup-*.log` files; ordinary setup exceptions go to launcher logs.

Damaged configuration is preserved rather than reset or replaced with another guessed save folder. Export diagnostics; do not create an empty instance.json. Initial saving retains atomic directory publication, occupancy retries and locking.

## Moving folders and limits

Move the whole Acbric folder, open the EXE and confirm the game to rebind the original instance. For moved external instances, explicitly select the original folder in Advanced settings. Internal instance bindings are relative; external bindings are absolute.

Currently Windows x64. Player packages include Java 21; minimal packages require a valid `JAVA_HOME`. The EXE is not code-signed. There is no online auto-update, automatic legacy import or uninstall wizard. Native GL issues, arbitrary MOD combinations and other devices require targeted acceptance.
