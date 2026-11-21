/*
 * CommandPanelAnchor.java — 命令面板扩展按钮条的定位锚点；区分原生面板与游戏窗口坐标系。
 */
package net.fabricacs.api.combat;

public enum CommandPanelAnchor {
    /** 主命令面板上方，按按钮条左边缘对齐面板左边缘。 */
    PANEL_ABOVE_LEFT,
    /** 默认：主命令面板上方居中。 */
    PANEL_ABOVE_CENTER,
    /** 主命令面板上方，按按钮条右边缘对齐面板右边缘。 */
    PANEL_ABOVE_RIGHT,

    WINDOW_TOP_LEFT,
    WINDOW_TOP_CENTER,
    WINDOW_TOP_RIGHT,
    WINDOW_CENTER_LEFT,
    /** 游戏窗口正中央。 */
    WINDOW_CENTER,
    WINDOW_CENTER_RIGHT,
    WINDOW_BOTTOM_LEFT,
    WINDOW_BOTTOM_CENTER,
    WINDOW_BOTTOM_RIGHT
}
