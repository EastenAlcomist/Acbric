/* NestedModMetadata.java — 有界读取声明的内嵌依赖元数据，供启停预检查使用；不提取或执行其代码。 */
package net.fabricacs.api.impl;

import net.fabricmc.loader.impl.metadata.*;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.*;

final class NestedModMetadata {
    private static final int LIMIT = 32 * 1024 * 1024;
    private NestedModMetadata() {}
    static List<LoaderModMetadata> read(ZipFile zip, LoaderModMetadata root, Path config) throws IOException {
        List<LoaderModMetadata> result = new ArrayList<>(); int[] budget = {64 * 1024 * 1024};
        for (var jar : root.getJars()) {
            var entry = zip.getEntry(jar.getFile());
            if (entry == null) throw new IOException("Missing declared nested JAR: " + jar.getFile());
            try (var input = zip.getInputStream(entry)) { readArchive(bounded(input, LIMIT, budget), config, result, budget, 0); }
        }
        return List.copyOf(result);
    }
    private static void readArchive(byte[] bytes, Path config, List<LoaderModMetadata> result, int[] budget, int depth) throws IOException {
        if (depth > 8 || result.size() >= 128) throw new IOException("Nested MOD metadata limit exceeded");
        byte[] metadata = find(bytes, "fabric.mod.json", 1024 * 1024, budget);
        if (metadata == null) throw new IOException("Nested JAR has no Fabric metadata");
        try {
            LoaderModMetadata mod = ModMetadataParser.parseMetadata(new ByteArrayInputStream(metadata), "nested manager candidate", List.of(), new VersionOverrides(), new DependencyOverrides(config), false);
            result.add(mod);
            for (var jar : mod.getJars()) {
                byte[] child = find(bytes, jar.getFile(), LIMIT, budget);
                if (child == null) throw new IOException("Missing declared nested JAR: " + jar.getFile());
                readArchive(child, config, result, budget, depth + 1);
            }
        } catch (ParseMetadataException | RuntimeException ex) { throw new IOException("Invalid nested MOD metadata", ex); }
    }
    private static byte[] find(byte[] bytes, String name, int limit, int[] budget) throws IOException {
        byte[] found = null;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals(name)) {
                    if (found != null) throw new IOException("Duplicate nested entry: " + name);
                    found = bounded(zip, limit, budget);
                }
            }
        }
        return found;
    }
    private static byte[] bounded(InputStream input, int limit, int[] budget) throws IOException {
        int max = Math.min(limit, budget[0]);
        byte[] bytes = input.readNBytes(max + 1);
        if (bytes.length > max) throw new IOException("Nested MOD metadata size limit exceeded");
        budget[0] -= bytes.length; return bytes;
    }
}
