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
    @Inject(method = "refreshMods", at = @At("HEAD"), remap = false)
    private static void acbric$refreshBundleState(CallbackInfo ci) {
        net.fabricacs.api.impl.DisabledBundledMods.invalidate();
    }

    @Inject(method = "refreshMods", at = @At("RETURN"), remap = false)
    private static void acbric$appendFabricMods(CallbackInfo ci) {
        for (Mod mod : Mod.mods) {
            if (mod.preemptedBy == null && net.fabricacs.api.impl.DisabledBundledMods.blocked(mod)) mod.preemptedBy = mod;
        }
        FabricModListBridge.appendFabricMods();
    }
    @Inject(method = {"isCurrentlyEnabled", "isAvailable"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void acbric$blockDisabledBundle(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (net.fabricacs.api.impl.DisabledBundledMods.blocked((Mod) (Object) this)) cir.setReturnValue(false);
    }

}
