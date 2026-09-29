/* SavedRuleFiles.java — 原生目录存档的有界快照；原样保留其他块，只在新目录替换世界标记与规则块。 */
package net.fabricacs.api.impl;

import org.json.JSONObject;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;

final class SavedRuleFiles {
    private static final long MAX_TOTAL = 256L * 1024 * 1024;
    private static final int MAX_FILES = 20_000, MAX_JSON = 16 * 1024 * 1024;
    private final Path path;
    private final SortedMap<String, byte[]> files;
    private final String fingerprint;
    private final String id;
    private final JSONObject index, world;
    private final String worldFile;

    private SavedRuleFiles(Path path, SortedMap<String, byte[]> files) throws IOException {
        this.path = path; this.files = Collections.unmodifiableSortedMap(files);
        fingerprint = fingerprint(files);
        try {
            JSONObject header = json(required("index.gug"));
            if (CampaignDataStore.version(header, "version") != 1) throw new IOException("UNSUPPORTED_SAVE_HEADER");
            id = segment(header.getString("id"));
            index = json(required(id + "/index.gug"));
            for (Object raw : index.keySet()) {
                String key = (String)raw;
                segment(key); int version = CampaignDataStore.version(index, key);
                required(id + "/" + version + "_" + key + ".gug");
            }
            worldFile = chunk("world"); world = json(required(worldFile));
            world.getJSONObject("map"); // 拒绝错误类型，并统一包装为可诊断的存档结构错误。
        } catch (RuntimeException bad) { throw new IOException("INVALID_SAVE_STRUCTURE", bad); }
    }

