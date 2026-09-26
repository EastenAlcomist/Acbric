/* ModListDrawFixture.java — 仅在测试中观测原版界面是否真正进入列表绘制分支，不替换判断或绘制。 */
package net.fabricacs.regression.fixtures;

import com.zarkonnen.airships.ModsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ModsScreen.class, remap = false)
public abstract class ModListDrawFixture {
    @Inject(method = "render", at = @At(value = "FIELD", target = "Lcom/zarkonnen/airships/ModsScreen;modsSB:Lcom/zarkonnen/airships/ScrollBar;", opcode = 180), require = 1)
    private void acbric$observeList(CallbackInfo ci) {
        System.setProperty("acbric.test.listDraws", Integer.toString(Integer.getInteger("acbric.test.listDraws", 0) + 1));
    }
}
