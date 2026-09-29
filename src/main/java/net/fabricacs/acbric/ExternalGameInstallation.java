/* ExternalGameInstallation.java — 只读解析外部安装及确定性类路径；预检查不定义游戏类、不运行游戏。 */
package net.fabricacs.acbric;

import net.fabricmc.loader.impl.lib.gson.JsonReader;
import net.fabricmc.loader.impl.lib.gson.JsonToken;
import net.fabricacs.management.ModSelection;
import org.objectweb.asm.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

final class ExternalGameInstallation {
    static final String MAIN = "com.zarkonnen.airships.Main";
    static final String INSTALL_PROPERTY = "acbric.external.install";
    static final String INSTANCE_PROPERTY = "acbric.external.instance";
    // 仅供隔离回归测试；公开启动器不暴露探针参数。
    static final String PROBE_PROPERTY = "acbric.internal.externalProbeMain";
    record Plan(Path install, Path instance, Path nativeDir, List<Path> classPath,
                GameBuildIdentity identity, List<String> warnings) {}

    static IOException failure(String code, String en, String zh) {
        return new IOException(code + ": " + en + " / " + zh);
    }

    static void runtime(String os, int java, String arch) throws IOException {
        if (!os.startsWith("Windows") || !(arch.equals("amd64") || arch.equals("x86_64")))
            throw failure("PLATFORM_UNSUPPORTED", "Windows x64 is required", "首批仅支持 Windows x64");
        if (java < 21) throw failure("JAVA_TOO_OLD", "Java 21 or newer is required", "请使用独立的 Java 21 或更新运行时，游戏内置 Java 8 不适用");
    }

    /** 不创建实例；先确认真实路径互不包含，并拒绝实例中的链接/重解析路径。 */
    static Path validateInstance(Path install, Path instance) throws IOException {
        Path result = instance.toAbsolutePath().normalize();
        ModSelection.rejectLinks(result);
        if (result.startsWith(install) || install.startsWith(result))
            throw failure("OVERLAPPING_PATHS", "Installation and instance must be separate", "游戏安装与实例目录不能互相包含");
        if (Files.exists(result) && !Files.isDirectory(result))
            throw failure("INSTANCE_NOT_DIRECTORY", "Instance is not a directory: " + result, "实例路径不是目录");
        return result;
    }

