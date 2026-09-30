/* PlayerEnvironment.java — 数据环境：默认直接使用原版存档与模组（不复制），可选隔离成实例自己的副本。
 *
 * 共享模式下游戏的原生用户数据与原生 MOD 都指向原版目录，磁盘上只有一份；隔离模式下指向
 * 实例 userdata 与框架 mods，由玩家数据镜像负责搬运。模式按实例保存，未写配置时为共享。
 */
package net.fabricacs.acbric;

import net.fabricacs.management.ModSelection;
import net.fabricmc.loader.impl.lib.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

final class PlayerEnvironment {
    /** 内部回归覆盖原版数据目录用；普通运行只按 %APPDATA% 或游戏安装的启动设置识别。 */
    static final String VANILLA_PROPERTY = "acbric.internal.vanillaData";
    /** 模式配置文件名；与 instance.json 同级，由启动器管理。 */
    static final String CONFIG = "environment.json";
    static final String SHARED = "shared", ISOLATED = "isolated";
    private static final String USERDATA = "userdata";
    private static final String MODS = "mods";

    /** 当前数据环境：模式、游戏使用的用户数据目录、原生 MOD 根，以及识别到的原版数据目录。 */
    record Layout(String mode, Path dataDir, Path modsDir, Path vanilla) {
        boolean isolated() { return ISOLATED.equals(mode); }
        boolean shared() { return SHARED.equals(mode); }
    }

    private PlayerEnvironment() {}

    /** 识别原版数据目录：游戏安装的启动设置优先，其次 %APPDATA%\AirshipsGame；找不到不猜别处。 */
    static Path detect(Path install) throws IOException {
        Path configured = customDataDirectory(install);
        if (configured != null) return usable(configured);
        String override = System.getProperty(VANILLA_PROPERTY);
        Path root = (override == null || override.isBlank()
                ? roaming().resolve("AirshipsGame") : Path.of(override)).toAbsolutePath().normalize();
        return usable(root);
    }

    /** 原版游戏允许在 launch_settings.json 里自定义数据目录，识别时沿用同一份设置。 */
    private static Path customDataDirectory(Path install) throws IOException {
        if (install == null) return null;
        Path settings = install.resolve("launch_settings.json");
        if (!Files.isRegularFile(settings)) return null;
        String value;
        try { value = ExternalLaunchSettings.read(settings).get("customDataDirectoryLocation"); }
        catch (IOException invalid) { return null; }   // 损坏的启动设置交给正式启动流程报告，不在这里中断
        String text = unquote(value);
        if (text == null || text.isBlank()) return null;
        Path path;
        try { path = Path.of(text.trim()); } catch (RuntimeException invalid) { return null; }
        if (!path.isAbsolute() || !Files.isDirectory(path)) return null;
        return path.toAbsolutePath().normalize();
    }

    /** 启动设置读取结果保留 JSON 原样片段（字符串带引号并转义），这里还原成真实路径。 */
    private static String unquote(String value) {
        if (value == null) return null;
        String text = value.trim();
        if (text.length() < 2 || !text.startsWith("\"") || !text.endsWith("\"")) return null;
        try (JsonReader json = new JsonReader(new java.io.StringReader(text))) {
            if (json.peek() != JsonToken.STRING) return null;
            String result = json.nextString();
            return json.peek() == JsonToken.END_DOCUMENT ? result : null;
        } catch (IOException | RuntimeException ex) { return null; }
    }

    private static Path usable(Path root) throws IOException {
        if (!Files.isDirectory(root))
            throw ExternalGameInstallation.failure("VANILLA_DATA_MISSING",
                    "No vanilla data folder: " + root, "没有找到原版数据文件夹: " + root);
        if (!Files.isWritable(root))
            throw ExternalGameInstallation.failure("VANILLA_DATA_READONLY",
                    "Vanilla data folder is not writable: " + root, "原版数据文件夹不可写: " + root);
        return root;
    }

    private static Path roaming() {
        String appData = System.getenv("APPDATA");
        return appData == null || appData.isBlank()
                ? Path.of(System.getProperty("user.home"), "AppData", "Roaming") : Path.of(appData);
    }

    /** 共享布局：游戏直接读写原版目录，磁盘上不产生第二份存档或 MOD。 */
    static Layout shared(Path frameworkMods, Path install) throws IOException {
        return shared(detect(install));
    }

    private static Layout shared(Path vanilla) {
        return new Layout(SHARED, vanilla, vanilla.resolve(MODS), vanilla);
    }

    /** 隔离布局：存档在实例 userdata，原生 MOD 在框架 mods。 */
    static Layout isolated(Path instance, Path frameworkMods, Path install) {
        return new Layout(ISOLATED, instance.resolve(USERDATA), frameworkMods, vanillaHint(instance, install));
    }

