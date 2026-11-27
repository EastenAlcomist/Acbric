# Acbric — 飞艇 Java MOD 框架

[English](README.md) · [文档总目录](docs/README.zh-CN.md)

Acbric 将 Fabric Loader 与《Airships: Conquer the Skies》连接起来，让 Java MOD 通过 API、事件和 Mixin 扩展游戏，同时兼容原版 JSON MOD。

当前开发版为 **0.3.8**。版本以 [fabric.mod.json](src/apiMod/resources/fabric.mod.json) 为准；完整历史见[改动记录](docs/CHANGELOG.zh-CN.md)。

当前编译基线为《Airships: Conquer the Skies》**1.2.15.3**，并兼容 1.2.15.2；其他游戏版本不保证可用。

## 从这里开始

| 目的 | 入口 |
| --- | --- |
| 安装、启动、添加 MOD、更新与排错 | [玩家使用指南](docs/INSTALLER.zh-CN.md) |
| 制作第一个 Java MOD | [开发流程](docs/DEVELOPMENT.zh-CN.md)、[独立模板](acbric-mod-template/README.zh-CN.md) |
| 查询已有接口 | [API 手册](docs/API.zh-CN.md)、[专题目录](docs/README.zh-CN.md) |
| 编译、测试或修改框架 | [构建指南](docs/BUILDING.zh-CN.md)、[代理与维护指引](AGENTS.md) |

## 玩家安装

完整解压带 Java 的 `Acbric-external-<version>.zip`，打开其中的 `Acbric.exe`。首次自动查找 Steam 游戏，确认后点击“使用此游戏”；未找到时手动选择游戏文件夹。以后仍打开同一个 EXE。Java MOD 的 JAR 始终放在 Acbric 同层 `mods`；原生 MOD 使用当前数据环境的 `mods`。详细步骤见[使用指南](docs/INSTALLER.zh-CN.md)。

游戏本体需单独安装。实例保存该配置下的设置、缓存和日志；默认模式直接使用原版存档与 MOD。仓库及外部发行包不含游戏代码、资源或第三方功能 MOD。

## 框架开发

准备 JDK 21 或更高版本和自有游戏的本地编译依赖，按[构建指南](docs/BUILDING.zh-CN.md)配置环境。以下命令均在仓库根目录执行：

```powershell
.\build.cmd       # 编译
.\test.cmd all    # 完整回归
.\test.cmd mods   # 按修改范围选择套件
```

对外打包使用 `externalDistZip`，默认携带 Java 21 runtime；需要精简包时增加 `-PbundleRuntime=false`，具体命令见[外部发行指南](docs/EXTERNAL_START.zh-CN.md)。

## 目录

| 目录 | 内容 |
| --- | --- |
| `docs/` | 中英文使用、开发、API、机制和历史文档 |
| `src/main/`、`src/shared/` | 启动、安装与两层共用协议 |
| `src/apiMod/` | 核心 API、游戏适配和 Mixin |
| `src/regressionTest/`、`tools/` | 隔离回归、真实运行夹具与验收工具 |
| `src/dist/` | 发行入口脚本与玩家简明说明 |
| `acbric-mod-template/` | 可复制为独立项目的 MOD 模板 |
| `gradle/` | 构建包装器 |
| `build/`、`.gradle/` | 本地构建产物与缓存，不纳入 Git |
| `libs/`、`game/` | 自备编译依赖与旧布局测试资料，不纳入 Git |


Acbric 使用 [MIT 许可证](LICENSE)。游戏及第三方组件的权利和许可证分别归其作者所有。
