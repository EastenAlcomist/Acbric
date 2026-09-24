/*
 * ModMixin.java — 在原版 MOD 列表刷新后补入 Fabric 行，保留原版扫描流程。
 */
package net.fabricacs.api.mixin;

import com.zarkonnen.airships.Mod;
import net.fabricacs.api.impl.FabricModListBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Mod.class, remap = false)
public abstract class ModMixin {
    @Inject(method = "refreshMods", at = @At("RETURN"), remap = false)
    private static void acbric$appendFabricMods(CallbackInfo ci) {
        FabricModListBridge.appendFabricMods();
    }
}
