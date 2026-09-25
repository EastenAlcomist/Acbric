/* AcbricLanguage.java — 框架与 MOD 共用游戏语言选择；支持游戏 chi 和标准中文标签，其他语言回退英文。 */
package net.fabricacs.api.util;

import com.zarkonnen.airships.Lang;
import java.util.Objects;

public final class AcbricLanguage {
    private AcbricLanguage() { }

    /** 每次读取游戏当前语言，不缓存系统 Locale；在游戏初始化后的游戏线程使用。 */
    public static boolean isChinese() {
        return chineseLocale(Lang.currentLocale);
    }

    static boolean chineseLocale(java.util.Locale locale) {
        if (locale == null) return false;
        String language = locale.getLanguage();
        return language.equals("chi") || language.equals("zh") || language.equals("zho");
    }

    /** 按游戏语言返回英文或中文文案，参数顺序固定为英文、中文。 */
    public static String text(String english, String chinese) {
        Objects.requireNonNull(english); Objects.requireNonNull(chinese);
        return isChinese() ? chinese : english;
    }
}
