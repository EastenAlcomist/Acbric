/* SharedRules.java — MOD 的共享规则声明句柄；更新只影响后续新战役，不写配置或已存在的战役。 */
package net.fabricacs.api.rules;

import net.fabricacs.api.impl.SharedRulesRegistry;
import org.json.JSONObject;
import java.util.Objects;

public final class SharedRules {
    private final String modId;
    private final Validator validator;
    private SharedRuleSnapshot current;
    private boolean validating;
    public SharedRules(String modId, int version, JSONObject values, Validator validator) {
        net.fabricacs.api.impl.CampaignDataStore.validateModId(modId);
        this.modId=modId;this.validator=Objects.requireNonNull(validator);
        current=checked(new SharedRuleSnapshot(version,values));
    }
    public String modId(){return modId;}
    public synchronized SharedRuleSnapshot current(){return current;}
    /** 显式更新新战役候选值；失败保留旧值，活动战役必须继续读取 forCampaign。 */
    public synchronized void update(JSONObject values){
        if(validating)throw new IllegalStateException("Reentrant rule validation");
        SharedRuleSnapshot next=checked(new SharedRuleSnapshot(current.version(),values));
        if(!next.equals(current)){current=next;SharedRulesRegistry.changed();}
    }
    public SharedRuleSnapshot forCampaign(Object worldMap){
        SharedRuleSnapshot snapshot=SharedRulesRegistry.savedRule(worldMap,modId);
        return checked(snapshot);
    }
    /** 校验器应纯读取；得到的是副本，修改不会影响规则。 */
    public synchronized SharedRuleSnapshot checked(SharedRuleSnapshot snapshot){
        if(current!=null && snapshot.version()!=current.version())throw new IllegalStateException("RULE_VERSION: "+modId);
        if(validating)throw new IllegalStateException("Reentrant rule validation");
        validating=true;
        try{validator.validate(snapshot.values());return snapshot;}
        catch(Exception failure){throw new IllegalArgumentException("RULE_VALIDATION: "+modId,failure);}
        finally{validating=false;}
    }
    @FunctionalInterface public interface Validator {void validate(JSONObject values) throws Exception;}
}
