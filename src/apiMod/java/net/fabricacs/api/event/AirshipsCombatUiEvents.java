/*
 * AirshipsCombatUiEvents.java — 舰船状态条与面板事件；BEFORE 可取消，AFTER 只在正常返回时执行。
 * ONE_SHOT 是历史重命名面板接口，新代码使用 RENAME_SHIP；完整契约见 EVENTS.md。
 */
package net.fabricacs.api.event;

public final class AirshipsCombatUiEvents {
    /** 状态条绘制前，CANCEL 跳过整次状态条绘制及 AFTER。 */
    public static final Event<ShipStatusBarBeforeDraw> SHIP_STATUS_BAR_BEFORE_DRAW = new Event<>(listeners -> context -> {
        for (ShipStatusBarBeforeDraw listener : listeners) {
            EventResult result = listener.beforeShipStatusBarDraw(context);
            if (result == EventResult.CANCEL) {
                return EventResult.CANCEL;
            }
        }
        return EventResult.PASS;
    });

    /** 状态条绘制正常返回后通知，不可取消。 */
    public static final Event<ShipStatusBarAfterDraw> SHIP_STATUS_BAR_AFTER_DRAW = new Event<>(listeners -> context -> {
        for (ShipStatusBarAfterDraw listener : listeners) {
            listener.afterShipStatusBarDraw(context);
        }
    });

    /** 命令/直接控制面板绘制前，类型由上下文区分。 */
    public static final Event<PlayerControlPanelBeforeDraw> PLAYER_CONTROL_PANEL_BEFORE_DRAW = new Event<>(listeners -> context -> {
        for (PlayerControlPanelBeforeDraw listener : listeners) {
            EventResult result = listener.beforePlayerControlPanelDraw(context);
            if (result == EventResult.CANCEL) {
                return EventResult.CANCEL;
            }
        }
        return EventResult.PASS;
    });

    /** 命令/直接控制面板绘制正常返回后通知。 */
    public static final Event<PlayerControlPanelAfterDraw> PLAYER_CONTROL_PANEL_AFTER_DRAW = new Event<>(listeners -> context -> {
        for (PlayerControlPanelAfterDraw listener : listeners) {
            listener.afterPlayerControlPanelDraw(context);
        }
    });

    /** 命令/直接控制面板输入更新前，CANCEL 跳过该方法。 */
    public static final Event<PlayerControlPanelBeforeTick> PLAYER_CONTROL_PANEL_BEFORE_TICK = new Event<>(listeners -> context -> {
        for (PlayerControlPanelBeforeTick listener : listeners) {
            EventResult result = listener.beforePlayerControlPanelTick(context);
            if (result == EventResult.CANCEL) {
                return EventResult.CANCEL;
            }
        }
        return EventResult.PASS;
    });

    /** 命令/直接控制面板输入更新正常返回后通知。 */
    public static final Event<PlayerControlPanelAfterTick> PLAYER_CONTROL_PANEL_AFTER_TICK = new Event<>(listeners -> context -> {
        for (PlayerControlPanelAfterTick listener : listeners) {
            listener.afterPlayerControlPanelTick(context);
        }
    });

    // ---- 重命名面板新事件，旧回调先执行 ----

    /** 重命名面板 draw 入口；取消将跳过原方法和 AFTER，标签为 RENAME_SHIP。
     * @since 0.3.3-dev.1 */
    public static final Event<PlayerControlPanelBeforeDraw> RENAME_SHIP_BEFORE_DRAW = new Event<>(listeners -> context -> {
        for (PlayerControlPanelBeforeDraw listener : listeners) {
            EventResult result = listener.beforePlayerControlPanelDraw(context);
            if (result == EventResult.CANCEL) {
                return EventResult.CANCEL;
            }
        }
        return EventResult.PASS;
    });

    /** 重命名面板 draw 正常返回后执行，包含无活动对话框时的提前返回。
     * @since 0.3.3-dev.1 */
    public static final Event<PlayerControlPanelAfterDraw> RENAME_SHIP_AFTER_DRAW = new Event<>(listeners -> context -> {
        for (PlayerControlPanelAfterDraw listener : listeners) {
            listener.afterPlayerControlPanelDraw(context);
        }
    });