    /** 读取实例当前环境：没有配置即共享；记录过的原版目录被移走时重新识别，识别不到则退回实例副本。 */
    static Layout read(Path instance, Path frameworkMods, Path install) throws IOException {
        Optional<String[]> saved = saved(instance);
        if (saved.isEmpty()) return defaultLayout(instance, frameworkMods, install);
        if (ISOLATED.equals(saved.get()[0])) return isolated(instance, frameworkMods, install);
        Path recorded = Path.of(saved.get()[1]);
        if (Files.isDirectory(recorded)) return shared(recorded.toAbsolutePath().normalize());
        return defaultLayout(instance, frameworkMods, install);
    }

    /** 启动路径上的兜底：能共享就共享，找不到可用的原版目录时退回实例副本，保证游戏始终能启动。
     *  玩家主动点「一键同步」时仍会得到 VANILLA_DATA_MISSING 的明确提示，这里只是不让启动失败。 */
    private static Layout defaultLayout(Path instance, Path frameworkMods, Path install) throws IOException {
        try { return shared(frameworkMods, install); }
        catch (IOException unavailable) { return isolated(instance, frameworkMods, install); }
    }

    /** 原版目录提示：优先使用配置里记录过的路径，其次重新识别；隔离模式与界面提示都用它。 */
    static Path vanilla(Path instance, Path install) throws IOException {
        Optional<String[]> saved = saved(instance);
        if (saved.isPresent()) {
            Path recorded = Path.of(saved.get()[1]);
            if (Files.isDirectory(recorded)) return recorded.toAbsolutePath().normalize();
        }
        return detect(install);
    }

    private static Path vanillaHint(Path instance, Path install) {
        try { return vanilla(instance, install); } catch (IOException missing) { return null; }
    }

    /** 保存环境：先写临时文件再原子替换，损坏时保留原文件并报错。 */
    static void write(Path instance, Layout layout) throws IOException {
        Path directory = instance.resolve(InstanceSetup.DIRECTORY);
        ModSelection.rejectLinks(directory);
        Files.createDirectories(directory);
        Path file = directory.resolve(CONFIG);
        ModSelection.rejectLinks(file);
        String recorded = layout.vanilla() == null ? "" : layout.vanilla().toString();
        String json = "{\n  \"schema\": \"1\",\n  \"mode\": " + ExternalLaunchSettings.quote(layout.mode())
                + ",\n  \"vanilla\": " + ExternalLaunchSettings.quote(recorded) + "\n}\n";
        Path temp = Files.createTempFile(directory, "environment-", ".tmp");
        try {
            Files.writeString(temp, json, StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally { Files.deleteIfExists(temp); }
    }

    /** 模式配置是可选的：缺失表示共享；存在时字段必须完全匹配，避免误读未知格式。 */
    private static Optional<String[]> saved(Path instance) throws IOException {
        Path file = instance.resolve(InstanceSetup.DIRECTORY).resolve(CONFIG);
        ModSelection.rejectLinks(file);
        if (!Files.exists(file)) return Optional.empty();
        if (!Files.isRegularFile(file) || Files.size(file) > 65536)
            throw ExternalGameInstallation.failure("ENVIRONMENT_CONFIG_INVALID",
                    "Invalid environment configuration: " + file, "环境配置不是文件或过大: " + file);
        Map<String, String> fields = new HashMap<>();
        try (JsonReader json = new JsonReader(Files.newBufferedReader(file, StandardCharsets.UTF_8))) {
            json.beginObject();
            while (json.hasNext()) {
                String key = json.nextName();
                if (json.peek() != JsonToken.STRING || fields.putIfAbsent(key, json.nextString()) != null) throw new IOException("Duplicate/invalid field");
            }
            json.endObject();
            if (json.peek() != JsonToken.END_DOCUMENT || !fields.keySet().equals(Set.of("schema", "mode", "vanilla"))
                    || !"1".equals(fields.get("schema")) || !Set.of(SHARED, ISOLATED).contains(fields.get("mode"))
                    || (SHARED.equals(fields.get("mode")) && fields.get("vanilla").isBlank()))
                throw new IOException("Unknown schema/fields");
        } catch (IOException | RuntimeException ex) {
            throw ExternalGameInstallation.failure("ENVIRONMENT_CONFIG_INVALID",
                    "Corrupt environment configuration; preserved / 环境配置损坏，未覆盖: " + file, "环境配置损坏，未覆盖: " + file + " (" + ex + ")");
        }
        return Optional.of(new String[]{fields.get("mode"), fields.get("vanilla")});
    }
}
