/*
 * CommandButtonsPanelMixin.java — 为命令按钮面板转发 draw/tick 事件，使用 COMMAND_BUTTONS 标签。
 */
package net.fabricacs.api.mixin;

import com.zarkonnen.airships.CommandButtonsPanel;
import com.zarkonnen.airships.MyDraw;
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

@Mixin(value = CommandButtonsPanel.class, remap = false)
public abstract class CommandButtonsPanelMixin {
    @Inject(method = "draw", at = @At("HEAD"), cancellable = true, remap = false)
    private void acbric$beforeDraw(MyDraw draw, Pt mouse, ScreenMode mode,
                                    Hooks hooks, UniScreen screen, CallbackInfo ci) {
        if (LifecycleHooks.firePlayerControlPanelBeforeDraw(
                CombatUiPanelType.COMMAND_BUTTONS, this, draw, mouse, mode, hooks, screen)) {
            ci.cancel();
        }
    }

    @Inject(method = "draw", at = @At("RETURN"), remap = false)
    private void acbric$afterDraw(MyDraw draw, Pt mouse, ScreenMode mode,
                                   Hooks hooks, UniScreen screen, CallbackInfo ci) {
        LifecycleHooks.firePlayerControlPanelAfterDraw(
                CombatUiPanelType.COMMAND_BUTTONS, this, draw, mouse, mode, hooks, screen);
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private void acbric$beforeTick(Input input, int elapsedMs,
                                    UniScreen screen, CallbackInfo ci) {
        if (LifecycleHooks.firePlayerControlPanelBeforeTick(
                CombatUiPanelType.COMMAND_BUTTONS, this, input, elapsedMs, screen)) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("RETURN"), remap = false)
    private void acbric$afterTick(Input input, int elapsedMs,
                                   UniScreen screen, CallbackInfo ci) {
        LifecycleHooks.firePlayerControlPanelAfterTick(
                CombatUiPanelType.COMMAND_BUTTONS, this, input, elapsedMs, screen);
    }
}
