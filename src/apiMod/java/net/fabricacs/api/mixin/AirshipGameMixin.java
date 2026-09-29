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
    @org.spongepowered.asm.mixin.Shadow private com.zarkonnen.airships.MyDraw.State drawState;
    @Unique private final CampaignSession acbric$session = new CampaignSession();

    @Override
    public CampaignSession acbric$campaignSession() { return acbric$session; }

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void acbric$onClientCreated(CallbackInfo ci) {
        net.fabricacs.api.impl.UiBridge.created((AirshipGame) (Object) this);
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
        net.fabricacs.api.impl.UiBridge.exit((AirshipGame) (Object) this);
        CampaignLifecycleHooks.exit((AirshipGame) (Object) this);
    }
    // 原版在这个时点已完成 ScaledInput 包装；继续执行 input 主体以保留模拟和网络推进。
    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "input", at = @At(value = "INVOKE",
            target = "Ljava/lang/System;currentTimeMillis()J", ordinal = 0), argsOnly = true, ordinal = 0, remap = false)
    private Input acbric$filterUiInput(Input input) {
        return net.fabricacs.api.impl.UiBridge.filter((AirshipGame) (Object) this, input);
    }

    @org.spongepowered.asm.mixin.injection.ModifyArgs(method = "input", at = @At(value = "INVOKE",
            target = "Lcom/zarkonnen/airships/Screen;input(Lcom/zarkonnen/catengine/Input;Lcom/zarkonnen/airships/MyDraw$State;Lcom/zarkonnen/catengine/util/Pt;Lcom/zarkonnen/catengine/util/Pt;I)V"), remap = false)
    private void acbric$blockOpeningClick(org.spongepowered.asm.mixin.injection.invoke.arg.Args args) {
        if (net.fabricacs.api.impl.UiBridge.openedDuringNativeInput((AirshipGame) (Object) this)) {
            args.set(0, new net.fabricacs.api.impl.UiMaskedInput(args.get(0), true, true));
            args.set(2, null); args.set(3, null);
        }
    }

    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void acbric$prepareUiRender(com.zarkonnen.catengine.Frame frame, CallbackInfo ci) {
        net.fabricacs.api.impl.UiBridge.beforeRender((AirshipGame) (Object) this, drawState);
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/zarkonnen/airships/AirshipGame;updateUIGlowRects(ILcom/zarkonnen/airships/MyDraw$State;)V"), remap = false)
    private void acbric$renderUi(com.zarkonnen.catengine.Frame frame, CallbackInfo ci) {
        net.fabricacs.api.impl.UiBridge.render((AirshipGame) (Object) this, frame, drawState);
    }

    @Inject(method = "getTypedText", at = @At("HEAD"), cancellable = true, remap = false)
    private static void acbric$maskedText(Input input, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<String> cir) {
        if (net.fabricacs.api.impl.UiBridge.mask(input) != null) cir.setReturnValue("");
    }

    @Inject(method = "getMyInput", at = @At("HEAD"), cancellable = true, remap = false)
    private static void acbric$unwrapUi(Input input, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<com.zarkonnen.catengine.SlickEngine.MyInput> cir) {
        if (net.fabricacs.api.impl.UiBridge.mask(input) != null)
            cir.setReturnValue((com.zarkonnen.catengine.SlickEngine.MyInput) net.fabricacs.api.impl.UiBridge.unwrap(input));
    }

}
