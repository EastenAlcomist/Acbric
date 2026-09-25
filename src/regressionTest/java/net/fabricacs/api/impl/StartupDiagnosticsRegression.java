/*
 * StartupDiagnosticsRegression.java — 通过真实入口桥验证失败续行、多入口汇总和诊断不可写降级。
 */
package net.fabricacs.api.impl;

import net.fabricacs.api.AcbricInitializer;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.impl.metadata.BuiltinModMetadata;
import org.json.JSONObject;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class StartupDiagnosticsRegression {
    private static int checks;
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
        System.out.println("PASS " + label);
    }
    private static ModContainer mod(String id) {
        var metadata = new BuiltinModMetadata.Builder(id, "1.0.0").setName(id).build();
        return (ModContainer) Proxy.newProxyInstance(ModContainer.class.getClassLoader(), new Class<?>[]{ModContainer.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getMetadata")) return metadata;
                    throw new AssertionError(method.getName());
                });
    }
    private static EntrypointContainer<AcbricInitializer> entry(ModContainer mod, String name, Supplier<AcbricInitializer> factory) {
        return new EntrypointContainer<>() {
            public AcbricInitializer getEntrypoint() { return factory.get(); }
            public ModContainer getProvider() { return mod; }
            public String getDefinition() { return name; }
        };
    }
    private static JSONObject row(JSONObject report, String id) {
        var mods = report.getJSONArray("mods");
        for (int i = 0; i < mods.length(); i++) if (mods.getJSONObject(i).getString("id").equals(id)) return mods.getJSONObject(i);
        throw new AssertionError("Missing mod " + id);
    }
    public static int run(Path root) throws Exception {
        checks = 0;
        Files.createDirectories(root);
        String previous = System.getProperty("acbric.internal.diagnostics.session");
        String session = UUID.randomUUID().toString();
        System.setProperty("acbric.internal.diagnostics.session", session);
        try {
            var good = mod("good_mod");
            var mixed = mod("mixed_mod");
            var construction = mod("construction_failure");
            var resource = mod("resource_only");
            var loaded = List.of(good, mixed, construction, resource);
            AtomicInteger successful = new AtomicInteger();
            var diagnostics = new StartupDiagnostics(root, loaded);
            Path reportFile = root.resolve("logs/acbric/" + session + "/startup.json");
            check(row(new JSONObject(Files.readString(reportFile)), "resource_only").getString("acbricStatus").equals("NOT_REACHED"),
                    "before discovery loaded mod does not imply initialized or no entrypoint");
            var entries = List.of(
                    entry(mixed, "failing-callback", () -> () -> { throw new IllegalStateException("expected callback failure"); }),
                    entry(construction, "failing-constructor", () -> { throw new IllegalArgumentException("expected construction failure"); }),
                    entry(mixed, "successful-callback", () -> successful::incrementAndGet),
                    entry(good, "legacy-noarg", () -> successful::incrementAndGet));
            AcbricApiPreLaunch.initializeEntrypoints(entries, diagnostics);
            JSONObject report = new JSONObject(Files.readString(reportFile));
            check(successful.get() == 2 && report.getString("phase").equals("ENTRYPOINTS_COMPLETED_WITH_FAILURES"),
                    "callback and construction failure do not prevent later entrypoints");
            var mixedRow = row(report, "mixed_mod");
            check(mixedRow.getBoolean("loaded") && mixedRow.getString("acbricStatus").equals("FAILED")
                    && mixedRow.getJSONArray("entrypoints").getJSONObject(1).getString("status").equals("SUCCEEDED"),
                    "multiple entrypoints retain partial failure instead of overwriting with success");
            check(row(report, "good_mod").getString("acbricStatus").equals("SUCCEEDED")
                    && row(report, "resource_only").getString("acbricStatus").equals("NO_ACBRIC_ENTRYPOINT"),
                    "no Acbric entrypoint is distinct from successful initialization");
            var failure = row(report, "construction_failure").getJSONArray("entrypoints").getJSONObject(0).getJSONObject("failure");
            check(failure.getString("type").equals("java.lang.IllegalArgumentException")
                    && failure.getString("stackTrace").contains("expected construction failure"), "failure includes type and stack trace");
            try (var files = Files.list(reportFile.getParent())) {
                check(files.noneMatch(file -> file.toString().endsWith(".tmp")), "diagnostics leave no temporary reports");
            }
            Path blocked = Files.createDirectories(root.resolve("blocked"));
            Files.writeString(blocked.resolve("logs"), "user file");
            AcbricApiPreLaunch.initializeEntrypoints(List.of(entry(good, "still-runs", () -> successful::incrementAndGet)),
                    new StartupDiagnostics(blocked, List.of(good)));
            check(successful.get() == 3 && Files.readString(blocked.resolve("logs")).equals("user file"),
                    "unwritable startup report preserves initialization and existing file");
            var interrupted = new StartupDiagnostics(root.resolve("interrupted"), loaded);
            JSONObject running = interrupted.register(entries.get(0));
            interrupted.register(entries.get(1));
            interrupted.discoveryComplete();
            interrupted.starting(running);
            JSONObject partial = new JSONObject(Files.readString(root.resolve("interrupted/logs/acbric/" + session + "/startup.json")));
            check(row(partial, "mixed_mod").getString("acbricStatus").equals("RUNNING")
                    && row(partial, "construction_failure").getString("acbricStatus").equals("PENDING"),
                    "interrupted initialization identifies current and pending entrypoints");
            interrupted.failed(new IllegalStateException("prelaunch failed"));
            partial = new JSONObject(Files.readString(root.resolve("interrupted/logs/acbric/" + session + "/startup.json")));
            check(partial.getString("phase").equals("PRELAUNCH_FAILED"), "fatal prelaunch stage is recorded separately");
        } finally {
            if (previous == null) System.clearProperty("acbric.internal.diagnostics.session");
            else System.setProperty("acbric.internal.diagnostics.session", previous);
        }
        return checks;
    }
}
