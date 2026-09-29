/*
 * CampaignWorldMixin.java — 在生成准备及存档反序列化成功处通知；不挂通用地图构造器。
 * setupPlayer 位于生成后的首份自动存档之前；恢复用的 WorldMap 构造重载不发加载事件。
 */
package net.fabricacs.api.mixin;

import com.zarkonnen.airships.AirshipGame;
import com.zarkonnen.airships.CampaignWorld;
import com.zarkonnen.airships.InPipe;
import net.fabricacs.api.event.AirshipsCampaignEvents;
import org.json.JSONObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CampaignWorld.class, remap = false)
public abstract class CampaignWorldMixin {
    @Unique private boolean acbric$created;

    @Inject(method = "setupPlayer()V", at = @At("RETURN"))
    private void acbric$created(CallbackInfo ci) {
        CampaignWorld world = (CampaignWorld) (Object) this;
        if (!acbric$created && world.map.campaignWorldDuringGen == world) {
            acbric$created = true;
            AirshipsCampaignEvents.CREATED.invoker().onCreated(world);
        }
    }

    @Inject(method = "<init>(Lorg/json/JSONObject;Lcom/zarkonnen/airships/AirshipGame;ZLcom/zarkonnen/airships/InPipe;)V", at = @At("RETURN"))
    private void acbric$loaded(JSONObject data, AirshipGame game, boolean multiplayer, InPipe input, CallbackInfo ci) throws java.io.IOException {
        net.fabricacs.api.impl.SharedRulesRegistry.validateLoaded(((CampaignWorld)(Object)this).map);
        AirshipsCampaignEvents.LOADED.invoker().onLoaded(this, multiplayer);
    }
}
