/* ResumeScreenMixin.java — 原版恢复输入步骤正常返回后确认新战役已安装，再通知句柄交接。 */
package net.fabricacs.api.mixin;

import com.zarkonnen.airships.ResumeScreen;
import net.fabricacs.api.impl.CampaignLifecycleHooks;
import net.fabricacs.api.impl.LobbyHandshakeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ResumeScreen.class, remap = false)
public abstract class ResumeScreenMixin {
    @Inject(method = "input", at = @At("RETURN"))
    private void acbric$restored(CallbackInfo ci) {
        CampaignLifecycleHooks.afterResume((ResumeScreen) (Object) this);
        LobbyHandshakeBridge.afterResume((ResumeScreen) (Object) this);
    }
}
