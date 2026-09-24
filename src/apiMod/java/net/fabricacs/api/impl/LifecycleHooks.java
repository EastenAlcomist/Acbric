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

    // ---- Lifecycle (infrequent) ----------------------------------------------

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

    // ---- Client tick (every frame) -------------------------------------------

    public static void fireClientTickStart(Object airshipGame) {
        AirshipsClientEvents.CLIENT_TICK_START.invoker().onClientTickStart(airshipGame);
    }

    public static void fireClientTickEnd(Object airshipGame) {
        AirshipsClientEvents.CLIENT_TICK_END.invoker().onClientTickEnd(airshipGame);
    }

    // ---- Data (infrequent) ---------------------------------------------------

    public static void fireDataLoadStarting() {
        AirshipsDataEvents.DATA_LOAD_STARTING.invoker().onDataLoadStarting();
    }

    public static void fireDataLoaded(boolean successful) {
        AirshipsDataEvents.DATA_LOADED.invoker().onDataLoaded(successful);
    }

    // ---- Combat UI — Ship status bar (every frame for every visible ship) -----

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

    // ---- Combat UI — Player control panel (every frame) ----------------------

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

    // ---- One-shot weapons panel (same panel class, separate event) ----------

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

    public static void fireOneShotWeaponsAfterTick(CombatUiPanelType panelType,
            Object panel, Object input, int elapsedMs, Object screen) {
        if (AirshipsCombatUiEvents.ONE_SHOT_WEAPONS_AFTER_TICK.listenerCount() == 0) {
            return;
        }
        AirshipsCombatUiEvents.ONE_SHOT_WEAPONS_AFTER_TICK.invoker()
                .afterPlayerControlPanelTick(new PlayerControlPanelTickContext(
                        panelType, panel, input, elapsedMs, screen));
    }

    // ---- One-shot buoyancy --------------------------------------------------

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

    public static void fireOneShotBuoyancyAfterDraw(CombatUiPanelType panelType,
            Object panel, Object draw, Object mouse, Object screenMode,
            Object hooks, Object screen) {
        if (AirshipsCombatUiEvents.ONE_SHOT_BUOYANCY_AFTER_DRAW.listenerCount() == 0)
            return;
        AirshipsCombatUiEvents.ONE_SHOT_BUOYANCY_AFTER_DRAW.invoker()
                .afterPlayerControlPanelDraw(new PlayerControlPanelDrawContext(
                        panelType, panel, draw, mouse, screenMode, hooks, screen));
    }

    public static boolean fireOneShotBuoyancyBeforeTick(CombatUiPanelType panelType,
            Object panel, Object input, int elapsedMs, Object screen) {
        if (AirshipsCombatUiEvents.ONE_SHOT_BUOYANCY_BEFORE_TICK.listenerCount() == 0)
            return false;
        return AirshipsCombatUiEvents.ONE_SHOT_BUOYANCY_BEFORE_TICK.invoker()
                .beforePlayerControlPanelTick(new PlayerControlPanelTickContext(
                        panelType, panel, input, elapsedMs, screen))
                .shouldCancel();
    }

    public static void fireOneShotBuoyancyAfterTick(CombatUiPanelType panelType,
            Object panel, Object input, int elapsedMs, Object screen) {
        if (AirshipsCombatUiEvents.ONE_SHOT_BUOYANCY_AFTER_TICK.listenerCount() == 0)
            return;
        AirshipsCombatUiEvents.ONE_SHOT_BUOYANCY_AFTER_TICK.invoker()
                .afterPlayerControlPanelTick(new PlayerControlPanelTickContext(
                        panelType, panel, input, elapsedMs, screen));
    }

    // ---- One-shot power -----------------------------------------------------

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

    public static void fireOneShotPowerAfterDraw(CombatUiPanelType panelType,
            Object panel, Object draw, Object mouse, Object screenMode,
            Object hooks, Object screen) {
        if (AirshipsCombatUiEvents.ONE_SHOT_POWER_AFTER_DRAW.listenerCount() == 0)
            return;
        AirshipsCombatUiEvents.ONE_SHOT_POWER_AFTER_DRAW.invoker()
                .afterPlayerControlPanelDraw(new PlayerControlPanelDrawContext(
                        panelType, panel, draw, mouse, screenMode, hooks, screen));
    }

    public static boolean fireOneShotPowerBeforeTick(CombatUiPanelType panelType,
            Object panel, Object input, int elapsedMs, Object screen) {
        if (AirshipsCombatUiEvents.ONE_SHOT_POWER_BEFORE_TICK.listenerCount() == 0)
            return false;
        return AirshipsCombatUiEvents.ONE_SHOT_POWER_BEFORE_TICK.invoker()
                .beforePlayerControlPanelTick(new PlayerControlPanelTickContext(
                        panelType, panel, input, elapsedMs, screen))
                .shouldCancel();
    }

    public static void fireOneShotPowerAfterTick(CombatUiPanelType panelType,
            Object panel, Object input, int elapsedMs, Object screen) {
        if (AirshipsCombatUiEvents.ONE_SHOT_POWER_AFTER_TICK.listenerCount() == 0)
            return;
        AirshipsCombatUiEvents.ONE_SHOT_POWER_AFTER_TICK.invoker()
                .afterPlayerControlPanelTick(new PlayerControlPanelTickContext(
                        panelType, panel, input, elapsedMs, screen));
    }
}
