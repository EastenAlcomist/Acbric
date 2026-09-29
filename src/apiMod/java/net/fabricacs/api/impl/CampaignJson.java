/*
 * CampaignJson.java — 内部 JSON 验证及防御性复制，拒绝环、任意对象和无法稳定保存的数字。
 * 规范化为游戏 JSONObject 读回的数值类型，避免浮点整数在保存前后产生不同校验值。
 */
package net.fabricacs.api.impl;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.TreeSet;

public final class CampaignJson {
    private CampaignJson() {}

    public static JSONObject copy(JSONObject object, int maxDepth) {
        Objects.requireNonNull(object, "Campaign data must be a JSON object");
        JSONObject copy = (JSONObject) copyValue(object, 0, maxDepth, new IdentityHashMap<>());
        return new JSONObject(copy.toString());
    }

    private static Object copyValue(Object value, int depth, int limit, IdentityHashMap<Object, Boolean> active) {
        if (depth > limit) throw new IllegalArgumentException("Campaign JSON exceeds nesting limit " + limit);
        if (value == null || value == JSONObject.NULL) return JSONObject.NULL;
        if (value instanceof String || value instanceof Boolean) return value;
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) return value;
        if (value instanceof Float || value instanceof Double) {
            double number = ((Number) value).doubleValue();
            if (!Double.isFinite(number)) throw new IllegalArgumentException("Non-finite campaign JSON number");
            return value;
        }
        if (active.put(value, true) != null) throw new IllegalArgumentException("Cycle in campaign JSON");
        try {
            if (value instanceof JSONObject object) {
                JSONObject result = new JSONObject();
                TreeSet<String> keys = new TreeSet<>();
                var names = object.keys();
                while (names.hasNext()) keys.add((String) names.next());
                for (String key : keys) result.put(key, copyValue(object.get(key), depth + 1, limit, active));
                return result;
            }
            if (value instanceof JSONArray array) {
                JSONArray result = new JSONArray();
                for (int i = 0; i < array.length(); i++) result.put(copyValue(array.get(i), depth + 1, limit, active));
                return result;
            }
            throw new IllegalArgumentException("Unsupported campaign JSON value: " + value.getClass().getName());
        } finally {
            active.remove(value);
        }
    }
}
