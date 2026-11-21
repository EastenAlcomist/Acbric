/* CommandPanelUiRegression.java — 验证命令面板按钮注册隔离、上限、注销和延迟动作。 */
package net.fabricacs.api.impl;

import net.fabricacs.api.combat.CommandPanelUi;
import net.fabricacs.api.combat.CommandPanelAnchor;
import net.fabricacs.api.combat.CommandPanelLayout;
import net.fabricacs.api.combat.CommandPanelPlacement;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class CommandPanelUiRegression {
    private static int checks;

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
        System.out.println("PASS COMMAND PANEL UI: " + message);
    }

    public static int run() {
        checks = 0;
        String owner = "command_panel_test";
        CommandPanelUi ui = new CommandPanelUi(owner);
        Object screen = new Object();
        Object panel = new Object();
        CommandPanelUi.CommandPanelContext context = new CommandPanelUi.CommandPanelContext(screen, panel);
        AtomicInteger labels = new AtomicInteger();
        AtomicInteger actions = new AtomicInteger();
        List<CommandPanelUi.Registration> registrations = new ArrayList<>();
        try {
            CommandPanelUi.Registration first = ui.registerButton("first",
                    value -> {
                        labels.incrementAndGet();
                        return "First";
                    },
                    value -> actions.incrementAndGet());
            registrations.add(first);
            check(first.id().equals("first") && first.isRegistered(), "registration exposes stable id and active state");
            check(labels.get() == 0, "dynamic label is not evaluated during registration");
            check(snapshot(owner).size() == 1, "registered button is visible in immutable snapshot");
            check(snapshot(owner).getFirst().placement.equals(CommandPanelPlacement.DEFAULT),
                    "legacy registration keeps default panel-above placement");
            try {
                ui.registerButton("first", "Duplicate", () -> { });
                throw new AssertionError("duplicate accepted");
            } catch (IllegalStateException expected) {
                check(true, "duplicate button id rejected");
            }
            registrations.add(ui.registerButton("second", "Second", () -> { },
                    CommandPanelPlacement.at(CommandPanelAnchor.WINDOW_TOP_RIGHT, -12, 14)));
            check(snapshot(owner).stream().filter(value -> value.id.equals("second"))
                            .findFirst().orElseThrow().placement
                            .equals(CommandPanelPlacement.at(CommandPanelAnchor.WINDOW_TOP_RIGHT, -12, 14)),
                    "custom window anchor and offset retained");
            check(CommandPanelPlacement.vertical(CommandPanelAnchor.WINDOW_CENTER_LEFT, 20, 0).layout()
                            == CommandPanelLayout.VERTICAL,
                    "vertical placement retains sidebar layout");
            for (String id : List.of("third", "fourth")) {
                registrations.add(ui.registerButton(id, id, () -> { }));
            }
            try {
                ui.registerButton("fifth", "Fifth", () -> { });
                throw new AssertionError("fifth button accepted");
            } catch (IllegalStateException expected) {
                check(true, "four buttons per MOD enforced");
            }
            try {
                new CommandPanelUi("Bad Id");
                throw new AssertionError("invalid MOD id accepted");
            } catch (IllegalArgumentException expected) {
                check(true, "invalid MOD id rejected");
            }
            try {
                ui.registerButton("Bad Id", "Bad", () -> { });
                throw new AssertionError("invalid button id accepted");
            } catch (IllegalArgumentException expected) {
                check(true, "invalid button id rejected");
            }
            try {
                CommandPanelPlacement.at(CommandPanelAnchor.WINDOW_CENTER, 4097, 0);
                throw new AssertionError("oversized placement accepted");
            } catch (IllegalArgumentException expected) {
                check(true, "placement offsets bounded");
            }
            CommandPanelUiBridge.Position aboveLeft = CommandPanelUiBridge.position(
                    CommandPanelPlacement.at(CommandPanelAnchor.PANEL_ABOVE_LEFT),
                    120, 24, 100, 400, 500, 800, 600, 10, 30, 8);
            CommandPanelUiBridge.Position aboveCenter = CommandPanelUiBridge.position(
                    CommandPanelPlacement.at(CommandPanelAnchor.PANEL_ABOVE_CENTER),
                    120, 24, 100, 400, 500, 800, 600, 10, 30, 8);
            CommandPanelUiBridge.Position aboveRight = CommandPanelUiBridge.position(
                    CommandPanelPlacement.at(CommandPanelAnchor.PANEL_ABOVE_RIGHT),
                    120, 24, 100, 400, 500, 800, 600, 10, 30, 8);
            check(aboveLeft.equals(new CommandPanelUiBridge.Position(100, 468))
                            && aboveCenter.equals(new CommandPanelUiBridge.Position(240, 468))
                            && aboveRight.equals(new CommandPanelUiBridge.Position(380, 468)),
                    "panel anchors preserve above-left center and right layout");
            CommandPanelUiBridge.Position windowTopLeft = CommandPanelUiBridge.position(
                    CommandPanelPlacement.at(CommandPanelAnchor.WINDOW_TOP_LEFT),
                    120, 24, 100, 400, 500, 800, 600, 10, 30, 8);
            CommandPanelUiBridge.Position windowTopCenter = CommandPanelUiBridge.position(
                    CommandPanelPlacement.at(CommandPanelAnchor.WINDOW_TOP_CENTER),
                    120, 24, 100, 400, 500, 800, 600, 10, 30, 8);
            CommandPanelUiBridge.Position windowTopRight = CommandPanelUiBridge.position(
                    CommandPanelPlacement.at(CommandPanelAnchor.WINDOW_TOP_RIGHT),
                    120, 24, 100, 400, 500, 800, 600, 10, 30, 8);
            check(windowTopLeft.equals(new CommandPanelUiBridge.Position(10, 30))
                            && windowTopCenter.equals(new CommandPanelUiBridge.Position(340, 30))
                            && windowTopRight.equals(new CommandPanelUiBridge.Position(670, 30)),
                    "window top anchors ignore native panel position");
            CommandPanelUiBridge.Position windowCenter = CommandPanelUiBridge.position(
                    CommandPanelPlacement.at(CommandPanelAnchor.WINDOW_CENTER),
                    120, 24, 100, 400, 500, 800, 600, 10, 30, 8);
            CommandPanelUiBridge.Position windowBottomRight = CommandPanelUiBridge.position(
                    CommandPanelPlacement.at(CommandPanelAnchor.WINDOW_BOTTOM_RIGHT, 100, 100),
                    120, 24, 100, 400, 500, 800, 600, 10, 30, 8);
            check(windowCenter.equals(new CommandPanelUiBridge.Position(340, 288)),
                    "window center anchor uses viewport center");
            CommandPanelUiBridge.Position sidebar = CommandPanelUiBridge.position(
                    CommandPanelPlacement.vertical(CommandPanelAnchor.WINDOW_CENTER_LEFT, 20, 0),
                    180, 132, 100, 400, 500, 800, 600, 10, 30, 8);
            check(sidebar.equals(new CommandPanelUiBridge.Position(30, 234)),
                    "vertical sidebar uses grouped height for center-left anchor");
            check(windowBottomRight.equals(new CommandPanelUiBridge.Position(670, 566)),
                    "offsets clamp inside window safe area");
            CommandPanelUiRegistry.Entry entry = snapshot(owner).getFirst();
            entry.activate(context);
            check(actions.get() == 1, "activation forwards context exactly once");
            for (CommandPanelUi.Registration registration : registrations) registration.close();
            check(snapshot(owner).isEmpty(), "closing all registrations removes buttons");
            first.close();
            check(snapshot(owner).isEmpty(), "registration close is idempotent");
            entry.activate(context);
            check(actions.get() == 1, "closed registration cannot activate");
            CommandPanelUi.Registration replacement = ui.registerButton("first", "Again", () -> { });
            check(snapshot(owner).size() == 1, "closed id can be registered again");
            replacement.close();
            CommandPanelUi.Registration label = ui.registerLabel("status", contextValue -> "Ready",
                    CommandPanelPlacement.at(CommandPanelAnchor.WINDOW_TOP_LEFT));
            boolean[] toggled = {false};
            CommandPanelUi.Registration toggle = ui.registerToggle("toggle",
                    contextValue -> "Enabled", contextValue -> toggled[0],
                    (contextValue, value) -> toggled[0] = value,
                    CommandPanelPlacement.at(CommandPanelAnchor.WINDOW_BOTTOM_CENTER));
            List<CommandPanelUiRegistry.Entry> mixed = snapshot(owner);
            check(mixed.size() == 2 && mixed.get(0).kind == CommandPanelUiRegistry.Kind.LABEL
                            && mixed.get(1).kind == CommandPanelUiRegistry.Kind.TOGGLE,
                    "status label and native toggle share the command-panel registry");
            mixed.get(1).toggle(context);
            check(toggled[0], "toggle reads current state and sends the inverse value");
            mixed.get(0).activate(context);
            check(actions.get() == 1, "status label has no activation action");
            label.close();
            toggle.close();
            check(snapshot(owner).isEmpty(), "test owner leaves no global entries");
            return checks;
        } finally {
            for (CommandPanelUi.Registration registration : registrations) registration.close();
        }
    }

    private static List<CommandPanelUiRegistry.Entry> snapshot(String owner) {
        return CommandPanelUiRegistry.snapshot().stream().filter(entry -> entry.owner.equals(owner)).toList();
    }
}
