# Acbric — 飞艇 Java MOD 框架

[English](README.md) · [文档总目录](docs/README.zh-CN.md)

Acbric 将 Fabric Loader 与《Airships: Conquer the Skies》连接起来，让 Java MOD 使用事件、配置、战役存储、公共 UI、控制台和 Mixin 扩展游戏，并整合原版 JSON MOD。

当前开发版为 **0.3.5.1**。版本以 [fabric.mod.json](src/apiMod/resources/fabric.mod.json) 为准；完整历史见[改动记录](docs/CHANGELOG.zh-CN.md)。

游戏兼容：**适配《Airships: Conquer the Skies》1.2.15.3**（含其 LWJGL3 引擎后端的引擎迁移 MOD）。编译基线也是 1.2.15.3（`libs/asplit-*.zip`，可用 `AGame.VERSION` 核对），运行期兼容 1.2.15.2 等旧引擎版本——代码不写死引擎输入类型，详见[改动记录](docs/CHANGELOG.zh-CN.md) 的 0.3.4 条目。

## 从这里开始

| 目的 | 入口 |
| --- | --- |
| 安装、启动、添加 MOD、更新与排错 | [玩家使用指南](docs/INSTALLER.zh-CN.md) |
| 制作第一个 Java MOD | [开发流程](docs/DEVELOPMENT.zh-CN.md)、[独立模板](acbric-mod-template/README.zh-CN.md) |
| 查询已有接口 | [API 手册](docs/API.zh-CN.md)、[专题目录](docs/README.zh-CN.md) |
| 编译、测试或修改框架 | [构建指南](docs/BUILDING.zh-CN.md)、[代理与维护指引](AGENTS.md) |

## 玩家安装

完整解压带 Java 的 `Acbric-external-<version>.zip`，打开其中的 `Acbric.exe`。首次自动查找 Steam 游戏，确认后点击“使用此游戏”；未找到时手动选择游戏文件夹。以后仍打开同一个 EXE，点击“开始游戏”。Java MOD 的 JAR 和原版 MOD 文件夹都放在同层 `mods`。详细步骤见[使用指南](docs/INSTALLER.zh-CN.md)。

游戏使用已有的独立安装；实例保存存档、设置、缓存和日志。仓库及外部发行包不含游戏代码、资源或第三方功能 MOD。

## 框架开发

准备 JDK 21 和自有游戏的本地编译依赖，按[构建指南](docs/BUILDING.zh-CN.md)配置环境。以下命令均在仓库根目录执行：

```powershell
.\build.cmd       # 编译
.\test.cmd all    # 完整回归
.\test.cmd mods   # 按修改范围选择套件
```

对外打包使用 `externalDistZip`，带 Java 21 时增加 `-PbundleRuntime`；具体命令见[外部发行指南](docs/EXTERNAL_START.zh-CN.md)。旧 `distZip` 会包含本地游戏内容，仅供旧布局研究，不用于公开分发。

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
| `build/`、`.gradle/` | 本地构建产物、验证记录与缓存，不纳入 Git |
| `libs/`、`game/` | 自备编译依赖与旧布局测试资料，不纳入 Git |

## 验证与边界

本版新增玩家启动流程、双语界面及诊断导出检查；保留安装占用重试、更新保护和已有 API 行为。验证范围见[改动记录](docs/CHANGELOG.zh-CN.md)。

Java MOD 启停需要重启；写入战役扩展数据不等于自动联机同步；代码一致性检查不等于反作弊或任意 MOD 组合兼容。已知原生 GL 报错暂缓处理，详见[外部安装研究](docs/EXTERNAL_INSTALL.zh-CN.md)。

Acbric 使用 [MIT 许可证](LICENSE)。游戏及第三方组件的权利和许可证分别归其作者所有。
