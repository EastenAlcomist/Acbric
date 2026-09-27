# Runtime diagnostics and event scopes

**English** | [中文](EVENT_SCOPES.zh-CN.md)

Since `acbric_api >=0.3.3-dev.6`, `AcbricModContext.eventScope(name)` creates a `net.fabricacs.api.event.EventScope` that groups subscriptions and attributes callback failures to a MOD, event and purpose. MODs close scopes explicitly; the framework does not infer application, screen or campaign lifetime.

## Usage

```java
EventScope application = context.eventScope("application");
application.registerOnce(AirshipsDataEvents.DATA_LOADED, successful -> {
    context.logger().info("Data loaded: " + successful);
});
EventHandle tick = application.register(AirshipsClientEvents.CLIENT_TICK_START, game -> {
    // Local observation; callbacks still run on the event's calling thread.
});
tick.unregister(); // Cancel this registration only.
application.close(); // Cancel remaining registrations; idempotent.
```

Registering the same listener twice creates independent handles. Identically named scopes are independent objects. Creating a scope and successful dispatch do not write diagnostic files. `EventScope(String modId, String name)` supports custom events and tests; actual MODs should prefer the context factory. Labels must be nonblank and at most 256 characters. Attribution is caller-declared, not a permission/security boundary.

| Member | Contract |
| --- | --- |
| `register(Event<T>, T)` | Managed registration returning `EventHandle` |
| `registerOnce(Event<T>, T)` | Consumed before first execution, including failure |
| `close()` | Permanently close and unregister all entries; later registration throws `IllegalStateException` |
| `isClosed()` | Whether the scope is closed |
| `size()` | Unregistered/unconsumed entries remaining; a momentary observation under concurrency |
| `modId()` / `name()` | Ownership and purpose labels |

Scopes implement `AutoCloseable`. Use try-with-resources for temporary operations, not for application listeners that must outlive initialization. Managed listeners must implement Java interfaces, as required by proxies, similar to legacy registerOnce; inherited interfaces work. Plain `Event.register` retains support for existing custom listener types.

## Closing, snapshots and threads

Closing or unregistering releases the proxy's listener reference. Retained invoker snapshots subsequently use the event factory's empty/neutral result, such as `PASS`. A concurrent dispatch that already acquired the listener may still finish: close neither waits nor interrupts it and is not a callback-join barrier.

One-shot registrations leave both event and scope before invoking the callback, with a single winner under recursion/concurrency. Closing inside a callback suppresses later, not-yet-started callbacks in that scope even in the same snapshot. Other scopes and legacy registrations are unaffected. External `event.clearListeners()` or unregistering the proxy returned by `event.listeners()` also releases managed entries; MODs should use their own handles instead of clearing global events.

Managed proxies differ from original listeners; use the returned handle rather than `event.unregister(originalListener)`. Failed registration rolls back its managed entry. Close first deactivates every proxy, then attempts every removal; if a custom factory throws while rebuilding, other entries are still cleaned up and the first error is rethrown. Factories should be side-effect-free, accept empty lists and avoid manipulating other events/scopes during construction.

Scopes manage subscription lifetime, not game-thread scheduling, external side-effect rollback or automatic duplicate prevention. Close already-created scopes in your own initialization failure handling. Legacy prelaunch entrypoints do not automatically roll back all registrations.

## Campaign lifetimes

- Keep CREATED/LOADED/RESTORED/EXITED observers in an application scope across campaigns.
- When accepting a different world instance, close the previous campaign scope, create another and rebind local listeners. RESTORED also replaces the world; do not reinitialize shared data.
- On EXITED, close the campaign scope only if the world matches your binding. Keep application observers so subsequent campaigns still receive notifications.

CREATED/LOADED do not guarantee that a world is the active screen; consult [lifecycle contracts](CAMPAIGN_LIFECYCLE.md). MODs needing active-campaign-only behavior must use their own active-world/screen checks. The v3.1 demo illustrates binding/release without treating every JSON construction as entering gameplay.

## Runtime reports

Managed callback failures emit one `[Acbric runtime event]` JSON line to the console. During framework startup, reports also go to `game/logs/acbric/<session>/runtime-events.jsonl`, alongside `startup.json` and `launch.properties`. No failures means the file may not exist. Standalone scopes outside framework startup use bounded console output.

Records contain MOD ID, scope, event, callback method, listener class, thread, time, that registration's failure occurrence, original exception type/message and truncated stack. Event arguments, game objects and Throwable references are not retained. The diagnostic format is internal, not a stable data API.

Only occurrences **1, 2, 4, 8 and 16** are recorded per registration, with a session ceiling of **64 records and 1 MiB encoded data**. Messages are limited to 1024 characters, stacks to 4096 with a truncation flag. Every failure still propagates and listeners remain enabled after limits are reached; this is not a complete error-count history. A file-write failure disables further file output for that session, while bounded console output remains. Diagnostic/formatting failures are isolated on a best-effort basis and do not replace the original callback exception.

This is a troubleshooting report, not a transaction log; process termination may leave a partial final line. Check MOD messages/local paths before sharing. Nested events report each managed boundary; outer attribution identifies a propagation boundary, not necessarily the root cause.

## Compatibility

Adds `Event(String name, InvokerFactory<T>)` and `name()`. The original constructor remains and uses `custom`. All 34 built-in events use `EventClass.FIELD` names for diagnostics, without changing order/cancellation.

Legacy `register/registerWithHandle/registerOnce` do not automatically gain ownership or new error reporting and retain their snapshot/exception behavior. Managed callbacks remain synchronous and ordered; the first CANCEL stops further dispatch, and the original throwable propagates. No swallowing, automatic disabling or retries. Reflection wrappers are unwrapped; checked exceptions not declared by the callback interface may still be wrapped by Java Proxy, so use declared exceptions or RuntimeException/Error.

This revision adds no general network-command API, multiplayer handshake or hot unloading. The template now uses managed subscriptions. The independent v3.1 demo provides an F12 diagnostic probe while retaining campaign schema 3 and config schema 2.
