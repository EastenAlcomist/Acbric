# 公共 UI 组件（dev.13）

dev.18 新增显式字段的 MOD 设置页、数字/选项组件和受控文本绑定；草稿应用、取消、默认值、重读及双重冲突保护。新增 ModConfig.defaults()/save(expected,data)、ConfigField、ConfigEditor、SettingsUi。生效时机由 MOD 处理，不自动同步配置或修改已有战役。详见 [设置 API](SETTINGS.zh-CN.md)。

2026-09-26 实机反馈：用户确认 dev.17 本轮 UI 修复可以继续推进。本阶段包含公共组件、详情/工具入口、中英文、输入框定位、切屏坐标与关闭字形修复。已有 712 项标准检查、两版共 230 项集成检查；本次提交前 34 个变更文件与已验证快照完全一致，仅补充验收记录，不重复运行未变更代码的测试。此确认不扩大为全部 GPU/输入法/第三方 MOD 组合已验收。

[English](UI.md) | 中文

适用于未发布的 `acbric_api 0.3.3-dev.13`。框架 MOD 详情页和功能 MOD 使用同一套组件；字体、面板、按钮和开关沿用游戏绘制。没有接入浏览器或另一套 GUI 运行时。

## 从注册入口开始

在带 `AcbricModContext` 的初始化方法里注册工厂，**不要在初始化期间直接打开窗口**。游戏启动后，在原生 MOD 列表找到自己的 Java MOD，点击“详情”，再点击注册的工具按钮。

```java
import net.fabricacs.api.AcbricInitializer;
import net.fabricacs.api.AcbricModContext;
import net.fabricacs.api.ui.*;

public final class Example implements AcbricInitializer {
    private int count;
    private boolean enabled = true;
    private String name = "Player";

    @Override public void onInitializeAcbric() { }

    @Override public void onInitializeAcbric(AcbricModContext context) {
        context.ui().register("settings", "设置 / Settings", () ->
            new UiWindow("Example", 480, 420, true, Ui.column(10,
                Ui.label(() -> "Count: " + count),
                Ui.toggle("Enabled", () -> enabled, value -> enabled = value),
                Ui.textField(name, 64, value -> name = value),
                Ui.button("Add", handle -> count++).enabled(() -> enabled),
                Ui.button("Confirm", handle -> handle.confirm(
                    "Confirm", "Reset count?", "Yes", "No", () -> count = 0)),
                Ui.button("Close", UiWindowHandle::close))));
    }
}
```

MOD metadata 中声明 `"depends": { "acbric_api": ">=0.3.3-dev.13" }`。UI 本身不需要 MOD 自写 Mixin。上述变量仅在本进程中保存；持久化仍需显式调用 [配置 API](CONFIG.zh-CN.md)，战役数据仍用 [战役存储 API](CAMPAIGN_DATA.zh-CN.md)。界面操作不自动广播。

## 公开接口

| 接口 | 用途与约束 |
| --- | --- |
| `context.ui()` / `new ModUi(modId)` | 取得带 MOD 归属的入口；优先使用 context。归属用于诊断，不是权限隔离。 |
| `ModUi.register(id, label, Supplier<UiWindow>)` | 初始化期可用；只保存工厂，不执行它。返回 `Registration`，可查询 `id()`、`isRegistered()` 和幂等 `close()`。 |
| `ModUi.open(window)` | 已有有效 Screen、没有原生错误/帮助/聊天覆盖层时，在游戏线程打开根窗口，返回句柄。 |
| `UiWindow(title, width, maxHeight, modal, content[, onClose])` | 不可变窗口描述；居中，按内容收缩高度，受当前视口限制，超长内容自动纵向滚动。 |
| `UiWindowHandle.dialog(window)` | 仅栈顶窗口可打开模态子窗口；不允许非模态子窗口。 |
| `handle.message(title, text)` | 带“确定 / OK”按钮的消息框；需要自定义按钮文案时自己构造子窗口。 |
| `handle.confirm(title, text, yes, no, Runnable)` | 确认时先关闭子窗口再调用动作；取消和 Esc 不调用动作。 |
| `handle.manage(AutoCloseable)` | 托管资源或订阅范围；返回原资源，窗口关闭时逆序释放。 |
| `handle.modId()` / `isOpen()` / `close()` | 查询归属及状态；关闭幂等，关闭父窗口同时关闭子窗口。 |

一个运行时同时只允许一个根窗口，最多包含 8 层窗口。第二个根窗口直接 `open` 会抛 `IllegalStateException`；需要先关闭现有根窗口。详情页工具按钮会自动关闭详情页后打开目标窗口。注册入口按 `MOD ID + entry ID` 隔离，重复注册拒绝；入口 ID 为 `[a-z][a-z0-9_-]{0,63}`，全局最多 256 个。注册直到显式注销或进程退出；关闭一次窗口不会注销工具入口。

