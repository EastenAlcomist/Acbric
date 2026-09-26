/*
 * BundledVanillaModLoader.java — 在预启动阶段扫描顶层 MOD 归档，将内嵌原版资源交给归属存储器管理。
 */
package net.fabricacs.api.impl;

import com.zarkonnen.airships.AGame;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.IOException;
import java.nio.file.*;
import java.util.Locale;

/** 扫描顶层 JAR/ZIP 的 acbric_vanilla 目录，安装到原版用户数据的 mods/modId。 */
public final class BundledVanillaModLoader {

    private BundledVanillaModLoader() {}

    /**
     * 为已加载的 Fabric MOD 准备内嵌资源；在 preLaunch 或之后调用。
     */
    public static void extractAll() {
        // 原版只在 AGame 用户数据的 mods 下扫描 info.json，不能解包到 Fabric 的 game/mods。
        Path gameModsDir = AGame.getGameDirectory().toPath().resolve("mods").toAbsolutePath().normalize();
        try {
            BundledResourceStore.rejectLinks(gameModsDir);
            Files.createDirectories(gameModsDir);
            BundledResourceStore.rejectLinks(gameModsDir);
        } catch (IOException e) {
            DiagnosticHub.publish("acbric_api",net.fabricacs.api.diagnostics.DiagnosticMessage.Level.ERROR,"Cannot prepare bundled MOD directory / 无法准备配套资源目录",e);
            System.err.println("[Acbric] Failed to prepare mods dir: " + gameModsDir + ": " + e);
            return;
        }

        for (ModContainer mc : FabricLoader.getInstance().getAllMods()) {
            try {
                for (Path origin : mc.getOrigin().getPaths()) {
                    extractBundled(origin, gameModsDir, mc.getMetadata().getId());
                }
            } catch (UnsupportedOperationException ignored) {
                // 嵌套/内置 MOD 没有直接文件来源，本轮不处理其内嵌原版资源。
            }
        }
    }

    private static void extractBundled(Path jarPath, Path gameModsDir, String modId) {
        if (!Files.isRegularFile(jarPath)) return;
        String name = jarPath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".jar") && !name.endsWith(".zip")) return;
        try {
            BundledResourceStore.install(jarPath, gameModsDir, modId, false);
        } catch (IOException | InvalidPathException e) {
            DiagnosticHub.publish(modId,net.fabricacs.api.diagnostics.DiagnosticMessage.Level.ERROR,"Cannot install bundled resources / 无法安装配套资源: "+jarPath,e);
            System.err.println("[Acbric] Failed to install bundled resources from " + jarPath + ": " + e);
        }
    }
}