    /** 重命名面板 tick 入口；取消将跳过原方法和 AFTER，标签为 RENAME_SHIP。
     * @since 0.3.3-dev.1 */
    public static final Event<PlayerControlPanelBeforeTick> RENAME_SHIP_BEFORE_TICK = new Event<>(listeners -> context -> {
        for (PlayerControlPanelBeforeTick listener : listeners) {
            EventResult result = listener.beforePlayerControlPanelTick(context);
            if (result == EventResult.CANCEL) {
                return EventResult.CANCEL;
            }
        }
        return EventResult.PASS;
    });

    /** 重命名面板 tick 正常返回后执行，包含无活动对话框时的提前返回。
     * @since 0.3.3-dev.1 */
    public static final Event<PlayerControlPanelAfterTick> RENAME_SHIP_AFTER_TICK = new Event<>(listeners -> context -> {
        for (PlayerControlPanelAfterTick listener : listeners) {
            listener.afterPlayerControlPanelTick(context);
        }
    });

    // ---- 旧重命名面板事件：仅保留历史兼容语义 ----

    /** @deprecated 历史上实际由 RenameShipPanel 触发，不是一次性设备操作。
     * 请使用 {@link #RENAME_SHIP_BEFORE_DRAW}；旧触发位置与上下文继续保留。 */
    @Deprecated(since = "0.3.3", forRemoval = false)
    public static final Event<PlayerControlPanelBeforeDraw> ONE_SHOT_WEAPONS_BEFORE_DRAW = new Event<>(listeners -> context -> {
        for (PlayerControlPanelBeforeDraw listener : listeners) {
            EventResult result = listener.beforePlayerControlPanelDraw(context);
            if (result == EventResult.CANCEL) {
                return EventResult.CANCEL;
            }
        }
        return EventResult.PASS;
    });

    /** @deprecated 历史上实际由 RenameShipPanel 触发，不是一次性设备操作。
     * 请使用 {@link #RENAME_SHIP_AFTER_DRAW}；旧触发位置与上下文继续保留。 */
    @Deprecated(since = "0.3.3", forRemoval = false)
    public static final Event<PlayerControlPanelAfterDraw> ONE_SHOT_WEAPONS_AFTER_DRAW = new Event<>(listeners -> context -> {
        for (PlayerControlPanelAfterDraw listener : listeners) {
            listener.afterPlayerControlPanelDraw(context);
        }
    });

    /** @deprecated 历史上实际由 RenameShipPanel 触发，不是一次性设备操作。
     * 请使用 {@link #RENAME_SHIP_BEFORE_TICK}；旧触发位置与上下文继续保留。 */
    @Deprecated(since = "0.3.3", forRemoval = false)
    public static final Event<PlayerControlPanelBeforeTick> ONE_SHOT_WEAPONS_BEFORE_TICK = new Event<>(listeners -> context -> {
        for (PlayerControlPanelBeforeTick listener : listeners) {
            EventResult result = listener.beforePlayerControlPanelTick(context);
            if (result == EventResult.CANCEL) {
                return EventResult.CANCEL;
            }
        }
        return EventResult.PASS;
    });

    /** @deprecated 历史上实际由 RenameShipPanel 触发，不是一次性设备操作。
     * 请使用 {@link #RENAME_SHIP_AFTER_TICK}；旧触发位置与上下文继续保留。 */
    @Deprecated(since = "0.3.3", forRemoval = false)
    public static final Event<PlayerControlPanelAfterTick> ONE_SHOT_WEAPONS_AFTER_TICK = new Event<>(listeners -> context -> {
        for (PlayerControlPanelAfterTick listener : listeners) {
            listener.afterPlayerControlPanelTick(context);
        }
    });

    // ---- 旧重命名面板事件：仅保留历史兼容语义 ----

