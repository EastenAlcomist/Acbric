# 开发文档导航

[English](DEVELOPMENT.md) · 当前源码/API：**0.3.8**。

## 第一个 MOD 的流程

1. 按[构建指南](BUILDING.zh-CN.md)准备自有游戏文件和 JDK 21 或更高版本，使用独立游戏/用户数据目录测试。
2. 将 `acbric-mod-template` 复制成独立项目，按[模板说明](../acbric-mod-template/README.zh-CN.md)配置 `local.properties`。
3. 设置唯一 MOD ID 和 `acbric` 入口。见 [API](API.zh-CN.md)。
4. 从下表选择所需服务，优先使用已有事件/API。只有确实缺少能力时再引入依赖游戏版本的 Mixin，并声明实际所用的最低 API 版本。
5. 构建独立 JAR，在已配置的外部框架 `Acbric/mods/` 只保留一个版本，重启后进入 **Acbric API → 详情 → 开发者工具**。分别检查是否已加载、入口是否成功，需要时查看错误并导出诊断。
6. 通过 Acbric 启动游戏，检查实际界面、失败路径和中英文显示。

## 能力与权威契约

| 需求 | 契约 | 最低版本 / 边界 |
|---|---|---|
| 初始化、上下文、路径、日志 | [API](API.zh-CN.md) | 以当前 API 手册为准。 |
| 事件与重命名 UI 回调 | [事件](EVENTS.md) | 旧 `ONE_SHOT_*` 保留兼容行为。 |
| 受管理订阅与事件异常 | [订阅范围](EVENT_SCOPES.zh-CN.md) | 异常仍然传播。 |
| 受管理原版资源 | [资源](BUNDLED_RESOURCES.md) | 保留用户修改，显式迁移。 |
| 本地 JSON 配置 | [配置](CONFIG.zh-CN.md) | 不自动广播。 |
| 战役 JSON 和恢复 | [存储](CAMPAIGN_DATA.zh-CN.md)、[生命周期](CAMPAIGN_LIFECYCLE.zh-CN.md) | 存储不是运行期同步。 |
| 共享规则声明 | [规则](SHARED_RULES.zh-CN.md) | 新战役固化，续局读取存档。 |
| 显式旧档转换 | [迁移](RULE_SAVE_MIGRATION.zh-CN.md) | 预览后另存。 |
| Java MOD 管理 | [管理](MOD_MANAGEMENT.zh-CN.md) | 重启生效，不热卸载。 |
| 公共界面、文本与清理 | [UI](UI.zh-CN.md) | 组件、输入、窗口生命周期与缩放。 |
| 战斗命令面板控件 | [命令面板 UI](COMMAND_PANEL_UI.zh-CN.md) | 原生控件、锚点与布局；不同步玩法。 |
| 草稿式设置界面 | [设置](SETTINGS.zh-CN.md) | 生效时机由 MOD 定义。 |
| 命令、帮助与补全 | [命令](COMMANDS.zh-CN.md) | 共享玩法执行尚未开放。 |
| 游戏内工具、诊断快照 | [开发者工具](DEVELOPER_TOOLS.zh-CN.md) | 有界本地记录与导出。 |
| 启动身份与报告 | [诊断](DIAGNOSTICS.zh-CN.md) | 文件布局属内部格式。 |
| 代码身份和大厅协议 | [清单](CODE_MANIFEST.zh-CN.md)、[握手](CODE_HANDSHAKE.zh-CN.md)、[大厅](LOBBY_HANDSHAKE.zh-CN.md) | 不提供身份认证或状态复制。 |

公开包定义 MOD 契约；`impl`、`mixin`、`UiRuntime`、`DeveloperConsoleUi` 即使 Java 可见性是 public，也属于内部适配器。诊断记录 API 公开，JSON/文件布局仍是内部格式。已有公开签名和约定事件时序须保持兼容，另行达成的变更除外。

## 维护者检查

发布前运行 `build full`，并用 `externalDistZip` 生成默认携带 Java 21 runtime、且不含游戏内容的框架包；精简包使用 `-PbundleRuntime=false`。独立测试 MOD 不进入框架 source set 或默认发行包。涉及 Mixin 的改动必须核对真实游戏字节码和加载结果；录制绘制器不能替代输入法、窗口焦点或 OpenGL 实机检查。

历史变更：[中文](CHANGELOG.zh-CN.md) / [English](CHANGELOG.md)。
