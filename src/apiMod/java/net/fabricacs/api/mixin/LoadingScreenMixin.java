package net.fabricacs.api.mixin;

import com.zarkonnen.airships.AirshipGame;
import com.zarkonnen.airships.LoadingScreen;
import net.fabricacs.api.impl.LifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LoadingScreen.class, remap = false)
public abstract class LoadingScreenMixin {
    @Inject(method = "<init>(Lcom/zarkonnen/airships/AirshipGame;)V", at = @At("RETURN"), remap = false)
    private void acbric$onLoadingScreenCreated(AirshipGame game, CallbackInfo ci) {
        LifecycleHooks.fireLoadingScreenCreated(this);
    }

    @Inject(method = "<init>(Lcom/zarkonnen/airships/AirshipGame;Ljava/lang/String;)V", at = @At("RETURN"), remap = false)
    private void acbric$onLoadingScreenCreated(AirshipGame game, String modId, CallbackInfo ci) {
        LifecycleHooks.fireLoadingScreenCreated(this);
    }
}
