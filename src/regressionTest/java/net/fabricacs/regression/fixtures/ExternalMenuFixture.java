/* ExternalMenuFixture.java — 仅观测真实菜单 render 返回，不取消方法或替换 GPU/资源。 */
package net.fabricacs.regression.fixtures;

import com.zarkonnen.airships.MainMenu;
import net.fabricacs.regression.ExternalMenuProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MainMenu.class, remap = false)
public abstract class ExternalMenuFixture {
    @Inject(method = "render", at = @At("RETURN"))
    private void acbric$menuCheckpoint(CallbackInfo ci) { ExternalMenuProbe.rendered(); }
}
