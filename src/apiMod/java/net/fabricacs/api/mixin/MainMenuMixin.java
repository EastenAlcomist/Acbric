package net.fabricacs.api.mixin;

import com.zarkonnen.airships.AirshipGame;
import com.zarkonnen.airships.MainMenu;
import net.fabricacs.api.impl.LifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MainMenu.class, remap = false)
public abstract class MainMenuMixin {
    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void acbric$onMainMenuCreated(AirshipGame game, MainMenu.Submenu submenu, CallbackInfo ci) {
        LifecycleHooks.fireMainMenuCreated(this);
    }
}
