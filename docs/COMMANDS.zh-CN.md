# 命令 API

[English](COMMANDS.md) · 从 `acbric_api 0.3.3-dev.20` 开始提供，尚未发布稳定版。

命令是框架与 MOD 服务的共同入口，游戏内控制台和 Java 调用方使用同一注册表。它不提供 shell、脚本解释、热重载或网络传输。

## 最小 MOD

```java
import net.fabricacs.api.*;
import net.fabricacs.api.command.*;
import java.util.List;

final class Example implements AcbricInitializer {
    private ModCommands.Registration command;
    @Override public void onInitializeAcbric() { }
    @Override public void onInitializeAcbric(AcbricModContext context) {
        command = context.commands().register(new CommandSpec(
            "echo", new CommandText("Echo text", "回显文本"),
            List.of(CommandArgument.text("text", false)),
            CommandSpec.Effect.READ_ONLY, CommandSpec.Requirement.ACTIVE_SCREEN),
            c -> CommandResult.success(c.text("text"), c.text("text")));
    }
}
```

在 `fabric.mod.json` 声明 `"acbric_api": ">=0.3.3-dev.20"` 和 `"java": ">=21"`。调用方式为 `your_mod_id:echo "hello world"`。按所需生命周期保留注册句柄，调用 `close()` 注销。应用级命令不应托管给临时窗口；Java MOD 停用仍须重启。

## 公开接口

| API | 契约 |
|---|---|
| `context.commands()` / `new ModCommands(modId)` | 获取带 MOD 归属的注册入口；归属标记不代表身份认证。 |
| `register(CommandSpec, Handler)` | 注册唯一的 `modId:commandId`，初始化时即可调用。返回 `Registration`，提供 `name()`、`isRegistered()`、幂等 `close()`。 |
| `Commands.list()` | 按完整名称排序的不可变描述列表，每项包含 `name`、`modId`、`spec`。 |
| `Commands.help(name, chinese)` | 根据声明生成语法和帮助；空名称列出命令，未知名称返回对应语言提示。 |
| `Commands.complete(line)` | 最多 32 条可整体替换输入的候选，仅补全命令名及声明的布尔/选项值，不执行 MOD 回调。 |
| `Commands.execute(line)` | 在游戏线程同步执行，返回 `CommandResult`；尚未绑定游戏线程或线程错误时抛 `IllegalStateException`。 |
| `UiNode.onSubmit(handler)` | 仅用于文本框；有焦点时 Enter 在本帧编辑完成后提交。已有文本框行为保持不变。 |

注册、列表、帮助、补全、注销支持多线程调用。执行绑定到当前游戏界面创建线程。注销释放注册表对回调的引用，阻止之后的查找，不等待已经取得回调的在途调用；注销后可重新注册同名命令，旧句柄不会删除新注册。回调应及时返回；耗时操作先截取不可变数据，再交后台处理。重入执行返回 `UNAVAILABLE`。

`CommandSpec(id, description, arguments, effect, requirement)` 使用双语 `CommandText(english, chinese)`。命令 ID 匹配 `[a-z][a-z0-9_-]{0,63}`，MOD ID 长度为 2–64。最多 16 个不重名位置参数，每 MOD 最多 64 个命令，总计最多 256 个。必填参数必须排在可选参数前。

| 参数工厂 | 结果与校验 |
|---|---|
| `text(name, optional)` | `String`，允许通过双引号传空字符串。 |
| `integer(name, min, max, optional)` | Java `int`，上下界均包含，拒绝溢出。 |
| `decimal(name, min, max, optional)` | Java `double`，边界必须有限，拒绝非有限数、精确十进制越界及非零输入舍入为零。 |
| `bool(name, optional)` | 只接受小写 `true` / `false`。 |
| `choice(name, values, optional)` | 精确匹配稳定值，1–64 个互异选项，每项不超过 128 个 UTF-16 单元。翻译标签和说明，不翻译命令 ID 或参数值。 |

`CommandContext` 提供所属 MOD ID、不可变 `arguments`、`screen`、可空的 `campaignWorld` 和 `session`（`SINGLEPLAYER` / `MULTIPLAYER` / `UNKNOWN`）。每次执行重新取得上下文。游戏引用只在本次回调中使用，不跨界面/战役保存，也不交后台读取。`text`、`integer`、`decimal`、`bool` 读取对应类型。可选参数缺省时映射中没有该项，读取可选数值/布尔值前使用 `arguments().containsKey(name)` 判断，避免拆箱空值。

## 执行边界

`ACTIVE_SCREEN` 要求当前界面有效，且没有原生错误、帮助或聊天覆盖层。`CAMPAIGN` 还要求已识别的活动战役界面（战略、科技、大学）；其他界面不提供战役，即使游戏别处仍保留引用。无法判定的会话模式保持 `UNKNOWN`。

`READ_ONLY` 声明查询，`LOCAL` 声明本地动作（例如诊断导出），满足上下文条件即可执行。`SHARED` 为同步玩法命令预留，**本版始终拒绝执行，包括单人游戏**。尚无主机转发、权限控制、复制同步或撤销。进程内 Java MOD 能够错误声明影响范围，因此这不是沙箱或反作弊保证。

结果包含 `SUCCESS`、`INVALID_ARGUMENTS`、`UNAVAILABLE` 或 `FAILED` 和双语文字。处理器抛出的 `Exception` / `LinkageError` 会记录并转成 `FAILED`；已经发生的修改不回滚，不吞掉虚拟机错误/断言错误。旧事件回调的异常传播保持不变。`Commands.execute` 本身不记录输入和成功输出，由控制台记录，其他调用方自行决定展示/日志方式。

## 输入、补全与容量

输入为单行，最多 2048 个 UTF-16 单元，以 ASCII 空格分词。双引号保留空格；引号内 `\"`、`\\` 分别转义引号和反斜杠，其他反斜杠按原文处理。带引号和无引号片段可连接。禁止控制字符，不解析 shell 展开、分号管道、命名参数或多条命令。补全允许未闭合引号，并返回已正确引用/转义的完整替换行。

说明和结果每种语言最多 8192 个 UTF-16 单元。诊断缓冲将控制台消息截到 4096，存储和显示限制详见[开发者工具](DEVELOPER_TOOLS.zh-CN.md)。ID、枚举值、命令名在中英文界面一致；框架帮助跟随游戏语言，MOD 文本由作者提供。

内置命令与测试步骤见[开发者工具](DEVELOPER_TOOLS.zh-CN.md)，开发文档入口见[开发导航](DEVELOPMENT.zh-CN.md)。
