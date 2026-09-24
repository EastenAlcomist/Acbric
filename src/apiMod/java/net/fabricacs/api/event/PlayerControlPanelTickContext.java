package net.fabricacs.api.event;

public final class PlayerControlPanelTickContext {
    private final CombatUiPanelType panelType;
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
