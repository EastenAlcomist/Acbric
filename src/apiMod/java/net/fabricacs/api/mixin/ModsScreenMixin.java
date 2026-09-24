package net.fabricacs.api.mixin;

import com.zarkonnen.airships.AirshipGame;
import com.zarkonnen.airships.Mod;
import com.zarkonnen.airships.ModsScreen;
import com.zarkonnen.catengine.Input;
import net.fabricacs.api.impl.FabricModInstallBridge;
import net.fabricacs.api.impl.FabricModListBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;

@Mixin(value = ModsScreen.class, remap = false)
public abstract class ModsScreenMixin {
    @Shadow
    public Mod selected;

    @Shadow
    private String installError;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void acbric$appendFabricMods(AirshipGame game, CallbackInfo ci) {
        FabricModListBridge.appendFabricMods();
        if (this.selected == null && !Mod.mods.isEmpty()) {
            this.selected = Mod.mods.get(0);
        }
    }

    @Inject(method = "doInstall", at = @At("HEAD"), cancellable = true, remap = false)
    private void acbric$installFabricModJar(File file, Input input, CallbackInfo ci) {
        FabricModInstallBridge.InstallResult result = FabricModInstallBridge.installIfFabricModJar(file);
        if (!result.isHandled()) {
            return;
        }

        this.installError = result.getMessage();
        ci.cancel();
    }
}
