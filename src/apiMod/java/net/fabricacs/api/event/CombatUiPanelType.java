/*
 * CombatUiPanelType.java — 标识 UI 事件对应的面板；旧 ONE_SHOT 标签保留原枚举顺序及兼容语义。
 */
package net.fabricacs.api.event;

public enum CombatUiPanelType {
    COMMAND_BUTTONS,
    DIRECT_CONTROL,
    /** @deprecated 历史重命名面板标签；新事件使用 RENAME_SHIP。 */
    @Deprecated(since = "0.3.3", forRemoval = false)
    ONE_SHOT_WEAPONS,
    /** @deprecated 历史重命名面板标签；新事件使用 RENAME_SHIP。 */
    @Deprecated(since = "0.3.3", forRemoval = false)
    ONE_SHOT_BUOYANCY,
    /** @deprecated 历史重命名面板标签；新事件使用 RENAME_SHIP。 */
    @Deprecated(since = "0.3.3", forRemoval = false)
    ONE_SHOT_POWER,
    /** 真实的重命名面板 draw/tick 上下文。
     * @since 0.3.3-dev.1 */
    RENAME_SHIP
}
