# Acbric Mod Template

[中文](README.zh-CN.md)

Standalone Gradle project using JDK 21. Copy `local.properties.example` to `local.properties`, then set your local `gameInstallDir`, external distribution `frameworkDir`, and target `instanceDir`, using forward slashes. Command-line `-P` overrides these values.

```powershell
.\gradlew.bat build
.\gradlew.bat installMod
```

`build` references local game A/B, game libraries, framework launch dependencies and API without including them in the MOD JAR. `installMod` requires an explicit target and copies output to `<frameworkDir>/mods`. Do not share game files, `libs/` or private `local.properties` with the template.

Legacy local `libs/` remains a compile fallback. `syncModTemplateLibs` is for local development only and is no longer part of external distribution packaging. Do not distribute its copied dependencies.

## UI event version

This template targets the unreleased Acbric API `0.3.3-dev.10` or newer and Java 21.
Sync its dependencies from the matching framework checkout before compiling. Its
`RENAME_SHIP_AFTER_TICK` example logs once after a panel tick; it does not report
rename confirmation. The old `ONE_SHOT_*` names are deprecated compatibility hooks.
When copied elsewhere, keep this API minimum in `fabric.mod.json`.

See the framework [API guide](../API.md) ([中文](../API.zh-CN.md)) and [change record](../CHANGELOG.md). After copying the template elsewhere, consult these documents on the upstream dev branch.

## Campaign data example

`CampaignDataExample.prepare(context, worldMap)` demonstrates initialization and schema migration. It is not invoked by the template: call it from your mod only after obtaining the actual map, on the simulation thread, with matching execution on peers. Obtain a new handle when the map is replaced. Data writes do not broadcast messages. See [campaign data](../CAMPAIGN_DATA.md).

## Managed subscriptions (dev.6)

The template now requires API >=0.3.3-dev.10 and registers through `context.eventScope("application")`. One-shot listeners leave the scope when consumed. Keep application scopes across campaigns; do not close them when initialization returns. Close local scopes explicitly. Initialization failure cleanup is demonstrated. See the framework EVENT_SCOPES.md.

## Shared rules example (dev.10)

Optionally call `SharedRulesExample.declare(context, damagePercent)` once at entrypoint initialization. Gameplay reads `multiplier(handle, world)`; explicit config reload may call `changeNextCampaign` to update candidates. The default entrypoint does not enable this example or change damage. Read [shared rules](../SHARED_RULES.md), especially the rule-missing old-save restrictions, before adopting it.

Shared UI tools require API dev.13 or newer: see [UI.md](../UI.md). Raise the template dependency minimum only when using these APIs.

Optional settings forms require API >= dev.18; see SETTINGS.md. The template minimum remains unchanged unless using this feature.
