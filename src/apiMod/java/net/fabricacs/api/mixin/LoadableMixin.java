/*
 * LoadableMixin.java — 观察 Loadable.load 的开始和真实结果，不清理诊断，也不将失败改为成功。
 */
package net.fabricacs.api.mixin;

import com.zarkonnen.airships.Loadable;
import net.fabricacs.api.impl.LifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Loadable.class, remap = false)
public abstract class LoadableMixin {

    @Inject(method = "load", at = @At("HEAD"), remap = false)
    private static void acbric$onDataLoadStarting(CallbackInfoReturnable<Boolean> cir) {
        LifecycleHooks.fireDataLoadStarting();
    }

    // 原样通知游戏结果；解析、I/O 和后处理错误不一定体现在 failures 集合中。
    @Inject(method = "load", at = @At("RETURN"), remap = false)
    private static void acbric$onDataLoaded(CallbackInfoReturnable<Boolean> cir) {
        LifecycleHooks.fireDataLoaded(cir.getReturnValueZ());
    }
}