    /** @deprecated 历史上实际由 RenameShipPanel 触发，不是一次性设备操作。
     * 请使用 {@link #RENAME_SHIP_BEFORE_DRAW}；旧触发位置与上下文继续保留。 */
    @Deprecated(since = "0.3.3", forRemoval = false)
    public static final Event<PlayerControlPanelBeforeDraw> ONE_SHOT_BUOYANCY_BEFORE_DRAW = new Event<>(listeners -> context -> {
        for (PlayerControlPanelBeforeDraw listener : listeners) {
            EventResult r = listener.beforePlayerControlPanelDraw(context);
            if (r == EventResult.CANCEL) return EventResult.CANCEL;
        }
        return EventResult.PASS;
    });
    /** @deprecated 历史上实际由 RenameShipPanel 触发，不是一次性设备操作。
     * 请使用 {@link #RENAME_SHIP_AFTER_DRAW}；旧触发位置与上下文继续保留。 */
    @Deprecated(since = "0.3.3", forRemoval = false)
    public static final Event<PlayerControlPanelAfterDraw> ONE_SHOT_BUOYANCY_AFTER_DRAW = new Event<>(listeners -> context -> {
        for (PlayerControlPanelAfterDraw listener : listeners) {
            listener.afterPlayerControlPanelDraw(context);
        }
    });
    /** @deprecated 历史上实际由 RenameShipPanel 触发，不是一次性设备操作。
     * 请使用 {@link #RENAME_SHIP_BEFORE_TICK}；旧触发位置与上下文继续保留。 */
    @Deprecated(since = "0.3.3", forRemoval = false)
    public static final Event<PlayerControlPanelBeforeTick> ONE_SHOT_BUOYANCY_BEFORE_TICK = new Event<>(listeners -> context -> {
        for (PlayerControlPanelBeforeTick listener : listeners) {
            EventResult r = listener.beforePlayerControlPanelTick(context);
            if (r == EventResult.CANCEL) return EventResult.CANCEL;
        }
        return EventResult.PASS;
    });
    /** @deprecated 历史上实际由 RenameShipPanel 触发，不是一次性设备操作。
     * 请使用 {@link #RENAME_SHIP_AFTER_TICK}；旧触发位置与上下文继续保留。 */
    @Deprecated(since = "0.3.3", forRemoval = false)
    public static final Event<PlayerControlPanelAfterTick> ONE_SHOT_BUOYANCY_AFTER_TICK = new Event<>(listeners -> context -> {
        for (PlayerControlPanelAfterTick listener : listeners) {
            listener.afterPlayerControlPanelTick(context);
        }
    });

    // ---- 旧重命名面板事件：仅保留历史兼容语义 ----

    /** @deprecated 历史上实际由 RenameShipPanel 触发，不是一次性设备操作。
     * 请使用 {@link #RENAME_SHIP_BEFORE_DRAW}；旧触发位置与上下文继续保留。 */
    @Deprecated(since = "0.3.3", forRemoval = false)
    public static final Event<PlayerControlPanelBeforeDraw> ONE_SHOT_POWER_BEFORE_DRAW = new Event<>(listeners -> context -> {
        for (PlayerControlPanelBeforeDraw listener : listeners) {
            EventResult r = listener.beforePlayerControlPanelDraw(context);
            if (r == EventResult.CANCEL) return EventResult.CANCEL;
        }
        return EventResult.PASS;
    });
    /** @deprecated 历史上实际由 RenameShipPanel 触发，不是一次性设备操作。
     * 请使用 {@link #RENAME_SHIP_AFTER_DRAW}；旧触发位置与上下文继续保留。 */
    @Deprecated(since = "0.3.3", forRemoval = false)
    public static final Event<PlayerControlPanelAfterDraw> ONE_SHOT_POWER_AFTER_DRAW = new Event<>(listeners -> context -> {
        for (PlayerControlPanelAfterDraw listener : listeners) {
            listener.afterPlayerControlPanelDraw(context);
        }
    });
    /** @deprecated 历史上实际由 RenameShipPanel 触发，不是一次性设备操作。
     * 请使用 {@link #RENAME_SHIP_BEFORE_TICK}；旧触发位置与上下文继续保留。 */
    @Deprecated(since = "0.3.3", forRemoval = false)
    public static final Event<PlayerControlPanelBeforeTick> ONE_SHOT_POWER_BEFORE_TICK = new Event<>(listeners -> context -> {
        for (PlayerControlPanelBeforeTick listener : listeners) {
            EventResult r = listener.beforePlayerControlPanelTick(context);
            if (r == EventResult.CANCEL) return EventResult.CANCEL;
        }
        return EventResult.PASS;
    });
    /** @deprecated 历史上实际由 RenameShipPanel 触发，不是一次性设备操作。
     * 请使用 {@link #RENAME_SHIP_AFTER_TICK}；旧触发位置与上下文继续保留。 */
    @Deprecated(since = "0.3.3", forRemoval = false)
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
