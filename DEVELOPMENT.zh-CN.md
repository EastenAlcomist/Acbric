# 开发文档导航

dev.23 继续外部安装重构：[实例启动设置、用户输出与纹理缓存隔离](EXTERNAL_INSTALL.zh-CN.md)。924 项标准检查、1.2.15.3 的 36 项外部加载检查、旧布局两版 142 项 ARC 检查通过；源安装 5,325 文件内容未变。新模式仍等待真实菜单/图形/战役验收，尚未开放正常启动；旧 `distZip` 仍含本地游戏依赖，不可作为干净框架包分发。下方运行/构建方法继续适用于 legacy 布局。

从 dev.21 起，按 **Esc 下方、数字 1 左侧的 ~ 键**（物理键 `GRAVE`，可带 Shift）直接打开控制台并聚焦命令框，开窗按键不会混入文本。Ctrl/Alt/Meta 组合、窗口失焦、原生错误/帮助/聊天覆盖层以及已有 Acbric 窗口时不触发；先关闭其他 Acbric 窗口再使用快捷键。控制台打开后该键仍可输入普通字符，用 Esc/X/关闭退出。长按不会反复开窗。这是固定控制台入口，尚未提供通用改键 API。

[English](DEVELOPMENT.md) · 当前源码/API：**0.3.3-dev.23**，尚未发布稳定版。

从这里查找当前契约。README 负责安装构建，专题文档负责行为定义；变更记录和工作区带日期的研究文档是历史证据，不是另一套当前规范。新增接口同时维护中英文说明、最小示例、明确的失败边界和回归验证。

## 第一个 MOD 的流程

1. 按 [README](README.zh-CN.md) 准备自有游戏文件和 JDK 21，使用独立游戏/用户数据目录测试。
2. 执行 `gradlew.bat build syncModTemplateLibs`，将 `acbric-mod-template` 复制成独立项目，参照[模板说明](acbric-mod-template/README.md)。不要把游戏类和资源打进 MOD 源码发行包。
3. 设置唯一 MOD ID 和 `acbric` 入口。覆盖带上下文初始化方法时仍要实现无参方法，保留旧兼容协议，见 [API](API.zh-CN.md)。
4. 从下表选择所需服务，已有事件/API 能解决时优先使用。只有确实缺少能力时再引入依赖游戏版本的 Mixin。声明实际所用的最低 API 版本；模板 dev.10 下限不覆盖更新的 UI、设置和命令接口。
5. 构建独立 JAR，在测试副本 `game/mods/` 只保留一个版本，重启后进入 **Acbric API → 详情 → 开发者工具**。分别检查是否已加载、入口是否成功，需要时查看错误并导出诊断。
6. 测试中英文、不可用上下文、错误路径和注册清理，保留复现所需代码与日志。实机界面验收和联机验证不等于无界面检查。

## 能力与权威契约

| 需求 | 契约 | 最低版本 / 边界 |
|---|---|---|
| 初始化、上下文、路径、日志 | [API](API.zh-CN.md) | 按具体成员版本。 |
| 事件与重命名 UI 回调 | [事件](EVENTS.md) | dev.1；旧 `ONE_SHOT_*` 保留历史行为。 |
| 受管理订阅与事件异常 | [订阅范围](EVENT_SCOPES.zh-CN.md) | dev.6；异常仍然传播。 |
| 受管理原版资源 | [资源](BUNDLED_RESOURCES.md) | 保留用户修改，显式迁移。 |
| 本地 JSON 配置 | [配置](CONFIG.zh-CN.md) | dev.5；不自动广播。 |
| 战役 JSON 和恢复 | [存储](CAMPAIGN_DATA.zh-CN.md)、[生命周期](CAMPAIGN_LIFECYCLE.zh-CN.md) | dev.3 / dev.4；存储不是运行期同步。 |
| 共享规则声明 | [规则](SHARED_RULES.zh-CN.md) | dev.10；新战役固化，续局读取存档。 |
| 显式旧档转换 | [迁移](RULE_SAVE_MIGRATION.zh-CN.md) | dev.11；预览后另存。 |
| Java MOD 管理 | [管理](MOD_MANAGEMENT.zh-CN.md) | dev.12；重启生效，不热卸载。 |
| 公共界面、文本与清理 | [UI](UI.zh-CN.md) | dev.13 基础、dev.18 绑定/选项、dev.19 编辑/页脚、dev.20 提交。 |
| 草稿式设置界面 | [设置](SETTINGS.zh-CN.md) | dev.18；生效时机由 MOD 定义。 |
| 命令、帮助与补全 | [命令](COMMANDS.zh-CN.md) | dev.20；共享玩法执行预留。 |
| 游戏内工具、诊断快照 | [开发者工具](DEVELOPER_TOOLS.zh-CN.md) | dev.20；有界本地记录与导出。 |
| 启动身份与报告 | [诊断](DIAGNOSTICS.zh-CN.md) | 从 dev.2 开始；文件布局属内部格式。 |
| 代码身份和大厅协议 | [清单](CODE_MANIFEST.zh-CN.md)、[握手](CODE_HANDSHAKE.zh-CN.md)、[大厅](LOBBY_HANDSHAKE.zh-CN.md) | dev.7–9；不提供身份认证或状态复制。 |

公开包定义 MOD 契约；`impl`、`mixin`、`UiRuntime`、`DeveloperConsoleUi` 即使 Java 可见性是 public，也属于内部适配器。诊断记录 API 公开，JSON/文件布局仍是内部格式。已有公开签名和约定事件时序须保持兼容，另行达成的变更除外。

## 维护者验证流程

执行 `gradlew.bat build` 运行隔离回归；准备依赖后 `distZip` 组装本地发行目录，不代表可公开分发所含游戏内容。独立测试 MOD 不放入框架 source set 或默认发行包。用实际 API JAR 编译文档示例，对比旧版公开二进制签名，再通过真实 Fabric/Mixin 在两套维护的游戏版本验证新增接入。录制绘制器不能代替 Windows 剪贴板、输入法、切屏或 OpenGL 验收。

新增命令先把行为放入可复用服务，再提供双语声明和可注销注册；执行时重新查询运行状态。首版内置控制台只查询诊断和本地导出，后续配置/存档命令必须复用已有校验和同步契约。

历史变更：[中文](CHANGELOG.zh-CN.md) / [English](CHANGELOG.md)。
