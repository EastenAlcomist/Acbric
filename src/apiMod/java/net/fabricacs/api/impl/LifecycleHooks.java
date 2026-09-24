/*
 * LifecycleHooks.java — 连接 Mixin 与公开事件：组装上下文并在没有监听器时跳过热路径分配。
 * fire 方法供框架内部调用；MOD 应订阅事件，避免手动制造重复通知。
 */
package net.fabricacs.api.impl;

import net.fabricacs.api.event.AirshipsClientEvents;
import net.fabricacs.api.event.AirshipsCombatUiEvents;
import net.fabricacs.api.event.AirshipsDataEvents;
import net.fabricacs.api.event.AirshipsLifecycleEvents;
import net.fabricacs.api.event.CombatUiPanelType;
import net.fabricacs.api.event.EventResult;
import net.fabricacs.api.event.PlayerControlPanelDrawContext;
import net.fabricacs.api.event.PlayerControlPanelTickContext;
import net.fabricacs.api.event.ShipStatusBarContext;

public final class LifecycleHooks {
    private LifecycleHooks() {}

    // ---- 生命周期 ----

    public static void fireGameStarting(String[] args) {
        AirshipsLifecycleEvents.GAME_STARTING.invoker().onGameStarting(args);
    }

    public static void fireClientCreated(Object game) {
        AirshipsLifecycleEvents.CLIENT_CREATED.invoker().onClientCreated(game);
    }

    public static void fireLoadingScreenCreated(Object loadingScreen) {
        AirshipsLifecycleEvents.LOADING_SCREEN_CREATED.invoker().onLoadingScreenCreated(loadingScreen);
    }

    public static void fireMainMenuCreated(Object mainMenu) {
        AirshipsLifecycleEvents.MAIN_MENU_CREATED.invoker().onMainMenuCreated(mainMenu);
    }

    // ---- 客户端 input 方法前后 ----

    public static void fireClientTickStart(Object airshipGame) {
        AirshipsClientEvents.CLIENT_TICK_START.invoker().onClientTickStart(airshipGame);
    }

    public static void fireClientTickEnd(Object airshipGame) {
        AirshipsClientEvents.CLIENT_TICK_END.invoker().onClientTickEnd(airshipGame);
    }

    // ---- 原版数据加载 ----

    public static void fireDataLoadStarting() {
        AirshipsDataEvents.DATA_LOAD_STARTING.invoker().onDataLoadStarting();
    }

    public static void fireDataLoaded(boolean successful) {
        AirshipsDataEvents.DATA_LOADED.invoker().onDataLoaded(successful);
    }

    // ---- 舰船状态条绘制 ----

    public static boolean fireShipStatusBarBeforeDraw(Object statusBar, Object draw,
            Object mouse, Object airship, Object side, int x, int y,
            int width, int height, Object screenMode, Object screen) {
        if (AirshipsCombatUiEvents.SHIP_STATUS_BAR_BEFORE_DRAW.listenerCount() == 0) {
            return false;
        }
        return AirshipsCombatUiEvents.SHIP_STATUS_BAR_BEFORE_DRAW.invoker()
                .beforeShipStatusBarDraw(new ShipStatusBarContext(statusBar, draw, mouse,
                        airship, side, x, y, width, height, screenMode, screen))
                .shouldCancel();
    }

    public static void fireShipStatusBarAfterDraw(Object statusBar, Object draw,
            Object mouse, Object airship, Object side, int x, int y,
            int width, int height, Object screenMode, Object screen) {
        if (AirshipsCombatUiEvents.SHIP_STATUS_BAR_AFTER_DRAW.listenerCount() == 0) {
            return;
        }
        AirshipsCombatUiEvents.SHIP_STATUS_BAR_AFTER_DRAW.invoker()
                .afterShipStatusBarDraw(new ShipStatusBarContext(statusBar, draw, mouse,
                        airship, side, x, y, width, height, screenMode, screen));
    }

    // ---- 控制面板绘制与输入 ----

    public static boolean firePlayerControlPanelBeforeDraw(CombatUiPanelType panelType,
            Object panel, Object draw, Object mouse, Object screenMode,
            Object hooks, Object screen) {
        if (AirshipsCombatUiEvents.PLAYER_CONTROL_PANEL_BEFORE_DRAW.listenerCount() == 0) {
            return false;
        }
        return AirshipsCombatUiEvents.PLAYER_CONTROL_PANEL_BEFORE_DRAW.invoker()
                .beforePlayerControlPanelDraw(new PlayerControlPanelDrawContext(
                        panelType, panel, draw, mouse, screenMode, hooks, screen))
                .shouldCancel();
    }

