# MOD 设置界面（dev.18）

2026-09-26 实机验收：用户确认 dev.18 测试没有发现问题，并要求提交。提交前全部 28 个变更文件与构建验证快照一致；本次只补充验收说明，沿用 769 项标准检查和两版共 304 项集成检查，不重复测试未改动的代码。未提供逐项实机测试矩阵，此反馈不代表全部 GPU、输入法或第三方 MOD 组合均已覆盖。

最终验证：769 项标准检查（57 项新增）、两版共 304 项真实 Fabric/界面/示例保存/新进程重启及损坏配置启动检查通过，保留 dev.17 的 71 个公开类型/354 个成员。UI 与设置双语文档示例编译检查。绘制终端无 GPU；用户实机反馈见上方，自动化验证不覆盖操作系统显示、输入法和切屏。

[English](SETTINGS.md) | 中文

需要 `acbric_api >=0.3.3-dev.18`。MOD 显式声明字段及英中文说明，框架把设置页放入该 MOD 的“详情与工具”入口。保存沿用现有 `ModConfig` 格式、备份与磁盘冲突保护；不会扫描任意 JSON 自动生成编辑器。

## 注册示例

```java
import net.fabricacs.api.*;
import net.fabricacs.api.config.*;
import net.fabricacs.api.ui.SettingsUi;
import net.fabricacs.api.util.AcbricLanguage;
import org.json.JSONObject;
import java.io.IOException;
import java.util.List;

public final class Example implements AcbricInitializer {
    private boolean showHud;
    @Override public void onInitializeAcbric() { }
    @Override public void onInitializeAcbric(AcbricModContext context) {
        try {
            ModConfig config = context.config("settings", 1,
                new JSONObject().put("showHud", true).put("count", 2).put("mode", "normal"), data -> {
                    if (!(data.get("showHud") instanceof Boolean)
                        || !(data.get("count") instanceof Integer)
                        || data.getInt("count") < 1 || data.getInt("count") > 10
                        || !List.of("normal", "compact").contains(data.getString("mode")))
                        throw new IllegalArgumentException("Invalid settings / 设置无效");
                });
            // 读取不会写默认文件；启动时由 MOD 显式采用保存值。
            showHud = config.load().data().getBoolean("showHud");
            SettingsUi.register(context.ui(), "settings",
                () -> AcbricLanguage.text("Settings", "设置"), config, List.of(
                    ConfigField.bool("showHud", new ConfigField.Text("Show HUD", "显示 HUD"),
                        ConfigField.Effect.IMMEDIATE),
                    ConfigField.integer("count", new ConfigField.Text("Starting count", "初始数量"),
                        1, 10, ConfigField.Effect.NEW_CAMPAIGN),
                    ConfigField.choice("mode", new ConfigField.Text("Display mode", "显示模式"), List.of(
                        new ConfigField.Option("normal", new ConfigField.Text("Normal", "普通")),
                        new ConfigField.Option("compact", new ConfigField.Text("Compact", "紧凑"))),
                        ConfigField.Effect.RESTART)
                ), result -> showHud = result.snapshot().data().getBoolean("showHud"));
        } catch (IOException ex) {
            // 不覆盖损坏文件，不静默恢复默认值；项目可换成自己的错误入口。
            throw new IllegalStateException("Cannot load settings / 无法读取设置", ex);
        }
    }
}
```

`SettingsUi.register` 本身只保存工厂，打开入口时才创建 `ConfigEditor`、调用 `config.load()`。已经加载的句柄沿用其当前内存快照；需要重新读磁盘时使用“重新读取文件”。旧版本配置必须由 MOD 显式迁移；字段缺失、类型不符、声明有误或读文件失败时显示可关闭的错误窗口，不自动重置文件。

## 字段和编辑会话

`ConfigField` 提供 `bool`、`integer`、`decimal`、`text`、`choice`，并可追加 `.description(new ConfigField.Text(英文, 中文))`。字段名只匹配顶层 JSON 键 `[a-zA-Z][a-zA-Z0-9_-]{0,63}`；一个编辑会话 1–32 个唯一字段。每个声明字段必须在 `ModConfig.defaults()` 中存在且符合字段规则。未声明字段和未知封装字段保留。

| 类型 | 范围与草稿表示 |
| --- | --- |
| BOOLEAN | 草稿为 Boolean |
| INTEGER | int 上下界，草稿为字符串；提交后是 Integer |
| DECIMAL | 有限 double 上下界，草稿为字符串；支持小数/指数，拒绝非有限值、超界和非零值下溢为零 |
| TEXT | 单行，最大 1–4096 个 Unicode 码点，草稿为字符串 |
| CHOICE | 1–64 个唯一稳定字符串值，标签独立翻译；未知选项提示错误，可重新选择 |

数字编辑可暂存空串、负号或未完成小数，界面显示错误并禁用应用；不会静默夹到边界或转换成默认值。整数不接受小数语法，数值范围按输入的十进制值先校验；double 仍有浮点精度限制。既有存储会规范化数字类型，例如 `1.0` 读回可能为 Integer；decimal 字段接受 Number。

