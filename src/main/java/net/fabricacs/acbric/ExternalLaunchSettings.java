/* ExternalLaunchSettings.java — 在任何游戏类初始化之前合并启动设置；原文件只读，输出固定在实例。 */
package net.fabricacs.acbric;

import net.fabricacs.management.ModSelection;
import net.fabricmc.loader.impl.lib.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.*;

final class ExternalLaunchSettings {
    static Path prepare(ExternalGameInstallation.Plan plan) throws IOException {
        Map<String, String> values = read(plan.install().resolve("launch_settings.json"));
        Path override = plan.instance().resolve("config/launch-settings.json");
        ModSelection.rejectLinks(override);
        values.putAll(read(override));
        values.put("customDataDirectoryLocation", quote(plan.instance().resolve("userdata").toString()));
        Path gifs = plan.instance().resolve("userdata/gifs");
        ModSelection.rejectLinks(gifs); Files.createDirectories(gifs);
        values.put("customGIFSaveDirectoryLocation", quote(gifs.toString()));
        Path output = plan.instance().resolve(".fabric/acbric/launch-settings.json");
        ModSelection.rejectLinks(output); Files.createDirectories(output.getParent());
        Path temp = Files.createTempFile(output.getParent(), "launch-settings-", ".tmp");
        try {
            Files.writeString(temp, object(values) + "\n", StandardCharsets.UTF_8);
            Files.move(temp, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally { Files.deleteIfExists(temp); }
        return output;
    }

    static Map<String, String> read(Path file) throws IOException {
        ModSelection.rejectLinks(file);
        if (!Files.exists(file)) return new LinkedHashMap<>();
        if (!Files.isRegularFile(file) || Files.size(file) > 1048576) throw new IOException("LAUNCH_SETTINGS_INVALID / 启动设置不是文件或超过 1 MiB: " + file);
        try (JsonReader json = new JsonReader(Files.newBufferedReader(file, StandardCharsets.UTF_8))) {
            Map<String, String> value = fields(json, 0);
            if (json.peek() != JsonToken.END_DOCUMENT) throw new IOException("Trailing JSON");
            return value;
        } catch (IOException | RuntimeException ex) { throw new IOException("LAUNCH_SETTINGS_INVALID / 启动设置损坏，不覆盖原文件: " + file, ex); }
    }
    private static Map<String, String> fields(JsonReader json, int depth) throws IOException {
        Map<String, String> fields = new LinkedHashMap<>(); json.beginObject();
        while (json.hasNext()) {
            String key = json.nextName();
            if (fields.putIfAbsent(key, value(json, depth + 1)) != null) throw new IOException("Duplicate key: " + key);
        }
        json.endObject(); return fields;
    }
    private static String value(JsonReader json, int depth) throws IOException {
        if (depth > 32) throw new IOException("JSON too deeply nested");
        return switch (json.peek()) {
            case STRING -> quote(json.nextString());
            case BOOLEAN -> Boolean.toString(json.nextBoolean());
            case NULL -> { json.nextNull(); yield "null"; }
            case NUMBER -> new BigDecimal(json.nextString()).toString();
            case BEGIN_OBJECT -> object(fields(json, depth));
            case BEGIN_ARRAY -> {
                List<String> values = new ArrayList<>(); json.beginArray();
                while (json.hasNext()) values.add(value(json, depth + 1));
                json.endArray(); yield "[" + String.join(",", values) + "]";
            }
            default -> throw new IOException("Invalid JSON value");
        };
    }
    private static String object(Map<String, String> values) {
        return "{" + String.join(",", values.entrySet().stream().map(e -> quote(e.getKey()) + ":" + e.getValue()).toList()) + "}";
    }
    static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            if (c == '\\' || c == '"') out.append('\\').append(c);
            else if (c < 32) out.append(String.format(Locale.ROOT, "\\u%04x", (int)c));
            else out.append(c);
        }
        return out.append('"').toString();
    }
}