    public static void firePlayerControlPanelAfterDraw(CombatUiPanelType panelType,
            Object panel, Object draw, Object mouse, Object screenMode,
            Object hooks, Object screen) {
        if (AirshipsCombatUiEvents.PLAYER_CONTROL_PANEL_AFTER_DRAW.listenerCount() == 0) {
            return;
        }
        AirshipsCombatUiEvents.PLAYER_CONTROL_PANEL_AFTER_DRAW.invoker()
                .afterPlayerControlPanelDraw(new PlayerControlPanelDrawContext(
                        panelType, panel, draw, mouse, screenMode, hooks, screen));
    }

    public static boolean firePlayerControlPanelBeforeTick(CombatUiPanelType panelType,
            Object panel, Object input, int elapsedMs, Object screen) {
        if (AirshipsCombatUiEvents.PLAYER_CONTROL_PANEL_BEFORE_TICK.listenerCount() == 0) {
            return false;
        }
        return AirshipsCombatUiEvents.PLAYER_CONTROL_PANEL_BEFORE_TICK.invoker()
                .beforePlayerControlPanelTick(new PlayerControlPanelTickContext(
                        panelType, panel, input, elapsedMs, screen))
                .shouldCancel();
    }

    public static void firePlayerControlPanelAfterTick(CombatUiPanelType panelType,
            Object panel, Object input, int elapsedMs, Object screen) {
        if (AirshipsCombatUiEvents.PLAYER_CONTROL_PANEL_AFTER_TICK.listenerCount() == 0) {
            return;
        }
        AirshipsCombatUiEvents.PLAYER_CONTROL_PANEL_AFTER_TICK.invoker()
                .afterPlayerControlPanelTick(new PlayerControlPanelTickContext(
                        panelType, panel, input, elapsedMs, screen));
    }

    // ---- 重命名面板新事件，旧回调先执行 ----

    public static boolean fireRenameShipBeforeDraw(Object panel, Object draw, Object mouse, Object screenMode,
            Object hooks, Object screen) {
        if (AirshipsCombatUiEvents.RENAME_SHIP_BEFORE_DRAW.listenerCount() == 0) {
            return false;
        }
        return AirshipsCombatUiEvents.RENAME_SHIP_BEFORE_DRAW.invoker()
                .beforePlayerControlPanelDraw(new PlayerControlPanelDrawContext(
                        CombatUiPanelType.RENAME_SHIP, panel, draw, mouse, screenMode, hooks, screen))
                .shouldCancel();
    }

    public static void fireRenameShipAfterDraw(Object panel, Object draw, Object mouse, Object screenMode,
            Object hooks, Object screen) {
        if (AirshipsCombatUiEvents.RENAME_SHIP_AFTER_DRAW.listenerCount() == 0) {
            return;
        }
        AirshipsCombatUiEvents.RENAME_SHIP_AFTER_DRAW.invoker()
                .afterPlayerControlPanelDraw(new PlayerControlPanelDrawContext(
                        CombatUiPanelType.RENAME_SHIP, panel, draw, mouse, screenMode, hooks, screen));
    }

    public static boolean fireRenameShipBeforeTick(Object panel, Object input, int elapsedMs, Object screen) {
        if (AirshipsCombatUiEvents.RENAME_SHIP_BEFORE_TICK.listenerCount() == 0) {
            return false;
        }
        return AirshipsCombatUiEvents.RENAME_SHIP_BEFORE_TICK.invoker()
                .beforePlayerControlPanelTick(new PlayerControlPanelTickContext(
                        CombatUiPanelType.RENAME_SHIP, panel, input, elapsedMs, screen))
                .shouldCancel();
    }

    public static void fireRenameShipAfterTick(Object panel, Object input, int elapsedMs, Object screen) {
        if (AirshipsCombatUiEvents.RENAME_SHIP_AFTER_TICK.listenerCount() == 0) {
            return;
        }
        AirshipsCombatUiEvents.RENAME_SHIP_AFTER_TICK.invoker()
                .afterPlayerControlPanelTick(new PlayerControlPanelTickContext(
                        CombatUiPanelType.RENAME_SHIP, panel, input, elapsedMs, screen));
    }

    // ---- 旧重命名面板事件：仅保留历史兼容语义 ----

