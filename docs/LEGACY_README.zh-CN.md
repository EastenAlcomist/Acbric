# 旧版 README 归档说明

[English](LEGACY_README.md) · [当前 README](../README.zh-CN.md)

本文替代 2026-09-27 文档整理前的根目录 README。旧页面中的版本号、测试数量、提交状态、证据路径和本地目录仅记录当时情况，不能作为当前指引。

旧开发布局把游戏文件放在仓库内：

- `libs/`：游戏 A/B、游戏库和 Fabric Loader，用于编译与旧布局启动。
- `game/`：`Airships.json`、静态数据、原生库和 MOD，用于 `startAirships`。
- `loader-libs/`：启动垫片、Fabric Loader、Mixin 和 ASM。

当前玩家入口使用外部发行包和 `Acbric.exe`，不再要求把游戏复制进仓库。开发构建、输入文件和测试命令见[构建指南](BUILDING.zh-CN.md)；旧布局 `distZip` 只作为内部研究工具，不对外分发。

当前架构、API 和发行方式以[文档总目录](README.zh-CN.md)、[API 手册](API.zh-CN.md)和[改动记录](CHANGELOG.zh-CN.md)为准。

Acbric 使用 MIT 许可证；游戏及第三方组件归各自权利人所有，不随仓库分发。
