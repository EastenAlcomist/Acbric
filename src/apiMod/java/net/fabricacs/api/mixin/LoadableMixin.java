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

    /**
     * The game's data loader treats cosmetic log warnings the same as fatal
     * errors: {@code loadBase} copies {@code LoadResult.log} into
     * {@code errorLogs}, and {@code load()} aborts the whole load when any
     * class's log (or an expansion/mod's log) is non-empty.  This cascades
     * into later loadable types (e.g. Charge, ModuleType) never loading.
     *
     * <p>This clears the log whenever there are no real parse failures, so
     * warnings are downgraded to non-fatal and the full data set loads.</p>
     */
    @Inject(method = "loadDir",
            at = @At("RETURN"),
            cancellable = false,
            remap = false)
    private static void acbric$clearNonFatalLog(
            java.lang.Class<?> c, java.io.File dir,
            java.util.HashMap<String, org.json.JSONObject> baseEntries,
            CallbackInfoReturnable<com.zarkonnen.airships.Loadable.LoadResult> cir) {
        com.zarkonnen.airships.Loadable.LoadResult result = cir.getReturnValue();
        if (result == null) return;
        if (result.failures == null || result.failures.isEmpty()) {
            if (result.log != null) {
                result.log.clear();
            }
        }
    }

    /** Fallback: only override the return when there are no real failures. */
    @Inject(method = "load", at = @At("RETURN"), cancellable = true, remap = false)
    private static void acbric$onDataLoaded(CallbackInfoReturnable<Boolean> cir) {
        boolean ok = cir.getReturnValueZ();
        if (!ok) {
            boolean anyErrors = false;
            if (Loadable.LOADABLES != null) {
                for (java.lang.Class<?> c : Loadable.LOADABLES) {
                    java.util.ArrayList<String> errors = Loadable.getErrors(c);
                    if (errors != null && !errors.isEmpty()) {
                        anyErrors = true;
                        break;
                    }
                }
            }
            if (!anyErrors) {
                cir.setReturnValue(true);
                ok = true;
            }
        }
        LifecycleHooks.fireDataLoaded(ok);
    }
}
