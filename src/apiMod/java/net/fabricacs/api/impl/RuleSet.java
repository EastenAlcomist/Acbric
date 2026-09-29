/* RuleSet.java — 全部已声明规则的内部快照；值以规范 JSON 文本保存，传输/存档不调用游戏浮点编码。 */
package net.fabricacs.api.impl;

import net.fabricacs.api.rules.SharedRuleSnapshot;
import org.json.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class RuleSet {
    final String source, problem;
    final Map<String,SharedRuleSnapshot> entries;
    private final String text;
    RuleSet(String source, Map<String,SharedRuleSnapshot> entries, String problem) {
        if(!Set.of("NEW","SAVED").contains(source))throw new IllegalArgumentException("RULE_SOURCE");
        if(entries.size()>64 || problem.length()>160)throw new IllegalArgumentException("RULES_LIMIT");
        this.source=source;this.entries=Collections.unmodifiableMap(new TreeMap<>(entries));this.problem=problem;
        JSONObject mods=new JSONObject();
        this.entries.forEach((id,value)->{CampaignDataStore.validateModId(id);mods.put(id,new JSONObject().put("version",value.version()).put("values",RulesJson.encode(value.values())));});
        text=RulesJson.encode(new JSONObject().put("format",1).put("source",source).put("problem",problem).put("mods",mods));
    }
    static RuleSet invalid(String source,String problem){return new RuleSet(source,Map.of(),problem);}
    static RuleSet empty(String source){return new RuleSet(source,Map.of(),"");}
    boolean valid(){return problem.isEmpty();}
    String text(){return text;}
    JSONObject json(){return RulesJson.parse(text);}
    String digest(){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(Exception impossible){throw new AssertionError(impossible);}}
    static RuleSet parse(String text){
        JSONObject root=RulesJson.parse(text);keys(root,Set.of("format","source","problem","mods"));
        if(CampaignDataStore.version(root,"format")!=1 || !(root.get("source") instanceof String) || !(root.get("problem") instanceof String))throw new IllegalArgumentException("RULE_FORMAT");
        Map<String,SharedRuleSnapshot> entries=new TreeMap<>();JSONObject mods=root.getJSONObject("mods");
        if(mods.length()>64)throw new IllegalArgumentException("RULES_LIMIT");
        var it=mods.keys();while(it.hasNext()){
            String id=(String)it.next();JSONObject row=mods.getJSONObject(id);keys(row,Set.of("version","values"));
            if(!(row.get("values") instanceof String data))throw new IllegalArgumentException("RULE_VALUES");
            entries.put(id,new SharedRuleSnapshot(CampaignDataStore.version(row,"version"),RulesJson.parse(data)));
        }
        if(!root.getString("problem").isEmpty() && !entries.isEmpty())throw new IllegalArgumentException("RULE_INVALID_ENTRIES");
        return new RuleSet(root.getString("source"),entries,root.getString("problem"));
    }
    static void keys(JSONObject object,Set<String> expected){Set<String> keys=new HashSet<>();var it=object.keys();while(it.hasNext())keys.add((String)it.next());if(!keys.equals(expected))throw new IllegalArgumentException("RULE_FIELDS");}
    boolean matches(RuleSet other){return valid() && other!=null && other.valid() && text.equals(other.text);}
    List<String> differences(RuleSet other){
        List<String> result=new ArrayList<>();
        if(!valid())result.add(problem);if(!other.valid())result.add("REMOTE: "+other.problem);
        if(!source.equals(other.source))result.add("CAMPAIGN_SOURCE");
        TreeSet<String> ids=new TreeSet<>(entries.keySet());ids.addAll(other.entries.keySet());
        for(String id:ids){
            var a=entries.get(id);var b=other.entries.get(id);
            if(a==null || b==null){result.add(id+": DECLARATION_MISSING");continue;}
            if(a.version()!=b.version())result.add(id+": RULE_VERSION");
            diff(id,a.values(),b.values(),result);
            if(result.size()>=32)break;
        }
        return List.copyOf(result.subList(0,Math.min(32,result.size())));
    }
    private static void diff(String path,JSONObject a,JSONObject b,List<String> result){
        TreeSet<String> keys=new TreeSet<>();var ai=a.keys();while(ai.hasNext())keys.add((String)ai.next());var bi=b.keys();while(bi.hasNext())keys.add((String)bi.next());
        for(String key:keys){
            if(result.size()>=32)return;String name=path+"/"+key.replace("~","~0").replace("/","~1");
            if(!a.has(key)||!b.has(key)){result.add(name+": MISSING");continue;}
            Object av=a.get(key),bv=b.get(key);
            if(av instanceof JSONObject ao && bv instanceof JSONObject bo)diff(name,ao,bo,result);
            else if(!RulesJson.encode(new JSONObject().put("v",av)).equals(RulesJson.encode(new JSONObject().put("v",bv))))result.add(name+": VALUE");
        }
    }
}