    /** @deprecated 重命名面板的旧分发入口；框架新增调用应使用对应 fireRenameShip 方法。 */
    @Deprecated(since = "0.3.3", forRemoval = false)
    public static boolean fireOneShotWeaponsBeforeDraw(CombatUiPanelType panelType,
            Object panel, Object draw, Object mouse, Object screenMode,
            Object hooks, Object screen) {
        if (AirshipsCombatUiEvents.ONE_SHOT_WEAPONS_BEFORE_DRAW.listenerCount() == 0) {
            return false;
        }
        return AirshipsCombatUiEvents.ONE_SHOT_WEAPONS_BEFORE_DRAW.invoker()
                .beforePlayerControlPanelDraw(new PlayerControlPanelDrawContext(
                        panelType, panel, draw, mouse, screenMode, hooks, screen))
                .shouldCancel();
    }

    /** @deprecated 重命名面板的旧分发入口；框架新增调用应使用对应 fireRenameShip 方法。 */
    @Deprecated(since = "0.3.3", forRemoval = false)
    public static void fireOneShotWeaponsAfterDraw(CombatUiPanelType panelType,
            Object panel, Object draw, Object mouse, Object screenMode,
            Object hooks, Object screen) {
        if (AirshipsCombatUiEvents.ONE_SHOT_WEAPONS_AFTER_DRAW.listenerCount() == 0) {
            return;
        }
        AirshipsCombatUiEvents.ONE_SHOT_WEAPONS_AFTER_DRAW.invoker()
                .afterPlayerControlPanelDraw(new PlayerControlPanelDrawContext(
                        panelType, panel, draw, mouse, screenMode, hooks, screen));
    }

    /** @deprecated 重命名面板的旧分发入口；框架新增调用应使用对应 fireRenameShip 方法。 */
    @Deprecated(since = "0.3.3", forRemoval = false)
    public static boolean fireOneShotWeaponsBeforeTick(CombatUiPanelType panelType,
            Object panel, Object input, int elapsedMs, Object screen) {
        if (AirshipsCombatUiEvents.ONE_SHOT_WEAPONS_BEFORE_TICK.listenerCount() == 0) {
            return false;
        }
        return AirshipsCombatUiEvents.ONE_SHOT_WEAPONS_BEFORE_TICK.invoker()
                .beforePlayerControlPanelTick(new PlayerControlPanelTickContext(
                        panelType, panel, input, elapsedMs, screen))
                .shouldCancel();
    }

    /** @deprecated 重命名面板的旧分发入口；框架新增调用应使用对应 fireRenameShip 方法。 */
    @Deprecated(since = "0.3.3", forRemoval = false)
    public static void fireOneShotWeaponsAfterTick(CombatUiPanelType panelType,
            Object panel, Object input, int elapsedMs, Object screen) {
        if (AirshipsCombatUiEvents.ONE_SHOT_WEAPONS_AFTER_TICK.listenerCount() == 0) {
            return;
        }
        AirshipsCombatUiEvents.ONE_SHOT_WEAPONS_AFTER_TICK.invoker()
                .afterPlayerControlPanelTick(new PlayerControlPanelTickContext(
                        panelType, panel, input, elapsedMs, screen));
    }

    // ---- 旧重命名面板事件：仅保留历史兼容语义 ----

    /** @deprecated 重命名面板的旧分发入口；框架新增调用应使用对应 fireRenameShip 方法。 */
    @Deprecated(since = "0.3.3", forRemoval = false)
    public static boolean fireOneShotBuoyancyBeforeDraw(CombatUiPanelType panelType,
            Object panel, Object draw, Object mouse, Object screenMode,
            Object hooks, Object screen) {
        if (AirshipsCombatUiEvents.ONE_SHOT_BUOYANCY_BEFORE_DRAW.listenerCount() == 0)
            return false;
        return AirshipsCombatUiEvents.ONE_SHOT_BUOYANCY_BEFORE_DRAW.invoker()
                .beforePlayerControlPanelDraw(new PlayerControlPanelDrawContext(
                        panelType, panel, draw, mouse, screenMode, hooks, screen))
                .shouldCancel();
    }

    /** @deprecated 重命名面板的旧分发入口；框架新增调用应使用对应 fireRenameShip 方法。 */
    @Deprecated(since = "0.3.3", forRemoval = false)
    public static void fireOneShotBuoyancyAfterDraw(CombatUiPanelType panelType,
            Object panel, Object draw, Object mouse, Object screenMode,
            Object hooks, Object screen) {
        if (AirshipsCombatUiEvents.ONE_SHOT_BUOYANCY_AFTER_DRAW.listenerCount() == 0)
            return;
        AirshipsCombatUiEvents.ONE_SHOT_BUOYANCY_AFTER_DRAW.invoker()
                .afterPlayerControlPanelDraw(new PlayerControlPanelDrawContext(
                        panelType, panel, draw, mouse, screenMode, hooks, screen));
    }

