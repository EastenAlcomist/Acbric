/*
 * NativeEmbeddedAcmodScanner.java — 读取原版 MOD 根目录 AcbricMod 中的唯一 ACMOD 并预检 Fabric 元数据。
 */
package net.fabricacs.api.impl;

import net.fabricacs.management.ModSelection;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class NativeEmbeddedAcmodScanner {
    public static final String CARRIER_DIRECTORY = "AcbricMod";
    private static final String METADATA = "fabric.mod.json";
    private static final long MAX_METADATA_BYTES = 1024L * 1024L;

    private NativeEmbeddedAcmodScanner() {
    }

    /** 本地与 Steam 原版 MOD 共用约定：AcbricMod 下恰好一个小写 .jar。 */
    public static Optional<CarrierSource> find(Path nativeModDirectory) throws IOException {
        Path root = nativeModDirectory.toAbsolutePath().normalize();
        Path carrierDirectory = root.resolve(CARRIER_DIRECTORY);
        if (!Files.exists(carrierDirectory, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        if (!Files.isDirectory(carrierDirectory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("AcbricMod is not a regular directory: " + carrierDirectory);
        }

        List<Path> jars;
        try (var entries = Files.list(carrierDirectory)) {
            jars = entries
                    .filter(path -> !path.getFileName().toString().startsWith("."))
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
        if (jars.isEmpty()) {
            return Optional.empty();
        }
        if (jars.size() != 1) {
            throw new IOException("AcbricMod must contain exactly one .jar: " + carrierDirectory);
        }

        Path jar = jars.getFirst().toAbsolutePath().normalize();
        return Optional.of(new CarrierSource(jar, readFabricId(jar)));
    }

    /** 只做网络清单之外的结构预检；正式安装仍交给 Fabric 元数据解析器。 */
    public static String readFabricId(Path jar) throws IOException {
        if (!Files.isRegularFile(jar, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("ACMOD is not a regular file: " + jar);
        }

        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry metadata = null;
            int metadataCount = 0;
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (METADATA.equals(entry.getName())) {
                    metadata = entry;
                    metadataCount++;
                }
            }
            if (metadataCount != 1 || metadata == null) {
                throw new IOException("ACMOD must contain exactly one fabric.mod.json: " + jar);
            }
            if (metadata.getSize() < 0 || metadata.getSize() > MAX_METADATA_BYTES) {
                throw new IOException("Invalid fabric.mod.json size: " + jar);
            }

            String json;
            try (InputStream input = zip.getInputStream(metadata)) {
                json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            String id;
            try {
                id = new JSONObject(json).optString("id", "");
            } catch (RuntimeException error) {
                throw new IOException("Invalid fabric.mod.json: " + jar, error);
            }
            ModSelection.validateId(id);
            return id;
        }
    }

    public record CarrierSource(Path jar, String modId) {
    }
}
