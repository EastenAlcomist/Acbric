# 文档总目录

[English](README.md)

当前开发版：**0.3.5**。玩家从安装指南开始，MOD 作者从开发流程开始，框架贡献者先读构建指南。文档中的构建命令以仓库根目录为工作目录。

专题文档定义行为，CHANGELOG 和历史快照记录当时状态。文档标题中的 dev.N 通常表示功能引入版本，不表示当前框架仍停留在该版本。游戏代码、资源、用户配置和测试产物不纳入文档仓库。

## 使用与开发入门

| 主题 | English | 中文 |
| --- | --- | --- |
| 安装、更新与排错 | [English](INSTALLER.md) | [中文](INSTALLER.zh-CN.md) |
| 外部启动与发行 | [English](EXTERNAL_START.md) | [中文](EXTERNAL_START.zh-CN.md) |
| Java MOD 启停 | [English](MOD_MANAGEMENT.md) | [中文](MOD_MANAGEMENT.zh-CN.md) |
| 构建与测试 | [English](BUILDING.md) | [中文](BUILDING.zh-CN.md) |
| 第一个 MOD 与能力导航 | [English](DEVELOPMENT.md) | [中文](DEVELOPMENT.zh-CN.md) |

## API 与数据

| 主题 | English | 中文 |
| --- | --- | --- |
| API 总览 | [English](API.md) | [中文](API.zh-CN.md) |
| 事件兼容约定 | [English](EVENTS.md) | 现有英文契约 |
| 订阅范围 | [English](EVENT_SCOPES.md) | [中文](EVENT_SCOPES.zh-CN.md) |
| 原版资源包管理 | [English](BUNDLED_RESOURCES.md) | 现有英文契约 |
| 本地配置 | [English](CONFIG.md) | [中文](CONFIG.zh-CN.md) |
| 战役存储 | [English](CAMPAIGN_DATA.md) | [中文](CAMPAIGN_DATA.zh-CN.md) |
| 战役生命周期 | [English](CAMPAIGN_LIFECYCLE.md) | [中文](CAMPAIGN_LIFECYCLE.zh-CN.md) |
| 公共 UI | [English](UI.md) | [中文](UI.zh-CN.md) |
| 设置界面 | [English](SETTINGS.md) | [中文](SETTINGS.zh-CN.md) |
| 控制台命令 | [English](COMMANDS.md) | [中文](COMMANDS.zh-CN.md) |
| 开发者工具 | [English](DEVELOPER_TOOLS.md) | [中文](DEVELOPER_TOOLS.zh-CN.md) |
| 启动身份与诊断 | [English](DIAGNOSTICS.md) | [中文](DIAGNOSTICS.zh-CN.md) |

## 联机与共享规则

| 主题 | English | 中文 |
| --- | --- | --- |
| 代码清单 | [English](CODE_MANIFEST.md) | [中文](CODE_MANIFEST.zh-CN.md) |
| 握手协议 | [English](CODE_HANDSHAKE.md) | [中文](CODE_HANDSHAKE.zh-CN.md) |
| 大厅接入 | [English](LOBBY_HANDSHAKE.md) | [中文](LOBBY_HANDSHAKE.zh-CN.md) |
| 共享规则 | [English](SHARED_RULES.md) | [中文](SHARED_RULES.zh-CN.md) |
| 规则预检查与旧档转换 | [English](RULE_SAVE_MIGRATION.md) | [中文](RULE_SAVE_MIGRATION.zh-CN.md) |

## 实现与历史

| 主题 | English | 中文 |
| --- | --- | --- |
| 外部安装机制与验证边界 | [English](EXTERNAL_INSTALL.md) | [中文](EXTERNAL_INSTALL.zh-CN.md) |
| 版本改动记录 | [English](CHANGELOG.md) | [中文](CHANGELOG.zh-CN.md) |
| 整理前 README 快照（历史） | [English](LEGACY_README.md) | [中文](LEGACY_README.zh-CN.md) |

## 维护文档

新增专题放入本目录，同时提供英文和中文并加入此表。更新已有规范，不在 README 前反复堆积阶段记录；阶段成果写入 CHANGELOG。模板保留自己的双语 README，面向玩家的简明流程保留在 src/dist，发行包会携带 docs。目录移动后检查相对链接、构建复制清单和工具引用。

[开发入口历史笔记](HISTORY_NOTES.md)
