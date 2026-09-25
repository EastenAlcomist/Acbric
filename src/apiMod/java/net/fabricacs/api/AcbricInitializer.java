/*
 * AcbricInitializer.java — MOD 初始化协议：保留无参入口，并用带上下文的默认方法兼容旧实现。
 */
package net.fabricacs.api;

/** MOD 初始化接口；在 fabric.mod.json 的 acbric 入口下声明实现类。 */
@FunctionalInterface
public interface AcbricInitializer {
    /** 旧版无参入口；实现带上下文方法的类仍需保留该方法。 */
    void onInitializeAcbric();

    /** 默认转调无参入口，保证旧 MOD 不需重新实现接口。 */
    default void onInitializeAcbric(AcbricModContext context) {
        onInitializeAcbric();
    }
}
