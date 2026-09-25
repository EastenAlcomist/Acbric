/*
 * WorldMapMixin.java — 将 MOD 战役数据附着于地图实例，参与存档、校验及重连恢复。
 * 不在序列化热路径调用 MOD 代码，也不改变游戏字段或自动发送指令。
 */
package net.fabricacs.api.mixin;

import com.zarkonnen.airships.AirshipGame;
import com.zarkonnen.airships.InPipe;
import com.zarkonnen.airships.OutPipe;
import com.zarkonnen.airships.WorldMap;
import net.fabricacs.api.impl.CampaignDataAccess;
import net.fabricacs.api.impl.CampaignDataHooks;
import net.fabricacs.api.impl.CampaignDataStore;
import org.json.JSONObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;

@Mixin(value = WorldMap.class, remap = false)
public abstract class WorldMapMixin implements CampaignDataAccess {
    @Unique
    private final CampaignDataStore acbric$campaignData = new CampaignDataStore();

    @Override
    public CampaignDataStore acbric$campaignDataStore() { return acbric$campaignData; }

    @Inject(method = "<init>(Lorg/json/JSONObject;Lcom/zarkonnen/airships/AirshipGame;Lcom/zarkonnen/airships/InPipe;)V", at = @At("RETURN"))
    private void acbric$readCampaignData(JSONObject data, AirshipGame game, InPipe input, CallbackInfo ci) throws IOException {
        CampaignDataHooks.read(acbric$campaignData, data, input);
    }

    @Inject(method = "toJSON(Lcom/zarkonnen/airships/OutPipe;)Lorg/json/JSONObject;", at = @At("RETURN"))
    private void acbric$writeCampaignData(OutPipe output, CallbackInfoReturnable<JSONObject> cir) {
        CampaignDataHooks.write(acbric$campaignData, cir.getReturnValue(), output);
    }
}