`UiRuntime` 及其嵌套类型虽然因适配层访问而为 public，**属于内部实现，不是 MOD 扩展 API**。`api.impl`、`api.mixin` 同样不是公开契约。

## 组件与布局

| 工厂 / 修饰器 | 行为 |
| --- | --- |
| `Ui.label(String / Supplier<String>)` | 多行、自动换行文字；动态值在绘制布局时读取。 |
| `Ui.button(String / Supplier<String>, Consumer<UiWindowHandle>)` | 点击或聚焦后 Enter 激活，收到所属窗口句柄。 |
| `Ui.toggle(String, BooleanSupplier, Consumer<Boolean>)` | 读取当前值并把新值交给 MOD；MOD 自己更新状态。 |
| `Ui.textField(initial, maxLength, Consumer<String>)` | 单行输入；每个打开的窗口独立保存编辑状态，文本实际变化时通知。初始值不是动态绑定。 |
| `Ui.column(gap[, Align], nodes...)` | 纵向排列，Align 控制有指定宽度的子项水平对齐。 |
| `Ui.row(gap[, Align], nodes...)` | 横向排列，Align 控制竖向对齐；未指定宽度的子项平分剩余空间。 |
| `Ui.panel(padding, child)` | 原生面板背景和内边距。 |
| `Ui.scroll(height, child)` | 有限高度纵向滚动区，裁剪绘制和点击；鼠标滚轮控制，支持嵌套。 |
| `Ui.space(height)` | 空白间隔。 |
| `node.width(value)` | 指定期望宽度，空间不足时缩小；默认使用父级分配宽度。 |
| `node.enabled(BooleanSupplier)` | 动态启用；父容器禁用影响所有后代，激活前重新检查。 |
| `node.tooltip(String)` | 鼠标悬停提示，禁用组件也可解释原因。 |

`Align` 可取 `START / CENTER / END / STRETCH`。默认 column 为 STRETCH、row 为 CENTER。修饰器返回新节点，必须使用返回值。单棵树禁止重复使用同一节点对象，最多 512 个节点、深度 24。数值以 GUI 逻辑单位表示，跟随游戏按钮缩放；窗口宽度 160–4096，最大高度 120–4096；组件宽度 1–4096，间距 0–4096，滚动高度 24–4096。

布局和供应器应保持纯读取，不做磁盘 I/O、不修改窗口栈。Supplier 可每帧及输入阶段多次调用。动态标签每次读取最多 16384 个 UTF-16 单元。框架不解释组件文字中的游戏颜色标记：方括号显示为全角括号，底层文本值保持原样。

## 输入与寿命

- 普通窗口拦截自身范围内的鼠标操作；窗外鼠标输入可继续交给游戏。鼠标在窗内或窗口拥有键盘焦点时，原生快捷键被遮蔽。窗外单击移除键盘焦点。
- 模态窗口遮蔽游戏普通键盘/鼠标入口。**不会暂停模拟、网络或 Screen 的 tick**；菜单弹窗不等于单机暂停菜单。
- Tab / Shift+Tab 在可见且启用的控件间切换；Enter 激活按钮或开关；Esc 关闭当前拥有键盘输入的顶层窗口。首次绘制前不接受点击，滚动后等下一次布局再命中。
- 文本支持 Unicode 码点长度限制、左右/Home/End、Backspace/Delete、Ctrl+A、Ctrl+V。粘贴过滤控制字符。点击输入框获得焦点，光标保留当前位置（初始在末尾）；当前没有鼠标定位光标、局部选择、Ctrl+C/X、连续按键重复、多行编辑或专用 IME 面板。
- 改变游戏 Screen、原生错误/帮助/聊天覆盖层出现或正常退出时关闭窗口。`onClose` 原因包括 `CLOSED / SCREEN_CHANGED / GAME_EXIT / NATIVE_DIALOG / ERROR / PARENT_CLOSED`。父窗口连带关闭的子窗口收到 `PARENT_CLOSED`。
- 先标记所有待关闭句柄，再从子到父释放资源；每个窗口内部逆序释放，最后调用 `onClose`。普通清理异常会记录并继续释放。关闭后释放组件回调引用；不能在清理回调中重新开窗口。强杀进程不保证回调。
- 打开、交互和关闭须在游戏线程；异步任务不能直接改句柄。注册/注销工具入口可并发，但工厂始终在游戏线程执行。常规运行时回调异常会关闭所属窗口并报告错误；虚拟机级错误不作恢复保证。

