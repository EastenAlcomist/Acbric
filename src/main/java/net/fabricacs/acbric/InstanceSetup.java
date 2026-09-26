/* InstanceSetup.java — 实例配置服务：只读预览、占用保护、原子发布启动入口；不搬迁旧数据或复制游戏。 */
package net.fabricacs.acbric;

import net.fabricacs.management.ModSelection;
import net.fabricmc.loader.impl.lib.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

final class InstanceSetup {
    static final String DIRECTORY = "acbric-launcher";
    static final String CONFIG = "instance.json";
    static final List<String> SCRIPTS = List.of("launch.ps1", "Start Acbric.cmd");
    record Saved(Path game, Path framework, String javaHome, String language) {}
    record Preview(ExternalGameInstallation.Plan plan, Path framework, String javaHome, String language, String previous, String previousEntry) {}
    private InstanceSetup() {}

    static void checkFrameworkPath(Path bundle, Path instance) throws IOException {
        if (instance.equals(bundle) || bundle.startsWith(instance)
                || (instance.startsWith(bundle) && !instance.startsWith(bundle.resolve("instances"))))
            throw new IOException("INSTANCE_OVERLAPS_FRAMEWORK / 实例不能包含框架或使用框架核心目录");
    }

    static Preview preview(Path bundle, Path game, Path instance, String language) throws IOException {
        if (!Set.of("zh", "en").contains(language)) throw new IOException("LANGUAGE_INVALID / 请选择中文或 English");
        Path framework = bundle.toRealPath();
        ExternalLauncher.verifyBundle(framework);
        ExternalLauncher.requireCore(framework.resolve("core/acbric-api.jar").toString());
        var plan = ExternalGameInstallation.inspect(game, instance);
        checkFrameworkPath(framework, plan.instance());
        ExternalMods.directory(framework, plan);
        String previous = existing(plan.instance());
        String javaHome = Path.of(System.getProperty("java.home")).toAbsolutePath().normalize().equals(framework.resolve("runtime"))
                ? "" : Path.of(System.getProperty("java.home")).toAbsolutePath().normalize().toString();
        return new Preview(plan, framework, javaHome, language, previous, FrameworkEntry.read(framework));
    }

    /** 只允许新目录或本向导管理的实例；保留失败初始化留下的空目录，方便重试。 */
    static String existing(Path instance) throws IOException {
        ModSelection.rejectLinks(instance);
        Path dir = instance.resolve(DIRECTORY);
        ModSelection.rejectLinks(dir);
        if (Files.exists(dir)) {
            read(instance);
            for (String name : SCRIPTS) {
                Path script = dir.resolve(name); ModSelection.rejectLinks(script);
                if (!Files.isRegularFile(script) || Files.size(script) != script(name).length || !Arrays.equals(Files.readAllBytes(script), script(name)))
                    throw new IOException("ENTRY_MODIFIED / 启动入口已被修改或缺失，请先保留并检查: " + script);
            }
            return Files.readString(dir.resolve(CONFIG), StandardCharsets.UTF_8);
        }
        if (Files.isDirectory(instance)) {
            try (var paths = Files.walk(instance)) {
                for (Path path : paths.toList()) {
                    ModSelection.rejectLinks(path);
                    if (Files.isRegularFile(path) && !path.equals(instance.resolve(".acbric-instance.lock")))
                        throw new IOException("INSTANCE_NOT_FRESH / 请选择空目录或由本向导创建的实例，不自动接管旧目录: " + path);
                }
            }
        }
        return null;
    }

