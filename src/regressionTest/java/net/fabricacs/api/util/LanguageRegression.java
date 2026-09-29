/* LanguageRegression.java — 固定游戏 chi 与标准中文标签，防止按系统地区误判界面语言。 */
package net.fabricacs.api.util;

import java.util.Locale;

public final class LanguageRegression {
    public static int run() {
        String[] tags = {null, "en", "en-CN", "chi", "zh", "zh-CN", "zh-Hant-TW", "zho"};
        for (int i=0;i<tags.length;i++) {
            Locale locale=tags[i]==null?null:Locale.forLanguageTag(tags[i]);
            if (AcbricLanguage.chineseLocale(locale)!=(i>=3)) throw new AssertionError("Game language: "+tags[i]);
        }
        System.out.println("LANGUAGE REGRESSION PASS: "+tags.length+" checks");
        return tags.length;
    }
}
