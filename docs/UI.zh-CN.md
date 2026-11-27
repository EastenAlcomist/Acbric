# 公共 UI 组件

[English](UI.md) · 适用于 `acbric_api 0.3.8`

框架 MOD 详情页和功能 MOD 共用原生字体、面板、按钮与开关。战斗命令面板控件是独立的原生界面，见[命令面板 UI](COMMAND_PANEL_UI.zh-CN.md)；显式字段的设置页见[设置 API](SETTINGS.zh-CN.md)。

## 注册工具窗口

在带 `AcbricModContext` 的初始化方法中注册工厂，不要在初始化期间直接打开窗口。游戏启动后，在原生 MOD 列表中选择自己的 MOD，进入“详情”并点击工具入口。

```java
import net.fabricacs.api.AcbricInitializer;
import net.fabricacs.api.AcbricModContext;
import net.fabricacs.api.ui.*;

public final class Example implements AcbricInitializer {
    private int count;
    private boolean enabled = true;
    private String name = "Player";

    @Override public void onInitializeAcbric() { }

    @Override
    public void onInitializeAcbric(AcbricModContext context) {
        context.ui().register("tools", "工具 / Tools", () ->
            new UiWindow("Example", 480, 420, true, Ui.column(10,
                Ui.label(() -> "Count: " + count),
                Ui.toggle("Enabled", () -> enabled, value -> enabled = value),
                Ui.textField(name, 64, value -> name = value),
                Ui.button("Add", handle -> count++).enabled(() -> enabled),
                Ui.button("Close", UiWindowHandle::close))));
    }
}
```

在 `fabric.mod.json` 声明 `"acbric_api": ">=0.3.8"` 和 `"java": ">=21"`。组件不需要 MOD 自写 Mixin。示例状态只存在于当前进程；需要持久化时使用[配置 API](CONFIG.zh-CN.md)或[战役数据 API](CAMPAIGN_DATA.zh-CN.md)。界面操作不会自动广播。

## 公开接口

| 接口 | 用途与约束 |
| --- | --- |
| `context.ui()` / `new ModUi(modId)` | 取得带 MOD 归属的入口；优先使用 context。归属用于诊断，不是权限隔离。 |
| `ModUi.register(id, label, Supplier<UiWindow>)` | 初始化期可用，只保存工厂。返回 `Registration`，提供 `id()`、`isRegistered()` 和幂等 `close()`。 |
| `ModUi.open(window)` | 在游戏线程、有效 Screen 且无原生错误/帮助/聊天覆盖层时打开根窗口。 |
| `UiWindow(title, width, maxHeight, modal, content[, onClose])` | 不可变窗口描述；居中、按内容收缩并受视口限制，超长正文纵向滚动。 |
| `window.withFooter(UiNode)` | 返回带固定页脚的新窗口描述；正文和页脚各自滚动。 |
| `window.onCloseRequest(Consumer<UiWindowHandle>)` | 玩家通过 X、Esc 或 `requestClose()` 关闭时先执行回调。 |
| `handle.dialog(window)` | 仅栈顶窗口可打开模态子窗口。 |
| `handle.message(title, text)` | 显示带确定按钮的消息框。 |
| `handle.confirm(title, text, yes, no, Runnable)` | 确认时先关闭子窗口再执行动作；取消和 Esc 不执行。 |
| `handle.manage(AutoCloseable)` | 托管资源或订阅范围，窗口关闭时逆序释放。 |
| `handle.modId()` / `isOpen()` / `close()` | 查询归属和状态；关闭幂等，父窗口关闭时同时关闭子窗口。 |

同时只允许一个根窗口，最多 8 层。第二个根窗口会抛出 `IllegalStateException`。设置页工具按钮会先关闭详情页再打开目标窗口。入口 ID 匹配 `[a-z][a-z0-9_-]{0,63}`，按 `MOD ID + entry ID` 隔离，重复注册拒绝，全局最多 256 个。关闭窗口不会注销工具入口。

`UiRuntime` 及其嵌套类型、`api.impl` 和 `api.mixin` 属于内部实现，不是公开扩展契约。

## 组件与布局

| 工厂或修饰器 | 行为 |
| --- | --- |
| `Ui.label(String / Supplier<String>)` | 自动换行的多行文字，动态值在布局时读取。 |
| `Ui.button(String / Supplier<String>, Consumer<UiWindowHandle>)` | 点击或聚焦后按 Enter 激活。 |
| `Ui.toggle(String, BooleanSupplier, Consumer<Boolean>)` | 读取当前值并提交新值，由 MOD 更新状态。 |
| `Ui.textField(initial, maxLength, Consumer<String>)` | 单行输入；每次打开的窗口有独立编辑状态，初始值不是动态绑定。 |
| `Ui.column(gap[, Align], nodes...)` | 纵向排列。 |
| `Ui.row(gap[, Align], nodes...)` | 横向排列，未指定宽度的子项平分剩余空间。 |
| `Ui.panel(padding, child)` | 原生面板背景和内边距。 |
| `Ui.scroll(height, child)` | 有限高度滚动区，裁剪绘制和命中。 |
| `Ui.space(height)` | 空白间隔。 |
| `node.width(value)` | 指定期望宽度；空间不足时缩小。 |
| `node.enabled(BooleanSupplier)` | 动态启用，并由祖先状态继承。 |
| `node.tooltip(String)` | 悬停提示，禁用控件也可显示原因。 |

