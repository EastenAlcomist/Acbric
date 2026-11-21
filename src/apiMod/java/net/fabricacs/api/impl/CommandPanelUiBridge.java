/*
 * CommandPanelUiBridge.java — 按锚点与布局分组命令面板项目，并接入原生 MyDraw/Hooks。
 */
package net.fabricacs.api.impl;

import com.zarkonnen.airships.AGame;
import com.zarkonnen.airships.CombatIntent;
import com.zarkonnen.airships.CommandButtonsPanel;
import com.zarkonnen.airships.DirectControlPanel;
import com.zarkonnen.airships.MyDraw;
import com.zarkonnen.airships.PlaybackIntent;
import com.zarkonnen.airships.UniScreen;
import com.zarkonnen.catengine.util.ScreenMode;
import net.fabricacs.api.combat.CommandPanelAnchor;
import net.fabricacs.api.combat.CommandPanelLayout;
import net.fabricacs.api.combat.CommandPanelPlacement;
import net.fabricacs.api.combat.CommandPanelUi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CommandPanelUiBridge {
    private CommandPanelUiBridge() {
    }

    public static void draw(CommandButtonsPanel panel, MyDraw draw, ScreenMode mode,
                            UniScreen screen, int panelTop) {
        List<CommandPanelUiRegistry.Entry> registered = CommandPanelUiRegistry.snapshot();
        if (registered.isEmpty() || !active(panel, mode, screen, panelTop)) return;

        Map<CommandPanelPlacement, List<CommandPanelUiRegistry.Entry>> groups = new LinkedHashMap<>();
        for (CommandPanelUiRegistry.Entry entry : registered) {
            if (entry.isRegistered()) {
                groups.computeIfAbsent(entry.placement, ignored -> new ArrayList<>()).add(entry);
            }
        }
        CommandPanelUi.CommandPanelContext context = new CommandPanelUi.CommandPanelContext(screen, panel);
        for (Map.Entry<CommandPanelPlacement, List<CommandPanelUiRegistry.Entry>> group : groups.entrySet()) {
            drawGroup(panel, draw, mode, screen, panelTop, context, group.getKey(), group.getValue());
        }
    }

    private static void drawGroup(CommandButtonsPanel panel, MyDraw draw, ScreenMode mode,
                                  UniScreen screen, int panelTop,
                                  CommandPanelUi.CommandPanelContext context,
                                  CommandPanelPlacement placement,
                                  List<CommandPanelUiRegistry.Entry> entries) {
        int side = Math.max(4, MyDraw.SIDE_CLEARANCE);
        int available = Math.max(56, mode.width - side * 2);
        int gap = Math.max(4, MyDraw.BUTTON_SPACING);
        List<Candidate> candidates = evaluate(draw, context, entries, available);
        if (candidates.isEmpty()) return;

        int buttonHeight = MyDraw.BUTTON_H;
        if (placement.layout() == CommandPanelLayout.VERTICAL) {
            int width = candidates.stream().mapToInt(Candidate::naturalWidth).max().orElse(56);
            int height = candidates.size() * buttonHeight + (candidates.size() - 1) * gap;
            Position position = position(placement, width, height, panel.panelX, panel.panelW,
                    panelTop, mode.width, mode.height, side, Math.max(side, MyDraw.TOP_BAR_H + 8),
                    Math.max(4, MyDraw.UI_SPACING));
            drawWindow(draw, position, width, height);
            int y = position.y;
            for (Candidate candidate : candidates) {
                drawItem(draw, screen, panel, context, candidate, position.x, y, width);
                y += buttonHeight + gap;
            }
            return;
        }

        List<Candidate> fitted = new ArrayList<>(candidates.size());
        int used = 0;
        for (Candidate candidate : candidates) {
            int remaining = available - used - (fitted.isEmpty() ? 0 : gap);
            if (remaining < 56) break;
            int width = Math.min(candidate.naturalWidth, remaining);
            fitted.add(candidate.withWidth(width));
            used += width + (fitted.size() == 1 ? 0 : gap);
        }
        if (fitted.isEmpty()) return;

        int width = Math.max(1, used);
        Position position = position(placement, width, buttonHeight, panel.panelX, panel.panelW,
                panelTop, mode.width, mode.height, side, Math.max(side, MyDraw.TOP_BAR_H + 8),
                Math.max(4, MyDraw.UI_SPACING));
        drawWindow(draw, position, width, buttonHeight);
        int x = position.x;
        for (Candidate candidate : fitted) {
            drawItem(draw, screen, panel, context, candidate, x, position.y, candidate.width);
            x += candidate.width + gap;
        }
    }

    private static List<Candidate> evaluate(MyDraw draw, CommandPanelUi.CommandPanelContext context,
                                            List<CommandPanelUiRegistry.Entry> entries, int available) {
        List<Candidate> candidates = new ArrayList<>(entries.size());
        for (CommandPanelUiRegistry.Entry entry : entries) {
            if (!entry.isRegistered()) continue;
            try {
                String label = displayText(entry.label.apply(context), entry.id, 128);
                String tooltip = displayText(entry.tooltip.apply(context), "", 512);
                boolean enabled = entry.enabled.test(context);
                boolean checked = entry.kind == CommandPanelUiRegistry.Kind.TOGGLE
                        && Boolean.TRUE.equals(entry.checked.apply(context));
                int rawWidth = switch (entry.kind) {
                    case BUTTON -> draw.bw(label);
                    case TOGGLE -> draw.tw(label);
                    case LABEL -> draw.tw(label) + 12;
                };
                int naturalWidth = Math.min(available, Math.min(260, Math.max(56, rawWidth)));
                candidates.add(new Candidate(entry, label, tooltip, enabled, checked, naturalWidth, naturalWidth));
                entry.drawSucceeded();
            } catch (RuntimeException | LinkageError failure) {
                entry.reportDrawFailure(failure);
            }
        }
        return candidates;
    }

    private static void drawWindow(MyDraw draw, Position position, int width, int height) {
        int inset = Math.max(2, MyDraw.WINDOW_INSET / 2);
        draw.drawWindow(position.x - inset, position.y - inset, width + inset * 2,
                height + inset * 2, 4);
    }

    private static void drawItem(MyDraw draw, UniScreen screen, CommandButtonsPanel panel,
                                 CommandPanelUi.CommandPanelContext context, Candidate item,
                                 int x, int y, int width) {
        try {
            switch (item.entry.kind) {
                case BUTTON -> draw.button(x, y, width, item.label,
                        () -> item.entry.activate(new CommandPanelUi.CommandPanelContext(screen, panel)),
                        item.enabled);
                case TOGGLE -> draw.toggle(x, y, width, item.label, "",
                        input -> item.entry.toggle(new CommandPanelUi.CommandPanelContext(screen, panel)),
                        item.checked, item.enabled);
                case LABEL -> {
                    draw.drawPanel(x, y, width, MyDraw.BUTTON_H, 4);
                    draw.text("[f5edb5]" + item.label, AGame.FOUNT, x + 6,
                            y + Math.max(0, (MyDraw.BUTTON_H - AGame.FOUNT.lineHeight) / 2));
                }
            }
            if (!item.tooltip.isBlank()) {
                draw.tooltip(x, y, width, MyDraw.BUTTON_H, item.tooltip);
            }
        } catch (RuntimeException | LinkageError failure) {
            item.entry.reportDrawFailure(failure);
        }
    }

    static Position position(CommandPanelPlacement placement, int groupWidth, int groupHeight,
                             int panelX, int panelWidth, int panelTop,
                             int windowWidth, int windowHeight, int side, int top, int gap) {
        CommandPanelAnchor anchor = placement.anchor();
        int x;
        int y;
        if (anchor == CommandPanelAnchor.PANEL_ABOVE_LEFT) {
            x = panelX;
            y = panelTop - groupHeight - gap;
        } else if (anchor == CommandPanelAnchor.PANEL_ABOVE_CENTER) {
            x = panelX + panelWidth / 2 - groupWidth / 2;
            y = panelTop - groupHeight - gap;
        } else if (anchor == CommandPanelAnchor.PANEL_ABOVE_RIGHT) {
            x = panelX + panelWidth - groupWidth;
            y = panelTop - groupHeight - gap;
        } else {
            x = horizontal(anchor, groupWidth, windowWidth, side);
            y = vertical(anchor, groupHeight, windowHeight, side, top);
        }

        int maxX = Math.max(side, windowWidth - side - groupWidth);
        int maxY = Math.max(top, windowHeight - side - groupHeight);
        return new Position(
                Math.max(side, Math.min(maxX, x + placement.dx())),
                Math.max(top, Math.min(maxY, y + placement.dy()))
        );
    }

    private static int horizontal(CommandPanelAnchor anchor, int width, int windowWidth, int side) {
        return switch (anchor) {
            case WINDOW_TOP_LEFT, WINDOW_CENTER_LEFT, WINDOW_BOTTOM_LEFT -> side;
            case WINDOW_TOP_CENTER, WINDOW_CENTER, WINDOW_BOTTOM_CENTER ->
                    windowWidth / 2 - width / 2;
            case WINDOW_TOP_RIGHT, WINDOW_CENTER_RIGHT, WINDOW_BOTTOM_RIGHT ->
                    windowWidth - side - width;
            default -> throw new IllegalArgumentException("Window anchor required: " + anchor);
        };
    }

    private static int vertical(CommandPanelAnchor anchor, int height, int windowHeight, int side, int top) {
        return switch (anchor) {
            case WINDOW_TOP_LEFT, WINDOW_TOP_CENTER, WINDOW_TOP_RIGHT -> top;
            case WINDOW_CENTER_LEFT, WINDOW_CENTER, WINDOW_CENTER_RIGHT ->
                    windowHeight / 2 - height / 2;
            case WINDOW_BOTTOM_LEFT, WINDOW_BOTTOM_CENTER, WINDOW_BOTTOM_RIGHT ->
                    windowHeight - side - height;
            default -> throw new IllegalArgumentException("Window anchor required: " + anchor);
        };
    }

    private static boolean active(CommandButtonsPanel panel, ScreenMode mode, UniScreen screen, int panelTop) {
        return panel != null && mode != null && screen != null && panelTop > 0
                && panel.panelW > 0 && panel.panelH > 0
                && !screen.hideUI && screen.combat != null && screen.combat.startCountdown <= 0
                && screen.tool == UniScreen.NAVIGATE
                && screen.intent instanceof CombatIntent intent
                && !(intent instanceof PlaybackIntent)
                && !intent.spectate()
                && DirectControlPanel.getShip(screen) == null;
    }

    private static String displayText(String value, String fallback, int limit) {
        if (value == null) return fallback;
        StringBuilder text = new StringBuilder(Math.min(value.length(), limit));
        value.codePoints()
                .map(codePoint -> Character.isISOControl(codePoint) ? ' ' : codePoint)
                .limit(limit)
                .forEach(text::appendCodePoint);
        String result = text.toString().strip();
        return result.isEmpty() ? fallback : result;
    }

    record Position(int x, int y) {
    }

    private record Candidate(CommandPanelUiRegistry.Entry entry, String label, String tooltip,
                             boolean enabled, boolean checked, int naturalWidth, int width) {
        private Candidate withWidth(int value) {
            return new Candidate(entry, label, tooltip, enabled, checked, naturalWidth, value);
        }
    }
}
