/*
 * SteamLaunch.java — 生成原版 Packr 可读取的 Acbric 配置，并通过 Steam App 342560 启动。
 * 只接受真实 Steam 库中的游戏；Steam 客户端负责账号许可，游戏进程再用环境上下文验证来源。
 */
package net.fabricacs.acbric;

import net.fabricacs.management.ModSelection;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class SteamLaunch {
    static final String MODE_PROPERTY = "acbric.external.steam";
    static final String CONFIG_NAME = "steam-launch.json";
    static final String MAIN_CLASS = "net.fabricacs.acbric.ExternalLauncher";

    private SteamLaunch() {
    }

    record Config(Path javaHome, List<Path> classPath, List<String> vmArgs) {
        Config {
            classPath = List.copyOf(classPath);
            vmArgs = List.copyOf(vmArgs);
        }
    }

    /** 启动前复用正式路径校验，并短暂取得实例/MOD 锁以发现正在运行的另一个进程。 */
    static void launch(Path framework, Path game, Path instance) throws IOException {
        ExternalGameInstallation.runtime(System.getProperty("os.name"),
                Runtime.version().feature(), System.getProperty("os.arch"));
        Path bundle = framework.toAbsolutePath().normalize();
        ExternalGameInstallation.Plan plan = ExternalGameInstallation.inspect(game, instance);
        InstanceSetup.checkFrameworkPath(bundle, plan.instance());
        List<Path> loaders = ExternalLauncher.verifyBundle(bundle);
        Path core = ExternalLauncher.requireCore(bundle.resolve("core/acbric-api.jar").toString());
        Path javaMods = ExternalMods.directory(bundle, plan);
        PlayerEnvironment.Layout environment =
                PlayerEnvironment.read(plan.instance(), javaMods, plan.install());
        Path nativeMods = environment.modsDir();
        ExternalMods.validate(nativeMods, plan);
        try (var lease = ExternalPreflight.InstanceLease.open(plan);
             var shared = ExternalMods.open(nativeMods, plan)) {
            lease.prepareDirectories(plan.instance());
        }

        Path steam = SteamGameLocator.steamExecutable(plan.install())
                .orElseThrow(() -> new IOException(
                        "STEAM_GAME_NOT_REGISTERED / 游戏不在已识别的 Steam 库中: " + plan.install()));
        Path javaHome = javaHome();
        List<String> vmArgs = new ArrayList<>(List.of(
                "-Xmx4G",
                "-Dfile.encoding=UTF-8",
                "-Dstdout.encoding=UTF-8",
                "-Dstderr.encoding=UTF-8",
                "--add-opens=java.base/java.util=ALL-UNNAMED",
                "-Dsteam=true",
                "-Ddev=false",
                "-D" + MODE_PROPERTY + "=true",
                "-Dacbric.external.core=" + core,
                "-Dfabric.addMods=" + core));
        vmArgs.addAll(ExternalLauncher.pathArguments(
                plan.install(), plan.instance(), environment, javaMods));

        Path config = configFile(plan.instance());
        writeConfig(config, new Config(javaHome, loaders, vmArgs));
        List<String> command = command(steam, config, plan.install(), plan.instance());
        Path logDir = plan.instance().resolve("logs/acbric/launcher");
        ModSelection.rejectLinks(logDir);
        Files.createDirectories(logDir);
        Path log = Files.createTempFile(logDir, "steam-launch-", ".log");
        try {
            Files.writeString(log,
                    "command=" + String.join(" ", command) + "\n"
                            + "game=" + plan.install() + "\n"
                            + "instance=" + plan.instance() + "\n"
                            + "config=" + config + "\n",
                    StandardCharsets.UTF_8);
            Process process = new ProcessBuilder(command)
                    .directory(plan.instance().toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()))
                    .start();
            // Steam 已在运行时，代理进程很快结束；未启动时会继续运行 Steam 客户端，因此不无限等待。
            if (process.waitFor(8, TimeUnit.SECONDS) && process.exitValue() != 0) {
                throw new IOException("STEAM_LAUNCH_FAILED / Steam 启动请求失败，退出码 "
                        + process.exitValue() + ": " + log);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("STEAM_LAUNCH_INTERRUPTED / Steam 启动被中断", error);
        } catch (IOException error) {
            if (error.getMessage() != null && error.getMessage().contains("STEAM_")) throw error;
            throw new IOException("STEAM_LAUNCH_FAILED / 无法启动 Steam: " + error.getMessage(), error);
        }
    }

    static Path configFile(Path instance) {
        return instance.toAbsolutePath().normalize().resolve("config").resolve(CONFIG_NAME);
    }

    static List<String> command(Path steam, Path config, Path game, Path instance) {
        return List.of(
                steam.toAbsolutePath().normalize().toString(),
                "-applaunch",
                SteamGameLocator.APP_ID,
                "--cli",
                "--config=" + config.toAbsolutePath().normalize(),
                "--",
                "--game-dir",
                game.toAbsolutePath().normalize().toString(),
                "--instance-dir",
                instance.toAbsolutePath().normalize().toString());
    }

    static String json(Config config) {
        List<String> classPath = config.classPath().stream()
                .map(path -> ExternalLaunchSettings.quote(path.toAbsolutePath().normalize().toString()))
                .toList();
        List<String> vmArgs = config.vmArgs().stream()
                .map(ExternalLaunchSettings::quote)
                .toList();
        return "{\n"
                + "  \"jrePath\": "
                + ExternalLaunchSettings.quote(config.javaHome().toAbsolutePath().normalize().toString()) + ",\n"
                + "  \"classPath\": [" + String.join(", ", classPath) + "],\n"
                + "  \"mainClass\": " + ExternalLaunchSettings.quote(MAIN_CLASS) + ",\n"
                + "  \"useZgcIfSupportedOs\": false,\n"
                + "  \"vmArgs\": [" + String.join(", ", vmArgs) + "]\n"
                + "}\n";
    }

    private static Path javaHome() throws IOException {
        Path home;
        try {
            home = Path.of(System.getProperty("java.home")).toRealPath();
        } catch (RuntimeException error) {
            throw new IOException("STEAM_RUNTIME_INVALID / 无法读取当前 Java 运行时", error);
        }
        Path jvm = home.resolve("bin/server/jvm.dll");
        if (!Files.isRegularFile(jvm)) {
            throw new IOException("STEAM_RUNTIME_INVALID / Java 运行时缺少 bin/server/jvm.dll: " + home);
        }
        return home;
    }

    private static void writeConfig(Path output, Config config) throws IOException {
        ModSelection.rejectLinks(output);
        Path parent = output.getParent();
        Files.createDirectories(parent);
        ModSelection.rejectLinks(parent);
        Path temporary = Files.createTempFile(parent, ".steam-launch-", ".tmp");
        try {
            Files.writeString(temporary, json(config), StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException unsupportedAtomicMove) {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
