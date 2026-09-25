/* SharedRulesRegistry.java — 声明注册、候选快照及战役中的只读规则；不读取磁盘配置，不静默补齐旧存档。 */
package net.fabricacs.api.impl;

import net.fabricacs.api.rules.*;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public final class SharedRulesRegistry {
    private static final Map<String,SharedRules> DECLARED=new TreeMap<>();
    private static final AtomicLong REVISION=new AtomicLong();
    private SharedRulesRegistry(){}
    public static synchronized SharedRules register(String id,int version,JSONObject values,SharedRules.Validator validator){
        if(DECLARED.containsKey(id))throw new IllegalStateException("Shared rules already declared: "+id);
        if(DECLARED.size()>=64)throw new IllegalStateException("Too many rule declarations");
        SharedRules handle=new SharedRules(id,version,values,validator);
        DECLARED.put(id,handle);changed();return handle;
    }
    public static void changed(){REVISION.incrementAndGet();}
    static long revision(){return REVISION.get();}
    private static synchronized Map<String,SharedRules> declarations(){return Map.copyOf(DECLARED);}
    static RuleSet newCampaign(){
        long before=revision();Map<String,SharedRuleSnapshot> values=new TreeMap<>();
        declarations().forEach((id,handle)->values.put(id,handle.current()));
        if(before!=revision())return RuleSet.invalid("NEW","RULES_CHANGED_DURING_CAPTURE");
        try{return new RuleSet("NEW",values,"");}catch(IllegalArgumentException tooLarge){return RuleSet.invalid("NEW","RULES_TOO_LARGE");}
    }
    static CampaignDataStore store(Object map){
        if(!(map instanceof CampaignDataAccess access) || access.acbric$campaignDataStore()==null)throw new IllegalArgumentException("Expected Acbric WorldMap");
        return access.acbric$campaignDataStore();
    }
    private static RuleSet stored(Object map){
        var entry=store(map).read("acbric_api");
        if(entry.isEmpty() || !entry.get().data().has("sharedRules"))return RuleSet.empty("SAVED");
        if(entry.get().dataVersion()!=1)throw new IllegalArgumentException("RULE_STORAGE_VERSION");
        Object raw=entry.get().data().get("sharedRules");
        if(!(raw instanceof String text))throw new IllegalArgumentException("RULE_STORAGE_FORMAT");
        RuleSet saved=RuleSet.parse(text);
        if(!saved.valid())throw new IllegalArgumentException("RULE_STORAGE_INVALID");
        return new RuleSet("SAVED",saved.entries,"");
    }
    static RuleSet saved(Object map){
        try{
            long before=revision();RuleSet saved=stored(map);
            for(var entry:declarations().entrySet()){
                var value=saved.entries.get(entry.getKey());
                if(value==null)return RuleSet.invalid("SAVED","RULE_MISSING: "+entry.getKey());
                try{entry.getValue().checked(value);}catch(RuntimeException bad){return RuleSet.invalid("SAVED","RULE_INVALID_OR_VERSION: "+entry.getKey());}
            }
            if(before!=revision())return RuleSet.invalid("SAVED","RULES_CHANGED_DURING_CAPTURE");
            return saved;
        }catch(RuntimeException bad){return RuleSet.invalid("SAVED","RULE_STORAGE_INVALID");}
    }
    public static SharedRuleSnapshot savedRule(Object map,String id){
        var rule=stored(map).entries.get(id);
        if(rule==null)throw new IllegalStateException("RULE_MISSING: "+id);
        return rule;
    }
    /** 构造加载战役成功后、通知 LOADED 前校验；不补值、不迁移、不写盘。 */
    public static void validateLoaded(Object map) throws java.io.IOException {
        RuleSet saved=saved(map);
        if(!saved.valid())throw new java.io.IOException("Acbric shared rules: "+saved.problem);
    }
    /** 原生地图反序列化只检查保留块结构，不执行任何 MOD 校验回调。 */
    public static void validateStored(Object map) throws java.io.IOException {
        try{stored(map);}catch(RuntimeException bad){throw new java.io.IOException("Acbric shared rule storage is invalid",bad);}
    }
    static void freeze(Object map,RuleSet candidate){
        if(candidate==null || !candidate.valid() || !candidate.source.equals("NEW"))throw new IllegalStateException("Unconfirmed new-campaign rules");
        CampaignDataStore store=store(map);var old=store.read("acbric_api");
        if(old.isPresent() && old.get().dataVersion()!=1)throw new IllegalStateException("RULE_STORAGE_VERSION");
        JSONObject payload=old.isEmpty()?new JSONObject():old.get().data();
        if(payload.has("sharedRules"))throw new IllegalStateException("Rules already frozen; refusing overwrite");
        store.write("acbric_api",1,payload.put("sharedRules",candidate.text()));
    }
    /** 在 WorldGenScreen 构造入口固化，早于生成和 CREATED，不会重写加载/恢复的地图。 */
    public static void beforeGeneration(com.zarkonnen.airships.CampaignWorld world,com.zarkonnen.airships.AirshipGame game){
        RuleSet selected;
        if(world.isMultiplayer()){
            if(!(game.s instanceof LobbyHandshakeAccess access))throw new IllegalStateException("Multiplayer generation requires confirmed campaign lobby");
            selected=access.acbric$lobbyHandshake().startingRules();
        }else selected=newCampaign();
        freeze(world.map,selected);
    }
}
