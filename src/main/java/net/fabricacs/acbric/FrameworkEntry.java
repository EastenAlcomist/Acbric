/* FrameworkEntry.java — 框架根启动入口的实例绑定；只保存路径数据，检查冲突后原子替换。 */
package net.fabricacs.acbric;

import net.fabricacs.management.ModSelection;
import net.fabricmc.loader.impl.lib.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

final class FrameworkEntry {
    static final String CONFIG = ".acbric-active-instance.json";
    private FrameworkEntry() {}

    static String read(Path framework) throws IOException {
        Path file = framework.resolve(CONFIG);
        ModSelection.rejectLinks(file);
        if (!Files.exists(file)) return null;
        if (!Files.isRegularFile(file) || Files.size(file) > 65536)
            throw new IOException("LAUNCH_CONFIG_INVALID / 启动配置不可读取: " + file);
        String content = Files.readString(file, StandardCharsets.UTF_8);
        resolve(framework, content);
        return content;
    }

    static Path resolve(Path framework, String content) throws IOException {
        try (var json = new JsonReader(new StringReader(content))) {
            Map<String, String> fields = new HashMap<>();
            json.beginObject();
            while (json.hasNext()) {
                String key = json.nextName();
                if (json.peek() != JsonToken.STRING || fields.putIfAbsent(key, json.nextString()) != null) throw new IOException("Invalid field");
            }
            json.endObject();
            if (json.peek() != JsonToken.END_DOCUMENT || !fields.keySet().equals(Set.of("schema", "instanceDir"))
                    || !"1".equals(fields.get("schema")) || fields.get("instanceDir").isBlank()) throw new IOException("Invalid schema");
            Path instance = Path.of(fields.get("instanceDir"));
            if (!instance.isAbsolute() && (instance.getRoot() != null || instance.normalize().startsWith(".."))) throw new IOException("Invalid relative path");
            return framework.resolve(instance).toAbsolutePath().normalize();
        } catch (RuntimeException | IOException ex) {
            throw new IOException("LAUNCH_CONFIG_INVALID / Invalid launch configuration; file preserved / 启动配置损坏，文件保留", ex);
        }
    }

    /** 内部实例使用相对路径，外置实例保留绝对路径；移动后仍需向导重新绑定框架。 */
    static String serialize(Path framework, Path instance) {
        Path saved = instance.startsWith(framework) ? framework.relativize(instance) : instance;
        return "{\n  \"schema\": \"1\",\n  \"instanceDir\": " + ExternalLaunchSettings.quote(saved.toString()) + "\n}\n";
    }

    static void save(Path framework, Path instance, String expected) throws IOException {
        if (!Objects.equals(expected, read(framework))) throw new IOException("SETUP_CONFLICT / Default instance changed; check again / 默认实例已改变，请重新检查");
        Path temporary = Files.createTempFile(framework, ".acbric-entry-", ".tmp");
        try {
            Files.writeString(temporary, serialize(framework, instance), StandardCharsets.UTF_8);
            Files.move(temporary, framework.resolve(CONFIG), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
}
