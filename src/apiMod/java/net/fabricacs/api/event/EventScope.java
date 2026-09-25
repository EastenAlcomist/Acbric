/*
 * EventScope.java — 按 MOD/用途管理订阅，显式关闭并释放监听器引用；不会自动判断战役寿命。
 * 关闭不等待已进入的回调；诊断失败不能替代原异常，旧 Event 注册行为保持不变。
 */
package net.fabricacs.api.event;

import net.fabricacs.api.impl.RuntimeEventDiagnostics;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class EventScope implements AutoCloseable {
    private final String modId;
    private final String name;
    private final Set<ScopedListener<?>> listeners = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    /** 独立事件/工具可直接构造；MOD 优先使用 context.eventScope(name) 提供归属。 */
    public EventScope(String modId, String name) {
        this.modId = label(modId); this.name = label(name);
    }
    private static String label(String value) {
        Objects.requireNonNull(value);
        if (value.isBlank() || value.length() > 256) throw new IllegalArgumentException("Scope labels must contain 1..256 characters");
        return value;
    }
    public String modId() { return modId; }
    public String name() { return name; }
    public boolean isClosed() { return closed; }
    /** 尚未注销/消费的订阅数；并发调用时为瞬时值。 */
    public int size() { return listeners.size(); }
    public <T> EventHandle register(Event<T> event, T listener) { return add(event, listener, false); }
    public <T> EventHandle registerOnce(Event<T> event, T listener) { return add(event, listener, true); }

    private synchronized <T> EventHandle add(Event<T> event, T listener, boolean once) {
        if (closed) throw new IllegalStateException("Event scope is closed: " + name);
        ScopedListener<T> entry = new ScopedListener<>(this, Objects.requireNonNull(event), Objects.requireNonNull(listener), once);
        listeners.add(entry);
        try { return event.registerScoped(entry); }
        catch (RuntimeException | Error failure) { entry.released(); throw failure; }
    }

    /** 幂等关闭；先关闭入口，锁外注销，避免与 Event 的分发/注销形成锁顺序循环。 */
    @Override public void close() {
        List<ScopedListener<?>> pending;
        synchronized (this) {
            if (closed) return;
            closed = true;
            pending = new ArrayList<>(listeners);
            // 先让全部代理失效，某个自定义工厂重建失败也不会让后续订阅继续调用。
            for (var entry : pending) entry.target.set(null);
        }
        Throwable first = null;
        for (var entry : pending) {
            try { entry.unregister(); }
            catch (RuntimeException | Error failure) { if (first == null) first = failure; }
        }
        listeners.clear();
        if (first instanceof RuntimeException runtime) throw runtime;
        if (first instanceof Error error) throw error;
    }

    static final class ScopedListener<T> implements InvocationHandler, EventHandle {
        final EventScope scope;
        final Event<T> event;
        final AtomicReference<T> target;
        final boolean once;
        final String listenerClass;
        final AtomicLong failures = new AtomicLong();
        volatile EventHandle handle;

        ScopedListener(EventScope scope, Event<T> event, T target, boolean once) {
            this.scope = scope; this.event = event; this.target = new AtomicReference<>(target); this.once = once;
            listenerClass = target.getClass().getName();
        }
        @SuppressWarnings("unchecked") T proxy() {
            T original = target.get();
            Set<Class<?>> interfaces = new LinkedHashSet<>();
            for (Class<?> type = original.getClass(); type != null; type = type.getSuperclass()) interfaces.addAll(List.of(type.getInterfaces()));
            if (interfaces.isEmpty()) throw new IllegalArgumentException("Scoped listeners require interface-backed callbacks");
            return (T) Proxy.newProxyInstance(original.getClass().getClassLoader(), interfaces.toArray(Class<?>[]::new), this);
        }
        void released() { target.set(null); scope.listeners.remove(this); }
        @Override public void unregister() { handle.unregister(); }
        @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) return switch (method.getName()) {
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "ScopedListener[" + scope.modId + "/" + scope.name + "/" + event.name() + "]";
                default -> throw new UnsupportedOperationException(method.getName());
            };
            T original = once ? target.getAndSet(null) : target.get();
            if (original == null) return call(method, event.emptyInvoker(), args);
            if (once) unregister(); // 执行前消费资格；失败、递归或并发分发不会重试。
            try { return call(method, original, args); }
            catch (Throwable failure) {
                RuntimeEventDiagnostics.report(scope.modId, scope.name, event.name(), method.getName(), listenerClass, failures.incrementAndGet(), failure);
                throw failure;
            }
        }
        private static Object call(Method method, Object receiver, Object[] args) throws Throwable {
            try {
                if (!method.canAccess(receiver)) method.trySetAccessible();
                return method.invoke(receiver, args);
            } catch (InvocationTargetException wrapper) { throw wrapper.getCause(); }
        }
    }
}
