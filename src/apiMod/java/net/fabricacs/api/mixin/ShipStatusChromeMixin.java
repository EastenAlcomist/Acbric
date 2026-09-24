package net.fabricacs.api.mixin;

import com.zarkonnen.airships.Airship;
import com.zarkonnen.airships.Combat;
import com.zarkonnen.airships.MyDraw;
import com.zarkonnen.airships.UniScreen;
import com.zarkonnen.catengine.util.Pt;
import com.zarkonnen.catengine.util.ScreenMode;
import net.fabricacs.api.impl.LifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = com.zarkonnen.airships.ShipStatusChrome.class, remap = false)
public abstract class ShipStatusChromeMixin {
    @Inject(method = "draw", at = @At("HEAD"), cancellable = true, remap = false)
    private void acbric$beforeShipStatusBarDraw(MyDraw draw, Pt mouse, Airship airship, Combat.Side side, int x, int y, int width, int height, ScreenMode screenMode, UniScreen screen, CallbackInfo ci) {
        if (LifecycleHooks.fireShipStatusBarBeforeDraw(this, draw, mouse, airship, side, x, y, width, height, screenMode, screen)) {
            ci.cancel();
        }
    }

    @Inject(method = "draw", at = @At("RETURN"), remap = false)
    private void acbric$afterShipStatusBarDraw(MyDraw draw, Pt mouse, Airship airship, Combat.Side side, int x, int y, int width, int height, ScreenMode screenMode, UniScreen screen, CallbackInfo ci) {
        LifecycleHooks.fireShipStatusBarAfterDraw(this, draw, mouse, airship, side, x, y, width, height, screenMode, screen);
    }
}
