package net.fabricacs.api.mixin;

import com.zarkonnen.airships.Main;
import net.fabricacs.api.impl.LifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Main.class, remap = false)
public abstract class MainMixin {
    @Inject(method = "main", at = @At("HEAD"), remap = false)
    private static void acbric$onGameStarting(String[] args, CallbackInfo ci) {
        LifecycleHooks.fireGameStarting(args);
    }
}