`ConfigEditor` 是创建线程限定的编辑会话；在游戏中从工具工厂创建。`fields()` 返回只读定义，`value(key)`/`set(key, value)` 读写独立草稿；数字的 value 是字符串。`isDirty()` 比较草稿表示，`isValid()` 与 `error(key, chinese)` 只检查字段约束，不运行 MOD 的全局校验器。全局/跨字段校验仍在应用时运行。

- `cancel()` 恢复本会话最近成功应用/载入的基线；不会撤销其他调用者改动配置的结果。
- `restoreDefaults()` 只改已声明字段的草稿；未声明字段保留。
- `apply()` 返回 `Applied(snapshot, changedKeys, effects)`；changedKeys 按值比较，数字的 `0.50` 与 `0.5` 不产生虚假的值变化。保存成功后更新基线，失败保留草稿。
- `reload()` 显式丢弃草稿并调用配置句柄的 reload；失败保留草稿。UI 总是先确认，因为此操作也会丢弃句柄中其他尚未保存的 update。若文件本身有效但不符合设置页声明，句柄可能已经 reload 成功，而表单仍保留旧草稿；先修正声明或迁移后重开。
- `SettingsUi.window(title, editor, applied)` 可直接生成窗口。取消、X、Esc、切换 Screen 等关闭路径都丢弃未应用草稿；弹窗内部关闭不丢弃父页草稿。

应用即使值未变化也可显式创建默认配置文件。每次成功应用调用 `applied` 一次；MOD 可用 changedKeys 判断是否需要运行更新。回调在游戏线程执行，保存后才调用；如果回调失败，界面明确显示“配置已保存但生效回调失败”，不回滚文件、不自动重试回调。校验器和回调应避免重入及不可恢复的副作用。

## 提交保护与生效时机

新增 `ModConfig.defaults()` 返回默认快照；`save(ConfigSnapshot expected, JSONObject data)` 要求 expected 是**同一句柄最近 read/load 返回的原始快照对象**，不能自行构造替代。另一个调用者 update/reload/migrate 或成功草稿提交后，旧快照冲突。验证和文件保存完成后才替换内存值；写入失败不先发布新配置。旧 `update()` + `save()` 的行为不变。磁盘仍使用原有锁、备份和字节比较。

提交后关闭文件锁失败等极少数 I/O 情况仍可能“报错但磁盘已替换”；这时草稿和旧内存基线保留，需明确重新读取核实，不能直接断言磁盘未写。详情见 [配置契约](CONFIG.zh-CN.md)。

生效标记 `IMMEDIATE / RESTART / NEW_CAMPAIGN` 是 MOD 声明，由 MOD 在成功回调、启动读取、创建战役时分别采用；框架不自动热重载 MOD，不广播配置，不更新已有战役数据。成功返回的 snapshot 包含全部配置，回调应只把适合立即应用的字段用于当前状态。

共享玩法参数继续通过已有共享规则/存档流程处理。此 UI 本身不是联机同步接口，“新战役”标签也不会自动修改已注册的共享规则候选值；当候选值在启动时注册，需要按既有规则在下次启动后采用。

## 可复用 UI 组件

- `Ui.textField(Supplier<String>, maxLength, Consumer<String>)` 是受控单行文本。供应器返回非空且长度合法的值，回调必须同步写回；外部值变化时重置选区并把光标移至末尾。旧 String 重载仍保留窗口内部编辑状态。
- `Ui.integerField(value, min, max, change)` / `Ui.numberField(value, min, max, change)` 组合受控文本与中英文校验提示；value/change 都是字符串，调用者决定何时提交。
- `Ui.choice(value, List<Ui.Choice>, change)` 打开模态选项列表；Choice 的值稳定，标签可用 Supplier 动态翻译；只有选择选项才回调，取消不改值。

本版尚无嵌套字段路径、任意 JSON 编辑、滑块、配置自动迁移、鼠标定位文本光标或专用 IME 面板。语言跟随游戏，窗口标题及部分固定文案在重新打开时更新。

## 验证

标准回归 769 项，其中新增配置/数字/草稿检查 57 项。普通 JDK 测试进程用 `--patch-module jdk.unsupported=FloatIO.jar` 载入原版 JSON 小数格式化依赖；真实游戏仍由 Knot 加载，无产品启动参数改动。设置 UI 回归依赖游戏字体/语言，位于 `SettingsUiRegression` 并通过工作区真实 Fabric 探针执行，不在无资源的标准总入口中执行。

两版 1.2.15.2 / 1.2.14 覆盖表单交互、验证失败、取消/默认值、回调失败、外部冲突，以及独立展示 MOD 的保存和新进程重启。绘制终端无 GPU；中文排版、不同分辨率、输入法和截图/切屏仍需实机复测。独立展示 MOD 0.2.0 需要 dev.18，示例配置位于 `game/config/acbric_ui_showcase/ui-settings.json`；其新战役按钮仅模拟数值复制。
