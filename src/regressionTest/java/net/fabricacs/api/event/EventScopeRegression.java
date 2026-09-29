/* EventScopeRegression.java — 覆盖归属订阅、快照失效、引用释放、并发/重入及旧接口兼容边界。 */
package net.fabricacs.api.event;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class EventScopeRegression {
    public interface Callback { void call(); }
    public interface Decision { EventResult decide(); }
    public interface Checked { void call() throws IOException; }
    public static class Parent implements Callback { public void call() {} }
    public static final class Child extends Parent {}
    private static int checks;
    private static void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); checks++; }
    private static Event<Callback> event() { return new Event<>("test.TICK", ls -> () -> { for (var l : ls) l.call(); }); }
    public static int run() throws Exception {
        checks = 0;
        Event<Callback> event = event(); EventScope a = new EventScope("mod_a", "campaign"), b = new EventScope("mod_b", "campaign");
        AtomicInteger own = new AtomicInteger(), other = new AtomicInteger(), legacy = new AtomicInteger();
        Callback shared = own::incrementAndGet;
        EventHandle handle = a.register(event, shared); a.register(event, shared); b.register(event, other::incrementAndGet); event.register(legacy::incrementAndGet);
        check(a.size() == 2 && b.size() == 1 && event.listenerCount() == 4, "scopes track duplicate registrations independently");
        Callback snapshot = event.invoker(); handle.unregister(); handle.unregister(); snapshot.call();
        check(own.get() == 1 && other.get() == 1 && legacy.get() == 1 && a.size() == 1, "owned handle deactivates old snapshots without touching other registrations");
        a.close(); a.close(); snapshot.call();
        check(own.get() == 1 && other.get() == 2 && legacy.get() == 2 && a.isClosed() && a.size() == 0, "close is idempotent and isolated");
        try { a.register(event, shared); throw new AssertionError(); } catch (IllegalStateException expected) { checks++; }
        b.close(); event.clearListeners();
        EventScope fresh = new EventScope("mod_a", "campaign"); fresh.register(event, shared); event.invoker().call();
        check(own.get() == 2 && fresh.size() == 1, "same-name scope is an independent lifetime");
        Callback proxy = event.listeners().getFirst(); var entry = (EventScope.ScopedListener<?>) Proxy.getInvocationHandler(proxy);
        check(proxy.equals(proxy) && !proxy.equals(shared) && proxy.toString().contains("mod_a/campaign/test.TICK"), "managed proxy identity and diagnostic labels");
        event.clearListeners(); proxy.call();
        check(entry.target.get() == null && fresh.size() == 0 && own.get() == 2, "clear releases listener references and invalidates managed snapshots");
        fresh.register(event, shared); proxy = event.listeners().getFirst(); check(event.unregister(proxy) && fresh.size() == 0, "explicit proxy removal updates scope");
        EventScope once = new EventScope("once_mod", "session");
        once.registerOnce(event, () -> { own.incrementAndGet(); event.invoker().call(); }); snapshot = event.invoker(); snapshot.call(); snapshot.call();
        check(own.get() == 3 && once.size() == 0 && event.listenerCount() == 0, "once consumes before recursive calls and detaches scope");
        once.registerOnce(event, () -> { throw new IllegalStateException("intentional once"); }); snapshot = event.invoker();
        try { snapshot.call(); throw new AssertionError(); } catch (IllegalStateException expected) { checks++; }
        snapshot.call(); check(once.size() == 0, "once failure never retries");
        once.register(event, new Child()); event.invoker().call(); check(once.size() == 1, "inherited callback interface supported"); once.close();
        Event<Decision> decision = new Event<>("test.DECISION", ls -> () -> { for (var l : ls) { var r = l.decide(); if (r.shouldCancel()) return r; } return EventResult.PASS; });
        EventScope decisions = new EventScope("decision_mod", "menu"); decisions.register(decision, () -> EventResult.CANCEL); Decision old = decision.invoker();
        check(old.decide() == EventResult.CANCEL, "scoped listener preserves cancellation"); decisions.close();
        check(old.decide() == EventResult.PASS, "closed proxy returns factory neutral result");
        EventScope failing = new EventScope("broken_mod", "runtime"); RuntimeException original = new RuntimeException("original identity");
        AtomicInteger later = new AtomicInteger(); failing.register(event, () -> { throw original; }); event.register(later::incrementAndGet);
        for (int i = 0; i < 20; i++) {
            try { event.invoker().call(); throw new AssertionError(); } catch (RuntimeException caught) { check(caught == original, "original exception propagated on every failure"); }
        }
        check(later.get() == 0 && failing.size() == 1, "failure stops later callbacks without disabling listener"); failing.close(); event.clearListeners();
        Error originalError = new AssertionError("original error"); EventScope errors = new EventScope("broken_mod", "error"); errors.register(event, () -> { throw originalError; });
        try { event.invoker().call(); throw new AssertionError(); } catch (Error caught) { check(caught == originalError, "Error identity preserved"); } errors.close();
        Event<Checked> checked = new Event<>("test.CHECKED", ls -> () -> { for (var l : ls) l.call(); }); IOException io = new IOException("checked original");
        EventScope checkedScope = new EventScope("broken_mod", "checked"); checkedScope.register(checked, () -> { throw io; });
        try { checked.invoker().call(); throw new AssertionError(); } catch (IOException caught) { check(caught == io, "declared checked exception preserved"); } checkedScope.close();
        EventScope self = new EventScope("self_mod", "self-close"); self.register(event, self::close); self.register(event, () -> { throw new AssertionError("closed later listener ran"); });
        event.invoker().call(); check(self.size() == 0 && event.listenerCount() == 0, "close inside callback suppresses later callbacks in same snapshot");

        EventScope parallel = new EventScope("parallel_mod", "once"); AtomicInteger wins = new AtomicInteger(); parallel.registerOnce(event, wins::incrementAndGet); final Callback concurrent = event.invoker();
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            List<Future<?>> tasks = new ArrayList<>(); CountDownLatch start = new CountDownLatch(1);
            for (int i=0;i<8;i++) tasks.add(pool.submit(() -> { start.await(); for(int j=0;j<100;j++) concurrent.call(); return null; }));
            start.countDown(); for (Future<?> task : tasks) task.get(5, TimeUnit.SECONDS);
        }
        check(wins.get() == 1 && parallel.size() == 0 && event.listenerCount() == 0, "concurrent once has one winner");
        EventScope inFlight = new EventScope("parallel_mod", "in-flight"); CountDownLatch entered = new CountDownLatch(1), finish = new CountDownLatch(1); AtomicInteger done = new AtomicInteger();
        inFlight.register(event, () -> { entered.countDown(); try { if (!finish.await(5, TimeUnit.SECONDS)) throw new AssertionError("timeout"); } catch (InterruptedException ex) { throw new RuntimeException(ex); } done.incrementAndGet(); });
        final Callback running = event.invoker();
        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            Future<?> task = pool.submit(running::call);
            try { check(entered.await(5, TimeUnit.SECONDS), "callback entered"); inFlight.close(); check(event.listenerCount() == 0 && !task.isDone(), "close does not wait for in-flight callback"); }
            finally { finish.countDown(); }
            task.get(5, TimeUnit.SECONDS);
        }
        running.call(); check(done.get() == 1, "in-flight callback finishes once and retained snapshot stays inactive");
        EventScope brokenFactory = new EventScope("factory_mod", "factory");
        Event<Callback> invalid = new Event<>("bad-factory", ls -> { if (!ls.isEmpty()) throw new IllegalStateException("factory failure"); return () -> {}; });
        try { brokenFactory.register(invalid, () -> {}); throw new AssertionError(); } catch (IllegalStateException expected) { checks++; }
        check(brokenFactory.size() == 0 && invalid.listenerCount() == 0, "failed registration rolls back owned entries");
        EventScope cleanup = new EventScope("factory_mod", "cleanup"); AtomicInteger rebuild = new AtomicInteger();
        Event<Callback> badClose = new Event<>("bad-close", ls -> { if (rebuild.incrementAndGet() > 2) throw new IllegalStateException("close failure"); return () -> { for (var l : ls) l.call(); }; });
        cleanup.register(badClose, () -> { throw new AssertionError("should be inactive"); }); cleanup.register(event, () -> { throw new AssertionError("cleanup skipped"); });
        Callback cachedBad = badClose.invoker();
        try { cleanup.close(); throw new AssertionError(); } catch (IllegalStateException expected) { checks++; }
        cachedBad.call(); check(cleanup.isClosed() && cleanup.size() == 0 && badClose.listenerCount() == 0 && event.listenerCount() == 0, "close attempts every registration despite custom factory failure");
        // 原注册仍保留旧快照语义，不受新范围语义影响。
        AtomicInteger historical = new AtomicInteger(); EventHandle oldHandle = event.registerWithHandle(historical::incrementAndGet); snapshot = event.invoker(); oldHandle.unregister(); snapshot.call();
        check(historical.get() == 1, "legacy normal registration retains historical snapshot behavior");
        for (Class<?> type : List.of(AirshipsCampaignEvents.class, AirshipsClientEvents.class, AirshipsCombatUiEvents.class, AirshipsDataEvents.class, AirshipsLifecycleEvents.class)) {
            for (var field : type.getFields()) if (field.getType() == Event.class) check(((Event<?>) field.get(null)).name().equals(type.getSimpleName()+"."+field.getName()), "built-in event has stable diagnostic name");
        }
        System.out.println("EVENT SCOPE REGRESSION PASS: " + checks + " checks"); return checks;
    }
}
