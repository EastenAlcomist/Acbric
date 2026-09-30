# Documentation index

[中文](README.zh-CN.md)

Current development version: **0.3.5**. Players start with setup, MOD authors with the development workflow, and framework contributors with the build guide. Build commands assume the repository root as the working directory.

Topic documents define behavior; changelogs and historical snapshots record earlier states. A dev.N in a topic title usually identifies the feature introduction, not the current framework version. Game content, user configuration and test outputs are not documentation source.

## Usage and development

| Topic | English | 中文 |
| --- | --- | --- |
| Setup, updates and troubleshooting | [English](INSTALLER.md) | [中文](INSTALLER.zh-CN.md) |
| External launch and distribution | [English](EXTERNAL_START.md) | [中文](EXTERNAL_START.zh-CN.md) |
| Java MOD management | [English](MOD_MANAGEMENT.md) | [中文](MOD_MANAGEMENT.zh-CN.md) |
| Building and testing | [English](BUILDING.md) | [中文](BUILDING.zh-CN.md) |
| First MOD and capability map | [English](DEVELOPMENT.md) | [中文](DEVELOPMENT.zh-CN.md) |

## API and data

| Topic | English | 中文 |
| --- | --- | --- |
| API overview | [English](API.md) | [中文](API.zh-CN.md) |
| Event compatibility | [English](EVENTS.md) | Existing English contract |
| Event scopes | [English](EVENT_SCOPES.md) | [中文](EVENT_SCOPES.zh-CN.md) |
| Bundled native resources | [English](BUNDLED_RESOURCES.md) | Existing English contract |
| Local configuration | [English](CONFIG.md) | [中文](CONFIG.zh-CN.md) |
| Campaign storage | [English](CAMPAIGN_DATA.md) | [中文](CAMPAIGN_DATA.zh-CN.md) |
| Campaign lifecycle | [English](CAMPAIGN_LIFECYCLE.md) | [中文](CAMPAIGN_LIFECYCLE.zh-CN.md) |
| Shared UI | [English](UI.md) | [中文](UI.zh-CN.md) |
| Settings UI | [English](SETTINGS.md) | [中文](SETTINGS.zh-CN.md) |
| Console commands | [English](COMMANDS.md) | [中文](COMMANDS.zh-CN.md) |
| Developer tools | [English](DEVELOPER_TOOLS.md) | [中文](DEVELOPER_TOOLS.zh-CN.md) |
| Startup identity and diagnostics | [English](DIAGNOSTICS.md) | [中文](DIAGNOSTICS.zh-CN.md) |

## Networking and shared rules

| Topic | English | 中文 |
| --- | --- | --- |
| Code manifests | [English](CODE_MANIFEST.md) | [中文](CODE_MANIFEST.zh-CN.md) |
| Handshake protocol | [English](CODE_HANDSHAKE.md) | [中文](CODE_HANDSHAKE.zh-CN.md) |
| Lobby integration | [English](LOBBY_HANDSHAKE.md) | [中文](LOBBY_HANDSHAKE.zh-CN.md) |
| Shared rules | [English](SHARED_RULES.md) | [中文](SHARED_RULES.zh-CN.md) |
| Saved rule checks and conversion | [English](RULE_SAVE_MIGRATION.md) | [中文](RULE_SAVE_MIGRATION.zh-CN.md) |

## Implementation and history

| Topic | English | 中文 |
| --- | --- | --- |
| External installation mechanics and validation | [English](EXTERNAL_INSTALL.md) | [中文](EXTERNAL_INSTALL.zh-CN.md) |
| Changelog | [English](CHANGELOG.md) | [中文](CHANGELOG.zh-CN.md) |
| Previous README snapshot (historical) | [English](LEGACY_README.md) | [中文](LEGACY_README.zh-CN.md) |

## Maintaining documentation

Place new topics here, add English and Chinese versions, and list them above. Update existing contracts; put progress records in CHANGELOG instead of repeatedly prepending them to README. The template keeps its own bilingual README; player quick starts stay in src/dist. Distributions include docs. After moving files, check relative links, build copy rules and tool references.

[Historical development-entry notes](HISTORY_NOTES.md)
