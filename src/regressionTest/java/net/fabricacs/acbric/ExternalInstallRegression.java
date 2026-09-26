/* ExternalInstallRegression.java — 合成安装夹具验证错误诊断、路径边界、依赖顺序及实例锁；不使用真实游戏。 */
package net.fabricacs.acbric;

import org.objectweb.asm.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;
import java.util.zip.*;

public final class ExternalInstallRegression {
    private static int checks;
    private interface Action { void run() throws Exception; }
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++; System.out.println("PASS external install: " + label);
    }
    private static void reject(String code, Action action) throws Exception {
        try { action.run(); } catch (Exception ex) {
            check(ex.toString().contains(code), code + " diagnosed: " + ex.getMessage()); return;
        }
        throw new AssertionError("Expected rejection: " + code);
    }
    private static byte[] gameClass(String name, boolean version) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        if (version) writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "VERSION", "Ljava/lang/String;", null, "1.2.15.3").visitEnd();
        if (name.endsWith("/Main")) {
            var main = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
            main.visitCode(); main.visitInsn(Opcodes.RETURN); main.visitMaxs(0, 1); main.visitEnd();
        }
        // 误加载游戏类就失败；预检查必须只读字节码。
        var init = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        init.visitCode(); init.visitInsn(Opcodes.ACONST_NULL); init.visitInsn(Opcodes.ATHROW); init.visitMaxs(1, 0); init.visitEnd();
        writer.visitEnd(); return writer.toByteArray();
    }
    private static void archive(Path path, String... names) throws Exception {
        try (var zip = new ZipOutputStream(Files.newOutputStream(path))) {
            for (String name : names) {
                zip.putNextEntry(new ZipEntry(name + ".class")); zip.write(gameClass(name, name.endsWith("/AGame"))); zip.closeEntry();
            }
        }
    }
    private static Path installation(Path root) throws Exception {
        Files.createDirectories(root.resolve("lib/native"));
        Files.writeString(root.resolve("Airships.json"), "{\"mainClass\":\"com.zarkonnen.airships.Main\",\"classPath\":[\"asplit-A.zip\",\"asplit-B.zip\"],\"other\":{\"array\":[1,true,null,\"escaped \\\" text\"]}}");
        archive(root.resolve("asplit-A.zip"), "com/zarkonnen/airships/Main", "com/zarkonnen/airships/AGame");
        archive(root.resolve("asplit-B.zip"), "com/zarkonnen/airships/WorldMap", "org/json/JSONObject");
        archive(root.resolve("lib/runtime.jar"), "com/zarkonnen/catengine/Fount", "org/newdawn/slick/AppGameContainer", "org/lwjgl/opengl/Display", "sun/misc/FloatingDecimal2");
        archive(root.resolve("lib/steamworks4j-1.9.0.jar"), "com/codedisaster/steamworks/SteamAPI");
        archive(root.resolve("lib/steamworks4j-1.3.0.jar"), "com/codedisaster/steamworks/SteamAPI");
        archive(root.resolve("lib/renamed-loader.jar"), "net/fabricmc/loader/Example");
        for (String name : List.of("data/fontmetrics", "data/lang", "data/images", "data/GUISetting", "data/ModuleType", "default_ships", "default_buildings", "default_landships")) {
            Files.createDirectories(root.resolve(name)); Files.writeString(root.resolve(name + "/fixture.txt"), "fixture");
        }
        for (String name : List.of("lwjgl64.dll", "OpenAL64.dll", "jinput-dx8_64.dll", "jinput-raw_64.dll")) {
            byte[] bytes = new byte[128]; bytes[0] = 0x4d; bytes[1] = 0x5a; bytes[0x3c] = 64;
            bytes[64] = 0x50; bytes[65] = 0x45; bytes[68] = 0x64; bytes[69] = (byte) 0x86;
            Files.write(root.resolve("lib/native/" + name), bytes);
        }
        return root;
    }

    public static int run(Path root) throws Exception {
        checks = 0; Files.createDirectories(root);
        Path install = installation(root.resolve("游戏 安装"));
        Path instance = root.resolve("独立 实例");
        var plan = ExternalGameInstallation.inspect(install, instance);
        check(plan.classPath().size() == 4 && plan.classPath().getFirst().equals(install.resolve("asplit-A.zip")), "ordered root A/B then isolated libraries, ignores old steamworks and renamed loader");
        check(plan.identity().rawVersion().equals("1.2.15.3") && !Files.exists(instance), "inspect reads version without initialization or instance writes");
        check(plan.warnings().stream().anyMatch(x -> x.contains("UNVERIFIED")), "recognized version is not reported as compatibility proven");
        ExternalGameInstallation.runtime("Windows 11", 21, "amd64"); check(true, "Windows x64 Java 21 accepted");
        reject("JAVA_TOO_OLD", () -> ExternalGameInstallation.runtime("Windows 11", 8, "amd64"));
        reject("PLATFORM_UNSUPPORTED", () -> ExternalGameInstallation.runtime("Windows 11", 21, "x86"));
        reject("PLATFORM_UNSUPPORTED", () -> ExternalGameInstallation.runtime("Linux", 21, "amd64"));
        reject("INSTALL_NOT_FOUND", () -> ExternalGameInstallation.inspect(root.resolve("missing"), instance));
        reject("OVERLAPPING_PATHS", () -> ExternalGameInstallation.inspect(install, install.resolve("instance")));
        reject("OVERLAPPING_PATHS", () -> ExternalGameInstallation.inspect(install, root));
        Path blocker = Files.writeString(root.resolve("blocker"), "preserve");
        reject("INSTANCE_NOT_DIRECTORY", () -> ExternalGameInstallation.inspect(install, blocker));
        String original = Files.readString(install.resolve("Airships.json"));
        for (String invalid : List.of("{} trailing", "{\"mainClass\": 42}", original.replace("\"classPath\":", "\"classPath\":[],\"classPath\":"))) {
            Files.writeString(install.resolve("Airships.json"), invalid);
            reject("CONFIG_INVALID", () -> ExternalGameInstallation.inspect(install, instance));
        }
        Files.writeString(install.resolve("Airships.json"), original.replace("asplit-A.zip", "../outside.zip"));
        reject("PATH_OUTSIDE_INSTALL", () -> ExternalGameInstallation.inspect(install, instance));
        Files.writeString(install.resolve("Airships.json"), original.replace("asplit-A.zip", "missing.zip"));
        reject("ARCHIVE_MISSING", () -> ExternalGameInstallation.inspect(install, instance));
        Files.writeString(install.resolve("Airships.json"), original.replace("asplit-B.zip", "asplit-A.zip"));
        reject("DUPLICATE_ARCHIVE", () -> ExternalGameInstallation.inspect(install, instance));
        Files.writeString(install.resolve("Airships.json"), original.replace("airships.Main", "airships.Other"));
        reject("MAIN_UNSUPPORTED", () -> ExternalGameInstallation.inspect(install, instance));
        Files.writeString(install.resolve("Airships.json"), original);
        Path gameArchive = install.resolve("asplit-A.zip"); byte[] gameBytes = Files.readAllBytes(gameArchive);
        Files.writeString(gameArchive, "broken archive");
        reject("ARCHIVE_INVALID", () -> ExternalGameInstallation.inspect(install, instance));
        archive(gameArchive, "com/zarkonnen/airships/AGame");
        reject("MAIN_MISSING", () -> ExternalGameInstallation.inspect(install, instance)); Files.write(gameArchive, gameBytes);
        Path extra = install.resolve("lib/extra.jar"); archive(extra, "com/zarkonnen/airships/AGame");
        reject("DUPLICATE_CLASS", () -> ExternalGameInstallation.inspect(install, instance)); Files.delete(extra);
        Files.writeString(extra, "corrupt"); reject("LIBRARY_INVALID", () -> ExternalGameInstallation.inspect(install, instance)); Files.delete(extra);
        Path library = install.resolve("lib/runtime.jar"); byte[] originalLibrary = Files.readAllBytes(library); Files.delete(library);
        reject("DEPENDENCY_MISSING", () -> ExternalGameInstallation.inspect(install, instance)); Files.write(library, originalLibrary);
        Path resource = install.resolve("data/lang/fixture.txt"); Files.delete(resource);
        reject("RESOURCE_EMPTY", () -> ExternalGameInstallation.inspect(install, instance)); Files.writeString(resource, "fixture");
        Path nativeFile = install.resolve("lib/native/lwjgl64.dll"); byte[] nativeBytes = Files.readAllBytes(nativeFile); Files.delete(nativeFile);
        reject("NATIVE_MISSING", () -> ExternalGameInstallation.inspect(install, instance));
        byte[] wrongMachine = nativeBytes.clone(); wrongMachine[68] = 0x4c; wrongMachine[69] = 1; Files.write(nativeFile, wrongMachine);
        reject("NATIVE_ARCHITECTURE", () -> ExternalGameInstallation.inspect(install, instance)); Files.write(nativeFile, nativeBytes);
        try (var lease = ExternalPreflight.InstanceLease.open(plan)) {
            reject("INSTANCE_BUSY", () -> ExternalPreflight.InstanceLease.open(plan));
            lease.prepareDirectories(instance);
            check(Files.isDirectory(instance.resolve("userdata")), "instance directories prepared under lease");
        }
        try (var ignored = ExternalPreflight.InstanceLease.open(plan)) { check(true, "instance can reopen after lease closes"); }
        Path configBlock = Files.writeString(root.resolve("bad-data"), "preserve");
        var blockedPlan = ExternalGameInstallation.inspect(install, root.resolve("blocked-instance"));
        try (var lease = ExternalPreflight.InstanceLease.open(blockedPlan)) {
            Files.copy(configBlock, blockedPlan.instance().resolve("userdata"));
            try { lease.prepareDirectories(blockedPlan.instance()); throw new AssertionError("Expected unavailable userdata"); }
            catch (IOException expected) { check(Files.readString(blockedPlan.instance().resolve("userdata")).equals("preserve"), "userdata failure preserves blocker and never falls back"); }
        }
        String oldInstall = System.getProperty(ExternalGameInstallation.INSTALL_PROPERTY);
        String oldInstance = System.getProperty(ExternalGameInstallation.INSTANCE_PROPERTY);
        try {
            System.setProperty(ExternalGameInstallation.INSTALL_PROPERTY, install.toString());
            System.setProperty(ExternalGameInstallation.INSTANCE_PROPERTY, instance.toString());
            reject("EXTERNAL_NOT_READY", () -> new AirshipsGameProvider().locateGame(null, new String[0]));
            System.clearProperty(ExternalGameInstallation.INSTANCE_PROPERTY);
            reject("EXTERNAL_PATHS_REQUIRED", () -> new AirshipsGameProvider().locateGame(null, new String[0]));
        } finally {
            if (oldInstall == null) System.clearProperty(ExternalGameInstallation.INSTALL_PROPERTY); else System.setProperty(ExternalGameInstallation.INSTALL_PROPERTY, oldInstall);
            if (oldInstance == null) System.clearProperty(ExternalGameInstallation.INSTANCE_PROPERTY); else System.setProperty(ExternalGameInstallation.INSTANCE_PROPERTY, oldInstance);
        }
        return checks;
    }
}