    static Plan inspect(Path requestedInstall, Path requestedInstance) throws IOException {
        if (!Files.isDirectory(requestedInstall))
            throw failure("INSTALL_NOT_FOUND", "Select the game root containing Airships.json", "请选择包含 Airships.json 的游戏根目录");
        Path install = requestedInstall.toRealPath();
        Path instance = validateInstance(install, requestedInstance);
        Path config = inside(install, "Airships.json");
        if (!Files.isRegularFile(config)) throw failure("CONFIG_MISSING", "Airships.json is missing", "游戏根目录缺少 Airships.json");
        if (Files.size(config) > 1048576) throw failure("CONFIG_INVALID", "Airships.json exceeds 1 MiB", "启动配置超过大小限制");
        List<String> entries = new ArrayList<>();
        Set<String> fields = new HashSet<>();
        String main = null;
        try (JsonReader json = new JsonReader(Files.newBufferedReader(config, StandardCharsets.UTF_8))) {
            json.beginObject();
            while (json.hasNext()) {
                String field = json.nextName();
                if (!fields.add(field)) throw new IOException("Duplicate field: " + field);
                switch (field) {
                    case "mainClass" -> { if (json.peek() != JsonToken.STRING) throw new IOException("mainClass must be string"); main = json.nextString(); }
                    case "classPath" -> {
                        json.beginArray();
                        while (json.hasNext()) {
                            if (json.peek() != JsonToken.STRING) throw new IOException("classPath must contain strings");
                            entries.add(json.nextString());
                        }
                        json.endArray();
                    }
                    default -> json.skipValue(); // 原游戏的 jrePath/vmArgs 不执行，也不继承服务器等偏好。
                }
            }
            json.endObject();
            if (json.peek() != JsonToken.END_DOCUMENT) throw new IOException("Trailing JSON content");
        } catch (IOException | RuntimeException ex) {
            throw failure("CONFIG_INVALID", "Invalid Airships.json: " + ex.getMessage(), "启动配置 JSON 无效");
        }
        if (!MAIN.equals(main)) throw failure("MAIN_UNSUPPORTED", "Expected mainClass " + MAIN, "不支持此游戏入口类");
        if (entries.isEmpty()) throw failure("CLASSPATH_EMPTY", "classPath is empty", "启动配置没有游戏代码归档");
        LinkedHashSet<Path> classPath = new LinkedHashSet<>();
        for (String entry : entries) {
            Path path = inside(install, entry);
            if (!Files.isRegularFile(path)) throw failure("ARCHIVE_MISSING", "Missing game archive: " + path, "游戏代码归档缺失");
            if (!classPath.add(path)) throw failure("DUPLICATE_ARCHIVE", "Repeated classPath: " + entry, "启动配置重复引用同一归档");
            boolean runtimeArchive;
            try { runtimeArchive = AirshipsGameProvider.isRuntimeLibrary(path); }
            catch (IOException ex) { throw failure("ARCHIVE_INVALID", path + ": " + ex.getMessage(), "游戏代码归档损坏或无法读取"); }
            if (!runtimeArchive) throw failure("ARCHIVE_UNSUPPORTED", "Not a game archive: " + path, "游戏代码路径包含启动层库或不支持的文件");
        }
        for (String name : List.of("asplit-A.zip", "asplit-B.zip")) {
            if (!classPath.contains(inside(install, name))) throw failure("ARCHIVE_MISSING", "classPath must include " + name, "启动配置缺少必需的 A/B 归档");
        }
        Path libs = inside(install, "lib");
        if (!Files.isDirectory(libs)) throw failure("LIBS_MISSING", "Missing game lib directory", "缺少游戏 lib 依赖目录");
        try (var files = Files.list(libs)) {
            for (Path path : files.filter(Files::isRegularFile).sorted().toList()) {
                String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!name.endsWith(".jar") && !name.endsWith(".zip")) continue;
                Path safe = inside(install, install.relativize(path).toString());
                if (AirshipsGameProvider.isRuntimeLibrary(safe)) classPath.add(safe);
            }
        } catch (IOException ex) { throw failure("LIBRARY_INVALID", "Cannot inspect game libraries: " + ex.getMessage(), "游戏依赖归档无法读取"); }
        Map<String, Path> owners = new HashMap<>();
        boolean[] validMain = {false};
        for (Path path : classPath) {
            try (ZipFile zip = new ZipFile(path.toFile())) {
                for (var e : zip.stream().filter(e -> e.getName().endsWith(".class") && !e.getName().equals("module-info.class") && !e.getName().startsWith("META-INF/versions/")).toList()) {
                    Path old = owners.putIfAbsent(e.getName(), path);
                    if (old != null) throw failure("DUPLICATE_CLASS", e.getName() + " in " + old + " and " + path, "游戏类路径出现重复类，无法确定依赖版本");
                    if (e.getName().equals(MAIN.replace('.', '/') + ".class")) {
                        try (var input = zip.getInputStream(e)) {
                            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                                @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                                    if (name.equals("main") && desc.equals("([Ljava/lang/String;)V") && (access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) == (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) validMain[0] = true;
                                    return null;
                                }
                            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                        }
                    }
                }
            } catch (IOException | RuntimeException ex) { throw failure("ARCHIVE_INVALID", path + ": " + ex.getMessage(), "游戏归档损坏或存在类冲突"); }
        }
        if (!validMain[0]) throw failure("MAIN_MISSING", "Game main(String[]) not found", "所选代码中找不到有效的游戏主入口");
        for (String required : List.of("com/zarkonnen/airships/AGame.class", "com/zarkonnen/airships/WorldMap.class",
                "org/json/JSONObject.class", "com/zarkonnen/catengine/Fount.class", "org/newdawn/slick/AppGameContainer.class",
                "org/lwjgl/opengl/Display.class", "com/codedisaster/steamworks/SteamAPI.class", "sun/misc/FloatingDecimal2.class")) {
            if (!owners.containsKey(required)) throw failure("DEPENDENCY_MISSING", "Missing class " + required, "缺少必要游戏依赖，请检查 lib 目录");
        }
        GameBuildIdentity identity = GameBuildIdentity.inspect(List.copyOf(classPath));
        if (identity.rawVersion().equals("unknown") || identity.fingerprint().equals("unavailable") || identity.normalizedVersion().equals("0.0.0"))
            throw failure("IDENTITY_INVALID", String.join("; ", identity.warnings()), "无法可靠识别游戏版本或代码指纹");
        for (String dir : List.of("data/fontmetrics", "data/lang", "data/images", "data/GUISetting", "data/ModuleType", "default_ships", "default_buildings", "default_landships")) {
            Path resource = inside(install, dir);
            if (!Files.isDirectory(resource)) throw failure("RESOURCE_MISSING", "Missing resource directory " + dir, "游戏基本资源目录缺失");
            try (var contents = Files.list(resource)) {
                if (contents.findAny().isEmpty()) throw failure("RESOURCE_EMPTY", "Empty resource directory " + dir, "游戏基本资源目录为空");
            }
        }
        Path nativeDir = inside(install, "lib/native");
        for (String dll : List.of("lwjgl64.dll", "OpenAL64.dll", "jinput-dx8_64.dll", "jinput-raw_64.dll")) verifyNative(inside(install, "lib/native/" + dll));
        List<String> warnings = new ArrayList<>(identity.warnings());
        warnings.add("EXPERIMENTAL: external instances; Workshop and arbitrary MOD writes remain unverified / 外部实例为实验功能，Workshop 与任意 MOD 写入尚未完整验收");
        warnings.add((Set.of("1.2.14", "1.2.15.2", "1.2.15.3").contains(identity.rawVersion()) ? "KNOWN_VERSION_UNVERIFIED_CONTENT" : "UNKNOWN_VERSION")
                + ": " + identity.rawVersion() + "; version recognition is not a compatibility guarantee / 版本识别不等于兼容性验收");
        return new Plan(install, instance, nativeDir, List.copyOf(classPath), identity, List.copyOf(warnings));
    }

    static Path inside(Path root, String relative) throws IOException {
        Path part;
        try { part = Path.of(relative); } catch (InvalidPathException ex) { throw failure("PATH_INVALID", relative, "游戏路径格式无效"); }
        Path path = root.resolve(part).normalize();
        if (relative.isBlank() || part.isAbsolute() || !path.startsWith(root)) throw failure("PATH_OUTSIDE_INSTALL", relative, "游戏配置路径超出安装目录");
        if (Files.exists(path) && !path.toRealPath().startsWith(root)) throw failure("PATH_OUTSIDE_INSTALL", relative, "游戏资源链接指向安装目录外部");
        return Files.exists(path) ? path.toRealPath() : path;
    }

    static void verifyNative(Path path) throws IOException {
        if (!Files.isRegularFile(path)) throw failure("NATIVE_MISSING", "Missing " + path, "缺少 Windows x64 原生库");
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            if (file.length() < 64 || file.readUnsignedShort() != 0x4d5a) throw new IOException("Missing MZ");
            file.seek(0x3c); long pe = Integer.toUnsignedLong(Integer.reverseBytes(file.readInt()));
            if (pe > file.length() - 6) throw new IOException("Invalid PE offset");
            file.seek(pe);
            if (file.readInt() != 0x50450000 || Short.toUnsignedInt(Short.reverseBytes(file.readShort())) != 0x8664) throw new IOException("Not AMD64 PE");
        } catch (IOException ex) { throw failure("NATIVE_ARCHITECTURE", path + ": " + ex.getMessage(), "原生库不是有效的 Windows x64 DLL"); }
    }
}
