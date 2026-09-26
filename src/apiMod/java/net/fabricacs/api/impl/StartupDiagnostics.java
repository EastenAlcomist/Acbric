/*
 * StartupDiagnostics.java — 记录 Loader 已加载 MOD 与 acbric 入口执行结果，二者不能混为成功。
 * 仅保存本地排错报告，不提供新公开 API，不吞掉原流程异常，也不尝试回滚 MOD 状态。
 */
package net.fabricacs.api.impl;

import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class StartupDiagnostics {
    private final Path directory;
    private final JSONObject report = new JSONObject();
    private final Map<String, JSONObject> mods = new LinkedHashMap<>();
    private boolean discoveryComplete;
    private boolean writeWarning;
    private final Collection<ModContainer> loadedMods;

    StartupDiagnostics(Path gameDir, Collection<ModContainer> loadedMods) {
        this.loadedMods = java.util.List.copyOf(loadedMods);
        String session = System.getProperty("acbric.internal.diagnostics.session", "");
        // 防止外部属性意外变成路径；正常会话由 Provider 在每次启动时重新生成。
        if (!session.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")) session = UUID.randomUUID().toString();
        directory = gameDir.resolve("logs/acbric").resolve(session);
        RuntimeEventDiagnostics.initialize(directory);
        report.put("schema", 1);
        report.put("session", session);
        report.put("startedAt", Instant.now().toString());
        report.put("javaVersion", System.getProperty("java.version", "unknown"));
        report.put("scope", "Fabric loaded mods and acbric entrypoints only; not gameplay or resource validation");
        JSONArray rows = new JSONArray();
        loadedMods.stream().sorted(java.util.Comparator.comparing(mod -> mod.getMetadata().getId())).forEach(mod -> {
            var metadata = mod.getMetadata();
            JSONObject row = new JSONObject();
            row.put("id", metadata.getId());
            row.put("name", metadata.getName());
            row.put("version", metadata.getVersion().getFriendlyString());
            row.put("loaded", true);
            row.put("entrypoints", new JSONArray());
            mods.put(metadata.getId(), row);
            rows.put(row);
        });
        report.put("mods", rows);
        phase("PREPARING_BUNDLED_RESOURCES");
    }

    JSONObject register(EntrypointContainer<?> container) {
        JSONObject row = mods.get(container.getProvider().getMetadata().getId());
        JSONObject entry = new JSONObject();
        entry.put("definition", container.getDefinition());
        entry.put("status", "PENDING");
        row.getJSONArray("entrypoints").put(entry);
        return entry;
    }

    void discoveryComplete() {
        discoveryComplete = true;
        phase("INITIALIZING_ENTRYPOINTS");
    }

    void starting(JSONObject entry) {
        entry.put("status", "RUNNING");
        persist();
    }

    void completed(JSONObject entry, Throwable failure) {
        entry.put("status", failure == null ? "SUCCEEDED" : "FAILED");
        if (failure != null) entry.put("failure", failure(failure));
        persist();
    }

    void finish() {
        boolean failed = mods.values().stream().anyMatch(row -> status(row).equals("FAILED"));
        phase(failed ? "ENTRYPOINTS_COMPLETED_WITH_FAILURES" : "ENTRYPOINTS_COMPLETED");
        try {
            Map<String, String> states = new LinkedHashMap<>();
            mods.forEach((id, row) -> states.put(id, status(row)));
            CodeManifest manifest = CodeManifestCollector.collect(loadedMods, states);
            StartupCodeManifest.current = manifest;
            CodeManifestCollector.publish(directory, manifest);
            System.out.println("[Acbric API] Local code manifest: " + directory.resolve("code-manifest.json"));
        } catch (IOException | RuntimeException failure) {
            System.err.println("[Acbric API] Cannot export local code manifest: " + failure.getClass().getSimpleName());
        }
        System.out.println("[Acbric API] Entrypoint diagnostics: " + directory.resolve("startup.json")
                + (failed ? " (initialization failures recorded)" : ""));
    }

    void failed(Throwable failure) {
        report.put("failure", failure(failure));
        phase("PRELAUNCH_FAILED");
    }

    private void phase(String value) {
        report.put("phase", value);
        persist();
    }

    /** 失败优先，避免同一 MOD 的第二个入口成功后把第一个入口失败掩盖掉。 */
    private String status(JSONObject mod) {
        JSONArray entries = mod.getJSONArray("entrypoints");
        if (!discoveryComplete) return "NOT_REACHED";
        if (entries.length() == 0) return "NO_ACBRIC_ENTRYPOINT";
        boolean pending = false, running = false;
        for (int i = 0; i < entries.length(); i++) {
            String state = entries.getJSONObject(i).getString("status");
            if (state.equals("FAILED")) return "FAILED";
            pending |= state.equals("PENDING");
            running |= state.equals("RUNNING");
        }
        return running ? "RUNNING" : pending ? "PENDING" : "SUCCEEDED";
    }

    private static JSONObject failure(Throwable error) {
        JSONObject detail = new JSONObject();
        detail.put("type", error.getClass().getName());
        detail.put("message", String.valueOf(error.getMessage()));
        StringWriter trace = new StringWriter();
        error.printStackTrace(new PrintWriter(trace));
        detail.put("stackTrace", trace.toString());
        return detail;
    }

    /** 原子发布尽可能保留完整报告；目录不可写时仅告警一次，继续原来的启动流程。 */
    private void persist() {
        Path temporary = null;
        try {
            report.put("updatedAt", Instant.now().toString());
            for (JSONObject mod : mods.values()) mod.put("acbricStatus", status(mod));
            DeveloperTools.startup(report,directory);
            Files.createDirectories(directory);
            temporary = Files.createTempFile(directory, "startup-", ".tmp");
            Files.writeString(temporary, report.toString(2) + "\n", StandardCharsets.UTF_8);
            Path target = directory.resolve("startup.json");
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            if (!writeWarning) System.err.println("[Acbric API] Cannot write startup diagnostics: " + e);
            writeWarning = true;
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException | RuntimeException ignored) { }
            }
        }
    }
}