    /** @deprecated 重命名面板的旧分发入口；框架新增调用应使用对应 fireRenameShip 方法。 */
    @Deprecated(since = "0.3.3", forRemoval = false)
    public static boolean fireOneShotBuoyancyBeforeTick(CombatUiPanelType panelType,
            Object panel, Object input, int elapsedMs, Object screen) {
        if (AirshipsCombatUiEvents.ONE_SHOT_BUOYANCY_BEFORE_TICK.listenerCount() == 0)
            return false;
        return AirshipsCombatUiEvents.ONE_SHOT_BUOYANCY_BEFORE_TICK.invoker()
                .beforePlayerControlPanelTick(new PlayerControlPanelTickContext(
                        panelType, panel, input, elapsedMs, screen))
                .shouldCancel();
    }

    /** @deprecated 重命名面板的旧分发入口；框架新增调用应使用对应 fireRenameShip 方法。 */
    @Deprecated(since = "0.3.3", forRemoval = false)
    public static void fireOneShotBuoyancyAfterTick(CombatUiPanelType panelType,
            Object panel, Object input, int elapsedMs, Object screen) {
        if (AirshipsCombatUiEvents.ONE_SHOT_BUOYANCY_AFTER_TICK.listenerCount() == 0)
            return;
        AirshipsCombatUiEvents.ONE_SHOT_BUOYANCY_AFTER_TICK.invoker()
                .afterPlayerControlPanelTick(new PlayerControlPanelTickContext(
                        panelType, panel, input, elapsedMs, screen));
    }

    // ---- 旧重命名面板事件：仅保留历史兼容语义 ----

    /** @deprecated 重命名面板的旧分发入口；框架新增调用应使用对应 fireRenameShip 方法。 */
    @Deprecated(since = "0.3.3", forRemoval = false)
    public static boolean fireOneShotPowerBeforeDraw(CombatUiPanelType panelType,
            Object panel, Object draw, Object mouse, Object screenMode,
            Object hooks, Object screen) {
        if (AirshipsCombatUiEvents.ONE_SHOT_POWER_BEFORE_DRAW.listenerCount() == 0)
            return false;
        return AirshipsCombatUiEvents.ONE_SHOT_POWER_BEFORE_DRAW.invoker()
                .beforePlayerControlPanelDraw(new PlayerControlPanelDrawContext(
                        panelType, panel, draw, mouse, screenMode, hooks, screen))
                .shouldCancel();
    }

    /** @deprecated 重命名面板的旧分发入口；框架新增调用应使用对应 fireRenameShip 方法。 */
    @Deprecated(since = "0.3.3", forRemoval = false)
    public static void fireOneShotPowerAfterDraw(CombatUiPanelType panelType,
            Object panel, Object draw, Object mouse, Object screenMode,
            Object hooks, Object screen) {
        if (AirshipsCombatUiEvents.ONE_SHOT_POWER_AFTER_DRAW.listenerCount() == 0)
            return;
        AirshipsCombatUiEvents.ONE_SHOT_POWER_AFTER_DRAW.invoker()
                .afterPlayerControlPanelDraw(new PlayerControlPanelDrawContext(
                        panelType, panel, draw, mouse, screenMode, hooks, screen));
    }

    /** @deprecated 重命名面板的旧分发入口；框架新增调用应使用对应 fireRenameShip 方法。 */
    @Deprecated(since = "0.3.3", forRemoval = false)
    public static boolean fireOneShotPowerBeforeTick(CombatUiPanelType panelType,
            Object panel, Object input, int elapsedMs, Object screen) {
        if (AirshipsCombatUiEvents.ONE_SHOT_POWER_BEFORE_TICK.listenerCount() == 0)
            return false;
        return AirshipsCombatUiEvents.ONE_SHOT_POWER_BEFORE_TICK.invoker()
                .beforePlayerControlPanelTick(new PlayerControlPanelTickContext(
                        panelType, panel, input, elapsedMs, screen))
                .shouldCancel();
    }

    /** @deprecated 重命名面板的旧分发入口；框架新增调用应使用对应 fireRenameShip 方法。 */
    @Deprecated(since = "0.3.3", forRemoval = false)
    public static void fireOneShotPowerAfterTick(CombatUiPanelType panelType,
            Object panel, Object input, int elapsedMs, Object screen) {
        if (AirshipsCombatUiEvents.ONE_SHOT_POWER_AFTER_TICK.listenerCount() == 0)
            return;
        AirshipsCombatUiEvents.ONE_SHOT_POWER_AFTER_TICK.invoker()
                .afterPlayerControlPanelTick(new PlayerControlPanelTickContext(
                        panelType, panel, input, elapsedMs, screen));
    }
}
