/*
 * GameIdentityRegression.java — 用合成字节码验证版本来源、归档顺序、未知回退与诊断写入隔离。
 */
package net.fabricacs.acbric;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class GameIdentityRegression {
    private static int checks;

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
        System.out.println("PASS " + label);
    }

    private static Path archive(Path path, String version, String marker) throws Exception {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/zarkonnen/airships/AGame", null, "java/lang/Object", null);
        if (version != null) writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "VERSION", "Ljava/lang/String;", null, version).visitEnd();
        // 若有人改用反射加载游戏类，夹具的静态初始化器立即失败。
        var method = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ATHROW);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();
        try (var zip = new ZipOutputStream(Files.newOutputStream(path))) {
            zip.putNextEntry(new ZipEntry("com/zarkonnen/airships/AGame.class"));
            zip.write(writer.toByteArray());
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("marker.txt"));
            zip.write(marker.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return path;
    }

    public static int run(Path root) throws Exception {
        checks = 0;
        Files.createDirectories(root);
        String previous = System.getProperty("acbric.internal.diagnostics.session");
        try {
            Path original = archive(root.resolve("original.zip"), "1.2.15.2", "original");
            var identity = GameBuildIdentity.inspect(List.of(original));
            check(identity.rawVersion().equals("1.2.15.2") && identity.normalizedVersion().equals("1.2.15.2")
                    && identity.warnings().isEmpty(), "read four-component version without initializing game class");
            Path moved = Files.copy(original, root.resolve("renamed.zip"));
            check(GameBuildIdentity.inspect(List.of(moved)).fingerprint().equals(identity.fingerprint()),
                    "game fingerprint does not depend on file name or location");
            Path changed = archive(root.resolve("changed.zip"), "1.2.15.2", "changed");
            check(!GameBuildIdentity.inspect(List.of(changed)).fingerprint().equals(identity.fingerprint()),
                    "same version with different archive bytes has a different fingerprint");
            Path newer = archive(root.resolve("newer.zip"), "1.2.16", "newer");
            var first = GameBuildIdentity.inspect(List.of(original, newer));
            var reversed = GameBuildIdentity.inspect(List.of(newer, original));
            check(first.rawVersion().equals("1.2.15.2") && reversed.rawVersion().equals("1.2.16")
                    && !first.fingerprint().equals(reversed.fingerprint()) && !first.warnings().isEmpty(),
                    "first classpath version wins and archive order affects fingerprint");
            Path absent = archive(root.resolve("absent.zip"), null, "no constant");
            var missing = GameBuildIdentity.inspect(List.of(absent, newer));
            check(missing.rawVersion().equals("unknown") && missing.normalizedVersion().equals("0.0.0")
                    && !missing.warnings().isEmpty(), "missing first VERSION never borrows shadowed version");
            Path text = archive(root.resolve("text.zip"), "development build", "text");
            var custom = GameBuildIdentity.inspect(List.of(text));
            check(custom.rawVersion().equals("development build") && custom.normalizedVersion().equals("0.0.0"),
                    "non-semantic raw version is retained with explicit normalized fallback");
            var empty = GameBuildIdentity.inspect(List.of());
            check(empty.fingerprint().equals("unavailable") && !empty.warnings().isEmpty(), "no input is not a known build");
            Path corrupt = Files.writeString(root.resolve("corrupt.zip"), "invalid archive");
            var partial = GameBuildIdentity.inspect(List.of(original, corrupt));
            check(partial.fingerprint().equals("unavailable") && partial.rawVersion().equals("1.2.15.2"),
                    "unreadable archive never produces a misleading complete fingerprint");
            AirshipsGameProvider provider = new AirshipsGameProvider();
            var field = AirshipsGameProvider.class.getDeclaredField("buildIdentity");
            field.setAccessible(true);
            field.set(provider, identity);
            check(provider.getBuiltinMods().iterator().next().metadata.getVersion().getFriendlyString().equals("1.2.15.2"),
                    "Fabric builtin game metadata uses detected version");
            LaunchDiagnostics diagnostics = new LaunchDiagnostics(root, identity);
            String session = System.getProperty("acbric.internal.diagnostics.session");
            diagnostics.phase("MAIN_FAILED", new IllegalStateException("expected failure"));
            Properties report = new Properties();
            try (var reader = Files.newBufferedReader(root.resolve("logs/acbric/" + session + "/launch.properties"))) {
                report.load(reader);
            }
            check(report.getProperty("session").equals(session) && report.getProperty("phase").equals("MAIN_FAILED")
                    && report.getProperty("game.fingerprint").equals(identity.fingerprint())
                    && report.getProperty("failure.type").equals("java.lang.IllegalStateException"),
                    "launch report preserves build identity and failure stage");
            Path blocked = Files.createDirectories(root.resolve("blocked"));
            Files.writeString(blocked.resolve("logs"), "not a directory");
            new LaunchDiagnostics(blocked, identity).phase("ENTERING_MAIN", null);
            check(Files.readString(blocked.resolve("logs")).equals("not a directory"),
                    "unwritable launch report neither aborts nor replaces blocking user file");
        } finally {
            if (previous == null) System.clearProperty("acbric.internal.diagnostics.session");
            else System.setProperty("acbric.internal.diagnostics.session", previous);
        }
        return checks;
    }
}
