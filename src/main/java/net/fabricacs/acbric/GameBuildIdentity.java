/*
 * GameBuildIdentity.java — 启动前读取游戏版本常量及代码归档指纹，不定义或初始化游戏类。
 * 指纹覆盖按实际类路径顺序发现的游戏代码归档，不代表资源、MOD 或全部依赖兼容。
 */
package net.fabricacs.acbric;

import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.VersionParsingException;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipFile;

record GameBuildIdentity(String rawVersion, String normalizedVersion, String versionSource,
                         String fingerprint, List<Archive> archives, List<String> warnings) {
    private static final String VERSION_CLASS = "com/zarkonnen/airships/AGame.class";

    record Archive(String name, String sha256) {}

    static GameBuildIdentity unknown() {
        return new GameBuildIdentity("unknown", "0.0.0", "unavailable", "unavailable", List.of(), List.of());
    }

    static GameBuildIdentity inspect(List<Path> classPath) {
        List<Archive> archives = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        String raw = "unknown", normalized = "0.0.0", source = "unavailable";
        boolean versionClassSeen = false, hashesComplete = true;
        for (Path path : classPath) {
            try (ZipFile zip = new ZipFile(path.toFile())) {
                var versionEntry = zip.getEntry(VERSION_CLASS);
                if (versionEntry != null && !versionClassSeen) {
                    // 首个定义决定运行时类来源，读取失败也不借用后续同名类的版本。
                    versionClassSeen = true;
                    source = path.getFileName() + "!/" + VERSION_CLASS + "#VERSION";
                    try (var input = zip.getInputStream(versionEntry)) {
                        String[] value = {null};
                        new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                            @Override
                            public FieldVisitor visitField(int access, String name, String descriptor,
                                                           String signature, Object constant) {
                                if (name.equals("VERSION") && descriptor.equals("Ljava/lang/String;")
                                        && (access & (Opcodes.ACC_STATIC | Opcodes.ACC_FINAL))
                                        == (Opcodes.ACC_STATIC | Opcodes.ACC_FINAL) && constant instanceof String text) {
                                    value[0] = text;
                                }
                                return null;
                            }
                        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                        if (value[0] != null && !value[0].isBlank()) {
                            raw = value[0];
                            try {
                                SemanticVersion parsed = SemanticVersion.parse(raw);
                                if (parsed.hasWildcard()) throw new VersionParsingException("Wildcard game version");
                                normalized = parsed.getFriendlyString();
                            } catch (VersionParsingException e) {
                                warnings.add("Unrecognized game version; Fabric version falls back to 0.0.0: " + raw);
                            }
                        } else {
                            warnings.add("No constant String VERSION in " + source);
                        }
                    } catch (IOException | RuntimeException e) {
                        warnings.add("Cannot read version from " + source + ": " + e);
                    }
                } else if (versionEntry != null) {
                    warnings.add("Shadowed AGame definition in " + path.getFileName() + "; first classpath definition wins");
                }
                if (zip.stream().anyMatch(entry -> entry.getName().startsWith("com/zarkonnen/airships/")
                        && entry.getName().endsWith(".class"))) {
                    MessageDigest digest = sha256();
                    try (var input = Files.newInputStream(path)) {
                        byte[] buffer = new byte[65536];
                        int count;
                        while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
                    }
                    archives.add(new Archive(path.getFileName().toString(), HexFormat.of().formatHex(digest.digest())));
                }
            } catch (IOException | RuntimeException e) {
                hashesComplete = false;
                warnings.add("Cannot inspect " + path.getFileName() + ": " + e);
            }
        }
        if (!versionClassSeen) warnings.add("AGame.class not found; Fabric version falls back to 0.0.0");
        String fingerprint = "unavailable";
        if (hashesComplete && !archives.isEmpty()) {
            MessageDigest digest = sha256();
            // 固定协议前缀与换行分隔；路径/文件名不参与，以便搬迁或改名后比较。
            digest.update("acbric-game-archives-v1\n".getBytes(StandardCharsets.UTF_8));
            for (Archive archive : archives) digest.update((archive.sha256() + "\n").getBytes(StandardCharsets.UTF_8));
            fingerprint = HexFormat.of().formatHex(digest.digest());
        }
        return new GameBuildIdentity(raw, normalized, source, fingerprint, List.copyOf(archives), List.copyOf(warnings));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Java runtime has no SHA-256", e);
        }
    }
}
