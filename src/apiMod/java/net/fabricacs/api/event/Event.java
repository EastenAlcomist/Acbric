package net.fabricacs.api.event;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A lightweight event bus backed by a thread-safe listener list.
 *
 * <p>The invoker (dispatcher) is recreated only when the listener list
 * changes, which is rare (typically only at startup).  Hot-path
 * {@code invoker()} returns a stable reference.</p>
 */
public final class Event<T> {
    private final List<T> listeners;
    private final InvokerFactory<T> invokerFactory;
    private volatile T invoker;

    public Event(InvokerFactory<T> invokerFactory) {
        this.invokerFactory = Objects.requireNonNull(invokerFactory);
        this.listeners = new ArrayList<>();
        this.invoker = invokerFactory.create(snapshot());
    }

    public synchronized void register(T listener) {
        listeners.add(Objects.requireNonNull(listener));
        invoker = invokerFactory.create(snapshot());
    }

    public EventHandle registerWithHandle(T listener) {
        register(listener);
        return () -> unregister(listener);
    }

    @SuppressWarnings("unchecked")
    public EventHandle registerOnce(T listener) {
        Objects.requireNonNull(listener);
        Class<?>[] ifaces = listener.getClass().getInterfaces();
        if (ifaces.length == 0) {
            throw new IllegalArgumentException("registerOnce requires interface-backed listener");
        }
        OnceWrapper<T> w = new OnceWrapper<>(this, listener);
        T proxy = (T) Proxy.newProxyInstance(
                listener.getClass().getClassLoader(), ifaces, w);
        w.proxy = proxy;
        register(proxy);
        return w;
    }

    public synchronized boolean unregister(T listener) {
        boolean removed = listeners.remove(listener);
        if (removed) invoker = invokerFactory.create(snapshot());
        return removed;
    }

    public synchronized void clearListeners() {
        if (!listeners.isEmpty()) {
            listeners.clear();
            invoker = invokerFactory.create(snapshot());
        }
    }

    public int listenerCount() {
        return listeners.size();
    }

    /** Returns the current invoker. */
    public T invoker() {
        return invoker;
    }

    public synchronized List<T> listeners() {
        return new ArrayList<>(listeners);
    }

    private List<T> snapshot() {
        return new ArrayList<>(listeners);
    }

    @FunctionalInterface
    public interface InvokerFactory<T> {
        T create(List<T> listeners);
    }

    private static final class OnceWrapper<T> implements EventHandle, InvocationHandler {
        private final Event<T> event;
        private final T original;
        T proxy;

        OnceWrapper(Event<T> event, T original) {
            this.event = event;
            this.original = original;
        }

        @Override
        public Object invoke(Object p, java.lang.reflect.Method method, Object[] args)
                throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(original, args);
            }
            unregister();
            try {
                return method.invoke(original, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }

        @Override
        public void unregister() {
            event.unregister(proxy);
        }
    }
}
