/*
 * EventHandle.java — 代表一次独立订阅；重复注销无副作用，不影响同一监听器的其他注册。
 */
package net.fabricacs.api.event;

@FunctionalInterface
public interface EventHandle {
    /** 只注销当前订阅；重复调用无副作用。 */
    void unregister();
}
