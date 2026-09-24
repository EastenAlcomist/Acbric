/*
 * BundledVanillaModMigration.java — 旧原版资源目录的显式离线迁移入口；先备份，仅接管内容相同的文件。
 */
package net.fabricacs.api.impl;

import java.nio.file.Path;

/** 显式离线迁移工具，不初始化 Fabric 或游戏。 */
public final class BundledVanillaModMigration {
    private BundledVanillaModMigration() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Usage: BundledVanillaModMigration <mod.jar> <vanilla-mods-directory> <fabric-mod-id>");
        BundledResourceStore.install(Path.of(args[0]), Path.of(args[1]), args[2], true);
    }
}
