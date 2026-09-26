/* ConfigEditor.java — 设置编辑会话：草稿与配置隔离，默认值只改声明字段，显式提交检查内存及磁盘冲突。 */
package net.fabricacs.api.config;

import org.json.JSONObject;
import java.io.IOException;
import java.util.*;

public final class ConfigEditor {
    public record Applied(ConfigSnapshot snapshot,Set<String> changedKeys,Set<ConfigField.Effect> effects){
        public Applied {Objects.requireNonNull(snapshot);changedKeys=Set.copyOf(changedKeys);effects=Set.copyOf(effects);}
    }
    private final ModConfig config;
    private final List<ConfigField> fields;
    private final Map<String,ConfigField> indexed=new LinkedHashMap<>();
    private final Map<String,Object> draft=new LinkedHashMap<>();
    private final Thread thread=Thread.currentThread();
    private ConfigSnapshot baseline;
    public ConfigEditor(ModConfig config,List<ConfigField> fields)throws IOException{
        this.config=Objects.requireNonNull(config);this.fields=List.copyOf(fields);
        if(fields.isEmpty()||fields.size()>32)throw new IllegalArgumentException("1..32 fields");
        for(ConfigField f:this.fields)if(indexed.putIfAbsent(f.key(),f)!=null)throw new IllegalArgumentException("Duplicate field: "+f.key());
        JSONObject defaults=config.defaults().data();
        for(ConfigField f:this.fields)f.parse(f.fromJson(defaults.get(f.key())));
        accept(config.load());
    }
    private void checkThread(){if(Thread.currentThread()!=thread)throw new IllegalStateException("Settings editor is thread confined");}
    private ConfigField field(String key){ConfigField f=indexed.get(key);if(f==null)throw new IllegalArgumentException("Unknown setting: "+key);return f;}
    private void accept(ConfigSnapshot snapshot){
        if(snapshot.dataVersion()!=config.defaults().dataVersion())throw new IllegalStateException("Explicit config migration required");
        Map<String,Object> values=new LinkedHashMap<>();JSONObject data=snapshot.data();
        for(ConfigField f:fields)values.put(f.key(),f.fromJson(data.get(f.key())));
        baseline=snapshot;draft.clear();draft.putAll(values);
    }
    public List<ConfigField> fields(){return fields;}
    public Object value(String key){checkThread();field(key);return draft.get(key);}
    /** 数字以字符串保存草稿，允许 '-' 等暂态输入；应用前统一校验。 */
    public void set(String key,Object value){
        checkThread();ConfigField f=field(key);Objects.requireNonNull(value);
        if(f.kind()==ConfigField.Kind.BOOLEAN?!(value instanceof Boolean):!(value instanceof String))throw new IllegalArgumentException("Draft type");
        if(value instanceof String s&&(s.length()>8192||s.codePoints().anyMatch(Character::isISOControl)))throw new IllegalArgumentException("Draft too long or contains control characters");
        draft.put(key,value);
    }
    public String error(String key,boolean chinese){checkThread();return field(key).error(draft.get(key),chinese);}
    public boolean isValid(){checkThread();return fields.stream().allMatch(f->f.error(draft.get(f.key()),false).isEmpty());}
    public boolean isDirty(){
        checkThread();JSONObject data=baseline.data();
        return fields.stream().anyMatch(f->!Objects.equals(draft.get(f.key()),f.fromJson(data.get(f.key()))));
    }
    public void cancel(){checkThread();accept(baseline);}
    public void restoreDefaults(){
        checkThread();JSONObject defaults=config.defaults().data();for(ConfigField f:fields)draft.put(f.key(),f.fromJson(defaults.get(f.key())));
    }
    /** 显式重读会丢弃草稿；失败保留草稿。UI 调用前应向用户说明。 */
    public void reload()throws IOException{checkThread();accept(config.reload());}
    public Applied apply()throws IOException{
        checkThread();JSONObject data=baseline.data();Set<String> changed=new LinkedHashSet<>();Set<ConfigField.Effect> effects=EnumSet.noneOf(ConfigField.Effect.class);
        for(ConfigField f:fields){
            Object value;
            try{value=f.parse(draft.get(f.key()));}catch(RuntimeException ex){throw new ConfigException(ConfigException.Code.VALIDATION_FAILED,"Invalid setting: "+f.key(),ex);}
            Object old=data.get(f.key());
            boolean same=old instanceof Number a&&value instanceof Number b?new java.math.BigDecimal(a.toString()).compareTo(new java.math.BigDecimal(b.toString()))==0:Objects.equals(old,value);
            if(!same){changed.add(f.key());effects.add(f.effect());}
            data.put(f.key(),value);
        }
        ConfigSnapshot saved=config.save(baseline,data);
        accept(saved);
        return new Applied(saved,changed,effects);
    }
}
