/*
 * LaunchDiagnostics.java — 在依赖解析和 API 预启动之前保存构建身份及启动阶段。
 * 启动层仅用 JDK 写本地报告；通过会话 ID 关联 API 报告，写入失败不改变游戏启动结果。
 */
package net.fabricacs.acbric;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;

final class LaunchDiagnostics {
    private final Path directory;
    private final Properties report = new Properties();
    private boolean writeWarning;

    LaunchDiagnostics(Path gameDirectory, GameBuildIdentity identity) {
        String session = UUID.randomUUID().toString();
        // 仅传递字符串，避免启动层与游戏层共享自定义类身份；不是公开 MOD API。
        System.setProperty("acbric.internal.diagnostics.session", session);
        System.setProperty("acbric.internal.game.version", identity.rawVersion());
        System.setProperty("acbric.internal.game.fingerprint", identity.fingerprint());
        directory = gameDirectory.resolve("logs/acbric").resolve(session);
        report.setProperty("schema", "1");
        report.setProperty("session", session);
        report.setProperty("startedAt", Instant.now().toString());
        report.setProperty("pid", Long.toString(ProcessHandle.current().pid()));
        report.setProperty("java.version", System.getProperty("java.version", "unknown"));
        report.setProperty("game.rawVersion", identity.rawVersion());
        report.setProperty("game.normalizedVersion", identity.normalizedVersion());
        report.setProperty("game.versionSource", identity.versionSource());
        report.setProperty("game.fingerprint.algorithm", "acbric-game-archives-v1");
        report.setProperty("game.fingerprint", identity.fingerprint());
        report.setProperty("game.archiveCount", Integer.toString(identity.archives().size()));
        for (int i = 0; i < identity.archives().size(); i++) {
            var archive = identity.archives().get(i);
            report.setProperty("game.archive." + i + ".name", archive.name());
            report.setProperty("game.archive." + i + ".sha256", archive.sha256());
        }
        for (int i = 0; i < identity.warnings().size(); i++) report.setProperty("warning." + i, identity.warnings().get(i));
        System.out.println("[Acbric] Game " + identity.rawVersion() + "; code fingerprint=" + identity.fingerprint());
        System.out.println("[Acbric] Diagnostics: " + directory.toAbsolutePath());
        for (String warning : identity.warnings()) System.err.println("[Acbric] " + warning);
        phase("GAME_LOCATED", null);
    }

    void phase(String phase, Throwable failure) {
        report.setProperty("phase", phase);
        report.setProperty("updatedAt", Instant.now().toString());
        if (failure != null) {
            report.setProperty("failure.type", failure.getClass().getName());
            report.setProperty("failure.message", String.valueOf(failure.getMessage()));
        }
        Path temporary = null;
        try {
            Files.createDirectories(directory);
            temporary = Files.createTempFile(directory, "launch-", ".tmp");
            try (var writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                report.store(writer, "Acbric launch diagnostics; internal format, not a compatibility guarantee");
            }
            Path target = directory.resolve("launch.properties");
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            if (!writeWarning) System.err.println("[Acbric] Cannot write launch diagnostics: " + e);
            writeWarning = true;
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException | RuntimeException ignored) { }
            }
        }
    }
}
