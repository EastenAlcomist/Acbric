# Acbric Mod Template

Standalone Gradle template for Acbric mods. It builds from its own directory and
uses only the bundled `libs/` dependencies. See `README.zh-CN.md` for the full
Chinese guide.

Quick start:

```powershell
.\gradlew.bat build
.\gradlew.bat installMod
```

`build` does not depend on the main Acbric project. `installMod` copies the jar
to `gameDir/mods`; set `gameDir` in `gradle.properties` if the template is moved.

When working inside the main Acbric workspace, run `.\gradlew.bat syncModTemplateLibs`
from the workspace root to repopulate this template's `libs/` directory.

## UI event version

This template targets the unreleased Acbric API `0.3.3-dev.3` or newer and Java 21.
Sync its dependencies from the matching framework checkout before compiling. Its
`RENAME_SHIP_AFTER_TICK` example logs once after a panel tick; it does not report
rename confirmation. The old `ONE_SHOT_*` names are deprecated compatibility hooks.
When copied elsewhere, keep this API minimum in `fabric.mod.json`.

See the framework [API guide](../API.md) ([中文](../API.zh-CN.md)) and [change record](../CHANGELOG.md). After copying the template elsewhere, consult these documents on the upstream dev branch.

## Campaign data example

`CampaignDataExample.prepare(context, worldMap)` demonstrates initialization and schema migration. It is not invoked by the template: call it from your mod only after obtaining the actual map, on the simulation thread, with matching execution on peers. Obtain a new handle when the map is replaced. Data writes do not broadcast messages. See [campaign data](../CAMPAIGN_DATA.md).
