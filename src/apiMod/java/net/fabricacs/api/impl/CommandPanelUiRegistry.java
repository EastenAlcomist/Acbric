/*
 * CommandPanelUiRegistry.java — 按 MOD 隔离命令面板按钮、开关和状态标签；快照供绘制热路径无锁读取。
 */
package net.fabricacs.api.impl;

import net.fabricacs.api.combat.CommandPanelPlacement;
import net.fabricacs.api.combat.CommandPanelUi;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public final class CommandPanelUiRegistry {
    private static final int MAX_ITEMS = 32;
    private static final int MAX_PER_MOD = 4;
    private static final Object LOCK = new Object();
    private static final List<Entry> EMPTY = List.of();
    private static volatile List<Entry> entries = EMPTY;

    private CommandPanelUiRegistry() {
    }

    public static CommandPanelUi.Registration registerButton(
            String owner, String id,
            Function<CommandPanelUi.CommandPanelContext, String> label,
            Function<CommandPanelUi.CommandPanelContext, String> tooltip,
            Predicate<CommandPanelUi.CommandPanelContext> enabled,
            Consumer<CommandPanelUi.CommandPanelContext> action,
            CommandPanelPlacement placement) {
        return add(owner, id, Kind.BUTTON, label, tooltip, enabled, action, null, null, placement);
    }

    public static CommandPanelUi.Registration registerLabel(
            String owner, String id,
            Function<CommandPanelUi.CommandPanelContext, String> text,
            Function<CommandPanelUi.CommandPanelContext, String> tooltip,
            CommandPanelPlacement placement) {
        return add(owner, id, Kind.LABEL, text, tooltip, context -> true,
                null, null, null, placement);
    }

    public static CommandPanelUi.Registration registerToggle(
            String owner, String id,
            Function<CommandPanelUi.CommandPanelContext, String> label,
            Function<CommandPanelUi.CommandPanelContext, Boolean> checked,
            Function<CommandPanelUi.CommandPanelContext, String> tooltip,
            Predicate<CommandPanelUi.CommandPanelContext> enabled,
            BiConsumer<CommandPanelUi.CommandPanelContext, Boolean> change,
            CommandPanelPlacement placement) {
        return add(owner, id, Kind.TOGGLE, label, tooltip, enabled,
                null, checked, change, placement);
    }

    private static CommandPanelUi.Registration add(
            String owner, String id, Kind kind,
            Function<CommandPanelUi.CommandPanelContext, String> label,
            Function<CommandPanelUi.CommandPanelContext, String> tooltip,
            Predicate<CommandPanelUi.CommandPanelContext> enabled,
            Consumer<CommandPanelUi.CommandPanelContext> action,
            Function<CommandPanelUi.CommandPanelContext, Boolean> checked,
            BiConsumer<CommandPanelUi.CommandPanelContext, Boolean> change,
            CommandPanelPlacement placement) {
        validateId(owner, "MOD ID");
        validateId(id, "Control ID");
        synchronized (LOCK) {
            List<Entry> current = entries;
            if (current.size() >= MAX_ITEMS) {
                throw new IllegalStateException("At most " + MAX_ITEMS + " command panel items");
            }
            if (current.stream().filter(entry -> entry.owner.equals(owner)).count() >= MAX_PER_MOD) {
                throw new IllegalStateException("At most " + MAX_PER_MOD + " command panel items per MOD");
            }
            if (current.stream().anyMatch(entry -> entry.owner.equals(owner) && entry.id.equals(id))) {
                throw new IllegalStateException("Duplicate command panel item: " + owner + ":" + id);
            }
            List<Entry> next = new ArrayList<>(current.size() + 1);
            next.addAll(current);
            Entry entry = new Entry(owner, id, kind, label, tooltip, enabled,
                    action, checked, change, placement);
            next.add(entry);
            entries = List.copyOf(next);
            return entry;
        }
    }

    public static List<Entry> snapshot() {
        return entries;
    }

    private static void validateId(String value, String label) {
        if (value == null || !value.matches("[a-z][a-z0-9_-]{0,63}")) {
            throw new IllegalArgumentException(label);
        }
    }

    public enum Kind {
        BUTTON,
        TOGGLE,
        LABEL
    }

    public static final class Entry implements CommandPanelUi.Registration {
        final String owner;
        final String id;
        final Kind kind;
        final Function<CommandPanelUi.CommandPanelContext, String> label;
        final Function<CommandPanelUi.CommandPanelContext, String> tooltip;
        final Predicate<CommandPanelUi.CommandPanelContext> enabled;
        final Consumer<CommandPanelUi.CommandPanelContext> action;
        final Function<CommandPanelUi.CommandPanelContext, Boolean> checked;
        final BiConsumer<CommandPanelUi.CommandPanelContext, Boolean> change;
        final CommandPanelPlacement placement;
        private volatile boolean registered = true;
        private volatile boolean failureReported;

        private Entry(String owner, String id, Kind kind,
                      Function<CommandPanelUi.CommandPanelContext, String> label,
                      Function<CommandPanelUi.CommandPanelContext, String> tooltip,
                      Predicate<CommandPanelUi.CommandPanelContext> enabled,
                      Consumer<CommandPanelUi.CommandPanelContext> action,
                      Function<CommandPanelUi.CommandPanelContext, Boolean> checked,
                      BiConsumer<CommandPanelUi.CommandPanelContext, Boolean> change,
                      CommandPanelPlacement placement) {
            this.owner = owner;
            this.id = id;
            this.kind = kind;
            this.label = label;
            this.tooltip = tooltip;
            this.enabled = enabled;
            this.action = action;
            this.checked = checked;
            this.change = change;
            this.placement = placement;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public boolean isRegistered() {
            return registered;
        }

        @Override
        public void close() {
            synchronized (LOCK) {
                if (!registered) return;
                registered = false;
                entries = entries.stream().filter(entry -> entry != this).toList();
            }
        }

        boolean reportDrawFailure(Throwable failure) {
            if (failureReported) return false;
            failureReported = true;
            DiagnosticHub.publish(owner, net.fabricacs.api.diagnostics.DiagnosticMessage.Level.ERROR,
                    "Command panel item failed / 命令面板项目失败: " + id, failure);
            return true;
        }

        void drawSucceeded() {
            failureReported = false;
        }

        void activate(CommandPanelUi.CommandPanelContext context) {
            if (!registered || kind != Kind.BUTTON) return;
            try {
                action.accept(context);
            } catch (RuntimeException | LinkageError failure) {
                reportActionFailure(failure);
            }
        }

        void toggle(CommandPanelUi.CommandPanelContext context) {
            if (!registered || kind != Kind.TOGGLE) return;
            try {
                boolean next = !Boolean.TRUE.equals(checked.apply(context));
                change.accept(context, next);
            } catch (RuntimeException | LinkageError failure) {
                reportActionFailure(failure);
            }
        }

        private void reportActionFailure(Throwable failure) {
            DiagnosticHub.publish(owner, net.fabricacs.api.diagnostics.DiagnosticMessage.Level.ERROR,
                    "Command panel item action failed / 命令面板项目动作失败: " + id, failure);
        }
    }
}
