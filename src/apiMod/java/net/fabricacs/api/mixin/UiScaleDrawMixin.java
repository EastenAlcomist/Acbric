/* UiScaleDrawMixin.java — 让原生 Draw 使用 UI 素材适配器；覆盖普通 Draw 和 MyDraw，不绑定具体引擎。 */
package net.fabricacs.api.mixin;

import com.zarkonnen.catengine.Draw;
import com.zarkonnen.catengine.Frame;
import net.fabricacs.api.impl.UiScaleFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Draw.class, remap = false)
public abstract class UiScaleDrawMixin {
    @Shadow Frame f;

    // 不带描述符的 <init> 在当前 Mixin 版本只选中一个重载；原生 MyDraw 实际使用带 Hooks 的重载。
    @Inject(method = {"<init>(Lcom/zarkonnen/catengine/Frame;)V",
            "<init>(Lcom/zarkonnen/catengine/Frame;Lcom/zarkonnen/catengine/Hooks;)V"},
            at = @At("RETURN"), require = 2, remap = false)
    private void acbric$scaleImages(CallbackInfo ci) { f = UiScaleFrame.wrap(f); }
}
