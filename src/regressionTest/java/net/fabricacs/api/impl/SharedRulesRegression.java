/* SharedRulesRegression.java — 共享规则防御性复制、旧档拒绝、冻结、会话绑定与准备失效回归。 */
package net.fabricacs.api.impl;

import net.fabricacs.api.rules.*;
import org.json.*;
import java.util.*;

public final class SharedRulesRegression {
    private static int checks;
    private static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;System.out.println("PASS shared rules: "+name);}
    private static void rejects(Runnable body,String name){try{body.run();throw new AssertionError("Accepted: "+name);}catch(IllegalArgumentException|IllegalStateException expected){check(true,name);}}
    private static final class MapFixture implements CampaignDataAccess {
        final CampaignDataStore store=new CampaignDataStore();
        public CampaignDataStore acbric$campaignDataStore(){return store;}
    }
    private static RuleSet rule(String source,int version,int value){return new RuleSet(source,Map.of("rule_test",new SharedRuleSnapshot(version,new JSONObject().put("damage",value))),"");}
    public static int run() throws Exception {
        checks=0;
        JSONObject values=new JSONObject().put("damage",1.5).put("label","中文").put("nested",new JSONObject().put("x",1.0));
        var snapshot=new SharedRuleSnapshot(1,values);values.put("damage",99);
        check(snapshot.values().getDouble("damage")==1.5,"declaration copies mutable input and preserves decimals");
        snapshot.values().put("damage",5);
        check(snapshot.values().getDouble("damage")==1.5,"snapshot reads return defensive copies");
        check(new SharedRuleSnapshot(1,new JSONObject().put("x",1.0)).equals(new SharedRuleSnapshot(1,new JSONObject().put("x",1))),"numeric 1 and 1.0 have identical meaning");
        check(RulesJson.encode(new JSONObject().put("b",2).put("a",1)).equals("{\"a\":1,\"b\":2}"),"object order canonicalized");
        check(RulesJson.parse(RulesJson.encode(new JSONObject().put("n",JSONObject.NULL).put("d",0.00001))).isNull("n"),"null and small decimal survive roundtrip");
        rejects(()->new SharedRuleSnapshot(-1,new JSONObject()),"negative rule version rejected");
        JSONObject cycle=new JSONObject();cycle.put("self",cycle);rejects(()->new SharedRuleSnapshot(0,cycle),"cycles rejected before serialization");
        rejects(()->RulesJson.encode(new JSONObject().put("x",new Object())),"arbitrary objects rejected");
        rejects(()->RulesJson.encode(new JSONObject().put("x","a".repeat(12_001))),"oversized strings rejected");
        rejects(()->RulesJson.encode(new JSONObject().put("x","\ud800")),"unpaired surrogate rejected");
        JSONObject deep=new JSONObject(),cursor=deep;for(int i=0;i<20;i++){var next=new JSONObject();cursor.put("x",next);cursor=next;}
        JSONObject nested=deep;rejects(()->RulesJson.encode(nested),"deep input rejected");
        rejects(()->RulesJson.parse("{\"a\":1,\"a\":2}"),"duplicate JSON keys rejected");
        rejects(()->RulesJson.parse("{} false"),"trailing text rejected");

        RuleSet base=rule("NEW",1,1),different=rule("NEW",1,2);
        check(base.matches(RuleSet.parse(base.text())),"wire roundtrip retains exact rule identity");
        check(base.differences(different).contains("rule_test/damage: VALUE"),"difference names MOD and field path");
        check(!base.matches(rule("SAVED",1,1)),"new and resumed sources are distinct");
        check(!base.matches(RuleSet.invalid("NEW","FAILED")),"invalid snapshot cannot match");
        check(!RuleSet.invalid("NEW","FAILED").matches(RuleSet.invalid("NEW","FAILED")),"two identical failures never match");
        rejects(()->RuleSet.parse(base.json().put("unknown",true).toString()),"unknown manifest fields rejected");
        JSONObject future=base.json();future.put("format",2);rejects(()->RuleSet.parse(future.toString()),"future format rejected");
        JSONObject numeric=base.json();numeric.getJSONObject("mods").getJSONObject("rule_test").put("version",1.0);
        rejects(()->RuleSet.parse(RulesJson.encode(numeric).replace("\"version\":1","\"version\":1.5")),"fractional schema rejected");

        int[] validations={0};
        var handle=SharedRulesRegistry.register("rule_test",1,new JSONObject().put("damage",1),v->{validations[0]++;if(v.getDouble("damage")<=0)throw new IllegalArgumentException();v.put("damage",999);});
        check(handle.current().values().getInt("damage")==1,"validator mutation cannot alter accepted values");
        rejects(()->SharedRulesRegistry.register("rule_test",1,new JSONObject(),v->{}),"duplicate declaration rejected");
        long revision=SharedRulesRegistry.revision();
        rejects(()->handle.update(new JSONObject().put("damage",0)),"invalid update rejected");
        check(handle.current().values().getInt("damage")==1 && SharedRulesRegistry.revision()==revision,"failed update leaves candidate and revision intact");
        handle.update(new JSONObject().put("damage",1));check(SharedRulesRegistry.revision()==revision,"equal update does not invalidate preparation");
        MapFixture map=new MapFixture();map.store.write("other_mod",9,new JSONObject().put("kept",true));
        check(!SharedRulesRegistry.saved(map).valid(),"old save missing declared rules is blocked without defaults");
        rejects(()->handle.forCampaign(map),"public read does not fall back to local config");
        try{SharedRulesRegistry.validateLoaded(map);throw new AssertionError();}catch(java.io.IOException expected){check(true,"loaded world validation fails explicitly");}
        RuleSet selected=SharedRulesRegistry.newCampaign();SharedRulesRegistry.freeze(map,selected);
        check(handle.forCampaign(map).values().getInt("damage")==1,"selected rules freeze into campaign data");
        handle.update(new JSONObject().put("damage",3));
        check(handle.forCampaign(map).values().getInt("damage")==1 && handle.current().values().getInt("damage")==3,"future config changes cannot rewrite frozen rules");
        check(SharedRulesRegistry.saved(map).entries.get("rule_test").values().getInt("damage")==1,"resumed candidate uses save values");
        rejects(()->SharedRulesRegistry.freeze(map,SharedRulesRegistry.newCampaign()),"cannot overwrite frozen rules");
        check(map.store.read("other_mod").orElseThrow().data().getBoolean("kept"),"freeze preserves other MOD data");
        JSONObject saved=map.store.snapshot();int calls=validations[0];MapFixture restored=new MapFixture();restored.store.restore(saved);SharedRulesRegistry.validateStored(restored);
        check(validations[0]==calls,"serialization and structural recovery do not execute MOD validators");
        check(handle.forCampaign(restored).equals(handle.forCampaign(map)),"campaign store restoration retains frozen rules");
        MapFixture bad=new MapFixture();bad.store.write("acbric_api",1,new JSONObject().put("sharedRules",rule("NEW",2,1).text()));
        check(!SharedRulesRegistry.saved(bad).valid(),"saved version mismatch blocks instead of migrating");
        bad.store.write("acbric_api",1,new JSONObject().put("sharedRules","broken"));
        check(!SharedRulesRegistry.saved(bad).valid(),"malformed reserved block never becomes empty success");
        try{SharedRulesRegistry.validateStored(bad);throw new AssertionError();}catch(java.io.IOException expected){check(true,"native restore rejects malformed rules without callbacks");}
        Map<String,SharedRuleSnapshot> retained=new TreeMap<>(selected.entries);retained.put("absent_mod",new SharedRuleSnapshot(8,new JSONObject().put("old",true)));
        MapFixture absent=new MapFixture();SharedRulesRegistry.freeze(absent,new RuleSet("NEW",retained,""));
        check(SharedRulesRegistry.saved(absent).entries.containsKey("absent_mod"),"absent MOD rules retained without invoking absent code");

        Map<Integer,String> sessions=Map.of(1,UUID.randomUUID().toString(),2,UUID.randomUUID().toString());
        LobbyRuleExchange a=new LobbyRuleExchange(),b=new LobbyRuleExchange();a.context(0,1,sessions,base,0);b.context(0,2,sessions,base,0);
        String ap=a.tick(0).orElseThrow(),bp=b.tick(0).orElseThrow();
        for(String key:List.of("v","channel","from","sessions","rules")) {
            JSONObject wrong=new JSONObject(bp).put(key,"wrong");a.receive(wrong.toString(),0,false,0);
            check(!a.matched(),"wrong packet type rejected: "+key);
        }
        a.receive(new JSONObject(bp).put("unknown",true).toString(),0,false,0);check(!a.matched(),"unknown packet schema rejected");
        a.receive(new JSONObject(bp).put("###",-1).toString(),0,false,0);check(!a.matched(),"negative native sequence rejected");
        check(!a.matched() && a.state(0).equals("CHECKING"),"code context alone does not confirm rules");
        a.receive(bp,0,true,0);a.receive(bp,1,false,0);check(!a.matched(),"history and wrong outer room rejected");
        a.receive(new JSONObject(bp).put("###",Long.MAX_VALUE).toString(),0,false,0);b.receive(ap,0,false,0);check(a.matched() && b.matched(),"all-peer rules exchange accepts native long sequence in LAN zero room");
        check(a.tick(1).isEmpty(),"wire retries rate limited");
        var three=new LobbyRuleExchange();var larger=new HashMap<>(sessions);larger.put(3,UUID.randomUUID().toString());three.context(0,1,larger,base,0);three.receive(bp,0,false,0);
        check(!three.matched(),"incomplete or different full roster cannot confirm");
        var d=new LobbyRuleExchange();d.context(0,2,sessions,different,0);a.receive(d.tick(0).orElseThrow(),0,false,1);
        check(a.state(1).equals("UNVERIFIABLE"),"conflicting snapshots in one session remain unverifiable");
        a.receive(bp,0,false,2);check(!a.matched(),"replaying prior success cannot revive contradiction");
        a.reset();a.context(0,1,sessions,base,0);d.reset();d.context(0,2,sessions,different,0);a.receive(d.tick(0).orElseThrow(),0,false,0);
        check(a.state(0).equals("DIFFERENT") && a.differences().get(0).contains("damage"),"rule mismatch produces actionable field difference");
        a.reset();a.context(0,1,sessions,base,0);for(int i=0;i<5;i++)check(a.tick(i*2000).isPresent(),"bounded attempt "+(i+1));
        check(a.tick(10_000).isEmpty() && a.state(10_000).equals("TIMED_OUT"),"silent peer times out after five attempts");
        a.receive(bp,0,false,10_001);check(!a.matched(),"late packet cannot revive timed out exchange");
        var changed=new HashMap<>(sessions);changed.put(2,UUID.randomUUID().toString());a.context(0,1,changed,base,10_002);a.receive(bp,0,false,10_002);
        check(!a.matched(),"old session offer cannot confirm new context");
        JSONObject frame=new JSONObject().put("type","frame").put("messages",new JSONArray().put(new JSONObject().put("type",LobbyRuleExchange.TYPE)).put(new JSONObject().put("type","native")));
        check(LobbyHandshakeBridge.stripReserved(frame).getJSONArray("messages").length()==1,"reserved rule tails filtered, native messages preserved");
        return checks;
    }
}
