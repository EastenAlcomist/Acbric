/*
 * DisabledBundledMods.java — 按本次启动快照阻止已停用 Java MOD 的受管理资源生效。
 * 只屏蔽具有有效归属记录的原生目录；不删除、搬移资源，也不改写原生启用偏好。
 */
package net.fabricacs.api.impl;

import com.zarkonnen.airships.AGame;
import com.zarkonnen.airships.Mod;
import net.fabricacs.management.ModSelection;
import java.io.IOException;
import java.nio.file.Path;

public final class DisabledBundledMods {
    private static final java.util.Map<Mod, Boolean> cache = new java.util.WeakHashMap<>();
    private DisabledBundledMods() {}
    public static void invalidate() { cache.clear(); }
    public static boolean blocked(Mod mod) {
        if (mod == null || mod.dir == null || FabricModListBridge.isSyntheticFabricMod(mod)) return false;
        Boolean known = cache.get(mod);
        if (known != null) return known;
        boolean result = inspect(mod);
        cache.put(mod, result);
        return result;
    }
    private static boolean inspect(Mod mod) {
        Path directory = mod.dir.toPath().toAbsolutePath().normalize();
        Path root = AGame.getGameDirectory().toPath().resolve("mods").toAbsolutePath().normalize();
        if (!root.equals(directory.getParent())) return false;
        try {
            String id = directory.getFileName().toString();
            if (!ModSelection.ids(System.getProperty(ModSelection.EFFECTIVE_PROPERTY)).contains(id)) return false;
            return BundledResourceStore.hasOwnership(directory, id);
        } catch (IOException ex) {
            // 归属损坏时不能把未知数据当成已安全停用；中止此加载路径并保留原文件。
            throw new IllegalStateException("Cannot verify disabled bundled MOD: " + directory, ex);
        }
    }
}