在自己管理的游戏线程入口可这样绑定窗口寿命：

```java
UiWindowHandle handle = context.ui().open(window);
var scope = handle.manage(context.eventScope("settings-window"));
// scope.register(...); // 窗口关闭时自动注销这个范围
```

现有旧事件时序不变。UI 拦截发生在游戏完成输入缩放之后；第三方 HEAD 注入、直接读取底层 Slick 输入或自行绘制的界面不受统一管控。这里不提供安全隔离，也不承诺与任意重定向同一方法的 Mixin 共存。

## 本轮范围与验证

这是基础组件首版。暂不提供拖动窗口、拖动滚动条、滑块、下拉列表、树、表格、富文本、图标组件、主题系统或配置字段自动生成界面。框架详情页保留重启启停语义，不实现 Java 热卸载。

标准回归验证布局、输入、Unicode、禁用和清理；两套真实 Fabric 游戏字节码探针验证注入、原生输入循环、打开帧防穿透、工具入口和原生绘制适配器的裁剪恢复。底层绘制使用录制替身，尚不等于 OpenGL 实机验收。真实字体、分辨率/GUI 缩放、输入法和第三方 MOD 组合仍需在游戏里检查。工作区独立 `acbric-ui-showcase` 提供可安装测试 MOD，不是框架默认内容。

## 中英文界面（dev.14）

游戏线程中使用 `net.fabricacs.api.util.AcbricLanguage.isChinese()` 或 `text(英文, 中文)`。每次调用读取**游戏**当前语言；原生 `chi`、标准 `zh`/`zho` 均识别为中文，空值或其他语言回退英文，不读取系统默认语言。

`context.ui().register("tools", () -> AcbricLanguage.text("Open tools", "打开工具"), () -> window())` 在详情页绘制时解析入口名称；原 String 重载保留。窗口工厂在打开时选择文案，不在 MOD 初始化时缓存语言；切换语言后重新打开窗口。框架和配套示例后续同步支持英文、中文，第三方 MOD 自己的任意 metadata 不自动翻译。

## 原生绘制修复（dev.15）

输入遮蔽可在 input 阶段使用 null 坐标；原生绘制要求非空坐标，因此在 render HEAD、底层 Screen 绘制前恢复仅供绘制使用的状态，关闭后的释放帧也要处理。设备无坐标时使用屏幕外位置，不解开点击/按键遮蔽。回归现在实际执行 AirshipGame.render/MyDraw.button，只有 Frame 绘制终端不连接 GPU，不再把按钮方法本身替换掉。原生异常写入用户数据目录的 `log.txt`，与框架 `game/logs/acbric/` 下启动报告不同。

## 输入定位与诊断（dev.16）

光标误用 MyDraw.tw（大字体加开关按钮装饰宽度），改为和正文相同的 AGame.FOUNT 度量，并让文字及光标一起垂直居中。未更改点击语义或强制重置焦点。最新用户运行日志未见新异常，但不能据此排除事件丢失。新增 acbric-ui-input.log 和 acbric-ui-input.previous.log，位于游戏用户数据目录，每份最多 64 KiB；记录焦点、鼠标按下/释放、原生点击、导航键和框架屏障/布局/命中结果，不记录文本。输入日志写入失败停止诊断，不中断游戏。

`active=false` 表示窗口失焦，`null` 表示没有可读取的显示状态。`downButton=1` 是左键按下，随后 `downButton=0` 是释放；释放不保证生成 `click=true`。`release-barrier` 表示关闭后的释放屏障，`await-render` 表示等待有效布局，`click-miss`/`click-disabled` 表示未命中/禁用，`click-BUTTON` 表示动作路径已进入（不保证回调成功）。复现后保留两份日志及原生日志 log.txt，并记下时间、控件和截图/切屏顺序。此记录不能替代 Windows 实机焦点验证。

## 切屏坐标与关闭按钮（dev.17）

玩家日志 03:22:39–03:22:42 显示多次按下位置和点击事件位置约相差 119 像素，布局有效且释放屏障为 false。此证据支持坐标错位，不是点击没有送达。适配层对 Slick 输入将命中坐标统一为已经过 ScaledInput 的 cursor；仍需收到左键 clicked 才执行，悬停/按住不会合成点击。自定义 Input 保持事件坐标语义，缺失 cursor 时回退 clicked。若点击释放后至采样前鼠标继续移动，新 UI 以采样位置为准。底层为何在 Windows 切屏后暂时产生偏移尚未证实；没有修改原生游戏或全局鼠标状态。日志新增同帧 cursor 与 event 坐标便于验证。标题关闭按钮从缺失字形的 × 改为 X，中英文均可用。
