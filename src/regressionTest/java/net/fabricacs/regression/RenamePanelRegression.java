/*
 * RenamePanelRegression.java — 验证新旧重命名面板事件；普通构建检查上下文，真实 Knot 探针检查注入顺序。
 */
package net.fabricacs.regression;

import com.zarkonnen.airships.RenameShipPanel;
import net.fabricacs.api.event.*;
import net.fabricacs.api.impl.LifecycleHooks;

import java.util.ArrayList;
import java.util.List;

/** 同时提供普通上下文检查和真实 Knot/Mixin 注入验证入口。 */
@SuppressWarnings("deprecation")
public final class RenamePanelRegression {
    private static final List<Event<AirshipsCombatUiEvents.PlayerControlPanelBeforeDraw>> DRAW_BEFORE = List.of(
            AirshipsCombatUiEvents.ONE_SHOT_WEAPONS_BEFORE_DRAW,
            AirshipsCombatUiEvents.ONE_SHOT_BUOYANCY_BEFORE_DRAW,
            AirshipsCombatUiEvents.ONE_SHOT_POWER_BEFORE_DRAW,
            AirshipsCombatUiEvents.RENAME_SHIP_BEFORE_DRAW);
    private static final List<Event<AirshipsCombatUiEvents.PlayerControlPanelAfterDraw>> DRAW_AFTER = List.of(
            AirshipsCombatUiEvents.ONE_SHOT_WEAPONS_AFTER_DRAW,
            AirshipsCombatUiEvents.ONE_SHOT_BUOYANCY_AFTER_DRAW,
            AirshipsCombatUiEvents.ONE_SHOT_POWER_AFTER_DRAW,
            AirshipsCombatUiEvents.RENAME_SHIP_AFTER_DRAW);
    private static final List<Event<AirshipsCombatUiEvents.PlayerControlPanelBeforeTick>> TICK_BEFORE = List.of(
            AirshipsCombatUiEvents.ONE_SHOT_WEAPONS_BEFORE_TICK,
            AirshipsCombatUiEvents.ONE_SHOT_BUOYANCY_BEFORE_TICK,
            AirshipsCombatUiEvents.ONE_SHOT_POWER_BEFORE_TICK,
            AirshipsCombatUiEvents.RENAME_SHIP_BEFORE_TICK);
    private static final List<Event<AirshipsCombatUiEvents.PlayerControlPanelAfterTick>> TICK_AFTER = List.of(
            AirshipsCombatUiEvents.ONE_SHOT_WEAPONS_AFTER_TICK,
            AirshipsCombatUiEvents.ONE_SHOT_BUOYANCY_AFTER_TICK,
            AirshipsCombatUiEvents.ONE_SHOT_POWER_AFTER_TICK,
            AirshipsCombatUiEvents.RENAME_SHIP_AFTER_TICK);
    private static final List<CombatUiPanelType> TYPES = List.of(CombatUiPanelType.ONE_SHOT_WEAPONS,
            CombatUiPanelType.ONE_SHOT_BUOYANCY, CombatUiPanelType.ONE_SHOT_POWER, CombatUiPanelType.RENAME_SHIP);

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void clear() {
        DRAW_BEFORE.forEach(Event::clearListeners);
        DRAW_AFTER.forEach(Event::clearListeners);
        TICK_BEFORE.forEach(Event::clearListeners);
        TICK_AFTER.forEach(Event::clearListeners);
    }

