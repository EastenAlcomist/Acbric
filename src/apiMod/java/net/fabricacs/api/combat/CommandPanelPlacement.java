/*
 * CommandPanelPlacement.java — 锚点与像素偏移；绘制时仍限制在安全边距以内。
 */
package net.fabricacs.api.combat;

import java.util.Objects;

public record CommandPanelPlacement(CommandPanelAnchor anchor, int dx, int dy, CommandPanelLayout layout) {
    public static final CommandPanelPlacement DEFAULT =
            new CommandPanelPlacement(CommandPanelAnchor.PANEL_ABOVE_CENTER, 0, 0,
                    CommandPanelLayout.HORIZONTAL);

    public CommandPanelPlacement {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(layout, "layout");
        if (dx < -4096 || dx > 4096 || dy < -4096 || dy > 4096) {
            throw new IllegalArgumentException("Offsets must be in -4096..4096");
        }
    }

    public CommandPanelPlacement(CommandPanelAnchor anchor, int dx, int dy) {
        this(anchor, dx, dy, CommandPanelLayout.HORIZONTAL);
    }

    public static CommandPanelPlacement at(CommandPanelAnchor anchor) {
        return horizontal(anchor, 0, 0);
    }

    public static CommandPanelPlacement at(CommandPanelAnchor anchor, int dx, int dy) {
        return horizontal(anchor, dx, dy);
    }

    public static CommandPanelPlacement horizontal(CommandPanelAnchor anchor, int dx, int dy) {
        return new CommandPanelPlacement(anchor, dx, dy, CommandPanelLayout.HORIZONTAL);
    }

    public static CommandPanelPlacement vertical(CommandPanelAnchor anchor, int dx, int dy) {
        return new CommandPanelPlacement(anchor, dx, dy, CommandPanelLayout.VERTICAL);
    }
}
