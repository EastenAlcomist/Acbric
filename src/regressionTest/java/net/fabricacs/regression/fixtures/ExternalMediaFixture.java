/* ExternalMediaFixture.java — 观察原生重载页和录像页绘制，驱动隔离验收，不替换渲染与编码。 */
package net.fabricacs.regression.fixtures;
import com.zarkonnen.airships.*;
import net.fabricacs.regression.ExternalMediaProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
public final class ExternalMediaFixture {
    @Mixin(value=AirshipGame.class,remap=false)
    public abstract static class Tick {
        @Inject(method="input",at=@At("RETURN"))
        private void acbric$tick(com.zarkonnen.catengine.Input in, CallbackInfo ci) {ExternalMediaProbe.inputComplete(in);}
    }
    @Mixin(value=ModsScreen.class,remap=false)
    public abstract static class Mods {
        @Inject(method="render",at=@At("RETURN"))
        private void acbric$mods(CallbackInfo ci) {ExternalMediaProbe.modsRendered((ModsScreen)(Object)this);}
    }
    @Mixin(value=UniScreen.class,remap=false)
    public abstract static class Replay {
        @Inject(method="render",at=@At("RETURN"))
        private void acbric$replay(CallbackInfo ci) {ExternalMediaProbe.replayRendered((UniScreen)(Object)this);}
    }
}
