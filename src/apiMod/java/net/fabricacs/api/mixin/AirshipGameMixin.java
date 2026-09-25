/*
 * AirshipGameMixin.java — 在客户端构造完成及 input 方法前后发送生命周期/客户端事件。
 */
package net.fabricacs.api.mixin;

import com.zarkonnen.airships.AirshipGame;
import com.zarkonnen.catengine.Input;
import net.fabricacs.api.impl.LifecycleHooks;
import net.fabricacs.api.impl.CampaignLifecycleHooks;
import net.fabricacs.api.impl.CampaignSession;
import net.fabricacs.api.impl.CampaignSessionAccess;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AirshipGame.class, remap = false)
public abstract class AirshipGameMixin implements CampaignSessionAccess {
    @Unique private final CampaignSession acbric$session = new CampaignSession();

    @Override
    public CampaignSession acbric$campaignSession() { return acbric$session; }

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void acbric$onClientCreated(CallbackInfo ci) {
        LifecycleHooks.fireClientCreated(this);
    }

    @Inject(method = "input", at = @At("HEAD"), remap = false)
    private void acbric$onClientTickStart(Input input, CallbackInfo ci) {
        CampaignLifecycleHooks.observe((AirshipGame) (Object) this);
        LifecycleHooks.fireClientTickStart(this);
    }

    @Inject(method = "input", at = @At("RETURN"), remap = false)
    private void acbric$onClientTickEnd(Input input, CallbackInfo ci) {
        CampaignLifecycleHooks.observe((AirshipGame) (Object) this);
        LifecycleHooks.fireClientTickEnd(this);
    }

    @Inject(method = "startExit()V", at = @At("RETURN"), remap = false)
    private void acbric$onExit(CallbackInfo ci) {
        CampaignLifecycleHooks.exit((AirshipGame) (Object) this);
    }
}
