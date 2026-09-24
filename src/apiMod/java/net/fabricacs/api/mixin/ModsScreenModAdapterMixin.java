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
        }
    }
}
