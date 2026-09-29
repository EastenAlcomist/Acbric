/*
 * ModsScreenMixin.java — 在 MOD 界面追加 Fabric 列表，并把 Fabric 安装请求交给安装桥处理。
 */
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
public abstract class ModsScreenMixin implements net.fabricacs.api.impl.ModManagerScreenAccess {
    // 原版以“可加载资源 MOD 非空”决定是否绘制整表；Java 合成行不参与加载，但应独立可见。
    @org.spongepowered.asm.mixin.injection.Redirect(method = "render", at = @At(value = "INVOKE", target = "Lcom/zarkonnen/airships/Mod;getAvailableMods()Ljava/util/ArrayList;"), require = 1)
    private java.util.ArrayList<Mod> acbric$visibleMods() {
        return FabricModListBridge.modsForListVisibility();
    }

    @org.spongepowered.asm.mixin.injection.Redirect(method = "doInstall", at = @At(value = "NEW", target = "(Ljava/io/File;Ljava/lang/String;)Ljava/io/File;"))
    private java.io.File acbric$localMods(java.io.File parent, String child) {
        return net.fabricacs.api.impl.LocalModPaths.file(parent, child);
    }

    @Shadow private AirshipGame g;

    @Override public void acbric$managerMessage(String message) { g.showError(message); }
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
        if (result.isInstalled()) FabricModListBridge.appendFabricMods();
        ci.cancel();
    }
}
