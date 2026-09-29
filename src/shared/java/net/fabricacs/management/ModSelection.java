/*
 * ModSelection.java — 启动层与管理界面共用的停用配置协议；不依赖游戏类，不搬动 MOD 文件。
 * 两层分别编译同一源码，跨加载器仅通过文件和启动快照属性传递值，不共享可变静态状态。
 */
package net.fabricacs.management;

import net.fabricmc.loader.impl.lib.gson.JsonReader;
import net.fabricmc.loader.impl.lib.gson.JsonToken;
import java.io.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class ModSelection {
    public static final String LOADER_PROPERTY = "fabric.debug.disableModIds";
    public static final String EFFECTIVE_PROPERTY = "acbric.mods.disabled";
    public static final String EXTERNAL_PROPERTY = "acbric.mods.externalDisabled";
    public static final Set<String> PROTECTED = Set.of("acbric_api", "fabricloader", "airships", "java", "mixinextras");
    private ModSelection() {}

    public static Path file(Path configDir) { return configDir.resolve("acbric_mod_manager.json"); }

    public record Snapshot(Set<String> disabled, byte[] bytes) {
        public Snapshot { disabled = Set.copyOf(disabled); bytes = bytes == null ? null : bytes.clone(); }
        @Override public byte[] bytes() { return bytes == null ? null : bytes.clone(); }
    }

    public static void validateId(String id) throws IOException {
        if (!id.matches("[a-z][a-z0-9_-]{1,63}") || PROTECTED.contains(id))
            throw new IOException("Invalid or protected MOD ID: " + id);
    }

    public static Set<String> ids(String value) throws IOException {
        Set<String> ids = new TreeSet<>();
        if (value != null && !value.isBlank()) for (String part : value.split(",", -1)) {
            String id = part.trim(); validateId(id); ids.add(id);
        }
        return Set.copyOf(ids);
    }

    public static Snapshot read(Path path) throws IOException {
        rejectLinks(path);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return new Snapshot(Set.of(), null);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > 65536)
            throw new IOException("Invalid MOD manager config: " + path);
        byte[] bytes = Files.readAllBytes(path);
        Set<String> disabled = new TreeSet<>(), fields = new HashSet<>();
        try (JsonReader json = new JsonReader(new StringReader(new String(bytes, StandardCharsets.UTF_8)))) {
            json.beginObject();
            while (json.hasNext()) {
                String field = json.nextName();
                if (!fields.add(field)) throw new IOException("Duplicate manager field: " + field);
                switch (field) {
                    case "schemaVersion" -> {
                        if (json.peek() != JsonToken.NUMBER || !"1".equals(json.nextString()))
                            throw new IOException("Unsupported MOD manager schema");
                    }
                    case "disabledMods" -> {
                        json.beginArray();
                        while (json.hasNext()) {
                            if (json.peek() != JsonToken.STRING) throw new IOException("Expected MOD ID string");
                            String id = json.nextString(); validateId(id);
                            if (!disabled.add(id)) throw new IOException("Duplicate disabled MOD: " + id);
                        }
                        json.endArray();
                    }
                    default -> throw new IOException("Unknown MOD manager field: " + field);
                }
            }
            json.endObject();
            if (!fields.contains("disabledMods") || json.peek() != JsonToken.END_DOCUMENT)
                throw new IOException("Incomplete MOD manager config");
        } catch (RuntimeException ex) { throw new IOException("Invalid MOD manager config: " + path, ex); }
        return new Snapshot(disabled, bytes);
    }

    /** 保留启动参数已有的停用项；在 Provider 定位阶段执行，早于候选解析和 Mixin 加载。 */
    public static void applyAtStartup(Path configDir) throws IOException {
        Snapshot saved = read(file(configDir));
        Set<String> external = ids(System.getProperty(LOADER_PROPERTY));
        Set<String> effective = new TreeSet<>(external); effective.addAll(saved.disabled());
        System.setProperty(EXTERNAL_PROPERTY, String.join(",", new TreeSet<>(external)));
        System.setProperty(EFFECTIVE_PROPERTY, String.join(",", effective));
        System.setProperty(LOADER_PROPERTY, String.join(",", effective));
    }

    /** 锁内检查调用者读到的快照，原子替换；损坏、外部修改和锁冲突均不覆盖。 */
    public static Snapshot write(Path path, Snapshot expected, Set<String> disabled) throws IOException {
        for (String id : disabled) validateId(id);
        String text = "{\n  \"schemaVersion\": 1,\n  \"disabledMods\": [" +
                String.join(", ", new TreeSet<>(disabled).stream().map(id -> "\"" + id + "\"").toList()) + "]\n}\n";
        if (text.getBytes(StandardCharsets.UTF_8).length > 65536) throw new IOException("Too many disabled MODs");
        rejectLinks(path); Files.createDirectories(path.toAbsolutePath().getParent()); rejectLinks(path);
        Path lockPath = path.resolveSibling(path.getFileName() + ".lock"); rejectLinks(lockPath);
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = channel.tryLock()) {
            if (lock == null) throw new IOException("MOD manager config is in use");
            if (!Arrays.equals(expected.bytes(), read(path).bytes())) throw new IOException("MOD manager config changed; reopen the MOD screen");
            Path temp = Files.createTempFile(path.toAbsolutePath().getParent(), ".acbric-mods-", ".tmp");
            try {
                Files.writeString(temp, text, StandardCharsets.UTF_8);
                try (FileChannel output = FileChannel.open(temp, StandardOpenOption.WRITE)) { output.force(true); }
                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temp); }
        } catch (OverlappingFileLockException ex) { throw new IOException("MOD manager config is in use", ex); }
        return read(path);
    }

    public static void rejectLinks(Path path) throws IOException {
        for (Path p = path.toAbsolutePath().normalize(); p != null; p = p.getParent()) {
            if (Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
                var attrs = Files.readAttributes(p, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (attrs.isSymbolicLink() || attrs.isOther()) throw new IOException("Linked/special manager path: " + p);
            }
        }
    }
}