`Align` 可取 `START / CENTER / END / STRETCH`；column 默认 `STRETCH`，row 默认 `CENTER`。修饰器返回新节点，必须使用返回值。一棵树不能重复使用同一节点对象，最多 4096 个节点、深度 32。窗口宽度 160–4096，最大高度 120–4096；组件宽度和间距 0–4096，滚动高度 24–4096。超限会抛出错误，不会截断。

布局和绘制成本随节点数线性增长。滚动只裁剪绘制，不减少每帧测量；大量条目应先分页或过滤。Supplier 应只读取当前状态，不执行磁盘 I/O、不修改窗口栈，并可在绘制和输入阶段多次调用。动态文本每次最多读取 16384 个 UTF-16 单元。

## 输入与窗口寿命

- 普通窗口拦截范围内的鼠标输入；范围内鼠标或键盘焦点会遮蔽原生快捷键，窗外单击清除焦点。
- 模态窗口遮蔽普通游戏输入，但不会暂停模拟、网络或 Screen tick。
- Tab / Shift+Tab 切换可见且启用的控件；Enter 激活按钮或开关；Esc 请求关闭拥有键盘焦点的顶层窗口。
- 文本支持 Unicode 码点长度限制、左右/Home/End、Backspace/Delete、Ctrl+A/C/X/V、拖动和 Shift+方向键选区。组合音标和 ZWJ 字素簇仍按码点处理。
- Backspace/Delete 和导航键按下后立即执行，长按约 400 ms 后开始重复，每 45 ms 最多一次。Enter、Esc、按钮、粘贴和剪贴板操作不会合成重复。
- `onSubmit` 仅用于文本框；有焦点时 Enter 在编辑完成后提交。
- Screen 切换、原生错误/帮助/聊天覆盖层、正常退出或异常会关闭窗口。原因包括 `CLOSED / SCREEN_CHANGED / GAME_EXIT / NATIVE_DIALOG / ERROR / PARENT_CLOSED`。
- 关闭时先标记句柄，再从子到父、每个窗口逆序释放托管资源。普通清理异常会记录并继续；关闭回调中不能再打开窗口。
- 打开、交互和关闭必须在游戏线程。工具注册/注销可并发，但工厂在游戏线程执行。

默认关闭按钮、Esc 和 `requestClose()` 可被 `onCloseRequest` 改写；`close()` 始终无条件关闭。Screen 切换、原生覆盖层、退出、异常和父级清理也会直接关闭，不会等待确认。

可将订阅范围绑定到窗口寿命：

```java
UiWindowHandle handle = context.ui().open(window);
var scope = handle.manage(context.eventScope("window"));
// scope.register(...); // 窗口关闭时自动注销
```

## 本地化

在游戏线程使用 `AcbricLanguage.isChinese()` 或 `AcbricLanguage.text(英文, 中文)`。每次调用读取游戏当前 Locale：`chi`、`zh`、`zho` 识别为中文；空值或其他语言回退英文。不要读取系统 Locale 或在入口初始化时缓存语言。

`context.ui().register("tools", () -> AcbricLanguage.text("Open tools", "打开工具"), () -> window())` 会在详情页绘制时读取入口名称。窗口工厂应在打开时选择文案；切换语言后重新打开窗口。第三方 MOD metadata 不会自动翻译。

## 绘制与当前边界

输入遮蔽在 input 阶段可能使用空坐标；绘制适配器在 render HEAD 和关闭释放帧恢复非空绘制状态，不解除点击/按键遮蔽。原生失败写入游戏原生日志，框架启动报告位于 `game/logs/acbric/`。

当前不提供可拖动窗口、拖动滚动条、滑块、下拉列表、树、表格、富文本、图像控件或主题系统。没有多行编辑、双击选词、撤销/重做或专用 IME 候选面板。框架 Java MOD 管理仍为重启生效，不热卸载。

第三方 HEAD 注入、直接读取底层 Slick 输入或自行绘制的界面不受统一路由；UI 遮蔽不是安全隔离，也不保证与任意重定向同一方法的 Mixin 共存。真实字体、分辨率、输入法和第三方 MOD 组合仍需在游戏中验证。

## 原版界面缩放

`UiScale` 调整原版按钮、面板、滚动条、字体度量及 Acbric 窗口；只安装 API 时保持原版大小。

```java
import net.fabricacs.api.ui.UiScale;

UiScale.setPercent(125);           // 50..200；100 = 原版小号
int requested = UiScale.percent(); // 0 = 沿用原版
UiScale.useVanilla();
```

请求在下一次绘制前统一应用；越界抛出 `IllegalArgumentException` 并保留上一次请求。初始化阶段可以提交，不要求游戏实例已创建。值为进程级偏好，多个调用者以最后一次请求为准；API 不保存配置，也不进入共享规则。

100% 对应原版小号素材和度量，不是当前自动档位。尺寸取整到 UI 像素，正数边框至少保留 1 像素。原生档位或字体变化后从原始资源重建，恢复原版不会累乘缩放。固定标题字号保持原版设置；硬编码布局的界面不会自动适配大比例。

100%、150%、200% 直接使用原版小、中、大档素材和度量；中间比例使用较高档位向下采样，低于 100% 使用小档位。缺少高档字段时回退可用低档位。该过程不修改全局纹理过滤或游戏渲染分辨率。
