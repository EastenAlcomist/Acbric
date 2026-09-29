/* ExternalLauncherRegression.java — 正式启动参数与发行清单失败保护；不运行游戏或子进程。 */
package net.fabricacs.acbric;

import java.nio.file.*;
import java.util.*;

public final class ExternalLauncherRegression {
    private static int checks;
    private interface Action { void run() throws Exception; }
    private static void check(boolean result, String label) {
        if (!result) throw new AssertionError(label);
        checks++; System.out.println("PASS launcher: " + label);
    }
    private static void reject(String code, Action action) throws Exception {
        try { action.run(); } catch (Exception ex) { check(ex.toString().contains(code), code); return; }
        throw new AssertionError("Expected " + code);
    }
    public static int run(Path root) throws Exception {
        checks = 0;
        check(ExternalLauncher.options(new String[]{"--game-dir", "中文 游戏", "--instance-dir", "实例"}).get("--game-dir").equals("中文 游戏"), "paths preserved");
        reject("USAGE", () -> ExternalLauncher.options(new String[]{"--game-dir", "x"}));
        reject("USAGE", () -> ExternalLauncher.options(new String[]{"--game-dir", "x", "--game-dir", "y"}));
        reject("USAGE", () -> ExternalLauncher.options(new String[]{"--jvm-args", "x", "--instance-dir", "y"}));
        reject("CORE_REQUIRED", () -> ExternalLauncher.requireCore(null));
        reject("CORE_MISSING", () -> ExternalLauncher.requireCore(root.resolve("missing.jar").toString()));
        Files.createDirectories(root.resolve("loader-libs")); Files.createDirectories(root.resolve("core"));
        Path loader = Files.writeString(root.resolve("loader-libs/framework.jar"), "fixture loader");
        Path core = Files.writeString(root.resolve("core/acbric-api.jar"), "fixture api");
        String valid = "schema=1\napi.version=" + ExternalLauncher.CORE_VERSION + "\nsha256.loader-libs/framework.jar=" + ExternalLauncher.sha256(loader)
                + "\nsha256.core/acbric-api.jar=" + ExternalLauncher.sha256(core) + "\n";
        Files.writeString(root.resolve("bundle.properties"), valid);
        check(ExternalLauncher.verifyBundle(root).equals(List.of(loader)), "explicit loader list after hash verification");
        Files.writeString(core, "modified");
        reject("BUNDLE_CHANGED", () -> ExternalLauncher.verifyBundle(root));
        Files.writeString(core, "fixture api");
        Files.writeString(root.resolve("bundle.properties"), valid + "sha256.loader-libs/../../outside.jar=x\n");
        reject("BUNDLE_INVALID", () -> ExternalLauncher.verifyBundle(root));
        Files.writeString(root.resolve("bundle.properties"), valid.replace("schema=1", "schema=9"));
        reject("BUNDLE_INVALID", () -> ExternalLauncher.verifyBundle(root));
        Files.writeString(root.resolve("bundle.properties"), valid.replaceAll("sha256.core[^\n]+\n", ""));
        reject("BUNDLE_INVALID", () -> ExternalLauncher.verifyBundle(root));
        return checks;
    }
}
