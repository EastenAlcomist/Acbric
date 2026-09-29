/* StrategicLobbyMixin.java — 战役大厅握手状态展示及准备/开局双边界门禁，不改世界加载时序。 */
package net.fabricacs.api.mixin;

import com.zarkonnen.airships.*;
import com.zarkonnen.catengine.Draw;
import com.zarkonnen.catengine.Fount;
import net.fabricacs.api.impl.LobbyHandshakeAccess;
import net.fabricacs.api.impl.LobbyHandshakeBridge;
import org.json.JSONObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = StrategicLobbyScreen.class, remap = false)
public abstract class StrategicLobbyMixin implements LobbyHandshakeAccess {
    @Shadow private boolean modsLoaded;
    @Shadow private boolean unableToLoadCorrectMods;
    @Shadow private ModReloadProgressDialog mrpd;
    @Shadow private boolean canSendReady() { throw new AssertionError(); }
    @Shadow private boolean canStart() { throw new AssertionError(); }
    @Unique private LobbyHandshakeBridge acbric$handshake;

    @Override public LobbyHandshakeBridge acbric$lobbyHandshake() {
        if (acbric$handshake == null) acbric$handshake = new LobbyHandshakeBridge((StrategicLobbyScreen)(Object)this);
        return acbric$handshake;
    }
    @Inject(method = "processWelcome(Lorg/json/JSONObject;)Z", at = @At("RETURN"), remap = false)
    private void acbric$welcome(JSONObject message, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) acbric$lobbyHandshake().welcome(message);
    }
    @Inject(method = "update", at = @At("HEAD"), remap = false)
    private void acbric$tick(CallbackInfoReturnable<Boolean> cir) { acbric$lobbyHandshake().pulse(); }
    @Inject(method = "canSendReady()Z", at = @At("RETURN"), cancellable = true, remap = false)
    private void acbric$readyAllowed(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) cir.setReturnValue(modsLoaded && mrpd == null && !unableToLoadCorrectMods && acbric$lobbyHandshake().canReady());
    }
    @Inject(method = "canStart()Z", at = @At("RETURN"), cancellable = true, remap = false)
    private void acbric$startAllowed(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) cir.setReturnValue(acbric$lobbyHandshake().canStart());
    }
    @Inject(method = "sendReady()V", at = @At("HEAD"), cancellable = true, remap = false)
    private void acbric$guardReady(CallbackInfo ci) { if (!canSendReady()) ci.cancel(); }
    @Inject(method = "startGame()V", at = @At("HEAD"), cancellable = true, remap = false)
    private void acbric$guardStart(CallbackInfo ci) { if (!canStart() || !acbric$lobbyHandshake().beginStart()) ci.cancel(); }
    @Inject(method = "leave(Z)V", at = @At("HEAD"), remap = false)
    private void acbric$leave(CallbackInfo ci) { acbric$lobbyHandshake().close(); }
    @Inject(method = "startGame()V", at = @At("RETURN"), remap = false)
    private void acbric$started(CallbackInfo ci) { acbric$lobbyHandshake().close(); }

    @Redirect(method = "render", slice = @Slice(to = @At(value = "INVOKE", target = "Lcom/zarkonnen/airships/MyDraw;bw(Ljava/lang/String;)I", ordinal = 0)),
            at = @At(value = "INVOKE", target = "Lcom/zarkonnen/airships/MyDraw;text(Ljava/lang/String;Lcom/zarkonnen/catengine/Fount;DD)Lcom/zarkonnen/catengine/Draw;"), require = 2, remap = false)
    private Draw acbric$status(MyDraw draw, String original, Fount font, double x, double y) {
        return acbric$lobbyHandshake().drawStatus(draw, original, font, x, y);
    }
}
