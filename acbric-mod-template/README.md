# Acbric Mod Template

[中文](README.zh-CN.md)

Standalone Gradle project requiring JDK 21 or newer. Copy `local.properties.example` to `local.properties`, then set your local `gameInstallDir`, external distribution `frameworkDir`, and target `instanceDir`, using forward slashes. Command-line `-P` overrides these values.

```powershell
.\gradlew.bat build
.\gradlew.bat installMod
```

`build` references local game A/B, game libraries, framework launch dependencies and API without including them in the MOD JAR. `installMod` copies output to `<frameworkDir>/mods`. Game files, private `local.properties` and local dependencies must not be included in a template copy or distribution.

Legacy local `libs/` remains a compile fallback; `syncModTemplateLibs` is for local development only.

## Localized mod-list description

The top-level `description` in `fabric.mod.json` remains the compatibility
fallback. Acbric reads the optional localized values first:

```json
"custom": {
  "acbric": {
    "description": {
      "en": "English description.",
      "chi": "Chinese description."
    }
  }
}
```

The template expands `modDescriptionEnglish` and `modDescriptionChinese` into
the `en` and `chi` keys; add native keys such as `de`, `fr`, `jpn`, `kor` and
`ru` as needed. Acbric selects the active game Locale, accepts `zh`/`zho` as
Chinese aliases and `ja`/`ko` as Japanese/Korean aliases, then falls back to
English and finally the top-level `description`. This applies to Fabric rows in
the native mod list and Acbric's details view without changing standard Fabric
metadata's single-string contract.

`gradle.properties` is decoded using Java Properties rules. Keep Chinese values
as `\uXXXX` escapes; `modDescriptionChinese` already does so in the template.
Replacing them with literal UTF-8 Chinese will corrupt the expanded value.

## API version and event example

This template targets Acbric API `0.3.8` or newer. Prepare compilation dependencies from the matching framework release or source. Its
`RENAME_SHIP_AFTER_TICK` example logs once after a panel tick; it does not report
rename confirmation. The old `ONE_SHOT_*` names are deprecated compatibility hooks.
When copied elsewhere, keep this API minimum in `fabric.mod.json`.

See the framework [API guide](../docs/API.md) ([中文](../docs/API.zh-CN.md)) and [change record](../docs/CHANGELOG.md).

## Campaign data example

`CampaignDataExample.prepare(context, worldMap)` demonstrates initialization and schema migration. It is not invoked by the template: call it from your mod only after obtaining the actual map, on the simulation thread, with matching execution on peers. Obtain a new handle when the map is replaced. Data writes do not broadcast messages. See [campaign data](../docs/CAMPAIGN_DATA.md).

## Managed subscriptions

The template registers through `context.eventScope("application")`. One-shot listeners leave the scope when consumed. Keep application scopes across campaigns; do not close them when initialization returns. Close local scopes explicitly. Initialization failure cleanup is demonstrated. See [event scopes](../docs/EVENT_SCOPES.md).

## Shared rules example

Optionally call `SharedRulesExample.declare(context, damagePercent)` once at entrypoint initialization. Gameplay reads `multiplier(handle, world)`; explicit config reload may call `changeNextCampaign` to update candidates. The default entrypoint does not enable this example or change damage. Read [shared rules](../docs/SHARED_RULES.md), especially the rule-missing old-save restrictions, before adopting it.
