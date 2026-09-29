/* ExternalOpenFleetMissionMixin.java — 舰队编辑器沿用统一本地 MOD 目录，其他文件位置保持原生逻辑。 */
package net.fabricacs.api.mixin;

import java.io.File;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(targets = "com.zarkonnen.airships.FleetCreationScreen$OpenFleetMission", remap = false)
public abstract class ExternalOpenFleetMissionMixin {
    @Redirect(method = {"fileSelected"}, at = @At(value = "NEW", target = "(Ljava/io/File;Ljava/lang/String;)Ljava/io/File;"))
    private File acbric$localMods(File parent, String child) {
        return net.fabricacs.api.impl.LocalModPaths.file(parent, child);
    }
}
