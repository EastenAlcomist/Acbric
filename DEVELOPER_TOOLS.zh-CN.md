# 开发者工具与控制台

[English](DEVELOPER_TOOLS.md) · 当前开发 API：`0.3.3-dev.21`。

入口是 **MOD 列表 → Acbric API → 详情 → 开发者工具 / 控制台**。功能集成在框架中，不需要示例 MOD。界面跟随游戏当前语言，支持英文和中文（`chi`、`zh`、`zho`），其他语言回退英文；切换语言后重新打开窗口。从 dev.21 起，按 **Esc 下方、数字 1 左侧的 ~ 键**（物理键 `GRAVE`，可带 Shift）直接打开控制台并聚焦命令框，开窗按键不会混入文本。Ctrl/Alt/Meta 组合、窗口失焦、原生错误/帮助/聊天覆盖层以及已有 Acbric 窗口时不触发；先关闭其他 Acbric 窗口再使用快捷键。控制台打开后该键仍可输入普通字符，用 Esc/X/关闭退出。长按不会反复开窗。这是固定控制台入口，尚未提供通用改键 API。

开发者工具展示游戏/API 版本、构建指纹、会话、启动阶段、MOD 入口状态和消息淘汰计数，刷新重新截取状态。加载代码、Acbric 入口成功、玩法兼容是不同状态；`NO_ACBRIC_ENTRYPOINT` 仅表示未声明该入口。MOD 启停仍须重启。使用 `acbric_api:mod <id>` 查看来源、下次启用状态和初始化详情。

## 内置命令

| 命令 | 影响范围 | 结果 |
|---|---|---|
| `acbric_api:help [command]` | READ_ONLY | 列出名称，或生成语法、说明、参数和上下文要求。 |
| `acbric_api:status` | READ_ONLY | 当前框架/游戏诊断摘要。 |
| `acbric_api:mods` | READ_ONLY | 已加载/已发现的 MOD 摘要。 |
| `acbric_api:mod <mod_id>` | READ_ONLY | 指定 MOD 的详情。 |
| `acbric_api:diagnostics export` | LOCAL | 异步请求诊断 ZIP，完成或失败写入消息。 |

五个命令均要求 `ACTIVE_SCREEN`。帮助和补全由命令声明生成。后续新增命令应调用已有服务，与其他界面/API 共用行为，不在控制台内部另写一套实现。注册契约见 [COMMANDS.zh-CN.md](COMMANDS.zh-CN.md)。

## 控制台操作

- 让命令框获得焦点，Enter 执行，上下箭头浏览最多 32 条历史。到达历史末尾后，下箭头恢复进入历史前的草稿。历史仅属于当前窗口，关闭后清空。
- Tab 补全名称或声明的选项/布尔值，多候选弹出选择列表；Shift+Tab 保留普通焦点切换。也可使用对应按钮。
- 按完整 MOD ID（空为全部）和级别筛选。较早/较新/最新每页浏览 6 条。预览每条截到 1000 单元；详情展示保留的消息和堆栈，复制本页复制完整保留内容。剪贴板不可用时显示提示，仍可导出。
- 进程级缓冲最多保留 512 条，归属/消息/详情合计最多 524288 个 UTF-16 单元，每条消息和详情各不超过 4096。超限淘汰最早记录，状态页显示淘汰计数。它不是完整持久化日志。
- 收集来源：`AcbricLogger`、受管理事件失败（保留原限流）、UI 回调失败、配套资源安装错误/冲突、命令输入/结果、导出结果。不截获任意 `System.out`、全部 Fabric 日志或游戏完整日志；原生游戏异常仍查看游戏用户数据目录的 `log.txt`。
- 历史文本不追溯翻译，ID、堆栈、路径和 MOD 自己输出的文字保留原文。Windows 输入法、真实系统剪贴板、切屏仍需交互实测。

## 公开诊断接口

`DeveloperDiagnostics.snapshot()` 在游戏线程返回不可变 `DiagnosticSnapshot`。MOD 行包含 ID/名称/版本、当前加载、下次启用（未知可空）、Acbric 初始化状态、来源、管理说明、初始化失败摘要。使用管理器已有清单，刷新不扫描任意文件。快照还包含身份、会话、启动阶段和消息淘汰计数。

`DeveloperDiagnostics.messages(modId, level)` 可在任意线程调用，返回不可变 `DiagnosticMessage` 列表，包含序号、时间、归属、INFO/WARN/ERROR、消息和详情。空/空白归属及空级别分别表示不按该项筛选。写消息使用 `context.logger()` / `AcbricLogger`，不要依赖内部缓冲类。

`DeveloperDiagnostics.export()` 必须在游戏线程发起，先截取纯 JSON，再安排后台 I/O，返回 `CompletableFuture<Path>`。同一时间只执行一个导出，失败/忙碌会使 future 异常完成并记录消息。正常 MOD 代码不要在游戏线程 `.get()` / `.join()` 等待，也不要在 future 回调中操作 UI 或游戏对象。关闭控制台不取消导出。

输出相对于 Fabric 游戏目录：`game/logs/acbric/exports/acbric-diagnostics-<UUID>.zip`。包含快照和消息 JSON（各最多 4 MiB）、双语 README，以及当前会话可用的 `launch.properties`、`startup.json`、`code-manifest.json`、`runtime-events.jsonl`（各最多 1 MiB）。缺失、过大、不可访问的报告会标记不可用，不用旧会话代替。各文件读取时刻可能不同。拒绝链接/特殊文件系统节点，失败删除自己的半成品 ZIP。公开接口不接受任意目标目录或附件路径。

诊断包不包含存档、配置文件和游戏资源，但包含本地路径、MOD 身份、命令输入/结果和错误消息，分享前可自行检查。框架不会上传。JSON/报告布局和 `impl` 类属于内部格式，应通过公开记录类型读取。启动报告说明见 [DIAGNOSTICS.zh-CN.md](DIAGNOSTICS.zh-CN.md)。

## 快速验收

不安装示例也能打开框架入口。执行 `acbric_api:help`、`acbric_api:status`、`acbric_api:mod acbric_api`，检查历史、补全、筛选、详情、复制和导出，再切换游戏语言重新打开。扩展测试时将独立 `acbric-console-demo-0.1.0.jar` 放进测试副本的 `game/mods/`，其 `sum`、`echo`、`choice`、故意异常 `fail`、战役条件和共享命令保留项用于验证公开 API。移除示例并重启后示例命令消失，内置命令保留。
