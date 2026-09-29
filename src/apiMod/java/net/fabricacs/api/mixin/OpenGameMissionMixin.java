/* OpenGameMissionMixin.java — 原生选档读取后、世界构造前预检查规则；只报告，不转换或写档。 */
package net.fabricacs.api.mixin;

import com.zarkonnen.airships.*;
import com.zarkonnen.catengine.util.Utils;
import net.fabricacs.api.impl.SharedRulesRegistry;
import org.json.JSONObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.io.File;
import java.io.IOException;

@Mixin(value = OpenGameMission.class, remap = false)
public abstract class OpenGameMissionMixin {
    @Inject(method = "load(Ljava/io/File;)Lcom/zarkonnen/catengine/util/Utils$Pair;", at = @At("RETURN"))
    private static void acbric$preflight(File file, CallbackInfoReturnable<Utils.Pair<JSONObject, InPipe>> result) throws IOException {
        var data = result.getReturnValue();
        SharedRulesRegistry.preflight(data.a, data.b);
    }
}
