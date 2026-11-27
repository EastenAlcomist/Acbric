# Shared UI components

[中文](UI.zh-CN.md) · Applies to `acbric_api 0.3.8`

Framework MOD details and feature MODs use the same native fonts, panels, buttons and toggles. Combat command-panel controls are a separate native surface; see [command-panel UI](COMMAND_PANEL_UI.md). Explicitly declared settings forms are documented in the [settings API](SETTINGS.md).

## Register a tool window

Register a factory from the context-aware initializer. Do not open a window during initialization. After startup, select the MOD in the native MOD list, open **Details**, then select its tool entry.

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
        context.ui().register("tools", "Tools", () ->
            new UiWindow("Example", 480, 420, true, Ui.column(10,
                Ui.label(() -> "Count: " + count),
                Ui.toggle("Enabled", () -> enabled, value -> enabled = value),
                Ui.textField(name, 64, value -> name = value),
                Ui.button("Add", handle -> count++).enabled(() -> enabled),
                Ui.button("Close", UiWindowHandle::close))));
    }
}
```

Declare `"acbric_api": ">=0.3.8"` and `"java": ">=21"` in `fabric.mod.json`. These components require no MOD-owned Mixin. The example state exists only in the current process; persist it explicitly with the [configuration API](CONFIG.md) or [campaign data API](CAMPAIGN_DATA.md). UI actions do not broadcast.

## Public API

| API | Purpose and constraints |
| --- | --- |
| `context.ui()` / `new ModUi(modId)` | Owner-labelled facade; prefer context. Attribution is not a security boundary. |
| `ModUi.register(id, label, Supplier<UiWindow>)` | Stores a factory during initialization. Returns `Registration` with `id()`, `isRegistered()` and idempotent `close()`. |
| `ModUi.open(window)` | Opens a root on the game thread when a Screen is active and no native error/help/chat overlay is present. |
| `UiWindow(title, width, maxHeight, modal, content[, onClose])` | Immutable description; centered, content-sized and viewport-constrained. Overflowing content scrolls vertically. |
| `window.withFooter(UiNode)` | Returns a description with a fixed footer; body and footer scroll separately. |
| `window.onCloseRequest(Consumer<UiWindowHandle>)` | Intercepts player close through X, Esc or `requestClose()`. |
| `handle.dialog(window)` | Only the top window can open a child, which must be modal. |
| `handle.message(title, text)` | Shows a message with an OK button. |
| `handle.confirm(title, text, yes, no, Runnable)` | Closes the child before running the accepted action; cancel/Esc do nothing. |
| `handle.manage(AutoCloseable)` | Owns a resource or subscription scope until close; cleanup is LIFO. |
| `handle.modId()` / `isOpen()` / `close()` | Owner/state queries and idempotent close; closing a parent closes its children. |

There is one root and at most eight window levels. Opening another root throws `IllegalStateException`. The settings-tool button closes Details before opening its target. Entry IDs match `[a-z][a-z0-9_-]{0,63}`, are scoped by MOD ID, reject duplicates, and are limited to 256 globally. Closing a window does not unregister its tool.

`UiRuntime` and its nested types, `api.impl` and `api.mixin` are internal implementation details, not public extension contracts.

## Components and layout

| Factory or modifier | Behavior |
| --- | --- |
| `Ui.label(String / Supplier<String>)` | Wrapping multiline text; dynamic values are read during layout. |
| `Ui.button(String / Supplier<String>, Consumer<UiWindowHandle>)` | Activates by click or Enter while focused. |
| `Ui.toggle(String, BooleanSupplier, Consumer<Boolean>)` | Reads the current value and submits a proposed value; the MOD updates its state. |
| `Ui.textField(initial, maxLength, Consumer<String>)` | Single-line editor with state per opened window; initial text is not a live binding. |
| `Ui.column(gap[, Align], nodes...)` | Vertical arrangement. |
| `Ui.row(gap[, Align], nodes...)` | Horizontal arrangement; unspecified widths share remaining space. |
| `Ui.panel(padding, child)` | Native panel background and padding. |
| `Ui.scroll(height, child)` | Bounded viewport with clipped drawing and hits. |
| `Ui.space(height)` | Blank spacing. |
| `node.width(value)` | Desired width, reduced when space is limited. |
| `node.enabled(BooleanSupplier)` | Dynamic enablement inherited from ancestors. |
| `node.tooltip(String)` | Hover explanation, including on disabled controls. |

`Align` is `START / CENTER / END / STRETCH`; columns default to `STRETCH`, rows to `CENTER`. Modifiers return new nodes, so use the return value. A tree may contain at most 4096 unique nodes and depth 32. Window width is 160–4096, max height 120–4096; component width and spacing are 0–4096, scroll height 24–4096. Exceeding a limit fails explicitly.

Layout and drawing grow linearly with node count. Scrolling clips drawing but does not reduce per-frame measurement, so large collections should be paged or filtered. Suppliers should only read current state, perform no disk I/O and make no window changes; they may run multiple times during drawing and input. Dynamic text is limited to 16384 UTF-16 units per read.

## Input and window lifetime

- Non-modal windows capture pointer input inside their bounds. Pointer input outside continues to the game. Native hotkeys are masked while the pointer is inside or the window owns keyboard focus; an outside click clears focus.
- Modal windows mask ordinary game input but do not pause simulation, networking or Screen ticks.
- Tab / Shift+Tab traverse visible enabled controls; Enter activates focused buttons or toggles; Esc requests closing the top window that owns keyboard focus.
- Text supports Unicode code-point limits, Left/Right/Home/End, Backspace/Delete, Ctrl+A/C/X/V, dragging and Shift+navigation selection. Combining marks and ZWJ grapheme clusters remain individual code points.
- Backspace/Delete and navigation keys act immediately, then repeat after about 400 ms at most once per 45 ms. Enter, Esc, buttons, paste and clipboard operations do not synthesize repeats.
- `onSubmit` applies only to text fields; focused Enter submits after applying text edits.
- Screen replacement, native error/help/chat overlays, normal exit or an error close windows. Reasons are `CLOSED / SCREEN_CHANGED / GAME_EXIT / NATIVE_DIALOG / ERROR / PARENT_CLOSED`.
- Close marks handles first, then cleans children before parents and resources in reverse order. Ordinary cleanup failures are reported while cleanup continues; close callbacks cannot open new windows.
- Open, interact and close on the game thread. Tool registration/unregistration is concurrent-safe, but factories run on the game thread.

Default close through X, Esc and `requestClose()` may be intercepted by `onCloseRequest`; `close()` always closes unconditionally. Screen changes, native overlays, exit, errors and parent cleanup close directly without waiting for confirmation.

Own a subscription scope with the window lifetime:

```java
UiWindowHandle handle = context.ui().open(window);
var scope = handle.manage(context.eventScope("window"));
// scope.register(...); // removed when the window closes
```

## Localization

On the game thread, use `AcbricLanguage.isChinese()` or `AcbricLanguage.text(english, chinese)`. Each call reads the current game Locale: `chi`, `zh` and `zho` select Chinese; null and other locales fall back to English. Do not read the operating-system Locale or cache the choice during initialization.

`context.ui().register("tools", () -> AcbricLanguage.text("Open tools", "打开工具"), () -> window())` resolves the entry label while drawing Details. Window factories should resolve their wording when opened; reopen after changing language. Third-party MOD metadata is not translated automatically.

## Rendering and current limits

Input masking may expose a null cursor during input. The drawing adapter restores a non-null drawing state at render HEAD and on the close-release frame without unmasking clicks or keys. Native failures go to the game's native log; framework startup reports live under `game/logs/acbric/`.

The UI does not provide draggable windows, draggable scrollbars, sliders, dropdowns, trees, tables, rich text, image controls or themes. Text is single-line; double-click selection, undo/redo and a dedicated IME candidate panel are not implemented. Framework Java MOD management remains restart-based with no hot unload.

Third-party HEAD injections, direct Slick input access and independently rendered screens are outside this routing. Input masking is not security isolation and cannot guarantee coexistence with arbitrary Mixins redirecting the same methods. Verify real fonts, resolutions, IME behavior and third-party MOD combinations in game.

## Native UI scaling

`UiScale` controls native buttons, panels, scrollbars, font metrics and Acbric windows; installing the API alone keeps vanilla sizing.

```java
import net.fabricacs.api.ui.UiScale;

UiScale.setPercent(125);           // 50..200; 100 = vanilla Small
int requested = UiScale.percent(); // 0 = use vanilla
UiScale.useVanilla();
```

Requests apply together before the next render. Out-of-range values throw `IllegalArgumentException` and retain the previous request. Initialization-time submission is supported. This is a process-wide preference: the latest caller wins. The API neither persists it nor includes it in shared rules.

100% is the vanilla Small asset and metric baseline, not the current automatic preset. Dimensions round to UI pixels, with positive borders at least one pixel. Native preset/font changes rebuild metrics from source assets; reset does not accumulate factors. Fixed title sizes remain unchanged, and hard-coded layouts do not become responsive automatically.

At 100%, 150% and 200%, Acbric uses the vanilla Small, Medium and Large assets and metrics directly. Intermediate sizes sample down from the next larger available tier; below 100% uses Small. Missing higher-tier fields fall back to a lower tier. Global texture filtering and game rendering resolution are unchanged.
