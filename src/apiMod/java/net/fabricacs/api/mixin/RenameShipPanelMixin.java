/*
 * RenameShipPanelMixin.java — 保留重命名面板的旧 ONE_SHOT 回调，再触发准确命名的 RENAME_SHIP 回调。
 * 任何 BEFORE 取消都会阻止后续组、原方法及全部 AFTER，不能悄悄改旧钩子位置。
 */
package net.fabricacs.api.mixin;

import com.zarkonnen.airships.MyDraw;
import com.zarkonnen.airships.RenameShipPanel;
import com.zarkonnen.airships.UniScreen;
import com.zarkonnen.catengine.Hooks;
import com.zarkonnen.catengine.Input;
import com.zarkonnen.catengine.util.Pt;
import com.zarkonnen.catengine.util.ScreenMode;
import net.fabricacs.api.event.CombatUiPanelType;
import net.fabricacs.api.impl.LifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 旧回调始终先执行，其取消短路行为也必须保留。
@SuppressWarnings("deprecation")
@Mixin(value = RenameShipPanel.class, remap = false)
public abstract class RenameShipPanelMixin {

    @Inject(method = "draw", at = @At("HEAD"), cancellable = true, remap = false)
    private void acbric$beforeDraw(MyDraw draw, Pt mouse, ScreenMode mode,
                                    Hooks hooks, UniScreen screen, CallbackInfo ci) {
        if (LifecycleHooks.fireOneShotWeaponsBeforeDraw(
                CombatUiPanelType.ONE_SHOT_WEAPONS, this, draw, mouse, mode, hooks, screen)) {
            ci.cancel();
            return;
        }
        if (LifecycleHooks.fireOneShotBuoyancyBeforeDraw(
                CombatUiPanelType.ONE_SHOT_BUOYANCY, this, draw, mouse, mode, hooks, screen)) {
            ci.cancel();
            return;
        }
        if (LifecycleHooks.fireOneShotPowerBeforeDraw(
                CombatUiPanelType.ONE_SHOT_POWER, this, draw, mouse, mode, hooks, screen)) {
            ci.cancel();
            return;
        }
        if (LifecycleHooks.fireRenameShipBeforeDraw(this, draw, mouse, mode, hooks, screen)) {
            ci.cancel();
        }
    }

    @Inject(method = "draw", at = @At("RETURN"), remap = false)
    private void acbric$afterDraw(MyDraw draw, Pt mouse, ScreenMode mode,
                                   Hooks hooks, UniScreen screen, CallbackInfo ci) {
        LifecycleHooks.fireOneShotWeaponsAfterDraw(
                CombatUiPanelType.ONE_SHOT_WEAPONS, this, draw, mouse, mode, hooks, screen);
        LifecycleHooks.fireOneShotBuoyancyAfterDraw(
                CombatUiPanelType.ONE_SHOT_BUOYANCY, this, draw, mouse, mode, hooks, screen);
        LifecycleHooks.fireOneShotPowerAfterDraw(
                CombatUiPanelType.ONE_SHOT_POWER, this, draw, mouse, mode, hooks, screen);
        LifecycleHooks.fireRenameShipAfterDraw(this, draw, mouse, mode, hooks, screen);
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private void acbric$beforeTick(Input input, int elapsedMs,
                                    UniScreen screen, CallbackInfo ci) {
        if (LifecycleHooks.fireOneShotWeaponsBeforeTick(
                CombatUiPanelType.ONE_SHOT_WEAPONS, this, input, elapsedMs, screen)) {
            ci.cancel();
            return;
        }
        if (LifecycleHooks.fireOneShotBuoyancyBeforeTick(
                CombatUiPanelType.ONE_SHOT_BUOYANCY, this, input, elapsedMs, screen)) {
            ci.cancel();
            return;
        }
        if (LifecycleHooks.fireOneShotPowerBeforeTick(
                CombatUiPanelType.ONE_SHOT_POWER, this, input, elapsedMs, screen)) {
            ci.cancel();
            return;
        }
        if (LifecycleHooks.fireRenameShipBeforeTick(this, input, elapsedMs, screen)) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("RETURN"), remap = false)
    private void acbric$afterTick(Input input, int elapsedMs,
                                   UniScreen screen, CallbackInfo ci) {
        LifecycleHooks.fireOneShotWeaponsAfterTick(
                CombatUiPanelType.ONE_SHOT_WEAPONS, this, input, elapsedMs, screen);
        LifecycleHooks.fireOneShotBuoyancyAfterTick(
                CombatUiPanelType.ONE_SHOT_BUOYANCY, this, input, elapsedMs, screen);
        LifecycleHooks.fireOneShotPowerAfterTick(
                CombatUiPanelType.ONE_SHOT_POWER, this, input, elapsedMs, screen);
        LifecycleHooks.fireRenameShipAfterTick(this, input, elapsedMs, screen);
    }
}
