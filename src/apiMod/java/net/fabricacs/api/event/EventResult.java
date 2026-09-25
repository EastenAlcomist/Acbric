/*
 * EventResult.java — 可取消事件的返回值；PASS 继续执行，CANCEL 请求跳过后续处理。
 */
package net.fabricacs.api.event;

public enum EventResult {
    /** 保留游戏默认行为，继续后续监听器。 */
    PASS,
    /** 请求取消，具体范围由对应事件的注入位置定义。 */
    CANCEL;

    public boolean shouldCancel() {
        return this == CANCEL;
    }
}
