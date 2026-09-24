/*
 * Event.java — 事件订阅容器：缓存分发快照，支持独立注销句柄和最多执行一次的监听器。
 * 线程安全的是订阅状态；回调同步执行，调用方仍需管理游戏对象的线程访问。
 */
package net.fabricacs.api.event;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 注册表变更时重建分发快照；读取 invoker 不复制监听器列表。
 * <p>普通监听器即使在分发中被注销，旧快照仍可能执行它；一次性代理用共享资格位
 * 阻止旧快照重复回调。监听器异常向调用方传播，中止该次后续分发。</p>
 */
public final class Event<T> {
    private final List<Registration<T>> listeners;
    private final InvokerFactory<T> invokerFactory;
    private final T emptyInvoker;
    private volatile T invoker;

    /** 创建事件时立即用空列表调用工厂；工厂需能处理空列表。 */
    public Event(InvokerFactory<T> invokerFactory) {
        this.invokerFactory = Objects.requireNonNull(invokerFactory);
        this.listeners = new ArrayList<>();
        this.emptyInvoker = invokerFactory.create(snapshot());
        this.invoker = emptyInvoker;
    }

    /** 追加普通订阅，允许重复注册同一对象。 */
    public synchronized void register(T listener) {
        listeners.add(new Registration<>(Objects.requireNonNull(listener)));
        invoker = invokerFactory.create(snapshot());
    }

    /** 返回只对应本次注册的句柄，不按监听器对象批量注销。 */
    public synchronized EventHandle registerWithHandle(T listener) {
        Registration<T> registration = new Registration<>(Objects.requireNonNull(listener));
        listeners.add(registration);
        invoker = invokerFactory.create(snapshot());
        return () -> remove(registration);
    }

    /** 注册最多执行一次的接口监听器；首次回调抛出异常也视为已消费。 */
    @SuppressWarnings("unchecked")
    public synchronized EventHandle registerOnce(T listener) {
        Objects.requireNonNull(listener);
        Set<Class<?>> interfaces = new LinkedHashSet<>();
        for (Class<?> type = listener.getClass(); type != null; type = type.getSuperclass()) {
            interfaces.addAll(List.of(type.getInterfaces()));
        }
        Class<?>[] ifaces = interfaces.toArray(Class<?>[]::new);
        if (ifaces.length == 0) {
            throw new IllegalArgumentException("registerOnce requires interface-backed listener");
        }
        OnceWrapper<T> w = new OnceWrapper<>(this, listener);
        T proxy = (T) Proxy.newProxyInstance(
                listener.getClass().getClassLoader(), ifaces, w);
        w.registration = new Registration<>(proxy);
        listeners.add(w.registration);
        invoker = invokerFactory.create(snapshot());
        return w;
    }

    /** 移除第一个 equals 匹配的普通订阅；一次性订阅应使用返回的句柄。 */
    public synchronized boolean unregister(T listener) {
        for (Registration<T> registration : listeners) {
            if (Objects.equals(listener, registration.listener)) return remove(registration);
        }
        return false;
    }

    private synchronized boolean remove(Registration<T> registration) {
        registration.active.set(false);
        if (!listeners.remove(registration)) return false;
        invoker = invokerFactory.create(snapshot());
        return true;
    }

    /** 清空共享事件全部订阅；MOD 通常应只注销自己持有的句柄。 */
    public synchronized void clearListeners() {
        if (!listeners.isEmpty()) {
            for (Registration<T> registration : listeners) registration.active.set(false);
            listeners.clear();
            invoker = invokerFactory.create(snapshot());
        }
    }

    public synchronized int listenerCount() {
        return listeners.size();
    }

    /** 返回当前分发快照；保留旧引用不会自动刷新订阅列表。 */
    public T invoker() {
        return invoker;
    }

    /** 返回独立列表快照；修改该列表不会改变注册表。 */
    public synchronized List<T> listeners() {
        return snapshot();
    }

    private List<T> snapshot() {
        List<T> result = new ArrayList<>(listeners.size());
        for (Registration<T> registration : listeners) result.add(registration.listener);
        return result;
    }

    private static final class Registration<T> {
        final T listener;
        final AtomicBoolean active = new AtomicBoolean(true);
        Registration(T listener) { this.listener = listener; }
    }

    @FunctionalInterface
    public interface InvokerFactory<T> {
        /** 空列表必须生成返回中性值的分发器，例如可取消事件返回 PASS。 */
        T create(List<T> listeners);
    }

    private static final class OnceWrapper<T> implements EventHandle, InvocationHandler {
        private final Event<T> event;
        private final T original;
        Registration<T> registration;

        OnceWrapper(Event<T> event, T original) {
            this.event = event;
            this.original = original;
        }

        @Override
        public Object invoke(Object p, java.lang.reflect.Method method, Object[] args)
                throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "equals" -> p == args[0];
                    case "hashCode" -> System.identityHashCode(p);
                    case "toString" -> "OnceListener[" + original + "]";
                    default -> throw new UnsupportedOperationException(method.getName());
                };
            }
            // 旧快照仍可能持有代理；先原子地消耗资格，递归和并发调用也只能命中一次。
            boolean first = registration.active.compareAndSet(true, false);
            if (first) event.remove(registration);
            try {
                if (!method.canAccess(first ? original : event.emptyInvoker)) method.trySetAccessible();
                return method.invoke(first ? original : event.emptyInvoker, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }

        @Override
        public void unregister() {
            event.remove(registration);
        }
    }
}