    static Saved read(Path instance) throws IOException {
        Path config = instance.resolve(DIRECTORY).resolve(CONFIG);
        ModSelection.rejectLinks(config);
        if (!Files.isRegularFile(config) || Files.size(config) > 65536) throw new IOException("INSTANCE_CONFIG_INVALID / 实例配置缺失或过大: " + config);
        Map<String, String> fields = new HashMap<>();
        try (var json = new JsonReader(Files.newBufferedReader(config, StandardCharsets.UTF_8))) {
            json.beginObject();
            while (json.hasNext()) {
                String key = json.nextName();
                if (json.peek() != JsonToken.STRING || fields.putIfAbsent(key, json.nextString()) != null) throw new IOException("Duplicate/invalid field");
            }
            json.endObject();
            if (json.peek() != JsonToken.END_DOCUMENT || !fields.keySet().equals(Set.of("schema", "gameDir", "frameworkDir", "javaHome", "language"))
                    || !"1".equals(fields.get("schema")) || !Set.of("zh", "en").contains(fields.get("language"))) throw new IOException("Unknown schema/fields");
            Path game = Path.of(fields.get("gameDir")), framework = Path.of(fields.get("frameworkDir"));
            String java = fields.get("javaHome");
            if (!game.isAbsolute() || !framework.isAbsolute() || (!java.isEmpty() && !Path.of(java).isAbsolute())) throw new IOException("Paths must be absolute");
            return new Saved(game, framework, java, fields.get("language"));
        } catch (RuntimeException | IOException ex) { throw new IOException("INSTANCE_CONFIG_INVALID / 实例配置损坏，未覆盖: " + config, ex); }
    }

    static Path save(Preview preview) throws IOException {
        // 保存前重新检查安装/发行，防止预览后文件或实例配置被其他进程修改。
        Preview fresh = preview(preview.framework(), preview.plan().install(), preview.plan().instance(), preview.language());
        if (!Objects.equals(preview.previous(), fresh.previous()) || !Objects.equals(preview.previousEntry(), fresh.previousEntry()) || !preview.plan().identity().fingerprint().equals(fresh.plan().identity().fingerprint()))
            throw new IOException("SETUP_CONFLICT / 预览后配置或游戏已改变，请重新检查");
        Path instance = fresh.plan().instance(), dir = instance.resolve(DIRECTORY);
        try (var lease = ExternalPreflight.InstanceLease.open(fresh.plan());
             var shared = ExternalMods.open(ExternalMods.directory(fresh.framework(), fresh.plan()), fresh.plan())) {
            if (!Objects.equals(preview.previous(), existing(instance))) throw new IOException("SETUP_CONFLICT / 实例配置已改变，请重新检查");
            if (!Objects.equals(preview.previousEntry(), FrameworkEntry.read(fresh.framework()))) throw new IOException("SETUP_CONFLICT / Default instance changed / 默认实例已改变，请重新检查");
            lease.prepareDirectories(instance);
            String content = serialize(fresh);
            if (fresh.previous() == null) {
                Path temporary = Files.createTempDirectory(instance, ".acbric-setup-");
                try {
                    Files.writeString(temporary.resolve(CONFIG), content, StandardCharsets.UTF_8);
                    for (String name : SCRIPTS) Files.write(temporary.resolve(name), script(name));
                    // 同一卷内一次发布目录；失败只移除本次临时文件，不删除任何用户目录。
                    Files.move(temporary, dir, StandardCopyOption.ATOMIC_MOVE);
                } finally {
                    if (Files.exists(temporary)) {
                        for (String name : SCRIPTS) Files.deleteIfExists(temporary.resolve(name));
                        Files.deleteIfExists(temporary.resolve(CONFIG)); Files.delete(temporary);
                    }
                }
            } else {
                Path temp = Files.createTempFile(dir, "config-", ".tmp");
                try {
                    Files.writeString(temp, content, StandardCharsets.UTF_8);
                    Files.move(temp, dir.resolve(CONFIG), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } finally { Files.deleteIfExists(temp); }
            }
            FrameworkEntry.save(fresh.framework(), instance, fresh.previousEntry());
            return fresh.framework().resolve("Start Acbric.cmd");
        }
    }

    static String serialize(Preview preview) {
        return "{\n  \"schema\": \"1\",\n  \"gameDir\": " + ExternalLaunchSettings.quote(preview.plan().install().toString())
                + ",\n  \"frameworkDir\": " + ExternalLaunchSettings.quote(preview.framework().toString())
                + ",\n  \"javaHome\": " + ExternalLaunchSettings.quote(preview.javaHome())
                + ",\n  \"language\": " + ExternalLaunchSettings.quote(preview.language()) + "\n}\n";
    }

    static byte[] script(String name) throws IOException {
        String resource = name.endsWith("cmd") ? "/installer/start-instance.cmd" : "/installer/launch-instance.ps1";
        try (var input = InstanceSetup.class.getResourceAsStream(resource)) {
            if (input == null) throw new IOException("SETUP_RESOURCE_MISSING / 缺少安装器资源: " + resource);
            return input.readAllBytes();
        }
    }
}
