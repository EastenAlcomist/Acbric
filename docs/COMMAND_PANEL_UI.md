# Combat command-panel UI

Acbric 0.3.8 adds a compact command-panel extension surface. MODs register native-style buttons, toggles and status labels through `AcbricModContext.commandPanelUi()`. Acbric lays them out in a native panel window and connects accepted clicks to the game's existing `MyDraw` / `Hooks` path.

See [中文](COMMAND_PANEL_UI.zh-CN.md).

## Example

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
                // Cast panel.screen() / panel.panel() only when using game-specific fields.
                // Shared gameplay must be issued through a deterministic or networked command path.
            },
            CommandPanelPlacement.at(CommandPanelAnchor.WINDOW_TOP_RIGHT, -16, 8));
    }
}
```

The consuming MOD declares:

```json
"depends": {
  "acbric_api": ">=0.3.8"
}
```

## Public methods

`context.commandPanelUi()` returns a `CommandPanelUi` scoped to the current MOD.

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

`CommandPanelContext` exposes `screen()` and `panel()` as `Object`. These are the actual game references from the command-panel draw call. Cast them when using game-specific APIs; Acbric does not invent a version-independent wrapper around selected ships or combat state.

`Registration` provides `id()`, `isRegistered()` and an idempotent `close()`. All three item types count toward the same per-MOD and global limits.

## Anchors, offsets and layout

`CommandPanelPlacement` contains a `CommandPanelAnchor`, horizontal `dx`, vertical `dy`, and `CommandPanelLayout`. Existing overloads without a placement use `PANEL_ABOVE_CENTER` with zero offset and `HORIZONTAL` layout.

| Command-panel anchor | Meaning |
| --- | --- |
| `PANEL_ABOVE_LEFT` | Above the native panel, row left-aligned to the panel. |
| `PANEL_ABOVE_CENTER` | Above the native panel, row centered. Default. |
| `PANEL_ABOVE_RIGHT` | Above the native panel, row right-aligned to the panel. |

| Viewport anchor | Meaning |
| --- | --- |
| `WINDOW_TOP_LEFT` / `WINDOW_TOP_CENTER` / `WINDOW_TOP_RIGHT` | Top of the current game viewport. |
| `WINDOW_CENTER_LEFT` / `WINDOW_CENTER` / `WINDOW_CENTER_RIGHT` | Middle of the current game viewport. |
| `WINDOW_BOTTOM_LEFT` / `WINDOW_BOTTOM_CENTER` / `WINDOW_BOTTOM_RIGHT` | Bottom of the current game viewport. |

“Window” means the current `ScreenMode` viewport, not an operating-system window. Offsets accept `-4096..4096` GUI units. The final row is clamped below the native top bar and inside the screen safe margin, even when an offset would move it off-screen.

`CommandPanelLayout` supports:

- `HORIZONTAL`: registered items share one row and keep their natural widths.
- `VERTICAL`: registered items stack in one native window, use the widest item width, and form a sidebar-style list.

Use `CommandPanelPlacement.vertical(CommandPanelAnchor.WINDOW_CENTER_LEFT, 20, 0)` for a middle-left command sidebar. Equal anchor, offset and layout form one group; any difference forms another group. All groups are hidden whenever the main command panel is not active.

## Lifecycle and layout

- Registration may occur during MOD initialization. It does not open a window or require an active game screen.
- Dynamic label, toggle-state, tooltip and enabled-state functions run during command-panel drawing. They must be fast, read-only and free of blocking I/O.
- Item text is normalized to one line, at most 128 Unicode code points. The tooltip is limited to 512 code points.
- Acbric uses native button textures and metrics. Natural widths are clamped to 56–260 GUI units; buttons that no longer fit are omitted rather than wrapped or scrolled.
- Buttons and toggles use native MyDraw controls. Status labels use the native panel border, game font and compact command-panel height.
- At most four registered items are accepted per MOD and 32 globally. Duplicate `MOD ID + item ID` registrations are rejected.
- The group is drawn only after the main command panel has laid out. It is hidden during the countdown, spectator/playback mode, direct control, a non-navigation tool, hidden UI, or when the panel is not active.
- Placement does not resize or reserve space in the game's original layout. Window anchors can overlap game content or native controls; MOD authors must validate different selections, aircraft commands, one-use modules and display scales.
- Vertical groups have no collapse or scrolling. Their entire height is clamped to the viewport safe area; very tall sidebars can therefore overlap the top or bottom content boundary.

## Click and error behavior

The framework calls native `MyDraw.button` or `MyDraw.toggle` with the same draw/hooks path used by vanilla controls. A button action or toggle change runs only after interaction is accepted by that path. Closed registrations do not run later clicks. Toggle callbacks receive the inverse of the current `checked` supplier value and must update the MOD's own model.

Runtime callback failures are recorded in Acbric diagnostics and do not replace the game screen. A broken label/tooltip/enabled function skips that button for the frame and avoids repeating the same diagnostic every frame until it recovers.

There are no custom icon tiles, cooldowns, collapsible sections, section headers, text input, drag/drop, hotkey registration or scrolling in this first surface. Use the public `Ui` window API for larger interactions.

## Multiplayer and command authority

This API draws and receives a click. It does **not** synchronize gameplay. A command that changes combat state must still use the game's deterministic command/network path, or otherwise follow the MOD's multiplayer authority rules. A local UI counter can remain local; shared effects cannot be assumed to replicate from the button callback alone.

## Verification boundary

The standard UI suite verifies registration isolation, mixed label/toggle/button types, duplicate and capacity rejection, default/custom placement retention, horizontal/vertical layout retention, all anchor families, grouped-height sidebar positioning, offset clamping, dynamic-label deferral, toggle inversion, idempotent close and re-registration. Compilation verifies the real `CommandButtonsPanel` shadow and native draw signatures. An isolated Knot run on game 1.2.15.2 confirms the transformed AFTER injection and bridge classload. GPU placement, overlapping auxiliary panels, DPI/UI scaling, mouse focus switching and multiplayer authority still require targeted in-game acceptance.
