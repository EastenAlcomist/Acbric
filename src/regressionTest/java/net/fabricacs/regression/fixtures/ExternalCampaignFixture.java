/* ExternalCampaignFixture.java — 在设置和战役真实绘制之后驱动检查，不取消原生流程。 */
package net.fabricacs.regression.fixtures;

import com.zarkonnen.airships.*;
import net.fabricacs.regression.ExternalCampaignProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public final class ExternalCampaignFixture {
    @Mixin(value = GameSetupScreen.class, remap = false)
    public abstract static class Setup {
        @Inject(method = "render", at = @At("RETURN"))
        private void acbric$setup(CallbackInfo ci) { ExternalCampaignProbe.setupRendered((GameSetupScreen)(Object)this); }
    }
    @Mixin(value = StrategicScreen.class, remap = false)
    public abstract static class World {
        @Inject(method = "render", at = @At("RETURN"))
        private void acbric$world(CallbackInfo ci) { ExternalCampaignProbe.worldRendered((StrategicScreen)(Object)this); }
    }
}
