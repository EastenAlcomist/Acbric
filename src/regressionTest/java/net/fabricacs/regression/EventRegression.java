/*
 * EventRegression.java — 验证订阅注销、一次性代理、递归/并发分发与异常传播的回归用例。
 */
package net.fabricacs.regression;

import net.fabricacs.api.event.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;

public final class EventRegression {
    public interface Callback { void call(); }
    public interface Decision { EventResult decide(); }
    public static class Parent implements Callback { public void call() { inheritedCalls.incrementAndGet(); } }
    public static final class Child extends Parent {}
    private static final AtomicInteger inheritedCalls = new AtomicInteger();
    private static int checks;
    private static Event<Callback> event() { return new Event<>(ls -> () -> { for (var l : ls) l.call(); }); }
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++; System.out.println("PASS " + label);
    }
    public static int run() throws Exception {
        checks = 0;
        Event<Callback> e = event(); AtomicInteger calls = new AtomicInteger();
        EventHandle h = e.registerOnce(calls::incrementAndGet);
        Callback stale = e.invoker();
        stale.call(); stale.call(); e.invoker().call(); h.unregister();
        check(calls.get() == 1 && e.listenerCount() == 0, "once fires once even through retained invoker");
        h = e.registerOnce(calls::incrementAndGet); stale = e.invoker(); h.unregister(); stale.call();
        check(calls.get() == 1, "once handle cancels before first dispatch including stale snapshot");
        e.registerOnce(() -> { calls.incrementAndGet(); e.invoker().call(); }); e.invoker().call();
        check(calls.get() == 2, "recursive dispatch does not reenter once listener");
        Callback shared = calls::incrementAndGet;
        EventHandle first = e.registerWithHandle(shared); e.registerWithHandle(shared); first.unregister(); first.unregister();
        check(e.listenerCount() == 1, "handle is idempotent and removes only its own duplicate registration");
        e.clearListeners(); e.registerOnce(calls::incrementAndGet); stale = e.invoker(); e.clearListeners(); stale.call();
        check(calls.get() == 2, "clear cancels pending once snapshots");
        e.registerOnce(() -> { throw new IllegalStateException("expected"); }); stale = e.invoker();
        boolean thrown = false; try { stale.call(); } catch (IllegalStateException expected) { thrown = true; }
        stale.call(); check(thrown && e.listenerCount() == 0, "once consumes subscription when callback throws and preserves exception");
        e.registerOnce(new Child()); e.invoker().call();
        check(inheritedCalls.get() == 1, "once supports inherited callback interfaces");
        e.register(() -> { throw new IllegalStateException("expected"); }); e.register(calls::incrementAndGet);
        try { e.invoker().call(); } catch (IllegalStateException expected) { }
        check(calls.get() == 2, "ordinary listener exception still stops later listeners"); e.clearListeners();
        e.registerOnce(calls::incrementAndGet);
        Callback proxy = e.listeners().getFirst();
        check(proxy.equals(proxy) && !proxy.equals(shared), "once proxy has identity-based equality");
        check(e.unregister(proxy), "once proxy can be explicitly unregistered");

        Event<Decision> decisions = new Event<>(ls -> () -> {
            for (var l : ls) { EventResult result = l.decide(); if (result.shouldCancel()) return result; }
            return EventResult.PASS;
        });
        decisions.registerOnce(() -> EventResult.CANCEL); Decision cached = decisions.invoker();
        check(cached.decide() == EventResult.CANCEL && cached.decide() == EventResult.PASS, "consumed once uses factory's empty result, not null or stale CANCEL");

        Event<Callback> concurrent = event(); AtomicInteger wins = new AtomicInteger();
        concurrent.registerOnce(wins::incrementAndGet); Callback snapshot = concurrent.invoker();
        CountDownLatch ready = new CountDownLatch(8), start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            List<Future<?>> tasks = new ArrayList<>();
            for (int i = 0; i < 8; i++) tasks.add(pool.submit(() -> { ready.countDown(); start.await(); for (int j = 0; j < 100; j++) snapshot.call(); return null; }));
            boolean started = ready.await(5, TimeUnit.SECONDS); start.countDown();
            if (!started) throw new AssertionError("workers did not start");
            for (Future<?> task : tasks) task.get(10, TimeUnit.SECONDS);
        }
        check(wins.get() == 1 && concurrent.listenerCount() == 0, "concurrent once dispatch has exactly one winner");
        return checks;
    }
}
