/*
 * CodeManifestCollector.java — 从实际 Loader 容器及本次入口结果导出启动代码清单。
 * 不枚举 mods 目录，不读取玩家配置，不访问网络；报告失败不改变既有启动语义。
 */
package net.fabricacs.api.impl;

import net.fabricmc.loader.api.ModContainer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

final class CodeManifestCollector {
    static CodeManifest collect(Collection<ModContainer> loaded, Map<String, String> states) {
        var budget = new ModContentFingerprint.Budget(2L * 1024 * 1024 * 1024);
        List<CodeManifest.Entry> entries = new ArrayList<>();
        if (loaded.size() > 1026) throw new IllegalArgumentException("Too many loaded containers");
        for (ModContainer mod : loaded.stream().sorted(Comparator.comparing(m -> m.getMetadata().getId())).toList()) {
            var metadata = mod.getMetadata();
            String id = metadata.getId();
            // 游戏使用启动层的实际归档指纹；Java 只比较运行时版本，不把 JDK 安装路径当作 MOD 内容。
            if (id.equals("airships") || id.equals("java")) continue;
            ModContentFingerprint.Result fingerprint;
            try { fingerprint = ModContentFingerprint.inspect(mod.getRootPaths(), budget); }
            catch (RuntimeException failure) { fingerprint = new ModContentFingerprint.Result("", "UNSUPPORTED_ORIGIN"); }
            entries.add(new CodeManifest.Entry(id, metadata.getVersion().getFriendlyString(), fingerprint.digest(),
                    fingerprint.status(), states.getOrDefault(id, "NOT_REACHED")));
        }
        String digest = System.getProperty("acbric.internal.game.fingerprint", "unavailable");
        if (!digest.matches("[0-9a-f]{64}")) digest = "unavailable";
        return new CodeManifest(System.getProperty("acbric.internal.game.version", "unknown"), digest,
                System.getProperty("java.runtime.version", "unknown"), entries);
    }

    static void publish(Path directory, CodeManifest manifest) throws IOException {
        String content = manifest.json().toString(2) + "\n";
        if (content.getBytes(StandardCharsets.UTF_8).length > CodeManifest.MAX_JSON_BYTES) throw new IOException("Manifest exceeds size limit");
        Files.createDirectories(directory);
        Path temp = Files.createTempFile(directory, "code-manifest-", ".tmp");
        try {
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            try { Files.move(temp, directory.resolve("code-manifest.json"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException failure) { Files.move(temp, directory.resolve("code-manifest.json"), StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
