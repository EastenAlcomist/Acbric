/* WorldGenScreenMixin.java — 新战役开始生成之前保存已核准规则；不挂加载或状态恢复构造器。 */
package net.fabricacs.api.mixin;

import com.zarkonnen.airships.*;
import net.fabricacs.api.impl.SharedRulesRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=WorldGenScreen.class,remap=false)
public abstract class WorldGenScreenMixin {
    @Inject(method="<init>(Lcom/zarkonnen/airships/CampaignWorld;Lcom/zarkonnen/airships/AirshipGame;)V",at=@At("RETURN"))
    private void acbric$freezeRules(CampaignWorld world,AirshipGame game,CallbackInfo ci){SharedRulesRegistry.beforeGeneration(world,game);}
}