    Path path() { return path; }
    private byte[] required(String name) throws IOException {
        byte[] bytes = files.get(name);
        if (bytes == null) throw new IOException("MISSING_SAVE_FILE: " + name);
        return bytes;
    }
    private String chunk(String name) { return id + "/" + CampaignDataStore.version(index, name) + "_" + name + ".gug"; }
    private static String segment(String text) throws IOException {
        if (text.isEmpty() || text.equals(".") || text.equals("..") || text.endsWith(".") || text.endsWith(" ")
                || text.chars().anyMatch(c -> c < 32 || "\\/:*?\"<>|".indexOf(c) >= 0)) throw new IOException("UNSAFE_SAVE_PATH");
        return text;
    }
    private static JSONObject json(byte[] bytes) throws IOException {
        if (bytes.length > MAX_JSON) throw new IOException("SAVE_JSON_TOO_LARGE");
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            Object parsed = JSONObject.read(input);
            if (!(parsed instanceof JSONObject result) || input.available() != 0) throw new IOException("INVALID_BINARY_JSON");
            return result;
        } catch (RuntimeException invalid) { throw new IOException("INVALID_BINARY_JSON", invalid); }
    }
    private static byte[] bytes(JSONObject object) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(); object.toStream(output);
        if (output.size() > MAX_JSON) throw new IOException("SAVE_JSON_TOO_LARGE");
        return output.toByteArray();
    }

    static SavedRuleFiles read(Path source) throws IOException {
        if (Files.isSymbolicLink(source) || !Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("DIRECTORY_SAVE_REQUIRED: only native IODirectory saves are supported / 仅支持原生目录存档");
        Path root = source.toRealPath(); SortedMap<String, byte[]> files = new TreeMap<>(); long[] total = {0};
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!dir.toRealPath().equals(dir.normalize()) || attrs.isSymbolicLink() || attrs.isOther()) throw new IOException("SAVE_LINK_REFUSED");
                if (root.relativize(dir).getNameCount() > 16) throw new IOException("SAVE_DEPTH_LIMIT");
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!attrs.isRegularFile() || attrs.isSymbolicLink() || !file.toRealPath().equals(file.normalize())) throw new IOException("SAVE_LINK_REFUSED");
                if (files.size() >= MAX_FILES || attrs.size() > MAX_TOTAL - total[0]) throw new IOException("SAVE_SIZE_LIMIT");
                // 有界读取还检查读取期间文件增大的情况。
                byte[] data;
                try (InputStream in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                    data = in.readNBytes((int)(MAX_TOTAL - total[0]) + 1);
                }
                total[0] += data.length;
                if (total[0] > MAX_TOTAL || data.length != attrs.size()) throw new IOException("SAVE_CHANGED_DURING_READ");
                files.put(root.relativize(file).toString().replace('\\','/'), data);
                return FileVisitResult.CONTINUE;
            }
        });
        return new SavedRuleFiles(root, files);
    }
    CampaignDataStore store() throws IOException {
        CampaignDataStore store = new CampaignDataStore();
        CampaignDataHooks.read(store, world.getJSONObject("map"), name -> json(required(chunk(name))));
        return store;
    }
    SavedRuleFiles convert(CampaignDataStore store) throws IOException {
        SortedMap<String, byte[]> result = new TreeMap<>(files);
        // 通过二进制往返复制游戏 JSON，避免文本浮点格式改变其他游戏字段。
        JSONObject updatedWorld = json(bytes(world)), updatedIndex = json(bytes(index));
        updatedWorld.getJSONObject("map").put(CampaignDataHooks.KEY, 1);
        if (!updatedIndex.has(CampaignDataHooks.CHUNK)) updatedIndex.put(CampaignDataHooks.CHUNK, 1);
        String block = id + "/" + CampaignDataStore.version(updatedIndex, CampaignDataHooks.CHUNK) + "_" + CampaignDataHooks.CHUNK + ".gug";
        result.put(block, bytes(new JSONObject().put("format", 1).put("payload", store.snapshot().toString())));
        result.put(worldFile, bytes(updatedWorld));
        result.put(id + "/index.gug", bytes(updatedIndex));
        // 只在输出副本更新已有备用索引，防止恢复路径选到过期的块目录。
        if (result.containsKey(id + "/index2.gug")) result.put(id + "/index2.gug", bytes(updatedIndex));
        if (result.containsKey("index2.gug")) result.put("index2.gug", result.get("index.gug"));
        return new SavedRuleFiles(path, result);
    }
    @FunctionalInterface interface Guard { void check() throws IOException; }
    Path publish(SavedRuleFiles output, Path destination, Guard guard) throws IOException {
        Path requested = destination.toAbsolutePath().normalize();
        if (requested.getParent() == null || requested.getFileName() == null) throw new IOException("INVALID_SAVE_DESTINATION");
        Path parent = requested.getParent().toRealPath();
        Path target = parent.resolve(segment(requested.getFileName().toString()));
        if (target.startsWith(path) || path.startsWith(target)) throw new IOException("SAVE_PATH_OVERLAP");
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new FileAlreadyExistsException(target.toString());
        if (!fingerprint.equals(read(path).fingerprint)) throw new IOException("SOURCE_SAVE_CHANGED: inspect again / 请重新检查");
        Path staging = Files.createTempDirectory(parent, ".acbric-rules-");
        boolean published = false;
        try {
            for (var entry : output.files.entrySet()) {
                Path file = staging.resolve(entry.getKey()).normalize();
                if (!file.startsWith(staging)) throw new IOException("UNSAFE_SAVE_PATH");
                Files.createDirectories(file.getParent());
                Files.write(file, entry.getValue(), StandardOpenOption.CREATE_NEW);
            }
            if (!output.fingerprint.equals(read(staging).fingerprint)) throw new IOException("OUTPUT_VERIFY_FAILED");
            guard.check();
            if (!fingerprint.equals(read(path).fingerprint)) throw new IOException("SOURCE_SAVE_CHANGED: inspect again / 请重新检查");
            // 不使用 REPLACE_EXISTING，也不使用可替换目标的 ATOMIC_MOVE；同目录发布，不覆盖已有目标。
            Files.move(staging, target);
            published = true;
            return target;
        } finally {
            if (!published) {
                // 仅清理本次创建的暂存目录，不跟随链接，不触碰源或最终目标。
                try (var stream = Files.walk(staging)) {
                    for (Path file : stream.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
                }
            }
        }
    }
    private static String fingerprint(SortedMap<String, byte[]> files) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            files.forEach((name, data) -> {
                digest.update(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)); digest.update((byte)0);
                digest.update(MessageDigestHolder.hash(data));
            });
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static final class MessageDigestHolder {
        static byte[] hash(byte[] data) {
            try { return MessageDigest.getInstance("SHA-256").digest(data); }
            catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        }
    }
}
