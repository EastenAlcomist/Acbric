/* ConfigField.java — 设置页的显式字段与双语说明；生效标记是 MOD 契约，不自动改变战役。 */
package net.fabricacs.api.config;

import net.fabricacs.api.impl.NumberValues;
import java.util.*;

public final class ConfigField {
    public enum Kind { BOOLEAN, INTEGER, DECIMAL, TEXT, CHOICE }
    public enum Effect { IMMEDIATE, RESTART, NEW_CAMPAIGN }
    public record Text(String english,String chinese) {
        public Text { Objects.requireNonNull(english);Objects.requireNonNull(chinese);if(english.length()>2048||chinese.length()>2048)throw new IllegalArgumentException("Text too long"); }
        public String resolve(boolean chineseLanguage){return chineseLanguage?chinese:english;}
    }
    public record Option(String value,Text label) {
        public Option { Objects.requireNonNull(value);Objects.requireNonNull(label);if(value.isEmpty()||value.length()>128||value.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Option value"); }
    }
    private final String key;
    private final Text label,description;
    private final Kind kind;
    private final Effect effect;
    private final double min,max;
    private final int limit;
    private final List<Option> options;
    private ConfigField(String key,Text label,Text description,Kind kind,Effect effect,double min,double max,int limit,List<Option> options){
        if(key==null||!key.matches("[a-zA-Z][a-zA-Z0-9_-]{0,63}"))throw new IllegalArgumentException("Top-level setting key");
        this.key=key;this.label=Objects.requireNonNull(label);this.description=Objects.requireNonNull(description);
        this.kind=kind;this.effect=Objects.requireNonNull(effect);this.min=min;this.max=max;this.limit=limit;this.options=List.copyOf(options);
    }
    private static ConfigField of(String key,Text label,Kind kind,Effect effect,double min,double max,int limit,List<Option> options){
        return new ConfigField(key,label,new Text("",""),kind,effect,min,max,limit,options);
    }
    public static ConfigField bool(String key,Text label,Effect effect){return of(key,label,Kind.BOOLEAN,effect,0,0,0,List.of());}
    public static ConfigField integer(String key,Text label,int min,int max,Effect effect){NumberValues.bounds(true,min,max);return of(key,label,Kind.INTEGER,effect,min,max,128,List.of());}
    public static ConfigField decimal(String key,Text label,double min,double max,Effect effect){NumberValues.bounds(false,min,max);return of(key,label,Kind.DECIMAL,effect,min,max,128,List.of());}
    public static ConfigField text(String key,Text label,int maxLength,Effect effect){if(maxLength<1||maxLength>4096)throw new IllegalArgumentException("maxLength 1..4096");return of(key,label,Kind.TEXT,effect,0,0,maxLength,List.of());}
    public static ConfigField choice(String key,Text label,List<Option> options,Effect effect){
        List<Option> copy=List.copyOf(options);if(copy.isEmpty()||copy.size()>64||copy.stream().map(Option::value).distinct().count()!=copy.size())throw new IllegalArgumentException("1..64 unique options");
        return of(key,label,Kind.CHOICE,effect,0,0,128,copy);
    }
    public ConfigField description(Text description){return new ConfigField(key,label,description,kind,effect,min,max,limit,options);}
    public String key(){return key;}public Text label(){return label;}public Text description(){return description;}
    public Kind kind(){return kind;}public Effect effect(){return effect;}public double minimum(){return min;}public double maximum(){return max;}
    public int maxLength(){return limit;}public List<Option> options(){return options;}
    Object fromJson(Object value){
        // 过长/多行的现有文本应在打开设置时拒绝，不能等到绘制输入框时才抛异常。
        if(kind==Kind.TEXT&&value instanceof String s&&(s.codePointCount(0,s.length())>4096||s.codePoints().anyMatch(Character::isISOControl)))throw new IllegalArgumentException("Text cannot be edited as a single line: "+key);
        if(kind==Kind.INTEGER||kind==Kind.DECIMAL){if(!(value instanceof Number))throw new IllegalArgumentException("Expected number: "+key);return value.toString();}
        if(kind==Kind.BOOLEAN&&value instanceof Boolean||kind!=Kind.BOOLEAN&&value instanceof String)return value;
        throw new IllegalArgumentException("Wrong field type: "+key);
    }
    Object parse(Object draft){
        return switch(kind){
            case BOOLEAN -> {if(!(draft instanceof Boolean))throw new IllegalArgumentException("Expected boolean");yield draft;}
            case INTEGER,DECIMAL -> NumberValues.parse((String)draft,kind==Kind.INTEGER,min,max);
            case TEXT -> {String s=(String)draft;if(s.codePointCount(0,s.length())>limit||s.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid single-line text");yield s;}
            case CHOICE -> {if(options.stream().noneMatch(o->o.value().equals(draft)))throw new IllegalArgumentException("Unknown option");yield draft;}
        };
    }
    public String error(Object draft,boolean chinese){
        if((kind==Kind.INTEGER||kind==Kind.DECIMAL)&&draft instanceof String s)return NumberValues.error(s,kind==Kind.INTEGER,min,max,chinese);
        try{parse(draft);return "";}catch(RuntimeException ex){return chinese?"字段值无效，请检查类型、长度或选项。":"Invalid field value; check its type, length or option.";}
    }
}
