/*
 * ModsScreenModAdapterMixin.java — 仅替换 Fabric 合成列表行的名称与状态，原版 MOD 仍由游戏原逻辑显示。
 */
package net.fabricacs.api.mixin;

import com.zarkonnen.airships.Mod;
import com.zarkonnen.airships.ModsScreen;
import net.fabricacs.api.impl.FabricModListBridge;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.zarkonnen.airships.ModsScreen$5", remap = false)
public abstract class ModsScreenModAdapterMixin {
    @Shadow
    @Final
    private ModsScreen this$0;

    @Inject(method = "name", at = @At("HEAD"), cancellable = true, remap = false)
    private void acbric$nameFabricMod(Mod mod, CallbackInfoReturnable<String> cir) {
        if (FabricModListBridge.isSyntheticFabricMod(mod)) {
            cir.setReturnValue(FabricModListBridge.rowName(mod, mod == this.this$0.selected));
        }
    }

    @Inject(method = "status", at = @At("HEAD"), cancellable = true, remap = false)
    private void acbric$statusFabricMod(Mod mod, CallbackInfoReturnable<String> cir) {
        if (FabricModListBridge.isSyntheticFabricMod(mod)) {
            cir.setReturnValue(FabricModListBridge.rowStatus(mod));
        } else if (net.fabricacs.api.impl.DisabledBundledMods.blocked(mod)) {
            cir.setReturnValue(FabricModListBridge.chinese() ? "配套 Java MOD 已停用" : "Owning Java MOD is disabled");
        }
    }
    @Inject(method = "draw(Lcom/zarkonnen/airships/Mod;Lcom/zarkonnen/airships/MyDraw;III)V",
            at = @At("RETURN"), remap = false)
    private void acbric$drawManagerButton(Mod mod, com.zarkonnen.airships.MyDraw draw, int x, int y, int width,
                                          org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (FabricModListBridge.isSyntheticFabricMod(mod)) {
            FabricModListBridge.drawManagerButton(mod, draw, x, y, width,
                    ((net.fabricacs.api.impl.ModManagerScreenAccess) this.this$0)::acbric$managerMessage);
        }
    }

}
