package net.fabricacs.api.event;

public final class AirshipsCombatUiEvents {
    public static final Event<ShipStatusBarBeforeDraw> SHIP_STATUS_BAR_BEFORE_DRAW = new Event<>(listeners -> context -> {
        for (ShipStatusBarBeforeDraw listener : listeners) {
            EventResult result = listener.beforeShipStatusBarDraw(context);
            if (result == EventResult.CANCEL) {
                return EventResult.CANCEL;
            }
        }
        return EventResult.PASS;
    });

    public static final Event<ShipStatusBarAfterDraw> SHIP_STATUS_BAR_AFTER_DRAW = new Event<>(listeners -> context -> {
        for (ShipStatusBarAfterDraw listener : listeners) {
            listener.afterShipStatusBarDraw(context);
        }
    });

    public static final Event<PlayerControlPanelBeforeDraw> PLAYER_CONTROL_PANEL_BEFORE_DRAW = new Event<>(listeners -> context -> {
        for (PlayerControlPanelBeforeDraw listener : listeners) {
            EventResult result = listener.beforePlayerControlPanelDraw(context);
            if (result == EventResult.CANCEL) {
                return EventResult.CANCEL;
            }
        }
        return EventResult.PASS;
    });

    public static final Event<PlayerControlPanelAfterDraw> PLAYER_CONTROL_PANEL_AFTER_DRAW = new Event<>(listeners -> context -> {
        for (PlayerControlPanelAfterDraw listener : listeners) {
            listener.afterPlayerControlPanelDraw(context);
        }
    });

    public static final Event<PlayerControlPanelBeforeTick> PLAYER_CONTROL_PANEL_BEFORE_TICK = new Event<>(listeners -> context -> {
        for (PlayerControlPanelBeforeTick listener : listeners) {
            EventResult result = listener.beforePlayerControlPanelTick(context);
            if (result == EventResult.CANCEL) {
                return EventResult.CANCEL;
            }
        }
        return EventResult.PASS;
    });

    public static final Event<PlayerControlPanelAfterTick> PLAYER_CONTROL_PANEL_AFTER_TICK = new Event<>(listeners -> context -> {
        for (PlayerControlPanelAfterTick listener : listeners) {
            listener.afterPlayerControlPanelTick(context);
        }
    });

    // ---- One-shot weapons panel (DirectControlPanel, weapon area) ----------

    public static final Event<PlayerControlPanelBeforeDraw> ONE_SHOT_WEAPONS_BEFORE_DRAW = new Event<>(listeners -> context -> {
        for (PlayerControlPanelBeforeDraw listener : listeners) {
            EventResult result = listener.beforePlayerControlPanelDraw(context);
            if (result == EventResult.CANCEL) {
                return EventResult.CANCEL;
            }
        }
        return EventResult.PASS;
    });

    public static final Event<PlayerControlPanelAfterDraw> ONE_SHOT_WEAPONS_AFTER_DRAW = new Event<>(listeners -> context -> {
        for (PlayerControlPanelAfterDraw listener : listeners) {
            listener.afterPlayerControlPanelDraw(context);
        }
    });

    public static final Event<PlayerControlPanelBeforeTick> ONE_SHOT_WEAPONS_BEFORE_TICK = new Event<>(listeners -> context -> {
        for (PlayerControlPanelBeforeTick listener : listeners) {
            EventResult result = listener.beforePlayerControlPanelTick(context);
            if (result == EventResult.CANCEL) {
                return EventResult.CANCEL;
            }
        }
        return EventResult.PASS;
    });

    public static final Event<PlayerControlPanelAfterTick> ONE_SHOT_WEAPONS_AFTER_TICK = new Event<>(listeners -> context -> {
        for (PlayerControlPanelAfterTick listener : listeners) {
            listener.afterPlayerControlPanelTick(context);
        }
    });

    // ---- One-shot buoyancy panel -------------------------------------------

