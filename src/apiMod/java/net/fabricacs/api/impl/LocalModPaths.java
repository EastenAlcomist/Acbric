/* LocalModPaths.java — 原生 MOD 扫描、安装、舰队编辑与配套资源共用的内部路径适配。 */
package net.fabricacs.api.impl;

import com.zarkonnen.airships.AGame;
import java.io.File;
import java.nio.file.Path;

public final class LocalModPaths {
    private LocalModPaths() {}
    public static Path nativeMods() {
        String root = System.getProperty("acbric.external.mods");
        return System.getProperty("acbric.external.install") != null && root != null
                ? Path.of(root) : AGame.getGameDirectory().toPath().resolve("mods").toAbsolutePath().normalize();
    }

    /** 只替换本地 mods，Steam 下载目录、联机缓存和存档仍属于实例 userdata。 */
    public static File file(File parent, String child) {
        if (System.getProperty("acbric.external.install") != null && System.getProperty("acbric.external.mods") != null
                && "mods".equals(child) && parent != null && parent.toPath().toAbsolutePath().normalize().equals(AGame.getGameDirectory().toPath().toAbsolutePath().normalize()))
            return nativeMods().toFile();
        return new File(parent, child);
    }
}
