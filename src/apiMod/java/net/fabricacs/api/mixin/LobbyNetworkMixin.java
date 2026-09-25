/* LobbyNetworkMixin.java — 只观察原生消费者的 poll 返回，不增加额外网络读取；拦截准备发件。 */
package net.fabricacs.api.mixin;

import com.zarkonnen.airships.AirshipGame;
import net.fabricacs.api.impl.LobbyHandshakeBridge;
import org.json.JSONObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AirshipGame.class, remap = false)
public abstract class LobbyNetworkMixin {
    @Inject(method = "pollMessage()Lorg/json/JSONObject;", at = @At("RETURN"), cancellable = true, remap = false)
    private void acbric$consumeProtocol(CallbackInfoReturnable<JSONObject> cir) {
        cir.setReturnValue(LobbyHandshakeBridge.incoming((AirshipGame)(Object)this, cir.getReturnValue()));
    }
    @Inject(method = "sendMessage(Lorg/json/JSONObject;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void acbric$bindReady(JSONObject message, CallbackInfo ci) {
        if (!LobbyHandshakeBridge.outgoing((AirshipGame)(Object)this, message)) ci.cancel();
    }
}
