# 战斗命令面板 UI

Acbric 0.3.8 增加一个小型命令面板扩展面。MOD 通过 `AcbricModContext.commandPanelUi()` 注册原生样式按钮、开关和状态标签；Acbric 将其排布在原生面板窗口中，并把确认交互接入游戏已有的 `MyDraw` / `Hooks` 链路。

[English](COMMAND_PANEL_UI.md)

## 示例

```java
import net.fabricacs.api.AcbricInitializer;
import net.fabricacs.api.AcbricModContext;
import net.fabricacs.api.combat.CommandPanelAnchor;
import net.fabricacs.api.combat.CommandPanelLayout;
import net.fabricacs.api.combat.CommandPanelPlacement;
import net.fabricacs.api.combat.CommandPanelUi;
import net.fabricacs.api.util.AcbricLanguage;

public final class ExampleMod implements AcbricInitializer {
    private CommandPanelUi.Registration button;

    @Override
    public void onInitializeAcbric() {
    }

    @Override
    public void onInitializeAcbric(AcbricModContext context) {
        button = context.commandPanelUi().registerButton(
            "mark-ships",
            panel -> AcbricLanguage.text("Mark selected ships", "标记选中舰船"),
            panel -> AcbricLanguage.text(
                "Adds a local test marker to the selected-ship set.",
                "为当前选中的舰船添加本地测试标记。"),
            panel -> panel.screen() != null,
            panel -> {
                // 仅在确实需要游戏字段时转换 panel.screen() / panel.panel()。
                // 共享玩法仍须通过确定性模拟或原生网络命令路径发送。
            },
            CommandPanelPlacement.at(CommandPanelAnchor.WINDOW_TOP_RIGHT, -16, 8));
    }
}
```

使用 MOD 声明：

```json
"depends": {
  "acbric_api": ">=0.3.8"
}
```

## 公开方法

`context.commandPanelUi()` 返回按当前 MOD 隔离的 `CommandPanelUi`：

```java
Registration registerButton(String id, String label, Runnable action)
Registration registerButton(String id, String label, Runnable action,
    CommandPanelPlacement placement)
Registration registerButton(String id,
    Function<CommandPanelContext, String> label,
    Consumer<CommandPanelContext> action)
Registration registerButton(String id,
    Function<CommandPanelContext, String> label,
    Consumer<CommandPanelContext> action,
    CommandPanelPlacement placement)
Registration registerButton(String id,
    Function<CommandPanelContext, String> label,
    Function<CommandPanelContext, String> tooltip,
    Predicate<CommandPanelContext> enabled,
    Consumer<CommandPanelContext> action,
    CommandPanelPlacement placement)
Registration registerLabel(String id, String text)
Registration registerLabel(String id, String text, CommandPanelPlacement placement)
Registration registerLabel(String id,
    Function<CommandPanelContext, String> text,
    CommandPanelPlacement placement)
Registration registerLabel(String id,
    Function<CommandPanelContext, String> text,
    Function<CommandPanelContext, String> tooltip,
    CommandPanelPlacement placement)
Registration registerToggle(String id,
    Function<CommandPanelContext, String> label,
    Function<CommandPanelContext, Boolean> checked,
    BiConsumer<CommandPanelContext, Boolean> change,
    CommandPanelPlacement placement)
Registration registerToggle(String id,
    Function<CommandPanelContext, String> label,
    Function<CommandPanelContext, Boolean> checked,
    Function<CommandPanelContext, String> tooltip,
    Predicate<CommandPanelContext> enabled,
    BiConsumer<CommandPanelContext, Boolean> change,
    CommandPanelPlacement placement)
```

`CommandPanelContext` 的 `screen()` 与 `panel()` 是命令面板绘制调用中的真实游戏对象，公开类型保持 `Object`。需要游戏字段时由 MOD 转型；Acbric 没有另行复制选中舰船或战斗状态，也没有承诺跨游戏版本的访问器。

`Registration` 提供 `id()`、`isRegistered()` 和幂等 `close()`。三类项目共用每 MOD 与全局容量限制。

## 锚点、偏移与布局

`CommandPanelPlacement` 由 `CommandPanelAnchor`、水平偏移 `dx`、垂直偏移 `dy` 和 `CommandPanelLayout` 组成。不传位置的重载继续使用 `PANEL_ABOVE_CENTER`、零偏移和 `HORIZONTAL` 布局。

