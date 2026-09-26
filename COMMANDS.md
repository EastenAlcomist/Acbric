# Command API

[中文](COMMANDS.zh-CN.md) · Since `acbric_api 0.3.3-dev.20` (unreleased).

Commands are a common entry into framework/MOD services. The in-game console and Java callers use the same registry. Commands are not a shell, script interpreter, hot-reload system or network transport.

## Minimal MOD

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

Declare `"acbric_api": ">=0.3.3-dev.20"` and `"java": ">=21"` in `fabric.mod.json`. Invoke `your_mod_id:echo "hello world"`. Keep the registration for the intended lifetime; call `close()` to unregister. Do not put an application-wide command in a temporary UI window's managed resources. Java MOD disable still takes effect after restart.

## Public surface

| API | Contract |
|---|---|
| `context.commands()` / `new ModCommands(modId)` | Obtain an owner-scoped registry facade; this is attribution, not authentication. |
| `register(CommandSpec, Handler)` | Register a unique `modId:commandId`; available during initialization. Returns `Registration` with `name()`, `isRegistered()`, idempotent `close()`. |
| `Commands.list()` | Immutable descriptors, sorted by full name; each contains `name`, `modId`, `spec`. |
| `Commands.help(name, chinese)` | Metadata-derived syntax/help. Null/blank name lists commands; unknown name returns a localized message. |
| `Commands.complete(line)` | Up to 32 full replacement strings, command names and declared boolean/choice values only. No MOD callback executes. |
| `Commands.execute(line)` | Synchronous game-thread execution. Returns `CommandResult`; unavailable/wrong thread throws `IllegalStateException`. |
| `UiNode.onSubmit(handler)` | Text fields only; Enter submits after applying text edits while focused. Existing fields retain their behavior. |

Registration/list/help/completion/close are thread-safe. Execution is bound to the thread that creates the current game UI. Closing a registration drops its callback reference and prevents subsequent lookups; it does not wait for an already captured/in-flight handler. Re-registering the same name is allowed after close, and an old handle cannot remove the replacement. A callback must finish promptly; expensive work belongs on a worker using an immutable snapshot. Recursive command execution returns `UNAVAILABLE`.

`CommandSpec(id, description, arguments, effect, requirement)` uses bilingual `CommandText(english, chinese)`. IDs follow `[a-z][a-z0-9_-]{0,63}`; MOD IDs have 2–64 characters. There are at most 16 distinct positional arguments, 64 commands per owner and 256 overall. Required arguments must precede optional ones.

| Argument factory | Result / validation |
|---|---|
| `text(name, optional)` | `String`, including quoted empty text. |
| `integer(name, min, max, optional)` | Java `int`, inclusive bounds; rejects overflow. |
| `decimal(name, min, max, optional)` | Java `double`, finite bounds; rejects non-finite values, out-of-range decimal input and nonzero input rounded to zero. |
| `bool(name, optional)` | Exact lowercase `true` / `false`. |
| `choice(name, values, optional)` | Exact stable value, 1–64 distinct choices, each ≤128 UTF-16 units. Translate labels/descriptions, not command IDs or argument values. |

`CommandContext` exposes owner ID, immutable `arguments`, `screen`, optional `campaignWorld`, and `session` (`SINGLEPLAYER`, `MULTIPLAYER`, `UNKNOWN`). The context is captured again for each invocation. Use game references only within the callback; do not retain them across screen/campaign changes or read them on a worker. `text`, `integer`, `decimal`, `bool` return typed values. Absent optional arguments have no map entry: use `arguments().containsKey(name)` before unboxing an optional number/boolean.

## Execution boundaries

`ACTIVE_SCREEN` requires a current screen without a native error/help/chat overlay. `CAMPAIGN` additionally requires a recognized active campaign screen (strategic, technology or university). Other screens yield no campaign, even if the game still retains one elsewhere. Unknown session mode remains `UNKNOWN`.

`READ_ONLY` declares a query; `LOCAL` declares a local action such as diagnostics export. Both may run when their context requirement holds. `SHARED` is reserved for synchronized gameplay commands and **always returns `UNAVAILABLE` in this version**, including single-player. No host forwarding, authorization, replication or undo is implemented. A same-process Java MOD can misdeclare its effect; these declarations are not a sandbox or anti-cheat guarantee.

Results contain `SUCCESS`, `INVALID_ARGUMENTS`, `UNAVAILABLE` or `FAILED` plus bilingual text. Handler `Exception`/`LinkageError` is recorded and converted to `FAILED`; changes already made are not rolled back. VM errors/assertion errors are not swallowed. Existing event callback exception propagation remains unchanged. `Commands.execute` itself does not record command input or successful output; the console does, so non-UI callers decide what to display/log.

## Input, completion and limits

Input is a single line of at most 2048 UTF-16 units, with ASCII spaces separating tokens. Double quotes preserve spaces; within quotes, `\"` and `\\` escape a quote/backslash. Other backslashes are literal. Quotes may join unquoted fragments. No control characters, shell expansion, semicolon pipelines, named flags or multiple-command execution. Completion accepts an unfinished quote and returns replacement lines that quote/escape candidates correctly.

Descriptions/result text are limited to 8192 UTF-16 units per language. The diagnostic buffer clips console messages to 4096; see [developer tools](DEVELOPER_TOOLS.md) for storage and display limits. IDs, enum values and commands stay stable in both UI languages. Framework help uses the current game language; MOD text is supplied by its author.

Built-in commands and a test sequence: [developer tools](DEVELOPER_TOOLS.md). Documentation map: [developer guide index](DEVELOPMENT.md).
