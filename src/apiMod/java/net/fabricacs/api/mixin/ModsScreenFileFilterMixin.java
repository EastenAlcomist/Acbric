package net.fabricacs.api.mixin;

import net.fabricacs.api.impl.FabricModInstallBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;

@Mixin(targets = "com.zarkonnen.airships.ModsScreen$FF", remap = false)
public abstract class ModsScreenFileFilterMixin {
    @Inject(method = "accept", at = @At("HEAD"), cancellable = true, remap = false)
    private void acbric$acceptFabricModJar(File file, CallbackInfoReturnable<Boolean> cir) {
        if (FabricModInstallBridge.isFabricModJar(file)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getDescription", at = @At("RETURN"), cancellable = true, remap = false)
    private void acbric$describeFabricModJars(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(cir.getReturnValue() + " & Fabric JARs");
    }
}