| 命令面板锚点 | 含义 |
| --- | --- |
| `PANEL_ABOVE_LEFT` | 原生面板上方，按钮条左边缘对齐面板左侧。 |
| `PANEL_ABOVE_CENTER` | 原生面板上方居中；默认。 |
| `PANEL_ABOVE_RIGHT` | 原生面板上方，按钮条右边缘对齐面板右侧。 |

| 窗口锚点 | 含义 |
| --- | --- |
| `WINDOW_TOP_LEFT` / `WINDOW_TOP_CENTER` / `WINDOW_TOP_RIGHT` | 当前游戏视口顶部。 |
| `WINDOW_CENTER_LEFT` / `WINDOW_CENTER` / `WINDOW_CENTER_RIGHT` | 当前游戏视口中部。 |
| `WINDOW_BOTTOM_LEFT` / `WINDOW_BOTTOM_CENTER` / `WINDOW_BOTTOM_RIGHT` | 当前游戏视口底部。 |

这里的“窗口”是当前 `ScreenMode` 游戏视口，不是操作系统窗口。偏移使用 GUI 单位，范围 `-4096..4096`；最终位置仍会被限制在顶部栏下方与屏幕安全边距内，不会因为偏移过大完全移出画面。

`CommandPanelLayout` 支持：

- `HORIZONTAL`：项目排成一行，并保留各自自然宽度。
- `VERTICAL`：项目在同一原生窗口内从上到下排列，统一采用最宽项目宽度，形成侧边栏式列表。

使用 `CommandPanelPlacement.vertical(CommandPanelAnchor.WINDOW_CENTER_LEFT, 20, 0)` 可创建游戏视口左侧中部的命令侧边栏。锚点、偏移和布局全部相同才归为一组；任一不同都会形成另一个分组。主命令面板未激活时，所有分组仍会隐藏。

## 生命周期与布局

- 可在 MOD 初始化期注册，不要求已经存在游戏界面。
- 动态标签、开关状态、提示和启用状态在命令面板绘制时读取，必须快速、只读且不做阻塞 I/O。
- 项目文字规整为单行、最多 128 个 Unicode 码点；提示最多 512 个码点。
- 按钮使用游戏原生素材与度量。自然宽度限制在 56–260 GUI 单位；放不下的后续按钮不会换行或滚动。
- 按钮和开关使用原生 `MyDraw` 控件；状态标签使用原生面板边框、游戏字体和紧凑命令面板高度。
- 每个 MOD 最多 4 个项目，全局最多 32 个；重复的 `MOD ID + 项目 ID` 会被拒绝。
- 只会在主命令面板完成布局后绘制。倒计时、观战/回放、直接控制、非导航工具、隐藏 UI 或面板未激活时不显示。
- 位置设置不修改或预留原生布局空间。窗口锚点可能覆盖游戏画面或原生控件；飞机命令、一次性模块等动态子面板可能占用附近区域，需要在不同选择、工具与 UI 缩放下实机检查。
- 纵向分组首版没有折叠或滚动，整组高度会被限制在视口安全区域；很高的侧边栏仍可能贴近或压缩上下边界。

## 点击与错误

框架使用与原生控件相同的 `MyDraw.button` / `MyDraw.toggle` 和 Hooks 路径。只有交互被接受后才执行按钮动作或开关变更；已注销项目不会再响应保留的旧 Hook。开关回调收到当前 `checked` 供应器结果的反值，MOD 必须同步更新自己的状态。

运行时回调失败进入 Acbric 诊断，不替换游戏界面。标签/提示/启用状态函数失败时跳过该帧按钮，并避免每帧重复记录同一错误，恢复后重新允许诊断。

首版不提供自定义图标按钮、冷却遮罩、可折叠分区、分区标题、文本输入、拖放、快捷键或滚动。需要这些能力时继续使用公开 `Ui` 窗口组件。

## 联机与命令权威

本接口只负责绘制与接收点击，**不会自动同步玩法**。改变战斗状态的命令仍须使用游戏的确定性命令/网络路径，或遵守具体 MOD 的多人权威规则。本地 UI 计数可以只存在本机；不能因为按钮回调执行了本地代码就假定共享效果已经同步。

## 已知边界

窗口锚点可能覆盖游戏画面或原生控件，动态子面板可能占用附近区域；这些位置需要在不同选择、工具、DPI 和 UI 缩放下实机检查。本接口不提供同步、权限控制或反作弊，联机玩法仍由 MOD 使用确定的游戏命令路径或自己的权威规则。
