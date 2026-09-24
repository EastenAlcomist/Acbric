package net.fabricacs.api.event;

public final class PlayerControlPanelDrawContext {
    private final CombatUiPanelType panelType;
    private final Object panel;
    private final Object draw;
    private final Object mouse;
    private final Object screenMode;
    private final Object hooks;
    private final Object screen;

    public PlayerControlPanelDrawContext(CombatUiPanelType panelType, Object panel, Object draw, Object mouse, Object screenMode, Object hooks, Object screen) {
        this.panelType = panelType;
        this.panel = panel;
        this.draw = draw;
        this.mouse = mouse;
        this.screenMode = screenMode;
        this.hooks = hooks;
        this.screen = screen;
    }

    public CombatUiPanelType panelType() {
        return panelType;
    }

    public Object panel() {
        return panel;
    }

    public Object draw() {
        return draw;
    }

    public Object mouse() {
        return mouse;
    }

    public Object screenMode() {
        return screenMode;
    }

    public Object hooks() {
        return hooks;
    }

    public Object screen() {
        return screen;
    }
}
