/*
 * SteamLaunchIntegrationHarness.java — 真实 Steam 发行验收入口，只调用正式 SteamLaunch 组件。
 */
package net.fabricacs.acbric;

import java.nio.file.Path;

public final class SteamLaunchIntegrationHarness {
    private SteamLaunchIntegrationHarness() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("Expected framework, game and instance paths");
        }
        SteamLaunch.launch(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]));
        System.out.println("STEAM_LAUNCH_REQUESTED");
    }
}
