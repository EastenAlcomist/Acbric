package net.fabricacs.api.event;

public final class ShipStatusBarContext {
    private final Object statusBar;
    private final Object draw;
    private final Object mouse;
    private final Object airship;
    private final Object side;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final Object screenMode;
    private final Object screen;

    public ShipStatusBarContext(Object statusBar, Object draw, Object mouse, Object airship, Object side, int x, int y, int width, int height, Object screenMode, Object screen) {
        this.statusBar = statusBar;
        this.draw = draw;
        this.mouse = mouse;
        this.airship = airship;
        this.side = side;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.screenMode = screenMode;
        this.screen = screen;
    }

    public Object statusBar() {
        return statusBar;
    }

    public Object draw() {
        return draw;
    }

    public Object mouse() {
        return mouse;
    }

    public Object airship() {
        return airship;
    }

    public Object side() {
        return side;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public Object screenMode() {
        return screenMode;
    }

    public Object screen() {
        return screen;
    }
}
