/*
 * PlayerControlPanelTickContext.java — 面板输入更新上下文：保留输入对象、毫秒间隔及所在界面的引用。
 */
package net.fabricacs.api.event;

public final class PlayerControlPanelTickContext {
    private final CombatUiPanelType panelType;
    // 原方法传入的游戏对象引用；字段不可重新赋值，但对象自身仍可变化。
    private final Object panel;
    private final Object input;
    private final int elapsedMs;
    private final Object screen;

    public PlayerControlPanelTickContext(CombatUiPanelType panelType, Object panel, Object input, int elapsedMs, Object screen) {
        this.panelType = panelType;
        this.panel = panel;
        this.input = input;
        this.elapsedMs = elapsedMs;
        this.screen = screen;
    }

    public CombatUiPanelType panelType() {
        return panelType;
    }

    public Object panel() {
        return panel;
    }

    public Object input() {
        return input;
    }

    public int elapsedMs() {
        return elapsedMs;
    }

    public Object screen() {
        return screen;
    }
}