    public static final Event<PlayerControlPanelBeforeDraw> ONE_SHOT_BUOYANCY_BEFORE_DRAW = new Event<>(listeners -> context -> {
        for (PlayerControlPanelBeforeDraw listener : listeners) {
            EventResult r = listener.beforePlayerControlPanelDraw(context);
            if (r == EventResult.CANCEL) return EventResult.CANCEL;
        }
        return EventResult.PASS;
    });
    public static final Event<PlayerControlPanelAfterDraw> ONE_SHOT_BUOYANCY_AFTER_DRAW = new Event<>(listeners -> context -> {
        for (PlayerControlPanelAfterDraw listener : listeners) {
            listener.afterPlayerControlPanelDraw(context);
        }
    });
    public static final Event<PlayerControlPanelBeforeTick> ONE_SHOT_BUOYANCY_BEFORE_TICK = new Event<>(listeners -> context -> {
        for (PlayerControlPanelBeforeTick listener : listeners) {
            EventResult r = listener.beforePlayerControlPanelTick(context);
            if (r == EventResult.CANCEL) return EventResult.CANCEL;
        }
        return EventResult.PASS;
    });
    public static final Event<PlayerControlPanelAfterTick> ONE_SHOT_BUOYANCY_AFTER_TICK = new Event<>(listeners -> context -> {
        for (PlayerControlPanelAfterTick listener : listeners) {
            listener.afterPlayerControlPanelTick(context);
        }
    });

    // ---- One-shot power panel -----------------------------------------------

    public static final Event<PlayerControlPanelBeforeDraw> ONE_SHOT_POWER_BEFORE_DRAW = new Event<>(listeners -> context -> {
        for (PlayerControlPanelBeforeDraw listener : listeners) {
            EventResult r = listener.beforePlayerControlPanelDraw(context);
            if (r == EventResult.CANCEL) return EventResult.CANCEL;
        }
        return EventResult.PASS;
    });
    public static final Event<PlayerControlPanelAfterDraw> ONE_SHOT_POWER_AFTER_DRAW = new Event<>(listeners -> context -> {
        for (PlayerControlPanelAfterDraw listener : listeners) {
            listener.afterPlayerControlPanelDraw(context);
        }
    });
    public static final Event<PlayerControlPanelBeforeTick> ONE_SHOT_POWER_BEFORE_TICK = new Event<>(listeners -> context -> {
        for (PlayerControlPanelBeforeTick listener : listeners) {
            EventResult r = listener.beforePlayerControlPanelTick(context);
            if (r == EventResult.CANCEL) return EventResult.CANCEL;
        }
        return EventResult.PASS;
    });
    public static final Event<PlayerControlPanelAfterTick> ONE_SHOT_POWER_AFTER_TICK = new Event<>(listeners -> context -> {
        for (PlayerControlPanelAfterTick listener : listeners) {
            listener.afterPlayerControlPanelTick(context);
        }
    });

    private AirshipsCombatUiEvents() {
    }

    @FunctionalInterface
    public interface ShipStatusBarBeforeDraw {
        EventResult beforeShipStatusBarDraw(ShipStatusBarContext context);
    }

    @FunctionalInterface
    public interface ShipStatusBarAfterDraw {
        void afterShipStatusBarDraw(ShipStatusBarContext context);
    }

    @FunctionalInterface
    public interface PlayerControlPanelBeforeDraw {
        EventResult beforePlayerControlPanelDraw(PlayerControlPanelDrawContext context);
    }

    @FunctionalInterface
    public interface PlayerControlPanelAfterDraw {
        void afterPlayerControlPanelDraw(PlayerControlPanelDrawContext context);
    }

    @FunctionalInterface
    public interface PlayerControlPanelBeforeTick {
        EventResult beforePlayerControlPanelTick(PlayerControlPanelTickContext context);
    }

    @FunctionalInterface
    public interface PlayerControlPanelAfterTick {
        void afterPlayerControlPanelTick(PlayerControlPanelTickContext context);
    }
}
