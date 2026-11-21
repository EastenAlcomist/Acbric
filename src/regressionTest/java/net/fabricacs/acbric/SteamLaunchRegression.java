/* SteamLaunchRegression.java — 合成 Steam 库验证 App 映射、上下文门禁、Packr 配置和启动命令。 */
package net.fabricacs.acbric;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class SteamLaunchRegression {
    private static int checks;

    private SteamLaunchRegression() {
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
        System.out.println("PASS steam launch: " + label);
    }

    private static void reject(String code, Action action) throws Exception {
        try {
            action.run();
        } catch (Exception error) {
            check(error.toString().contains(code), code + " diagnosed");
            return;
        }
        throw new AssertionError("Expected rejection: " + code);
    }

    public static int run(Path root) throws Exception {
        checks = 0;
        Files.createDirectories(root);
        Path steamRoot = Files.createDirectories(root.resolve("Steam 客户端"));
        Path library = Files.createDirectories(root.resolve("SteamLibrary 中文"));
        Path steam = Files.writeString(steamRoot.resolve("steam.exe"), "fixture");
        Files.createDirectories(steamRoot.resolve("steamapps"));
        Files.writeString(steamRoot.resolve("steamapps/libraryfolders.vdf"),
                "\"libraryfolders\" { \"0\" { \"path\" \"" + escaped(library) + "\" } }");
        Path game = ExternalInstallRegression.installation(
                library.resolve("steamapps/common/Airships Conquer the Skies"));
        Path manifest = library.resolve("steamapps/appmanifest_342560.acf");
        String validManifest = "\"AppState\" { \"appid\" \"342560\" \"installdir\" "
                + "\"Airships Conquer the Skies\" }";
        Files.writeString(manifest, validManifest);

        check(SteamGameLocator.steamExecutable(List.of(steamRoot), game)
                        .orElseThrow().equals(steam),
                "registered App 342560 maps to the owning Steam client");
        check(SteamGameLocator.steamExecutable(List.of(root.resolve("missing")), game).isEmpty(),
                "unknown Steam root is rejected");
        Files.writeString(manifest, validManifest.replace("342560", "1"));
        check(SteamGameLocator.steamExecutable(List.of(steamRoot), game).isEmpty(),
                "wrong manifest App ID is rejected");
        Files.writeString(manifest, validManifest.replace("Airships Conquer the Skies", "Other game"));
        check(SteamGameLocator.steamExecutable(List.of(steamRoot), game).isEmpty(),
                "manifest install directory must match the selected game");
        Files.writeString(manifest, validManifest);

        Map<String, String> validContext = Map.of(
                "SteamAppId", "342560",
                "SteamGameId", "342560",
                "SteamClientLaunch", "1");
        ExternalGameInstallation.requireSteamLaunch(validContext);
        check(true, "Steam App 342560 context accepted");
        reject("STEAM_CONTEXT_INVALID", () -> ExternalGameInstallation.requireSteamLaunch(Map.of()));
        reject("STEAM_CONTEXT_INVALID", () -> ExternalGameInstallation.requireSteamLaunch(
                Map.of("SteamAppId", "342560", "SteamGameId", "342560", "SteamClientLaunch", "0")));
        reject("STEAM_CONTEXT_INVALID", () -> ExternalGameInstallation.requireSteamLaunch(
                Map.of("SteamAppId", "480", "SteamGameId", "480", "SteamClientLaunch", "1")));

        Path config = root.resolve("instance/config/steam-launch.json");
        List<String> command = SteamLaunch.command(steam, config, game, root.resolve("instance"));
        check(command.equals(List.of(
                        steam.toString(), "-applaunch", "342560", "--cli",
                        "--config=" + config.toAbsolutePath().normalize(),
                        "--", "--game-dir", game.toString(),
                        "--instance-dir", root.resolve("instance").toString())),
                "Steam command uses App 342560, attached config and in-process loader args");
        check(command.stream().noneMatch(argument -> argument.equals("--cwd")),
                "Steam command avoids the broken Packr --cwd option");
        check(SteamLaunch.configFile(root.resolve("instance")).equals(config),
                "Steam Packr config stays inside the instance");

        Path loaderA = root.resolve("bundle/loader-libs/Acbric launcher.jar");
        Path loaderB = root.resolve("bundle/loader-libs/fabric loader.jar");
        SteamLaunch.Config launchConfig = new SteamLaunch.Config(
                Path.of("C:/Program Files/Java/jdk-21"),
                List.of(loaderA, loaderB),
                List.of("-Dsteam=true", "-Dacbric.external.steam=true", "-Dpath=C:\\A B"));
        String json = SteamLaunch.json(launchConfig);
        check(json.contains("\"mainClass\": \"" + SteamLaunch.MAIN_CLASS + "\"")
                        && SteamLaunch.MAIN_CLASS.endsWith(".ExternalLauncher"),
                "Packr config selects the instance-aware Acbric launcher");
        check(json.contains("Acbric launcher.jar") && json.contains("fabric loader.jar")
                        && !json.contains("*.jar"),
                "Packr config enumerates JARs without wildcard");
        check(json.contains("\"-Dsteam=true\"") && json.contains("\"-Dacbric.external.steam=true\""),
                "Packr config enables Steam only for this mode");
        check(json.contains("C:\\\\A B"), "VM argument paths are JSON escaped");
        return checks;
    }

    private static String escaped(Path path) {
        return path.toString().replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @FunctionalInterface
    private interface Action {
        void run() throws Exception;
    }
}
