/*
 * ModsScreenModAdapterMixin.java — 为 Fabric 行安排管理按钮，并为原版行追加内嵌 ACMOD 操作。
 */
package net.fabricacs.api.mixin;

import com.zarkonnen.airships.Mod;
import com.zarkonnen.airships.ModsScreen;
import net.fabricacs.api.impl.FabricModListBridge;
import net.fabricacs.api.impl.NativeEmbeddedAcmods;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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

    @Inject(method = "name", at = @At("RETURN"), cancellable = true, remap = false)
    private void acbric$nameEmbeddedAcmod(Mod mod, CallbackInfoReturnable<String> cir) {
        String suffix = NativeEmbeddedAcmods.suffix(mod);
        if (!suffix.isEmpty()) cir.setReturnValue(cir.getReturnValue() + "[65c0f9]" + suffix);
    }

    @Inject(method = "status", at = @At("HEAD"), cancellable = true, remap = false)
    private void acbric$statusFabricMod(Mod mod, CallbackInfoReturnable<String> cir) {
        if (FabricModListBridge.isSyntheticFabricMod(mod)) {
            cir.setReturnValue(FabricModListBridge.rowStatus(mod));
        } else if (net.fabricacs.api.impl.DisabledBundledMods.blocked(mod)) {
            cir.setReturnValue(FabricModListBridge.chinese() ? "配套 Java MOD 已停用" : "Owning Java MOD is disabled");
        }
    }

    @Inject(method = "getHeight(Lcom/zarkonnen/airships/Mod;Lcom/zarkonnen/airships/MyDraw;I)I", at = @At("HEAD"), cancellable = true, remap = false)
    private void acbric$rowHeight(Mod mod, com.zarkonnen.airships.MyDraw draw, int width, CallbackInfoReturnable<Integer> cir) {
        if (FabricModListBridge.isSyntheticFabricMod(mod)) cir.setReturnValue(FabricModListBridge.managerRowHeight(mod, draw, width));
    }

    @Inject(
            method = "getHeight(Lcom/zarkonnen/airships/Mod;Lcom/zarkonnen/airships/MyDraw;I)I",
            at = @At("RETURN"),
            cancellable = true,
            remap = false)
    private void acbric$reserveEmbeddedAcmodHeight(
            Mod mod,
            com.zarkonnen.airships.MyDraw draw,
            int width,
            CallbackInfoReturnable<Integer> cir) {
        int extra = NativeEmbeddedAcmods.extraHeight(mod);
        if (extra > 0) cir.setReturnValue(cir.getReturnValue() + extra);
    }

    @Inject(method = "draw(Lcom/zarkonnen/airships/Mod;Lcom/zarkonnen/airships/MyDraw;III)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void acbric$drawManagerButton(Mod mod, com.zarkonnen.airships.MyDraw draw, int x, int y, int width,
                                          org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (FabricModListBridge.isSyntheticFabricMod(mod)) {
            FabricModListBridge.drawManagerRow(mod, draw, x, y, width, mod == this.this$0.selected, () -> this.this$0.selected = mod,
                    ((net.fabricacs.api.impl.ModManagerScreenAccess) this.this$0)::acbric$managerMessage);
            ci.cancel();
        }
    }

    @Inject(
            method = "draw(Lcom/zarkonnen/airships/Mod;Lcom/zarkonnen/airships/MyDraw;III)V",
            at = @At("RETURN"),
            remap = false)
    private void acbric$drawEmbeddedAcmod(
            Mod mod,
            com.zarkonnen.airships.MyDraw draw,
            int x,
            int y,
            int width,
            CallbackInfo ci) {
        NativeEmbeddedAcmods.drawAction(mod, draw, x, y, width,
                message -> ((net.fabricacs.api.impl.ModManagerScreenAccess) this.this$0)
                        .acbric$managerMessage(message));
    }
}
