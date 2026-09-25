/*
 * ConfigJson.java — 配置文件的严格 JSON 语法门禁，避免原版宽松解析器吞掉尾部垃圾/重复键。
 * 完成语法与深度检查后才交给游戏 JSONObject；不改变战役存档既有解析语义。
 */
package net.fabricacs.api.impl;

import org.json.JSONObject;
import org.json.JSONTokener;
import java.util.HashSet;
import java.util.regex.Pattern;

public final class ConfigJson {
    private static final Pattern NUMBER = Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");
    private final String text;
    private int pos;
    private ConfigJson(String text) { this.text = text; }
    public static JSONObject parse(String text) {
        if (text.startsWith("\ufeff")) text = text.substring(1);
        ConfigJson parser = new ConfigJson(text);
        parser.space();
        if (parser.peek() != '{') throw parser.invalid();
        parser.value(0); parser.space();
        if (parser.pos != text.length()) throw parser.invalid();
        return CampaignJson.copy(new JSONObject(text), 72);
    }
    private IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid config JSON at character " + pos); }
    private char peek() { return pos < text.length() ? text.charAt(pos) : '\0'; }
    private void space() { while (pos < text.length() && " \t\r\n".indexOf(text.charAt(pos)) >= 0) pos++; }
    private void expect(char c) { space(); if (peek() != c) throw invalid(); pos++; }
    private boolean take(char c) { space(); if (peek() != c) return false; pos++; return true; }
    private String string() {
        space(); int start = pos; expect('"');
        while (pos < text.length()) {
            char c = text.charAt(pos++);
            if (c == '"') return (String) new JSONTokener(text.substring(start, pos)).nextValue();
            if (c < 0x20) throw invalid();
            if (c == '\\') {
                if (pos == text.length()) throw invalid();
                char escape = text.charAt(pos++);
                if (escape == 'u') {
                    for (int i = 0; i < 4; i++) {
                        if (pos == text.length() || "0123456789abcdefABCDEF".indexOf(text.charAt(pos++)) < 0) throw invalid();
                    }
                } else if ("\"\\/bfnrt".indexOf(escape) < 0) throw invalid();
            }
        }
        throw invalid();
    }
    private void value(int depth) {
        if (depth > 72) throw invalid();
        space();
        switch (peek()) {
            case '{' -> {
                pos++; HashSet<String> keys = new HashSet<>();
                if (take('}')) return;
                do { if (!keys.add(string())) throw invalid(); expect(':'); value(depth + 1); } while (take(','));
                expect('}');
            }
            case '[' -> {
                pos++; if (take(']')) return;
                do { value(depth + 1); } while (take(','));
                expect(']');
            }
            case '"' -> string();
            case 't' -> literal("true");
            case 'f' -> literal("false");
            case 'n' -> literal("null");
            default -> {
                var number = NUMBER.matcher(text).region(pos, text.length());
                if (!number.lookingAt() || !Double.isFinite(Double.parseDouble(number.group()))) throw invalid();
                pos = number.end();
            }
        }
    }
    private void literal(String word) { if (!text.startsWith(word, pos)) throw invalid(); pos += word.length(); }
}
