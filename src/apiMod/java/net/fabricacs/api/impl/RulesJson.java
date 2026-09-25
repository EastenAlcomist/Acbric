/* RulesJson.java — 共享规则的有界规范 JSON；数字以稳定十进制编码，避免游戏浮点序列化差异。 */
package net.fabricacs.api.impl;

import org.json.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class RulesJson {
    public static final int MAX_BYTES = 12_000;
    private RulesJson() {}
    public static String encode(JSONObject object) {
        StringBuilder result = new StringBuilder();
        append(Objects.requireNonNull(object), result, new IdentityHashMap<>(), 0, new int[1]);
        String text = result.toString();
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IllegalArgumentException("RULES_TOO_LARGE");
        return text;
    }
    public static JSONObject parse(String text) {
        if (text == null || text.length() > MAX_BYTES || text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IllegalArgumentException("RULES_TOO_LARGE");
        JSONObject parsed = ConfigJson.parseObject(text);
        encode(parsed);
        return parsed;
    }
    private static void append(Object value, StringBuilder out, IdentityHashMap<Object, Boolean> active, int depth, int[] nodes) {
        if (depth > 16 || ++nodes[0] > 4096 || out.length() > MAX_BYTES) throw new IllegalArgumentException("RULES_LIMIT");
        if (value == null || value == JSONObject.NULL) { out.append("null"); return; }
        if (value instanceof String s) {
            if (s.length() > MAX_BYTES) throw new IllegalArgumentException("RULES_TOO_LARGE");
            for (int i=0;i<s.length();i++) if (Character.isSurrogate(s.charAt(i))) {
                if (!Character.isHighSurrogate(s.charAt(i)) || i+1==s.length() || !Character.isLowSurrogate(s.charAt(++i)))
                    throw new IllegalArgumentException("RULES_INVALID_UNICODE");
            }
            out.append(JSONObject.quote(s)); return;
        }
        if (value instanceof Boolean || value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) { out.append(value); return; }
        if (value instanceof Float || value instanceof Double) {
            double n=((Number)value).doubleValue();
            if (!Double.isFinite(n)) throw new IllegalArgumentException("RULES_INVALID_NUMBER");
            out.append(BigDecimal.valueOf(n).stripTrailingZeros().toPlainString()); return;
        }
        if (active.put(value,true)!=null) throw new IllegalArgumentException("RULES_CYCLE");
        try {
            if (value instanceof JSONObject object) {
                out.append('{'); boolean first=true;
                TreeSet<String> keys=new TreeSet<>(); var it=object.keys(); while(it.hasNext()) keys.add((String)it.next());
                if(keys.size()>4096) throw new IllegalArgumentException("RULES_LIMIT");
                for(String key:keys) {
                    if(!first)out.append(',');first=false;
                    append(key,out,active,depth+1,nodes);out.append(':');append(object.get(key),out,active,depth+1,nodes);
                }
                out.append('}'); return;
            }
            if(value instanceof JSONArray array) {
                if(array.length()>4096)throw new IllegalArgumentException("RULES_LIMIT");
                out.append('[');for(int i=0;i<array.length();i++){if(i>0)out.append(',');append(array.get(i),out,active,depth+1,nodes);}out.append(']');return;
            }
            throw new IllegalArgumentException("RULES_UNSUPPORTED_VALUE");
        } finally { active.remove(value); }
    }
}