    public static int run() {
        Object panel = new Object(), draw = new Object(), mouse = new Object(), mode = new Object();
        Object hooks = new Object(), screen = new Object(), input = new Object();
        List<Object> seen = new ArrayList<>();
        try {
            clear();
            DRAW_BEFORE.get(0).register(c -> { throw new AssertionError("New hook dispatched legacy listener"); });
            DRAW_BEFORE.get(3).register(c -> { seen.add(c); return EventResult.CANCEL; });
            DRAW_BEFORE.get(3).register(c -> { throw new AssertionError("CANCEL did not stop listeners"); });
            require(LifecycleHooks.fireRenameShipBeforeDraw(panel, draw, mouse, mode, hooks, screen), "Draw cancellation");
            PlayerControlPanelDrawContext d = (PlayerControlPanelDrawContext) seen.removeFirst();
            require(d.panelType() == CombatUiPanelType.RENAME_SHIP && d.panel() == panel && d.draw() == draw
                    && d.mouse() == mouse && d.screenMode() == mode && d.hooks() == hooks && d.screen() == screen, "Draw context identity");
            DRAW_AFTER.get(3).register(seen::add);
            LifecycleHooks.fireRenameShipAfterDraw(panel, draw, mouse, mode, hooks, screen);
            require(seen.size() == 1 && ((PlayerControlPanelDrawContext) seen.removeFirst()).panel() == panel, "After draw context");
            TICK_BEFORE.get(3).register(c -> { seen.add(c); return EventResult.PASS; });
            require(!LifecycleHooks.fireRenameShipBeforeTick(panel, input, 37, screen), "Tick PASS");
            PlayerControlPanelTickContext t = (PlayerControlPanelTickContext) seen.removeFirst();
            require(t.panelType() == CombatUiPanelType.RENAME_SHIP && t.panel() == panel && t.input() == input
                    && t.elapsedMs() == 37 && t.screen() == screen, "Tick context identity");
            TICK_AFTER.get(3).register(seen::add);
            LifecycleHooks.fireRenameShipAfterTick(panel, input, 37, screen);
            require(seen.size() == 1 && ((PlayerControlPanelTickContext) seen.removeFirst()).elapsedMs() == 37, "After tick context");
            clear();
            require(!LifecycleHooks.fireRenameShipBeforeDraw(panel, draw, mouse, mode, hooks, screen)
                    && !LifecycleHooks.fireRenameShipBeforeTick(panel, input, 37, screen), "No listeners is PASS");
            System.out.println("PASS rename panel hook contexts, isolation, cancellation and empty dispatch");
            return 7;
        } finally { clear(); }
    }

    /** 必须通过 Knot 调用转换后的游戏类，不在普通 regressionTest 中运行。 */
    public static void runTransformed() {
        RenameShipPanel panel = new RenameShipPanel();
        List<String> seen = new ArrayList<>();
        try {
            // 分别覆盖只用旧事件、只用新事件及混用，并逐个取消所有可到达的 BEFORE 组。
            for (boolean draw : new boolean[]{true, false}) {
                for (int mask : new int[]{7, 8, 15}) {
                    for (int cancelAt = -1; cancelAt < 4; cancelAt++) {
                        if (cancelAt >= 0 && (mask & (1 << cancelAt)) == 0) continue;
                        clear();
                        seen.clear();
                        final int cancel = cancelAt;
                        for (int i = 0; i < 4; i++) {
                            if ((mask & (1 << i)) == 0) continue;
                            final int n = i;
                            DRAW_BEFORE.get(i).register(c -> {
                                require(c.panel() == panel && c.panelType() == TYPES.get(n), "Transformed draw context");
                                seen.add("before" + n);
                                return cancel == n ? EventResult.CANCEL : EventResult.PASS;
                            });
                            DRAW_AFTER.get(i).register(c -> {
                                require(c.panel() == panel && c.panelType() == TYPES.get(n), "Transformed after draw context");
                                seen.add("after" + n);
                            });
                            TICK_BEFORE.get(i).register(c -> {
                                require(c.panel() == panel && c.panelType() == TYPES.get(n) && c.elapsedMs() == 37, "Transformed tick context");
                                seen.add("before" + n);
                                return cancel == n ? EventResult.CANCEL : EventResult.PASS;
                            });
                            TICK_AFTER.get(i).register(c -> {
                                require(c.panel() == panel && c.panelType() == TYPES.get(n), "Transformed after tick context");
                                seen.add("after" + n);
                            });
                        }
                        if (draw) panel.draw(null, null, null, null, null);
                        else panel.tick(null, 37, null);
                        List<String> expected = new ArrayList<>();
                        for (int i = 0; i < 4; i++) {
                            if ((mask & (1 << i)) != 0) expected.add("before" + i);
                            if (i == cancelAt) break;
                        }
                        if (cancelAt == -1) for (int i = 0; i < 4; i++) {
                            if ((mask & (1 << i)) != 0) expected.add("after" + i);
                        }
                        require(seen.equals(expected), "Transformed order/cancel draw=" + draw + " mask=" + mask
                                + " cancel=" + cancelAt + ": " + seen + " expected " + expected);
                    }
                }
            }
            System.out.println("PASS transformed rename panel: 22 legacy/new/mixed draw/tick ordering and cancellation cases");
        } finally { clear(); }
    }
}
