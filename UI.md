# Shared UI components (dev.13)

dev.18 adds explicit MOD settings forms, numeric/choice controls and controlled text binding, with draft apply/cancel/defaults/reload and memory/disk conflict protection. New APIs: ModConfig.defaults()/save(expected,data), ConfigField, ConfigEditor and SettingsUi. MOD code implements effect timing; configuration is not automatically synchronized and existing campaigns are not rewritten. See [settings API](SETTINGS.md).

2026-09-26 player feedback: the user accepted the dev.17 UI fixes and approved proceeding. This stage includes shared components, details/tool entries, English/Chinese support, text positioning, focus-return hit testing, and the close glyph. Existing validation: 712 standard checks and 230 integration checks across two game versions. All 34 changed files matched the tested snapshot before this commit; only acceptance notes were then added. Unchanged code was not retested. This does not establish coverage of every GPU, IME, or third-party MOD combination.

English | [中文](UI.zh-CN.md)

For the unpublished `acbric_api 0.3.3-dev.13`. Framework MOD details and feature MODs use the same components, rendered with native game fonts, panels, buttons and toggles. No browser or separate GUI runtime is required.

## Register a tool

Register a factory in the context-aware initializer. **Do not open a window during initialization.** After startup, select your Java MOD in the native MOD list, choose **Details**, then its registered tool button.

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
        context.ui().register("settings", "Settings", () ->
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

Declare `"depends": { "acbric_api": ">=0.3.3-dev.13" }`. Using these components requires no MOD-owned Mixin. The example's values live only in this process. Persist explicitly with the [configuration API](CONFIG.md) or [campaign data API](CAMPAIGN_DATA.md); UI actions are not automatically broadcast.

## Public API

| API | Purpose and constraints |
| --- | --- |
| `context.ui()` / `new ModUi(modId)` | Owner-labelled facade; prefer context. Attribution is not a security boundary. |
| `ModUi.register(id, label, Supplier<UiWindow>)` | May run at initialization; stores the factory without executing it. Returns `Registration` with `id()`, `isRegistered()` and idempotent `close()`. |
| `ModUi.open(window)` | Opens a root on the game thread with an active Screen and no native error/help/chat overlay. Returns a handle. |
| `UiWindow(title, width, maxHeight, modal, content[, onClose])` | Immutable description; centered, height shrinks to content, constrained to the viewport. Overflow scrolls vertically. |
| `handle.dialog(window)` | Only the top window can open a child, which must be modal. |
| `handle.message(title, text)` | Message with a localized OK/确定 button. Build a child explicitly for custom button wording. |
| `handle.confirm(title, text, yes, no, Runnable)` | Closes the child before invoking the accepted action. Cancel/Esc do not invoke it. |
| `handle.manage(AutoCloseable)` | Owns a resource/subscription scope until close; returns that resource. Cleanup is LIFO. |
| `handle.modId()` / `isOpen()` / `close()` | Owner/state queries and idempotent close; closing a parent closes its children. |

There is one root at a time and at most eight window levels. Opening another root throws `IllegalStateException`; close the current root first. The details tool button handles this transition. Entry IDs are scoped by MOD ID, must match `[a-z][a-z0-9_-]{0,63}`, and cannot be registered twice. There are at most 256 entries globally. Entries last until unregister/process exit; closing a window does not unregister its tool.

`UiRuntime` and its nested types are public only for adapter access and **are internal, unsupported implementation details**. `api.impl` and `api.mixin` are also outside the public contract.

## Components and layout

| Factory / modifier | Behavior |
| --- | --- |
| `Ui.label(String / Supplier<String>)` | Multiline wrapping label; dynamic values read during layout. |
| `Ui.button(String / Supplier<String>, Consumer<UiWindowHandle>)` | Click or focused Enter; action receives its owning window. |
| `Ui.toggle(String, BooleanSupplier, Consumer<Boolean>)` | Reads the current value and reports a proposed value; the MOD updates its state. |
| `Ui.textField(initial, maxLength, Consumer<String>)` | Single-line editor, separate state per opened window; reports actual changes. Initial text is not a live binding. |
| `Ui.column(gap[, Align], nodes...)` | Vertical arrangement; Align controls horizontal placement of children with explicit widths. |
| `Ui.row(gap[, Align], nodes...)` | Horizontal arrangement; Align controls vertical placement. Unspecified widths share the remaining space. |
| `Ui.panel(padding, child)` | Native panel background with padding. |
| `Ui.scroll(height, child)` | Bounded vertical viewport with clipped drawing/hits and mouse-wheel scrolling; supports nesting. |
| `Ui.space(height)` | Blank spacing. |
| `node.width(value)` | Desired width, reduced when space is limited. Defaults to the parent's allocation. |
| `node.enabled(BooleanSupplier)` | Dynamic enablement, inherited from ancestors and rechecked before activation. |
| `node.tooltip(String)` | Hover explanation, including on disabled controls. |

`Align`: `START / CENTER / END / STRETCH`. Columns default to STRETCH, rows to CENTER. Modifiers return new nodes: use the returned value. A tree may contain at most 512 unique node objects and depth 24; reusing one node twice in a tree is rejected. Sizes use GUI logical units scaled with native button size. Window width: 160–4096; max height: 120–4096; component width: 1–4096; spacing: 0–4096; scroll height: 24–4096.

Suppliers should be pure reads, with no disk I/O or window mutations; they may be called multiple times during drawing/input. Dynamic text is limited to 16384 UTF-16 units per read. Game color markup is not interpreted: square brackets display as fullwidth brackets, without changing underlying text values.

## Input and lifetime

- Non-modal windows capture pointer input inside their bounds. Outside pointer input continues to the game. Native hotkeys are masked while the pointer is inside or the window owns keyboard focus; an outside click clears focus.
- Modal windows mask ordinary game mouse/keyboard input. **Simulation, networking and Screen ticks continue**; a dialog is not a pause menu.
- Tab / Shift+Tab traverse visible enabled controls; Enter activates focused buttons/toggles; Esc requests closing the top window when it owns keyboard input. No hits before first layout; scrolling invalidates hits until the next render.
- Text supports Unicode code-point limits, Left/Right/Home/End, Backspace/Delete and Ctrl+A/C/X/V. In dev.19, clicks place the caret using actual body-font widths; dragging, Shift+click and Shift+navigation select text. Long text scrolls horizontally to reveal the caret. Surrogate pairs remain intact; combining marks and ZWJ grapheme clusters are still handled as individual code points.
- Screen replacement, native error/help/chat overlays and normal exit close windows. Reasons: `CLOSED / SCREEN_CHANGED / GAME_EXIT / NATIVE_DIALOG / ERROR / PARENT_CLOSED`. Children removed with their parent receive `PARENT_CLOSED`.
- All removed handles are marked closed before cleanup. Children clean up before parents, resources in each window close in reverse order, then `onClose` runs. Ordinary cleanup failures are reported while cleanup continues. Closed handles release component callbacks. Opening windows during cleanup is prohibited. Forced process termination cannot guarantee callbacks.
- Open/interact/close on the game thread. Background tasks must not mutate handles directly. Tool registration/unregistration supports concurrent access; factories execute on the game thread. Ordinary runtime callback failures close the owning window and report the error; VM-level error recovery is not guaranteed.

Own subscriptions with the window from a game-thread entrypoint:

```java
UiWindowHandle handle = context.ui().open(window);
var scope = handle.manage(context.eventScope("settings-window"));
// scope.register(...); // automatically removed when this window closes
```

Legacy event timing is unchanged. Input masking runs after native input scaling. Third-party HEAD injections, direct raw Slick input access and independently rendered interfaces are outside this routing. This is not security isolation or a guarantee of coexistence with arbitrary Mixins redirecting the same methods.

## Scope and verification

The current API still excludes draggable windows/scrollbars, sliders, dropdowns, trees, tables, rich text, image controls and themes. dev.18 added explicitly declared settings forms and modal option lists; see SETTINGS.md. Framework details retain restart-only Java MOD management, with no hot unloading.

Standard tests cover layout, input, Unicode, enablement and cleanup. Real Fabric probes for both game builds cover injection, native input ticks, opening-frame masking, registered tools and native adapter clip restoration. Recording drawing backends do not constitute OpenGL acceptance. Real fonts, resolution/GUI scaling, IME behavior and third-party MOD combinations still require in-game checks. The independent workspace `acbric-ui-showcase` project supplies an installable test MOD; it is not a default framework MOD.

## Bilingual UI (dev.14)

Use `net.fabricacs.api.util.AcbricLanguage.isChinese()` or `text(english, chinese)` on the game thread. The selector reads the current **game** locale on each call: native `chi` and standard `zh`/`zho` select Chinese; null/other locales fall back to English. It does not read the operating-system locale.

`context.ui().register("tools", () -> AcbricLanguage.text("Open tools", "打开工具"), () -> window())` reads the entry label when drawing Details. The String overload remains supported. Factories should resolve window/control wording when opened, not cache it at MOD initialization. Reopen windows after changing language. Framework UI and bundled examples must support English and Chinese; arbitrary third-party metadata is not auto-translated.

## Native drawing fix (dev.15)

Input masking may expose a null pointer during input. Native drawing requires a non-null cursor: the bridge restores drawing-only state at render HEAD, before the underlying Screen draws, including the release frame after closing. An absent device cursor uses an offscreen point. Restoring drawing state does not unmask clicks/keys. Regression now executes actual AirshipGame.render/MyDraw.button through a no-GPU Frame, rather than replacing the button method. Native failures are logged in the user-data directory `log.txt`; this differs from framework startup reports under `game/logs/acbric/`.

## Text positioning and input diagnostics (dev.16)

The caret incorrectly used MyDraw.tw (large-font toggle width including decoration). It now measures AGame.FOUNT, matching the rendered text, and centers text and caret vertically. Click semantics and focus are unchanged. The latest player log contained no new exception; this does not rule out missing input events. acbric-ui-input.log and acbric-ui-input.previous.log in the game user-data directory retain up to 64 KiB each. They record display activity, mouse press/release, native clicks, navigation keys, and framework barrier/layout/hit decisions, never text content. A write failure disables diagnostics without interrupting gameplay.

`active=false` means the display is inactive; `null` means unavailable. `downButton=1` is left-down and `0` is release; release does not guarantee `click=true`. `release-barrier` means the close-release guard; `await-render` means layout is not ready; `click-miss`/`click-disabled` identify rejected hits; `click-BUTTON` means the action path was entered, not that the callback succeeded. Preserve both input logs and native log.txt after a recurrence, noting the time, control, and screenshot/alt-tab sequence. These records do not replace Windows focus testing.

## Focus-return coordinates and close glyph (dev.17)

Player logs at 03:22:39–03:22:42 show repeated approximately 119-pixel differences between press and click-event positions, with valid layout and no release barrier. This supports coordinate drift, rather than absent clicks. For Slick input, hit testing now uses cursor after ScaledInput transformation. A native left clicked event is still required: hover/holding never synthesizes a click. Custom Input keeps its explicit event coordinates; missing cursor falls back to clicked. If the pointer moves between release and sampling, shared UI uses the sampled location. The underlying Windows focus/coordinate drift cause is not established; native game input and global mouse state are unchanged. Logs now include both cursor and event coordinates in the same frame. The title-bar close glyph changes from unsupported × to X in both languages.

## Editing and fixed actions (dev.19)

- Hit testing uses the last rendered body-font measurements, clipping and horizontal offset. Slick still uses scaled cursor coordinates for hits and native clicked events for ordinary buttons. Text dragging begins only on left press; releasing over another button never activates it. Child dialogs, loss of focus and disabled controls cancel dragging.
- Unmodified Left/Right collapse a selection to its start/end. Shift+Left/Right/Home/End extend it. Typing, pasting and deletion replace the selection. Ctrl+A selects all; Ctrl+C copies only the selection; Ctrl+X deletes only after a successful clipboard write. Busy/unavailable clipboard access preserves text and the window. C/X without a selection do nothing. Paste filters control characters and isolated surrogates; entirely filtered content preserves the selection.
- Backspace/Delete/Left/Right/Home/End act immediately and repeat after approximately 400 ms, at most once per 45 ms. Slow frames do not replay a backlog. Release, changed text focus, a child dialog, display focus loss or an external controlled-model update stops repetition. Press again after returning to resume. Enter, Esc, button actions and clipboard operations never receive synthetic repeats; printable text continues through the game's typedText/IME submissions.
- `window.withFooter(UiNode)` returns a new description with a fixed footer. Body/footer share node uniqueness/count/depth validation and have separate clipping/scrolling. At normal resolutions the footer stays put as the body scrolls; a very small viewport or large footer may clip it, in which case scrolling over the footer reveals its content. Never reuse the same node in both trees.
- `window.onCloseRequest(Consumer<UiWindowHandle>)` returns a description with a player-close callback. X, Esc and `handle.requestClose()` invoke it. The default calls close; a custom callback may open confirm and call `handle.close()` after acceptance. Only the top window can requestClose; closed handles are ignored.
- `handle.close()` remains unconditional and idempotent. Screen changes, native overlays, exit, errors and parent cleanup also bypass confirmation so resources cannot remain trapped. `onClose` runs only on actual removal. Existing 5/6-argument UiWindow constructors and accessors remain; the record now also has footer/closeRequest components.

New APIs require `acbric_api >=0.3.3-dev.19`. No multiline editing, double-click word selection, undo/redo, scrollbar dragging or dedicated IME candidate panel. UI and documentation continue to support English and Chinese together.

Text fields measure native glyph widths and spacing in one pass and draw only the horizontal viewport. Braces and backslashes are literal, square brackets retain fullwidth display, and underlying text is unchanged.
